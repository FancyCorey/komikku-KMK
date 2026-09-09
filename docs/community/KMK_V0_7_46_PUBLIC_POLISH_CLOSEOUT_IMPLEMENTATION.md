# KMK-Recs v0.7.46 Public Polish Closeout â€” Implementation Report

Status: **Public test / community beta candidate.** Not an official release â€” no manual device QA
and no release signing were performed as part of this pass. Automated readiness (build, unit tests,
lint, static checks) passed in full; see "Manual QA Status" below.

Plan implemented: `docs/community/KMK_V0_7_46_PUBLIC_POLISH_CLOSEOUT_PLAN.md`

## 1. Version Identity

- `KmkRecsReleaseNotes.VERSION_CODE`: `747` â†’ `748`.
- `KmkRecsReleaseNotes.VERSION_NAME`: `"KMK-Recs v0.7.45"` â†’ `"KMK-Recs v0.7.46"`.
- New changelog entry added to `KmkRecsReleaseNotes.MARKDOWN` (v0.7.45 entry preserved below it).
- Android `versionCode`/`versionName` (`88`/`"1.13.6"`) are unchanged â€” these track upstream Komikku
  baseline, not KMK-Recs feature version, per existing convention.

## 2. Summary of User-Visible Changes

- OCR error messages shown in the OCR Search screen are now short, localized, stable messages
  instead of raw exception text (e.g. "Storage error", "Could not read image", instead of a Java
  exception string).
- OCR search results now have a per-row overflow menu with "Clear OCR for this chapter" and
  "Clear OCR for this manga", each behind a confirmation dialog that explains only recognized text
  is removed, not downloaded images.
- A new "Clear empty/failed rows" button appears on the OCR Search screen when there are empty or
  failed rows to clear.
- Source Evaluation result rows show clearer, translated error messages instead of raw technical
  text (a second call site that v0.7.45's fix had missed).

No new recommendation features were added. No existing flows were redesigned. No database schema
changes were made â€” all new deletion controls call pre-existing `OcrIndexRepository` methods
(`deleteByManga`, `deleteByChapter`, `deleteEmptyAndFailed`) that already existed but had no
reachable UI.

## 3. OCR Error/Privacy Changes

- Added `exh.ocr.OcrErrorClassifier` (new file): pure function classifying a `Throwable` into one
  of six stable `OcrErrorKey` values (`Storage`, `ImageDecode`, `NoDownloadedPages`, `Cancelled`,
  `PermissionOrFileAccess`, `Internal`), each with a fixed `storageKey` string. 9 unit tests added
  in `OcrErrorClassifierTest`, all passing.
- `OcrIndexService.kt`: both the enumerate-pages failure path and the per-page failure path now
  store `OcrErrorClassifier.classifyToStorageKey(e)` in `ocr_indexed_page.error_message` instead of
  `e.message ?: "Unknown error"`.
- `OcrIndexService.kt` per-page failure logcat line changed from including `manga.title` and
  `chapter.name` to only `manga.id` and `chapter.id` (plus page index) â€” no manga/chapter names or
  OCR text appear in logs.
- `OcrSearchScreenModel.kt`: `State.errorMessage: String?` replaced with
  `State.errorKey: OcrErrorKey?`; all catch sites classify instead of storing raw text.
- `OcrSearchScreen.kt`: error snackbar renders a localized string mapped from `OcrErrorKey` instead
  of a raw string.
- Legacy rows written before v0.7.46 may still contain raw exception text in
  `ocr_indexed_page.error_message`. These are **not** rewritten (no backfill/migration â€” this pass
  does not touch the database schema). The UI does not attempt to parse or classify legacy raw text;
  it only displays the classified key path for errors it classifies itself in the current session.

## 4. OCR Deletion/Storage Control Verification

Findings: `OcrIndexRepository.deleteByManga`, `deleteByChapter`, `deleteAll`, `deleteEmptyAndFailed`,
and `deleteOldEngineRows` already existed in the repository layer, but only `deleteAll` and
`deleteOldEngineRows` were reachable from `OcrSearchScreen.kt`. Per-manga and per-chapter deletion
existed in the repository but had **no UI** calling them.

Changes:
- `OcrSearchScreenModel.kt`: added `deleteOcrForManga(mangaId)`, `deleteOcrForChapter(chapterId)`,
  `clearEmptyAndFailed()` â€” thin wrappers around the existing repository methods, updating in-memory
  state and refreshing stats afterward.
- `OcrSearchScreen.kt`: added a per-result overflow menu (`IconButton` + `DropdownMenu`) with "Clear
  OCR for this chapter" / "Clear OCR for this manga", each routed through a confirmation
  `AlertDialog` before the action runs. Added a "Clear empty/failed rows" button, shown only when
  `stats.emptyPages > 0 || stats.failedPages > 0`.
- All confirmation dialogs use KMR strings that state clearing removes recognized/searchable text
  only, not downloaded manga page images.
- OCR storage stats (page/word counts) were already visible on the OCR Search screen and remain so;
  no changes needed there.
- Per-manga/per-chapter deletion is reachable only from the OCR Search screen, not from the manga
  detail or library screens â€” documented as a known limitation, not fixed in this pass (out of
  scope: the plan asked to verify/add reachable controls, not to add new entry points elsewhere).

## 5. Non-OCR User-Facing Error Hygiene

Ran `rg -n "errorMessage = e\.message|Unknown error|e\.message \?:" app/src/main/java/exh` and
traced every hit to its consumer.

Fixed (reach user-visible UI):
- `SourceEvaluationRunner.kt` (~line 327, `recordExtensionError`): second raw-text path into
  `SourceEvaluation.errorMessage` (read by `EvaluationResultRow` subtitle in
  `SourceEvaluationScreen.kt`), missed by v0.7.45's earlier fix. Now uses
  `SourceEvaluationProbeErrorClassifier.classifyToStorageKey(e)`.
- `BestVersionCompareScreenModel.kt`: 3 sites (`ChapterError`, `PreviewError`,
  `BestVersionStep.Error`) rendered directly as `Text(...)` in `BestVersionCompareScreen.kt`. Added
  `exh.recs.RecommendationErrorClassifier` (new shared classifier) and a
  `recommendationErrorText(key)` composable in the screen that maps classified keys to localized
  strings, passing through the one pre-existing non-exception-derived literal
  (`"Could not load origin manga."`) verbatim.
- `RecommendationBundleImporter.kt`: `"Could not open file"` literal â†’ KMR string; IO exception
  message â†’ classified via `RecommendationErrorClassifier`.
- `RecommendationBundleValidator.kt`: JSON parse exception message no longer stored raw; changed to
  an empty string that triggers the screen's existing generic fallback (`LoadErrorKey.MalformedJson`
  null-check), fixed up in `RecommendationBundleImportScreenModel.kt` with `.ifBlank { null }` so the
  fallback actually triggers.

Confirmed diagnostic-only (not user-visible), left unchanged, with reasoning:
- `RecommendationImportItemState.Error(e.message ?: "Unknown error")` â€” stored but the import screen
  always renders a generic `rec_bundle_import_state_error` KMR string regardless of message content.
- `RecommendationBundleLibraryAdder.Outcome.Error(e.message ?: "Unknown error")` â€” only checked via
  `is ... -> failed++`; message field never read/displayed.
- `BrowsePersonalRecommendationsScreenModel.kt:1089` (`errorMessage = e.message?.take(200)`) â€”
  written into `RecommendationDiscoveryProgress.errorMessage` for retry/debug bookkeeping; no
  presentation-layer file reads this field.
- `SourceEvaluationJob.kt:80`, `SourceEvaluationRunner.kt:180` â€” write
  `SourceEvaluationQueueState.errorMessage`, read only via `lastError` into the clipboard diagnostics
  export (`SourceEvaluationDiagnosticsBuilder`/`copyDiagnosticsToClipboard()`), never shown in a live
  Composable/Text/snackbar/dialog. Treated as diagnostic export text, same category as other
  clipboard-diagnostics fields already in the codebase â€” not changed in this pass.
- `SourceEvaluationStartupRecovery.kt:129` â€” `Result.errorMessage` is set but never read by any
  caller; logged before construction, result otherwise discarded.
- `SourceRecommendationQualityJob.kt:85`, `SourceRecommendationQualityRunner.kt:118` â€” write
  `SourceRecommendationQualityQueueState.errorMessage`, never read by `SourceEvaluationScreen.kt`
  (the rec-quality UI section only reads `status`/`currentSourceName`/counts from this state). Note:
  the UI-visible rec-quality failure reason (`SourceRecommendationFit.errorMessage`, shown as a
  badge/expandable hint) is a *different* field, already populated via classified storage keys
  (`SourceRecommendationFitFailureClassifier`), not raw `e.message`.
- `GalleryAdder.kt:192` (`e.message ?: "Unknown error!"`) â€” confirmed SY-owned/EH-inherited legacy
  code, no `// KMK -->` markers. Out of scope for this KMK-focused cleanup pass.

## 6. Documentation/Versioning Updates

- `RECOMMENDATION_VERSIONING.md`: added the missing retroactive `KMK-Recs v0.7.45` entry, plus a new
  `KMK-Recs v0.7.46` entry.
- `CURRENT_STATE.md`, `NEXT_WORK.md`: updated to v0.7.46, with v0.7.45 sections preserved as history.
- `docs/community/KMK_PUBLIC_README_DRAFT.md`: version bumped to v0.7.46, APK filename updated, added
  explicit debug-signed-not-release-signed note.
- `docs/KMK_MARKDOWN_ENCYCLOPEDIA.md`: fixed 2 wrong `KmkRecsReleaseNotes.kt` path references; updated
  community/public-readiness pointer to this pass's plan.
- `docs/recommendations/KMK_RECS_DEFERRED_FEATURE_MASTER_IMPLEMENTATION_PLAN.md`: marked historical
  with a pointer to current work.
- `docs/recommendations/README.md`: fixed 3 mojibake occurrences (`ÃƒÂ¯Ã‚Â¿Ã‚Â½` sequences from a prior
  encoding mishap).
- `docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md`: SEC-08 marked fixed in both tables (per-chapter/
  per-manga deletion now reachable via OCR Search overflow menu); "OCR Logs" section updated to
  describe the sanitized log format (`mangaId`/`chapterId`, no title/chapter name); the "not confirmed
  in this audit" note about deletion UI accessibility updated to reflect the new controls and their
  actual entry point (OCR Search screen only, not library/manga-detail).
- `docs/ocr/README.md`: added a v0.7.46 section documenting the error-key classification, log
  sanitization, and the full set of OCR clearing controls with their confirmation-dialog behavior.

## 7. Tests, Lint, and Builds Run

All run with repo-local JDK 17 (`.tools/jdk17/jdk-17.0.19+10`).

| Command | Result |
|---|---|
| `.\gradlew.bat spotlessApply` | Initially failed â€” ktlint flagged a mixed `&&`/`\|\|` condition without parentheses in `OcrErrorClassifier.kt:50`. Fixed by adding parentheses. Re-run: **BUILD SUCCESSFUL**. |
| `.\gradlew.bat spotlessCheck` | **BUILD SUCCESSFUL** |
| `.\gradlew.bat :app:testDebugUnitTest` | **BUILD SUCCESSFUL** â€” 963 tests, 0 failures, 0 errors (up from 954 in v0.7.45; +9 from `OcrErrorClassifierTest`) |
| `.\gradlew.bat :app:lintKmkPublicTest` | **BUILD SUCCESSFUL** â€” only pre-existing, unrelated warnings (redundant `else`, unnecessary casts/safe-calls elsewhere in the codebase), no errors |
| `.\gradlew.bat :app:assembleDebug` | **BUILD SUCCESSFUL** |
| `.\gradlew.bat :app:assembleKmkPublicTest` | **BUILD SUCCESSFUL** |

Static checks (all passed / clean):
- `rg -n "errorMessage = e\.message|Unknown error|e\.message \?:" app/src/main/java/exh` â€” every
  remaining hit traced and documented above (diagnostic-only or SY-owned).
- `rg -n "raw_text|normalized_text|ocr_indexed_page" app docs` â€” confirmed OCR text storage remains
  local-only, documented consistently across security review, database audit, and OCR docs.
- `rg -n "KMK-Recs v0\.7\.45|KMK-Recs v0\.7\.46|kmk\.7\.45|kmk\.7\.46" ...` â€” active docs point to
  v0.7.46; v0.7.45 references remaining are correctly historical (changelog entries, prior
  implementation reports, prior plan docs).
- `rg -n "Ã¯Â¿Â½|Ã¢â‚¬â€|Ã¢â€ â€™|Ã‚Â·" ...` on the active recommendation/public docs â€” no matches (mojibake fixed).

## 8. APK Output and Metadata

- `app/build/outputs/apk/debug/output-metadata.json`: `"applicationId": "app.komikku.dev"` â€” confirmed.
- `app/build/outputs/apk/kmkPublicTest/output-metadata.json`: `"applicationId": "app.komikku.kmk"` â€” confirmed.

Copied:
- `C:\Users\USER\Downloads\Komikku\private\Komikku-v1.13.6-kmk.7.46-debug.apk`
- `C:\Users\USER\Downloads\Komikku\public\Komikku-KMK-PublicTest-v1.13.6-kmk.7.46-debug.apk`

## 9. Manual QA Status

**Not performed.** No physical device or emulator testing was done in this pass: no clean install,
no update-over-existing-install, no backup/restore, no on-device OCR indexing/search/clearing, no
Source Evaluation run, no phone/tablet layout check. Only automated build/test/lint/static-analysis
checks were run.

## 10. Known Limitations

- Legacy `ocr_indexed_page.error_message` rows written before v0.7.46 may still contain raw
  exception text; not backfilled (no schema/migration change made, per plan constraints).
- Per-manga/per-chapter OCR clearing is reachable only from the OCR Search screen, not from the
  manga detail or library screens.
- Clipboard-diagnostics-only raw-`e.message` sites (Source Evaluation queue-state fields) were left
  unclassified â€” they never reach live UI text, only an explicit user-initiated diagnostics copy
  action, matching the existing pattern for other diagnostic export fields.
- No manual device QA or release signing was performed (see above) â€” this build is a debug-signed
  community/public test candidate only.

## 11. Deviations From This Plan

None. All six implementation sections of the plan were completed as specified: versioning, OCR
error/privacy polish, OCR deletion/storage controls, non-OCR error hygiene, documentation hygiene,
and verification, followed by APK handoff after all checks passed.

---

**Bottom line:** Automated readiness: passed. Public test readiness: acceptable as a debug-signed
community beta candidate. Official/public release readiness: not complete until manual phone/tablet
QA and release signing are done.

