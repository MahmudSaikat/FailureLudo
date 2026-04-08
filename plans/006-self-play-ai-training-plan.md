## Plan: Self-Play AI Training and Real-Play Integration

Train a production bot from self-play data, then run the trained policy during live bot turns on device with safe fallbacks.

## Implementation Status (2026-04-07)
- [x] Headless self-play simulator added in engine module.
- [x] Episode and training-sample structures added.
- [x] Policy abstraction added with heuristic and random defaults.
- [x] Self-play runner tests added and passing.
- [x] Dataset export pipeline (JSONL) added.
- [x] Runtime bot policy boundary integrated (model-first scorer hook + heuristic fallback).
- [x] Bot turn automation hardened for restore/phase edge cases (no-moves and bot selection resume).
- [x] Heuristic baseline refreshed to score legal piece + defer-home-entry candidates under current rules.
- [x] Python baseline trainer starter added (JSONL -> policy checkpoint + metrics).
- [x] Baseline model training loop added (imitation warm start).
- [x] Runtime model inference path integrated (JSON model asset scorer + heuristic fallback).
- [x] Evaluation and promotion gates automated.
- [x] One-command training cycle script added and validated (`ai-training/run_training_cycle.sh`).
- [x] Multi-seed gate aggregation support added (`seedList`) for robust promotion decisions.
- [x] Large-scale dataset generation hardened by streaming JSONL writes; 10k episodes run validated (3,468,531 samples).
- [x] Training controls expanded for tuning (`--min-candidates`, outcome weights, `--outcome-margin-weight`, optional `--max-samples`, `--train-device`).
- [x] Exploration dataset support added (`--explorationEpsilon` in dataset CLI and `-PexplorationEpsilon` wiring in Gradle + cycle script).
- [x] Smoke-scale multi-seed gate pass observed previously, but not stable under larger validation runs.
- [x] Optional latency gate added (`maxP95DecisionMs`) with challenger move latency metrics in gate summaries.
- [ ] Robust multi-seed promotion gate is still failing in larger runs (for example 5 seeds x 80 episodes: 0.47 to 0.4725 decided win rate vs 0.55 threshold).
- [ ] Single-seed performance remains variable and should not be used alone for promotion.

## Phase 1 - Data Contract and Export
1. Define canonical training example schema:
- Encoded state (Markov-sufficient fields)
- Legal-action mask
- Chosen action
- Final outcome from actor perspective

2. Add dataset writer:
- Export episodes from self-play to disk (JSONL first, optional Parquet later).
- Include metadata: seed, policy version, ruleset version, timestamp.

3. Add reproducibility controls:
- Deterministic run config via fixed seed and fixed episode count.

## Phase 2 - Baseline Model (Imitation Warm Start)
1. Train a first model to imitate heuristic policy actions:
- Input: state encoding.
- Target: action selected by heuristic policy from legal actions.

2. Add value head warm start (optional):
- Predict final win/loss from state.

3. Produce first deployable checkpoint and evaluate vs heuristic baseline.

## Phase 3 - Self-Play Improvement Loop
1. Run iterative loop:
- Generate self-play with current model + exploration.
- Train on latest + replay buffer data.
- Evaluate challenger vs current production model.

2. Promotion gate:
- Promote only if challenger wins above threshold (for example >= 55% over N matches).

3. Keep model lineage:
- Version model artifacts and attach training config + metrics.

## Phase 4 - Runtime Integration in App
1. Add bot inference boundary:
- Introduce a `BotPolicyEngine` interface in app layer.
- Provide implementations:
  - `HeuristicBotPolicyEngine` (existing behavior)
  - `ModelBotPolicyEngine` (new ML inference)

2. Integrate into turn flow:
- Replace direct heuristic selection in bot turn path with policy engine call.
- Keep heuristic fallback if inference fails or outputs invalid action.

3. Preserve game legality:
- Mask illegal actions before final selection.
- If top action is illegal after masking, use next legal action.

## Phase 5 - Evaluation and Guardrails
1. Offline evaluation:
- Win rate vs current heuristic across diverse seeds and both game modes.
- Average game length, no-move frequency, and timeout/crash rate.

2. On-device validation:
- Inference latency budget per decision.
- Memory footprint budget.
- Battery impact sampling.

3. Safety checks:
- Always legal action.
- No app crash when model unavailable.
- Seamless fallback to heuristic policy.

## Phase 6 - Release Workflow
1. Ship staged:
- Internal toggle to switch heuristic/model bot.
- Canary build before wide rollout.

2. Telemetry:
- Bot decision latency.
- Fallback rate.
- Match outcomes by bot version.

3. Rollback:
- One-flag rollback to heuristic bot.

## Real-Play Behavior (Target)
1. Dice roll remains game-random (engine-owned), not model-owned.
2. When bot must choose a move:
- Encode current state.
- Build legal candidate actions.
- Model scores candidates.
- Apply legal mask and select best legal action.
3. If model cannot produce a valid decision:
- Use heuristic chooser immediately.

## Existing Code Anchors
- Self-play entry points:
  - `game-engine/src/main/kotlin/com/failureludo/engine/SelfPlayRunner.kt`
- Dataset export + CLI:
  - `game-engine/src/main/kotlin/com/failureludo/engine/SelfPlayDatasetExporter.kt`
  - `game-engine/src/main/kotlin/com/failureludo/engine/SelfPlayDatasetCli.kt`
- Automated evaluation and promotion:
  - `game-engine/src/main/kotlin/com/failureludo/engine/PolicyModel.kt`
  - `game-engine/src/main/kotlin/com/failureludo/engine/PolicyArenaEvaluator.kt`
  - `game-engine/src/main/kotlin/com/failureludo/engine/PolicyPromotionGateCli.kt`
  - `game-engine/build.gradle.kts` (`evaluatePolicyArena`, `runPolicyPromotionGate`)
- Baseline trainer + export:
  - `ai-training/train_policy.py`
  - `ai-training/README.md`
- Runtime bot policy boundary:
  - `app/src/main/kotlin/com/failureludo/ai/BotPolicyEngine.kt`
  - `app/src/main/kotlin/com/failureludo/ai/PolicyFeatureEncoder.kt`
  - `app/src/main/kotlin/com/failureludo/ai/JsonPolicyModel.kt`
  - `app/src/main/kotlin/com/failureludo/ai/AssetJsonPolicyMoveScorer.kt`
  - `app/src/main/kotlin/com/failureludo/viewmodel/GameViewModel.kt` (`botSelectPiece`)

## Verification Checklist
- [x] Generate 10k+ self-play episodes without illegal transitions.
- [x] Train baseline imitation model and export artifact.
- [x] Integrate model inference with legal-mask guard.
- [ ] Demonstrate stable win-rate lift vs heuristic baseline under robust multi-seed gate settings.
- [ ] Validate runtime latency on representative devices.