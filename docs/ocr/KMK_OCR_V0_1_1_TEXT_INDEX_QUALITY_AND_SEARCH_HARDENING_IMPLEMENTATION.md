# KMK-OCR v0.1.1 — Text Index Quality and Smart Search Hardening: Implementation Notes

> Branch/APK line: **KMK-OCR** (separate from KMK-Recs)
> APK: `Komikku-v1.13.6-kmk-ocr.0.1.1-debug.apk`
> versionCode: 81
> Plan: `docs/ocr/KMK_OCR_V0_1_1_TEXT_INDEX_QUALITY_AND_SEARCH_HARDENING_PLAN.md`

---

## What Changed

### 1. OCR Preprocessing — Width-Based Sampling and Vertical Tiling

**File:** `app/src/main/java/exh/ocr/OcrTextRecognizer.kt`

v0.1.0 problem: `maxDim = 1280` applied to the larger dimension. A 900x12000 page was decoded to ~100px wide — speech-bubble text unreadable.

v0.1.1 fix:
- Width-based sample size only. Height does not determine downsampling.
- Target decoded width: 1800px (see `OcrTilePlanner.DEFAULT_TARGET_WIDTH`).
- `ARGB_8888` instead of `RGB_565` for better text color fidelity.
- Long pages are split into vertical tiles (default: 2500px tiles, 120px overlap). Each tile is OCR'd independently and results are concatenated.
- Tile bitmaps are recycled after each tile finishes. The full bitmap is recycled in `finally`.
- `OutOfMemoryError` is caught around bitmap creation — affected pages get an empty result instead of crashing.

### 2. Engine/Preprocessing Version Bump

`engineVersion` changed from `"16.0.1"` to `"16.0.1-tiled-v2"`.

This means all pages indexed by v0.1.0 have a different `engine_version` in the database. The v0.1.1 indexer will not see those rows as "already indexed for the current engine" and will re-index all pages on the next run. No migration data wipe is needed — old rows remain in the table until the user explicitly clears them.

The engine version string is defined as `MlKitLatinOcrTextRecognizer.ENGINE_VERSION` (companion object const) so that the screenmodel can reference it without creating a recognizer instance.

### 3. Database Schema — Migration 55

**File:** `data/src/main/sqldelight/tachiyomi/migrations/55.sqm`

Three columns added to `ocr_indexed_page`:
- `recognized_text_length INTEGER NOT NULL DEFAULT 0` — byte length of the recognized raw text.
- `recognized_word_count INTEGER NOT NULL DEFAULT 0` — token count from `OcrSearchRanker.tokenize()`.
- `ocr_status TEXT NOT NULL DEFAULT 'success'` — one of `'success'`, `'empty'`, `'failed'`.

Migration 55 also backfills existing v0.1.0 rows:
```sql
UPDATE ocr_indexed_page SET ocr_status = 'failed' WHERE error_message IS NOT NULL;
UPDATE ocr_indexed_page SET ocr_status = 'empty' WHERE raw_text = '' AND error_message IS NULL;
```

### 4. SQLDelight Queries

**File:** `data/src/main/sqldelight/tachiyomi/data/ocr_indexed_page.sq`

New/changed queries:
- `upsertPage` — now accepts `recognized_text_length`, `recognized_word_count`, `ocr_status`.
- `searchSuccessful` — replaces `searchNormalized`; selects only needed columns (9), filtered by `engine_version`, `ocr_status = 'success'`, `recognized_word_count > 0`, exact phrase match on `normalized_text`.
- `searchCandidatesForToken` — same filter but for a single token; used for partial token search.
- `getExistingPageState` — replaces `getExistingIdentity`; returns `(page_identity, ocr_status, recognized_word_count, error_message)` for the current engine version. Returns null if no current-engine-version row exists.
- `countProcessed`, `countRecognized`, `countEmpty`, `countFailed` — per engine version.
- `countDistinctManga`, `countDistinctChapters` — recognized pages only, per engine version.
- `estimatedIndexBytes` — `COALESCE(SUM(length(raw_text) + ...), 0)` across all rows.
- `countOldEngineRows` — rows where `engine_version != :currentEngineVersion`.
- `deleteByEngineVersion`, `deleteOldEngineRows`, `deleteEmptyAndFailed` — cleanup.

Note: search queries use explicit `SELECT col1, col2, ...` (not `SELECT *`) to keep the mapper lambda under Kotlin's Function22 arity limit.

### 5. OCR Status Semantics

**File:** `app/src/main/java/exh/ocr/OcrIndexService.kt`

v0.1.0 stored empty text as `errorMessage = null` (treated as success).

v0.1.1 distinguishes three outcomes:
- `success`: `rawText.isNotBlank()` and at least one recognized token. `recognized_word_count > 0`.
- `empty`: OCR ran but returned blank text, or all tokens were filtered out.
- `failed`: exception during decode or ML Kit processing.

Progress tracking now includes `recognizedPages` in `OcrIndexProgress` alongside `completedPages`, `emptyPages`, and `failedPages`.

### 6. Skip Logic Fix

**File:** `app/src/main/java/exh/ocr/OcrIndexService.kt`

v0.1.0 skipped pages if `(identity, engine_version)` matched — including empty rows.

v0.1.1 skips only when ALL of:
1. Current-engine-version row exists (`getExistingPageState` returns non-null).
2. Stored `page_identity` matches current (page unchanged).
3. `ocr_status == 'success'`.
4. `recognized_word_count > 0`.
5. `retryMode != FORCE_ALL`.

Empty and failed rows are always retried in normal mode. This automatically repairs v0.1.0 empty rows on the next indexing run.

### 7. Smart Search Ranking

**New file:** `app/src/main/java/exh/ocr/OcrSearchRanker.kt`

Pure Kotlin object (no Android dependencies). Contains:
- `tokenize(normalizedText: String): List<String>` — splits on space, removes stop words, removes tokens < 2 chars, deduplicates.
- `score(queryTokens, normalizedPageText, isExactPhrase): OcrMatchScore?` — returns null if below 60% match threshold. Returns `OcrMatchScore` with `matchType` (EXACT / ALL_TOKENS / PARTIAL), `matchedWords`, `missingWords`, and `score` (0.0–1.0).

Search flow in `OcrIndexRepository.searchSmart()`:
1. Normalize and tokenize the query.
2. Exact phrase search via `searchSuccessful` SQL.
3. If fewer than 5 exact matches, run per-token candidate search for the top 3 tokens.
4. Rank candidates in Kotlin via `OcrSearchRanker.score()`.
5. Return exact results first, then token-ranked results, capped at 50.

### 8. `OcrSearchQueryNormalizer` Changes

**File:** `app/src/main/java/exh/ocr/OcrSearchQueryNormalizer.kt`

- Added `tokenize(query: String): List<String>` — delegates to `OcrSearchRanker.tokenize(normalize(query))`.
- Updated `buildSnippet` to a 3-param version that prefers raw text for display (searching raw text case-insensitively first, then falling back to normalized text).
- Added compat 2-param overload (`buildSnippet(normalizedText, query)`) to keep existing tests passing.

### 9. Updated Data Classes

**File:** `app/src/main/java/exh/ocr/OcrSearchResult.kt`

`OcrSearchResult` now includes:
- `sourceId: Long` — used by screenmodel to resolve source name from `SourceManager`.
- `matchType: OcrMatchType` — EXACT, ALL_TOKENS, or PARTIAL.
- `matchedWords: List<String>`, `missingWords: List<String>`.
- `matchScore: Double`.

`OcrIndexStats` expanded to 8 fields: `processedPages`, `recognizedPages`, `emptyPages`, `failedPages`, `totalManga`, `totalChapters`, `estimatedBytes`, `oldEngineRows`.

`OcrMatchType` enum added (EXACT / ALL_TOKENS / PARTIAL).

### 10. Source Name Resolution

`OcrSearchResult.sourceName` is now populated in `OcrSearchScreenModel` using `SourceManager.get(result.sourceId)?.name` after each search. Source names are not stored in the DB (they can change when extensions are updated).

### 11. New Tile Planner

**New file:** `app/src/main/java/exh/ocr/OcrTilePlanner.kt`

Pure Kotlin object. Two functions:
- `widthSampleSize(originalWidth, targetWidth)` — returns BitmapFactory `inSampleSize` based on width only. Ignores height.
- `planTiles(bitmapHeight, tileHeight, overlap)` — returns `List<OcrTileSpec>` for a decoded bitmap. Short images produce one tile. Long images produce overlapping strips.

### 12. Retry Mode and Page Limit

**File:** `app/src/main/java/exh/ocr/OcrIndexService.kt` and `OcrIndexWorker.kt`

`OcrRetryMode` enum: `SKIP_SUCCESS_RETRY_EMPTY_FAILED` (default) / `FORCE_ALL`.

`OcrIndexWorker.start()` now accepts `retryMode` and `maxPages` (0 = unlimited).

### 13. UI Changes

**File:** `app/src/main/java/exh/ocr/OcrSearchScreen.kt`

- Result cards show: manga title, source/extension name (colored as primary), chapter name, page number, match type label (Exact match / All words matched / Partial: N of M words), and snippet from raw text.
- Stats line shows: recognized pages / processed pages, manga count, chapter count, estimated index size.
- Old engine rows warning with one-tap clear.
- Max pages dropdown (Unlimited / 50 / 100 / 250 / 500).
- "Force re-index all" button with confirmation dialog.
- Improved empty states: distinguishes no-index, no-recognized-text, and no-search-results.

**File:** `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`

New strings added:
- `ocr_indexing_progress_v2` — includes recognized pages count.
- `ocr_notification_complete_v2` — shows recognized/empty/failed breakdown.
- `ocr_index_status_v2` — expanded stats line.
- `ocr_index_status_old_rows` — warns about old engine rows.
- `ocr_empty_state_no_recognized` — when index exists but no text found.
- `ocr_empty_state_no_results_partial` — search returned nothing.
- `ocr_force_reindex_all`, `ocr_force_reindex_confirm_*` — force re-index UI.
- `ocr_clear_old_rows`, `ocr_match_exact`, `ocr_match_all_tokens`, `ocr_match_partial`.
- `ocr_max_pages_label`, `ocr_max_pages_unlimited`.

---

## How Old v0.1.0 Rows Are Handled

1. Migration 55 backfills `ocr_status` for all existing rows (empty → `'empty'`, error → `'failed'`, rest remain `'success'`).
2. All v0.1.0 rows have `engine_version = "16.0.1"`. The current engine version is `"16.0.1-tiled-v2"`.
3. `getExistingPageState` queries for the current engine version — it returns null for all v0.1.0 rows, so they are not counted as "already indexed" and will be re-indexed.
4. Search queries filter by `engine_version = :engineVersion` (current) — old rows never appear in search results.
5. Old rows remain in the table and consume storage until the user explicitly clears them via "Clear old OCR rows" (calls `deleteOldEngineRows(currentEngineVersion)`).

---

## Tests Added/Run

**New test files:**

| File | Tests | All pass |
|------|-------|----------|
| `OcrTilePlannerTest.kt` | 10 (tile planning, width sampling) | Yes |
| `OcrSearchRankerTest.kt` | 11 (ranking, tokenization, thresholds) | Yes |
| `OcrSkipLogicTest.kt` | 7 (success/empty/failed/force scenarios) | Yes |

**Updated test file:**

| File | Tests | All pass |
|------|-------|----------|
| `OcrSearchQueryNormalizerTest.kt` | 11 (8 original + 3 new) | Yes |

Total: **39 OCR unit tests, all passing**.

---

## Remaining Limitations and Deferred Work

- CJK (Japanese, Korean, Chinese) script support not added — out of scope for v0.1.1. Latin model only.
- Per-manga indexing scope (launch OCR from manga detail page) not added — deferred to v0.1.2+.
- FTS (Full-Text Search) SQLite virtual table not used — LIKE queries are sufficient for current index sizes. FTS is a potential v0.2.x optimization.
- OCR text backup/export not added — privacy-sensitive, deferred.
- Background-while-charging-only indexing not added — deferred.
- The `countByManga` and `countByChapter` queries from v0.1.0 are still in the .sq file but unused in repository code — harmless.
- `OcrJobState.isRunning.value = true` in `OcrIndexWorker.start()` is set optimistically before the WorkManager enqueue — if enqueue fails, `isRunning` would be stuck true until the next state update from `OcrJobState.activeProgress`.
