# KMK OCR Documentation

This folder tracks the separate OCR-specific Komikku experiment.

The OCR build is intentionally separate from the main KMK-Recs APK line. Recommendation work currently lives under `docs/recommendations/`; OCR planning and implementation notes should stay here unless the user explicitly decides to merge OCR into the normal app line later.

Current OCR planning files:

- `KMK_OCR_V0_1_0_DOWNLOADED_TEXT_SEARCH_PLAN.md`
- `KMK_OCR_V0_1_1_TEXT_INDEX_QUALITY_AND_SEARCH_HARDENING_PLAN.md`

Current OCR implementation notes:

- `KMK_OCR_V0_1_0_DOWNLOADED_TEXT_SEARCH_IMPLEMENTATION.md`

Branch/build convention:

- Suggested branch: `kmk-ocr-v0.1`
- Existing feature label: `KMK-OCR v0.1.0`
- Existing APK name: `Komikku-v1.13.6-kmk-ocr.0.1-debug.apk`

Next planned hardening build:

- Suggested feature label: `KMK-OCR v0.1.1`
- Suggested APK name: `Komikku-v1.13.6-kmk-ocr.0.1.1-debug.apk`
- Suggested versionCode: `81` or higher, because OCR v0.1.0 used `80`
- Main purpose: improve OCR recognition quality for long pages, add smart/partial search, show text-index storage, and distinguish processed pages from pages with usable recognized text.

Important installation note:

- If this APK uses the same `applicationId` as the current Komikku Dev build, it replaces/updates that app and must use a higher Android `versionCode` than the installed build.
- If it uses a separate `applicationId`, it can install side by side, but it may not share the same app database/preferences. Since OCR needs access to the user's library/download metadata, the first implementation should use the same app id on a separate branch unless the user explicitly asks for side-by-side behavior.
