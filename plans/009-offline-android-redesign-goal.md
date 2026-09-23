# 009 — Offline Android redesign and release

## Goal

Build a polished, fully offline native Android Ludo app that the user can release and play.
Replace the existing presentation with a coherent new design rather than limiting work to
small cosmetic fixes. After releasing and playing the offline app, return to online development
and apply the finished design there.

## Branch and scope

- Branch: `feat/offline-improvements`, created by the user for this effort.
- Platform: Android, Kotlin + Jetpack Compose (`app/`).
- Modes: offline local pass-and-play and play against bots.
- The release experience must expose no online features and require no sign-in, network,
  or backend availability for startup or gameplay.
- Preserve the deterministic engine, rules, bot behavior, save/resume, undo/redo, history/replay,
  and reliable pawn selection unless a change is explicitly agreed.

## Presentation to rebuild

- App layout, navigation, home, setup, game controls, and results.
- Board geometry presentation, track markings, home areas, safe squares, and player identity.
- Pawn artwork, selection states, stacks, and touch interaction.
- Movement choreography, capture/return, finish, and turn transitions.
- Dice appearance, roll animation, result settling, and interaction feedback.
- Sound effects, timing, mixing, haptics, and feedback settings.
- Responsive layouts for Android screen sizes, readable contrast, accessibility, and reduced motion.

The visual direction has not been chosen. “Premium tabletop” was an assistant proposal only.
Establish the visual direction before a large implementation; use a concrete board/game-screen
prototype to assess it. Existing UI plans are background, not a requirement to retain the old appearance.

## Deferred work

Online multiplayer, matchmaking, Firebase feature development, React/web UI, deployment,
and web/native parity are paused. Plans 007 and 008 are historical/deferred context, not the
current task list. Avoid spending usage on analyzing those systems.

Existing online code can remain recoverable while offline navigation and startup are isolated.
Do not interpret this goal as permission to reset history, discard work, or delete online infrastructure
wholesale. Only inspect shared dependencies as needed to make the Android release fully offline.

## Delivery sequence

1. Establish a new Android visual direction and implement a representative game-screen prototype.
2. Rebuild the board, pawns, controls, movement, dice, and audiovisual feedback as a consistent system.
3. Bring home, setup, settings, results, and existing offline tools into the same design.
4. Validate full offline games, save/resume, bot turns, captures, stacked moves, team mode,
   replay, and usability/performance on Android devices.
5. Prepare the offline Android release, release with the user's publishing authorization,
   and incorporate feedback from actual play.
6. Resume online development only when the user explicitly returns to it; carry over the refined design.

## Current handoff

Scope is confirmed; the redesign has not been implemented and no visual style has been approved.
The previous web/online UI review is outside this effort. The next design task is the native Android
board/game-screen direction, followed by the offline presentation rebuild.
