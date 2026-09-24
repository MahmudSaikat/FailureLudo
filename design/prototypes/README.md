# Offline Android tabletop prototype

Working design for `feat/offline-improvements`; this is a playable first slice, not a release candidate.

- [Portrait gameplay](tabletop-portrait.png)
- [Landscape gameplay](tabletop-landscape.png): board beside player panels and the dice tray; Undo/Redo move into the header.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk` (generated, not committed).
- [Design and implementation handoff](../../plans/010-offline-android-fresh-design-plan.md)
- [Original audition audio and generator](../audio/README.md)

The new presentation includes the home screen, board/pawns, player panels, dice, smooth movement,
capture returns, offline navigation, and reduced-motion settings. Setup, results, and history
still use their existing screen designs. Audio is an original synthesized audition pack;
no third-party recordings have been selected.

Validation: the debug APK builds; 66 Android unit tests and 73 engine tests pass. The Android
UI smoke test passed on the Android 16 emulator in airplane mode, covering launch, setup,
dice settlement, rotation, and feedback settings. This does not replace full-game,
physical-device, audio, accessibility, or performance validation.

Test dependencies were updated to Espresso 3.7.0 and AndroidX JUnit 1.3.0 for the available
Android 16 emulator. Espresso's release notes document the InputManager compatibility fix:
https://developer.android.com/jetpack/androidx/releases/test#espresso-3.7.0
