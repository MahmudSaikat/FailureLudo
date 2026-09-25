# Online Android testing preparation

## Decision — 25 September 2026

Online Android development may resume before the offline Play Store release.
The online version is for private testing; it must not be submitted to Play Store.
The production release remains entirely offline.

- Offline release branch: `feat/offline-improvements`.
- Online development branch: `feat/online-testing`.
- Shared starting commit: `6fa82375e580c83ca1d2a9f8610146feed8026ed`.
- This preparation changes branch guidance only; it does not enable multiplayer,
  change Android build variants, deploy Firebase, or create a new APK.

## First implementation: isolate Android builds

Introduce `offline` and `online` product flavors before enabling online navigation.
Keep `com.failureludo` for offline production. Use `com.failureludo.online.test`
and the launcher name “Failure Ludo Online Test” for private online builds so both
apps can be installed together with separate saves and app data.

Confine authentication, online repositories, screens, view models, Firebase and
Google sign-in dependencies, Google Services configuration, and network permissions
to the online source set. Offline startup and gameplay must need no credentials,
backend, or network. Inspect the merged offline manifest and dependency graph to
verify this; hiding online buttons is insufficient.

Keep online release variants disabled initially. Use an online debug APK for direct
installation by testers. Preserve offline upload-key verification when flavor task
names change, and update release commands to select the offline variant explicitly.
Do not reuse production Firebase configuration blindly: configure the separate test
app identity and an appropriate test backend before enabling authentication.

Current evidence: `AppNavigation.kt` exposes offline routes, but
`app/build.gradle.kts` still declares Firebase/sign-in dependencies globally and
`app/src/main/AndroidManifest.xml` declares network permissions. Existing auth and
online code remains under `app/src/main`; it has not been validated for this effort.
The offline branch is unchanged by this preparation and still needs its release checks.

## Then restore private multiplayer

Reuse the native board and pure `game-engine` rules. Review the existing Android
online implementation and [earlier multiplayer plan](../plans/007-online-multiplayer-plan.md)
before restoring sign-in, room creation/joining, waiting rooms, and online games.
Validate authorization, synchronized turns, reconnects, and error handling using
a deliberately configured test backend. Do not treat the old plan's checkboxes as
proof of present functionality.

Build and verify both variants; confirm the offline build works without internet
and install both apps together. Test a complete multiplayer game across two devices
before calling the online APK playable. Device review remains with the user unless
requested. No publication or backend deployment is part of this preparation.

## Carrying shared improvements

Keep online-specific work on the online branch. Transfer reviewed, self-contained
shared fixes selectively; do not merge the whole online branch into the offline
release branch. Any build-isolation change needed for offline production must also
be applied and verified on the offline branch before its release.

The user's decision here supersedes the older sequencing requirement in
[plan 009](../plans/009-offline-android-redesign-goal.md) to finish offline publication
before resuming online development. Its offline release requirements still apply.
