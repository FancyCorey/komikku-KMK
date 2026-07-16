# KMK OCR Documentation

This folder tracks the OCR-specific Komikku feature planning and implementation notes.

**v0.7.45 update:** OCR is no longer a separate build line. Code inspection during the v0.7 final
public-readiness pass confirmed `implementation(libs.mlkit.text.recognition)` has always been an
unconditional entry in `app/build.gradle.kts`'s main `dependencies { }` block, present in every build
type including `kmkPublicTest` -- there is no product-flavor or source-set gating that excludes it from
any build. OCR tables (`ocr_indexed_page`), migrations, and UI have therefore always shipped in the same
APK as KMK-Recs; the "separate line" framing below described the intended plan, not the actual build
output, and has been corrected. See
`docs/community/KMK_V0_7_FINAL_PUBLIC_RELEASE_READINESS_IMPLEMENTATION.md` for the full decision record.
A true build-time split (a dedicated product flavor that excludes ML Kit and OCR code) is deferred to
v0.8+ as a build-system change out of scope for this closure pass.

**v0.7.46 update (public polish + privacy hygiene):**

- OCR failure text shown to the user and stored for new failed pages is no longer raw exception text.
  Failures are classified into a small stable set of `OcrErrorKey` values (`OcrErrorClassifier`,
  `app/src/main/java/exh/ocr/OcrErrorClassifier.kt`) and mapped to localized KMR strings at render time.
  Legacy rows written before v0.7.46 may still contain raw exception text in
  `ocr_indexed_page.error_message`; the UI displays these generically rather than trying to parse them.
- OCR page-error logcat output no longer includes manga title or chapter name -- only `manga.id`,
  `chapter.id`, and the page index. See `docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md` ("OCR Logs").
- OCR text remains local-only: not included in backups, sync, or recommendation bundle export.
- OCR clearing controls, all reachable from the OCR Search screen:
  - clear all indexed OCR data,
  - clear empty/failed rows only,
  - clear rows indexed by an old/outdated OCR engine version,
  - clear OCR for a single chapter (per-result overflow menu),
  - clear OCR for a single manga (per-result overflow menu).
  All clear actions are confirmed with a dialog before running, and the dialogs explicitly state that
  clearing removes recognized/searchable text only -- it does not delete downloaded manga page images.
  OCR storage stats (page/word counts) remain visible on the OCR Search screen at all times.

Recommendation work lives under `docs/recommendations/`; OCR planning and implementation notes stay here.

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

