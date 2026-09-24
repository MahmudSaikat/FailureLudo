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

## Google Play target API requirement — release blocker

Recorded on 2026-09-24 from the Play Console notice supplied by the user.
The notice requires this app to target **Android 16 (API level 36) or higher** and
identifies its current Android 15 (API level 35) target as non-compliant. It gives
**1 November 2026** as the deadline affecting the ability to publish app updates.
This records the app-specific notice; its policy wording/date has not been independently verified.

At the time of the notice, `app/build.gradle.kts` had `compileSdk = 35`, `targetSdk = 35`,
and `versionCode = 9`. Running the prototype on an Android 16 emulator does not satisfy
the target API requirement.

Before the offline production release:

- [x] Upgrade `targetSdk` to at least 36, with a compatible `compileSdk` and build toolchain.
- [ ] Review target-API behavior changes and validate offline gameplay, lifecycle, layout,
  audio, save/resume, and supported Android versions after the upgrade.
- [ ] Build a signed release with an appropriate higher version code; test through an
  internal, closed, or open testing track before production as needed.
- [ ] Publish the compliant version to **production** with the user's publishing authorization.
  A repository change or testing-track upload alone does not complete the notice's remedy.
- [ ] Confirm in Play Console that the production update succeeded and the issue is cleared.

Migration applied on 2026-09-24: `compileSdk = 36`, `targetSdk = 36`, and Android Gradle
Plugin 8.10.1. Gradle 8.11.1, Java 17, and `minSdk = 26` remain unchanged. AGP 8.10
[officially supports API 36 with this toolchain](https://developer.android.com/build/releases/agp-8-10-0-release-notes).
The version remains 1.0.8 / code 9 until release preparation; select a version code higher
than all uploaded Play artifacts before publication. Nothing has been published to Play.

Compatibility review: the native offline activity already enables edge-to-edge display,
and gameplay uses Compose `BackHandler`, with no legacy Back override or edge-to-edge opt-out.
The smoke test now also exercises system Back confirmation, dialog dismissal, and activity
recreation. See [Android 16 target behavior changes](https://developer.android.com/about/versions/16/behavior-changes-16).
The API 36 debug APK built successfully at `app/build/outputs/apk/debug/app-debug.apk`;
its packaged manifest confirms target API 36 and minimum API 26.
The migration passed Android debug/release Kotlin compilation and all 139 unit tests
(66 app, 73 engine). At the user's request, device testing is deferred to their manual test;
the expanded smoke test has not been run with target 36. Release bundling and lint were
stopped to conserve time/resources. Full-game, audio, physical-device, and older-Android
validation remain release checks.

## Current handoff

Implementation has started at the user's request. A first playable native Android prototype
uses modern tactile tabletop as a working direction, pending visual review. See the implementation
handoff in [plan 010](010-offline-android-fresh-design-plan.md). The broader redesign and release
are still in progress; setup/results styling and final audio remain unfinished.

The previous web/online UI review is outside this effort. Next, review the Android prototype
and refine its board/game-screen direction before expanding the presentation rebuild.
