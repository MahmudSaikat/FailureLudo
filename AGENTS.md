# Current project direction

Read [CLAUDE.md](CLAUDE.md) for project conventions and
[the offline Android goal](plans/009-offline-android-redesign-goal.md) for current scope.

- Work on the existing `feat/offline-improvements` branch.
- Perfect and release the native Android offline app first.
- Rebuild its presentation: layout, board, pawns, movement, dice, sound, and supporting screens.
- Online development and web/native parity are paused. Do not analyze or change online/web
  features unless explicitly requested or strictly necessary to remove an offline entry-point dependency.
- Preserve gameplay rules and offline functionality. Keep deferred work recoverable.
- Return to online development only after the offline release and play phase, when the user resumes it.
- Implementation has started with a playable modern tactile tabletop prototype. Treat its style as a working direction pending user review; see plan 010 for the handoff.
