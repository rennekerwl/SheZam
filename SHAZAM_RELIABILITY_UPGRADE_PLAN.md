# Shazam-Style Reliability Upgrade Plan for SheZam

This roadmap prioritizes high-impact steps to improve recognition in noisy, real-world phone playback conditions while keeping the system lightweight and understandable.

## Current constraints (why false negatives happen)

The current MVP intentionally uses a simple algorithm:
- top-5 global bins per frame
- adjacent-bin pairing only
- fixed `deltaFrames = 1`
- exact token matching
- confidence = strongest-offset-votes / observed-tokens

This is easy to reason about, but brittle to:
- room acoustics
- codec/compression artifacts
- slight playback-speed or pitch differences
- phone speaker/mic coloration

## Phase 1 — Quick wins (1–2 days)

### 1) Add spectral preprocessing and local-peak selection
**Change**
- Apply a Hann window before FFT.
- Use log-magnitude compression (`log(1 + magnitude)`).
- Replace “top N bins globally” with local maxima in a small time-frequency neighborhood.

**Why**
- Reduces leakage and dynamic-range domination by a few loud components.
- Produces more stable/meaningful peaks under noise.

**Acceptance criteria**
- Positive validation clips show improved median confidence vs baseline.
- Fewer runs with confidence < 0.2 when song is truly present.

### 2) Increase token redundancy via anchor-target fanout
**Change**
- For each anchor peak, pair with multiple target peaks in a future target zone.
- Encode hash as `(f1, f2, dt)` with quantized bins.

**Why**
- This is the core robustness pattern used by Shazam-style systems.
- More valid matching opportunities despite missing/corrupted peaks.

**Acceptance criteria**
- Strongest offset vote histograms show clearer spikes on true matches.

### 3) Introduce coarse quantization and tolerance
**Change**
- Quantize frequency bins (e.g., group by 2–4 bins).
- Quantize time delta to small buckets.

**Why**
- Avoids hard failures due to tiny spectral drift.

**Acceptance criteria**
- Same song recorded from different phones/speakers keeps confidence stable.

## Phase 2 — Better scoring and decisioning (2–3 days)

### 4) Improve scoring beyond a single ratio
**Change**
Use a composite score, e.g.:
- strongest offset votes
- top-2 offset margin
- matched unique hashes
- match density over time

**Why**
- Reduces false positives and false negatives from edge cases.

### 5) Adaptive thresholds from validation set
**Change**
- Build a small eval set: positives, hard negatives (other 80s pop), and noise-only captures.
- Set threshold(s) from precision/recall targets instead of a fixed 0.55 guess.

**Why**
- Threshold should be data-driven per algorithm variant.

## Phase 3 — Production hardening (3–5 days)

### 6) Time-frequency band limiting + denoising heuristics
**Change**
- Focus on robust mid bands (example: roughly 300 Hz–5 kHz bins).
- Optional simple high-pass / noise-floor pruning.

**Why**
- Drops low-frequency rumble and very-high-frequency unstable content.

### 7) Multi-window query strategy
**Change**
- Run matching on overlapping subwindows of the 5-second capture and aggregate.

**Why**
- If one part of the capture is noisy/silent, others may still match.

### 8) Performance optimizations
**Change**
- Replace naive DFT with FFT implementation.
- Precompute windows and reuse buffers.

**Why**
- More compute budget for better fingerprints without UI lag.

## Suggested implementation order (highest ROI first)
1. Hann window + log magnitude + local maxima peaks.
2. Anchor-target fanout hashing.
3. Quantization/tolerance.
4. Composite scoring.
5. Validation-driven thresholds.
6. Band limiting, subwindow aggregation, then performance tuning.

## Validation protocol (must-have)

Create a repeatable benchmark directory structure:
- `validation/positives/` (same song from multiple devices/distances/volumes)
- `validation/hard_negatives/` (similar genre/era songs)
- `validation/noise/` (speech, traffic, cafe, silence)

Track these metrics for each algorithm variant:
- True positive rate @ threshold
- False positive rate @ threshold
- Median confidence for positives
- 5th percentile confidence for positives
- Worst-case latency on device

Promote a variant only when all target metrics improve versus baseline.

## Practical threshold guidance for your current numbers

If you currently observe true positives around confidence `~0.10–0.25`, lowering threshold alone may increase detections but will likely increase false positives.

Use threshold reduction only as a temporary diagnostic step while implementing stronger hashing/scoring.
