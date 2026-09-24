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
