# KMK-Recs v0.7.39 Implementation Report
# For You Rolling Discovery Progress And Rated Group Recommendations

Date: 2026-07-10
Version label: KMK-Recs v0.7.39
APK: Komikku-v1.13.6-kmk.7.39-debug.apk

---

## User-Approved Scope

Approved plan: `KMK_RECS_V0_7_39_FOR_YOU_ROLLING_DISCOVERY_AND_RATED_GROUP_RECOMMENDATIONS_PLAN.md`

Two tracks:

- **Track A (new work):** For You rolling discovery â€” track evaluated pages separately from successful candidates in a new `recommendation_discovery_progress` table (migration 57). Empty/filtered/error pages must not be retried forever. Merge new + remembered candidates and show best overall.
- **Track B (already done, verify only):** Group-based recommendations from rated manga â€” confirmed that cross-source group seeding from all linked versions was already complete in v0.7.38.

---

## Problem Solved

### Core v0.7.38 Flaw

`RecommendationDiscoveryPlanner.nextPageToProbe()` (old) took `knownPages: Set<Int>` from `recommendation_candidate_memory`. A page only appeared in `knownPages` if it had at least one candidate stored in memory.

Consequence: pages returning empty results, fully filtered results, errors, or all-duplicate results were invisible to the planner. On every refresh they were retried from scratch â€” wasting network budget and resetting progress.

### Fix

A separate `recommendation_discovery_progress` table records ALL evaluated pages regardless of outcome. Status values: `success`, `empty`, `filtered`, `duplicate`, `error`, `unsupported`, `exhausted`. The planner now uses `evaluatedPages` from this table, so any evaluated page (including empty/filtered/error) advances the page counter.

Additional page cap raised from 3 to 20 pages total across all refreshes per source/query (slow progressive discovery). Per-refresh new page limit is 1 (unchanged behavior: only probe one extra page per source per refresh).

---

## Files Changed

### New Files

| File | Purpose |
|---|---|
| `data/src/main/sqldelight/tachiyomi/migrations/57.sqm` | Migration: creates `recommendation_discovery_progress` table + 2 indexes |
| `data/src/main/sqldelight/tachiyomi/data/recommendation_discovery_progress.sq` | SQLDelight queries: getBySourceQuery, getEvaluatedPagesBySourceQuery, upsert, deleteBySourceQuery, deleteBySource, deleteAll |
| `domain/src/main/java/tachiyomi/domain/taste/model/RecommendationDiscoveryProgress.kt` | Domain model with STATUS_* constants |
| `domain/src/main/java/tachiyomi/domain/taste/repository/RecommendationDiscoveryProgressRepository.kt` | Repository interface |
| `domain/src/main/java/tachiyomi/domain/taste/interactor/GetRecommendationDiscoveryProgress.kt` | Interactor: awaitEvaluatedPages, awaitBySourceQuery |
| `domain/src/main/java/tachiyomi/domain/taste/interactor/UpsertRecommendationDiscoveryProgress.kt` | Interactor: await(entry) |
| `domain/src/main/java/tachiyomi/domain/taste/interactor/ClearRecommendationDiscoveryProgress.kt` | Interactor: await() calls deleteAll |
| `data/src/main/java/tachiyomi/data/taste/RecommendationDiscoveryProgressRepositoryImpl.kt` | Repository impl: maps Long columns to Int for page/count fields |
| `app/src/main/java/exh/recs/memory/RecommendationDiscoveryProgressStore.kt` | Convenience wrapper: evaluatedPages(), recordProgress() |
| `app/src/test/java/exh/recs/memory/RecommendationDiscoveryPlannerTest.kt` | 10 unit tests for planner logic |

### Modified Files

| File | Change |
|---|---|
| `app/src/main/java/eu/kanade/domain/KMKDomainModule.kt` | DI bindings for new repository impl + 3 interactors |
| `app/src/main/java/exh/recs/memory/RecommendationDiscoveryPlanner.kt` | Rewritten: new constants (MAX_NEW_PAGES_PER_SOURCE_REFRESH=1, MAX_NEW_CANDIDATES_PER_DISCOVERY_PAGE=20, MAX_DISCOVERY_PAGE_PER_SOURCE_QUERY=20); nextPageToProbe() takes evaluatedPages: Set\<Int\> |
| `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt` | Injects GetRecommendationDiscoveryProgress + UpsertRecommendationDiscoveryProgress; uses progressStore.evaluatedPages() instead of memoryStore.knownPages(); records page-1 progress after upsertBatch; discoverAdditionalPage() records progress for every outcome |
| `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt` | confirmClearDiscoveryHistory() now also calls clearDiscoveryProgress.await() so discovery restarts from page 1 |
| `app/src/test/java/eu/kanade/tachiyomi/data/database/KmkMigrationTest.kt` | KMK_MIGRATION_RANGE=46..57; RECOMMENDATION_DISCOVERY_PROGRESS_COLUMNS constant; migration 57 test; test name updated to "12 files" |
| `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` | VERSION_CODE=739, VERSION_NAME="KMK-Recs v0.7.39"; v0.7.39 release notes added at top |

---

## Behavior Changed

### Before (v0.7.38)

1. Page 1 fetch â†’ if results found â†’ store in memory as `knownPages`.
2. Next refresh: planner reads `knownPages`, finds page 1, probes page 2.
3. If page 2 returned empty/error â†’ NOT stored in memory â†’ page 2 retried on every refresh forever.
4. Cap was 3 pages total per refresh (not cumulative).

### After (v0.7.39)

1. Page 1 fetch â†’ store candidates in memory AND record page-1 progress entry (status = success/filtered/empty).
2. Next refresh: planner reads `evaluatedPages` from progress table. Page 1 is known. Probes page 2.
3. If page 2 returned empty/error â†’ recorded in progress table with status=empty or status=error. Page 2 is now "evaluated" and skipped on future refreshes.
4. Cap is 20 pages cumulative across all refreshes. One new page per refresh session.
5. User can reset via Settings â†’ Reset For You discovery history (clears both candidate memory AND progress table).

---

## Constants

```
MAX_NEW_PAGES_PER_SOURCE_REFRESH = 1    (unchanged behavior: 1 new page per session)
MAX_NEW_CANDIDATES_PER_DISCOVERY_PAGE = 20    (candidates taken per additional page)
MAX_DISCOVERY_PAGE_PER_SOURCE_QUERY = 20    (cumulative page cap across all refreshes)
```

---

## Track B Verification

Read and verified:
- `GroupRecommendationSeedBuilder.build()` already queries ALL `manga_cross_source_link` members (cross-source) and builds a weighted seed from all linked versions' genres.
- `GroupSeededRecommendationsScreenModel` already uses 5-source cap, 45s total timeout, 5s per-candidate localization timeout, `NetworkToLocalManga` for crash safety.
- `RatedMangaScreen` / `LovedMangaScreen` already have full UI parity (v0.7.36).
- No new Track B code needed.

---

## Tests Run

Spotless: `.\gradlew.bat spotlessCheck` â€” BUILD SUCCESSFUL (1 import ordering fix applied via spotlessApply)

Unit tests: `.\gradlew.bat :app:testDebugUnitTest` â€” BUILD SUCCESSFUL (267+ actionable tasks)

Tests confirmed passing:
- `KmkMigrationTest` (range 46..57, 12 files, migration 57 column test)
- `RecommendationDiscoveryPlannerTest` (10 new tests)
- All prior test suites unchanged

---

## Known Limitations

- `recommendation_discovery_progress` is NOT in backup/sync (intentional: it is derived local cache, equivalent to `recommendation_candidate_memory`).
- The progress table does not track page fetches that fail before any result is received (network timeout before the first byte). Those pages will be retried next refresh. This is acceptable: true network failures are transient.
- If the profile fingerprint changes (ratings/settings updated), the progress table is NOT automatically cleared. Old progress records from a different profile remain and continue to gate page progression. This is a known tradeoff: clearing progress on every profile change would eliminate the benefit of progressive discovery for long-term stable profiles. The user can manually reset via Settings.

---

## Deviations From Plan

None. All planned behavior implemented as specified.

---

## APK Output

Build command: `.\gradlew.bat assembleDebug`
Copy to: `C:\Users\USER\Downloads\Komikku\private\Komikku-v1.13.6-kmk.7.39-debug.apk`

