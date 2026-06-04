# FailureLudo — Claude Context

## Project
Native Android Ludo game (Kotlin + Jetpack Compose). Pure deterministic game engine in `game-engine/` module. AI bots trained via Python pipeline in `ai-training/`.

**This is a real production app, not a study project.** All decisions and implementations must follow industry standards — architecture, security, error handling, scalability, and code quality.

## Active work
**Online multiplayer** — see `plans/007-online-multiplayer-plan.md` for the full checklist.
Currently on: Phase 6 (random matchmaking). Phases 1–5 complete.
One remaining Phase 5 task: register web app in Firebase console → get `appId` → add to `ludo-web/.env.local` → deploy.

## Key decisions
- Firebase backend (managed, no server), React web frontend
- Android + web browser players can play together
- Room codes + random matchmaking both needed
- Guest (anonymous) mode + Google Sign-In
- Platform badges (Android/browser icon per player)
- Watch mode via shared game URL
- Game history deferred to later

## Architecture reminder
GameEngine stays pure/local. Network layer feeds opponent moves into it via Firestore real-time listeners. Moves are synced, not full state.

## Commit & push rules
- After every major edit: commit and push automatically, no need to ask
- Commit message style: sentence case, imperative verb, no period. Examples: `Add Firebase auth to Android`, `Fix pawn selection on small screens`
- Use `git add -A`, then run `git status` and scan what is staged — unstage anything that looks like a secret, credential, or something that would break the repo structure before committing
- Never force-push to `main`
- Never skip hooks (`--no-verify`)
- Branch: work on `main` unless the user says otherwise

## Plans index
- 001–004: game experience, UI, player identity, pawn touchability
- 005: game history notation
- 006: self-play AI training
- 007: online multiplayer (current)
