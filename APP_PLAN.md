# SheZam Plan: "Is this She Bop?" (Android MVP)

## 1) Product Goal
Build a single-purpose Android app that listens to ambient audio and answers:

- **YES**: this likely is **"She Bop" by Cyndi Lauper**
- **NO**: this likely is not "She Bop"

This is intentionally **not** a general music-recognition app.

---

## 2) MVP Scope (What we will build first)

### In scope
- One-screen Android app (Jetpack Compose)
- Tap-to-listen flow (5-second capture)
- On-device matching against a precomputed "She Bop" fingerprint database
- Confidence score + binary result (YES/NO)
- Works offline after installation

### Out of scope
- Recognizing other songs
- Cloud backend/user accounts/history
- Continuous/background listening
- Polished social/product growth features

---

## 3) System Design (Simple + Practical)

### A. Capture
- Use `AudioRecord`
- Mono, PCM 16-bit, 16 kHz sample rate
- Record fixed window (default 5s)

### B. Signal Processing
- Normalize amplitude
- Optional high-pass filter (remove low-frequency rumble)
- Convert to spectrogram (STFT)

### C. Fingerprinting
- Detect local spectral peaks
- Create landmark pairs (peak A, peak B, delta-time)
- Hash into compact fingerprint tokens

### D. Matching
- Lookup observed hashes in stored "She Bop" hash map
- Vote for consistent time-offset alignments
- Compute confidence from strongest vote cluster

### E. Decision
- `confidence >= decision_threshold` → **YES**
- `confidence < decision_threshold` → **NO**

---

## 4) Delivery Plan (Execution Order)

## Phase 0 — Feasibility (1–2 days)
**Goal:** prove matching works before full app development.

Tasks:
1. Build small local prototype (Kotlin or Python) implementing fingerprint + match.
2. Test with:
   - clean "She Bop" clips
   - phone-speaker recordings in moderate noise
   - non-target songs
3. Pick first threshold values.

Exit criteria:
- Strong separation between positives and negatives on a small test set.

## Phase 1 — Android MVP (3–5 days)
**Goal:** working app end-to-end.

Tasks:
1. Create Compose screen with states: idle/listening/processing/result.
2. Add runtime mic permission handling.
3. Integrate 5-second capture.
4. Port/implement matcher in app code.
5. Bundle fingerprint DB in assets.
6. Show result + confidence + retry hint.

Exit criteria:
- User can tap once and get a result on-device in a few seconds.

## Phase 2 — Reliability Tuning (2–4 days)
**Goal:** reduce false positives and improve real-world behavior.

Tasks:
1. Expand evaluation clips across devices/noise conditions.
2. Tune thresholds prioritizing low false-positive rate.
3. Improve preprocessing and landmark selection.
4. Add guardrails for low-quality captures (too quiet/clipped).

Exit criteria:
- Consistent performance across at least two device profiles.

## Phase 3 — Hardening (optional, 2–3 days)
**Goal:** make it stable and shippable as a demo.

Tasks:
1. Unit tests for DSP/fingerprint components.
2. Instrumentation test for permission + capture flow.
3. Performance pass (latency/memory).
4. Crash/edge-case handling.

Exit criteria:
- Stable repeated runs, predictable latency, no major UX dead ends.

---

## 5) Data & Legal Considerations

1. Acquire a legally sourced high-quality reference track.
2. Generate and ship **derived fingerprints**, not raw track audio.
3. Validate licensing posture for distributing derivative fingerprint assets.

---

## 6) Quality Targets

### User-facing targets
- Capture time: ~5s
- Processing time after capture: <=2s on mid-range device
- Total turnaround: <=7s

### Recognition targets (initial)
- True positive rate: >=90% in moderate noise
- False positive rate: <=2% on chosen negative set

Design principle: tune thresholds to minimize wrong YES results (false positives).

---

## 7) Evaluation Dataset Plan

Build a compact validation pack:

### Positives
- Multiple sections of "She Bop" (intro/verse/chorus)
- Different playback volumes
- Different distances from speaker
- Quiet and moderately noisy rooms

### Negatives
- Other Cyndi Lauper songs
- Similar 80s pop tracks
- Speech-only and ambient-noise clips

For each run, log:
- confidence score
- predicted label
- environment notes

---

## 8) App UX (MVP)

Single screen:
1. Header: "SheZam"
2. Main action button: **Listen**
3. Status text: listening/processing
4. Result card:
   - ✅ "This sounds like She Bop"
   - ❌ "This does not match She Bop"
5. Secondary action: **Try Again**

---

## 9) Concrete Engineering Backlog

1. Android project bootstrap (Compose + MVVM)
2. Audio capture module (`audio`)
3. Spectrogram + peak extraction module (`fingerprint`)
4. Hash DB format + asset loading (`match`)
5. Match scoring + thresholds (`match`)
6. Result orchestration in ViewModel (`ui`)
7. Test harness for offline evaluation
8. Unit tests for deterministic DSP functions

---

## 10) Risks and Mitigation

- **Noisy input causes misses**
  - Mitigate with robust peaks, quality gating, and retry messaging.
- **Similar songs cause false positives**
  - Mitigate by tightening thresholds and broadening negative set.
- **Device mic variation**
  - Mitigate with normalization and cross-device calibration clips.
- **Latency on low-end devices**
  - Mitigate with smaller FFT/window tuning and reduced hash density.

---

## 11) Definition of Done (MVP)

- App requests microphone permission and captures audio reliably.
- End-to-end identification runs on-device and offline.
- Result returned within target turnaround.
- Meets initial recognition targets on internal evaluation pack.
- No crashes in repeated manual test runs.

---

## 12) Immediate Next 5 Actions

1. Implement a tiny offline prototype to lock algorithm choices.
2. Create initial fingerprint database for "She Bop".
3. Stand up Compose UI with microphone capture.
4. Integrate matcher and display confidence-based outcomes.
5. Run first evaluation sweep and tune thresholds.
