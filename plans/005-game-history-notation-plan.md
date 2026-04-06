## Plan: Chess-Style History and Replay for FailureLudo

Build a chess-app style game archive by introducing a Ludo notation language and replay workflow that mirror PGN plus position snapshots: FLN (Failure Ludo Notation) as the canonical move text, FLS (Failure Ludo Snapshot) as point-in-time board state, and a multi-game local archive with resume/delete/replay/export-import. Keep timeline linear (no branches), support multiple active unfinished games, retain up to 50 games locally, and keep repository boundaries ready for future remote sync.

## Implementation Status (2026-04-06)
- [x] User-side plan copied to repo as `plans/005-game-history-notation-plan.md`.
- [x] FLN foundation added: versioned parser registry, v1 codec, header codec, parse result contracts.
- [x] Deterministic replay engine hooks added (`rollDice(state, forcedDiceValue)`, deterministic turn and roll-only helpers).
- [x] Replay execution adapter added in app layer (`FlnReplayApplier`) with unit tests.
- [x] Transition-based move recorder added (`FlnMoveRecorder`) including defer-home-entry detection.
- [x] Roll-only FLN entries supported (`X:SKIP` / `X:FORFEIT`) to preserve replay order.
- [x] Canonical archive core added (`GameHistoryArchive`) with retention and unsupported-version handling.
- [x] Persistent archive boundary added (`GameHistoryStore` + `GameHistoryRepository`).
- [x] Archive integration wired into `GameViewModel` (recording, load, open, delete, refresh).
- [x] Extend UI with replay controls and file-based import/export flows (History now supports document-picker import/export and board replay controls).

**Steps**
1. Phase A: Finalize notation and archive contracts (foundation).
Define FLN v1 grammar and metadata tags modeled after PGN headers (gameId, createdAt, mode, players by PlayerId and name, result, status, move count).
Define move tokens that include dice value, acting playerId, selected pieceId, and optional choice markers needed by current rules (for example defer-home-entry decisions).
Define FLS snapshot payload for jump-in restore and import validation, similar to how chess uses FEN for board state.
Specify linear-history semantics: resuming from an earlier move overwrites forward moves in that same game record.

2. Phase B: Add deterministic replay primitive in engine (depends on 1).
Refactor engine turn application so dice can be injected deterministically for replay/import validation, while keeping current random roll path for live play.
Ensure one action record maps to one deterministic transition and preserves current eventLog behavior.
Add engine-side replay helpers that can rebuild a game from setup plus FLN actions without UI dependencies.

3. Phase C: Build FLN codec and replay pipeline (depends on 1 and 2).
Add parser and serializer for FLN text and FLS snapshots.
Implement strict validation that rejects illegal imports (bad player IDs, impossible dice/move combos, phase mismatches).
Support round-trip conversion: live game state updates append FLN action lines, and imported FLN reconstructs the same game state.

4. Phase D: Introduce multi-game local archive model and migration (depends on 1 and 3).
Design history data model with game status ACTIVE or FINISHED, timestamps, result metadata, and pointers to latest snapshot plus notation text.
Migrate current single-snapshot session data into the new archive model on first launch after schema bump.
Apply retention rule: keep up to 50 records, prune oldest FINISHED first, never auto-delete ACTIVE games.

5. Phase D: Implement history persistence and repository boundary (depends on 4).
Add a dedicated local history store in app data layer and a repository interface so future remote sync can implement the same contract.
Support CRUD operations required by product scope: list records, open game, delete game, save progress, mark finished, export FLN, import FLN.
Keep corruption-safe fallback behavior already used by session restore.

6. Phase E: Refactor orchestration for gameId-aware workflows (depends on 5).
Update ViewModel orchestration so each game action writes to the selected gameId record instead of a single global slot.
Maintain current undo-redo as in-session convenience, while archive history remains durable and cross-session.
On game switch or replay entry, clear pending bot and automation jobs before loading target state.

7. Phase E: Add archive navigation and list UI (parallel with 6 after 5).
Extend navigation with History destination and route arguments for selected gameId and mode (resume vs replay).
Add Home entry point to open archive.
Build History list with filtering (active and finished), sort by recent activity, and actions for resume, replay, delete, export, import.

8. Phase F: Add replay and analysis mode UX (depends on 6 and 7).
Provide read-only replay mode on board screen with step back/forward, autoplay with speed control, and jump-to-move.
Opening a FINISHED record defaults to replay mode; opening an ACTIVE record allows resume play and optional replay view.
When user resumes from earlier move in same game, truncate forward notation moves to enforce the selected linear-history rule.

9. Phase G: Export-import flow and compatibility hardening (depends on 3, 5, and 8).
Implement FLN file export and import using Android document picker flows.
Version FLN and archive schema explicitly; preserve backward-compatible readers where feasible and fail closed otherwise.
Add import guardrails for malformed or tampered files and surface clear user-facing error states.

10. Phase H: Verification, docs, and tracker update (depends on 2 through 9).
Add deterministic replay tests, archive migration tests, retention tests, and ViewModel switching/resume tests.
Add UI tests for history list actions and replay controls.
Document FLN and FLS formats and update roadmap trackers.
Recommendation for tracker workflow: create a new numbered plan in plans because this is materially new scope, and cross-link with plan 002 and plan 003.

**Compatibility policy (added refinement)**
- Version FLN and rules separately: `flnVersion` (language) and `rulesetVersion` (game-logic contract).
- Ship parser adapters by FLN major version (for example `FlnV1Parser`, `FlnV2Parser`) behind a registry, so old records can still be read without polluting newest grammar.
- Keep local archive in canonical structured JSON internally; generate human-readable FLN for export/share. This avoids lock-in to one text grammar and makes migrations safer.
- Migration ladder on app update:
  1. If parser + migrator exists: auto-migrate to latest internal canonical model.
  2. If parser exists but no safe migrator: keep as read-only legacy replay.
  3. If parser missing/incompatible: mark as unsupported (metadata visible, export raw, delete option).
- Fresh install is fallback only for severe corruption or when user explicitly opts to reset history, not the default compatibility strategy.
- Forward-compat rules:
  - Minor FLN versions must be additive and ignore unknown optional tags.
  - Major FLN versions may break syntax and require explicit adapter/migrator.

**Relevant files**
- [game-engine/src/main/kotlin/com/failureludo/engine/GameEngine.kt](game-engine/src/main/kotlin/com/failureludo/engine/GameEngine.kt) — add deterministic dice-application path and replay helpers.
- [game-engine/src/main/kotlin/com/failureludo/engine/GameState.kt](game-engine/src/main/kotlin/com/failureludo/engine/GameState.kt) — confirm or extend state fields needed for notation checkpoints.
- [game-engine/src/main/kotlin/com/failureludo/engine/DiceRoller.kt](game-engine/src/main/kotlin/com/failureludo/engine/DiceRoller.kt) — keep random live path while enabling deterministic replay injection.
- [app/src/main/kotlin/com/failureludo/data/GameSessionStore.kt](app/src/main/kotlin/com/failureludo/data/GameSessionStore.kt) — migration entry point from legacy single snapshot.
- [app/src/main/kotlin/com/failureludo/data](app/src/main/kotlin/com/failureludo/data) — add history store, notation codec, and repository contracts as new sibling files.
- [app/src/main/kotlin/com/failureludo/viewmodel/GameViewModel.kt](app/src/main/kotlin/com/failureludo/viewmodel/GameViewModel.kt) — move from global session slot to selected gameId archive workflows.
- [app/src/main/kotlin/com/failureludo/ui/navigation/Screen.kt](app/src/main/kotlin/com/failureludo/ui/navigation/Screen.kt) — add History and Replay routes.
- [app/src/main/kotlin/com/failureludo/ui/navigation/AppNavigation.kt](app/src/main/kotlin/com/failureludo/ui/navigation/AppNavigation.kt) — wire history list, replay, and resume routing.
- [app/src/main/kotlin/com/failureludo/ui/screens/HomeScreen.kt](app/src/main/kotlin/com/failureludo/ui/screens/HomeScreen.kt) — add archive entry point alongside new/resume.
- [app/src/main/kotlin/com/failureludo/ui/screens/GameBoardScreen.kt](app/src/main/kotlin/com/failureludo/ui/screens/GameBoardScreen.kt) — add replay controls and read-only replay mode behavior.
- [app/src/main/kotlin/com/failureludo/ui/screens/WinScreen.kt](app/src/main/kotlin/com/failureludo/ui/screens/WinScreen.kt) — add actions to open replay or archive.
- [game-engine/src/test/kotlin/com/failureludo/engine/PlayerIdentityMigrationTest.kt](game-engine/src/test/kotlin/com/failureludo/engine/PlayerIdentityMigrationTest.kt) — pattern for identity-safe serialization and migration tests.
- [app/src/test/kotlin/com/failureludo/viewmodel/GameStateRestoreValidationTest.kt](app/src/test/kotlin/com/failureludo/viewmodel/GameStateRestoreValidationTest.kt) — pattern for restore validator coverage to extend for archive imports.
- [plans/002-offline-game-ui-production-plan.md](plans/002-offline-game-ui-production-plan.md) — update status dependencies because history controls move beyond undo-redo.
- [plans/003-player-identity-decoupling-plan.md](plans/003-player-identity-decoupling-plan.md) — ensure notation and archive remain PlayerId-first.
- [docs/architecture-maps.md](docs/architecture-maps.md) — update architecture map with history and notation modules.

**Verification**
1. Engine determinism test: replay FLN action stream and assert resulting state equality against original run (players, moveCounter, winners, eventLog shape).
2. Parser tests: valid FLN round-trip and failure cases for invalid dice, invalid piece IDs, impossible destinations, and wrong turn ownership.
3. Migration test: existing single snapshot is imported into archive exactly once and remains resumable.
4. Retention test: creating 51st record prunes oldest FINISHED record while preserving ACTIVE records.
5. ViewModel test: switching between two ACTIVE games cancels pending automation jobs and restores correct selected gameId state.
6. UI test: history list supports resume, replay, delete, export, and import actions with correct routing.
7. Manual QA: create multiple unfinished games, resume any one, close app, reopen, and verify all records persist and remain selectable.
8. Manual QA: open FINISHED game and verify step controls, autoplay speed, and jump-to-move stay read-only.
9. Manual QA: resume from earlier move in an ACTIVE game and verify forward moves are truncated (linear overwrite behavior).
10. Export-import QA: export FLN, delete local record, re-import, and verify replay parity and resumability.

**Decisions**
- Notation format: human-readable PGN-like text (FLN).
- Timeline policy: no branches; resuming from older position overwrites forward line in that same record.
- Local capacity: retain 50 game records by default.
- Active sessions: allow multiple unfinished games concurrently.
- Replay release scope: step back/forward, autoplay with speed controls, jump-to-move, and export-import.
- Included now: full local archive, resume/delete, finished-game analysis replay, import-export.
- Excluded for now: online sync implementation, collaborative analysis, branch variations tree, gameplay-rule changes.
- Compatibility policy: no forced reinstall by default; use versioned parser adapters + migration ladder, with unsupported legacy records shown as read-only metadata when safe replay is not possible.
- Internal storage policy: structured canonical history is source of truth; FLN text is generated/imported representation for sharing and analysis.


**Further Considerations**
1. Use file extension .fln for notation exports and keep FLS as embedded snapshot section inside FLN header block to simplify single-file sharing.
2. Keep archive repository interface sync-ready now (local implementation only) so remote rollout can be added without UI or ViewModel contract churn.
