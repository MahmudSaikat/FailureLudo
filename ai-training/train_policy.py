#!/usr/bin/env python3
"""
Baseline policy trainer for FailureLudo self-play JSONL datasets.

The model scores legal move candidates per state and is trained with
cross-entropy against chosenMoveIndex.
"""

from __future__ import annotations

import argparse
import json
import math
import random
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence, Tuple

import torch
import torch.nn as nn
import torch.nn.functional as F
from tqdm import tqdm


TURN_PHASES = [
    "WAITING_FOR_ROLL",
    "WAITING_FOR_PIECE_SELECTION",
    "NO_MOVES_AVAILABLE",
    "GAME_OVER",
]

MODES = ["FREE_FOR_ALL", "TEAM"]

POSITION_TYPES = ["HOME_BASE", "MAIN_TRACK", "HOME_COLUMN", "FINISHED"]

PLAYER_IDS = [1, 2, 3, 4]
PIECE_IDS = [0, 1, 2, 3]


@dataclass
class Sample:
    state_features: torch.Tensor  # [state_dim]
    candidate_features: torch.Tensor  # [num_candidates, candidate_dim]
    target_index: int
    outcome: int
    weight: float


@dataclass
class DatasetStats:
    total_rows: int = 0
    move_rows: int = 0
    kept_rows: int = 0
    invalid_json_rows: int = 0
    single_option_rows: int = 0
    multi_option_rows: int = 0
    positive_outcome_rows: int = 0
    negative_outcome_rows: int = 0
    draw_outcome_rows: int = 0


class PolicyScorer(nn.Module):
    def __init__(self, state_dim: int, candidate_dim: int, hidden_dim: int) -> None:
        super().__init__()
        self.state_encoder = nn.Sequential(
            nn.Linear(state_dim, hidden_dim),
            nn.ReLU(),
            nn.Linear(hidden_dim, hidden_dim),
            nn.ReLU(),
        )
        self.candidate_head = nn.Sequential(
            nn.Linear(hidden_dim + candidate_dim, hidden_dim),
            nn.ReLU(),
            nn.Linear(hidden_dim, 1),
        )

    def score_candidates(self, state: torch.Tensor, candidates: torch.Tensor) -> torch.Tensor:
        """
        state: [state_dim]
        candidates: [num_candidates, candidate_dim]
        returns logits: [num_candidates]
        """
        if state.dim() != 1:
            raise ValueError(f"Expected state shape [D], got {tuple(state.shape)}")
        if candidates.dim() != 2:
            raise ValueError(f"Expected candidates shape [N, C], got {tuple(candidates.shape)}")

        encoded = self.state_encoder(state.unsqueeze(0))  # [1, H]
        tiled = encoded.expand(candidates.size(0), -1)  # [N, H]
        merged = torch.cat([tiled, candidates], dim=1)  # [N, H + C]
        logits = self.candidate_head(merged).squeeze(-1)  # [N]
        return logits


def _dense_layer_to_json(linear: nn.Linear) -> Dict[str, object]:
    return {
        "weight": linear.weight.detach().cpu().tolist(),
        "bias": linear.bias.detach().cpu().tolist(),
    }


def export_model_to_json(
    model: PolicyScorer,
    output_path: Path,
    state_dim: int,
    candidate_dim: int,
    hidden_dim: int,
) -> None:
    state_linear_1 = model.state_encoder[0]
    state_linear_2 = model.state_encoder[2]
    candidate_linear_1 = model.candidate_head[0]
    candidate_linear_2 = model.candidate_head[2]

    if not all(
        isinstance(layer, nn.Linear)
        for layer in (state_linear_1, state_linear_2, candidate_linear_1, candidate_linear_2)
    ):
        raise ValueError("Unsupported network layout for JSON export.")

    payload = {
        "format": "failureludo-policy-mlp-v1",
        "state_dim": state_dim,
        "candidate_dim": candidate_dim,
        "hidden_dim": hidden_dim,
        "state_encoder": {
            "linear1": _dense_layer_to_json(state_linear_1),
            "linear2": _dense_layer_to_json(state_linear_2),
        },
        "candidate_head": {
            "linear1": _dense_layer_to_json(candidate_linear_1),
            "linear2": _dense_layer_to_json(candidate_linear_2),
        },
    }

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", encoding="utf-8") as handle:
        json.dump(payload, handle, separators=(",", ":"))


def one_hot(value: str, choices: Sequence[str]) -> List[float]:
    return [1.0 if value == option else 0.0 for option in choices]


def encode_piece_position(position: Dict[str, object]) -> List[float]:
    pos_type = str(position.get("type", "HOME_BASE"))
    index = float(position.get("index", 0) or 0)
    step = float(position.get("step", 0) or 0)

    return (
        one_hot(pos_type, POSITION_TYPES)
        + [index / 51.0 if pos_type == "MAIN_TRACK" else 0.0]
        + [step / 5.0 if pos_type == "HOME_COLUMN" else 0.0]
    )


def encode_state(state: Dict[str, object]) -> List[float]:
    features: List[float] = []

    move_counter = float(state.get("moveCounter", 0) or 0)
    features.append(min(move_counter / 3000.0, 1.0))

    current_player = int(state.get("currentPlayerId", 1) or 1)
    features.extend([1.0 if current_player == pid else 0.0 for pid in PLAYER_IDS])

    features.extend(one_hot(str(state.get("turnPhase", "WAITING_FOR_ROLL")), TURN_PHASES))
    features.extend(one_hot(str(state.get("mode", "FREE_FOR_ALL")), MODES))

    last_dice = state.get("lastDice")
    if isinstance(last_dice, dict):
        features.append(1.0)
        features.append(float(last_dice.get("value", 0) or 0) / 6.0)
        features.append(float(last_dice.get("rollCount", 0) or 0) / 3.0)
    else:
        features.extend([0.0, 0.0, 0.0])

    shared_team = set(int(value) for value in (state.get("sharedTeamDiceEnabled") or []))
    features.extend([1.0 if team in shared_team else 0.0 for team in (0, 1)])

    winners = set(int(value) for value in (state.get("winners") or []))
    features.extend([1.0 if pid in winners else 0.0 for pid in PLAYER_IDS])

    dice_by_player: Dict[int, Optional[int]] = {pid: None for pid in PLAYER_IDS}
    for row in (state.get("diceByPlayer") or []):
        if not isinstance(row, dict):
            continue
        pid = int(row.get("playerId", 0) or 0)
        if pid in dice_by_player:
            raw = row.get("dice")
            dice_by_player[pid] = int(raw) if raw is not None else None
    for pid in PLAYER_IDS:
        dice = dice_by_player[pid]
        features.append(1.0 if dice is not None else 0.0)
        features.append((float(dice) / 6.0) if dice is not None else 0.0)

    entered_by_player: Dict[int, bool] = {pid: False for pid in PLAYER_IDS}
    for row in (state.get("enteredBoardFlags") or []):
        if not isinstance(row, dict):
            continue
        pid = int(row.get("playerId", 0) or 0)
        if pid in entered_by_player:
            entered_by_player[pid] = bool(row.get("entered", False))
    for pid in PLAYER_IDS:
        features.append(1.0 if entered_by_player[pid] else 0.0)

    players_by_id: Dict[int, Dict[str, object]] = {}
    for player in (state.get("players") or []):
        if not isinstance(player, dict):
            continue
        pid = int(player.get("id", 0) or 0)
        if pid in PLAYER_IDS:
            players_by_id[pid] = player

    max_move_counter = max(1.0, move_counter)
    for pid in PLAYER_IDS:
        player = players_by_id.get(pid) or {}
        features.append(1.0 if bool(player.get("isActive", False)) else 0.0)
        player_type = str(player.get("type", "HUMAN"))
        features.extend([1.0 if player_type == "HUMAN" else 0.0, 1.0 if player_type == "BOT" else 0.0])

        pieces_by_id: Dict[int, Dict[str, object]] = {}
        for piece in (player.get("pieces") or []):
            if not isinstance(piece, dict):
                continue
            piece_id = int(piece.get("id", 0) or 0)
            if piece_id in PIECE_IDS:
                pieces_by_id[piece_id] = piece

        for piece_id in PIECE_IDS:
            piece = pieces_by_id.get(piece_id) or {}
            position = piece.get("position") if isinstance(piece.get("position"), dict) else {"type": "HOME_BASE"}
            features.extend(encode_piece_position(position))
            last_moved = float(piece.get("lastMovedAt", 0) or 0)
            features.append(min(last_moved / max_move_counter, 1.0))

    return features


def encode_candidate(option: Dict[str, object]) -> List[float]:
    moving_player_id = int(option.get("movingPlayerId", 1) or 1)
    piece_id = int(option.get("pieceId", 0) or 0)
    defer_home_entry = bool(option.get("deferHomeEntry", False))

    return (
        [1.0 if moving_player_id == pid else 0.0 for pid in PLAYER_IDS]
        + [1.0 if piece_id == pid else 0.0 for pid in PIECE_IDS]
        + [1.0 if defer_home_entry else 0.0]
    )


def load_samples(
    dataset_path: Path,
    max_samples: Optional[int] = None,
    min_candidates: int = 1,
    positive_outcome_weight: float = 1.0,
    negative_outcome_weight: float = 1.0,
    draw_outcome_weight: float = 1.0,
) -> Tuple[List[Sample], DatasetStats]:
    samples: List[Sample] = []
    stats = DatasetStats()

    if min_candidates < 1:
        raise ValueError("min_candidates must be >= 1.")

    if positive_outcome_weight <= 0 or negative_outcome_weight <= 0 or draw_outcome_weight <= 0:
        raise ValueError("Outcome weights must be > 0.")

    with dataset_path.open("r", encoding="utf-8") as handle:
        for raw_line in handle:
            stats.total_rows += 1
            line = raw_line.strip()
            if not line:
                continue

            try:
                row = json.loads(line)
            except json.JSONDecodeError:
                stats.invalid_json_rows += 1
                if stats.invalid_json_rows <= 3:
                    print(f"warning: skipped malformed JSON row at line={stats.total_rows}")
                continue
            if row.get("actionType") != "MOVE":
                continue
            stats.move_rows += 1

            legal_options = row.get("legalMoveOptions") or []
            chosen_index = row.get("chosenMoveIndex")
            state = row.get("state")

            if not isinstance(state, dict):
                continue
            if not isinstance(legal_options, list) or not legal_options:
                continue
            if len(legal_options) < min_candidates:
                continue
            if chosen_index is None:
                continue

            target = int(chosen_index)
            if target < 0 or target >= len(legal_options):
                continue

            if len(legal_options) > 1:
                stats.multi_option_rows += 1
            else:
                stats.single_option_rows += 1

            state_vec = torch.tensor(encode_state(state), dtype=torch.float32)
            candidate_mat = torch.tensor(
                [encode_candidate(option) for option in legal_options],
                dtype=torch.float32,
            )
            outcome = int(row.get("outcome", 0) or 0)

            if outcome > 0:
                weight = positive_outcome_weight
                stats.positive_outcome_rows += 1
            elif outcome < 0:
                weight = negative_outcome_weight
                stats.negative_outcome_rows += 1
            else:
                weight = draw_outcome_weight
                stats.draw_outcome_rows += 1

            samples.append(
                Sample(
                    state_features=state_vec,
                    candidate_features=candidate_mat,
                    target_index=target,
                    outcome=outcome,
                    weight=weight,
                )
            )
            stats.kept_rows += 1

            if max_samples is not None and len(samples) >= max_samples:
                break

    if not samples:
        raise ValueError("No usable MOVE rows were found in dataset.")

    return samples, stats


def split_samples(
    samples: List[Sample],
    val_split: float,
    seed: int,
) -> Tuple[List[Sample], List[Sample]]:
    if not 0.0 <= val_split < 1.0:
        raise ValueError("val_split must be in [0.0, 1.0).")

    indices = list(range(len(samples)))
    rng = random.Random(seed)
    rng.shuffle(indices)

    val_count = int(len(samples) * val_split)
    val_indices = set(indices[:val_count])

    train_samples = [samples[i] for i in range(len(samples)) if i not in val_indices]
    val_samples = [samples[i] for i in range(len(samples)) if i in val_indices]

    if not train_samples:
        raise ValueError("Training split is empty. Lower val_split or increase samples.")

    return train_samples, val_samples


def iter_batches(indices: Sequence[int], batch_size: int) -> Iterable[Sequence[int]]:
    for start in range(0, len(indices), batch_size):
        yield indices[start : start + batch_size]


def evaluate(
    model: PolicyScorer,
    samples: Sequence[Sample],
    device: torch.device,
) -> Dict[str, float]:
    if not samples:
        return {"loss": 0.0, "accuracy": 0.0, "decision_accuracy": 0.0, "decision_examples": 0.0}

    model.eval()
    total_loss = 0.0
    total_correct = 0
    decision_examples = 0
    decision_correct = 0

    with torch.no_grad():
        for sample in samples:
            state = sample.state_features.to(device)
            candidates = sample.candidate_features.to(device)
            logits = model.score_candidates(state, candidates)

            target = torch.tensor([sample.target_index], dtype=torch.long, device=device)
            loss = F.cross_entropy(logits.unsqueeze(0), target)
            total_loss += float(loss.item())

            prediction = int(torch.argmax(logits).item())
            if prediction == sample.target_index:
                total_correct += 1
                if candidates.size(0) > 1:
                    decision_correct += 1
            if candidates.size(0) > 1:
                decision_examples += 1

    return {
        "loss": total_loss / len(samples),
        "accuracy": total_correct / len(samples),
        "decision_accuracy": (decision_correct / decision_examples) if decision_examples else 0.0,
        "decision_examples": float(decision_examples),
    }


def train(
    model: PolicyScorer,
    train_samples: List[Sample],
    val_samples: List[Sample],
    device: torch.device,
    epochs: int,
    batch_size: int,
    learning_rate: float,
    weight_decay: float,
    outcome_margin_weight: float,
    seed: int,
    show_batch_progress: bool,
) -> List[Dict[str, float]]:
    optimizer = torch.optim.AdamW(model.parameters(), lr=learning_rate, weight_decay=weight_decay)
    history: List[Dict[str, float]] = []

    rng = random.Random(seed)

    for epoch in range(1, epochs + 1):
        epoch_percent = (float(epoch) / float(max(1, epochs))) * 100.0
        print(f"epoch {epoch}/{epochs} ({epoch_percent:.1f}%) started")

        model.train()
        indices = list(range(len(train_samples)))
        rng.shuffle(indices)

        total_loss = 0.0
        total_margin_loss = 0.0
        total_combined_loss = 0.0
        total_weighted_loss = 0.0
        total_correct = 0
        total_examples = 0
        total_decision_examples = 0
        total_decision_correct = 0

        progress = tqdm(
            iter_batches(indices, batch_size),
            total=math.ceil(len(indices) / batch_size),
            desc=f"epoch {epoch}/{epochs}",
            disable=not show_batch_progress,
            leave=False,
            dynamic_ncols=True,
        )
        for batch in progress:
            optimizer.zero_grad(set_to_none=True)

            batch_loss: Optional[torch.Tensor] = None
            for sample_index in batch:
                sample = train_samples[sample_index]
                state = sample.state_features.to(device)
                candidates = sample.candidate_features.to(device)

                logits = model.score_candidates(state, candidates)
                target = torch.tensor([sample.target_index], dtype=torch.long, device=device)
                loss = F.cross_entropy(logits.unsqueeze(0), target)

                margin_loss = torch.tensor(0.0, device=device)
                if outcome_margin_weight > 0 and sample.outcome != 0:
                    sign = 1.0 if sample.outcome > 0 else -1.0
                    chosen_logit = logits[sample.target_index]
                    margin_loss = F.softplus(-sign * chosen_logit)

                combined_loss = loss + (outcome_margin_weight * margin_loss)
                weighted_loss = combined_loss * sample.weight

                batch_loss = weighted_loss if batch_loss is None else (batch_loss + weighted_loss)
                total_loss += float(loss.item())
                total_margin_loss += float(margin_loss.item())
                total_combined_loss += float(combined_loss.item())
                total_weighted_loss += float(weighted_loss.item())
                total_examples += 1

                prediction = int(torch.argmax(logits).item())
                if prediction == sample.target_index:
                    total_correct += 1
                    if candidates.size(0) > 1:
                        total_decision_correct += 1
                if candidates.size(0) > 1:
                    total_decision_examples += 1

            if batch_loss is None:
                continue
            batch_loss = batch_loss / max(1, len(batch))
            batch_loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
            optimizer.step()

            progress.set_postfix(
                train_loss=f"{(total_weighted_loss / max(1, total_examples)):.4f}",
                train_acc=f"{(total_correct / max(1, total_examples)):.3f}",
                decision_acc=f"{(total_decision_correct / max(1, total_decision_examples)):.3f}",
            )

        train_metrics = {
            "loss": total_loss / max(1, total_examples),
            "margin_loss": total_margin_loss / max(1, total_examples),
            "combined_loss": total_combined_loss / max(1, total_examples),
            "weighted_loss": total_weighted_loss / max(1, total_examples),
            "accuracy": total_correct / max(1, total_examples),
            "decision_accuracy": total_decision_correct / max(1, total_decision_examples),
            "decision_examples": float(total_decision_examples),
        }
        val_metrics = evaluate(model, val_samples, device)

        summary = {
            "epoch": float(epoch),
            "train_loss": train_metrics["loss"],
            "train_margin_loss": train_metrics["margin_loss"],
            "train_combined_loss": train_metrics["combined_loss"],
            "train_weighted_loss": train_metrics["weighted_loss"],
            "train_accuracy": train_metrics["accuracy"],
            "train_decision_accuracy": train_metrics["decision_accuracy"],
            "val_loss": val_metrics["loss"],
            "val_accuracy": val_metrics["accuracy"],
            "val_decision_accuracy": val_metrics["decision_accuracy"],
            "val_decision_examples": val_metrics["decision_examples"],
        }
        history.append(summary)

        print(
            "epoch {epoch}/{epochs} ({epoch_percent:.1f}%): "
            "train_loss={train_loss:.4f} train_margin_loss={train_margin_loss:.4f} "
            "train_combined_loss={train_combined_loss:.4f} train_weighted_loss={train_weighted_loss:.4f} "
            "train_acc={train_acc:.3f} train_decision_acc={train_decision_acc:.3f} "
            "val_loss={val_loss:.4f} val_acc={val_acc:.3f} val_decision_acc={val_decision_acc:.3f}".format(
                epoch=epoch,
                epochs=epochs,
                epoch_percent=epoch_percent,
                train_loss=summary["train_loss"],
                train_margin_loss=summary["train_margin_loss"],
                train_combined_loss=summary["train_combined_loss"],
                train_weighted_loss=summary["train_weighted_loss"],
                train_acc=summary["train_accuracy"],
                train_decision_acc=summary["train_decision_accuracy"],
                val_loss=summary["val_loss"],
                val_acc=summary["val_accuracy"],
                val_decision_acc=summary["val_decision_accuracy"],
            )
        )

    return history


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train baseline policy on FailureLudo self-play JSONL dataset.")
    parser.add_argument("--dataset", type=Path, required=True, help="Path to JSONL file produced by self-play exporter.")
    parser.add_argument("--output-dir", type=Path, default=Path("ai-training/artifacts/baseline"))
    parser.add_argument("--epochs", type=int, default=6)
    parser.add_argument("--batch-size", type=int, default=64)
    parser.add_argument("--lr", type=float, default=1e-3)
    parser.add_argument("--weight-decay", type=float, default=1e-4)
    parser.add_argument("--hidden-dim", type=int, default=256)
    parser.add_argument("--val-split", type=float, default=0.1)
    parser.add_argument("--max-samples", type=int, default=None)
    parser.add_argument("--min-candidates", type=int, default=1)
    parser.add_argument("--positive-outcome-weight", type=float, default=1.0)
    parser.add_argument("--negative-outcome-weight", type=float, default=1.0)
    parser.add_argument("--draw-outcome-weight", type=float, default=1.0)
    parser.add_argument("--outcome-margin-weight", type=float, default=0.0)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument(
        "--export-json-path",
        type=Path,
        default=None,
        help="Optional path to export app-readable JSON model weights.",
    )
    parser.add_argument(
        "--device",
        type=str,
        default="auto",
        choices=["auto", "cpu", "cuda", "mps"],
        help="Training device preference.",
    )
    parser.add_argument(
        "--show-batch-progress",
        action="store_true",
        help="Force detailed tqdm batch progress bars (interactive terminals already show these by default).",
    )
    return parser.parse_args()


def resolve_device(device_arg: str) -> torch.device:
    if device_arg == "cpu":
        return torch.device("cpu")
    if device_arg == "cuda":
        return torch.device("cuda")
    if device_arg == "mps":
        return torch.device("mps")

    if torch.cuda.is_available():
        return torch.device("cuda")
    if getattr(torch.backends, "mps", None) and torch.backends.mps.is_available():
        return torch.device("mps")
    return torch.device("cpu")


def main() -> None:
    args = parse_args()

    if not args.dataset.exists():
        raise FileNotFoundError(f"Dataset not found: {args.dataset}")

    random.seed(args.seed)
    torch.manual_seed(args.seed)

    device = resolve_device(args.device)
    print(f"device={device}")

    started_at = time.time()
    samples, dataset_stats = load_samples(
        args.dataset,
        max_samples=args.max_samples,
        min_candidates=args.min_candidates,
        positive_outcome_weight=args.positive_outcome_weight,
        negative_outcome_weight=args.negative_outcome_weight,
        draw_outcome_weight=args.draw_outcome_weight,
    )
    print(
        "dataset_stats total_rows={total_rows} move_rows={move_rows} kept_rows={kept_rows} "
        "invalid_json_rows={invalid_json_rows} "
        "single_option={single_option} multi_option={multi_option} "
        "positive_outcomes={positive} negative_outcomes={negative} draws={draws}".format(
            total_rows=dataset_stats.total_rows,
            move_rows=dataset_stats.move_rows,
            kept_rows=dataset_stats.kept_rows,
            invalid_json_rows=dataset_stats.invalid_json_rows,
            single_option=dataset_stats.single_option_rows,
            multi_option=dataset_stats.multi_option_rows,
            positive=dataset_stats.positive_outcome_rows,
            negative=dataset_stats.negative_outcome_rows,
            draws=dataset_stats.draw_outcome_rows,
        )
    )
    train_samples, val_samples = split_samples(samples, args.val_split, seed=args.seed)

    state_dim = int(train_samples[0].state_features.shape[0])
    candidate_dim = int(train_samples[0].candidate_features.shape[1])

    model = PolicyScorer(state_dim=state_dim, candidate_dim=candidate_dim, hidden_dim=args.hidden_dim).to(device)
    history = train(
        model=model,
        train_samples=train_samples,
        val_samples=val_samples,
        device=device,
        epochs=args.epochs,
        batch_size=args.batch_size,
        learning_rate=args.lr,
        weight_decay=args.weight_decay,
        outcome_margin_weight=args.outcome_margin_weight,
        seed=args.seed,
        show_batch_progress=args.show_batch_progress or sys.stdout.isatty(),
    )

    args.output_dir.mkdir(parents=True, exist_ok=True)

    checkpoint_path = args.output_dir / "policy_baseline.pt"
    metrics_path = args.output_dir / "metrics.json"

    torch.save(
        {
            "model_state_dict": model.state_dict(),
            "state_dim": state_dim,
            "candidate_dim": candidate_dim,
            "hidden_dim": args.hidden_dim,
            "seed": args.seed,
            "dataset": str(args.dataset),
            "train_samples": len(train_samples),
            "val_samples": len(val_samples),
        },
        checkpoint_path,
    )

    if args.export_json_path is not None:
        export_model_to_json(
            model=model,
            output_path=args.export_json_path,
            state_dim=state_dim,
            candidate_dim=candidate_dim,
            hidden_dim=args.hidden_dim,
        )

    final_metrics = history[-1] if history else {}
    with metrics_path.open("w", encoding="utf-8") as handle:
        json.dump(
            {
                "history": history,
                "final": final_metrics,
                "train_samples": len(train_samples),
                "val_samples": len(val_samples),
                "dataset_stats": {
                    "total_rows": dataset_stats.total_rows,
                    "move_rows": dataset_stats.move_rows,
                    "kept_rows": dataset_stats.kept_rows,
                    "invalid_json_rows": dataset_stats.invalid_json_rows,
                    "single_option_rows": dataset_stats.single_option_rows,
                    "multi_option_rows": dataset_stats.multi_option_rows,
                    "positive_outcome_rows": dataset_stats.positive_outcome_rows,
                    "negative_outcome_rows": dataset_stats.negative_outcome_rows,
                    "draw_outcome_rows": dataset_stats.draw_outcome_rows,
                },
                "training_args": {
                    "min_candidates": args.min_candidates,
                    "positive_outcome_weight": args.positive_outcome_weight,
                    "negative_outcome_weight": args.negative_outcome_weight,
                    "draw_outcome_weight": args.draw_outcome_weight,
                    "outcome_margin_weight": args.outcome_margin_weight,
                },
                "state_dim": state_dim,
                "candidate_dim": candidate_dim,
                "elapsed_seconds": time.time() - started_at,
            },
            handle,
            indent=2,
        )

    print(f"saved_checkpoint={checkpoint_path}")
    print(f"saved_metrics={metrics_path}")
    if args.export_json_path is not None:
        print(f"saved_json_model={args.export_json_path}")


if __name__ == "__main__":
    main()
