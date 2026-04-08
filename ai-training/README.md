## Python Baseline Trainer

This folder contains a practical starter trainer for FailureLudo self-play JSONL data.

### 1) Install dependencies

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r ai-training/requirements.txt
```

### 2) Generate training data

From repo root:

```bash
./gradlew :game-engine:generateSelfPlayDataset \
  -Pepisodes=30000 \
  -PmaxPly=3000 \
  -Pseed=42 \
  -PexplorationEpsilon=0.15 \
  -Poutput=game-engine/build/self-play/train.jsonl
```

### 3) Train baseline policy

```bash
python ai-training/train_policy.py \
  --dataset game-engine/build/self-play/train.jsonl \
  --output-dir ai-training/artifacts/baseline-v1 \
  --export-json-path ai-training/artifacts/baseline-v1/policy_candidate.json \
  --epochs 8 \
  --batch-size 64 \
  --lr 1e-3 \
  --hidden-dim 256 \
  --min-candidates 2 \
  --positive-outcome-weight 2.0 \
  --negative-outcome-weight 1.0 \
  --outcome-margin-weight 0.5
```

### 4) Evaluate challenger and run promotion gate

One-command workflow (generate dataset + train candidate + evaluate + gate + promote):

```bash
./ai-training/run_training_cycle.sh \
  --python-bin .venv/bin/python \
  --seed-list 41,42,43 \
  --seed 42 \
  --dataset-episodes 30000 \
  --dataset-max-ply 3000 \
  --dataset-exploration-epsilon 0.15 \
  --train-epochs 8 \
  --train-min-candidates 2 \
  --train-positive-outcome-weight 2.0 \
  --train-negative-outcome-weight 1.0 \
  --train-outcome-margin-weight 0.5 \
  --arena-episodes 400 \
  --arena-max-ply 3000 \
  --promotion-threshold 0.55 \
  --minimum-decided-games 100 \
  --max-p95-decision-ms 5.0
```

Use `--skip-dataset` and/or `--skip-train` to reuse existing artifacts.
Use `--seed-list` to aggregate gate decisions across multiple seeds.
Use `--dataset-exploration-epsilon` to inject controlled exploration during dataset generation.
Use trainer knobs (`--train-min-candidates`, outcome weights, `--train-outcome-margin-weight`) for outcome-aware learning.

Policy arena evaluation (always exits successfully, writes summary JSON):

```bash
./gradlew :game-engine:evaluatePolicyArena \
  -Pepisodes=400 \
  -PmaxPly=3000 \
  -Pseed=42 \
  -PseedList=41,42,43 \
  -PchallengerPolicy=json \
  -PchallengerModel=ai-training/artifacts/baseline-v1/policy_candidate.json \
  -PbaselinePolicy=heuristic \
  -PpromotionThreshold=0.55 \
  -PminimumDecidedGames=100 \
  -PmaxP95DecisionMs=5.0 \
  -PsummaryOutput=game-engine/build/self-play/policy_arena_summary.json
```

Promotion gate (fails build when threshold is not met):

```bash
./gradlew :game-engine:runPolicyPromotionGate \
  -Pepisodes=400 \
  -PmaxPly=3000 \
  -Pseed=42 \
  -PseedList=41,42,43 \
  -PchallengerPolicy=json \
  -PchallengerModel=ai-training/artifacts/baseline-v1/policy_candidate.json \
  -PbaselinePolicy=heuristic \
  -PpromotionThreshold=0.55 \
  -PminimumDecidedGames=100 \
  -PmaxP95DecisionMs=5.0 \
  -PsummaryOutput=game-engine/build/self-play/policy_promotion_gate_summary.json \
  -PpromoteTo=app/src/main/assets/models/policy_baseline_v1.json
```

Supported policy values for arena tasks:

- `heuristic`
- `random`
- `json` (requires corresponding `-PchallengerModel` or `-PbaselineModel` path)

Arena note:

- In FREE_FOR_ALL mode, challenger seats rotate each episode so challenger/baseline seat exposure stays balanced.
- Gate outcomes can still vary by seed; validate on multiple seeds before promotion decisions.

### Output files

- `policy_baseline.pt`: PyTorch checkpoint.
- `metrics.json`: epoch-wise train/val metrics and run metadata.
- `policy_candidate.json`: challenger model exported by trainer.
- `policy_baseline_v1.json`: active on-device model path after promotion gate copy.
- `policy_arena_summary.json`: challenger-vs-baseline evaluation snapshot.
- `policy_promotion_gate_summary.json`: promotion gate decision + aggregate metrics (including `decisionReason`).
- `challengerMoveLatency` in summaries: challenger move latency metrics (`averageMs`, `p95Ms`, etc.).

### Notes

- The trainer uses only `MOVE` rows where legal options exist.
- Training target is `chosenMoveIndex` among legal move candidates.
- `--min-candidates 2` is recommended when evaluating decision quality (it filters out trivial single-option moves).
- Pure behavior cloning from deterministic heuristic-only data can plateau near heuristic parity; exploration + outcome-aware settings are intended for improvement experiments.
- This is a warm-start imitation baseline, not full AlphaZero/MCTS.
- If `policy_baseline_v1.json` is missing, the app falls back to heuristic bot logic.
