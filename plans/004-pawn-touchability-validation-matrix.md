# 004 - Pawn Touchability Validation Matrix

## Test Goals
- Verify pawn selection reliability improves under realistic phone usage.
- Verify enlarged pawn visuals improve readability without harming board clarity.
- Verify no regressions in stack semantics, safe-square handling, or turn correctness.
- Verify behavior is deterministic across device sizes and orientations.

## Measurement Targets
- First-tap intended selection success >= 95%.
- Accidental adjacent selection <= 3%.
- No rules regressions in stacked-move behavior.

## Visual Geometry Targets
- Radius multiplier targets (to be tuned and then locked):
- Single stack: 0.34 to 0.38 x cellSize
- Two stack: 0.32 to 0.35 x cellSize
- Three stack: 0.30 to 0.33 x cellSize
- Four stack: 0.28 to 0.31 x cellSize
- Overflow cap: no axis exceeds 0.18 x cellSize outside originating cell bounds.
- Readability target: tester score >= 4/5 for stack clarity on compact phone profile.

## Device and Layout Matrix

### Compact phones
- 360 x 640 dp (portrait)
- 360 x 640 dp (landscape)
- Split-screen compact-height scenario

### Typical phones
- 411 x 891 dp (portrait)
- 412 x 915 dp (portrait)
- 411 x 891 dp (landscape)

### Medium/large
- 600+ dp width class (tablet portrait and landscape)

For each profile above, test with default and any new board floor settings.

## Scenario Matrix

### S1 - Single movable pawn in cell
Steps:
1. Ensure one legal movable pawn in target area.
2. Tap center, edge, and near-edge around pawn.
Expected:
- Pawn selected every time within effective target radius.
- No adjacent piece selected.

### S2 - Two pawns in same cell (movable set includes one)
Steps:
1. Create two-piece stack with only one movable.
2. Tap around both rendered offsets.
Expected:
- Movable pawn selected deterministically.
- No chooser shown unless ambiguity policy requires it.

### S3 - Three-piece same-color non-safe stack (single and pair legal)
Steps:
1. Create stack state where both options are legal.
2. Tap in ambiguous area.
Expected:
- Existing chooser appears with single/pair options.
- Choosing each option triggers correct move.

### S4 - Four-piece stack stress
Steps:
1. Create 4-piece same-cell crowded state.
2. Tap near each visual pawn offset and around cluster edge.
Expected:
- Deterministic selection or deterministic chooser fallback.
- No random candidate jumps between taps.

### S5 - Adjacent cell crowding
Steps:
1. Place movable pawns in neighboring cells.
2. Tap near cell boundary and diagonals.
Expected:
- Nearest intended pawn selected within policy.
- Leak cap prevents distant or unrelated selections.

### S6 - Safe-square behavior
Steps:
1. Use stacked pieces on safe square.
2. Repeat taps near offsets.
Expected:
- Safe-square selection behavior remains consistent with existing policy.

### S7 - Animation interaction
Steps:
1. Tap during and after piece animation transitions.
2. Repeat with rapid taps.
Expected:
- No crash.
- No stale position mapping.
- Final selected piece is consistent with rendered state.

### S8 - Forced single-move assist (if enabled)
Steps:
1. Create turn with exactly one legal move.
2. Observe behavior under selected mode.
Expected:
- Auto-select/auto-play only if feature enabled.
- No rule or turn-order drift.

### S9 - Visual size verification on compact board
Steps:
1. Run compact profile (360 x 640 dp portrait).
2. Measure/inspect rendered pawn size for stack counts 1 to 4.
Expected:
- Each stack tier matches locked multiplier range.
- Pawn is clearly visible without requiring zoom.

### S10 - Overflow edge and clipping checks
Steps:
1. Move pawns to top, bottom, left, and right board-edge lanes.
2. Use enlarged pawn and overflow settings.
Expected:
- No clipping or cut-off rendering at board edges.
- Overflow remains within cap and does not break layout.

### S11 - Stack readability and z-order checks
Steps:
1. Create dense stack and neighboring-piece scenarios.
2. Verify overlap ordering and badge placement.
Expected:
- Visual hierarchy is deterministic and understandable.
- Stack badge remains readable and not occluded.

## Accessibility Checks
- Run Android Accessibility Scanner on game board screen.
- Verify no critical touch-target warnings on interactive controls.
- Verify interaction remains usable with one-handed thumb taps.

## Regression Suite Checklist
- [x] game-engine stacked selection tests pass.
- [x] Existing move legality tests pass.
- [x] No new crashes in resume/session restore path.
- [ ] No new clipping/artifact failures in visual screenshot baselines.

## Automated Validation Runs (2026-04-05)
- app unit tests and compile pass:
	- :app:testDebugUnitTest
	- :app:compileDebugKotlin
- game-engine regression pass:
	- :game-engine:test
	- :game-engine:test --tests com.failureludo.engine.StackedMoveSelectionTest
- accessibility pre-gate pass:
	- :app:lintDebug completed successfully.
	- lint report scan did not surface touch-target/accessibility keyword matches.
- device/scanner readiness:
	- adb available, no connected device/emulator at run time.
	- Android Accessibility Scanner run is pending device/emulator attachment.

## Data Capture Template
Per run record:
- Device/profile:
- Build/version:
- Scenario id:
- Attempt count:
- First-tap success count:
- Accidental adjacent selections:
- Visual readability score (1-5):
- Screenshot artifact path/id:
- Notes:

## Sign-off Conditions
- All scenarios pass in at least one compact and one typical profile.
- Metric targets are met or exceeded.
- No P0/P1 regressions remain open.

## Status
- Matrix status: In progress
- Runbook: plans/004-device-validation-runbook.md
- Last updated: 2026-04-05
