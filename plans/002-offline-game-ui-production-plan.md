# 002 — Offline Game UI Production Plan

## Purpose
Define a production-grade, offline-first game UI improvement roadmap for FailureLudo, focused on board clarity, pawn design quality, visual feedback, emoji/sound effects, and reliability on low-end devices.

## Product Constraints (Non-Negotiable)
- **Offline-first must be correct before enhancements** (all core gameplay available without internet).
- **No gameplay-rule changes in this plan** unless needed to improve clarity/feedback.
- **Deterministic and responsive UX**: players should always understand whose turn it is, what can move, and what just happened.
- **Low-end Android support**: target smooth play on constrained devices.

## Research Signals from Leading Ludo Apps
Observed recurring patterns from market leaders (Ludo King, Ludo Club):
- Strong emphasis on **theme variety** (board/dice skins) and visual personalization.
- Clear **turn and move signaling** (players expect explicit arrows/highlights when interaction is available).
- Frequent use of **reaction loops** (emoji/chat in online modes; celebratory moments in all modes).
- Rich **audio feedback** (dice roll, move, capture, win) and optional voice/chat features online.
- Explicit mention of **offline support**, **save/load continuity**, and **low-end device compatibility**.

Implication: for an offline production app, we should ship polished visual/audio feedback and robust local continuity before adding online-social complexity.

## Gap Assessment (Current Build)
- Board is functional but lacks directional guidance (entry/home arrows, lane emphasis).
- Pawn visuals are clean but not premium (no dynamic states, stack readability could improve).
- Capture and dice events rely mostly on text; limited emotional feedback.
- No dedicated audio/haptics pipeline for game events.
- No in-game settings panel for sound/music/haptic/accessibility.
- Offline reliability features (save/restore at process death) are not fully productized.

## Improvement Backlog (Offline UI Focus)

### P0 — Foundation (Must ship first)
- [x] **Offline Session Integrity**
  - Persist current match and setup locally (auto-save on each action).
  - Resume from exact turn state after app kill/reopen.
  - Add corruption-safe fallback: last valid snapshot + graceful reset.

- [x] **Board Clarity Upgrade v1**
  - Add directional arrows for each color path and home-lane entry.
  - Distinguish safe squares with icon + subtle ring (not color-only).
  - Highlight last moved path briefly (start → end).

- [x] **Pawn Design Upgrade v1**
  - Improve pawn silhouette readability at small sizes.
  - Add selectable state ring + glow standardization.
  - Add stack indicator treatment when 2–4 pawns overlap.

- [x] **Core SFX + Haptics Pack**
  - Dice roll throw + settle sounds.
  - Pawn move tick, capture impact, finish celebration, invalid tap cue.
  - Haptic mapping: light (move), medium (capture), heavy (win).

- [x] **Feedback Settings (Offline)**
  - Sound effects toggle.
  - Music toggle.
  - Haptics toggle.
  - Master volume slider.

### P1 — Production Polish (High value)
- [x] **Move History Controls (Offline)**
  - Add undo/redo controls for recent actions.
  - Keep bounded local history to avoid memory growth.
  - Ensure delayed bot/no-move automation is canceled safely on timeline changes.

- [x] **Dice Interaction Upgrade**
  - Roll animation with brief anticipation + settle state.
  - Disable/re-enable states with explicit visual affordance.
  - “Your turn” pulse around current player dice.

- [x] **Event FX Layer**
  - Capture burst with emoji-style reaction (configurable intensity).
  - Finish/home arrival confetti-lite effect.
  - Extra-roll visual badge near current player.

- [x] **Turn Guidance System**
  - No legal destination indicator (manual calculation by design for gameplay feel).
  - If only one legal move exists, allow optional one-tap quick move.
  - Contextual microcopy for no-move skip and triple-six forfeit.

- [ ] **Accessibility Pass**
  - Color-blind-safe markers on pawns and lanes (shape/symbol overlays).
  - High-contrast board mode.
  - Reduce-motion mode (less animation amplitude/duration).

### P2 — Premium UI Layer (After P0/P1)
- [ ] **Theme System v1 (Offline assets)**
  - 2–3 board themes shipped in APK/AAB (Classic, Night, Minimal).
  - Matching pawn/dice skins.
  - Theme preview in setup screen.

- [ ] **Emote Wheel (Local/Offline)**
  - Quick emote reactions for local pass-and-play and bot matches.
  - Non-intrusive placement and cooldown to prevent spam.

- [ ] **Win Moment Redesign**
  - Winner sequence with polished animation timeline.
  - Team-win specific visuals for TEAM mode.

## Production Architecture Notes

### UI Rendering
- Move board overlays (arrows, highlights, effect layers) into structured drawing phases:
  1. Base board
  2. Static indicators (safe/home markers)
  3. Dynamic turn hints
  4. Pawns
  5. Event FX (temporary)

### Audio/Haptics
- Introduce a local feedback manager abstraction:
  - `play(eventType)` for SFX
  - `vibrate(feedbackType)` for haptics
  - Runtime preference checks before emitting feedback

### Offline Persistence
- Store serialized game snapshots and settings locally.
- Save on every committed move and dice result.
- Restore atomically at app start before navigation decisions.

## Suggested Code Touchpoints
- Existing UI:
  - `app/src/main/kotlin/com/failureludo/ui/components/LudoBoardCanvas.kt`
  - `app/src/main/kotlin/com/failureludo/ui/components/DiceView.kt`
  - `app/src/main/kotlin/com/failureludo/ui/screens/GameBoardScreen.kt`
  - `app/src/main/kotlin/com/failureludo/ui/screens/GameSetupScreen.kt`
- Existing orchestration:
  - `app/src/main/kotlin/com/failureludo/viewmodel/GameViewModel.kt`
- Existing engine models:
  - `game-engine/src/main/kotlin/com/failureludo/engine/GameState.kt`
- New modules/classes (proposed):
  - `app/src/main/kotlin/com/failureludo/feedback/GameFeedbackManager.kt`
  - `app/src/main/kotlin/com/failureludo/feedback/GameAudioManager.kt`
  - `app/src/main/kotlin/com/failureludo/feedback/GameHapticsManager.kt`
  - `app/src/main/kotlin/com/failureludo/data/GameSessionStore.kt`
  - `app/src/main/kotlin/com/failureludo/data/GamePreferencesStore.kt`

## Delivery Plan (Execution Order)

### Milestone A — Offline Correctness + Core Feedback (Week 1)
- Implement local session save/restore.
- Add baseline SFX/haptics + settings toggles.
- Add board arrows and safe-cell emphasis.

### Milestone B — Interaction Polish (Week 2)
- Dice animation pass.
- Pawn visual state improvements.
- Capture and finish event effects.

### Milestone C — Accessibility + Theme Seed (Week 3)
- Color-blind markers, high contrast, reduce motion.
- Add at least one alternate board/dice theme.

## Definition of Done (Production Criteria)
- 100% offline playability for all existing local modes.
- App can recover active match after process death with no rule-state loss.
- No ambiguous turn state (dice/pawn affordances always explicit).
- Sound/haptic behavior is consistent, user-configurable, and disabled correctly when toggled off.
- Board and pawn visuals remain clear on small screens and low brightness.
- Performance target met on low-end test device profile (stable, no visible jank in core loop).

## QA Checklist (Offline UI)
- [ ] Kill/reopen during each turn phase and verify exact resume.
- [ ] Verify capture/finish/extra-roll/no-move cues are visible and audible.
- [ ] Validate settings persistence across relaunch.
- [ ] Validate color-blind and high-contrast modes for all four colors.
- [ ] Stress test repeated animations for memory growth/regressions.

## Status
- Plan status: **Milestone B In Progress**
- Milestone A status: **Completed (2026-03-26)**
- Milestone B status: **Completed (2026-03-27)**
- Milestone B progress: **3/3 complete (Dice + Pawn + Event FX + Turn Guidance done)**
- Move history controls: **Completed (undo/redo for recent actions)**
- Pawn cell-by-cell walking animation: **In Progress (UI-layer implementation started, validation pending)**
- Pending in P0: **None**
- Priority: **High (Accessibility pass)**
- Last updated: 2026-03-27
