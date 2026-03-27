# 003 — Player Identity Decoupling Plan

## Objective
Decouple player identity from color across engine, persistence, and UI so the app uses stable internal IDs while users see only player names (with defaults Player-1 to Player-4).

## Product Decisions
- Internal identity is ID-based (`PlayerId`) and not color-based.
- User-facing identity is always player names.
- Default placeholders are `Player-1`, `Player-2`, `Player-3`, `Player-4`.
- Custom colors remain visual/theme and board-path semantics only.
- Backward compatibility for old saved sessions is not required; incompatible sessions should reset.

## Why This Is Needed
Current code still ties identity to `PlayerColor` in several places:
- Engine state ownership fields (`winners`, `diceByPlayer`, event payloads)
- Session JSON keys and serialization shape
- Setup/game/win UI labels and badges

This causes UX confusion once users can choose arbitrary colors.

## Current Status (Implementation Started)
- [x] Added stable internal `PlayerId` value type.
- [x] Added `id: PlayerId` to `Player` model.
- [x] New-game default names no longer fall back to color names.
- [x] Setup defaults normalized to `Player-1`..`Player-4`.
- [x] Setup/game/win labels updated to avoid color-name identity in key surfaces.
- [x] Session schema versioning introduced and incompatible legacy snapshots rejected.
- [x] Convert game-state ownership fields from color-keyed to id-keyed.
- [x] Convert remaining event payload ownership from color-based to id-based.
- [ ] Complete full UI semantics and accessibility pass for name-first identity.
- [ ] Add regression tests for ID identity flow and schema resets.

## Workstream A — Engine Identity Contracts
### Scope
- `game-engine/src/main/kotlin/com/failureludo/engine/PlayerId.kt`
- `game-engine/src/main/kotlin/com/failureludo/engine/Player.kt`
- `game-engine/src/main/kotlin/com/failureludo/engine/GameState.kt`
- `game-engine/src/main/kotlin/com/failureludo/engine/GameEngine.kt`
- `game-engine/src/main/kotlin/com/failureludo/engine/GameRules.kt`

### Tasks
- [x] Introduce `PlayerId` and attach to `Player`.
- [x] Migrate `GameState.diceByPlayer` from `Map<PlayerColor, Int?>` to ID-keyed map.
- [x] Migrate `GameState.winners` from `List<PlayerColor>` to ID-keyed list.
- [x] Migrate remaining `GameEvent` actor references from color identity to ID identity.
- [ ] Update engine flow and rules to keep color only for board/team semantics.

### Coupling Notes
- `GameRules` movement, entry, home-column, and team checks are legitimately color-based.
- Ownership and winner attribution should be ID-based.

## Workstream B — Session Persistence & Recovery
### Scope
- `app/src/main/kotlin/com/failureludo/data/GameSessionStore.kt`

### Tasks
- [x] Add schema version field to setup/game snapshots.
- [x] Reject incompatible old schema to trigger clean reset behavior.
- [x] Persist and restore `Player.id`.
- [ ] After engine model migration, convert remaining color-keyed state fields to ID-keyed JSON.
- [x] Winners and dice JSON moved to ID-keyed shape.
- [x] Convert remaining color-keyed event JSON actor fields to ID shape.

### Coupling Notes
- JSON currently stores event and winner color identity; this must follow engine model migration.

## Workstream C — Name-First UI/UX
### Scope
- `app/src/main/kotlin/com/failureludo/ui/screens/GameSetupScreen.kt`
- `app/src/main/kotlin/com/failureludo/ui/screens/GameBoardScreen.kt`
- `app/src/main/kotlin/com/failureludo/ui/screens/WinScreen.kt`
- `app/src/main/kotlin/com/failureludo/ui/components/LudoBoardCanvas.kt`

### Tasks
- [x] Remove color-name identity labels in setup/player chips/win badges.
- [x] Use player name for winner headline and side-rail labels.
- [x] Ensure all semantics/content descriptions are name-first.
- [x] Add truncation and long-name polish in all chips/cards.
- [x] Verify no visible identity text uses color labels.

### UX Quality Bar
- Name is primary in all user-visible identity surfaces.
- Color stays as visual support only.
- Fast path works: skip edits -> starts with Player-1..Player-4.

## Workstream D — Validation and Test Coverage
### Scope
- Engine tests (new)
- ViewModel/session tests (new)
- Compose UI tests (new)

### Tasks
- [x] Add engine tests for ID-based winner attribution, ID-keyed dice initialization, PlayerId bounds, and default placeholder names.
- [ ] Add session tests for schema-version reset behavior.
- [ ] Add setup/game/win UI tests for name-first identity rendering.
- [ ] Manual QA pass for setup -> game -> win -> replay path.

## Dependencies and Triggered Changes
- Migrating `GameState` identity fields triggers updates in:
  - `GameViewModel` turn/dice/win consumption
  - `GameBoardScreen` rail dice lookup and current-turn checks
  - `GameSessionStore` serialization contract
  - `WinScreen` winner resolution
- Migrating `GameEvent` actor ownership triggers updates in:
  - feedback event handling in `GameBoardScreen`
  - session event serialization and deserialization

## Rollout Sequence (Recommended)
1. Finalize engine ID-keyed state/event contracts.
2. Update app/viewmodel call sites.
3. Update persistence JSON shape for new contracts.
4. Complete UI/UX polish and accessibility semantics.
5. Add test coverage and run full verification.

## Risks
- Partial migration can create mixed ID/color ownership bugs.
- Session resets may surprise users if not communicated.
- Winner/event rendering can regress if ID-to-player mapping is inconsistent.

## Mitigations
- Keep migration atomic per layer and compile-check after each slice.
- Fail closed on schema mismatch (already started).
- Add helper lookups (`playerById`, `playerNameById`) to avoid repeated mapping bugs.

## Acceptance Criteria
- No user-visible identity text depends on color names.
- Internal actor ownership is ID-based in engine state and events.
- New sessions restore correctly; legacy sessions reset safely.
- Setup default and no-edit flow reliably produce Player-1..Player-4 identities.

## Status
- Plan status: In Progress
- Priority: High
- Last updated: 2026-03-27
