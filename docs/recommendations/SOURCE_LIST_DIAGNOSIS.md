# Recommendation Source List Diagnosis

Date: 2026-06-14

Status: diagnostic note only. Do not treat this as an approved implementation plan.

## User-Visible Issue

The user reported that Browse > For You shows fewer source rows than Recommendation Settings > Source Priority.

Examples reported in For You:

- Asura Scans
- Manga Demon
- Manga Fire
- Manga Here
- Manwa Top / ManhwaTop
- QI Scans
- Vortex Scans

Examples reported in Source Priority include additional sources such as:

- MangaFire
- ThunderScans
- MangaHere
- ManhwaPlus
- QIScans
- Drake/DriftScans
- MangaDemon
- Asmodius
- ManhwaFast
- KyanScans
- VortexScans
- AsuraScans

The user also asked about the missing Local Source / Top Picks recommendation behavior.

## What The Code Currently Does

### Settings Source Priority List

`RecommendationsSettingsScreenModel` loads:

```text
sourceManager.getVisibleCatalogueSources()
-> RecommendationSourceFilter.filterForRecommendations(...)
-> RecommendationSourceOrdering.applyAll(...)
```

This list is the configurable source pool for the selected recommendation languages. It includes sources even if they are disabled, because settings need to let the user re-enable them.

Relevant file:

- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt`

### For You Source Pool

`BrowsePersonalRecommendationsScreenModel` loads:

```text
sourceManager.getVisibleCatalogueSources()
-> RecommendationSourceFilter.filterForRecommendations(...)
-> RecommendationSourceOrdering.apply(... disabledSourceIds ...)
-> take(MAX_SOURCES = 20)
-> search each selected source
```

Relevant file:

- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt`

This means Source Priority can show a source that For You does not display because:

- the source is disabled,
- the source is outside the selected top 20,
- the source returned zero recommendations,
- all results were filtered out as favorites/rated/blocked,
- all results were removed by cross-source dedupe,
- the source search errored or loaded slowly,
- the source is Local Source and was excluded before ordering.

### Empty Rows Are Hidden

For You deliberately hides completed source rows with zero visible results.

Relevant file:

- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsTab.kt`

This is why settings can show more sources than For You.

### Visual Row Order — Fixed In v0.4.0

Prior to v0.4.0, For You stored rows using:

```kotlin
newItems.toSortedMap(compareBy { s -> s.name })
```

So rows displayed alphabetically. This was a bug.

As of v0.4.0, the screen model stores `sourceOrder: PersistentList<CatalogueSource>` in `State` and the UI iterates that list to render rows in source priority order.

### Local Source Is Explicitly Excluded

`RecommendationSourceFilter` excludes Local Source using source id `0L`.

Relevant files:

- `app/src/main/java/exh/recs/RecommendationSourceFilter.kt`
- `source-local/src/androidMain/kotlin/tachiyomi/source/local/LocalSource.kt`

The helper has an `includeLocal` parameter, but current For You and settings calls use the default:

```kotlin
includeLocal = false
```

So Local Source is currently not part of For You or Source Priority after the recommendation language filter pass.

### Manga-Detail Cross-Extension Recommendations Are Separate

`CrossExtensionGenreSearchSource` exists under the manga detail Recommendations system. It creates one recommendation provider per visible catalogue source when cross-extension search is enabled.

Relevant files:

- `app/src/main/java/exh/recs/sources/RecommendationPagingSource.kt`
- `app/src/main/java/exh/recs/sources/CrossExtensionGenreSearchSource.kt`

This is separate from Browse > For You. For You does not currently use `CrossExtensionGenreSearchSource`. Browse > For You has a synthetic Top Picks row derived from already-fetched source rows, not from the manga-detail cross-extension recommendation provider.

## Current Best Diagnosis

The observed source mismatch is probably a combination of expected filtering plus one real ordering/display bug:

1. Source Priority shows the configured source pool for the selected languages.
2. For You only shows sources that are selected, searched, and still have visible results after scoring/filtering/dedupe.
3. Local Source is missing because the code intentionally excludes it.
4. The remembered cross-extension provider behavior exists only on manga-detail recommendations, not For You.
5. The alphabetical row ordering bug has been fixed in v0.4.0.
6. A compiled row was added in v0.4.0 and renamed to Top Picks in v0.4.1. It is synthesized from per-source results and appears first.

## Planning Implications

Remaining open decisions:

- product decision: whether Local Source should be included and what "Local Source recommendations" should mean,
- optional diagnostics: show source status/counts so the user can tell whether a source was skipped, empty, disabled, filtered, or deduped away,
- Top Picks header click drill-down (currently no-op).
