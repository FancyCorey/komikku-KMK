# KMK-Recs v0.7.46 Public Polish Closeout Plan

Date: 2026-07-12

Status: implementation plan for Claude Code. Do not implement without user approval.

Target version: `KMK-Recs v0.7.46`

Target `KmkRecsReleaseNotes.VERSION_CODE`: `748`

Purpose: make the current v0.7 public-test build more polished and publishable without adding new recommendation features. This pass is a cleanup and release-readiness pass for the gaps left after `KMK-Recs v0.7.45`.

## Non-Negotiable Constraints

1. Do not add new recommendation behavior or redesign existing flows.
2. Do not change database schema unless absolutely required. This plan should not require a migration.
3. Do not change backup/sync/proto fields.
4. Do not remove OCR from the current build line in this pass. The current decision remains: OCR ships in the normal KMK build and public-test build.
5. Do not claim manual device QA is complete unless it was actually performed on device.
6. Keep all KMK strings in `i18n-kmk`; do not add new hardcoded user-facing English strings in Compose or screen models.
7. Keep the public-test build package ID as `app.komikku.kmk`.
8. Keep the personal/debug build package ID as `app.komikku.dev`.
9. Do not produce or copy final APKs until every code, doc, versioning, and verification item in this plan is complete.

## Required Reading Before Editing

Claude must read these first:

```text
docs/KMK_MARKDOWN_ENCYCLOPEDIA.md
docs/recommendations/DOCUMENTATION_RULES.md
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
RECOMMENDATION_VERSIONING.md
docs/community/KMK_V0_7_FINAL_PUBLIC_RELEASE_READINESS_PLAN.md
docs/community/KMK_V0_7_FINAL_PUBLIC_RELEASE_READINESS_AMENDMENT.md
docs/community/KMK_V0_7_FINAL_PUBLIC_RELEASE_READINESS_IMPLEMENTATION.md
docs/community/KMK_PUBLIC_README_DRAFT.md
docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md
docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md
docs/ocr/README.md
```

Then inspect these code areas before editing:

```text
app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt
app/src/main/java/exh/ocr/
app/src/main/java/exh/recs/share/
app/src/main/java/exh/recs/bestversion/
app/src/main/java/exh/recs/evaluation/
i18n-kmk/src/commonMain/moko-resources/base/strings.xml
app/build.gradle.kts
```

## Current Confirmed State

`KMK-Recs v0.7.45` builds and passes:

```text
spotlessCheck
:app:testDebugUnitTest
:app:assembleDebug
:app:assembleKmkPublicTest
```

The public APK line exists and uses:

```text
applicationId = app.komikku.kmk
```

The private/debug APK line uses:

```text
applicationId = app.komikku.dev
```

OCR is included in all current build types, including `kmkPublicTest`.

Remaining polish gaps from the v0.7.45 implementation report and follow-up code review:

- `RECOMMENDATION_VERSIONING.md` is missing a `KMK-Recs v0.7.45` entry and needs a v0.7.46 entry after this pass.
- `:app:lintKmkPublicTest` was not run in v0.7.45.
- Manual device QA was not performed.
- OCR still has raw `e.message` paths in UI state and stored page error rows.
- OCR logcat page errors include manga title/chapter name/page index. This is too casual for public beta privacy posture.
- OCR per-manga/per-chapter deletion APIs exist, but the audit did not confirm whether users can reach them easily from UI.
- Some active docs still have stale/historical phrasing or mojibake.
- Some public README wording still describes OCR as a "separate experimental feature line" even though it is now included in the same APK.
- Some non-OCR KMK import/best-version paths still surface raw exception messages. These are not the primary goal, but this pass should handle the user-facing ones if low risk.

## Phase 1: Version Identity And Release Notes

### Files

```text
app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt
RECOMMENDATION_VERSIONING.md
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/community/KMK_PUBLIC_README_DRAFT.md
```

### Required Changes

1. Bump the local KMK release marker:

```kotlin
VERSION_CODE = 748
VERSION_NAME = "KMK-Recs v0.7.46"
```

2. Add a short user-facing What's New entry for v0.7.46. Keep it feature-facing, not developer-facing. Suggested wording:

- Public polish and OCR safety cleanup.
- OCR errors are now shown as safer, clearer messages.
- OCR index cleanup controls are easier to find.
- Public test documentation and versioning were refreshed.

Do not mention internal markdown/doc hygiene in user-facing What's New unless it affects the user.

3. Update `RECOMMENDATION_VERSIONING.md`:

- Add the missing `KMK-Recs v0.7.45` entry.
- Add the new `KMK-Recs v0.7.46` entry after implementation.
- Mention both APK lines for v0.7.46:

```text
Private/debug: Komikku-v1.13.6-kmk.7.46-debug.apk
Public test:   Komikku-KMK-PublicTest-v1.13.6-kmk.7.46-debug.apk
```

- Explicitly state that OCR is included in the same KMK build line as of v0.7.45/v0.7.46.
- Preserve historical entries; do not rewrite older release history except for obvious mojibake if necessary.

4. Update `CURRENT_STATE.md` and `NEXT_WORK.md` to reflect:

- current version is v0.7.46 after implementation;
- v0.7.46 is a polish/public-readiness closeout, not a new recommendation feature phase;
- v0.7.45 remains the public-readiness foundation, v0.7.46 closes remaining practical polish gaps.

## Phase 2: OCR Error Hygiene And Privacy Polish

### Files

```text
app/src/main/java/exh/ocr/OcrSearchScreenModel.kt
app/src/main/java/exh/ocr/OcrIndexService.kt
app/src/main/java/exh/ocr/OcrIndexRepository.kt
app/src/main/java/exh/ocr/OcrSearchScreen.kt
i18n-kmk/src/commonMain/moko-resources/base/strings.xml
app/src/test/java/exh/ocr/
docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md
docs/ocr/README.md
```

### Problem

Current OCR code still has raw exception paths:

```kotlin
errorMessage = e.message
errorMessage = e.message ?: "Unknown error"
```

These occur in:

```text
OcrSearchScreenModel.kt
OcrIndexService.kt
```

OCR is privacy-sensitive because it stores recognized manga page text locally. Even if current exception messages are usually harmless, public polish should not depend on that. User-facing OCR errors should be stable, localized messages. Stored per-page error rows should not store arbitrary exception text going forward.

### Required Design

Create a small typed OCR error classifier. Keep it pure and testable.

Recommended new file:

```text
app/src/main/java/exh/ocr/OcrErrorClassifier.kt
```

Recommended model:

```kotlin
enum class OcrErrorKey {
    Storage,
    ImageDecode,
    NoDownloadedPages,
    Cancelled,
    PermissionOrFileAccess,
    Internal,
}
```

Use stable storage keys, not raw exception messages. Example:

```text
ocr_error_storage
ocr_error_image_decode
ocr_error_no_downloaded_pages
ocr_error_file_access
ocr_error_cancelled
ocr_error_internal
```

Add KMR strings for each user-facing message:

```text
ocr_error_storage
ocr_error_image_decode
ocr_error_no_downloaded_pages
ocr_error_file_access
ocr_error_cancelled
ocr_error_internal
```

### Required Behavior

1. In `OcrSearchScreenModel`, replace raw `e.message` UI state with a stable `OcrErrorKey` or stable string key.

2. In `OcrSearchScreen`, map the stable key to a KMR string.

3. In `OcrIndexService`, when writing failed OCR rows:

- do not persist raw exception messages into `ocr_indexed_page.error_message` for new failures;
- persist a stable key instead;
- keep backward compatibility for old rows that already contain raw text.

4. Do not expose recognized OCR text in error logs or diagnostics.

5. Sanitize OCR logcat:

Current log pattern includes:

```kotlin
"OCR: page error manga=${pageRef.manga.title} ch=${pageRef.chapter.name} page=${pageRef.pageIndex}"
```

Replace this with a less revealing diagnostic, such as:

```text
OCR: page error mangaId=<id> chapterId=<id> page=<index>
```

Do not log raw OCR text, normalized OCR text, manga dialogue, or page content.

6. If old raw error text appears in UI from stored rows, show a generic "OCR failed for this page" message unless it matches a known stable key. Do not display arbitrary raw stored exception messages in normal UI.

### Tests

Add focused tests in `app/src/test/java/exh/ocr/`:

- classifier maps `CancellationException` to `Cancelled`;
- file-not-found or permission-like exceptions map to `PermissionOrFileAccess`;
- decode-like exceptions map to `ImageDecode` when feasible;
- generic exceptions map to `Internal`;
- stable keys round-trip as strings;
- unknown legacy raw error text maps to a generic display message rather than being shown verbatim.

If direct UI string mapping is hard to unit test because of resource access, test the pure key/classifier layer and document the UI mapping.

## Phase 3: OCR Deletion And Storage Controls

### Files

```text
app/src/main/java/exh/ocr/OcrIndexRepository.kt
app/src/main/java/exh/ocr/OcrSearchScreenModel.kt
app/src/main/java/exh/ocr/OcrSearchScreen.kt
i18n-kmk/src/commonMain/moko-resources/base/strings.xml
docs/ocr/README.md
docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md
```

### Current State

`OcrIndexRepository` already exposes:

```kotlin
deleteByManga(mangaId)
deleteByChapter(chapterId)
deleteAll()
deleteOldEngineRows(currentEngineVersion)
deleteEmptyAndFailed()
getStats(engineVersion)
```

The audit says deletion APIs exist, but per-manga/per-chapter deletion UI accessibility was not confirmed.

### Required Changes

1. Confirm whether `OcrSearchScreen` already exposes:

- clear all OCR index;
- clear empty/failed rows;
- clear old engine rows;
- clear a manga's OCR rows;
- clear a chapter's OCR rows.

2. If per-manga/per-chapter actions are missing, add them in the OCR search result UI in a compact, non-invasive way.

Recommended UI:

- each OCR search result row gets an overflow/menu or secondary actions area;
- actions:
  - "Clear OCR for this chapter"
  - "Clear OCR for this manga"
- both require confirmation dialogs;
- after deletion, stats refresh and search results update.

3. Ensure the "Clear all OCR index" dialog clearly states that it removes local recognized text only and does not delete downloaded manga images.

4. Ensure storage usage remains visible:

- processed pages;
- recognized pages;
- empty pages;
- failed pages;
- manga count;
- chapter count;
- approximate storage size.

If already visible, do not duplicate it. If not visible, add a compact stats section using existing `OcrIndexStats`.

5. Update `docs/ocr/README.md` to accurately document:

- OCR is included in the main build;
- OCR text is local-only;
- OCR text is not backed up/synced/exported;
- clear all / clear failed-empty / clear old engine rows / clear manga / clear chapter controls;
- OCR errors are stored as stable keys after v0.7.46, with old legacy raw rows displayed generically.

6. Update `KMK_SECURITY_AND_PRIVACY_REVIEW.md`:

- mark SEC-08 as verified/fixed if UI is added or confirmed;
- update OCR Logs section after log sanitization;
- note that new OCR failure storage uses stable keys rather than raw exception text.

## Phase 4: User-Facing Error Hygiene Outside OCR

### Files To Inspect

```text
app/src/main/java/exh/recs/share/RecommendationBundleImportScreenModel.kt
app/src/main/java/exh/recs/share/RecommendationBundleLibraryAdder.kt
app/src/main/java/exh/recs/bestversion/BestVersionCompareScreenModel.kt
app/src/main/java/exh/recs/evaluation/SourceEvaluationJob.kt
app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityJob.kt
app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityRunner.kt
app/src/main/java/exh/recs/evaluation/SourceEvaluationRunner.kt
app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt
i18n-kmk/src/commonMain/moko-resources/base/strings.xml
```

### Required Work

Run:

```powershell
rg -n "errorMessage = e\.message|Unknown error|e\.message \?:" app/src/main/java/exh
```

For each hit:

1. Determine whether it reaches normal UI.
2. If it is diagnostic-only and not user-visible, document why in the implementation report.
3. If it reaches user-visible UI, replace it with:

- a typed key;
- a localized KMR string;
- or an existing Komikku/Mihon `formattedMessage` path if that is already the local convention.

Specific likely user-facing targets:

- `RecommendationBundleImportScreenModel.kt`
- `RecommendationBundleLibraryAdder.kt`
- `BestVersionCompareScreenModel.kt`
- OCR files from Phase 2

Do not over-refactor. The goal is to remove casual raw exception text from public-facing KMK flows, not to redesign every diagnostic object.

### Tests

Add or update focused tests for any new classifier/policy objects.

If UI-state classes are hard to test, isolate the classification into pure helpers and test those.

## Phase 5: Documentation Hygiene

### Files

```text
docs/recommendations/README.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/CURRENT_STATE.md
docs/recommendations/KMK_RECS_DEFERRED_FEATURE_MASTER_IMPLEMENTATION_PLAN.md
docs/KMK_MARKDOWN_ENCYCLOPEDIA.md
docs/community/KMK_PUBLIC_README_DRAFT.md
docs/community/KMK_V0_7_FINAL_PUBLIC_RELEASE_READINESS_IMPLEMENTATION.md
RECOMMENDATION_VERSIONING.md
```

### Required Changes

1. Mark `KMK_RECS_DEFERRED_FEATURE_MASTER_IMPLEMENTATION_PLAN.md` historical if its contents are no longer the active roadmap.

Do this by adding a prominent note at the top, not deleting the file:

```text
Historical note: this was a deferred feature master plan from an earlier phase. Current open work lives in NEXT_WORK.md and focused plans created after v0.7.45.
```

2. Clean `docs/recommendations/README.md`:

- remove mojibake such as `Ã¯Â¿Â½`, `Ã¢â‚¬â€`, `Ã¢â€ â€™` where it appears in active table text;
- ensure v0.7.45/v0.7.46 are listed as the latest active implementation reports after this pass;
- ensure stale active plans are labeled implemented or historical.

3. Update `docs/KMK_MARKDOWN_ENCYCLOPEDIA.md`:

- add this v0.7.46 plan/report as the current public polish closeout pointer after implementation;
- keep v0.7.45 public readiness docs as foundation docs;
- fix the wrong release-note code path currently listed if present. The actual file is:

```text
app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt
```

not:

```text
app/src/main/java/exh/recs/release/KmkRecsReleaseNotes.kt
```

4. Update public README:

- remove contradiction that says OCR is a separate experimental feature line;
- keep honest beta wording;
- state `v0.7.46` and final public test filename after build;
- include "debug-signed public test build" wording unless a proper release-signed APK is actually created.

5. Update `NEXT_WORK.md`:

- move completed v0.7.46 items into a shipped section after implementation;
- leave broader v0.8 items as future work;
- do not re-open items fixed by v0.7.45/v0.7.46.

## Phase 6: Lint And Verification

### Required Commands

Use the repo-local JDK 17:

```powershell
$env:JAVA_HOME='C:\Users\USER\Downloads\Komikku\komikku-source\.tools\jdk17\jdk-17.0.19+10'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
```

Run, in this order:

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat spotlessCheck
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintKmkPublicTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleKmkPublicTest
```

If `:app:lintKmkPublicTest` reports warnings but does not fail because project lint is non-blocking, still record the lint result honestly. If lint fails, fix the issue or document exactly why it is non-actionable.

### Static Checks

Run and record:

```powershell
rg -n "errorMessage = e\.message|Unknown error|e\.message \?:" app/src/main/java/exh
rg -n "raw_text|normalized_text|ocr_indexed_page" app docs
rg -n "KMK-Recs v0\.7\.45|KMK-Recs v0\.7\.46|kmk\.7\.45|kmk\.7\.46" docs app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt RECOMMENDATION_VERSIONING.md
rg -n "Ã¯Â¿Â½|Ã¢â‚¬â€|Ã¢â€ â€™|Ã‚Â·" docs/recommendations/README.md docs/recommendations/NEXT_WORK.md docs/recommendations/CURRENT_STATE.md docs/community/KMK_PUBLIC_README_DRAFT.md RECOMMENDATION_VERSIONING.md
```

Do not require historical implementation reports to be rewritten purely because they contain old versions. Active docs should be clean and current.

## Phase 7: APK Output And Handoff

Only after all phases pass:

1. Build private/debug:

```powershell
.\gradlew.bat :app:assembleDebug
```

2. Build public test:

```powershell
.\gradlew.bat :app:assembleKmkPublicTest
```

3. Copy/rename artifacts:

```text
C:\Users\USER\Downloads\Komikku\private\Komikku-v1.13.6-kmk.7.46-debug.apk
C:\Users\USER\Downloads\Komikku\public\Komikku-KMK-PublicTest-v1.13.6-kmk.7.46-debug.apk
```

4. Confirm metadata:

```text
app/build/outputs/apk/debug/output-metadata.json
app/build/outputs/apk/kmkPublicTest/output-metadata.json
```

Expected:

```text
debug applicationId = app.komikku.dev
kmkPublicTest applicationId = app.komikku.kmk
```

5. Document that these are debug-signed unless release signing is separately configured.

## Phase 8: Implementation Report

Create:

```text
docs/community/KMK_V0_7_46_PUBLIC_POLISH_CLOSEOUT_IMPLEMENTATION.md
```

Required sections:

1. Version identity.
2. Summary of user-visible changes.
3. OCR error/privacy changes.
4. OCR deletion/storage control verification.
5. Non-OCR error-hygiene changes.
6. Documentation/versioning updates.
7. Tests and lint run.
8. APK output and metadata.
9. Manual QA status.
10. Known limitations.
11. Deviations from this plan.

The report must not claim "public release ready" unless:

- lint was run;
- automated tests passed;
- APK metadata was verified;
- manual device QA was actually performed.

If manual QA is still not performed, label the artifact as:

```text
Public test / community beta candidate
```

not:

```text
official release
```

## Suggested Final Readiness Wording

If all automated work passes but no device QA is performed:

```text
Automated readiness: passed.
Public test readiness: acceptable as a debug-signed community beta candidate.
Official/public release readiness: not complete until manual phone/tablet QA and release signing are done.
```

## Acceptance Criteria

This plan is complete only when:

- v0.7.46 release notes are present.
- `RECOMMENDATION_VERSIONING.md` includes v0.7.45 and v0.7.46.
- OCR normal UI no longer shows raw `e.message`.
- OCR new failed rows store stable error keys rather than raw exception text.
- OCR page-error logs no longer include manga title/chapter names or OCR text.
- OCR per-manga/per-chapter deletion is either confirmed reachable or added.
- active public/docs no longer contradict OCR build inclusion.
- `docs/recommendations/README.md` active text is cleaned of obvious mojibake.
- `docs/KMK_MARKDOWN_ENCYCLOPEDIA.md` points to current v0.7.46 closeout docs.
- `spotlessCheck` passes.
- `:app:testDebugUnitTest` passes.
- `:app:lintKmkPublicTest` is run and documented.
- `:app:assembleDebug` passes.
- `:app:assembleKmkPublicTest` passes.
- both private and public APKs are copied with v0.7.46 names.
- public APK metadata confirms `app.komikku.kmk`.
- implementation report is created with honest limitations.

