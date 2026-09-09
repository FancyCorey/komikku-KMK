# KMK-Recs v0.7.40 For You And Rated-Group Policy Foundation Implementation

Date: 2026-07-11

Status: COMPLETE

APK: `Komikku-v1.13.6-kmk.7.40-debug.apk`

Verification: spotlessApply âœ“ Â· spotlessCheck âœ“ Â· testDebugUnitTest âœ“ (267 tasks) Â· assembleDebug âœ“

---

## Summary

Four deliverables shipped together in one batch:

- **A** â€” Same-refresh discovery merge fix: extra-page candidates no longer dropped when memory is empty.
- **B** â€” Typed discovery retry policy: migration 58, retry metadata columns, exponential backoff, 20-second probe timeout.
- **C** â€” Shared `RecommendationSourceSelector`: adopted in For You (same behavior) and group flow (now uses real user preferences).
- **D** â€” Shared `RecommendationCandidateVisibilityPolicy`: adopted in For You all three paths and in group flow with batched DB lookups.

---

## Deliverable A â€” Same-Refresh Discovery Merge Fix

### Problem

`BrowsePersonalRecommendationsScreenModel.searchSource()` had two early-return short-circuits:

```kotlin
// Cached path
if (resolvedMemory.isEmpty()) cached  // BUG: omits extra-page candidates

// Live path
if (resolvedMemory.isEmpty()) recommendations  // BUG: omits additionalResults
```

When the remembered-candidate memory for a source was empty (e.g., first-ever run or after a history reset), the code returned page-1 or cached results without merging additional-page discovery results. Any extra-page candidates fetched in the same refresh were silently discarded.

### Fix

Both short-circuits removed. Both paths always call `RecommendationCandidateMemoryRanker.merge(page1 + extraPage + memory, profile)` and the merged output is filtered for score > 0.

---

## Deliverable B â€” Typed Discovery Retry Policy

### Migration 58

File: `data/src/main/sqldelight/tachiyomi/migrations/58.sqm`

Three `ALTER TABLE recommendation_discovery_progress ADD COLUMN ...` statements:
- `attempt_count INTEGER NOT NULL DEFAULT 0`
- `next_retry_at INTEGER` (nullable, epoch ms)
- `failure_kind TEXT` (nullable, `"retryable"` or `"permanent"`)

Schema version advances from 57 to 58.

### Domain Model

`RecommendationDiscoveryProgress` gained three new fields (all with defaults):
- `attemptCount: Int = 0`
- `nextRetryAt: Long? = null`
- `failureKind: String? = null`

New constants: `FAILURE_KIND_RETRYABLE = "retryable"`, `FAILURE_KIND_PERMANENT = "permanent"`.

### RecommendationRetryClassifier

New pure object at `app/src/main/java/exh/recs/memory/RecommendationRetryClassifier.kt`.

- `MAX_ATTEMPTS = 3`
- `INITIAL_DELAY_MS = 5 * 60 * 1000` (5 minutes)
- `MAX_DELAY_MS = 24 * 60 * 60 * 1000` (24 hours)
- `classify(e)`: IO errors â†’ retryable; UnsupportedOperation, HTTP 4xx â†’ permanent; unknown â†’ retryable
- `isRetryable(failureKind, attemptCount)`: retryable kind AND `attemptCount < MAX_ATTEMPTS`
- `nextRetryAt(attemptCount, nowMs)`: `(5min shl attemptCount).coerceAtMost(24h) + nowMs`

`CancellationException` is never passed to this classifier; callers rethrow it before reaching the catch block.

### RecommendationDiscoveryPlanner

`nextPageToProbe(progressRecords: List<RecommendationDiscoveryProgress>, nowMs: Long): Int?` replaces the old `nextPageToProbe(evaluatedPages: Set<Int>): Int?`.

Decision logic on frontier record (max page):
1. Empty records â†’ null (page 1 not yet evaluated)
2. Frontier retryable error + `isRetryable` + due â†’ return frontier (retry)
3. Frontier retryable error + NOT due â†’ null (wait for backoff)
4. Frontier retryable error + max attempts â†’ advance frontier+1
5. Frontier permanent error, success, empty, filtered â†’ advance frontier+1
6. Frontier at `MAX_DISCOVERY_PAGE_PER_SOURCE_QUERY` â†’ null (cap reached)

### discoverAdditionalPage

- Parameter changed: `evaluatedPages: Set<Int>` â†’ `progressRecords: List<RecommendationDiscoveryProgress>`
- `withTimeoutOrNull(ADDITIONAL_PAGE_TIMEOUT_MS)` (20 seconds) wraps the source search call. Timeout â†’ retryable error recorded with `nextRetryAt`.
- On any exception (except `CancellationException`, which is rethrown): `RecommendationRetryClassifier.classify(e)` determines `failureKind`. If retryable, `nextRetryAt` is computed.
- `recordProgress()` now includes `attemptCount + 1`, `nextRetryAt`, `failureKind` on error; unchanged on success.
- `baseAttemptCount` is read from the existing progress record for that page (or 0 if first attempt).

---

## Deliverable C â€” Shared RecommendationSourceSelector

New pure object at `app/src/main/java/exh/recs/RecommendationSourceSelector.kt`.

```kotlin
internal object RecommendationSourceSelector {
    fun select(
        sources: List<CatalogueSource>,
        languages: Set<String>,
        storedOrder: List<Long>,
        effectiveDisabledIds: Set<Long>,
        maxSources: Int = 0,
    ): List<CatalogueSource>
}
```

Wraps `RecommendationSourceFilter.filterForRecommendations()` + `RecommendationSourceOrdering.apply()` then optionally `take(maxSources)`.

**For You**: replaces the three-step inline call with `RecommendationSourceSelector.select(sources, recommendationLanguages, storedOrder, effectiveDisabledIds)` â€” same behavior, no functional change.

**Group flow** (`GroupSeededRecommendationsScreenModel`): replaced `RecommendationSourceFilter.filterForRecommendations(..., emptySet()).take(MAX_SOURCES)` with `RecommendationSourceSelector.select(sources, recommendationLanguages, storedOrder, effectiveDisabledIds, MAX_SOURCES)`. The group flow now:
- Reads `sourcePreferences.recommendationLanguages()` (was always `emptySet()` = all English)
- Reads `storedOrder` from `RecommendationSourcePreferenceStore` (was always empty = no priority)
- Computes `effectiveDisabledIds` as disabled + disliked source IDs (was never computed)

---

## Deliverable D â€” Shared RecommendationCandidateVisibilityPolicy

New file `app/src/main/java/exh/recs/RecommendationCandidateVisibilityPolicy.kt`.

### CandidateVisibility enum

```kotlin
enum class CandidateVisibility {
    VISIBLE,
    HIDDEN_FAVORITE,
    HIDDEN_RATED,
    HIDDEN_SEEN,
    HIDDEN_KNOWN,
    HIDDEN_MIN_CHAPTERS,
    HIDDEN_SEED_MEMBER,
}
```

### RecommendationCandidateVisibilityPolicy.evaluate()

```kotlin
fun evaluate(
    manga: Manga,
    tasteByKey: Map<MangaTasteKey, MangaTaste>,
    visibility: RatedMangaVisibility,
    seenKeys: Set<SeenMangaKey>,
    knownIds: Set<Long>,
    minChapterCount: Int = 0,
    chapterCounts: Map<Long, Long> = emptyMap(),
    seedMemberKeys: Set<Pair<Long, String>> = emptySet(),
): CandidateVisibility
```

Priority order (highest wins):
1. `HIDDEN_SEED_MEMBER` â€” source+url pair in `seedMemberKeys` (group flow only)
2. `HIDDEN_FAVORITE` â€” `manga.favorite`
3. `HIDDEN_RATED` â€” taste entry found; hides based on `RatedMangaVisibility` (disliked-only / all-rated / none)
4. `HIDDEN_SEEN` â€” `SeenMangaKey(source, url)` in `seenKeys`
5. `HIDDEN_KNOWN` â€” `manga.id` in `knownIds` (only checked when `knownIds.isNotEmpty()`)
6. `HIDDEN_MIN_CHAPTERS` â€” `chapterCounts[id]` < `minChapterCount` (skipped when count unknown â†’ fail open)
7. `VISIBLE`

### For You adoption

All three For You candidate-filtering passes (cached path, live page-1 path, live extra-page path) now call `RecommendationCandidateVisibilityPolicy.evaluate()`. The old chained `filterNot` chains are replaced with a single evaluate call per candidate.

`knownIds` for extra-page candidates: computed after the additional-page discovery call from the localized candidates, then unioned with page-1 `knownIds` â†’ `allKnownIds` for the final merge.

### Group flow adoption

Three-phase structure in `buildRecommendations()`:

- **Phase 1** â€” candidate collection loop: per-source per-source-plan loop, fast check (`!blocked && score > 0.0`), collects `ScoredCandidate` until `TARGET_RESULTS * 2`. No DB calls in this phase.
- **Phase 2** â€” batched DB lookups: `getChapterCounts.await(allIds)`, `getKnownMangaIds.await(allIds)` after the loop completes. Single call each.
- **Phase 3** â€” visibility policy: `RecommendationCandidateVisibilityPolicy.evaluate()` per candidate, filter `== VISIBLE`, sort by score descending, take `TARGET_RESULTS`.

New injected dependencies in `GroupSeededRecommendationsScreenModel`: `sourcePreferences`, `getDisabledSources`, `getMangaTaste`, `getKnownMangaIds`, `getChapterCounts`.

---

## Files Changed

| File | Change |
|------|--------|
| `data/.../migrations/58.sqm` | NEW â€” migration 58: three ALTER TABLE columns |
| `data/.../recommendation_discovery_progress.sq` | Updated CREATE TABLE + upsert for new columns |
| `domain/.../taste/model/RecommendationDiscoveryProgress.kt` | Added 3 fields + 2 constants |
| `data/.../taste/RecommendationDiscoveryProgressRepositoryImpl.kt` | upsert + mapper extended |
| `app/.../exh/recs/memory/RecommendationRetryClassifier.kt` | NEW â€” pure retry classifier |
| `app/.../exh/recs/memory/RecommendationDiscoveryPlanner.kt` | New `nextPageToProbe(List, Long)` overload |
| `app/.../exh/recs/memory/RecommendationDiscoveryProgressStore.kt` | `progressRecords()` method + extended `recordProgress()` |
| `app/.../exh/recs/RecommendationSourceSelector.kt` | NEW â€” shared source selector |
| `app/.../exh/recs/RecommendationCandidateVisibilityPolicy.kt` | NEW â€” shared visibility policy |
| `app/.../exh/recs/BrowsePersonalRecommendationsScreenModel.kt` | Deliverables A+B+C+D applied |
| `app/.../exh/recs/group/GroupSeededRecommendationsScreenModel.kt` | Deliverables C+D applied |
| `app/.../exh/recs/KmkRecsReleaseNotes.kt` | VERSION_CODE=740, VERSION_NAME=v0.7.40, new changelog entry |

## Test Files Changed

| File | Change |
|------|--------|
| `app/src/test/.../KmkMigrationTest.kt` | Range 46..58, 13 files, migration 58 column assertions |
| `app/src/test/.../RecommendationDiscoveryPlannerTest.kt` | Rewritten for new API; added retry/backoff/due/max-attempts tests |
| `app/src/test/.../RecommendationSourceSelectorTest.kt` | NEW â€” 11 tests |
| `app/src/test/.../RecommendationCandidateVisibilityPolicyTest.kt` | NEW â€” 13 tests |
| `app/src/test/.../GroupRecommendationSourcePolicyTest.kt` | NEW â€” 6 tests |

