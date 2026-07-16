# KMK-Recs v0.7.13 Source Evaluation Recommendation Quality Functional Fix â€” Implementation

Date: 2026-06-27

Status: COMPLETE. All build gates pass.

APK: `Komikku-v1.13.6-kmk.7.13-debug.apk`

## Summary

v0.7.12 made rec-quality errors visible in the UI. v0.7.13 fixes the underlying problem: nearly every Strong Fit / Worth Trying source was returning Error or No Matches from the probe.

**Root cause:** `SourceRecommendationFitProbe` called `source.getSearchManga()`, converted raw `SManga` to domain manga via `toDomainManga()`, and scored immediately. Most extensions return `SManga` with `genre = null` on search results â€” `getMangaDetails` is required to populate tag metadata. `PersonalRecommendationScorer` sees null genre â†’ score = 0 â†’ all candidates filtered out â†’ WEAK or NO_MATCHES even for good recommenders.

**Fix:** Bounded inline enrichment in the probe â€” call `getMangaDetails(smanga)` for up to 5 candidates per plan with no genre, 10s timeout per call, sequential. Matches For You's `RecommendationCandidateEnricher` behavior without requiring `NetworkToLocalManga` (no DB dependency in the probe).

## Version

- `versionCode = 85`
- `VERSION_CODE = 713`
- `VERSION_NAME = "KMK-Recs v0.7.13"`

## Files Modified

### Core Probe Logic

**`app/src/main/java/exh/recs/evaluation/SourceRecommendationFitProbe.kt`**
- Added `ENRICH_CAP_PER_PLAN = 5`, `ENRICH_TIMEOUT_MS = 10_000L` constants.
- Per-plan enrichment loop: builds `smangaByUrl` map, then for each candidate where `needsProbeEnrichment()` is true (i.e., `genre.isNullOrEmpty()`), calls `source.getMangaDetails(smanga)` with a 10s timeout and re-converts to domain manga.
- Tracks `planEnrichedCount` and `planWeakCount` (candidates still lacking genre after enrichment).
- Populates `enrichedCandidateCount` and `weakMetadataCandidateCount` in the outcome.
- Adds enrichment note to reason strings: "3 visible (2 enriched)" or "5 results, all had no genre metadata".

**`app/src/main/java/exh/recs/evaluation/SourceRecommendationFitProbeOutcome.kt`**
- Added `enrichedCandidateCount: Int = 0` and `weakMetadataCandidateCount: Int = 0` fields.

### New Files

**`app/src/main/java/exh/recs/evaluation/SourceRecommendationFitFailureClassifier.kt`** (NEW)
- `SourceRecommendationProbeFailureKind` enum with 14 values: NONE, NO_TASTE_EVIDENCE, AVAILABLE_EXTENSION_LIST_EMPTY, EXTENSION_NOT_FOUND, EXTENSION_MATCH_AMBIGUOUS, INSTALL_FAILED_OR_TIMED_OUT, INSTALLED_EXTENSION_DID_NOT_LOAD, SOURCE_NOT_FOUND, SOURCE_MATCH_AMBIGUOUS, SEARCH_ERROR, SEARCH_TIMED_OUT, RAW_RESULTS_EMPTY, RESULTS_NO_METADATA, ALL_RESULTS_BLOCKED, UNKNOWN.
- `SourceRecommendationFitFailureClassifier.classify(errorMessage)`: case-insensitive substring matching on fixed-format error strings.
- `isInstallOrLoadIssue(kind)`: distinguishes infrastructure failures from search-level failures.

**`app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityDiagnostics.kt`** (NEW)
- `Summary(checkedCount, notCheckedCount, installLoadIssueCount, searchErrorCount, noResultsCount, weakCount, goodCount)` data class.
- `compute(evaluations, fitsByEvalKey)`: filters promising evaluations (STRONG_FIT + WORTH_TRYING), counts checked vs not-checked, routes each checked fit to the appropriate category.

### Screen Model

**`app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt`**
- `State` gained `recQualityDiagnostics: SourceRecommendationQualityDiagnostics.Summary?`.
- `loadRecommendationFits()`: after loading fits, computes diagnostics and updates state.
- `buildRecQualityFitFromOutcome()`: extended to populate `errorMessage` for WEAK and NO_MATCHES verdicts in addition to ERROR.

### UI

**`app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt`**
- Added compact diagnostics `Text` label in the Recommendation Quality section (shown when `diag.hasAnyResults`).
- Changed reason text display: shown for all non-positive verdicts, with error color for ERROR and subdued color (`onSurfaceVariant`) for WEAK/NO_MATCHES.

### Strings

**`i18n-kmk/src/commonMain/moko-resources/base/strings.xml`**
- Added `source_evaluation_rec_quality_diagnostics`: "Checked: %1$d Â· Install/load issues: %2$d Â· Search errors: %3$d Â· No results: %4$d Â· Weak: %5$d Â· Good: %6$d"

### Version and Release Notes

**`app/build.gradle.kts`**: `versionCode = 85`

**`app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`**: `VERSION_CODE = 713`, `VERSION_NAME = "KMK-Recs v0.7.13"`, What's New added.

## Test Files Modified / Added

**`app/src/test/java/exh/recs/evaluation/SourceRecommendationFitProbeTest.kt`** (modified)
- Extended `FakeCatalogueSource` with `returns: List<SManga>` and `detailsGenre: String?` parameters.
- Added 3 new v0.7.13 tests:
  1. `probe returns NO_MATCHES when search succeeds but raw list is empty` â€” verifies empty search results map to NO_MATCHES without errors.
  2. `probe reports weak metadata when source returns results without genre` â€” verifies no-genre results are counted as weak, not as errors.
  3. `probe enrichment turns weak raw result into scored candidate when details provide genre` â€” verifies that `getMangaDetails` filling genre causes the candidate to become visible.

**`app/src/test/java/exh/recs/evaluation/SourceRecommendationFitFailureClassifierTest.kt`** (NEW)
- 15 tests covering: null/blank â†’ NONE, each known error string pattern â†’ correct kind, all install/load kinds recognized by `isInstallOrLoadIssue`, search kinds not recognized as install/load.

**`app/src/test/java/exh/recs/evaluation/SourceRecommendationQualityDiagnosticsTest.kt`** (NEW)
- 10 tests covering: empty list, non-promising verdicts ignored, not-yet-checked counting, GREAT/GOOD/MIXED â†’ goodCount, WEAK â†’ weakCount, NO_MATCHES/TOO_LITTLE_EVIDENCE â†’ noResultsCount, ERROR+install message â†’ installLoadIssueCount, ERROR+search message â†’ searchErrorCount, mixed evaluations, GOOD+MIXED both increment goodCount.

## Build Gates

All run and passed:

```
.\gradlew.bat spotlessApply    â†’ BUILD SUCCESSFUL
.\gradlew.bat spotlessCheck    â†’ BUILD SUCCESSFUL
.\gradlew.bat :app:testDebugUnitTest  â†’ BUILD SUCCESSFUL (690 tests, 0 failed)
.\gradlew.bat assembleDebug    â†’ BUILD SUCCESSFUL
```

## Design Decisions

**Why no `NetworkToLocalManga` in probe:** The probe is a bounded quality assessment, not a library sync. Avoiding `NetworkToLocalManga` keeps the probe free of DB side-effects and makes it testable with a pure `FakeCatalogueSource` stub. For You uses `NetworkToLocalManga` because it needs DB-backed deduplication and manga tracking; the probe does not.

**Enrichment signal: `genre.isNullOrEmpty()` vs `!initialized`:** `RecommendationCandidateEnricher` uses `!initialized` as the enrichment signal. The probe uses `genre.isNullOrEmpty()` because genre presence is what the scorer actually needs â€” `initialized` being true does not guarantee genre was populated.

**Cap of 5 per plan:** Keeps the worst case bounded: 2 plans Ã— 5 enrichment calls Ã— 10s = 100s max additional time, which is within a reasonable async background probe budget. No user-facing spinner is shown for the probe.

**`SManga.genre` mutability:** `SManga` is an interface with mutable `var genre: String?`. `getMangaDetails` sets `manga.genre = ...` on the same object. The probe passes the original `smanga` reference to `getMangaDetails`, then calls `details.toDomainManga(source.id)` on the returned object to get an updated domain manga.

