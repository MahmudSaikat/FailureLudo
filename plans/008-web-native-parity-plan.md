# 008 — Web ↔ Native parity & synchronized development

## Context
The React web app (`ludo-web/`) and the native Android app (`app/`) currently diverge in two ways:

1. **Look & feel** — Web uses a flat blue/gray theme with a simple SVG board. Native uses a
   warm cream/brown theme, Canvas-drawn pawn silhouettes, animated dice, and a richer set of
   screens/dialogs. The goal is for the web version to *look and behave like the native app* as
   closely as the platform allows.
2. **Modes** — Web is **online-only** and forces sign-in even to reach the lobby. Native is
   **offline-first**: Home → Setup → local pass-and-play / vs bot, with online as one option.

The two game engines (Kotlin `game-engine/` and the hand-ported TypeScript `ludo-web/src/engine/`)
are functional twins that can silently drift. We need a strategy to keep them in lockstep.

## Goals (staged, as requested)
- **Stage 1 — Web local mode + native look.** Give web an offline pass-and-play mode (Home →
  Setup → Game → Win) using the existing TS engine, and re-skin the whole app to the native theme.
- **Stage 2 — Sync the two apps.** Lock engine parity with a shared golden-test suite; align
  behavior, screens, and copy across platforms.
- **Stage 3 — Online features on both, in tandem.** Build remaining online features
  (random matchmaking, watch-mode polish, history) on web and native together.

## Decisions (defaults — adjust if needed)
- **Web local mode:** human pass-and-play first. A minimal random-move auto-player fills BOT seats
  so solo play never deadlocks; porting the real heuristic/AI bot from Kotlin is a Stage-2 task.
- **Engine sync strategy:** shared **golden test vectors** — a JSON suite of
  `(seed move-sequence) → expected serialized GameState` that *both* the Kotlin and TS engines must
  reproduce in CI. Catches drift without a rewrite. Kotlin Multiplatform migration deferred.
- **Native online:** already works (rooms, join codes, real-time move sync — plan 007 phases 1–5).
  We verify/polish rather than rebuild.
- **Board fidelity:** faithful **SVG re-skin** (cream/brown theme, pawn silhouettes, dice dots +
  roll animation, capture/finish effects). Full `<canvas>` rewrite deferred unless needed.

## Native reference values (source of truth for the re-skin)
From `app/.../ui/theme/Color.kt`:
- Players: RED `#D32F2F`, BLUE `#1976D2`, YELLOW `#F9A825`, GREEN `#388E3C`
- Light: RED `#FFCDD2`, BLUE `#BBDEFB`, YELLOW `#FFF9C4`, GREEN `#C8E6C9`
- Board: BoardWhite `#FAFAFA`, BoardCream `#F5F0E8`, SafeSquare `#E0E0E0`
- Theme: Primary `#5C3D2E` (brown), Secondary `#D4A044` (gold), Background `#F5EDDC` (cream),
  Surface `#FFFFFF`, OnSurface `#1C1B1F`
- Title: 🎲 + "LUDO" extra-bold letter-spaced, subtitle "Failure Edition" in gold.

## Stage 1 implementation (web)
Critical files (new unless noted):
- `ludo-web/src/theme.ts` — color constants + selectable color palette (mirrors `Color.kt` +
  `GameSetupScreen` HSV palette).
- `ludo-web/src/index.css` (modify) — re-skin tokens, buttons, cards, board layout, dialogs.
- `ludo-web/src/game/useLocalGame.ts` — local pass-and-play controller over the existing
  `engine/gameEngine.ts` (roll → no-moves/forfeit/selection → bot auto-play for BOT seats).
- `ludo-web/src/components/Dice.tsx` — dice face (dot layout per value) + roll scramble/scale anim.
- `ludo-web/src/components/LudoBoard.tsx` (modify) — native palette, safe-square markers, pawn
  silhouettes, movable glow.
- `ludo-web/src/screens/HomeScreen.tsx` — 🎲/LUDO title, New Game (local), Play Online, color row.
- `ludo-web/src/screens/SetupScreen.tsx` — mode, player count, names, Human/Bot toggle, colors
  (2-step like native), Start Game.
- `ludo-web/src/screens/LocalGameScreen.tsx` — board + dice rail + turn indicator + Win.
- `ludo-web/src/components/WinCard.tsx` — shared end-of-game card (used by local + online).
- `ludo-web/src/App.tsx` (modify) — Home is the **public** entry; Play Online routes through auth.

Reuse: `engine/gameEngine.ts` (`newGame`, `rollDice`, `selectPiece`, `advanceNoMoves`),
`engine/board.ts`, existing `onlineGameRepository.ts` / `authRepository.ts` for the online path.

## Stage 2 — engine parity (later)
- Add `engine-tests/vectors/*.json`: deterministic move logs + expected state snapshots.
- Kotlin: a JUnit test in `game-engine/` that loads each vector and asserts state equality.
- TS: a Vitest test in `ludo-web/` that loads the same vectors and asserts equality.
- Port `HeuristicBotMoveSelector` (and optionally `PolicyModel`) to TS for real web bots.
- Align dialogs/copy: pawn-stack chooser, home-entry chooser, quit/leave dialogs, feedback settings.

## Stage 3 — online features on both (later)
- Random matchmaking queue (Cloud Function or Firestore-only), wired into both clients.
- Watch-mode polish (share links, spectator count), online game history.

## Web platform limitations to flag
- **Haptics** — no reliable vibration on desktop browsers (mobile web partial via `navigator.vibrate`).
- **Audio autoplay** — browsers block sound until first user gesture; music/SFX need an unlock tap.
- **Background persistence** — a backgrounded tab may throttle timers; long-lived local sessions
  rely on in-memory state (no Android-style session store yet).
- **Native share sheet** — uses the Web Share API where available, else clipboard fallback.

## Verification
- `cd ludo-web && npm run build` (tsc + vite) must pass.
- `npm run dev`, then: Home → New Game → Setup (2–4 players, names, colors) → play a full local
  game to a win; confirm capture/finish/extra-roll/forfeit behavior matches native.
- Confirm Play Online still reaches auth → lobby → waiting → online game.
- Stage 2: `./gradlew :game-engine:test` and `npm run test` both green on shared vectors.
</content>
</invoke>
