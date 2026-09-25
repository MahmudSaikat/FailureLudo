# Fastlane for the Android app

Package: `com.failureludo`. Run commands from the repository root.
Fastlane and its dependencies are pinned by `Gemfile.lock`.

## Installation

Use Ruby 3.3+ (the local setup uses the version in `.ruby-version`) and Bundler:

```sh
bundle config set --local path vendor/bundle
bundle install
./bin/fastlane --version
./bin/fastlane lanes
```

On this workstation, Ruby is installed under the ignored
`.local-tools/mise-data/installs/ruby/` directory. `bin/fastlane` selects that Ruby
automatically. On other machines it uses Ruby/Bundler from `PATH`. For local
dependency installation, add that Ruby version's `bin` directory to `PATH` first:

```sh
export PATH="$PWD/.local-tools/mise-data/installs/ruby/$(cat .ruby-version)/bin:$PATH"
bundle install
```

No system Ruby installation or shell-profile changes are needed here. The launcher
disables inherited `DEBUG` logging because Supply HTTP logs can include access tokens.

## Google Play credentials

1. Select or create a project in [Google Cloud](https://console.cloud.google.com/).
2. Enable the [Google Play Android Developer API](https://console.cloud.google.com/apis/library/androidpublisher.googleapis.com).
3. Create a service account in [IAM & Admin → Service Accounts](https://console.cloud.google.com/iam-admin/serviceaccounts).
   A Google Cloud project role is not needed just for Play publishing.
4. Under that service account, open **Keys → Add key → Create new key → JSON**.
   Keep the downloaded key private, outside version control. The directory
   `fastlane/credentials/` is ignored if you choose to store it in this checkout.
5. In [Play Console](https://play.google.com/console), open **Users and permissions →
   Invite new users**, enter the service account email, and grant app-specific access
   to FailureLudo: view app information, release to testing tracks, and manage testing
   tracks/tester lists. Add store-presence or production-release permissions only
   when those capabilities are needed.
6. Copy `fastlane/.env.example` to `fastlane/.env` and set `SUPPLY_JSON_KEY` to the
   key's absolute local path. This file is ignored. Never paste the JSON key into
   chat, commit it, or use a personal Google password for Fastlane.
7. Run `./bin/fastlane android verify_play` to verify access to this app, not merely
   whether the key can authenticate. It lists track version codes without publishing.

Google's [setup guide](https://developers.google.com/android-publisher/getting_started)
is authoritative; linking a Play account to a Cloud project is no longer required.
For future CI use, Fastlane also supports Workload Identity Federation, avoiding a
long-lived downloaded key.

## Commands

```sh
# App-access check, no release upload/publication
./bin/fastlane android verify_play
./bin/fastlane android verify_play track:production

# List all tracks and visible APK/AAB version codes without publishing
./bin/fastlane android inventory_play

# Build and publish to the existing closed-testing track (alpha)
./bin/fastlane android closed

# Local signed bundle; uses the existing ignored keystore.properties
./bin/fastlane android build_release

# Builds and uploads an AAB to an uncommitted edit, then validates it
./bin/fastlane android internal validate_only:true

# Builds and publishes to the internal testing track
./bin/fastlane android internal
```

Before uploading, choose a `versionCode` higher than **all** previously uploaded
artifacts, including custom testing tracks. A single track query is not a complete
version-code inventory. Version bumps remain explicit in `app/build.gradle.kts`.
The internal lane preserves existing store metadata, screenshots and release notes.
It does not configure testers. Production publication is outside this initial setup.

Validation uploads a bundle but does not commit the edit. It is not a complete
policy/readiness check. First-app setup, required declarations, testing eligibility,
and review can still require Play Console work. See the offline release checklist
in [plan 009](../plans/009-offline-android-redesign-goal.md).

Reference: [Fastlane Android setup](https://docs.fastlane.tools/getting-started/android/setup/)
and [Supply options](https://docs.fastlane.tools/actions/supply/).
