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

## Notes

The included fingerprint CSV is placeholder development data. Replace this file with generated
fingerprints derived from a legally sourced reference track before shipping.
