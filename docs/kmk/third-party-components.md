# Additional third-party components

Komikku KMK retains Komikku's existing dependency and open-source license screen. KMK adds the following direct dependencies to support its feature set and tests.

## ML Kit text recognition

`com.google.mlkit:text-recognition:16.0.1` provides the on-device text recognizer used by OCR Search Downloads. Input images and recognized text are processed on the device and are not sent to Google. The SDK may contact Google for updates or compatibility information and may send SDK performance and utilization metrics. Use is subject to the [ML Kit terms and privacy notice](https://developers.google.com/ml-kit/terms) and the incorporated Google API terms.

## SQLDelight SQLite driver

`app.cash.sqldelight:sqlite-driver` is used only by JVM migration tests. SQLDelight is distributed under the [Apache License 2.0](https://github.com/sqldelight/sqldelight/blob/master/LICENSE.txt). It is not added to the production APK by this test dependency.

## AndroidX test components

AndroidX test extensions and UI Automator are used by Android UI tests that check documented screens. They are `androidTest` dependencies and are not included in the production app.

## Google Drive sync

The inherited Google Drive sync provider uses Google's installed-app OAuth flow and app-data storage. Its client configuration is not stored in this repository. A release maintainer must provide a fork-owned configuration through the protected release workflow, verify its consent-screen and redirect settings, and test sign-in and revocation before publishing. User authorization tokens are stored by the app and should never be included in issues, screenshots, backups shared for support, or public build logs.

Before publishing a release, regenerate or inspect the app's open-source license list so all included libraries and notices match that exact build.
