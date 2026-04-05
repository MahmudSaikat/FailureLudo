# 004 - Pawn Touchability and Selection Reliability Plan

## Objective
Improve pawn selection reliability and pawn readability on mobile devices by combining forgiving touch targets with larger pawns (including controlled out-of-cell overflow), while preserving current game rules and stacked-move semantics.

## Why This Plan Exists
Current feedback: pawns feel too small to tap consistently.

The current implementation is visually clean but precision-heavy:
- Board tap handling snaps to a 15x15 cell grid in LudoBoardCanvas.
- Pawn visual radius scales down for stacks (0.30x cell down to 0.24x cell).
- Board can render at small sizes under constrained height.

Result: players need high tap precision during crowded states.

## Baseline Observations (Current Code)
- Tap model:
  - detectTapGestures resolves row/col by tapOffset / cellSize.
  - Selection is cell-based and only proceeds if tapped cell contains movable pieces.
- Visual pawn geometry:
  - Radius per stack count:
    - 1 piece: 0.30 x cellSize
    - 2 pieces: 0.28 x cellSize
    - 3 pieces: 0.26 x cellSize
    - 4+ pieces: 0.24 x cellSize
- Board sizing:
  - boardSize uses maxHeight minus rails with a low floor of 140dp.

Practical implication on common phone boards:
- 360dp board width => 24dp cell => single pawn diameter ~14.4dp.
- 412dp board width => 27.5dp cell => single pawn diameter ~16.5dp.

These are far below typical mobile interaction recommendations for reliable touch.

## Product Decisions (Locked)
- No game rule changes in this plan.
- Selection reliability and visual readability are dual priorities.
- Visible pawn scale will increase in addition to hit-area improvements.
- Controlled overflow beyond cell boundaries is allowed and intentional, with strict caps to preserve board readability.
- Touch target and visual size remain decoupled so each can be tuned independently.
- Existing stack-choice UX remains the fallback for true ambiguity.
- Changes must be safe for low-end devices and should not introduce frame drops.

## External Guidance and Market Signal
- Android accessibility guidance recommends at least 48dp touch targets for interactive elements.
- Compose accessibility guidance repeats the 48dp minimum for reliable interaction.
- WCAG target-size guidance recommends at least 44x44 CSS px as a minimum baseline.
- Popular Ludo app flows are simple and low-friction (tap dice, then tap/select token), which suggests precision burden should be minimized.

Note: Commercial app internals are not public, so exact competitor hitbox math is inferred from behavior and standards rather than source code.

## Scope
In scope:
- Pawn hit testing and tap-to-selection behavior.
- Visual pawn geometry scaling, stack offsets, z-order, and controlled overflow behavior.
- Ambiguity handling and stack disambiguation policy.
- Board sizing guardrails affecting touchability.
- Optional forced single-move assist.
- Verification, metrics, and regression protection.

Out of scope:
- Full board theming overhaul beyond pawn readability.
- Major art-pipeline changes unrelated to selection clarity.
- Networking, matchmaking, chat.
- Rule-set changes in engine movement logic.

## Success Metrics
Primary:
- First-tap success rate for intended pawn selection >= 95% in manual QA matrix.
- Adjacent-cell accidental selection rate <= 3% in crowded scenarios.

Secondary:
- No regressions in stacked move semantics.
- No regressions in safe-square and lock behaviors.
- No noticeable input latency increase.

Visual quality:
- Pawn radius multipliers increase by at least 15 percent versus baseline for each stack tier unless profiling on compact devices forces a lower tuned value.
- No clipping artifacts from overflow in portrait, landscape, or split-screen layouts.
- Stack readability score >= 4/5 in manual QA (testers can identify the intended pawn cluster at a glance).

## Workstream A - Baseline Instrumentation and Guardrails
Status: In progress

Tasks:
- [x] Add a lightweight debug-only tap diagnostics mode (tap point, chosen piece, candidate count).
- [ ] Capture before/after first-tap success rates in test matrix scenarios.
- [ ] Define and lock tuning constants with comments (min target, multipliers, tie-break policy).

Deliverable:
- Baseline metrics attached to this plan before behavior changes are merged.

## Workstream B - Hit Target Decoupling (Core Fix)
Status: Completed

Design direction:
- Compute per-pawn invisible hit radius independently from visible radius.
- Keep rendering unchanged initially; improve interaction first.

Candidate constants (to be tuned):
- MIN_EFFECTIVE_TOUCH_TARGET_DP = 48dp
- HIT_RADIUS_MULTIPLIER = 1.25 to 1.45 of visual radius
- NEIGHBOR_LEAK_CAP = limit selection spillover into unrelated distant pieces

Tasks:
- [x] Build tap candidate list using distance to pawn center, not only tapped cell.
- [x] Convert dp thresholds to px using current density.
- [x] Preserve movable-piece filtering: only legal movable pieces can be selected.
- [x] Keep complexity linear in number of pieces and avoid allocations in hot path where possible.

Deliverable:
- Tap on/near intended pawn works reliably without needing exact center hits.

## Workstream C - Ambiguity Resolution Policy
Status: In progress

Policy:
1. Filter candidates by effective hit radius.
2. Keep only movable candidates.
3. If one candidate remains: auto-select.
4. If multiple remain:
   - Prefer nearest center.
   - If equal distance, prefer most recent lastMovedAt.
   - If still tied, prefer deterministic id ordering.
5. If unresolved and same-cell stacked ambiguity remains, route to existing chooser.

Tasks:
- [x] Implement deterministic ranking with stable ordering.
- [x] Preserve existing 3-piece same-color stack chooser behavior.
- [x] Preserve safe-square fast path behavior unless explicitly changed.

Deliverable:
- Reduced accidental choice with deterministic and explainable behavior.

## Workstream D - Board Scale Guardrails
Status: Completed

Problem:
- Extremely small board rendering under compact height increases tap difficulty and visual crowding.

Tasks:
- [x] Raise minimum board floor from current value after testing compact-height layouts.
- [x] Add window-size-aware board floor values (compact/medium/expanded).
- [x] Verify side rails and board still fit without clipping in portrait and landscape.

Deliverable:
- No tiny-board states that materially hurt interaction.

## Workstream E - Visual Pawn Scaling and Controlled Overflow
Status: In progress

Design direction:
- Increase visual pawn radii and stack offsets to improve readability.
- Allow bounded overflow outside the originating cell so pawns feel substantial, similar to leading Ludo apps.
- Keep overflow deterministic and capped to avoid visual chaos.

Candidate constants (to be tuned):
- PAWN_RADIUS_SINGLE = 0.34 to 0.38 x cellSize (baseline 0.30)
- PAWN_RADIUS_DOUBLE = 0.32 to 0.35 x cellSize (baseline 0.28)
- PAWN_RADIUS_TRIPLE = 0.30 to 0.33 x cellSize (baseline 0.26)
- PAWN_RADIUS_QUAD = 0.28 to 0.31 x cellSize (baseline 0.24)
- MAX_OVERFLOW_PER_AXIS = 0.18 x cellSize
- STACK_OFFSET_LIMIT = up to +/-0.24 x cellSize where needed

Tasks:
- [x] Introduce and document visual radius constants per stack tier.
- [x] Re-tune stack offsets and badge placement after size increase.
- [x] Add deterministic z-order for overlap readability (for example movable above non-movable).
- [x] Ensure larger/overflowed pawns are never clipped at board edges.
- [ ] Capture before/after screenshot set on compact and typical phone profiles.

Deliverable:
- Pawns are visibly larger, easier to read, and can exceed cell bounds in a controlled way without reducing board clarity.

## Workstream F - Optional Forced Single-Move Assist
Status: Completed

Purpose:
- Remove unnecessary tap when only one legal move exists.

Modes for product decision:
- Mode A: Auto-select only. (implemented)
- Mode B: Auto-play after short delay with clear visual confirmation.
- Mode C: Keep manual behavior (current default).

Tasks:
- [x] Add feature flag in preferences.
- [x] Implement selected mode with cancellation safety around animations and turn transitions.
- [x] Ensure no change to rules, only interaction flow.

Deliverable:
- Reduced friction in forced-move turns (if enabled).

## Workstream G - Verification and Regression
Status: In progress

Tasks:
- [x] Add geometry-focused tests for hit radius math.
- [x] Add selection-ranking tests for candidate ordering.
- [x] Add geometry-focused tests for radius multipliers and overflow caps.
- [x] Add UI interaction tests for dense stacks and adjacent tap edges.
- [ ] Add visual regression checks (or screenshot baselines) for compact/typical layouts.
- [x] Run existing stacked-selection engine tests and ensure no behavior drift.
- [x] Run automated accessibility lint gate and review output for touch-target related warnings.
- [ ] Run accessibility scanner pass for touch target warnings.

Deliverable:
- Quantified reliability improvement and no rules regressions.

## Implementation Touchpoints
App layer:
- app/src/main/kotlin/com/failureludo/ui/components/LudoBoardCanvas.kt
- app/src/main/kotlin/com/failureludo/ui/screens/GameBoardScreen.kt
- app/src/main/kotlin/com/failureludo/ui/components/BoardCoordinates.kt
- app/src/main/kotlin/com/failureludo/viewmodel/GameViewModel.kt
- app/src/main/kotlin/com/failureludo/data/GamePreferencesStore.kt

Engine tests impacted for regression confidence:
- game-engine/src/test/kotlin/com/failureludo/engine/StackedMoveSelectionTest.kt

## Milestones
Milestone 1 (Core reliability):
- Workstreams A + B complete.
- Early QA run on compact phone profile.

Milestone 2 (Ambiguity + sizing + visual scale):
- Workstreams C + D + E complete.
- Full manual matrix run.

Milestone 3 (Optional assist and hardening):
- Workstreams F + G complete.
- Ready for production toggle decision.

## Risks and Mitigations
Risk: Larger invisible targets select wrong nearby pawn.
- Mitigation: nearest-center ranking + leak cap + fallback chooser.

Risk: Behavior inconsistency with stack rules.
- Mitigation: preserve chooser path and run stack regression suite.

Risk: Larger visuals and overflow reduce board readability in dense situations.
- Mitigation: cap overflow, tune z-order, and validate with screenshot/matrix checks.

Risk: Performance impact in pointerInput path.
- Mitigation: keep candidate computation simple and avoid expensive allocations.

Risk: Layout regressions when increasing board floor.
- Mitigation: window-size-class checks and manual orientation matrix.

## Acceptance Criteria
- Pawn selection is noticeably easier in crowded board states.
- Pawns are visibly larger and remain readable in all stack states.
- Controlled pawn overflow beyond cell bounds is present and free of clipping artifacts.
- First-tap success meets target in validation matrix.
- Stack chooser appears only when needed, and still supports legal pair/single decisions.
- No rule regressions or crash regressions.
- Plan artifacts are complete and executable without rediscovery.

## Status Tracker
- Plan status: In Progress
- Priority: High
- Owner: TBD
- Next action: Connect a device/emulator and execute accessibility scanner, full manual matrix, and screenshot baseline capture (see plans/004-device-validation-runbook.md).
- Last updated: 2026-04-05
