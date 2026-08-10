# Privacy and data

KMK is an Android client, not a hosted recommendation service. Recommendation, rating, exposure, source-evaluation, linked-version, and OCR state is stored through the app's local repositories and settings. Installed source extensions and configured trackers can still contact their own services when the user invokes the related feature.

## Local data

- Ratings, Not Interested, recommendation exposure, source evaluation, source quality, linked versions, and feature settings are stored locally.
- OCR recognizes downloaded page images on the device and stores recognized text in the local database. ML Kit does not send the input images or recognized text to Google.
- OCR text is excluded from backup and sync payloads and can be cleared by chapter, manga, status group, or entire index.
- Action History stores bounded local receipts for supported actions. It does not make remote tracker writes or Android package operations inherently reversible.

## Network boundaries

For You, source evaluation, matching, metadata enrichment, extension installation, and tracking can make requests through the same installed extensions or configured services used by the surrounding Komikku feature. A source-specific failure is isolated and presented with a short category instead of a raw request, URL, credential, or exception message.

The bundled ML Kit SDK may contact Google for model or compatibility updates and may send SDK performance and utilization metrics. See the [ML Kit terms and privacy notice](https://developers.google.com/ml-kit/terms). This is separate from the page images and recognized text, which remain on the device.

The inherited analytics and crash-reporting adapter accepts only official packages signed by the official certificate, so it does not initialize for `app.komikku.kmk`. This public tree does not include Google service credentials, Google Drive OAuth client configuration, signing keys, or a crash-report endpoint. A release maintainer must provide a fork-owned Google Drive installed-app OAuth client configuration at build time before offering that sync provider. The configuration identifies the app to Google's OAuth service; user authorization tokens remain in app storage. Analytics and crash reporting remain excluded. The updater is disabled unless a build explicitly enables it; when enabled, it checks only this fork's repository.

## Backups and exports

Backups include supported KMK state that is difficult to recreate, such as ratings, recommendation preferences, links, and source evaluation. They do not erase remote side effects, uninstall extensions, restore the clipboard, or replace a device backup.

Exports use Android's document APIs. Cleanup is limited to the exact document created by the operation and never scans an arbitrary folder for similarly named files.

## Screenshots and diagnostics

Evaluation Mode replaces source and repository identities with neutral labels for review. It does not anonymize manga artwork, titles, account state, reader pages, notifications, or every part of Android system UI. Before sharing evidence, review the complete image and any companion XML for private content, identifiers, paths, URLs, and status-bar information.

Public documentation includes only reviewed screenshots and semantic XML. Raw device dumps, unrestricted logs, databases, preferences, account data, and local paths are not publication artifacts.
