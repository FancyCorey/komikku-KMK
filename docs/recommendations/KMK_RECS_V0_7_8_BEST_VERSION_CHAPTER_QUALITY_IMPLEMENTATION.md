# KMK-Recs v0.7.8: Best Version / Chapter Quality Implementation

Date: 2026-06-22

Status: implemented.

## Overview

v0.7.8 adds a "Find best version" workflow to the manga detail page. The user picks a reference chapter, the app searches for the same manga across all configured sources, loads matching chapters in each candidate, previews sampled mid-chapter pages, and lets the user pick the best-quality version to migrate to or copy.

A new `manga_source_quality_signal` table (migration 53) stores the user's confirmed chapter quality choices for future use.

## What Was Implemented

### Entry Point

A new "Find best version" item is added to the rating dropdown in the manga detail header (after "Seen other versions"). Tapping it navigates to `BestVersionCompareScreen(originMangaId)`.

### Preferences (4 new)

Added to `SourcePreferences.kt` in a new KMK v0.7.8 block:

| Preference | Key | Default |
| --- | --- | --- |
| `sameMangaMatchResultsPerSource()` | `same_manga_match_results_per_source` | 2 |
| `sameMangaMatchPreselectResults()` | `same_manga_match_preselect_results` | true |
| `bestVersionPreviewSampleSize()` | `best_version_preview_sample_size` | 5 |
| `bestVersionAvoidFirstPages()` | `best_version_avoid_first_pages` | true |

### Settings UI

Recommendation Settings gains a "Same manga matching" section above the Source Evaluation section. It contains four items:

- Results per source: list preference, valid values `[1, 2, 5, 10]`.
- Preselect results: switch â€” whether all same-manga candidates are selected by default.
- Preview sample size: list preference, valid values `[2, 5, 10]`.
- Avoid first pages: switch â€” skip cover / title pages when sampling.

### SameMangaMatchSettings (new pure data class)

`app/src/main/java/exh/recs/matching/SameMangaMatchSettings.kt`

Holds the four preference values, provides `VALID_RESULT_CAPS`, `VALID_SAMPLE_SIZES`, `DEFAULT_RESULT_CAP`, `DEFAULT_SAMPLE_SIZE`, and clamp functions `clampResultCap(value)` / `clampSampleSize(value)`.

### SameMangaCandidateResult (new sealed interface)

`app/src/main/java/exh/recs/matching/SameMangaCandidateResult.kt`

States: `Loading`, `Error(throwable)`, `Success(results: List<Manga>)`.

Also holds `SameMangaSourceResult(source, result)` for per-source packaging.

### SameMangaCandidateSearcher (new)

`app/src/main/java/exh/recs/matching/SameMangaCandidateSearcher.kt`

Encapsulates the multi-query, per-source, bounded, parallel search logic that was previously inline in `CrossExtensionMatchScreenModel`. Key responsibilities:

- `getMatchingSources()` â€” language filter, source order, disabled-source exclusion.
- `search(queries, settings, originManga, sources, onResult)` â€” parallel coroutine fan-out using `coroutineScope { ... .map { async { ... } }.awaitAll() }`.
- `searchOneSource(source, queries, cap, originManga)` â€” private; runs queries in sequence, stops when cap is reached after filtering out the origin manga.

### CrossExtensionMatchScreenModel (updated)

Two changes to use the new settings:

1. `search()` reads cap from preferences via `SameMangaMatchSettings.clampResultCap(sourcePreferences.sameMangaMatchResultsPerSource().get())`.
2. `updateItem()` respects preselect preference â€” only auto-selects all candidates when `sameMangaMatchPreselectResults().get()` is `true`.

### MangaInfoHeader (updated)

`eu/kanade/presentation/manga/components/MangaInfoHeader.kt`

Added `onFindBestVersionClicked: (() -> Unit)? = null` parameter. When non-null, a "Find best version" dropdown menu item (with `Icons.AutoMirrored.Outlined.CallMerge`) is added after "Seen other versions".

### Presentation MangaScreen (updated)

`eu/kanade/presentation/manga/MangaScreen.kt`

`onFindBestVersionClicked` parameter threaded through `MangaScreen`, `MangaScreenSmallImpl`, and `MangaScreenLargeImpl` (3 function signatures, all call sites, and all `MangaInfoBox` call sites).

### UI MangaScreen (updated)

`eu/kanade/tachiyomi/ui/manga/MangaScreen.kt`

Wired `onFindBestVersionClicked` callback that pushes `BestVersionCompareScreen(successState.manga.id)` onto the Voyager navigator.

### BestVersionPageSampler (new pure object)

`app/src/main/java/exh/recs/bestversion/BestVersionPageSampler.kt`

```
sample(totalPages, sampleSize, avoidFirstPages): List<Int>
```

Algorithm:
- Returns empty for zero pages or zero sample size.
- Returns all pages (0-indexed) for tiny chapters (totalPages â‰¤ sampleSize).
- Otherwise samples evenly from a 30â€“75% window. When `avoidFirstPages=true`, skips pages 0 and 1. Never includes the last page. Returns distinct 0-based indexes.

### BestVersionChapterMatcher (new pure object)

`app/src/main/java/exh/recs/bestversion/BestVersionChapterMatcher.kt`

- `findMatch(targetChapterNumber: Double, candidates: List<SChapter>): SChapter?` â€” returns the candidate whose `chapter_number` is within Â±1.0 of target; `null` if none qualify.
- `selectDefaultChapter(chapters: List<Chapter>): Chapter?` â€” priority: in-progress (lastPageRead > 0 and not read) > latest read > latest by chapterNumber.

### BestVersionCompareScreen + BestVersionCompareScreenModel (new)

`app/src/main/java/exh/recs/bestversion/`

**State machine** (`BestVersionStep` sealed interface):

| Step | Description |
| --- | --- |
| `LoadingOrigin` | Fetching origin manga from DB |
| `SearchingCandidates` | Running parallel source search |
| `ConfirmCandidates` | User reviews and confirms candidate list |
| `LoadingChapters` | Fetching chapter lists for confirmed candidates |
| `SelectChapter` | User selects origin chapter number for comparison |
| `LoadingPreview` | Fetching page lists and resolving image URLs for sampled pages |
| `ComparePreview` | User views sampled pages side-by-side and picks best |
| `PreparingMigration` | Calling `MigrateMangaUseCase` |
| `Done` | Migration or copy complete |
| `Error(message)` | Any fatal failure; shows retry button |

**Supporting sealed interfaces:**

- `CandidateChapterState`: `Loading`, `Available(chapter, totalChapters)`, `Unavailable`, `ChapterError(msg)`
- `CandidatePreviewState`: `Loading`, `Loaded(pageUrls)`, `PreviewError(msg)`

**Key state fields:** step, originManga, candidates map, selectedKeys, selectedChapterNumber, originChapters, candidateChapters map, candidatePreviews map, sampleSize, avoidFirstPages, selectedBestKey, isMigrating, migrationComplete.

**Key actions:**
- `toggleSelection(key)` â€” toggles candidate selection, ignores origin key.
- `confirmCandidates()` â†’ sets step to `LoadingChapters`, fans out chapter fetches per candidate.
- `startPreview()` â†’ sets step to `LoadingPreview`, samples pages, resolves image URLs via `HttpSource.getImageUrl(page)`.
- `selectBestVersion(key)` â†’ sets `selectedBestKey`, opens migration confirm dialog.
- `confirmMigration(replace: Boolean)` â†’ calls `migrateMangaUseCase(current, target, replace)`, then `saveQualitySignal()`, then step â†’ `Done`.

**Screen (Voyager):** `class BestVersionCompareScreen(private val originMangaId: Long)` â€” primitive constructor for Voyager state-save safety.

UI switches on `state.step` and renders:
- `ConfirmCandidatesContent` â€” candidate list with ElevatedCards, checkmarks, Confirm button.
- `SelectChapterContent` â€” per-candidate chapter state display.
- `ComparePreviewContent` â€” page thumbnail LazyRow per candidate, OutlinedButton to select best.
- `MigrationConfirmDialog` â€” AlertDialog with Migrate / Copy / Cancel.

### SQL Domain/Data Layer: manga_source_quality_signal (new)

**SQLDelight table** (`data/src/main/sqldelight/tachiyomi/data/manga_source_quality_signal.sq`):

15 columns: id (AUTOINCREMENT PK), origin_source_id, origin_url, origin_title, selected_source_id, selected_url, selected_title, selected_source_name, compared_candidates_json, chapter_number (nullable REAL), chapter_name, sample_size, sampled_pages_json, selected_at, quality_signal_version.

Queries: `getByOrigin`, `getBySelectedSource`, `getAll`, `insert`, `deleteById`, `deleteAll`.

**Migration 53.sqm** (`data/src/main/sqldelight/tachiyomi/migrations/53.sqm`): `CREATE TABLE IF NOT EXISTS manga_source_quality_signal (...)`.

**Domain model** (`domain/src/main/java/tachiyomi/domain/taste/model/MangaSourceQualitySignal.kt`): data class matching all 15 columns.

**Repository interface** (`domain/src/main/java/tachiyomi/domain/taste/repository/MangaSourceQualitySignalRepository.kt`): `getAll()`, `getByOrigin(sourceId, url)`, `getBySelectedSource(sourceId)`, `insert(signal)`, `deleteById(id)`, `deleteAll()`.

**Interactors:**
- `GetMangaSourceQualitySignals` â€” wraps `getAll()`, `getByOrigin()`, `getBySelectedSource()`.
- `UpsertMangaSourceQualitySignal` â€” wraps `insert(signal)`.

**Impl** (`data/src/main/java/tachiyomi/data/taste/MangaSourceQualitySignalRepositoryImpl.kt`): `DatabaseHandler`-based with `signalMapper` private val for row â†’ domain mapping.

**DI** (`KMKDomainModule.kt`): registered `MangaSourceQualitySignalRepository`, `GetMangaSourceQualitySignals`, `UpsertMangaSourceQualitySignal`.

### i18n Strings

40+ new strings added after the v0.7.7 block in `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`:

Settings strings: `same_manga_matching_settings_header`, `same_manga_match_results_per_source_title/summary`, `same_manga_match_preselect_title/summary`, `best_version_preview_pages_title/summary`, `best_version_avoid_first_pages_title/summary`.

Screen strings: `best_version_find_action`, `best_version_screen_title`, `best_version_searching`, `best_version_loading_origin`, `best_version_confirm_candidates_title/summary`, `best_version_confirm_action`, `best_version_no_candidates`, `best_version_loading_chapters`, `best_version_select_chapter_title`, `best_version_chapter_unavailable`, `best_version_start_preview_action`, `best_version_loading_preview`, `best_version_pages_loaded`, `best_version_candidate_source`, `best_version_candidate_chapter`, `best_version_select_as_best`, `best_version_migrate_title/body`, `best_version_migrate_action`, `best_version_copy_action`, `best_version_preparing_migration`, `best_version_migration_complete`, `best_version_error_origin_missing`, `best_version_error_no_target_chapters`, `best_version_retry`, `best_version_change_chapter`, `best_version_change_sample`, `best_version_sample_size_label`.

### KmkRecsReleaseNotes (updated)

- `VERSION_CODE = 780`
- `VERSION_NAME = "KMK-Recs v0.7.8"`
- v0.7.8 changelog added at top of MARKDOWN (6 user-facing bullet points).

## Files Changed

### New Files

| File | Description |
| --- | --- |
| `app/src/main/java/exh/recs/matching/SameMangaMatchSettings.kt` | Pure data class for same-manga matching settings with clamp helpers |
| `app/src/main/java/exh/recs/matching/SameMangaCandidateResult.kt` | Sealed interface for per-source search results |
| `app/src/main/java/exh/recs/matching/SameMangaCandidateSearcher.kt` | Extracted parallel bounded search logic |
| `app/src/main/java/exh/recs/bestversion/BestVersionPageSampler.kt` | Pure sampling algorithm (30â€“75% window) |
| `app/src/main/java/exh/recs/bestversion/BestVersionChapterMatcher.kt` | Chapter matching by closest chapter_number within Â±1.0 |
| `app/src/main/java/exh/recs/bestversion/BestVersionCompareScreenModel.kt` | StateScreenModel with full state machine (10 steps) |
| `app/src/main/java/exh/recs/bestversion/BestVersionCompareScreen.kt` | Voyager screen (primitive Long constructor) |
| `data/src/main/sqldelight/tachiyomi/data/manga_source_quality_signal.sq` | SQLDelight schema + queries for quality signal table |
| `data/src/main/sqldelight/tachiyomi/migrations/53.sqm` | Migration 53: creates manga_source_quality_signal |
| `domain/src/main/java/tachiyomi/domain/taste/model/MangaSourceQualitySignal.kt` | Domain model (15 fields) |
| `domain/src/main/java/tachiyomi/domain/taste/repository/MangaSourceQualitySignalRepository.kt` | Repository interface |
| `domain/src/main/java/tachiyomi/domain/taste/interactor/GetMangaSourceQualitySignals.kt` | Read interactor |
| `domain/src/main/java/tachiyomi/domain/taste/interactor/UpsertMangaSourceQualitySignal.kt` | Write interactor |
| `data/src/main/java/tachiyomi/data/taste/MangaSourceQualitySignalRepositoryImpl.kt` | DatabaseHandler-based impl |
| `app/src/test/java/exh/recs/matching/SameMangaMatchSettingsTest.kt` | 18 tests |
| `app/src/test/java/exh/recs/bestversion/BestVersionPageSamplerTest.kt` | 11 tests |
| `app/src/test/java/exh/recs/bestversion/BestVersionChapterMatcherTest.kt` | 11 tests |

### Modified Files

| File | Changes |
| --- | --- |
| `app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt` | +4 new preferences in KMK v0.7.8 block |
| `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` | +40 new strings |
| `app/src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt` | Read cap from preferences; respect preselect preference |
| `app/src/main/java/eu/kanade/presentation/manga/components/MangaInfoHeader.kt` | +onFindBestVersionClicked parameter and dropdown item |
| `app/src/main/java/eu/kanade/presentation/manga/MangaScreen.kt` | Thread onFindBestVersionClicked through 3 function signatures |
| `app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreen.kt` | Wire onFindBestVersionClicked â†’ BestVersionCompareScreen |
| `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt` | +4 action methods, +4 state fields, init from preferences |
| `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt` | +"Same manga matching" section with 4 preference items |
| `app/src/main/java/eu/kanade/domain/KMKDomainModule.kt` | Register quality signal repository and interactors |
| `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` | VERSION_CODE=780, v0.7.8 changelog |

## Tests

### New Tests

| Test File | Count | Coverage |
| --- | --- | --- |
| `SameMangaMatchSettingsTest` | 18 | Default values, valid caps (1/2/5/10), invalid caps â†’ default, valid sample sizes, invalid â†’ default |
| `BestVersionPageSamplerTest` | 11 | Empty chapter, zero sample size, tiny chapter all-pages, sample size respected, avoidFirstPages skips page 0, indexes in range, no duplicates, 30-page/5-sample example |
| `BestVersionChapterMatcherTest` | 11 | findMatch: empty, exact, within tolerance, outside tolerance, single; selectDefaultChapter: empty, in-progress, latest-read, fallback-to-latest, single |

### Test Results

```
SameMangaMatchSettingsTest     â€” 18 tests, all PASSED
BestVersionPageSamplerTest     â€” 11 tests, all PASSED
BestVersionChapterMatcherTest  â€” 11 tests, all PASSED
:app:testDebugUnitTest (full)  â€” BUILD SUCCESSFUL
:app:assembleDebug             â€” BUILD SUCCESSFUL in 2m 4s
```

## APK

```
Komikku-v1.13.6-kmk.7.8-debug.apk
VERSION_CODE = 780
VERSION_NAME = KMK-Recs v0.7.8
```

## What Was Deferred

- **Quality signal display**: `manga_source_quality_signal` records are written after migration/copy but there is no UI to browse them yet. A future pass could show past quality decisions in the manga detail page or in Recommendation Settings.
- **Automatic best-version re-search**: There is no trigger to re-run the workflow when a higher-quality version is suspected (e.g. after a source is re-evaluated). The workflow is entirely manual.
- **`SameMangaCandidateSearcher` adoption**: The extractor was created but `CrossExtensionMatchScreenModel` was updated with inline cap/preselect preference reads rather than delegating its entire search to the new class. Full adoption (sharing the `search()` method) is deferred.
- **Network image loading**: Page preview thumbnails rely on Coil3 `AsyncImage` with the resolved URL. If a source requires session cookies or special headers for image URLs, preview loading may fail. No workaround is implemented.
- **Quality signal backup/restore**: `manga_source_quality_signal` is not included in proto backup.

## Known Risks

- **MangaScreen.kt parameter threading**: the `onFindBestVersionClicked` parameter was threaded through three overloaded composables (`MangaScreen`, `MangaScreenSmallImpl`, `MangaScreenLargeImpl`). A compile error occurred during implementation because `MangaScreenLargeImpl` starts its body with a different statement from `MangaScreenSmallImpl`; a targeted fix was needed. If upstream adds more screen variants, the parameter must be threaded manually again.
- **Migration 53**: if an existing install skips a migration (common when multiple migrations land between installs), the `manga_source_quality_signal` table may be absent. The `insert` path should be wrapped in a `.catch` if quality signals become load-bearing. Currently they are write-on-confirm only, so absence is non-fatal.
- **Voyager state save**: `BestVersionCompareScreen` uses a primitive `Long` constructor per the established pattern. Any future addition of non-primitive constructor params will require the route-mode extraction pattern from v0.7.1.

