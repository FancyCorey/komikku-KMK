# Build and verify Komikku KMK

This guide produces a local development build and checks the behavior covered by automated tests. A local debug APK is not a signed public release.

## Requirements

- JDK 17.
- The Android SDK versions requested by the Gradle build.
- Git and a checkout of this repository.
- Network access for the first dependency download, or a complete compatible Gradle cache for offline builds.

## Build

From the repository root, run:

```shell
./gradlew :app:assembleDebug
```

On Windows PowerShell, use `./gradlew.bat :app:assembleDebug`. The universal debug APK is written below `app/build/outputs/apk/debug/`.

The release package is `app.komikku.kmk`. Debug and test variants add their own suffixes so they do not overwrite a release install. Public releases also require a maintainer-controlled signing configuration; debug signing is not a release identity.

## Verify

Run the formatting, unit, and local-source checks before sharing a change:

```shell
./gradlew spotlessCheck :app:testDebugUnitTest :source-local:testDebugUnitTest :app:assembleDebug
```

For a change that affects a specific feature, also run its focused test classes. Device-dependent behavior still needs a supported Android target and privacy-safe evidence; a successful host build does not prove navigation, document-provider behavior, or extension interoperability by itself.

## Inspect the artifact

Before distributing an APK:

1. Record its SHA-256 hash.
2. Confirm its application ID, version, label, icon, and signing certificate.
3. Confirm that the update source refers to this fork.
4. Review bundled files for credentials and machine-specific paths.
5. Install only on an approved test target with an appropriate backup or data-preservation plan.

## Repository automation

Pushes and pull requests run formatting, debug unit tests, local-source tests, and a debug build without signing keys or service credentials. Manual preview and benchmark workflows upload unsigned, short-lived test artifacts; they do not create releases or tags.

Stable releases are created only from a `v*` tag in `FancyCorey/komikku-KMK`. Before pushing a release tag, the maintainer must configure `SIGNING_KEY`, `ALIAS`, `KEY_STORE_PASSWORD`, and `KEY_PASSWORD` with the fork's release signing identity. The `GOOGLE_CLIENT_SECRETS_JSON` secret must contain the fork's Google Drive installed-app OAuth client configuration so that the existing Google Drive sync provider remains usable. This client configuration is written only into the release build workspace; it is not committed or printed.

The release workflow builds without telemetry service credentials, signs the APKs, generates SHA-256 checksums, and creates a draft GitHub release. A human must verify the certificate, hashes, generated notes, update behavior, Google Drive sign-in, and APK behavior before publishing that draft.

## Upstream and fork remotes

Keep the official Komikku repository as an upstream source and this repository as the fork destination. Never publish private evidence, local build output, signing keys, device captures, or machine-specific configuration.
