# Fingerprint collision validation

## Before tuning
reference_tokens=1100
- positive: votes[n=20 min=816 p50=854.5 p90=866 max=879 mean=850.55] confidence[min=0.742 p50=0.777 max=0.799]
- hard_negative: votes[n=20 min=343 p50=380.5 p90=408 max=419 mean=381.20] confidence[min=0.312 p50=0.346 max=0.381]

## After tuning
reference_tokens=342
- positive: votes[n=20 min=30 p50=39.5 p90=44 max=50 mean=39.25] confidence[min=0.088 p50=0.115 max=0.146]
- hard_negative: votes[n=20 min=5 p50=7.0 p90=12 max=13 mean=7.80] confidence[min=0.015 p50=0.020 max=0.038]
