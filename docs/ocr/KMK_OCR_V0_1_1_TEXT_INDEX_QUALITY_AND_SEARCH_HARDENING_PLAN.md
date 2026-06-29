# KMK-OCR v0.1.1 Text Index Quality And Smart Search Hardening Plan

> Branch/APK line: **KMK-OCR** (separate from KMK-Recs)
> Target version label: **KMK-OCR v0.1.1**
> Suggested APK name: `Komikku-v1.13.6-kmk-ocr.0.1.1-debug.apk`
> Suggested versionCode: `81` or higher, because OCR v0.1.0 used `80`
> Status: **Planning / ready for implementation after approval**

---

## Purpose

OCR v0.1.0 successfully adds a local downloaded-page OCR flow, but user testing shows a serious quality gap: the app reports that pages are indexed, yet searches for visible speech-bubble text return no results.

This plan hardens the OCR feature so it behaves like a useful text index rather than a simple "run OCR once and hope" feature. The core goals are:

- Improve OCR recognition quality, especially for long webtoon/manhwa pages.
- Store only the useful text index permanently, not page images or OCR scan artifacts.
- Show how much OCR index storage is being used.
- Make search tolerant of partial phrases and missing words.
- Clearly distinguish pages that were processed from pages where usable text was actually recognized.
- Avoid permanently skipping pages that previously produced empty OCR text.
- Keep the OCR APK line separate from the main KMK-Recs APK line.

---

## Current User-Reported Problem

The user tested OCR with two downloaded manga chapters. The app said the pages were indexed and found the two manga, but searching for exact text visible inside speech bubbles returned nothing.

The likely cause is not that indexing never ran. The likely cause is that OCR ran with image preprocessing that destroys text detail on long pages, then stored empty or poor OCR output as if it were a valid indexed result.

---

## Current Implementation Summary

Implemented in OCR v0.1.0:

- Entry screen: `app/src/main/java/exh/ocr/OcrSearchScreen.kt`
- Screen model: `app/src/main/java/exh/ocr/OcrSearchScreenModel.kt`
- Worker: `app/src/main/java/exh/ocr/OcrIndexWorker.kt`
- Index service: `app/src/main/java/exh/ocr/OcrIndexService.kt`
- Page provider: `app/src/main/java/exh/ocr/OcrDownloadPageProvider.kt`
- OCR engine wrapper: `app/src/main/java/exh/ocr/OcrTextRecognizer.kt`
- Repository: `app/src/main/java/exh/ocr/OcrIndexRepository.kt`
- SQLDelight table/queries: `data/src/main/sqldelight/tachiyomi/data/ocr_indexed_page.sq`
- Initial migration: `data/src/main/sqldelight/tachiyomi/migrations/54.sqm`
- Current implementation notes: `docs/ocr/KMK_OCR_V0_1_0_DOWNLOADED_TEXT_SEARCH_IMPLEMENTATION.md`

Current OCR engine:

- Google ML Kit Text Recognition v2 Latin model.
- Dependency: `com.google.mlkit:text-recognition:16.0.1`
- Engine key: `mlkit-latin`
- Engine version: `16.0.1`

Current behavior:

- User manually starts indexing from **More -> Search Downloads (OCR)**.
- Only downloaded chapters are indexed.
- No cloud OCR is used.
- Images are not intentionally persisted.
- Text is stored in `ocr_indexed_page`.

---

## Main Technical Problems Found

### 1. Long Page Downsampling Is Too Aggressive

Current file: `app/src/main/java/exh/ocr/OcrTextRecognizer.kt`

Current logic:

```kotlin
val maxDim = 1280
val sampleSize = maxOf(
    opts.outWidth / maxDim,
    opts.outHeight / maxDim,
    1,
)
```

This uses the larger dimension to decide downsampling. That is dangerous for long webtoon/manhwa pages.

Example:

- A page is `900 x 12000`.
- Height dominates the calculation.
- `12000 / 1280 = 9`.
- The decoded width becomes about `100px`.
- Speech-bubble text becomes unreadable.

The app can then "index" the page while OCR returns empty or nearly useless text.

### 2. `RGB_565` Reduces OCR Quality

Current code uses:

```kotlin
inPreferredConfig = Bitmap.Config.RGB_565
```

This is memory-efficient but worse for OCR. It reduces color precision and can damage anti-aliased text. OCR should prefer `ARGB_8888` unless memory pressure forces fallback.

### 3. Empty OCR Output Is Stored As Success

Current `OcrIndexService` stores a page with:

- `rawText = ""`
- `normalizedText = ""`
- `errorMessage = null`

That means the page appears successfully indexed even though no searchable text exists.

### 4. Empty OCR Output Can Be Skipped Forever

Current skip logic checks only:

- same chapter/page
- same page identity
- same engine key
- same engine version

It does not check whether recognized text was empty. Once a bad empty OCR row exists, re-indexing can skip that page forever.

### 5. Search Is Exact Substring Only

Current SQL:

```sql
WHERE normalized_text LIKE '%' || :query || '%'
```

This only works if the normalized query appears exactly as a continuous substring. OCR often changes spacing, punctuation, word breaks, or misses one word. The user needs partial/smart search where most words can match.

### 6. Stats Are Misleading

Current `OcrIndexStats` only has:

```kotlin
val totalPages: Long
val totalManga: Long
```

`totalPages` counts database rows, not pages with useful recognized text. The UI can say many pages are indexed even if most have empty OCR text.

### 7. Results Do Not Show Source/Extension

`OcrSearchResult` has `sourceName`, but repository mapping currently sets it to `null`. The user wants OCR results to identify:

- manga
- extension/source
- chapter
- page

### 8. Indexing Scope Is Too Broad

The current UI exposes only:

- Index all downloaded
- Clear index

For OCR, "all downloaded" can become expensive. The feature needs safer index scopes and run limits.

### 9. No Storage Visibility

The user specifically wants to know how much OCR text/index storage is being used, and to be able to clear it. v0.1.0 has clear-all, but no storage estimate.

---

## Design Principles For v0.1.1

1. OCR should process one page at a time, store text, and release image memory immediately.
2. The app should not persist page images, crops, scan artifacts, or OCR input images.
3. The persistent artifact should be the text index only.
4. Empty OCR output must not be treated as equivalent to a successful recognized page.
5. Search should still work when the user remembers only part of a line.
6. The user should be able to see index size and clear it.
7. Indexing should be cancellable and safe for large download libraries.
8. Old poor-quality v0.1.0 rows should not prevent better v0.1.1 OCR from running.
9. This must remain local and private. No OCR text should be uploaded.

---

## Non-Goals

- Do not add cloud/server OCR.
- Do not merge OCR into the normal KMK-Recs APK line yet.
- Do not index non-downloaded chapters.
- Do not automatically scan the whole library on app startup.
- Do not permanently store page images or OCR preview images.
- Do not add CJK/Chinese/Japanese/Korean OCR support in this version unless explicitly approved later.
- Do not add OCR backup/sync/export in this version.
- Do not try to infer manga identity across extensions from OCR text in this version.

---

## Implementation Plan

### Phase 1: Versioning And Documentation

Update OCR documentation only.

Files:

- `docs/ocr/README.md`
- `docs/ocr/KMK_OCR_V0_1_1_TEXT_INDEX_QUALITY_AND_SEARCH_HARDENING_PLAN.md`
- Later, after implementation, create `docs/ocr/KMK_OCR_V0_1_1_TEXT_INDEX_QUALITY_AND_SEARCH_HARDENING_IMPLEMENTATION.md`

Build/version requirements:

- Increment Android `versionCode` above v0.1.0's `80`.
- Suggested `versionCode = 81`.
- Keep the application ID behavior consistent with v0.1.0 unless the user explicitly asks for side-by-side OCR builds.
- Suggested APK name: `Komikku-v1.13.6-kmk-ocr.0.1.1-debug.apk`.

Do not modify recommendation version docs for this OCR-only work.

### Phase 2: Add A Preprocessing Version

The current `engineVersion = "16.0.1"` is not enough because the recognition model may be the same while preprocessing changes dramatically.

Implement one of these approaches:

Preferred:

```kotlin
override val engineVersion: String = "16.0.1-tiled-v2"
```

Alternative:

- Add a separate `preprocessing_version` column.
- Keep `engine_version = "16.0.1"`.
- Include preprocessing version in skip/search decisions.

The preferred approach is simpler for v0.1.1 because the unique index already includes `engine_version`.

Important:

- Search should avoid returning duplicate old v0.1.0 rows and new v0.1.1 rows for the same page.
- Either filter search to the current engine version or add cleanup for old engine-version rows.
- The safest v0.1.1 behavior is:
  - Search only current OCR engine/preprocessing version by default.
  - Provide a clear-old-index action or automatically ignore old engine rows.

### Phase 3: Replace Max-Dimension Downsampling With OCR-Safe Decoding

File:

- `app/src/main/java/exh/ocr/OcrTextRecognizer.kt`

Replace the current `maxDim = 1280` strategy.

New strategy:

1. Decode image bounds first.
2. Preserve enough width for text recognition.
3. Prefer target width around `1600-2048px`, with a conservative default such as `1800px`.
4. Use `ARGB_8888` by default.
5. Do not downsample long vertical pages based only on height.

Suggested logic:

- If the original width is <= target width, keep `inSampleSize = 1`.
- If the original width is much larger than target width, downsample by width.
- Ignore height when calculating sample size except for memory safety/tile planning.

This prevents a `900 x 12000` page from becoming `100px` wide.

### Phase 4: Add Long-Page Tiling

Long webtoon/manhwa pages should be OCR'd in vertical strips instead of as one heavily downsampled image.

Implementation:

- Decode to a bitmap with OCR-safe width.
- If decoded height is above a threshold, split into vertical tiles.
- Suggested tile height: `2000-3000px`.
- Suggested tile overlap: `80-160px`.
- OCR each tile individually.
- Concatenate recognized text in page order.
- Recycle each tile bitmap after OCR.
- Recycle the full decoded bitmap after all tiles finish.

Add a pure helper where possible:

- `OcrImagePreprocessor`
- `OcrTilePlanner`
- or private helper functions inside `OcrTextRecognizer.kt` if keeping scope small.

Recommended testable model:

```kotlin
data class OcrTileSpec(
    val y: Int,
    val height: Int,
)
```

Add unit tests for tile planning:

- short image -> one tile
- long image -> multiple overlapping tiles
- final tile does not exceed bitmap height
- overlap does not create invalid negative/zero ranges

### Phase 5: Improve Memory And Failure Handling

Current code reads the whole stream into memory with `stream.use { it.readBytes() }`. For v0.1.1 this can remain if time is limited, but it must be guarded carefully.

Required:

- Catch decode failures per page.
- Catch ML Kit failures per tile/page.
- Record failed pages without crashing the worker.
- Respect coroutine cancellation.
- Recycle all bitmaps in `finally`.
- Avoid storing image bytes beyond the current page operation.

Recommended:

- Catch `OutOfMemoryError` around bitmap decode/tile creation and mark the page failed with a clear error message.
- Do not broadly swallow fatal errors elsewhere.

### Phase 6: Update Database Schema And Queries

Files:

- `data/src/main/sqldelight/tachiyomi/data/ocr_indexed_page.sq`
- new migration: `data/src/main/sqldelight/tachiyomi/migrations/55.sqm` or next correct migration number based on the current repo state

Add fields that let the app distinguish recognized text from empty/failure rows:

Recommended columns:

```sql
recognized_text_length INTEGER NOT NULL DEFAULT 0;
recognized_word_count INTEGER NOT NULL DEFAULT 0;
ocr_status TEXT NOT NULL DEFAULT 'success';
```

Where:

- `success`: usable text was recognized.
- `empty`: OCR completed but no usable text was found.
- `failed`: page failed due to decode/OCR/storage exception.

If the project prefers integer enums:

- `ocr_status INTEGER NOT NULL DEFAULT 1`
- Use constants in Kotlin.

Add or update queries:

- Count all processed pages.
- Count successful recognized pages.
- Count empty pages.
- Count failed pages.
- Count distinct manga with recognized text.
- Count distinct chapters with recognized text.
- Estimate index bytes with `length(...)`.
- Search only usable rows by default:
  - `ocr_status = 'success'`
  - `recognized_word_count > 0`
  - `error_message IS NULL`
  - current engine/preprocessing version.

Storage estimate query should include at minimum:

```sql
SUM(
    length(raw_text) +
    length(normalized_text) +
    length(manga_title) +
    length(chapter_name) +
    length(page_identity)
)
```

This is not exact SQLite file size, but it is a useful text-index estimate for the user.

### Phase 7: Fix Index Semantics

File:

- `app/src/main/java/exh/ocr/OcrIndexService.kt`

Current behavior treats empty OCR as success. Change this.

New behavior:

- If OCR returns non-blank normalized text:
  - `ocr_status = success`
  - `recognized_text_length = rawText.length`
  - `recognized_word_count = normalized tokens count`
  - `errorMessage = null`
- If OCR completes but text is blank:
  - `ocr_status = empty`
  - `recognized_text_length = 0`
  - `recognized_word_count = 0`
  - `errorMessage = null` or a non-fatal message such as `No text recognized`
- If OCR/decode fails:
  - `ocr_status = failed`
  - `recognized_text_length = 0`
  - `recognized_word_count = 0`
  - `errorMessage = exception message`

Progress should show:

- completed pages
- recognized pages
- empty pages
- failed pages

### Phase 8: Fix Skip Logic

Current skip logic:

- skip if identity and engine version match.

New skip logic:

- Skip unchanged pages only when the existing row is a valid success for the current engine/preprocessing version.
- Empty and failed rows should be eligible for retry by default in v0.1.1, especially because v0.1.0 preprocessing was poor.

Recommended repository function:

```kotlin
getExistingPageState(chapterId, pageIndex, engineKey)
```

Return:

- page identity
- engine version
- OCR status
- recognized word count
- error message

Then skip only if:

- identity matches
- engine version matches current version
- status is success
- recognized word count > 0
- user did not force re-index

Add explicit actions:

- Re-index empty/failed pages.
- Force re-index all indexed pages.

### Phase 9: Add Smart Search

Files:

- `app/src/main/java/exh/ocr/OcrSearchQueryNormalizer.kt`
- `app/src/main/java/exh/ocr/OcrIndexRepository.kt`
- `app/src/main/java/exh/ocr/OcrSearchResult.kt`
- `data/src/main/sqldelight/tachiyomi/data/ocr_indexed_page.sq`

Current search requires the full normalized phrase. Replace this with a ranked search pipeline.

Search flow:

1. Normalize query.
2. Tokenize query.
3. If query is blank or has no useful tokens, return empty result.
4. Query exact phrase matches first.
5. Query token candidate matches second.
6. Rank candidates in Kotlin.

Ranking signals:

- exact normalized phrase match: strongest
- all query tokens present: strong
- most query tokens present: medium
- fewer than a minimum threshold: reject
- earlier match position: small boost
- query token order preserved: small boost

Recommended threshold:

- For 1 token: token must match.
- For 2 tokens: at least 1 strong token can match, but rank below both-token matches.
- For 3+ tokens: require at least 60-70% of useful tokens.

Result model additions:

```kotlin
val matchScore: Double
val matchType: OcrMatchType // EXACT, STRONG, PARTIAL
val matchedWords: List<String>
val missingWords: List<String>
```

UI should show a short reason:

- `Exact match`
- `Matched 5 of 6 words`
- `Partial match`

This directly supports the user's requirement that they may not remember every word exactly.

### Phase 10: Improve Snippets

Current snippets are built from normalized text, which can look unnatural.

Improve snippet generation:

- Prefer raw text for displayed snippets.
- Use normalized text only for matching/ranking.
- If exact raw-text position is hard to map, use a simple fallback:
  - find the first matched token in raw text case-insensitively
  - show surrounding text
  - otherwise show the start of raw text

Ensure snippets are concise and do not flood the UI.

### Phase 11: Show Source/Extension In Results

`OcrSearchResult.sourceName` currently exists but is always `null`.

Fix by either:

Option A, preferred:

- Resolve source name from `sourceId` in the screen model or repository using `SourceManager`.
- Do not persist source names because source names can change.

Option B:

- Add `source_name` to the OCR table at indexing time.

The UI result card should show:

- Manga title
- Source/extension name
- Chapter name
- Page number
- Match type/score
- Snippet

This satisfies the user's stated purpose: find the manga, the extension/source, and the chapter.

### Phase 12: Add Safer Index Scopes And Limits

Current UI only exposes "Index all downloaded". OCR can become slow with hundreds of chapters.

Add a basic scope/limit system:

Recommended first controls:

- `Index all downloaded`
- `Index empty/failed pages`
- `Force re-index all`
- `Max pages this run`

Suggested `Max pages this run` choices:

- 50
- 100
- 250
- 500
- Unlimited, with warning

If current manga context is easy to wire:

- Add `Index current manga` from a manga page or OCR screen launched with manga ID.

If current manga context is not already available:

- Do not block v0.1.1 on it.
- Keep it as a follow-up.

Indexing should always process page-by-page:

1. Open page stream.
2. Decode/preprocess.
3. OCR.
4. Store text/status.
5. Recycle bitmap and close stream.
6. Continue.

Do not scan all pages into memory first.

### Phase 13: Add Storage Visibility And Cleanup

Files:

- `app/src/main/java/exh/ocr/OcrSearchResult.kt`
- `app/src/main/java/exh/ocr/OcrIndexRepository.kt`
- `app/src/main/java/exh/ocr/OcrSearchScreen.kt`
- string resources under `i18n/src/commonMain/moko-resources/...` or the repo's current KMR string location

Expand `OcrIndexStats`.

Suggested model:

```kotlin
data class OcrIndexStats(
    val processedPages: Long,
    val recognizedPages: Long,
    val emptyPages: Long,
    val failedPages: Long,
    val totalManga: Long,
    val totalChapters: Long,
    val estimatedBytes: Long,
)
```

UI example:

```text
OCR index: 18 MB - 42 manga - 310 chapters - 7,420 processed pages - 5,980 with text - 120 empty - 8 failed
```

Add:

- Clear all OCR index.
- Clear old OCR engine rows if current engine version changed.
- Optional: clear empty/failed rows only.

The UI should make it clear that storage is the local text index, not stored images.

### Phase 14: Improve Empty States And User Feedback

The OCR screen should distinguish:

- no index exists
- index exists but recognized text count is zero
- search returned no matches
- indexing produced many empty pages
- indexing produced failures

Examples:

- `No OCR index yet. Index downloaded chapters to search text.`
- `OCR processed pages, but no readable text was recognized. Try re-indexing with the improved OCR mode.`
- `No matches for "..." Try fewer words or re-index empty pages.`

### Phase 15: String Cleanup

Current docs/tests show mojibake in some generated text, such as `â€¦`.

Do a small OCR-only cleanup:

- Replace mojibake ellipses/arrows in OCR docs or strings touched by this work.
- Prefer ASCII in new docs unless the existing file already uses proper Unicode.
- Do not churn unrelated recommendation docs.

### Phase 16: Tests

Add focused tests rather than broad UI tests.

Recommended tests:

1. `OcrSearchQueryNormalizerTest`
   - tokenizes punctuation
   - handles case
   - ignores repeated whitespace
   - returns useful tokens for partial search

2. New ranking tests, if ranking is extracted:
   - exact phrase outranks token match
   - all-token match outranks partial match
   - partial match below threshold is rejected
   - missing one word still returns a result when enough words match

3. Tile planner tests:
   - normal page -> one tile
   - long page -> multiple tiles
   - tile overlap applied
   - no invalid tile ranges

4. Index skip logic tests:
   - successful unchanged page is skipped
   - empty unchanged page is retried
   - failed unchanged page is retried
   - force re-index bypasses skip
   - old engine version does not block current version

5. Stats tests, if repository DB testing pattern exists:
   - processed count includes all rows
   - recognized count excludes empty/failed rows
   - estimated bytes increases after inserting text

### Phase 17: Manual QA Checklist

Claude should complete and document manual verification steps, even if not all can be run locally.

Required manual QA:

- Install OCR v0.1.1 over OCR v0.1.0.
- Open **More -> Search Downloads (OCR)**.
- Confirm old v0.1.0 empty rows do not prevent re-indexing.
- Index the same two test chapters the user used.
- Search an exact phrase from a speech bubble.
- Search only 2-3 distinctive words from the phrase.
- Search with one missing word.
- Confirm results show manga, source/extension, chapter, page, and snippet.
- Tap a result and confirm reader opens near the correct page.
- Confirm stats show processed pages and recognized pages separately.
- Confirm storage estimate appears.
- Clear index and confirm stats reset.
- Start indexing with a page limit and confirm it stops at the limit.
- Cancel indexing and confirm partial indexed text remains searchable.

---

## Recommended Implementation Order For Claude

1. Read this plan and the v0.1.0 implementation notes.
2. Inspect the current OCR files listed above.
3. Add/update OCR v0.1.1 documentation and version notes.
4. Add database migration and SQLDelight query updates.
5. Expand `OcrIndexStats` and repository stats.
6. Implement OCR-safe decoding and tiling.
7. Bump engine/preprocessing version.
8. Fix empty/failed/success index status semantics.
9. Fix skip logic.
10. Add smart search ranking and result metadata.
11. Update UI stats, result cards, scope/limit controls, and cleanup actions.
12. Add tests.
13. Build/test.
14. Create v0.1.1 implementation notes documenting exactly what changed.

---

## Acceptance Criteria

The implementation is acceptable when:

- OCR no longer destroys long webtoon/manhwa pages by shrinking width excessively.
- Empty OCR output is visible as empty, not counted as useful recognized text.
- Re-indexing can repair old empty v0.1.0 rows.
- Search can find results using partial remembered text, not only exact full phrases.
- OCR results show manga, source/extension, chapter, page, and snippet.
- The OCR screen shows estimated text-index storage.
- Users can clear the OCR index.
- Users can limit indexing runs so large libraries do not become a runaway task.
- No page images or temporary scan artifacts are stored permanently.
- Tests cover the new ranking, tile planning, and skip semantics.
- A new implementation markdown file documents the completed work.

---

## Notes For Future OCR Versions

Possible later improvements, not part of v0.1.1 unless explicitly approved:

- Optional CJK OCR APK variant.
- Per-manga OCR launch directly from manga detail screen.
- OCR result filtering by manga/source/chapter.
- FTS virtual table if SQLDelight/SQLite setup supports it cleanly.
- Backup/export of OCR text index, likely disabled by default due privacy and size.
- User-selectable OCR languages/models.
- Background indexing while charging only.
- OCR indexing only unread/recent/downloaded-favorite chapters.
