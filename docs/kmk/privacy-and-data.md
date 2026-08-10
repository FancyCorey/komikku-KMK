# Privacy and data

KMK is an Android app, not an online recommendation service. Recommendations, ratings, recently shown cards, source evaluations, linked versions, and OCR data are stored locally through the app's database and settings. Installed extensions and configured trackers can still contact their own services when you use them.

## Local data

- Ratings, Not Interested, recently shown recommendation cards, source evaluations, source quality, linked versions, and feature settings are stored locally.
- OCR recognizes downloaded page images on the device and stores recognized text in the local database. ML Kit does not send the input images or recognized text to Google.
- OCR text is excluded from backup and sync payloads and can be cleared by chapter, manga, status group, or entire index.
- Action History stores small local undo records for supported actions. It cannot guarantee that a tracker update or Android package action can be reversed.

## Network boundaries

For You, source evaluation, matching, extra manga details, extension installation, and tracking can make requests through installed extensions or configured services. If one of those requests fails, the app shows a short explanation instead of exposing the request, URL, credentials, or raw error message.

The bundled ML Kit SDK may contact Google for model or compatibility updates and may send SDK performance and utilization metrics. See the [ML Kit terms and privacy notice](https://developers.google.com/ml-kit/terms). This is separate from the page images and recognized text, which remain on the device.

The inherited analytics and crash-reporting code accepts only official packages signed with the official certificate, so it does not start for `app.komikku.kmk`. This repository does not contain Google service credentials, Google Drive OAuth configuration, signing keys, or a crash-report address.

A release maintainer must provide this fork's own Google Drive OAuth configuration while building a release before Google Drive sync can be offered. That configuration identifies the app to Google; each user's authorization token remains in app storage. Analytics and crash reporting remain excluded. The updater is off unless a build enables it, and an enabled updater checks only this fork's repository.

## Backups and exports

Backups include supported KMK state that is difficult to recreate, such as ratings, recommendation preferences, links, and source evaluation. They do not erase remote side effects, uninstall extensions, restore the clipboard, or replace a device backup.

Exports use Android's document APIs. Cleanup is limited to the exact document created by the operation and never scans an arbitrary folder for similarly named files.

## Screenshots and diagnostics

Evaluation Mode replaces source and repository names with neutral labels. It does not hide manga artwork, titles, account information, reader pages, notifications, or every part of Android's interface. Before sharing a screenshot, check the whole image and any accompanying XML for private content, identifiers, paths, URLs, and status-bar information.

Public documentation includes only reviewed images and purpose-written XML references. Raw device dumps, full logs, databases, preferences, account data, and local paths are not included.
