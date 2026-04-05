# 004 - Device Validation Runbook

This runbook is for completing the remaining manual Phase 6 items once a device or emulator is connected.

## Preconditions
- Android device or emulator is visible in `adb devices -l`.
- App debug build is installable.
- Validation matrix reference: `plans/004-pawn-touchability-validation-matrix.md`.

## 1) Build and Install Debug App
Run:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 2) Capture Device Identity for Logs
Run:

```bash
adb devices -l
adb shell getprop ro.product.model
adb shell wm size
adb shell wm density
```

Record these in the validation matrix data capture template.

## 3) Execute Scenario Matrix (S1-S11)
For each scenario in `plans/004-pawn-touchability-validation-matrix.md`:
- Perform at least 20 taps in target zones for reliability scenarios.
- Record first-tap success count and accidental adjacent selections.
- Record readability score (1-5).

## 4) Screenshot Baseline Capture
Create a local folder for artifacts:

```bash
mkdir -p artifacts/004-screenshots
```

Capture screenshots using deterministic names:

```bash
adb exec-out screencap -p > artifacts/004-screenshots/compact-portrait-before.png
adb exec-out screencap -p > artifacts/004-screenshots/compact-portrait-after.png
adb exec-out screencap -p > artifacts/004-screenshots/typical-portrait-before.png
adb exec-out screencap -p > artifacts/004-screenshots/typical-portrait-after.png
```

Recommended captures:
- Compact portrait and landscape.
- Typical portrait and landscape.
- At least one dense 3-stack and one dense 4-stack board state.
- At least one board-edge overflow state.

## 5) Accessibility Scanner Pass
On device:
1. Install and open Android Accessibility Scanner.
2. Open game board scenarios with dense stacks and edge-adjacent pieces.
3. Run scans for touch target and contrast warnings.
4. Save screenshots of scan findings.

Log result summary:
- Critical warnings: count and IDs.
- Non-critical warnings: count and IDs.
- Any target-size warnings affecting gameplay controls.

## 6) Final Phase 6 Closeout
When complete, update:
- `plans/004-pawn-touchability-execution-checklist.md`
- `plans/004-pawn-touchability-validation-matrix.md`
- `plans/004-release-notes-draft.md`

Specifically:
- Mark scanner and screenshot items complete.
- Add metric comparison before vs after.
- Add artifact paths under Data Capture Template entries.
