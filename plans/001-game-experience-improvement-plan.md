# 001 — Game Experience Improvement Plan

## Objective
Improve player enjoyment, trust, and replayability in `FailureLudo` by closing the biggest UX/gameplay gaps relative to top Ludo apps, while keeping scope realistic for the current codebase.

## Baseline Observations (Current Project)
- Core turn loop is solid: roll → select → move with event logging (`GameEngine`, `GameRules`, `GameViewModel`).
- Bot logic is functional but predictable (capture > finish > progress > enter).
- UX is clear but minimal: board view, turn chips, status text, win screen.
- No game persistence (app restart loses match state).
- No onboarding/help or move explanation system.
- No progression/rewards/meta loop to encourage return play.

## Competitor Pattern Snapshot (Ludo King / Ludo Club)
Common expectations seen in leading apps:
- Multiple game modes and match pacing options (classic + fast variants).
- Strong social loops (friends, chat, invites, private rooms).
- Retention loops (daily goals, rewards, seasons/events, cosmetic unlocks).
- Better trust affordances (clear turn feedback, smooth animations, fewer “why did that happen?” moments).
- Session resilience (save/load, reconnect-like continuity).

## Gap Analysis (High Impact)
1. **Trust & Clarity Gap**: important state changes are text-only and easy to miss (captures, extra turn reason, legal move rationale).
2. **Pacing Gap**: bot timing and turn transitions are static, causing dead time and repetition.
3. **Continuity Gap**: no resume after process death; this is a major frustration source in mobile board games.
4. **Replayability Gap**: single core mode + no progression means low long-term stickiness.
5. **Accessibility/Usability Gap**: setup/name editing and move affordances are usable but not optimized for quick repeat play.

## Prioritized Roadmap

### P0 — Must Have (Player Trust + Core UX)
- [ ] **Event Timeline Panel (last N events + color tags)**  
      Surface clear action history (moved/captured/extra roll/skip) instead of only last event.
- [ ] **Move Preview + Destination Highlight**  
      On tap/selectable piece, show destination cell and capture risk/safe-square hint.
- [x] **Stacked-Cell Move Disambiguation (pair vs single)**
      For ambiguous stacked taps (notably 3-piece same-color stacks), show explicit move choice so players can intentionally move the top single or locked pair.
- [ ] **Per-turn Explainers**  
      Add explicit reason labels for auto-skip, extra roll, and three-sixes forfeit.
- [ ] **Session Save/Restore**  
      Persist `GameState` + `SetupState` via DataStore/serialization for true resume.

### P1 — Should Have (Pacing + Fairness Perception)
- [ ] **Bot Difficulty Profiles** (`Easy`, `Normal`, `Aggressive`)  
      Keep deterministic heuristics but vary priority weighting and risk tolerance.
- [ ] **Adaptive Turn Timing**  
      Shorten delay when only one legal move exists; keep slight delay for readability.
- [ ] **Dice Roll Animation & Turn Hand-off Animation**  
      Improve responsiveness and reduce “stuck” perception.
- [ ] **Quick Rematch Flow**  
      One-tap rematch preserving players + mode + bot settings.

### P2 — Nice to Have (Retention)
- [ ] **Game Modes: Quick/Rush variant**  
      Faster match option with modified win/capture emphasis.
- [ ] **Stats & Streaks**  
      Track wins, captures, average turns, fastest win.
- [ ] **Cosmetic Progression (board/dice themes)**  
      Unlockables without affecting game fairness.

## Implementation Mapping (Where to Change)
- Engine rules and heuristics:
  - `game-engine/src/main/kotlin/com/failureludo/engine/GameEngine.kt`
  - `game-engine/src/main/kotlin/com/failureludo/engine/GameRules.kt`
  - `game-engine/src/main/kotlin/com/failureludo/engine/GameState.kt`
- Game orchestration and timing:
  - `app/src/main/kotlin/com/failureludo/viewmodel/GameViewModel.kt`
- UX surfaces:
  - `app/src/main/kotlin/com/failureludo/ui/screens/GameBoardScreen.kt`
  - `app/src/main/kotlin/com/failureludo/ui/components/LudoBoardCanvas.kt`
  - `app/src/main/kotlin/com/failureludo/ui/components/DiceView.kt`
  - `app/src/main/kotlin/com/failureludo/ui/screens/GameSetupScreen.kt`
  - `app/src/main/kotlin/com/failureludo/ui/screens/WinScreen.kt`

## Suggested Milestones
- **Milestone 1 (1 week):** P0 trust/clarity improvements (timeline, explainers, move preview).
- **Milestone 2 (1 week):** P0 persistence + P1 pacing upgrades.
- **Milestone 3 (1–2 weeks):** P1 bot difficulty + quick rematch.
- **Milestone 4 (optional):** P2 retention systems.

## Acceptance Signals
- Reduced user confusion events (“why skipped?”, “why extra turn?”, “which piece should move?”).
- Higher completed-match ratio after app background/restore.
- Shorter average turn idle time without reducing readability.
- Increased replay rate (play again / rematch usage).

## Status Tracker
- Plan status: **In Progress**
- Next action: Implement remaining Milestone 1 trust/clarity items (timeline, move preview, explainers)
- Owner: TBD
- Last updated: 2026-03-28
