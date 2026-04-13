from __future__ import annotations

import argparse
import tempfile
from pathlib import Path

import torchaudio

try:
    from speechbrain.inference.separation import SepformerSeparation
except ImportError:  # SpeechBrain also exposes this in older versions.
    from speechbrain.pretrained import SepformerSeparation


TARGET_SAMPLE_RATE = 8000


def load_model(model_id: str, cache_dir: Path) -> SepformerSeparation:
    cache_dir.mkdir(parents=True, exist_ok=True)
    return SepformerSeparation.from_hparams(source=model_id, savedir=str(cache_dir))


def normalize_audio(input_path: Path) -> tuple[object, int]:
    waveform, sample_rate = torchaudio.load(str(input_path))

    if waveform.shape[0] > 1:
        waveform = waveform.mean(dim=0, keepdim=True)

    if sample_rate != TARGET_SAMPLE_RATE:
        waveform = torchaudio.functional.resample(waveform, sample_rate, TARGET_SAMPLE_RATE)

    return waveform, TARGET_SAMPLE_RATE


def separate(input_path: Path, output_dir: Path, model_id: str, cache_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    model = load_model(model_id, cache_dir)

    waveform, sample_rate = normalize_audio(input_path)

    with tempfile.TemporaryDirectory() as temp_dir:
        prepared_input = Path(temp_dir) / "prepared.wav"
        torchaudio.save(str(prepared_input), waveform, sample_rate)

        estimated_sources = model.separate_file(path=str(prepared_input)).detach().cpu()
        number_of_sources = estimated_sources.shape[-1]

        for index in range(number_of_sources):
            output_path = output_dir / f"source{index + 1}.wav"
            separated_waveform = estimated_sources[:, :, index]
            torchaudio.save(str(output_path), separated_waveform, TARGET_SAMPLE_RATE)


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
