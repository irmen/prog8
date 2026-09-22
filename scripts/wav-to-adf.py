#!/usr/bin/env python3
"""Read a WAV file, print its metadata, and write exactly 880 KB of its
audio data (data chunk) to 'music.adf' without modifying any data bytes."""

import argparse
import struct
import sys
import wave

TARGET_BYTES = 880 * 1024  # 880 KB


def read_ima_adpcm_wav(filename: str):
    with open(filename, "rb") as f:
        header = f.read(12)
        if len(header) < 12 or header[:4] != b"RIFF" or header[8:12] != b"WAVE":
            raise ValueError(f"not a RIFF/WAVE file: {filename}")

        fmt = None
        data_bytes = None
        while True:
            chunk_header = f.read(8)
            if len(chunk_header) < 8:
                break
            chunk_id, chunk_size = struct.unpack("<4sI", chunk_header)
            chunk_data = f.read(chunk_size)
            if len(chunk_data) != chunk_size:
                raise ValueError(f"truncated WAV chunk {chunk_id!r}")
            if chunk_id == b"fmt ":
                fmt = chunk_data
            elif chunk_id == b"data":
                data_bytes = chunk_data
            if chunk_size & 1:
                f.read(1)

    if fmt is None or len(fmt) < 20:
        raise ValueError("WAV file has no usable fmt chunk")
    if data_bytes is None:
        raise ValueError("WAV file has no data chunk")

    format_tag, channels, sample_rate, byte_rate, block_align, bits_per_sample, extra_size, samples_per_block = struct.unpack(
        "<HHIIHHHH", fmt[:20]
    )
    if format_tag != 17:
        raise ValueError(f"unsupported WAV format tag: {format_tag}")
    if channels != 1:
        raise ValueError("IMA ADPCM input must be mono")
    if block_align != 256 or samples_per_block != 505:
        raise ValueError(
            "IMA ADPCM input must use 256-byte blocks with 505 samples per block"
        )
    trailing_bytes = len(data_bytes) % block_align
    if trailing_bytes:
        data_bytes = data_bytes[:-trailing_bytes]

    return {
        "comptype": "IMA ADPCM (format 17)",
        "channels": channels,
        "sampwidth": 0,
        "framerate": sample_rate,
        "frames": (len(data_bytes) // block_align) * samples_per_block,
        "byte_rate": byte_rate,
        "block_align": block_align,
        "bits_per_sample": bits_per_sample,
        "data_bytes": data_bytes,
        "samples_per_block": samples_per_block,
        "trailing_bytes": trailing_bytes,
    }


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("wavfile", help="input .wav file")
    ap.add_argument("-o", "--out", default="music.adf", help="output file (default: music.adf)")
    args = ap.parse_args()

    try:
        with wave.open(args.wavfile, "rb") as wav:
            channels = wav.getnchannels()
            sampwidth = wav.getsampwidth()
            framerate = wav.getframerate()
            frames = wav.getnframes()
            comptype = wav.getcomptype()
            byte_rate = framerate * channels * sampwidth
            block_align = channels * sampwidth
            bits_per_sample = sampwidth * 8
            data_bytes = wav.readframes(frames)
        samples_per_block = None
        trailing_bytes = 0
    except wave.Error:
        info = read_ima_adpcm_wav(args.wavfile)
        channels = info["channels"]
        framerate = info["framerate"]
        frames = info["frames"]
        comptype = info["comptype"]
        byte_rate = info["byte_rate"]
        block_align = info["block_align"]
        bits_per_sample = info["bits_per_sample"]
        data_bytes = info["data_bytes"]
        samples_per_block = info["samples_per_block"]
        trailing_bytes = info["trailing_bytes"]

    print(f"Compression type  : {comptype}")
    print(f"Channels          : {channels}")
    print(f"Sample rate       : {framerate} Hz")
    print(f"Byte rate         : {byte_rate} bytes/s")
    print(f"Block align       : {block_align} bytes")
    print(f"Bits per sample   : {bits_per_sample}")
    print(f"Data chunk size   : {len(data_bytes)} bytes")
    print(f"Duration          : {frames / framerate:.2f} s")
    if samples_per_block is not None:
        print(f"Samples per block : {samples_per_block}")
        if trailing_bytes:
            print(f"warning: discarded {trailing_bytes} bytes from incomplete final ADPCM block")

    out_size = min(len(data_bytes), TARGET_BYTES)
    with open(args.out, "wb") as f:
        f.write(data_bytes[:out_size])
        f.write(b"\x00" * (TARGET_BYTES - out_size))
    print(f"Wrote {TARGET_BYTES} bytes to {args.out} "
          f"({out_size} bytes of audio data"
          + (f" + {TARGET_BYTES - out_size} padding bytes)" if out_size < TARGET_BYTES else ")"))


if __name__ == "__main__":
    main()
