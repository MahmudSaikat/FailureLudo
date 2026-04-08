# On-Device Policy Model Asset

Place the exported JSON model at:

- models/policy_baseline_v1.json

Expected format is produced by:

- ai-training/train_policy.py --export-json-path app/src/main/assets/models/policy_baseline_v1.json

If the file is missing or invalid, the app automatically falls back to the heuristic bot policy.
