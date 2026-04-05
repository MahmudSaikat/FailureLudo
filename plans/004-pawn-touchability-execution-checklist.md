# 004 - Pawn Touchability Execution Checklist

This checklist is execution-focused and maps directly to 004-pawn-touchability-and-selection-reliability-plan.md.

## Phase 0 - Setup and Baseline
- [ ] Confirm scope lock: interaction reliability plus visual pawn scaling, no game-rule changes.
- [ ] Confirm success metrics and target thresholds.
- [x] Add debug-only tap diagnostics flag.
- [ ] Capture baseline first-tap success across matrix scenarios.
- [ ] Record baseline accidental adjacent selection rate.

Exit criteria:
- Baseline metrics are documented.
- Constants to tune are listed and approved.

## Phase 1 - Core Hit Testing Refactor
- [x] Add effective hit radius model per pawn (visual radius plus invisible padding).
- [x] Implement distance-based candidate collection from tap point.
- [x] Restrict candidates to movable pieces.
- [x] Keep deterministic ordering for stability.
- [x] Add unit-level geometry tests.

Exit criteria:
- Single intended pawn can be selected with forgiving taps.
- All tests pass for new candidate ranking logic.

## Phase 2 - Visual Pawn Scaling and Overflow
- [x] Define and lock new visual radius multipliers for 1, 2, 3, and 4-stack pawns.
- [x] Re-tune stack offsets and stack-badge anchor positions for larger visuals.
- [x] Implement controlled overflow cap per axis and verify it is enforced.
- [x] Add deterministic overlap z-order rules for readability.
- [ ] Capture before/after screenshot set on compact and typical phone profiles.

Exit criteria:
- Pawns are visibly larger and easier to read.
- Overflow behavior is present but controlled.
- No clipping artifacts exist at board edges.

## Phase 3 - Ambiguity and Stack Policy
- [x] Add nearest-center preference for multiple candidates.
- [x] Add tie-break by lastMovedAt.
- [x] Add deterministic id tie-break fallback.
- [x] Route unresolved same-cell ambiguity to existing chooser.
- [x] Validate safe-square and lock behavior consistency.

Exit criteria:
- Ambiguous taps are deterministic.
- Existing stack chooser still works where required.

## Phase 4 - Board Sizing Guardrails
- [x] Raise minimum board size floor from current value after compact-height validation.
- [x] Add window-size-aware board floor values.
- [x] Validate portrait and landscape layouts with side rails.
- [x] Verify no clipping/overflow in compact height.

Exit criteria:
- No tiny-board layouts that materially hurt tapping.

## Phase 5 - Optional Forced Single-Move Assist
- [x] Decide product mode (auto-select, auto-play with delay, or keep manual): Mode A (auto-select only).
- [x] Add preference flag and default behavior.
- [x] Implement chosen behavior with safe cancellation.
- [x] Add tests for turn-phase correctness.

Exit criteria:
- Forced-move behavior is predictable and does not change rules.

## Phase 6 - Validation and Release Readiness
- Runbook: plans/004-device-validation-runbook.md
- [ ] Run full manual validation matrix. (pending device/emulator execution)
- [x] Run regression tests for stacked selection rules.
- [x] Run automated lint accessibility gate (lintDebug) and review output.
- [ ] Run accessibility scanner checks for target-size issues. (blocked: no connected device/emulator as of 2026-04-05)
- [ ] Run visual regression/screenshot comparison checks for enlarged pawns. (pending device/emulator and scenario setup)
- [ ] Compare before/after metrics and confirm success targets.
- [x] Prepare release notes for interaction changes.

Exit criteria:
- Success metrics achieved.
- No blocker regressions.

## Rollback Plan
- [x] Keep old cell-only hit path behind temporary fallback flag until release confidence is achieved.
- [ ] If severe regressions appear, switch fallback on while keeping diagnostics enabled.

## Sign-off
- Product sign-off: [ ]
- Engineering sign-off: [ ]
- QA sign-off: [ ]
- Date: __________
