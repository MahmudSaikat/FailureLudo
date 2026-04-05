# 004 - Pawn Touchability Release Notes (Draft)

Date: 2026-04-05
Scope: Pawn touchability, stack selection reliability, visual pawn readability, and optional single-move assist.

## User-Visible Changes
- Pawn visuals are larger across all stack sizes.
- Pawn rendering now allows controlled out-of-cell overflow for better readability while keeping overflow bounded.
- Tap selection is more forgiving and now resolves to nearest valid movable pawn when taps are near boundaries.
- Ambiguous stack taps remain deterministic and continue to use chooser UX where rules require it.
- Compact layouts now use board sizing guardrails to avoid tiny boards that reduce tap reliability.
- New optional setting: Auto-select single move (default off).

## Settings and Controls
- Setting location: Feedback settings dialog.
- Toggle name: Auto-select single move.
- Default value: Disabled.
- Behavior when enabled: If exactly one legal move exists for a human turn, the move is auto-selected after a short delay.

## Rule and Compatibility Notes
- No rule changes were introduced in engine movement logic.
- Existing safe-square and lock semantics are preserved.
- Existing stacked-selection engine behavior remains regression-tested.

## Validation Summary (Automated)
- App unit tests: passing.
- Game-engine unit tests: passing.
- Stacked selection regression test: passing.
- Accessibility pre-gate (lintDebug): passing build, no touch-target/accessibility keyword findings in lint text report.

## Pending Manual Validation Before Final Release
- Execute full 004 validation matrix scenarios on compact and typical phone profiles.
- Run Android Accessibility Scanner on device/emulator and record findings.
- Capture before/after screenshot baselines for compact and typical layouts.
- Confirm before/after first-tap success and accidental adjacent selection metrics.

## Rollback Guidance
- Temporary rollback switch is available in code via USE_CELL_ONLY_TAP_SELECTION.
- If severe tap regressions appear in manual QA, enable the rollback switch while keeping diagnostics support available.

## Risk Notes
- Nearest-candidate hit selection can still require manual tuning in dense edge cases based on final device testing.
- Visual clipping/artifact risk is reduced by bounds logic but must be validated with screenshot baseline comparisons.
