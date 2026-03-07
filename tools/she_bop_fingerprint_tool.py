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
import random
import statistics
import struct
import sys
import wave
from dataclasses import dataclass
from typing import Iterable

SAMPLE_RATE = 16_000
FRAME_SIZE = 1024
HOP_SIZE = 512
TOP_BINS_PER_FRAME = 3
PEAK_SALIENCE_THRESHOLD_RATIO = 0.45
TARGET_ZONE_MAX_DELTA_FRAMES = 2
BIN_QUANTIZATION = 1
DELTA_QUANTIZATION = 1
DEFAULT_DECISION_THRESHOLD = 0.55


@dataclass(frozen=True)
class FingerprintParams:
    sample_rate: int = SAMPLE_RATE
    frame_size: int = FRAME_SIZE
    hop_size: int = HOP_SIZE
    top_bins_per_frame: int = TOP_BINS_PER_FRAME
    peak_salience_threshold_ratio: float = PEAK_SALIENCE_THRESHOLD_RATIO
    target_zone_max_delta_frames: int = TARGET_ZONE_MAX_DELTA_FRAMES
    bin_quantization: int = BIN_QUANTIZATION
    delta_quantization: int = DELTA_QUANTIZATION


@dataclass(frozen=True)
class FingerprintToken:
    bin_a: int
    bin_b: int
    delta_frames: int
    frame: int


BASELINE_COLLISION_PARAMS = FingerprintParams(
    top_bins_per_frame=5,
    peak_salience_threshold_ratio=0.0,
    target_zone_max_delta_frames=4,
    bin_quantization=4,
    delta_quantization=2,
)
TUNED_COLLISION_PARAMS = FingerprintParams()


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


def top_salient_bins(magnitudes: list[float], count: int, salience_threshold_ratio: float) -> list[int]:
    if not magnitudes:
        return []
    max_magnitude = max(magnitudes)
    if max_magnitude <= 0.0:
        return []
    threshold = max_magnitude * salience_threshold_ratio

    indexed = [pair for pair in enumerate(magnitudes) if pair[1] >= threshold]
    indexed.sort(key=lambda pair: pair[1], reverse=True)
    return sorted(index for index, _ in indexed[:count])


def quantize(value: int, quantum: int) -> int:
    if quantum <= 1:
        return value
    return (value // quantum) * quantum


def fingerprint(samples: list[int], params: FingerprintParams = FingerprintParams()) -> list[FingerprintToken]:
    if not samples:
        return []

    mono = normalize(samples)
    frame_count = max(0, (len(mono) - params.frame_size)) // params.hop_size + 1
    if frame_count <= 0:
        return []

    peaks_by_frame: list[list[int]] = []
    for frame_index in range(frame_count):
        start = frame_index * params.hop_size
        frame = mono[start : min(start + params.frame_size, len(mono))]
        if len(frame) < params.frame_size:
            frame = frame + [0.0] * (params.frame_size - len(frame))
        magnitudes = dft_magnitudes(frame)
        peaks_by_frame.append(
            top_salient_bins(
                magnitudes,
                params.top_bins_per_frame,
                params.peak_salience_threshold_ratio,
            )
        )

    tokens: list[FingerprintToken] = []
    for anchor_frame, anchors in enumerate(peaks_by_frame):
        if not anchors:
            continue
        max_target = min(len(peaks_by_frame) - 1, anchor_frame + params.target_zone_max_delta_frames)
        for target_frame in range(anchor_frame + 1, max_target + 1):
            targets = peaks_by_frame[target_frame]
            if not targets:
                continue
            delta_frames = quantize(target_frame - anchor_frame, params.delta_quantization)
            for anchor_bin in anchors:
                for target_bin in targets:
                    tokens.append(
                        FingerprintToken(
                            bin_a=quantize(anchor_bin, params.bin_quantization),
                            bin_b=quantize(target_bin, params.bin_quantization),
                            delta_frames=delta_frames,
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


def synth_tokens(seed: int, hard_negative: bool, params: FingerprintParams) -> list[FingerprintToken]:
    rng = random.Random(seed)
    frame_count = 60
    tokens: list[FingerprintToken] = []
    for frame in range(frame_count - params.target_zone_max_delta_frames - 1):
        anchor_base = 32 + frame * 2
        if hard_negative:
            anchor_base += 1
        for delta in range(1, params.target_zone_max_delta_frames + 1):
            target_frame = frame + delta
            for i in range(params.top_bins_per_frame):
                anchor_bin = anchor_base + i * 7 + rng.randint(-1, 1)
                target_bin = 72 + target_frame * 2 + i * 9 + rng.randint(-1, 1)
                if hard_negative:
                    # Negatives are near enough to collide under coarse quantization.
                    anchor_bin += rng.choice((-2, 2))
                    target_bin += rng.choice((-2, 2))
                tokens.append(
                    FingerprintToken(
                        bin_a=quantize(anchor_bin, params.bin_quantization),
                        bin_b=quantize(target_bin, params.bin_quantization),
                        delta_frames=quantize(delta, params.delta_quantization),
                        frame=frame,
                    )
                )
    return tokens


def distribution(values: list[int]) -> str:
    if not values:
        return "n=0"
    p90 = sorted(values)[max(0, math.ceil(0.9 * len(values)) - 1)]
    return (
        f"n={len(values)} min={min(values)} p50={statistics.median(values):.1f} "
        f"p90={p90} max={max(values)} mean={statistics.mean(values):.2f}"
    )


def collision_report(output_path: pathlib.Path) -> None:
    groups = {
        "positive": lambda i, p: synth_tokens(seed=100 + i, hard_negative=False, params=p),
        "hard_negative": lambda i, p: synth_tokens(seed=200 + i, hard_negative=True, params=p),
    }

    rows: list[tuple[str, str, float, int]] = []
    sections: list[str] = []
    for label, params in (("before", BASELINE_COLLISION_PARAMS), ("after", TUNED_COLLISION_PARAMS)):
        ref_tokens = synth_tokens(seed=7, hard_negative=False, params=params)
        sections.append(f"## {label.title()} tuning")
        sections.append(f"reference_tokens={len(ref_tokens)}")
        for group_name, generator in groups.items():
            votes: list[int] = []
            confidences: list[float] = []
            for i in range(20):
                observed = generator(i, params)
                confidence, strongest = match_confidence(observed, ref_tokens)
                votes.append(strongest)
                confidences.append(confidence)
                rows.append((label, group_name, confidence, strongest))
            sections.append(
                f"- {group_name}: votes[{distribution(votes)}] "
                f"confidence[min={min(confidences):.3f} p50={statistics.median(confidences):.3f} "
                f"max={max(confidences):.3f}]"
            )
        sections.append("")

    output_path.parent.mkdir(parents=True, exist_ok=True)
    report = "# Fingerprint collision validation\n\n" + "\n".join(sections)
    output_path.write_text(report, encoding="utf-8")

    csv_path = output_path.with_suffix(".csv")
    with csv_path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["version", "group", "confidence", "votes"])
        writer.writerows(rows)


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


def cmd_collision_report(args: argparse.Namespace) -> int:
    out = pathlib.Path(args.output_report)
    collision_report(out)
    print(f"Wrote collision report to {out} and {out.with_suffix('.csv')}")
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

    report = subparsers.add_parser(
        "collision-report",
        help="Generate synthetic positive vs hard-negative vote distributions before/after tuning",
    )
    report.add_argument(
        "--output-report",
        default="tools/reports/she_bop_collision_report.md",
        help="Path to markdown report output",
    )
    report.set_defaults(func=cmd_collision_report)

    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
