# SheZam MVP Scaffold

This repository now contains a working Android MVP scaffold based on `APP_PLAN.md`:

- One-screen Jetpack Compose app with `idle/listening/processing/result` states.
- Runtime microphone permission handling.
- 5-second audio capture using `AudioRecord` (mono, PCM16, 16 kHz).
- On-device fingerprint extraction and matching.
- Confidence-based YES/NO output.
- Bundled reference fingerprint asset (`app/src/main/assets/she_bop_fingerprints.csv`).

## Modules

- `:app` — Android application and UI flow.
- `:core` — Pure Kotlin fingerprinting/matching logic + unit tests.

## Fingerprint asset generation and validation

Use `tools/she_bop_fingerprint_tool.py` to generate `she_bop_fingerprints.csv` with the **same algorithm and parameters used at runtime** (`SimpleFingerprinter`: 16 kHz, frame size 1024, hop size 512, top 5 bins).

1. Acquire a legally sourced She Bop reference recording.
2. Convert it to mono, 16-bit PCM WAV at 16 kHz (exact runtime ingest format).
   - Example conversion command (if `ffmpeg` is installed):
     - `ffmpeg -i she_bop_reference_source.wav -ac 1 -ar 16000 -c:a pcm_s16le she_bop_reference_16k.wav`
3. Generate the production fingerprint CSV:
   - `python tools/she_bop_fingerprint_tool.py generate she_bop_reference_16k.wav app/src/main/assets/she_bop_fingerprints.csv`
4. Validate against known positive clips (each clip must also be mono 16-bit PCM WAV at 16 kHz):
   - `python tools/she_bop_fingerprint_tool.py validate app/src/main/assets/she_bop_fingerprints.csv ./validation/positive_clips --threshold 0.55`

Validation exits non-zero if any known positive clip falls below threshold.
