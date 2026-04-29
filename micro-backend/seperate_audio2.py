from __future__ import annotations

import argparse
import sys
import tempfile
from pathlib import Path

import soundfile as sf
import torch
import torchaudio
import yaml
from huggingface_hub import snapshot_download


TARGET_SAMPLE_RATE = 16000

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")


def load_model(model_id: str, cache_dir: Path):
    """
    Download and load Rahma89/voice-separation-model from Hugging Face.

    This replaces SpeechBrain's:
        SepformerSeparation.from_hparams(...)
    because this model is an Asteroid Conv-TasNet checkpoint.
    """
    cache_dir.mkdir(parents=True, exist_ok=True)

    model_dir = Path(
        snapshot_download(
            repo_id=model_id,
            cache_dir=str(cache_dir),
            repo_type="model",
        )
    )

    # Allow imports from downloaded Hugging Face repo, especially src/model.py
    sys.path.insert(0, str(model_dir))

    from src.model import build_model, load_checkpoint

    train_config_path = model_dir / "configs" / "train.yaml"
    data_config_path = model_dir / "configs" / "data.yaml"
    checkpoint_path = model_dir / "best.ckpt"

    with open(train_config_path, "r", encoding="utf-8") as f:
        train_cfg = yaml.safe_load(f)

    with open(data_config_path, "r", encoding="utf-8") as f:
        data_cfg = yaml.safe_load(f)

    mod = train_cfg["model"]
    ds = data_cfg["dataset"]

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

    model = build_model(
        n_src=ds["n_src"],
        sample_rate=ds["sample_rate"],
        n_filters=mod["n_filters"],
        filter_length=mod["filter_length"],
        stride=mod["stride"],
        n_blocks=mod["n_blocks"],
        n_repeats=mod["n_repeats"],
        bn_chan=mod["bn_chan"],
        hid_chan=mod["hid_chan"],
        skip_chan=mod["skip_chan"],
        norm_type=mod["norm_type"],
        mask_act=mod["mask_act"],
        use_gradient_checkpointing=False,
    ).to(device)

    load_checkpoint(model, checkpoint_path, device)
    model.eval()

    return model, device, ds["sample_rate"]


def normalize_audio(input_path: Path, target_sample_rate: int) -> tuple[torch.Tensor, int]:
    audio, sample_rate = sf.read(str(input_path), always_2d=True, dtype="float32")
    waveform = torch.from_numpy(audio).transpose(0, 1)

    # Convert stereo/multi-channel audio to mono
    if waveform.shape[0] > 1:
        waveform = waveform.mean(dim=0, keepdim=True)

    if sample_rate != target_sample_rate:
        waveform = torchaudio.functional.resample(
            waveform,
            sample_rate,
            target_sample_rate,
        )

    return waveform, target_sample_rate


def separate(input_path: Path, output_dir: Path, model_id: str, cache_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)

    model, device, sample_rate = load_model(model_id, cache_dir)
    waveform, sample_rate = normalize_audio(input_path, sample_rate)

    waveform = waveform.to(device)

    with torch.no_grad():
        # ConvTasNet expects shape: [batch, time]
        mixture = waveform.unsqueeze(0)  # [1, 1, time]

        # Some Asteroid models expect [batch, time], not [batch, channels, time]
        if mixture.dim() == 3 and mixture.shape[1] == 1:
            mixture = mixture.squeeze(1)  # [1, time]

        estimated_sources = model(mixture)

    estimated_sources = estimated_sources.detach().cpu()

    # Expected Asteroid output shape is usually [batch, n_src, time]
    if estimated_sources.dim() == 3:
        estimated_sources = estimated_sources[0]  # [n_src, time]

    number_of_sources = estimated_sources.shape[0]

    for index in range(number_of_sources):
        output_path = output_dir / f"source{index + 1}.wav"

        separated_waveform = estimated_sources[index].unsqueeze(0)
        sf.write(str(output_path), separated_waveform.squeeze(0).numpy(), sample_rate)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--model-id", required=True)
    parser.add_argument("--cache-dir", required=True)
    args = parser.parse_args()

    separate(
        input_path=Path(args.input).resolve(),
        output_dir=Path(args.output_dir).resolve(),
        model_id=args.model_id,
        cache_dir=Path(args.cache_dir).resolve(),
    )


if __name__ == "__main__":
    main()
