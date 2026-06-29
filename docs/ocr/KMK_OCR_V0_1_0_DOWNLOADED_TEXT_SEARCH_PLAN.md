# KMK-OCR v0.1.0 Downloaded Text Search Implementation Plan

Status: Planning only

Owner intent: Build a dedicated OCR-specific APK/branch. This is not part of the normal KMK-Recs release line and should not be treated as the default Komikku APK going forward.

## Summary

Add an optional, manual OCR search tool that indexes text from already-downloaded manga pages and lets the user search for words or phrases when they cannot remember a manga title. The feature should be local-only, cache-based, cancellable, and isolated in a separate OCR branch/APK because OCR dependencies and processing can increase APK size, storage usage, CPU load, and battery drain.

The first implementation should focus on downloaded chapters only. It should not OCR online chapters, should not run automatically on app launch, and should not send images or extracted text to any remote service.

## Non-Goals

- Do not add OCR to the standard KMK-Recs APK line.
- Do not run OCR automatically in the background without a direct user action.
- Do not scan every source website or extension.
- Do not OCR non-downloaded chapters.
- Do not upload pages or OCR text to cloud services.
- Do not try to solve fuzzy image-based manga identification in v0.1.
- Do not require OCR to be part of recommendation scoring.

## Existing Code To Reuse

### Download Discovery

Use the existing download layer instead of manually guessing filesystem paths.

Relevant files:

- `app/src/main/java/eu/kanade/tachiyomi/data/download/DownloadProvider.kt`
- `app/src/main/java/eu/kanade/tachiyomi/data/download/DownloadManager.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/reader/loader/DownloadPageLoader.kt`

Observed behavior:

- `DownloadProvider.findMangaDir(...)` finds downloaded manga directories.
- `DownloadProvider.findChapterDir(...)` finds a downloaded chapter directory or file using current and legacy chapter names.
- `DownloadProvider.findChapterDirs(...)` supports bulk lookup and has local-source handling.
- `DownloadManager.buildPageList(source, manga, chapter)` builds page records for downloaded image files.
- `DownloadPageLoader` already distinguishes archive downloads from directory downloads.

Implementation implication:

- Create an OCR-specific page provider that reuses `DownloadProvider`, `DownloadManager.buildPageList`, and/or `DownloadPageLoader` patterns.
- Avoid duplicating path construction logic.
- Handle local source through existing `source.isLocal()` paths.

### Reader Navigation

Relevant files:

- `app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreen.kt`

Observed behavior:

- `MangaScreen.openChapter(...)` opens a chapter with `ReaderActivity.newIntent(context, mangaId, chapter.id)`.
- Existing page-preview code opens a specific page with `ReaderActivity.newIntent(context, chapter.mangaId, chapter.id, page)`.
- `ReaderViewModel.init(mangaId, initialChapterId, page)` accepts an optional page index and passes it into chapter loading.

Implementation implication:

- OCR search results can open directly into a matched chapter/page.
- If page-index opening fails for a specific viewer/source, fallback should open the chapter and show a toast/snackbar that page jump was unavailable.

### Background Work

Relevant files/patterns:

- `app/src/main/java/exh/recs/evaluation/SourceEvaluationJob.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationNotifier.kt`
- `app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/BackupRestoreJob.kt`
- `app/src/main/java/eu/kanade/tachiyomi/data/notification/Notifications.kt`

Observed behavior:

- Existing long-running work uses `CoroutineWorker`, `ForegroundInfo`, notification channels, and `setForegroundSafely()`.
- Source Evaluation already has tap-to-open notification behavior and progress notifications.

Implementation implication:

- OCR indexing should be a foreground `WorkManager` job with progress notification and cancel action.
- Notification tap should open the OCR Search/Index screen.
- Reuse the app's existing notification patterns rather than inventing a raw service.

### Database

Relevant path:

- `data/src/main/sqldelight/tachiyomi/data/`
- `data/src/main/sqldelight/tachiyomi/migrations/`

Observed behavior:

- SQLDelight is used for app tables.
- Current latest migration files are in the 50s.
- Recommendation-related tables live beside normal app tables.

Implementation implication:

- OCR index tables should be SQLDelight tables with matching migration files.
- Migration must be tested because missing tables previously caused settings crashes in recommendation work.

## Build And Versioning Strategy

This should be a separate feature line:

- Feature label: `KMK-OCR v0.1.0`
- Suggested branch: `kmk-ocr-v0.1`
- Suggested APK name: `Komikku-v1.13.6-kmk-ocr.0.1-debug.apk`

Because the user wants this as a dedicated APK, not the standard APK, there are two possible packaging strategies:

1. Same application id, separate branch
   - Pros: uses the same app data, database, library, and downloads.
   - Cons: installing the OCR APK replaces the normal Dev APK.
   - Requirement: Android `versionCode` must be higher than the currently installed build.
   - Recommended for v0.1 because OCR needs library/download context.

2. Separate application id/build type
   - Pros: can install side by side.
   - Cons: likely loses direct access to the original app database/preferences, and may not reliably see app-private data.
   - Not recommended for v0.1 unless the user explicitly wants a side-by-side experimental app and accepts data separation.

Do not update `docs/recommendations/CURRENT_STATE.md` or KMK-Recs version files for OCR unless the user explicitly requests that the OCR branch inherit and record KMK-Recs status. OCR docs should remain under `docs/ocr/`.

## OCR Engine Decision

Use Google ML Kit Text Recognition v2 with a bundled Latin-script model for `KMK-OCR v0.1.0`.

This is a deliberate first-version choice, not a placeholder for Claude to decide.

Concrete dependency:

```toml
# gradle/libs.versions.toml
mlkit-text-recognition = "com.google.mlkit:text-recognition:16.0.1"
```

```kotlin
// app/build.gradle.kts
implementation(libs.mlkit.text.recognition)
```

Why this specific build:

- The app already has `google()` in both plugin and dependency repositories, so the artifact can resolve through the existing Gradle repository setup.
- The app's Android minSdk is 26 (`buildSrc/src/main/kotlin/mihon/buildlogic/AndroidConfig.kt`), while ML Kit Text Recognition v2 requires API 23+.
- The bundled model avoids first-run "model still downloading" behavior. This matters because the OCR APK is meant to work predictably while scanning downloaded manga.
- The size cost is acceptable because this is a dedicated OCR APK/branch rather than the normal KMK-Recs APK.

Why not the Google Play Services / unbundled model for v0.1:

- It is smaller, but the OCR model can require dynamic download before first use.
- If the model is not ready, early requests may return no results.
- That uncertainty is a bad fit for a manual batch-indexing workflow where the user expects indexing to begin immediately after pressing the button.

Why Latin-only for v0.1:

- It is the smallest reliable first pass and covers many English-translated manga/manhwa/manhua downloads.
- Chinese/Japanese/Korean recognition can be added later as optional OCR model toggles, but bundling all scripts at once would increase APK size and implementation complexity.
- The UI must state that v0.1 OCR works best on Latin/English text and may perform poorly on vertical, stylized, Japanese, Korean, or Chinese text.

Future optional model dependencies, not for v0.1 unless explicitly approved:

```toml
mlkit-text-recognition-chinese = "com.google.mlkit:text-recognition-chinese:16.0.1"
mlkit-text-recognition-japanese = "com.google.mlkit:text-recognition-japanese:16.0.1"
mlkit-text-recognition-korean = "com.google.mlkit:text-recognition-korean:16.0.1"
```

Use an engine abstraction first so future script models can be added without rewriting indexing/search:

```kotlin
interface OcrTextRecognizer {
    suspend fun recognizeText(input: OcrImageInput): OcrPageText
}
```

Concrete implementation:

```kotlin
class MlKitLatinOcrTextRecognizer(
    private val context: Context,
) : OcrTextRecognizer {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognizeText(input: OcrImageInput): OcrPageText {
        val image = input.toInputImage(context)
        val result = recognizer.process(image).await()
        return OcrPageText(rawText = result.text)
    }
}
```

Implementation notes:

- Use `kotlinx-coroutines-play-services` only if it already exists in the dependency graph; otherwise add it deliberately or bridge ML Kit `Task<Text>` with `suspendCancellableCoroutine`.
- Prefer `InputImage.fromBitmap(bitmap, 0)` after controlled downsample/decode. `InputImage.fromFilePath(context, uri)` is acceptable only for direct file/content URIs that ML Kit can read reliably; archive pages will need stream/bitmap handling anyway.
- Do not keep the `Bitmap` after recognition. Recycle/let it be released immediately after page processing.
- Do not call OCR from Compose/UI code. OCR must run in repository/worker/domain code.
- Keep OCR dependency limited to the OCR branch/APK. If practical, isolate it behind an OCR-specific Gradle build variant/source set. If too invasive, keep it as a branch-only dependency and document that it must not be merged into normal KMK-Recs builds without approval.

Evidence/reference:

- Google ML Kit's Android Text Recognition v2 documentation lists bundled Latin dependency `com.google.mlkit:text-recognition:16.0.1`, says bundled models are statically linked and available immediately, and states API level 23+ is required.
## Data Model

Create SQLDelight tables in `data/src/main/sqldelight/tachiyomi/data/` and migrations in `data/src/main/sqldelight/tachiyomi/migrations/`.

Suggested table: `ocr_indexed_page`

Fields:

- `id INTEGER PRIMARY KEY`
- `manga_id INTEGER NOT NULL`
- `source_id INTEGER NOT NULL`
- `manga_url TEXT NOT NULL`
- `manga_title TEXT NOT NULL`
- `chapter_id INTEGER NOT NULL`
- `chapter_url TEXT NOT NULL`
- `chapter_name TEXT NOT NULL`
- `page_index INTEGER NOT NULL`
- `page_uri TEXT`
- `page_identity TEXT NOT NULL`
- `page_modified_at INTEGER`
- `page_size INTEGER`
- `engine_key TEXT NOT NULL`
- `engine_version TEXT NOT NULL`
- `language_hint TEXT`
- `raw_text TEXT NOT NULL`
- `normalized_text TEXT NOT NULL`
- `indexed_at INTEGER NOT NULL`
- `error_message TEXT`

Indexes:

- `(manga_id, chapter_id, page_index)`
- `(source_id, manga_url)`
- `(normalized_text)` if FTS is not used
- unique `(chapter_id, page_index, page_identity, engine_key, engine_version)`

Optional table: `ocr_index_job_state`

Fields:

- `job_id TEXT PRIMARY KEY`
- `scope TEXT NOT NULL`
- `status TEXT NOT NULL`
- `started_at INTEGER NOT NULL`
- `finished_at INTEGER`
- `total_pages INTEGER`
- `completed_pages INTEGER`
- `failed_pages INTEGER`
- `last_error TEXT`

Search strategy:

- v0.1 can use normalized `LIKE` search if FTS setup is too risky.
- Prefer SQLite FTS only if the project already supports it cleanly through SQLDelight and tests pass.
- Normalize text by lowercasing, removing extra whitespace, and optionally stripping punctuation for search.

Stale index strategy:

- Store a page identity based on URI/path plus modified time/size where available.
- Skip OCR if the existing row has the same page identity and OCR engine version.
- If a download is deleted or chapter no longer exists, hide stale results by validating chapter/manga before display.
- Add a "Clean stale OCR index" action or run cleanup after indexing completes.

## Domain Components

Add a separate OCR package, for example:

- `app/src/main/java/exh/ocr/`

Suggested components:

- `OcrTextRecognizer`
- `MlKitOcrTextRecognizer`
- `OcrImageInput`
- `OcrPageText`
- `OcrDownloadPageProvider`
- `OcrIndexRepository`
- `OcrIndexService`
- `OcrSearchQueryNormalizer`
- `OcrSearchResult`
- `OcrIndexWorker`
- `OcrNotifier`

### `OcrDownloadPageProvider`

Responsibilities:

- Enumerate downloaded chapters for a scope.
- Resolve page URIs/streams using existing download APIs.
- Support downloaded directory chapters and archive chapters.
- Return lightweight page references; do not load all bitmaps into memory at once.

Scopes:

- Current manga.
- Selected manga from library.
- All downloaded library manga.

Current manga should be implemented first because it is the smallest and safest scope.

### `OcrIndexService`

Responsibilities:

- Iterate pages sequentially or with very low concurrency.
- Downsample images before OCR where possible.
- Call `OcrTextRecognizer`.
- Store extracted text immediately after each page succeeds.
- Record per-page errors without aborting the entire job.
- Respect cancellation promptly.

Concurrency:

- Default to one page at a time.
- Do not OCR an entire chapter into memory.
- Do not index multiple manga in parallel for v0.1.

### `OcrIndexRepository`

Responsibilities:

- Insert/update OCR rows.
- Search indexed text.
- Delete index rows by manga, by source, by stale entries, or all OCR data.
- Provide counts for indexed manga/chapters/pages.

## UI Plan

Add a dedicated OCR screen, not a hidden automatic behavior.

Suggested entry points:

- More tab: `OCR Search Downloads`.
- Manga screen overflow: `OCR index downloaded chapters` or `Search downloaded text`.
- Optional reader page menu later: `OCR this chapter` or `Search downloaded text`.

v0.1 should prioritize one stable screen:

### OCR Search Downloads Screen

Sections:

1. Search field
   - User enters a word or phrase.
   - Search only runs against existing OCR index.

2. Scope selector
   - Current manga, when opened from a manga.
   - All downloaded manga.
   - Selected manga can be deferred if it adds too much UI complexity.

3. Index actions
   - `Index current manga`
   - `Index all downloaded manga`
   - `Cancel indexing`
   - `Clear OCR index`

4. Progress state
   - Current manga/chapter/page.
   - Completed pages / total pages.
   - Failed page count.
   - Last non-fatal error.

5. Search results
   - Manga title.
   - Chapter name.
   - Page number.
   - Short text snippet around the match.
   - Source name if available.
   - Tap result opens reader at matched page.

UX rules:

- If no OCR index exists, show a clear empty state with an index action.
- If a query has no matches, say no indexed text matched.
- If downloads are missing, say no downloaded chapters are available.
- If OCR is unavailable in the build, the screen should not appear.
- Do not bury this inside recommendation settings.

## Background Job And Notifications

Use `WorkManager` foreground job for indexing.

Requirements:

- Progress notification while OCR indexing is running.
- Notification tap opens OCR screen.
- Cancel action cancels the worker.
- Completion notification summarizes indexed pages and failures.
- Use a dedicated OCR notification channel, for example:
  - `Notifications.CHANNEL_OCR_INDEXING`
  - `Notifications.ID_OCR_INDEX_PROGRESS`
  - `Notifications.ID_OCR_INDEX_COMPLETE`

The worker should:

- Use `setForegroundSafely()`.
- Return success for completed-with-page-errors jobs, because individual page failures should not kill the whole job.
- Return retry only for truly transient job-level failures if retry is safe.
- Always persist progress/failure counts before exit.

## Exception Handling

The app must not crash because OCR failed on one page, chapter, manga, or archive.

Handle:

- Missing download directory.
- Storage permission or SAF access failures.
- Deleted/moved files during indexing.
- Empty chapter directories.
- Corrupt image files.
- Unsupported image types.
- Archive open/read failure.
- Out-of-memory or bitmap decode failure.
- OCR engine initialization failure.
- OCR model unavailable.
- SQLDelight/database write failure.
- Cancellation.
- Reader open failure from result tap.

Behavior:

- Per-page failures: record failure and continue.
- Per-chapter failures: record chapter-level message and continue to next chapter.
- Job-level failures: show a visible error on OCR screen and in notification.
- Cancellation: stop promptly and keep already-indexed rows.
- Database missing-table errors: fail gracefully with a clear message and log, because previous recommendation work had this class of issue.

Avoid catching `Throwable` broadly unless there is a clear reason to prevent native/VM fatal errors from escaping. Prefer targeted exception handling plus `CancellationException` rethrow.

## Performance And Storage Controls

OCR is expensive. v0.1 should be conservative.

Controls:

- Manual indexing only.
- One page at a time by default.
- Confirmation before indexing all downloads.
- Show estimated page count when possible.
- Allow cancellation.
- Skip already indexed unchanged pages.
- Clear all OCR data action.
- Optional setting: index only Wi-Fi/charging can be deferred unless easy.

Image handling:

- Decode/downsample to the OCR engine's useful target size.
- Close streams promptly.
- Do not hold page bitmaps after recognition.
- Avoid preloading many page images.

Storage:

- Store extracted text, metadata, and minimal indexing identity.
- Do not store page image copies.
- Provide OCR index size/count summary.

## Privacy And Safety

The OCR index may contain dialogue text from downloaded manga. Treat it as user-private local data.

Requirements:

- On-device OCR only.
- No cloud OCR.
- No telemetry for OCR text.
- No backup/export of OCR text in v0.1 unless explicitly approved later.
- Clear OCR index action should delete all OCR text rows.

## Suggested Implementation Phases

### Phase 1: Branch And Build Isolation

- Create OCR branch from the current working KMK-Recs state.
- Add `docs/ocr/` implementation notes.
- Decide whether same app id or separate app id.
- For v0.1, use same app id unless the user requests side-by-side.
- Add OCR version label and APK naming convention.

### Phase 2: Database

- Add `ocr_indexed_page.sq`.
- Add migrations.
- Add repository/interactors for insert/search/delete.
- Add unit tests for DB roundtrip and missing/stale rows.

### Phase 3: Page Enumeration

- Implement `OcrDownloadPageProvider`.
- Support directory downloads first.
- Support archive downloads before release if current downloads can be `.cbz`.
- Add tests with fake page provider where filesystem tests are hard.

### Phase 4: OCR Engine

- Add OCR engine abstraction.
- Add ML Kit implementation in OCR branch.
- Add text normalization.
- Add unit tests for normalization and search snippets.

### Phase 5: Index Worker

- Add `OcrIndexWorker`.
- Add foreground notification.
- Add cancellation.
- Persist progress/failures.
- Ensure indexing continues after page-level failures.

### Phase 6: UI

- Add OCR Search Downloads screen.
- Add search, indexing, progress, clear index, and results UI.
- Add result tap to open reader at page.
- Add manga-screen entry point if current manga scope is implemented.

### Phase 7: QA And Hardening

- Test current manga indexing.
- Test all downloads indexing with cancellation.
- Test deleted downloads between index/search.
- Test corrupt or empty chapter.
- Test archive chapter.
- Test page result opens correct page.
- Test no-index and no-download empty states.
- Test screen rotation/process recreation during indexing.
- Test app restart while WorkManager job is running.

## Testing Requirements

Unit tests:

- OCR query normalization.
- Snippet generation.
- Index upsert behavior.
- Stale result filtering.
- Search result ordering.
- Repository delete/clear behavior.

Integration/manual tests:

- Build OCR APK.
- Install over current app if same app id and versionCode is higher.
- Index one downloaded manga.
- Search a known phrase from a downloaded chapter.
- Tap result and confirm reader opens chapter/page.
- Cancel an active indexing job.
- Clear OCR index and verify results disappear.
- Try with a manga that has no downloaded chapters.
- Try with deleted or moved downloads.
- Try with airplane mode enabled; OCR should still work for already downloaded pages and should not need network.

Regression tests:

- Normal reading still works.
- Downloads still work.
- Recommendation screens still open.
- Source Evaluation still opens.
- Backup/restore unaffected.

## Risks And Mitigations

Risk: APK bloat.

- Mitigation: OCR is separate APK/branch. Do not merge OCR dependency into normal KMK-Recs without approval.

Risk: Slow indexing or battery drain.

- Mitigation: manual only, foreground notification, cancellation, one page at a time, skip unchanged pages.

Risk: Poor OCR accuracy.

- Mitigation: present OCR as a helper, not a guarantee. Let users search multiple remembered words. Document that stylized/vertical text may fail.

Risk: Download path bugs.

- Mitigation: reuse `DownloadProvider` and `DownloadManager`; do not manually reconstruct paths.

Risk: Missing DB migration.

- Mitigation: add migration tests and safe fallback UI if OCR tables are unavailable.

Risk: Stale index after downloads deleted.

- Mitigation: validate result target before opening and provide stale index cleanup.

Risk: Side-by-side APK cannot see app data.

- Mitigation: use same application id for v0.1 unless user explicitly wants separate app id and accepts data separation.

## Open Decisions For Claude To Confirm Before Coding

- Confirm whether the OCR APK should replace the current Dev APK using the same `applicationId`, or whether the user wants a side-by-side app despite data-sharing drawbacks.
- Confirm the OCR engine dependency and whether it can be kept branch-only.
- Confirm the first UI entry point: More tab only, manga page only, or both.
- Confirm whether v0.1 must support archive downloads immediately or can ship directory support first with archive support before v0.2.

## Acceptance Criteria

- OCR docs live under `docs/ocr/` and are not mixed into KMK-Recs release notes.
- OCR feature is only in the OCR branch/APK.
- User can manually index downloaded chapters.
- User can search indexed text.
- Search results show manga/chapter/page/snippet.
- Tapping a result opens the reader at the matched page when possible.
- OCR indexing can be cancelled.
- Per-page OCR failures do not crash the app.
- The app does not perform network OCR or automatic background scans.
- User can clear OCR index data.
- Tests cover normalization, indexing, search, stale handling, and key UI/view-model behavior.

