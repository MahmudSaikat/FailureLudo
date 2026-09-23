# FailureLudo — Claude Context

## Project
Native Android Ludo game (Kotlin + Jetpack Compose). Pure deterministic game engine in `game-engine/` module. AI bots trained via Python pipeline in `ai-training/`.

**This is a real production app, not a study project.** All decisions and implementations must follow industry standards — architecture, security, error handling, scalability, and code quality.

## Active work
**Offline Android redesign and release** on `feat/offline-improvements`.
Read [the branch goal](plans/009-offline-android-redesign-goal.md) and
[the fresh design proposal](plans/010-offline-android-fresh-design-plan.md) before planning or implementation.

The immediate goal is to rebuild and polish the native Android offline experience, release it,
and play it before returning to online development. This is a complete presentation redesign:
layout, board, pawns, movement, dice animation, sound, and supporting screens.

## Current scope and decisions
- Target: native Android (`app/`, Kotlin + Jetpack Compose), offline play only.
- The intended release has no online features exposed to players; local play must work without
  internet, sign-in, or backend availability.
- Online multiplayer, Firebase feature work, React/web work, and web/native parity are paused.
  Do not spend review, research, or implementation effort on those areas unless explicitly requested.
- Preserve game rules, bots, saves, history/replay, and reliable selection while replacing presentation.
- Keep deferred online work recoverable. “Go back in time” describes product scope; it is not an
  instruction to reset Git history, switch branches, or delete online infrastructure wholesale.
- Visual direction is not yet selected. Premium tabletop was suggested, not approved.
- After the offline Android release and real play, revisit online work and carry over the finished design.

## Architecture reminder
GameEngine stays pure/local. Network layer feeds opponent moves into it via Firestore real-time listeners. Moves are synced, not full state.

## Commit & push rules
- After every major edit: commit and push automatically, no need to ask
- Commit message style: sentence case, imperative verb, no period. Examples: `Add Firebase auth to Android`, `Fix pawn selection on small screens`
- Use `git add -A`, then run `git status` and scan what is staged — unstage anything that looks like a secret, credential, or something that would break the repo structure before committing
- Never force-push to `main`
- Never skip hooks (`--no-verify`)
- Branch: stay on `feat/offline-improvements` for this effort; do not switch to `main`

## Plans index
- 001–004: game experience, UI, player identity, pawn touchability
- 005: game history notation
- 006: self-play AI training
- 007: online multiplayer (paused)
- 008: web/native parity (paused)
- 009: offline Android redesign and release (current goal)
- 010: fresh offline Android design plan (proposal; visual direction pending)
