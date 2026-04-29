import io
import wave
import serial
import requests
import sys

PORT = "COM3"                   # change if needed
BAUD = 921600

SAMPLE_RATE = 16000
CHANNELS = 1
SAMPLE_WIDTH = 2
RECORD_SECONDS = 5

BACKEND_URL = "http://localhost:8080/upload"
UPLOAD_TIMEOUT_SECONDS = 300

TOTAL_BYTES = SAMPLE_RATE * CHANNELS * SAMPLE_WIDTH * RECORD_SECONDS


def wait_for_start(ser):
    print("Waiting for ESP32...")
    while True:
        line = ser.readline().decode(errors="ignore").strip()
        if line:
            print("ESP32:", line)
        if line == "START":
            return


def receive_audio(ser):
    print("Receiving audio...")
    audio_data = b""

    while len(audio_data) < TOTAL_BYTES:
        chunk = ser.read(TOTAL_BYTES - len(audio_data))
        if not chunk:
            raise RuntimeError("Serial read timed out before full audio was received")
        audio_data += chunk

        sys.stdout.write("\r{}/{} bytes".format(len(audio_data), TOTAL_BYTES))
        sys.stdout.flush()

    print()
    return audio_data


def build_wav_bytes(audio_data):
    wav_io = io.BytesIO()

    with wave.open(wav_io, "wb") as wf:
        wf.setnchannels(CHANNELS)
        wf.setsampwidth(SAMPLE_WIDTH)
        wf.setframerate(SAMPLE_RATE)
        wf.writeframes(audio_data)

    wav_io.seek(0)
    return wav_io


def upload_to_backend(wav_io):
    files = {
        "file": ("recorded.wav", wav_io, "audio/wav")
    }

    print("Uploading to backend...")
    response = requests.post(BACKEND_URL, files=files, timeout=UPLOAD_TIMEOUT_SECONDS)

    print("Status:", response.status_code)
    print("Response:", response.text)
    response.raise_for_status()


def main():
    with serial.Serial(PORT, BAUD, timeout=10) as ser:
        wait_for_start(ser)
        audio_data = receive_audio(ser)

    wav_io = build_wav_bytes(audio_data)
    upload_to_backend(wav_io)
    print("Done.")


if __name__ == "__main__":
    main()
