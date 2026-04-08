## AI Self-Play Training Workflow

This project now includes a headless self-play runner and JSONL dataset exporter in `:game-engine`.

### 1) Generate training data

Run the Gradle helper task:

```bash
./gradlew :game-engine:generateSelfPlayDataset \
  -Pepisodes=20000 \
  -PmaxPly=3000 \
  -Pseed=42 \
  -Pmode=FREE_FOR_ALL \
  -Ppolicy=heuristic \
  -PexplorationEpsilon=0.15 \
  -PactiveColors=RED,BLUE,YELLOW,GREEN \
  -Poutput=game-engine/build/self-play/train.jsonl
```

Or run the CLI directly with custom arguments:

```bash
./gradlew :game-engine:classes
java -cp game-engine/build/classes/kotlin/main com.failureludo.engine.SelfPlayDatasetCliKt \
  --episodes 5000 \
  --maxPly 3000 \
  --seed 7 \
  --mode FREE_FOR_ALL \
  --policy random \
  --explorationEpsilon 0.25 \
  --activeColors RED,BLUE \
  --output game-engine/build/self-play/train-small.jsonl
```

### 2) Dataset row contents (JSONL)

Each line includes:
- episode and ply metadata
- actor and dice value
- action type and payload
- chosen move index + legal move options (when applicable)
- outcome label from actor perspective (-1, 0, +1)
- encoded pre-action state snapshot

### 3) Suggested training loop (external Python)

1. Read JSONL lines and parse fields.
2. Build feature tensor from `state` object.
3. Build action targets from `action` and optional legal mask from `legalMoveOptions`.
4. Train policy network (and optional value head).
5. Evaluate candidate model versus heuristic policy.
6. Export promoted model for on-device inference.

Starter command (baseline imitation model):

```bash
python ai-training/train_policy.py \
  --dataset game-engine/build/self-play/train.jsonl \
  --output-dir ai-training/artifacts/baseline-v1 \
  --export-json-path ai-training/artifacts/baseline-v1/policy_candidate.json \
  --epochs 8 \
  --batch-size 64 \
  --min-candidates 2 \
  --positive-outcome-weight 2.0 \
  --negative-outcome-weight 1.0 \
  --outcome-margin-weight 0.5
```

Reference docs:
- `ai-training/README.md`
- `app/src/main/assets/models/README.md`

### 4) Runtime integration status

App bot selection is now routed through a policy boundary (`BotPolicyEngine`) with heuristic fallback, so model inference can be plugged in safely without changing turn orchestration.

When `app/src/main/assets/models/policy_baseline_v1.json` exists and is valid, bot move scoring uses the exported JSON model; otherwise it falls back to heuristic policy.

### 5) Automated evaluation and promotion gate

Single command full cycle:

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

Script behavior:
- generates self-play dataset
- trains and exports candidate model
- runs non-failing arena summary
- runs failing promotion gate and promotes on pass

Run arena evaluation (does not fail build):

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

Run promotion gate (fails build on gate failure and can promote artifact):

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

Gate logic:
- requires at least `minimumDecidedGames` non-draw outcomes
- requires challenger decided-game win rate >= `promotionThreshold`
- writes summary JSON with full aggregate metrics and decision
- in FREE_FOR_ALL arena runs, challenger seat assignment rotates so challenger and baseline receive balanced seat exposure over episodes
- promotion summary includes `decisionReason` for machine-readable failure/pass diagnosis
- use `seedList` for built-in multi-seed aggregation because single-seed gate outcomes can vary
- multi-seed summaries include per-seed diagnostics in `seedRuns`
- optional latency gate (`maxP95DecisionMs`) can fail promotion when challenger p95 move latency exceeds threshold
- behavior-cloning-only runs on heuristic-only datasets often converge near heuristic parity; use exploration plus outcome-aware trainer options for improvement attempts