#!/usr/bin/env python3
"""Generate and validate She Bop fingerprints with runtime-identical parameters."""

from __future__ import annotations

import argparse
import warnings

warnings.filterwarnings("ignore", message=".*audioop.*deprecated.*", category=DeprecationWarning)
import audioop
import csv
import math
import pathlib
import struct
import sys
import wave
from dataclasses import dataclass
from typing import Iterable

SAMPLE_RATE = 16_000
FRAME_SIZE = 1024
HOP_SIZE = 512
TOP_BINS_PER_FRAME = 5
TARGET_ZONE_FRAMES = 4
DEFAULT_DECISION_THRESHOLD = 0.55


@dataclass(frozen=True)
class FingerprintToken:
    bin_a: int
    bin_b: int
    delta_frames: int
    frame: int


def read_pcm16_mono_16k(path: pathlib.Path) -> list[int]:
    with wave.open(str(path), "rb") as wav:
        channels = wav.getnchannels()
        sample_width = wav.getsampwidth()
        sample_rate = wav.getframerate()
        frame_count = wav.getnframes()
        comp_type = wav.getcomptype()

        if comp_type != "NONE":
            raise ValueError(f"Only uncompressed PCM WAV is supported, got comp_type={comp_type}")

        data = wav.readframes(frame_count)

    data = convert_to_pcm16_mono_16k(
        data=data,
        channels=channels,
        sample_width=sample_width,
        sample_rate=sample_rate,
    )

    frame_count = len(data) // 2
    if frame_count == 0:
        return []

    return list(struct.unpack("<" + "h" * frame_count, data))


def convert_to_pcm16_mono_16k(
    *,
    data: bytes,
    channels: int,
    sample_width: int,
    sample_rate: int,
) -> bytes:
    if sample_width not in (1, 2, 3, 4):
        raise ValueError(f"Unsupported PCM sample width: {sample_width * 8}-bit")

    if channels < 1:
        raise ValueError(f"Invalid channel count: {channels}")

    converted = data

    if channels != 1:
        converted = audioop.tomono(converted, sample_width, 0.5, 0.5)

    if sample_rate != SAMPLE_RATE:
        converted, _ = audioop.ratecv(
            converted,
            sample_width,
            1,
            sample_rate,
            SAMPLE_RATE,
            None,
        )

    if sample_width != 2:
        converted = audioop.lin2lin(converted, sample_width, 2)

    return converted


def normalize(samples: list[int]) -> list[float]:
    max_abs = max(1, max(abs(v) for v in samples))
    return [v / float(max_abs) for v in samples]


def dft_magnitudes(frame: list[float]) -> list[float]:
    n = len(frame)
    useful_bins = n // 2
    output = [0.0] * useful_bins
    for k in range(useful_bins):
        real = 0.0
        imag = 0.0
        for t in range(n):
            angle = -2.0 * math.pi * k * t / n
            real += frame[t] * math.cos(angle)
            imag += frame[t] * math.sin(angle)
        output[k] = math.hypot(real, imag)
    return output


def top_bins(magnitudes: list[float], count: int) -> list[int]:
    indexed = list(enumerate(magnitudes))
    indexed.sort(key=lambda pair: pair[1], reverse=True)
    return sorted(index for index, _ in indexed[:count])


def fingerprint(samples: list[int]) -> list[FingerprintToken]:
    if not samples:
        return []

    mono = normalize(samples)
    frame_count = max(0, (len(mono) - FRAME_SIZE)) // HOP_SIZE + 1
    if frame_count <= 0:
        return []

    peaks_by_frame: list[list[int]] = []
    for frame_index in range(frame_count):
        start = frame_index * HOP_SIZE
        frame = mono[start : min(start + FRAME_SIZE, len(mono))]
        if len(frame) < FRAME_SIZE:
            frame = frame + [0.0] * (FRAME_SIZE - len(frame))
        magnitudes = dft_magnitudes(frame)
        peaks_by_frame.append(top_bins(magnitudes, TOP_BINS_PER_FRAME))

    tokens: list[FingerprintToken] = []
    for anchor_frame, anchor_peaks in enumerate(peaks_by_frame):
        if not anchor_peaks:
            continue

        max_target_frame = min(anchor_frame + TARGET_ZONE_FRAMES, len(peaks_by_frame) - 1)
        for target_frame in range(anchor_frame + 1, max_target_frame + 1):
            target_peaks = peaks_by_frame[target_frame]
            if not target_peaks:
                continue

            for bin_a in anchor_peaks:
                for bin_b in target_peaks:
                    tokens.append(
                        FingerprintToken(
                            bin_a=bin_a,
                            bin_b=bin_b,
                            delta_frames=target_frame - anchor_frame,
                            frame=anchor_frame,
                        )
                    )

    return tokens


def write_csv(tokens: Iterable[FingerprintToken], output_path: pathlib.Path) -> None:
    with output_path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["binA", "binB", "deltaFrames", "frame"])
        for token in tokens:
            writer.writerow([token.bin_a, token.bin_b, token.delta_frames, token.frame])


def read_csv(path: pathlib.Path) -> list[FingerprintToken]:
    tokens: list[FingerprintToken] = []
    with path.open("r", newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            tokens.append(
                FingerprintToken(
                    bin_a=int(row["binA"]),
                    bin_b=int(row["binB"]),
                    delta_frames=int(row["deltaFrames"]),
                    frame=int(row["frame"]),
                )
            )
    return tokens


def match_confidence(observed: list[FingerprintToken], reference: list[FingerprintToken]) -> tuple[float, int]:
    if not observed or not reference:
        return 0.0, 0

    ref_index: dict[tuple[int, int, int], list[FingerprintToken]] = {}
    for token in reference:
        key = (token.bin_a, token.bin_b, token.delta_frames)
        ref_index.setdefault(key, []).append(token)

    votes: dict[int, int] = {}
    for token in observed:
        key = (token.bin_a, token.bin_b, token.delta_frames)
        for ref in ref_index.get(key, []):
            offset = ref.frame - token.frame
            votes[offset] = votes.get(offset, 0) + 1

    strongest = max(votes.values()) if votes else 0
    confidence = strongest / float(len(observed))
    return confidence, strongest


def cmd_generate(args: argparse.Namespace) -> int:
    samples = read_pcm16_mono_16k(pathlib.Path(args.input_wav))
    tokens = fingerprint(samples)
    write_csv(tokens, pathlib.Path(args.output_csv))
    print(f"Generated {len(tokens)} tokens to {args.output_csv}")
    return 0


def cmd_validate(args: argparse.Namespace) -> int:
    reference = read_csv(pathlib.Path(args.reference_csv))
    threshold = args.threshold
    positives_dir = pathlib.Path(args.positive_clips_dir)
    clips = sorted(positives_dir.glob("*.wav"))
    if not clips:
        print(f"No .wav clips found in {positives_dir}", file=sys.stderr)
        return 2

    failed = 0
    for clip in clips:
        observed = fingerprint(read_pcm16_mono_16k(clip))
        confidence, votes = match_confidence(observed, reference)
        status = "PASS" if confidence >= threshold else "FAIL"
        if status == "FAIL":
            failed += 1
        print(f"{status}  confidence={confidence:.3f}  votes={votes:4d}  clip={clip.name}")

    if failed:
        print(f"Validation failed for {failed} clip(s).")
        return 1

    print("Validation passed for all positive clips.")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    gen = subparsers.add_parser("generate", help="Generate fingerprint CSV from a 16k PCM16 mono WAV")
    gen.add_argument("input_wav")
    gen.add_argument("output_csv")
    gen.set_defaults(func=cmd_generate)

    val = subparsers.add_parser("validate", help="Validate known positive WAV clips against reference CSV")
    val.add_argument("reference_csv")
    val.add_argument("positive_clips_dir")
    val.add_argument("--threshold", type=float, default=DEFAULT_DECISION_THRESHOLD)
    val.set_defaults(func=cmd_validate)

    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
