# KMK-Recs v0.7.37 Group-Seeded Recommendations Loading Fix Plan

Date: 2026-07-08

Status: implementation plan. Do not treat this as implemented until a matching implementation report, tests, and APK/build output exist.

## Problem

After KMK-Recs v0.7.36, opening "Recommendations from this" from Loved/Liked Manga no longer crashes when selecting a result, but the group-seeded recommendation screen can remain on the loading spinner indefinitely or for an unacceptably long time.

User-observed behavior:

- Long-pressing a Loved/Liked Manga card, or using the Explore overlay, opens the group-seeded recommendations screen.
- The screen continuously loads.
- It does not show recommendations, an empty state, progress, timeout, or an actionable error.

## Current Verified Code Path

Relevant files:

- `app/src/main/java/exh/recs/group/GroupSeededRecommendationsScreenModel.kt`
- `app/src/main/java/exh/recs/group/GroupSeededRecommendationsScreen.kt`
- `app/src/main/java/exh/recs/loved/RatedMangaScreen.kt`

Current v0.7.36 group-seeded pipeline:

```text
build seed
-> get taste profile
-> build query tags
-> visible catalogue sources
-> take 8 sources
-> for each source
   -> for each query plan, max 2
      -> getFilterList()
      -> getSearchManga() with 30s timeout
      -> for up to 20 results
         -> networkToLocalManga(listOf(raw)).firstOrNull()
         -> score
-> only after all loops finish, emit Success or Empty
```

The v0.7.36 crash fix added `NetworkToLocalManga`, which is correct for safe navigation, but the screen model now localizes candidates one-by-one inside the deepest loop without a per-candidate timeout, per-source timeout, total screen timeout, early success threshold, or partial-result emission.

## Likely Root Cause

The screen is not necessarily frozen. It can be doing a very large amount of sequential work before state changes away from `Loading`.

Worst-case shape:

```text
8 sources
x 2 query plans
x 30 seconds per search
= up to 8 minutes of search wait before candidate localization cost
```

Then each plan can process up to 20 results:

```text
8 sources
x 2 query plans
x 20 candidates
= up to 320 sequential NetworkToLocalManga calls
```

If any source/search/localization path is slow or hangs, the screen keeps showing `State.Loading` because it only emits final `Success` or `Empty` after the whole nested loop finishes.

## Goals

1. The group-seeded recommendations screen must never appear to load forever.
2. It should emit partial usable results as soon as enough recommendations are found.
3. It should use strict bounded work:
   - fewer sources,
   - fewer raw candidates,
   - timeout around localization,
   - timeout around the full load.
4. It should show `Empty` or `Error` after bounded failure instead of indefinite `Loading`.
5. It should preserve the v0.7.36 crash fix: never navigate to `MangaScreen` with a non-local manga id.
6. It must preserve and verify the rated-manga exclusivity invariant:
   - one exact manga identity (`source`, `url`) can only have one active rating at a time,
   - if a manga is changed from Like to Love, Love to Dislike, Dislike to Like, etc., the newer rating overwrites the older one,
   - confirmed cross-source groups must not be allowed to visually or logically behave as though the same grouped manga is simultaneously Loved, Liked, and Disliked.

## Rated-Manga Exclusivity Invariant

The current `manga_taste` table already has one row per `manga_id` and a unique index on `(source, url)`, and `SetMangaTaste` writes through `upsertMangaTaste`. This means an exact local manga entry is intended to have one rating row, not multiple rows.

However, cross-source groups need extra care. A confirmed group can contain multiple entries from different extensions. If one version is Loved and another linked version is later Disliked, the UI must not make the grouped manga appear in both Loved and Disliked as if those are independent truths.

Implementation must verify the current behavior and fix it if needed:

1. Exact identity overwrite:
   - rating the same `(source, url)` as Love, then Like, then Dislike should leave exactly one taste row with the latest rating.
2. Manga ID/source-url consistency:
   - `manga_taste.upsert` currently conflicts on `manga_id`; because `(source, url)` also has a unique index, verify that rating the same source/url through a different local manga id cannot create a constraint failure or duplicate display state.
3. Cross-source group display exclusivity:
   - if grouped versions have conflicting ratings, the rated manga collection screens should resolve the group consistently rather than showing the same grouped manga across multiple rating tabs.
   - Preferred policy: latest `updated_at` rating within the confirmed group wins for grouped display, unless a stronger existing project rule already exists.
4. Cross-extension rating actions:
   - "Love other versions", "Like other versions", and "Dislike other versions" should write the selected rating to all selected versions, overwriting previous ratings for those identities.
5. Direct single-manga rating:
   - if a manga belongs to a confirmed cross-source link group, evaluate whether direct rating should update only that exact entry or the whole group.
   - If whole-group update is too broad for this version, document the limitation and ensure grouped display still does not show the same group as multiple rating categories.

This invariant is especially important now that Loved/Liked/Disliked share one rated-manga collection UI.

## Non-Goals

- Do not remove `NetworkToLocalManga`; it is needed for safe navigation.
- Do not add DB caching in this pass.
- Do not redesign the rated manga collection screen.
- Do not change Loved/Liked/Disliked rating semantics.
- Do not launch a huge For You-style full source crawl for one manga seed.

## Required Fixes

### 1. Add Bounded Runtime Constants

In `GroupSeededRecommendationsScreenModel`, replace the current broad values with tighter, group-seeded-specific caps.

Suggested defaults:

```kotlin
private const val MAX_SOURCES = 5
private const val RAW_CAP_PER_SOURCE = 8
private const val TARGET_RESULTS = 20
private const val SEARCH_TIMEOUT_MS = 12_000L
private const val LOCALIZE_TIMEOUT_MS = 5_000L
private const val TOTAL_LOAD_TIMEOUT_MS = 45_000L
```

Rationale:

- Group-seeded recommendations are a quick drill-down from one manga, not a full For You refresh.
- The user expects a response quickly.
- 20 good results are enough for this screen.
- If more results are desired later, add explicit "Load more" rather than blocking first paint.

Exact values can be adjusted if existing code style has nearby constants, but the implementation must be bounded.

### 2. Add Total Load Timeout

Wrap the recommendation generation portion in `withTimeoutOrNull(TOTAL_LOAD_TIMEOUT_MS)`.

Behavior:

- If timeout occurs and `results` has at least one item, emit `State.Success(results.sortedByDescending { it.score })`.
- If timeout occurs and `results` is empty, emit `State.Empty` or a new timeout-specific error/empty message.
- Do not leave state as `Loading`.

Prefer a friendly empty-state message over a raw exception unless a true unexpected exception occurs.

### 3. Add Timeout Around `NetworkToLocalManga`

Current code:

```kotlin
val local = withContext(Dispatchers.IO) {
    networkToLocalManga(listOf(raw)).firstOrNull()
} ?: continue
```

Required behavior:

```kotlin
val local = withTimeoutOrNull(LOCALIZE_TIMEOUT_MS) {
    withContext(Dispatchers.IO) {
        networkToLocalManga(listOf(raw)).firstOrNull()
    }
} ?: continue
```

This prevents one bad candidate from blocking the whole screen.

### 4. Emit Partial Results Or Stop Early

After adding each successful recommendation:

- If `results.size >= TARGET_RESULTS`, stop processing additional sources/plans/candidates.
- Emit `State.Success(...)` once target is reached.

Implementation options:

- Use labeled loops and `break@outer`.
- Extract recommendation generation into a suspend helper that returns `List<PersonalRecommendation>`.

Prefer clarity over cleverness.

### 5. Dedupe Correctly Across Sources And Plans

Current `seenUrls` only dedupes by URL:

```kotlin
val seenUrls = mutableSetOf<String>()
if (smanga.url in seenUrls) continue
seenUrls += smanga.url
```

This can incorrectly merge unrelated sources that happen to use the same path, and it may fail to dedupe local manga correctly after localization.

Use source-aware keys:

```kotlin
val seenNetworkKeys = mutableSetOf<Pair<Long, String>>()
val seenLocalIds = mutableSetOf<Long>()
```

Before localization:

```kotlin
val key = source.id to smanga.url
if (key in seed.memberKeys) continue
if (!seenNetworkKeys.add(key)) continue
```

After localization:

```kotlin
if (!seenLocalIds.add(local.id)) continue
```

### 6. Use A Safer State Update Pattern

Ensure `load()` always ends in one of:

- `State.Success`
- `State.Empty`
- `State.Error`

Never allow paths where `State.Loading` remains after the coroutine completes or silently swallows all failures without state transition.

Current inner catch:

```kotlin
} catch (_: Exception) {
    continue
}
```

This is fine for per-source/per-plan errors, but the overall function should count failures and still emit a final state.

Optional diagnostic fields may be added internally:

- attempted source count,
- timed-out source count,
- localization skip count,
- total candidates skipped.

Do not expose noisy diagnostic text in the UI unless needed.

### 7. Improve UI Feedback

Minimum:

- Keep existing loading spinner.
- Ensure it changes to Empty/Error after bounded failure.

Better:

- Add a small loading subtitle such as "Finding similar manga..." if there is already a suitable KMR pattern.
- If no results are found due to timeout/no usable candidates, show the existing `group_seeded_recs_empty` string or add a clearer KMR string:

```xml
group_seeded_recs_empty_after_timeout
```

Avoid hardcoded English in Compose.

## Suggested Implementation Shape

In `GroupSeededRecommendationsScreenModel`:

1. Keep `load()` small:

```kotlin
private suspend fun load() {
    mutableState.update { State.Loading }
    runCatching {
        val results = withTimeoutOrNull(TOTAL_LOAD_TIMEOUT_MS) {
            buildRecommendations()
        } ?: emptyList()

        mutableState.update {
            if (results.isEmpty()) State.Empty else State.Success(results.sortedByDescending { it.score })
        }
    }.onFailure { e ->
        mutableState.update { State.Error(e) }
    }
}
```

2. Extract current nested loop into `buildRecommendations()`.
3. Use labeled break or return when `TARGET_RESULTS` is reached.
4. Use `withTimeoutOrNull` around search and localization.
5. Keep `CancellationException` behavior correct. Do not accidentally swallow real user/navigation cancellation if existing project conventions rethrow cancellation.

## Files To Change

Required:

- `app/src/main/java/exh/recs/group/GroupSeededRecommendationsScreenModel.kt`

Likely:

- `app/src/main/java/exh/recs/group/GroupSeededRecommendationsScreen.kt` if adding better loading/empty copy.
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` if adding strings.
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/README.md`
- `docs/recommendations/KMK_RECS_V0_7_37_GROUP_SEEDED_RECOMMENDATIONS_LOADING_FIX_IMPLEMENTATION.md`
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` if user-visible release notes are updated.
- `RECOMMENDATION_VERSIONING.md` if an APK is handed off.

## Testing Requirements

### Automated Tests

Add focused unit tests if feasible by injecting fakes into `GroupSeededRecommendationsScreenModel` or extracting a pure helper.

Recommended tests:

1. Stops after target result count.
2. Localization timeout skips candidate and continues.
3. Search timeout skips plan/source and continues.
4. Empty emitted when all candidates fail localization.
5. Source-aware dedupe does not collapse same URL across different sources.
6. Seed member keys are still filtered.
7. Exact manga rating overwrite keeps only the latest rating for a `(source, url)` identity.
8. Grouped rated manga with conflicting member ratings does not appear as the same grouped entry in multiple rating tabs.

If direct screen model tests are too hard, extract the loop policy into a helper and test that.

### Manual QA

1. Open Loved Manga.
2. Use Explore overlay on a loved/liked manga.
3. Confirm screen transitions away from loading within the bounded time.
4. Confirm results appear when available.
5. Confirm empty state appears when no recommendations are found.
6. Confirm tapping a result opens manga detail without crash.
7. Confirm backing out while loading does not crash.

### Build Verification

Run:

```text
./gradlew spotlessCheck
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

If full tests cannot be run, document exactly which tests were run and why.

## Acceptance Criteria

This is complete only when:

1. Group-seeded recommendations do not remain indefinitely in `Loading`.
2. Total work is bounded by a screen-level timeout.
3. Candidate localization is bounded by a per-candidate timeout.
4. The screen stops early once enough results are found.
5. All emitted manga are still local DB manga safe for `MangaScreen`.
6. Empty/error state appears when no usable recommendations can be produced.
7. Source-aware dedupe is used.
8. Documentation and implementation report are updated.
9. Tests/build are run and recorded.

## Expected Version

```text
KMK-Recs v0.7.37
```

Expected debug APK name if built:

```text
Komikku-v1.13.6-kmk.7.37-debug.apk
```


