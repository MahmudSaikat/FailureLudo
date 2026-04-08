#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

seed="42"
seed_list=""
dataset_episodes="30000"
dataset_max_ply="3000"
dataset_path="game-engine/build/self-play/train.jsonl"
dataset_exploration_epsilon="0"
mode="FREE_FOR_ALL"
active_colors="RED,BLUE,YELLOW,GREEN"

output_dir="ai-training/artifacts/baseline-v1"
candidate_path="ai-training/artifacts/baseline-v1/policy_candidate.json"
train_epochs="8"
train_batch_size="64"
train_lr="1e-3"
train_hidden_dim="256"
train_max_samples=""
train_min_candidates="1"
train_positive_outcome_weight="1.0"
train_negative_outcome_weight="1.0"
train_draw_outcome_weight="1.0"
train_outcome_margin_weight="0.0"
train_device="auto"

arena_episodes="400"
arena_max_ply="3000"
promotion_threshold="0.55"
minimum_decided_games="100"
max_p95_decision_ms=""
baseline_policy="heuristic"

arena_summary="game-engine/build/self-play/policy_arena_summary.json"
gate_summary="game-engine/build/self-play/policy_promotion_gate_summary.json"
promote_to="app/src/main/assets/models/policy_baseline_v1.json"

python_bin="python"
skip_dataset="0"
skip_train="0"

usage() {
    cat <<'EOF'
Usage: ai-training/run_training_cycle.sh [options]

Options:
  --python-bin PATH                 Python executable to use.
  --seed N                          Shared seed for dataset and evaluation runs.
  --seed-list CSV                   Optional seed list for multi-seed arena aggregation.
  --dataset-episodes N              Episodes for self-play dataset generation.
  --dataset-max-ply N               Max ply per self-play dataset episode.
  --dataset-path PATH               Dataset JSONL output path.
  --dataset-exploration-epsilon E   Epsilon-greedy move/dice exploration rate [0,1].
  --mode MODE                       FREE_FOR_ALL or TEAM.
  --active-colors CSV               Active colors CSV (ignored in TEAM mode).
  --output-dir PATH                 Training output directory.
  --candidate-path PATH             Export path for challenger JSON model.
  --train-epochs N                  Training epochs.
  --train-batch-size N              Training batch size.
  --train-lr FLOAT                  Training learning rate.
  --train-hidden-dim N              Policy hidden dimension.
  --train-max-samples N             Optional max MOVE samples consumed by trainer.
  --train-min-candidates N          Minimum legal candidate count to keep a row.
  --train-positive-outcome-weight W Loss weight for outcome > 0 rows.
  --train-negative-outcome-weight W Loss weight for outcome < 0 rows.
  --train-draw-outcome-weight W     Loss weight for outcome == 0 rows.
  --train-outcome-margin-weight W   Extra margin loss weight using outcome sign.
  --train-device DEVICE             Trainer device: auto|cpu|cuda|mps.
  --arena-episodes N                Episodes for arena evaluation/gate.
  --arena-max-ply N                 Max ply for arena episodes.
  --promotion-threshold FLOAT       Decided-game win-rate threshold.
  --minimum-decided-games N         Minimum decided games required by gate.
  --max-p95-decision-ms FLOAT       Optional max challenger p95 move latency (ms) gate.
  --baseline-policy POLICY          baseline policy: heuristic|random|json.
  --arena-summary PATH              Arena summary JSON output path.
  --gate-summary PATH               Gate summary JSON output path.
  --promote-to PATH                 Production model path on successful gate.
  --skip-dataset                    Reuse existing dataset file.
  --skip-train                      Reuse existing candidate model.
  --help                            Show this help.

Notes:
  - Challenger policy is always json from --candidate-path in this script.
  - runPolicyPromotionGate fails if gate conditions are not met.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --python-bin) python_bin="$2"; shift 2 ;;
        --seed) seed="$2"; shift 2 ;;
        --seed-list) seed_list="$2"; shift 2 ;;
        --dataset-episodes) dataset_episodes="$2"; shift 2 ;;
        --dataset-max-ply) dataset_max_ply="$2"; shift 2 ;;
        --dataset-path) dataset_path="$2"; shift 2 ;;
        --dataset-exploration-epsilon) dataset_exploration_epsilon="$2"; shift 2 ;;
        --mode) mode="$2"; shift 2 ;;
        --active-colors) active_colors="$2"; shift 2 ;;
        --output-dir) output_dir="$2"; shift 2 ;;
        --candidate-path) candidate_path="$2"; shift 2 ;;
        --train-epochs) train_epochs="$2"; shift 2 ;;
        --train-batch-size) train_batch_size="$2"; shift 2 ;;
        --train-lr) train_lr="$2"; shift 2 ;;
        --train-hidden-dim) train_hidden_dim="$2"; shift 2 ;;
        --train-max-samples) train_max_samples="$2"; shift 2 ;;
        --train-min-candidates) train_min_candidates="$2"; shift 2 ;;
        --train-positive-outcome-weight) train_positive_outcome_weight="$2"; shift 2 ;;
        --train-negative-outcome-weight) train_negative_outcome_weight="$2"; shift 2 ;;
        --train-draw-outcome-weight) train_draw_outcome_weight="$2"; shift 2 ;;
        --train-outcome-margin-weight) train_outcome_margin_weight="$2"; shift 2 ;;
        --train-device) train_device="$2"; shift 2 ;;
        --arena-episodes) arena_episodes="$2"; shift 2 ;;
        --arena-max-ply) arena_max_ply="$2"; shift 2 ;;
        --promotion-threshold) promotion_threshold="$2"; shift 2 ;;
        --minimum-decided-games) minimum_decided_games="$2"; shift 2 ;;
        --max-p95-decision-ms) max_p95_decision_ms="$2"; shift 2 ;;
        --baseline-policy) baseline_policy="$2"; shift 2 ;;
        --arena-summary) arena_summary="$2"; shift 2 ;;
        --gate-summary) gate_summary="$2"; shift 2 ;;
        --promote-to) promote_to="$2"; shift 2 ;;
        --skip-dataset) skip_dataset="1"; shift ;;
        --skip-train) skip_train="1"; shift ;;
        --help) usage; exit 0 ;;
        *)
            echo "Unknown option: $1" >&2
            usage
            exit 1
            ;;
    esac
done

cd "$REPO_ROOT"

if [[ "$skip_dataset" != "1" ]]; then
    ./gradlew :game-engine:generateSelfPlayDataset \
      -Pepisodes="$dataset_episodes" \
      -PmaxPly="$dataset_max_ply" \
      -Pseed="$seed" \
      -Pmode="$mode" \
      -PactiveColors="$active_colors" \
      -Ppolicy=heuristic \
      -PexplorationEpsilon="$dataset_exploration_epsilon" \
      -Poutput="$dataset_path"
else
    echo "Skipping dataset generation"
fi

if [[ "$skip_train" != "1" ]]; then
    train_args=(
      ai-training/train_policy.py
      --dataset "$dataset_path"
      --output-dir "$output_dir"
      --export-json-path "$candidate_path"
      --epochs "$train_epochs"
      --batch-size "$train_batch_size"
      --lr "$train_lr"
      --hidden-dim "$train_hidden_dim"
      --seed "$seed"
      --min-candidates "$train_min_candidates"
      --positive-outcome-weight "$train_positive_outcome_weight"
      --negative-outcome-weight "$train_negative_outcome_weight"
      --draw-outcome-weight "$train_draw_outcome_weight"
      --outcome-margin-weight "$train_outcome_margin_weight"
      --device "$train_device"
    )

    if [[ -n "$train_max_samples" ]]; then
      train_args+=(--max-samples "$train_max_samples")
    fi

    "$python_bin" "${train_args[@]}"
else
    echo "Skipping training"
fi

if [[ -n "$seed_list" ]]; then
  latency_args=()
  if [[ -n "$max_p95_decision_ms" ]]; then
    latency_args+=("-PmaxP95DecisionMs=$max_p95_decision_ms")
  fi

  ./gradlew :game-engine:evaluatePolicyArena \
    -Pepisodes="$arena_episodes" \
    -PmaxPly="$arena_max_ply" \
    -Pseed="$seed" \
    -PseedList="$seed_list" \
    -Pmode="$mode" \
    -PactiveColors="$active_colors" \
    -PchallengerPolicy=json \
    -PchallengerModel="$candidate_path" \
    -PbaselinePolicy="$baseline_policy" \
    -PpromotionThreshold="$promotion_threshold" \
    -PminimumDecidedGames="$minimum_decided_games" \
    -PsummaryOutput="$arena_summary" \
    "${latency_args[@]}"
else
  latency_args=()
  if [[ -n "$max_p95_decision_ms" ]]; then
    latency_args+=("-PmaxP95DecisionMs=$max_p95_decision_ms")
  fi

  ./gradlew :game-engine:evaluatePolicyArena \
    -Pepisodes="$arena_episodes" \
    -PmaxPly="$arena_max_ply" \
    -Pseed="$seed" \
    -Pmode="$mode" \
    -PactiveColors="$active_colors" \
    -PchallengerPolicy=json \
    -PchallengerModel="$candidate_path" \
    -PbaselinePolicy="$baseline_policy" \
    -PpromotionThreshold="$promotion_threshold" \
    -PminimumDecidedGames="$minimum_decided_games" \
    -PsummaryOutput="$arena_summary" \
    "${latency_args[@]}"
fi

if [[ -n "$seed_list" ]]; then
  latency_args=()
  if [[ -n "$max_p95_decision_ms" ]]; then
    latency_args+=("-PmaxP95DecisionMs=$max_p95_decision_ms")
  fi

  ./gradlew :game-engine:runPolicyPromotionGate \
    -Pepisodes="$arena_episodes" \
    -PmaxPly="$arena_max_ply" \
    -Pseed="$seed" \
    -PseedList="$seed_list" \
    -Pmode="$mode" \
    -PactiveColors="$active_colors" \
    -PchallengerPolicy=json \
    -PchallengerModel="$candidate_path" \
    -PbaselinePolicy="$baseline_policy" \
    -PpromotionThreshold="$promotion_threshold" \
    -PminimumDecidedGames="$minimum_decided_games" \
    -PsummaryOutput="$gate_summary" \
    -PpromoteTo="$promote_to" \
    "${latency_args[@]}"
else
  latency_args=()
  if [[ -n "$max_p95_decision_ms" ]]; then
    latency_args+=("-PmaxP95DecisionMs=$max_p95_decision_ms")
  fi

  ./gradlew :game-engine:runPolicyPromotionGate \
    -Pepisodes="$arena_episodes" \
    -PmaxPly="$arena_max_ply" \
    -Pseed="$seed" \
    -Pmode="$mode" \
    -PactiveColors="$active_colors" \
    -PchallengerPolicy=json \
    -PchallengerModel="$candidate_path" \
    -PbaselinePolicy="$baseline_policy" \
    -PpromotionThreshold="$promotion_threshold" \
    -PminimumDecidedGames="$minimum_decided_games" \
    -PsummaryOutput="$gate_summary" \
    -PpromoteTo="$promote_to" \
    "${latency_args[@]}"
fi

echo "Cycle complete."
echo "Arena summary: $arena_summary"
echo "Gate summary:  $gate_summary"
echo "Promoted model: $promote_to"