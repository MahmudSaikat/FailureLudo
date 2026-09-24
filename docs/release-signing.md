# Android release signing

The canonical upload keystore on this laptop is:

`/home/mahmud-saikat/.android/signing/failureludo/google-play-upload.jks`

Alias: `key0`

Google Play's expected upload certificate SHA-1:

`F7:95:24:B5:1C:24:B8:93:BE:1D:34:B3:2A:D1:25:83:E9:46:06:0A`

Gradle reads the path and passwords from the root `keystore.properties`, which is
ignored by Git. Android Studio's Generate Signed Bundle / APK dialog also points
to this canonical path on this laptop. The key directory has mode 700 and the key
and credentials file have mode 600. Never commit keystores or passwords.

The original `/home/mahmud-saikat/FailureLudoKeystore.jks` remains a local recovery
copy. The project's older `release-keystore.jks` is **not** the Google Play upload
key; do not select it. Its SHA-1 starts `DC:A0:F9:9C`.

Every Gradle release build checks the configured certificate against the expected
fingerprint before packaging. This also covers the existing Fastlane build path
and Android Studio release builds. Missing or mismatched signing configuration
fails with an explanation; debug builds do not require the upload key.

Build both release files:

```sh
./gradlew :app:assembleRelease :app:bundleRelease --offline
```

On another computer, securely transfer the verified key, create a local
`keystore.properties` with its absolute path and alias, and supply its passwords
locally. Keep an encrypted backup outside this laptop; the local recovery copy
does not protect against loss of the laptop. Change the pinned fingerprint only
after Google Play accepts an intentional upload-key reset.

## Native debug symbols

Release builds request `ndk.debugSymbolLevel = "FULL"` so available native symbols
are automatically included in future bundles. The current AndroidX graphics path
and DataStore shared-counter dependencies ship stripped libraries: all four ABIs
have neither `.symtab` nor `.debug_info`. Enabling collection therefore produces no
native symbol archive for these dependencies, and Play may still show its advisory
warning. Do not upload a fabricated symbol archive or rebuild different binaries
as substitute symbols. Matching unstripped artifacts must come from the dependency
producer. The bundle already includes the R8 mapping for Kotlin/Java crash reports.

Verified with a release build and ELF section inspection. Reference:
https://developer.android.com/build/include-native-symbols
