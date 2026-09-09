# Release channels

Komikku KMK has two deliberately separate distribution paths. The separation protects public users from unfinished work while keeping development installs convenient for maintainers.

## Public releases

Public releases use the `release` build type and package name `app.komikku.kmk`. A published GitHub release in `FancyCorey/komikku-KMK` is the public update source. The app checks that source only when the release build enables the updater.

Create a public release from an existing `v*` tag. The release workflow verifies that the tag matches the app version, runs format and unit checks, builds the APKs, signs every APK, checks their package and certificate identity, writes SHA-256 files and a release manifest, then creates a draft GitHub release. A maintainer reviews the draft before publishing it.

Android accepts an update only when the package name matches, the new version code is higher, and the APK is signed with the same release key. Keep the public keystore and its passwords outside the repository and preserve them for every future public release.

The public workflow needs these repository secrets:

- `SIGNING_KEY`: Base64-encoded public release keystore.
- `ALIAS`: key alias inside that keystore.
- `KEY_STORE_PASSWORD`: keystore password.
- `KEY_PASSWORD`: key password.

Google Drive sync is independent of the updater. Supplying `GOOGLE_CLIENT_SECRETS_JSON` includes the fork-owned Google Drive client in that build. Leaving it unset still creates a valid public release, but the Google Drive option is not included.

## Development builds

Development builds use the existing debug package suffix, producing `app.komikku.kmk.dev`. They install beside the public app, are signed with the Android debug key, and do not enable the in-app updater. The Development validation build workflow runs the normal checks and uploads a short-lived APK artifact. It creates no GitHub release, tag, or public update.

For a genuinely private development history, keep the development branch in a separate private repository or private clone. Branches inside a public repository are public. A private GitHub release cannot be used as an anonymous in-app update feed, so private development builds are intentionally distributed through authenticated artifacts or direct testing rather than the public updater.

## Release checklist

1. Run the development build and test the intended changes on a device.
2. Merge or copy the approved code to the public release branch.
3. Increase `versionCode` and `versionName` together.
4. Run the public verification commands in [Build and verification](build-and-verify.md).
5. Create and push one new `v*` tag that exactly matches `versionName`.
6. Review the workflow artifact, `SHA256SUMS.txt`, `CERTIFICATE_SHA256.txt`, and `RELEASE_MANIFEST.json`.
7. Install the signed APK over an earlier public build and confirm Android accepts the update.
8. Publish the reviewed draft release. A draft is not visible to the in-app updater.

The release workflow also supports a manual run for an existing `v*` tag. Use that only to rebuild a reviewed tag after the required signing secrets are in place; do not move an already published tag.
