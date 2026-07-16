# KMK-Recs v0.7.37 Group-Seeded Recommendations Loading Fix â€” Implementation Report

Date: 2026-07-08

Status: implemented. APK delivered.

Plan file: `KMK_RECS_V0_7_37_GROUP_SEEDED_RECOMMENDATIONS_LOADING_FIX_PLAN.md`

---

## Problem Addressed

After v0.7.36, the group-seeded recommendations screen could remain on the loading spinner indefinitely. Root cause: `GroupSeededRecommendationsScreenModel` ran a sequential nested loop (8 sources Ã— 2 plans Ã— 20 candidates Ã— 30s search timeout each) with no total timeout, no per-candidate localization timeout, no early exit, and no partial result emission.

Additionally, candidate deduplication used URL-only keys (`seenUrls: Set<String>`), which could incorrectly merge candidates from different sources that share the same URL path.

---

## Goals Met

1. Group-seeded recommendations never stay in `State.Loading` indefinitely.
2. Total load is bounded by `TOTAL_LOAD_TIMEOUT_MS = 45_000L`.
3. Per-candidate localization is bounded by `LOCALIZE_TIMEOUT_MS = 5_000L`.
4. The loop exits early once `TARGET_RESULTS = 20` are collected.
5. Source-aware deduplication via `GroupRecommendationLoopPolicy`.
6. `CancellationException` propagates correctly for navigation cancellation.
7. Cross-source link group rating exclusivity verified and fixed.
8. Tests written and all pass.

---

## Files Changed

### New Files

- `app/src/main/java/exh/recs/group/GroupRecommendationLoopPolicy.kt`
  - Pure object with `CandidateState(seedMemberKeys, seenNetworkKeys, seenLocalIds, resultCount)`.
  - `tryAcceptNetwork(state, sourceId, url)`: rejects seed members and URL-already-seen-on-same-source.
  - `tryAcceptLocal(state, localId)`: rejects post-localization duplicates.
  - `reachedTarget(state, targetResults)`: true when `resultCount >= targetResults`.

- `app/src/test/java/exh/recs/group/GroupRecommendationLoopPolicyTest.kt`
  - 13 unit tests: network accept/reject, seed member rejection, same-URL-different-source, local id dedup, target counting, combined flow.

- `app/src/test/java/exh/recs/loved/RatedMangaExclusivityTest.kt`
  - 9 unit tests for `resolveLinkedGroupRatingConflicts`: standalone kept, same-rating group kept, love>like winner, like>love winner, dislike newest suppresses love+like, standalone unaffected by other groups, rating overwrite semantics, empty input, key-not-in-map treated standalone.

### Modified Files

- `app/src/main/java/exh/recs/group/GroupSeededRecommendationsScreenModel.kt`
  - Constants updated: `MAX_SOURCES=5`, `RAW_CAP_PER_SOURCE=8`, `TARGET_RESULTS=20`, `SEARCH_TIMEOUT_MS=12_000L`, `LOCALIZE_TIMEOUT_MS=5_000L`, `TOTAL_LOAD_TIMEOUT_MS=45_000L`. `SEED_BOOST` and `SEED_BOOST_CAP` kept.
  - `load()`: calls `withTimeoutOrNull(TOTAL_LOAD_TIMEOUT_MS) { buildRecommendations(...) }`. Null result emits `State.Empty`.
  - `buildRecommendations(seed, sources, plans, aliasCandidates, boostedProfile, aliasMap)`: extracted loop with `outer@` label, `GroupRecommendationLoopPolicy.CandidateState`, `withTimeoutOrNull(LOCALIZE_TIMEOUT_MS)` around `networkToLocalManga`, `break@outer` at `TARGET_RESULTS`, and `catch (e: Exception) { if (e is CancellationException) throw e; continue }`.
  - Added imports: `CatalogueSource`, `RecommendationQueryPlan`, `CancellationException`, `withTimeoutOrNull` (replacing qualified `kotlinx.coroutines.withTimeoutOrNull`).

- `app/src/main/java/exh/recs/loved/LovedMangaSourceFilter.kt`
  - Added `resolveLinkedGroupRatingConflicts(tastes, linkGroupByKey)`: finds the latest `updatedAt` taste per confirmed link group, then filters tastes so only the winner-rating members remain. Standalone entries (no `linkGroupId`) always pass through.

- `app/src/main/java/exh/recs/loved/LovedMangaScreenModel.kt`
  - `load()` now loads `linkGroupByKey` before the rating filter.
  - Applies `resolveLinkedGroupRatingConflicts(installedTastes, linkGroupByKey)` before `filter { it.rating == filterRating.value }`.
  - The `linkGroupByKey` result (already computed) is still passed to `State.Success` for display grouping â€” no additional DB call added.

- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`
  - `VERSION_CODE = 737`, `VERSION_NAME = "KMK-Recs v0.7.37"`.
  - Release notes entry added for v0.7.37.

---

## Rated-Manga Exclusivity Invariant â€” Verification

### DB level (already correct, unchanged)

- `manga_taste.sq`: unique index on `(source, url)`, upsert on `manga_id` conflict.
- Rating the same `(source, url)` again overwrites the existing row â€” one taste row per exact local manga identity.
- No DB changes made in v0.7.37.

### Display level (fixed in v0.7.37)

Before this version: `LovedMangaScreenModel.load()` filtered tastes by rating before loading link groups. A confirmed cross-source link group with members at different ratings could appear in multiple rating tabs.

After this version: link groups are loaded first, `resolveLinkedGroupRatingConflicts` removes conflicting members (keeping only the latest-updated rating's members), and then the rating filter is applied. Each confirmed link group can appear in exactly one rating tab.

Cross-extension rating actions ("Love other versions", "Like other versions", "Dislike other versions") write per-version taste rows at `(source, url)` identity. The next `load()` re-applies conflict resolution so the group re-settles into one tab. No additional changes needed for those actions.

Direct single-manga rating (rating one version without cross-extension match) continues to work correctly: only that one taste row changes, and if the group now has a newer rating in a different tab, the next `load()` will resolve accordingly.

---

## Testing

```text
./gradlew spotlessApply       -- BUILD SUCCESSFUL
./gradlew :app:testDebugUnitTest -- BUILD SUCCESSFUL (267 tasks, all PASSED)
./gradlew :app:assembleDebug  -- BUILD SUCCESSFUL
```

New tests: 13 (GroupRecommendationLoopPolicyTest) + 9 (RatedMangaExclusivityTest) = 22 new tests.

---

## APK

```text
Komikku-v1.13.6-kmk.7.37-debug.apk
```

VERSION_CODE: 737

