# Top Picks Filtering And Exception Handling Implementation

Date: 2026-06-14

Version: KMK-Recs v0.4.1

APK: `Komikku-v1.13.6-kmk.4.1-debug.apk`

## User-Approved Scope

- Rename "Combined Picks" row to "Top Picks" (not just a string rename â€” reinforce that it represents the user's best preference-ranked picks).
- Add conservative cross-source duplicate merging using exact title + exact author/artist.
- Add local known-manga filter hiding rated, in-library, read, partially-read, or historically-read manga.
- Add "Hide known manga" setting (default enabled).
- Add exception handling: new quality filters fail open.
- No AniList/tracker lookup per result.
- No chapter list fetching for filtering.

## Pre-Implementation Verification

| Check | Finding |
|---|---|
| "Combined Picks" strings in i18n-kmk/strings.xml | âœ… Found at lines 273-275: `rec_combined_picks_title`, `rec_combined_picks_subtitle`, `rec_combined_picks_matched` |
| Row title from `rec_combined_picks_title` | âœ… Confirmed in `BrowsePersonalRecommendationsTab.kt` |
| CombinedPicksAccumulator keys by `(manga.source, manga.url)` | âœ… Line 37: `"${rec.manga.source}:${rec.manga.url}"` |
| Ranking uses only `bestScore + occurrence bonus + boosted bonus` | âœ… Lines 63-67 â€” no matched-group signal in primary score |
| Screen model filters favorites/rated before scoring | âœ… Line 301: `.filterNot { it.favorite || shouldHideForYou(it, tasteByKey, visibility) }` |
| PersonalRecommendationScorer uses explicit tags, learned weights, blocked groups, source affinity | âœ… All confirmed in scorer |
| Existing helper for known/read manga IDs | âŒ None found. `GetReadMangaNotInLibrary` exists but returns all read manga, not batched by candidate IDs |
| SQL query for read/started chapters by manga ID | âŒ None found. Added `getKnownRecommendationMangaIds` to `mangas.sq` |
| Per-source failures already isolated | âœ… Each source in its own `async` block with try/catch |
| New filter query failure could blank For You | âš ï¸ Would if not guarded â€” added `runCatching { }.getOrElse { emptySet() }` to fail open |
| Newer markdown files changing scope | None found beyond `NEXT_WORK.md` which references the same plan |

## Files Changed

- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` â€” removed `rec_combined_picks_{title,subtitle,matched}`, added `rec_top_picks_{title,subtitle,matched}`, `rec_hide_known_manga`, `rec_hide_known_manga_summary`
- `app/src/main/java/exh/recs/CombinedPicksAccumulator.kt` â€” added `workKeyToPrimaryKey` map, `conservativeWorkKey()` helper, `var manga` in Bucket, updated `add()` with work-key redirect, updated `clear()`, added `matchedGroups.size` tiebreaker in `rank()`
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsTab.kt` â€” string refs changed from `rec_combined_picks_*` to `rec_top_picks_*`, LazyColumn key changed from `"combined_picks"` to `"top_picks"`
- `data/src/main/sqldelight/tachiyomi/data/mangas.sq` â€” added `getKnownRecommendationMangaIds` query (KMK block)
- `domain/src/main/java/tachiyomi/domain/manga/repository/MangaRepository.kt` â€” added `getKnownRecommendationMangaIds(mangaIds: Collection<Long>): Set<Long>` (KMK block)
- `data/src/main/java/tachiyomi/data/manga/MangaRepositoryImpl.kt` â€” implemented `getKnownRecommendationMangaIds` with empty-collection guard (KMK block)
- `domain/src/main/java/tachiyomi/domain/taste/interactor/GetKnownRecommendationMangaIds.kt` â€” new interactor (KMK)
- `app/src/main/java/eu/kanade/domain/KMKDomainModule.kt` â€” registered `GetKnownRecommendationMangaIds`
- `app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt` â€” added `recommendationHideKnownManga()` (default true)
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt` â€” injected `GetKnownRecommendationMangaIds`, added `hideKnownManga` param in `searchSource()`, applied fail-open known filter after `networkToLocalManga()`
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt` â€” added `hideKnownMangaPref`, `hideKnownManga` state, `setHideKnownManga()` action
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt` â€” added `HideKnownMangaRow` composable, added toggle item in settings LazyColumn

## Behavior Changed

### Top Picks Rename

The row title now shows "Top Picks" instead of "Combined Picks." The subtitle is "Your best matches across sources" (was "Ranked across selected sources"). The matched-groups subtitle prefix is unchanged ("Matched: ..."). Internal class and variable names retain `CombinedPicks` to avoid unnecessary churn.

### Conservative Duplicate Merging

Two candidates from different (source, url) pairs are now merged in Top Picks when:
- Normalized title matches exactly, AND
- Normalized author matches exactly and is non-blank, OR normalized artist matches exactly and is non-blank.

Normalization converts to lowercase, replaces non-alphanumeric characters with spaces, trims, and collapses whitespace. This means punctuation differences and casing differences do not prevent merging.

Cases that do NOT merge:
- Same title, both author and artist blank.
- Same title, different author values.
- Similar but not exactly-equal normalized titles.
- Malformed metadata (protected by `runCatching`).

When merged: the higher-score entry becomes the representative (or the metadata-richer one on tie). Matched groups and source IDs from all contributing entries are merged.

### Known-Manga Filter

When "Hide known manga" is enabled (default), candidates are filtered after `networkToLocalManga()` but before enrichment and scoring. A candidate is hidden if its local manga ID matches any of:
- `mangas.favorite = 1`
- Has at least one chapter with `chapters.read = 1 OR chapters.last_page_read != 0`
- Has a history row with `history.last_read > 0`
- Has a `manga_taste` row (any rating)

The filter is applied per-source independently. If the known-manga DB query fails, all candidates are kept and the failure is logged at WARN. If the collection is empty, the query is skipped.

### Ranking Tiebreakers

Added `matchedGroups.size` as a tiebreaker between candidates with the same combined score and occurrence count. This helps prefer candidates with richer preference-match evidence when personal scores are equal.

## Tests Run

- `CombinedPicksAccumulatorTest` â€” 24 tests, BUILD SUCCESSFUL
- Full `:app:testDebugUnitTest` â€” BUILD SUCCESSFUL

## Known Limitations

- Known-manga filter only catches manga Komikku still has records for. Cross-device reads or deleted history entries will not be detected.
- The `getKnownRecommendationMangaIds` SQL query uses subselects rather than JOINs for `chapters` and `history`. This is correct but may be less efficient for very large collections. In practice, candidate counts are â‰¤ 60 per source.
- AniList/tracker known-list filtering is not implemented (deferred per plan).
- Minimum chapter count filter is not implemented (deferred per plan â€” would require network calls).
- Top Picks header click remains no-op. Drill-down not implemented in this pass.

## Deviations From Plan

- Plan showed the SQL query as a single LEFT JOIN. Implemented as three separate subselects to avoid row explosion from multiple LEFT JOINs (chapters Ã— history can produce many rows per manga). The semantics are equivalent.
- `matchedGroups.size` tiebreaker was added to `rank()`. Plan did not explicitly require it, but the plan said "more matched preferred/learned groups" should rank higher â€” this is the implementation of that intent.
- No separate dedupe logic added for the Top Picks row vs. source rows. The plan noted combined row should not be run through `dedupedItems()`. This is preserved from v0.4.0.

## Follow-Up Recommendations

- Consider adding a "Why hidden" debug mode that shows per-candidate hide reasons.
- AniList known-list cache (Phase 6 from plan) â€” cache locally on tracker sync, use as weak "probably known" signal.
- Chapter count filter (Phase 5) â€” implement using already-locally-known chapter counts only, no network.
- Top Picks header click drill-down (from v0.4.0 NEXT_WORK).

