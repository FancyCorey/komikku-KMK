# KMK-Recs v0.7.35 â€” Rated Manga Entry Points & Group-Seeded Recommendations â€” Implementation Report

Date: 2026-07-08

## Summary

Three features shipped in v0.7.35:

1. **IO threading fix** â€” `SourceRecommendationFitProbe` now runs all source calls on an injectable IO dispatcher. The `NetworkOnMainThreadException` that was causing mass-fail in recommendation-quality probe rows is resolved.
2. **Liked / Disliked manga views** â€” For You action bar gains thumbs-up (Liked) and thumbs-down (Disliked) buttons, each opening a `RatedMangaScreen` that reuses `LovedMangaScreenModel` with `filterRating`.
3. **Group-seeded recommendations** â€” Long-pressing a Loved Manga card opens "Recommendations from this" â€” a simplified For You search seeded by the manga's genre tags and its cross-source link group.

APK: `Komikku-v1.13.6-kmk.7.35-debug.apk`

---

## 1. IO Dispatcher Fix (`SourceRecommendationFitProbe`)

**Root cause:** `getFilterList()`, `getSearchManga()`, and `getMangaDetails()` were called directly without switching to a background dispatcher. On Android, any extension that makes a network call in `getFilterList()` (uncommon but possible) or the search/details methods would throw `NetworkOnMainThreadException`, causing the probe plan to error-out and the source's quality score to be penalized unfairly.

**Fix:** Added `private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO` constructor parameter. All three calls are wrapped with `withContext(ioDispatcher)`. The parameter is injectable for testability.

**Exception classification:** A `NetworkOnMainThreadException` that somehow reaches the outer catch block (e.g., thrown synchronously) is classified as `"internal-threading-error"` in the reasons list. It is visible in diagnostics but does not contribute to quality scoring.

**Files changed:**
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationFitProbe.kt` â€” injectable dispatcher, 3 `withContext` wrappers, classification logic

**Tests:**
- `SourceRecommendationFitProbeTest`: 2 new tests (17 total)
  - `probe runs source calls on injected dispatcher not caller dispatcher` â€” verifies `getSearchManga` is called when running with `UnconfinedTestDispatcher`
  - `probe classifies NetworkOnMainThreadException as internal-threading-error in reasons` â€” verifies the stub class name triggers the classifier
- 3 existing v0.7.13 enrichment tests updated to pass `UnconfinedTestDispatcher` â€” previously used `Dispatchers.IO` which raced against virtual time in `runTest`, causing false timeouts

---

## 2. Generalized Rating Filter

**Files changed:**
- `app/src/main/java/exh/recs/loved/LovedMangaSourceFilter.kt` â€” added `filterRatedTastesByInstalledSources(tastes, installedSourceIds, allowedRatings: Set<Int>)`; `filterLovedTastesByInstalledSources` now delegates to it with `allowedRatings = setOf(MangaRating.LOVE.value)`
- `app/src/main/java/exh/recs/loved/LovedMangaScreenModel.kt` â€” added `filterRating: MangaRating = MangaRating.LOVE` parameter; `load()` uses `filterRatedTastesByInstalledSources` with `setOf(filterRating.value)`

**Backward compatibility:** The `LOVE` default means `LovedMangaScreen` (which instantiates `LovedMangaScreenModel()` without arguments) is unaffected.

**Tests:**
- `LovedMangaSourceFilterTest`: 5 new tests (16 total)
  - `filterRatedTastesByInstalledSources keeps LIKE entries with matching rating`
  - `filterRatedTastesByInstalledSources keeps DISLIKE entries with matching rating`
  - `filterRatedTastesByInstalledSources excludes entries with different rating`
  - `filterRatedTastesByInstalledSources excludes entries from uninstalled sources`
  - `filterLovedTastesByInstalledSources still delegates correctly after refactor`

---

## 3. RatedMangaScreen (Liked / Disliked entry points)

**New file:** `app/src/main/java/exh/recs/loved/RatedMangaScreen.kt`

`RatedMangaScreen(ratingValue: Int)` is a `Screen()` data class. It:
- Instantiates `LovedMangaScreenModel(filterRating = MangaRating.fromValue(ratingValue) ?: LIKE)` via `rememberScreenModel(tag = "rated_$ratingValue")`
- Shows title/empty/error from `liked_manga_*` or `disliked_manga_*` i18n keys based on the rating
- Renders the same `LazyVerticalGrid` as `LovedMangaScreen` (no export, no link management, no sort chips â€” simplest possible grid)
- Uses `fallbackCover()` via a local `ratedFallbackCover()` copy

**Action bar changes** (`BrowsePersonalRecommendationsTab.kt`):
- `Icons.Outlined.ThumbUp` â†’ `RatedMangaScreen(MangaRating.LIKE.value)`
- `Icons.Outlined.ThumbDown` â†’ `RatedMangaScreen(MangaRating.DISLIKE.value)`

**New i18n strings** (`i18n-kmk/.../strings.xml`):
- `liked_manga_title`, `liked_manga_empty`, `liked_manga_error`
- `disliked_manga_title`, `disliked_manga_empty`, `disliked_manga_error`
- `rated_manga_recommendations_from_this`
- `group_seeded_recs_title` (format with `%1$s`), `group_seeded_recs_empty`, `group_seeded_recs_error`

---

## 4. Group-Seeded Recommendations

**New files:**

### `exh/recs/group/GroupRecommendationSeed.kt`
Pure data class: `primaryTitle, titles, tags, sourceIds, memberKeys: Set<Pair<Long, String>>, groupId: String?`

### `exh/recs/group/GroupRecommendationSeedBuilder.kt`
Pure builder. Steps:
1. Look up `CrossSourceMangaLink` for `(sourceId, url)` via `GetCrossSourceMangaLinks.awaitBySourceUrl`
2. If a group exists, fetch all members via `awaitByGroupId`
3. Resolve `Manga` from local DB for each member and collect genres
4. Build `seedTags`: top-10 tags by frequency across all member genres
5. Build `memberKeys`: all `(source, url)` pairs (to filter from results)

Fails open at every step (runCatching throughout).

### `exh/recs/group/GroupSeededRecommendationsScreenModel.kt`
Simplified pipeline (no cache, no source-preference reading, no boosted sources, no strategy persistence):
1. Build seed via `GroupRecommendationSeedBuilder`
2. Boost `TasteProfile.learnedTagWeights` for seed tags: `current + 0.3`, capped at `0.9`
3. If seed tags are empty, fall back to top-5 profile tags (> 0.3 threshold)
4. Fetch visible English catalogue sources, take up to 8
5. For each source + plan: `withContext(Dispatchers.IO)` for `getFilterList` and `getSearchManga`; timeout 30s
6. Skip results whose `(source.id, smanga.url)` is in `seed.memberKeys`
7. Score remaining with `PersonalRecommendationScorer`; emit `PersonalRecommendation` for score > 0 and not blocked
8. Sort by score descending

### `exh/recs/group/GroupSeededRecommendationsScreen.kt`
Voyager data class screen: `(sourceId: Long, url: String, primaryTitle: String)`. Renders Loading/Empty/Error/Success states. Grid with `MangaScreen` navigate on tap or long-tap.

### Entry point in `LovedMangaScreen.kt`
`onLongClick` on each `MangaItem` navigates to `GroupSeededRecommendationsScreen(sourceId, url, primaryTitle)` instead of the previous `MangaScreen(...)`.

---

## Deferred / Not Implemented in v0.7.35

- DB caching for group-seeded results (documented in implementation notes above â€” "no caching" is the documented choice for v0.7.35)
- Installed-source filter for group-seeded pipeline (currently uses English sources; does not consult source priority or disabled-source preference)
- Sort/filter controls on `RatedMangaScreen` (same design decision as `LovedMangaScreen` v0.7.0 â€” added in v0.7.14 after the initial grid was stable)
- Tests for `GroupRecommendationSeedBuilder` and `GroupSeededRecommendationsScreenModel` (network-bound; would require fake source + fake cross-source link DB; deferred to a separate test pass)

---

## Verification

- `spotlessCheck` + `spotlessApply`: passed
- `testDebugUnitTest` (full suite): BUILD SUCCESSFUL
  - `SourceRecommendationFitProbeTest`: 17/17 passed
  - `LovedMangaSourceFilterTest`: 16/16 passed (all 11 original + 5 new)
  - `LovedMangaSortTest`: 7/7 passed
  - `LovedMangaDuplicateGrouperTest`: passed
- `assembleDebug`: BUILD SUCCESSFUL
- APK: `Komikku-v1.13.6-kmk.7.35-debug.apk` (178 MB, at `private/`)

