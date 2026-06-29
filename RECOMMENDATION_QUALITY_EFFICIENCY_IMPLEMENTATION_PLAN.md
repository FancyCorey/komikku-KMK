# Recommendation Quality and Efficiency Implementation Plan

Date: 2026-06-13

Status: superseded implementation plan. This plan became KMK-Recs v0.3.0. For current behavior, read `docs/recommendations/CURRENT_STATE.md` and `RECOMMENDATION_IMPLEMENTATION_AUDIT.md` first.

## Executive Summary

The personal recommendation system is already functional:

- Manga can be rated with Love, Like, Dislike, and Clear.
- Browse has a For You tab.
- For You searches across capped installed catalogue sources.
- Recommendations are scored against explicit tag preferences, learned tag weights, blocked groups, and weak source affinity.
- The system caches recommendation rows.
- Settings allow tag preferences, rated manga visibility, and source enable/disable.
- Empty rows are hidden.
- Source row drill-down is implemented.
- Backup, restore, and sync support have already been added in the current branch.

This pass should improve recommendation quality without turning For You into a slow crawler. The core rule for every change:

> Add intelligence only when it has a cap, cache, fallback, and no chapter fetching.

The next implementation should focus on:

1. Manual recommendation source ordering.
2. Top-three preferred source boosting.
3. Capped metadata enrichment before scoring.
4. Conservative query strategy rotation.
5. Alias-aware genre filter matching without a giant source-specific alias database.
6. Lightweight source quality stats.
7. Conservative duplicate suppression, not aggressive duplicate deletion.

## Current Code Map

### For You Loading and Scoring

- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt`
  - Loads `TasteProfile`.
  - Loads tag aliases.
  - Loads disabled recommendation source ids.
  - Builds `topTags`.
  - Selects visible catalogue sources, currently capped at `MAX_SOURCES = 20`.
  - Searches each source with `GenreFilterMapper.buildSearch`.
  - Fetches only page 1 from each source.
  - Takes `MAX_RESULTS_PER_SOURCE * 3` raw candidates.
  - Converts network manga into local manga.
  - Filters favorites and hidden rated entries.
  - Scores via `PersonalRecommendationScorer.rankCandidates`.
  - Saves manga ids, scores, and row-level reasons to `recommendation_cache`.
  - Uses a 24 hour cache TTL.

- `app/src/main/java/exh/recs/PersonalRecommendationScorer.kt`
  - Hard-blocks candidates with blocked groups.
  - Adds explicit tag preference score.
  - Adds learned tag weight score.
  - Adds weak source affinity score.
  - Returns matched groups and human-readable reasons.

### UI

- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsTab.kt`
  - Shows For You rows using global search UI components.
  - Hides rows that completed with empty results.
  - Shows row-level matched tags.
  - Tapping a manga opens `MangaScreen`.
  - Tapping a source row opens `BrowseSourceScreen(source.id, ctx?.textQuery)`.

- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt`
  - Shows rated visibility.
  - Shows tag preferences.
  - Shows source enable/disable switches.
  - Does not currently support manual source ordering.

- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt`
  - Loads visible catalogue sources.
  - Loads disabled source ids.
  - Writes rated visibility.
  - Writes tag preferences.
  - Toggles recommendation sources.

### Source Filter Mapping

- `app/src/main/java/exh/recs/sources/GenreFilterMapper.kt`
  - Maps desired genres into a source `FilterList`.
  - Supports `Filter.Group` children of `TriState` and `CheckBox`.
  - Supports `Filter.Select`.
  - Supports `Filter.AutoComplete`.
  - Unmatched tags become a text query.
  - Current matching is exact after `normalizeTag`.

- `data/src/main/sqldelight/tachiyomi/migrations/46.sqm`
  - Already seeds a small number of tag aliases for Girls Love, Boys Love, and Sci-Fi.

### Existing Cache and Tables

- `data/src/main/sqldelight/tachiyomi/data/recommendation_cache.sq`
  - Stores one row per source/query/profile fingerprint.
  - Stores result manga ids as comma-separated ids.
  - Stores result scores as comma-separated doubles.
  - Stores row-level result reasons.

- `data/src/main/sqldelight/tachiyomi/data/recommendation_disabled_source.sq`
  - Stores disabled source ids.

No table currently stores recommendation source ordering, source quality stats, or source query strategy memory.

## Non-Goals

Do not fetch chapter lists for recommendations.

Reason: chapter fetching is expensive, source-specific, and not needed for the recommendation goal.

Do not build a full local manga catalogue.

Reason: extension sources are runtime APK code. There is no reliable compact global index for all installed sources.

Do not create a massive manual alias database.

Reason: source filters change, names vary, and a large alias table becomes maintenance debt. Use a small universal synonym layer plus user aliases.

Do not aggressively remove duplicate titles.

Reason: separate manga can have identical or near-identical names. Deduplication must be conservative and reversible at the presentation layer.

Do not make AniList, MAL, or MangaUpdates central to this pass.

Reason: external tracker data is incomplete for manhwa, manhua, and source-specific titles. It can be optional seed data later, but not the core architecture.

Do not implement reading-history learning in this pass.

Reason: reading history is valuable but ambiguous. Explicit taste ratings and source behavior should stabilize first.

## Design Principles

### Bounded Work

Every source search must have explicit caps:

- source count cap,
- raw result cap,
- enrichment cap,
- strategy attempt cap,
- cache TTL,
- coroutine parallelism cap.

The system should never enrich every result from every source.

### Reuse Without Over-Coupling

The single-manga cross-extension recommendation source already performs metadata enrichment. For You should reuse the same idea, but not necessarily the same class if the data flow differs.

Recommended approach:

- Extract small shared helpers if they clearly reduce duplication.
- Do not force `CrossExtensionGenreSearchSource` and `BrowsePersonalRecommendationsScreenModel` into one abstraction if that makes the flow harder to reason about.
- Prefer small utilities such as:
  - `RecommendationCandidateEnricher`
  - `RecommendationQueryPlanner`
  - `RecommendationSourceOrdering`
  - `RecommendationAliasResolver`

### Local and Explainable

Recommendation decisions should come from:

- explicit user ratings,
- explicit tag preferences,
- local source behavior,
- installed extension results,
- small local synonym mappings.

No hidden external service should be required.

### Conservative UI Changes

The existing For You UI is already usable. This pass should add only the UI needed for source ordering and maybe source quality visibility. Avoid redesigning Browse.

## Phase 1: Manual Recommendation Source Ordering

### Goal

Allow the user to prioritize recommendation sources manually, similar in spirit to Feed ordering. The first three enabled prioritized sources receive a boosted display and enrichment budget.

### Desired Behavior

- Recommendation settings show enabled catalogue sources in a reorderable list.
- The user can move preferred sources higher or lower.
- Disabled sources remain disabled and should not be searched.
- If no manual order exists, For You keeps the current default visible source order.
- For You searches up to 20 enabled sources.
- The top three enabled sources by recommendation order are boosted.
- Boosted sources show up to 20 results.
- Non-boosted sources show up to 10 results.

### Data Model

Preferred simple storage:

- Add a new preference to `SourcePreferences`:

```kotlin
fun recommendationSourceOrder() =
    preferenceStore.getString("recommendation_source_order", "")
```

Store a comma-separated list of source ids:

```text
12345,67890,11111
```

Rationale:

- Source order is a lightweight user preference, not analytical data.
- It does not need backup-critical relational behavior.
- It is easy to reset if sources are uninstalled.
- It avoids a migration if this pass can avoid one.

If backup of this preference is desired later, handle it through the existing preferences backup path, not a new taste backup table.

### Ordering Helper

Create a small helper, probably in `app/src/main/java/exh/recs/RecommendationSourceOrdering.kt`.

Responsibilities:

- Parse stored source id order safely.
- Remove ids that are no longer visible.
- Append newly visible sources not already in the order.
- Exclude disabled source ids.
- Return:
  - ordered enabled sources,
  - boosted source ids, first three enabled ordered ids.

Suggested API:

```kotlin
internal object RecommendationSourceOrdering {
    fun parse(value: String): List<Long>
    fun serialize(ids: List<Long>): String
    fun apply(
        visibleSources: List<CatalogueSource>,
        storedOrder: List<Long>,
        disabledSourceIds: Set<Long>,
    ): List<CatalogueSource>
    fun boostedSourceIds(orderedSources: List<CatalogueSource>): Set<Long>
}
```

Acceptance criteria:

- Sources missing from stored order are appended.
- Stored ids for uninstalled sources are ignored.
- Disabled sources are excluded.
- Re-enabled sources reappear in their stored position if possible, otherwise append.

### Settings UI

Modify:

- `RecommendationsSettingsScreen.kt`
- `RecommendationsSettingsScreenModel.kt`

Implementation guidance:

- Reuse existing reorderable list patterns from Feed ordering where practical:
  - `eu.kanade.presentation.browse.FeedOrderScreen`
  - `eu.kanade.presentation.browse.SourceFeedOrderScreen`
- Do not copy a large screen if a simpler inline order list is enough.
- Keep source enable/disable switches.
- Add a small section explaining by title only, not a long in-app explanation:
  - `Recommendation source priority`
- Show the top three with a small visual indicator if easy:
  - `Top source`
  - `Boosted`
  - or a small number badge.

Recommended model methods:

```kotlin
fun moveSource(sourceId: Long, toIndex: Int)
fun resetSourceOrder()
```

State should expose:

```kotlin
val orderedSources: ImmutableList<CatalogueSource>
val boostedSourceIds: ImmutableSet<Long>
```

Important:

- The source list in settings should use the same ordering helper as For You.
- Avoid one ordering path in settings and another ordering path in search.

### For You Integration

Modify `BrowsePersonalRecommendationsScreenModel.load`:

Current:

```kotlin
val sources = sourceManager.getVisibleCatalogueSources()
    .filter { it.id !in disabledSourceIds }
    .take(MAX_SOURCES)
```

Desired:

```kotlin
val orderedSources = RecommendationSourceOrdering.apply(
    visibleSources = sourceManager.getVisibleCatalogueSources(),
    storedOrder = RecommendationSourceOrdering.parse(sourcePreferences.recommendationSourceOrder().get()),
    disabledSourceIds = disabledSourceIds,
)
val sources = orderedSources.take(MAX_SOURCES)
val boostedSourceIds = RecommendationSourceOrdering.boostedSourceIds(sources)
```

Pass `isBoosted = source.id in boostedSourceIds` into `searchSource`.

Add caps:

```kotlin
private const val NORMAL_RESULTS_PER_SOURCE = 10
private const val BOOSTED_RESULTS_PER_SOURCE = 20
private const val BOOSTED_SOURCE_COUNT = 3
```

Do not simply increase all sources to 20.

## Phase 2: Capped Metadata Enrichment Before Scoring

### Goal

Improve scoring quality by fetching full manga details for a small number of promising candidates before tag scoring.

### Current Problem

Many extension search results only contain:

- title,
- URL,
- thumbnail,
- source id.

If `genre` is empty, `PersonalRecommendationScorer` has little to score against. It may return weak or flat scores.

`CrossExtensionGenreSearchSource` already enriches top candidates with `catalogueSource.getMangaDetails(smanga)`. For You should do the same kind of enrichment, but with strict caps.

### Desired Flow

Per source:

```text
get filter list
build search params
get search manga page 1
take bounded raw candidates
convert to local manga
filter obvious known/hidden entries
enrich only small candidate set with weak metadata
score enriched candidates
take display limit
save cache
update UI
```

### Enrichment Caps

Recommended constants:

```kotlin
private const val NORMAL_ENRICHMENT_LIMIT = 5
private const val BOOSTED_ENRICHMENT_LIMIT = 10
private const val RAW_CANDIDATE_MULTIPLIER = 3
```

For normal source:

- display limit: 10
- enrichment limit: 5
- raw search take: 30

For boosted source:

- display limit: 20
- enrichment limit: 10
- raw search take: 60

Important:

- Enrichment limit is smaller than or equal to half the boosted display limit. This prevents boosted sources from becoming too expensive.
- Never fetch details for candidates that were filtered out.

### Weak Metadata Check

Create helper:

```kotlin
private fun Manga.needsRecommendationEnrichment(): Boolean {
    return genre.isNullOrEmpty() || description.isNullOrBlank() || status == SManga.UNKNOWN
}
```

Use the local domain status type actually available on `Manga`. If `SManga.UNKNOWN` is not available in this file, compare against the project equivalent.

### Enrichment Helper

Create `app/src/main/java/exh/recs/RecommendationCandidateEnricher.kt`.

Suggested API:

```kotlin
internal class RecommendationCandidateEnricher(
    private val networkToLocalManga: NetworkToLocalManga,
) {
    suspend fun enrich(
        source: CatalogueSource,
        candidates: List<Manga>,
        limit: Int,
    ): List<Manga>
}
```

Implementation notes:

- Keep original order.
- Only enrich candidates that need enrichment.
- Convert `Manga` back to `SManga` carefully if source APIs require `SManga`.
- Prefer using existing conversions in the project instead of creating ad hoc field copying if available.
- If direct conversion is awkward, keep enrichment local inside `BrowsePersonalRecommendationsScreenModel` using the original `SManga` list before `toDomainManga`.
- On detail fetch failure, keep the original candidate.
- Do not fail the entire source row because one detail call failed.
- Use `withContext(coroutineDispatcher)` around detail calls.
- Keep detail calls sequential per source unless profiling proves parallelism is needed. The global search already runs multiple sources in parallel.

Important anti-pattern:

- Do not call `getMangaDetails` for every candidate from every source.
- Do not enrich cached results on cache read. Cache read should stay fast.

### Cache Behavior

The existing cache stores manga ids. If enriched details are written through `networkToLocalManga`, then later cache reads should retrieve the richer local manga.

If enrichment changes scoring materially, bump the cache key version:

```kotlin
"personal_v3:$sourceId:$queryKey"
```

Also update `profileFingerprint` version:

```kotlin
update("personal_v3")
```

Include these in fingerprint:

- top tags,
- explicit preferences,
- learned weights,
- blocked groups,
- source affinity,
- alias map,
- disabled source ids,
- recommendation source order,
- rated manga visibility,
- boosted source ids or source order version.

Do not include volatile source quality counters in the fingerprint. Quality stats should influence source order or strategy but should not invalidate all cached results every time a counter changes.

### Acceptance Criteria

- For You still loads progressively.
- Normal sources enrich at most 5 candidates.
- Boosted sources enrich at most 10 candidates.
- Detail failures do not break a row.
- No chapter fetches are added.
- Cache reads remain quick.
- Scored results improve when extensions provide genres only in details.

## Phase 3: Query Strategy Rotation

### Goal

Improve source search results without running every possible query. Each source should try one preferred strategy, with at most one fallback when results are too weak.

### Current Behavior

The search model uses a mutable `searchTags` list and reduces tag count on error. This is helpful, but it is still one family of strategy:

- top tags with filter mapping,
- fewer top tags after failures.

### Desired Behavior

Introduce a small query planner that provides ordered strategies per source.

Suggested strategy types:

```kotlin
internal enum class RecommendationQueryStrategyType {
    TOP_TAGS_FILTER,
    SINGLE_STRONGEST_TAG,
    TAG_PAIR,
    ALIAS_EXPANDED_TAGS,
    TEXT_ONLY_TOP_TAGS,
}
```

Suggested model:

```kotlin
internal data class RecommendationQueryPlan(
    val type: RecommendationQueryStrategyType,
    val tags: List<String>,
    val forceTextOnly: Boolean = false,
)
```

### Strategy Rules

For each source refresh:

1. Start with the last successful strategy for that source, if one exists.
2. Otherwise use `TOP_TAGS_FILTER`.
3. If the first strategy returns fewer than `MIN_USEFUL_RESULTS`, try exactly one fallback.
4. If a fallback succeeds, remember it as that source's last successful strategy.

Recommended constants:

```kotlin
private const val MAX_STRATEGIES_PER_SOURCE_REFRESH = 2
private const val MIN_USEFUL_RESULTS_NORMAL = 3
private const val MIN_USEFUL_RESULTS_BOOSTED = 5
```

### Strategy Persistence

Use a lightweight preference or a small SQL table.

Preferred first pass:

- Add preference:

```kotlin
fun recommendationSourceStrategies() =
    preferenceStore.getString("recommendation_source_strategies", "")
```

Store as simple text:

```text
sourceId=strategyName;sourceId=strategyName
```

If this feels too fragile, use JSON only if the project already has convenient serialization nearby. Avoid adding a new dependency.

Do not include last-success strategy in backup initially unless preferences backup naturally captures it. It is performance tuning, not user taste.

### Query Planner Helper

Create `app/src/main/java/exh/recs/RecommendationQueryPlanner.kt`.

Responsibilities:

- Build top tag, single tag, tag pair, alias-expanded, and text-only plans.
- Pick first plan based on stored source strategy.
- Pick fallback plan if the first result is weak.
- Serialize and parse last successful strategy map.

### Integration Details

In `searchSource`:

- Replace `while (attempts < MAX_SEARCH_ATTEMPTS && searchTags.isNotEmpty())` with planned attempts.
- Keep the old "reduce tags on thrown exception" behavior only as an internal fallback if needed, but do not allow it to explode beyond two strategy attempts.
- Update `RecommendationSearchContext` with the actual query text and tags for the row drill-down.

Important:

- The planner should not try every tag pair. It should pick one or two deterministic pairs from the strongest tags.
- Avoid random strategy selection. Randomness makes debugging and cache behavior unpleasant.

### Acceptance Criteria

- Each source tries at most two strategies per refresh.
- The chosen strategy is deterministic.
- Last successful strategy can be reused.
- Weak sources do not cause runaway request volume.
- Drill-down still opens the source search with a useful text query.

## Phase 4: Alias-Aware Genre Filter Matching

### Goal

Improve filter matching across sources with different tag names without creating a giant source-specific alias database.

### Current Behavior

`GenreFilterMapper` receives desired genres and tries exact normalized matches against filter names. It does not currently expand synonyms at query time.

### Desired Behavior

When matching a desired tag to a source filter, try multiple candidate labels:

1. the original desired tag,
2. the normalized group key display form,
3. user-defined aliases for the same group,
4. small built-in universal synonyms,
5. text fallback if none match.

### Alias Data Sources

Use two layers:

#### User aliases

Already stored in `tag_alias`.

Current `GetTagAliases.awaitAliasMap()` returns alias to group. For query expansion, Claude may need a second shape:

```kotlin
groupKey -> aliases
```

Options:

- Add helper in `GetTagAliases`.
- Build the reverse map locally where needed.

#### Built-in synonyms

Keep this small and universal. Suggested starting map:

```kotlin
girls_love -> ["Girls Love", "Yuri", "GL", "Shoujo Ai"]
boys_love -> ["Boys Love", "Yaoi", "BL", "Shounen Ai"]
sci_fi -> ["Sci-Fi", "Sci Fi", "Science Fiction"]
isekai -> ["Isekai", "Another World"]
martial_arts -> ["Martial Arts", "Wuxia", "Murim"]
reincarnation -> ["Reincarnation", "Reborn"]
regression -> ["Regression", "Second Chance", "Returner"]
```

Be careful with broad synonyms:

- `murim` and `martial arts` are not identical in every context.
- `reincarnation` and `regression` are not identical.

For broad terms, use them only as query expansion, not as permanent grouping unless the user explicitly aliases them.

### Mapper API Change

Change `GenreFilterMapper.buildSearch` to accept optional alias candidates:

```kotlin
fun buildSearch(
    filterList: FilterList,
    desiredGenres: List<String>,
    aliasCandidates: Map<String, List<String>> = emptyMap(),
    forceTextOnly: Boolean = false,
): SearchParams
```

Where `aliasCandidates` maps desired group key to labels to try.

If `forceTextOnly` is true:

- do not mutate filters,
- return text query built from desired genres and aliases according to the chosen strategy.

### Matching Rules

For each desired genre:

- Build candidate labels.
- Normalize all candidate labels.
- Try source filters against any normalized candidate.
- Stop at first match for that desired genre.
- If unmatched, add the best human-readable label to text fallback.

Do not set every alias as a separate filter. One matched source filter per desired genre is enough.

### Acceptance Criteria

- Existing exact match behavior still works.
- User aliases can help match source filter labels.
- Built-in synonyms improve common tag families.
- Unmatched tags still become text query.
- No source-specific alias table is required.
- Unit tests cover exact, user alias, built-in synonym, and text-only fallback.

## Phase 5: Lightweight Source Quality Stats

### Goal

Learn which sources are useful for recommendations without storing large candidate databases or slowing the app.

### Data To Track

Track one row per source id:

- `source_id`
- `search_attempts`
- `search_successes`
- `empty_results`
- `total_results`
- `total_scored_results`
- `detail_attempts`
- `detail_successes`
- `detail_failures`
- `total_latency_ms`
- `clicked_results`
- `rated_after_clicks` if easy later
- `updated_at`

Start without `rated_after_clicks` if it complicates navigation tracking.

### Storage Choice

Use SQLDelight if implementing this pass seriously:

- `data/src/main/sqldelight/tachiyomi/data/recommendation_source_quality.sq`
- new migration `47.sqm`

Do not modify `46.sqm` for already-installed test devices if a build using migration 46 has already been installed. Add a new migration.

Suggested schema:

```sql
CREATE TABLE recommendation_source_quality (
    source_id INTEGER NOT NULL PRIMARY KEY,
    search_attempts INTEGER NOT NULL DEFAULT 0,
    search_successes INTEGER NOT NULL DEFAULT 0,
    empty_results INTEGER NOT NULL DEFAULT 0,
    total_results INTEGER NOT NULL DEFAULT 0,
    total_scored_results INTEGER NOT NULL DEFAULT 0,
    detail_attempts INTEGER NOT NULL DEFAULT 0,
    detail_successes INTEGER NOT NULL DEFAULT 0,
    detail_failures INTEGER NOT NULL DEFAULT 0,
    total_latency_ms INTEGER NOT NULL DEFAULT 0,
    clicked_results INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL
);
```

Queries:

```sql
getAll:
SELECT * FROM recommendation_source_quality;

getBySourceId:
SELECT * FROM recommendation_source_quality
WHERE source_id = :sourceId;

upsertSearchStats:
INSERT INTO recommendation_source_quality(...)
VALUES (...)
ON CONFLICT(source_id)
DO UPDATE SET ...
```

Use additive counters. Do not store per-search logs.

### Domain Layer

Add:

- `RecommendationSourceQuality`
- `RecommendationSourceQualityRepository`
- interactors:
  - `GetRecommendationSourceQuality`
  - `RecordRecommendationSourceSearch`
  - `RecordRecommendationDetailFetch`
  - `RecordRecommendationClick`

Keep the model small.

### How To Use Stats In This Pass

Use stats gently:

- Do not hide a source automatically.
- Do not override manual order.
- Do not invalidate cache.
- Do not make stats the primary ranking signal.

Recommended usage:

1. In settings, optionally show low-yield source hints later.
2. In For You, if no manual ordering exists, use quality as a weak tiebreaker after the app's default source order.
3. Reduce enrichment for very poor sources only if needed later.

Manual ordering always wins over quality learning.

### Recording Stats

In `searchSource`:

- Record start time.
- On successful source response:
  - increment attempts,
  - increment successes,
  - add raw result count,
  - add scored result count,
  - increment empty if scored result count is 0,
  - add latency.
- On source error:
  - increment attempts,
  - add latency.
- During enrichment:
  - add detail attempts,
  - successes,
  - failures.

Use `try/finally` or local `runCatching` so stats recording cannot break recommendations.

### Acceptance Criteria

- Stats are tiny and per-source only.
- Stats write failures do not affect For You.
- Manual priority still controls source ordering.
- No large candidate or history table is introduced.

## Phase 6: Conservative Duplicate Suppression

### Goal

Reduce repeated popular titles across rows without hiding legitimate different manga that happen to share similar names.

### Current Behavior

`State.dedupedItems()` computes the best score per normalized title and keeps recommendations whose score is greater than or equal to the best score.

Issue:

- Equal scores can keep duplicates.
- Title-only matching can collapse legitimate distinct works if made too aggressive.

### Desired Behavior

Keep duplicate suppression conservative and presentation-only.

Rules:

- Do not delete data from cache.
- Do not remove duplicates before scoring.
- Do not suppress if confidence is weak.
- Prefer the best occurrence only when title match is exact after normalization and at least one supporting signal agrees.

Supporting signals:

- same normalized title exactly,
- high genre overlap,
- same author if available,
- same thumbnail URL,
- very similar URL slug,
- higher score from a manually boosted source,
- higher metadata richness.

### Suggested Helper

Create `RecommendationDuplicateSuppressor`.

Suggested model:

```kotlin
internal data class DuplicateDecision(
    val keep: PersonalRecommendation,
    val suppressed: List<PersonalRecommendation>,
    val confidence: DuplicateConfidence,
)

internal enum class DuplicateConfidence {
    NONE,
    WEAK,
    STRONG,
}
```

But keep implementation simple. If this becomes too large, only fix the current equal-score issue first.

### Minimum Safe Fix

If Claude wants a low-risk implementation first:

- Replace `bestScorePerTitle` with `bestRecommendationPerTitle`.
- Winner tie-break:
  1. higher score,
  2. more genres,
  3. boosted source wins,
  4. source order index lower wins,
  5. title lexical order or manga id for deterministic final tie.
- Only suppress exact normalized title matches.
- Consider not suppressing boosted source duplicates unless the duplicate is clearly lower quality.

### Acceptance Criteria

- Equal-score duplicates no longer leak simply because of `>=`.
- Dedupe is stable across recompositions.
- Legitimate same/similar title entries are not aggressively hidden.
- Cache is untouched.

## Phase 7: Optional Result Explanations

### Goal

Make recommendations more trustworthy by showing why a source row or item appeared.

Current state:

- Row-level matched tags are shown.
- Cache stores `resultReasons`.
- Item-level reasons are not displayed.

Recommended status:

- Defer unless the user specifically wants it in this pass.

If implemented:

- Add result-level matched groups to the UI only if global search cards have a clean subtitle/badge slot.
- Do not redesign cards.
- Avoid clutter.

## Implementation Order

Use this order unless compilation reveals a better dependency order:

1. Add this plan's implementation notes to a new or existing markdown file before coding.
2. Add `RecommendationSourceOrdering` and source order preference.
3. Wire source ordering into settings state and For You source selection.
4. Add boosted source display/enrichment caps.
5. Add capped enrichment before scoring.
6. Bump cache version to `personal_v3` if enrichment changes result quality.
7. Add `RecommendationQueryPlanner` with at most two attempts per source.
8. Add alias expansion to `GenreFilterMapper`.
9. Add tests for ordering, query planning, and alias filter mapping.
10. Add source quality stats only after the above is stable.
11. Improve duplicate suppression conservatively.
12. Run focused tests and build debug APK.

If time is limited, implement phases 1-4 first. Those are likely to produce the largest user-visible improvement.

## Test Plan

### Unit Tests

Add or extend tests:

- `RecommendationSourceOrderingTest`
  - parses empty order,
  - ignores missing sources,
  - appends new sources,
  - excludes disabled sources,
  - returns first three boosted sources.

- `RecommendationQueryPlannerTest`
  - chooses default strategy with no memory,
  - starts with last successful strategy,
  - chooses one fallback only,
  - does not produce more than two plans for one refresh.

- `GenreFilterMapperTest`
  - exact match still works,
  - user alias matches source filter,
  - built-in synonym matches source filter,
  - text-only strategy skips filters,
  - unmatched aliases fall back to text query.

- `RecommendationDuplicateSuppressorTest` if a separate suppressor is created.

- `RecommendationSourceQualityTest` if quality stats are implemented.

### Focused Gradle Commands

Use the known local environment:

```powershell
$env:JAVA_HOME="C:\Users\USER\Downloads\Komikku\komikku-source\.tools\jdk21\jdk-21.0.11+10"
$env:ANDROID_HOME="C:\Users\USER\Downloads\Komikku\komikku-source\.tools\android-sdk"
$env:ANDROID_SDK_ROOT="C:\Users\USER\Downloads\Komikku\komikku-source\.tools\android-sdk"
$env:GRADLE_USER_HOME="C:\Users\USER\Downloads\Komikku\komikku-source\.tools\gradle-home"
$env:GRADLE_OPTS="-Djavax.net.ssl.trustStoreType=Windows-ROOT"
```

Gradle path:

```powershell
C:\Users\USER\Downloads\Komikku\gradle-dist\gradle-9.3.1\bin\gradle.bat
```

Suggested test command:

```powershell
& "C:\Users\USER\Downloads\Komikku\gradle-dist\gradle-9.3.1\bin\gradle.bat" :app:testDebugUnitTest --tests "*RecommendationSourceOrderingTest*" --tests "*RecommendationQueryPlannerTest*" --tests "*GenreFilterMapperTest*" --tests "*PersonalRecommendationScorerTest*" --tests "*GetTasteProfileTest*"
```

If SQLDelight schema changes:

```powershell
& "C:\Users\USER\Downloads\Komikku\gradle-dist\gradle-9.3.1\bin\gradle.bat" :data:generateCommonMainTachiyomiDatabaseInterface
```

Build:

```powershell
& "C:\Users\USER\Downloads\Komikku\gradle-dist\gradle-9.3.1\bin\gradle.bat" :app:assembleDebug
```

### Manual Tablet Test Plan

After installing the debug APK:

1. Open Browse > For You.
2. Confirm the first three manually prioritized sources appear first.
3. Confirm top three sources can show up to 20 manga.
4. Confirm normal sources show up to 10 manga.
5. Refresh For You.
6. Confirm the screen does not feel substantially slower than before.
7. Open recommendation settings.
8. Reorder sources.
9. Return to For You and refresh.
10. Confirm the order changes.
11. Disable a source and refresh.
12. Confirm it disappears.
13. Re-enable it and confirm it returns in the expected order.
14. Rate a few manga Love, Like, and Dislike.
15. Confirm Disliked manga remain hidden.
16. Confirm liked/loved behavior follows the existing rated visibility setting.
17. Test a source known to lack genres in search results.
18. Confirm recommendations improve when details provide genres.
19. Test a source known to have weak filters.
20. Confirm query fallback still returns results or fails gracefully.

## Risk Assessment

### Low Risk

- Source order preference.
- Top-three result cap changes.
- Cache version bump.
- Exact-title tie-break fix.

### Medium Risk

- Metadata enrichment.
  - Risk: slower recommendations.
  - Mitigation: hard caps, only weak metadata, sequential per source, existing parallelism cap.

- Query strategy rotation.
  - Risk: more source requests.
  - Mitigation: at most two strategies per source refresh, persist last successful strategy.

- Alias-aware filter matching.
  - Risk: broad synonyms can over-match.
  - Mitigation: small synonym set, user aliases first, fallback to text, no permanent source-specific mapping.

### Higher Risk

- Source quality stats.
  - Risk: schema and repository expansion.
  - Mitigation: additive per-source counters only, no candidate logs, stats failure must not break recommendations.

- Duplicate suppression.
  - Risk: hiding legitimate separate works.
  - Mitigation: presentation-only, exact normalized title first, conservative supporting signals, no cache deletion.

## Final Acceptance Criteria

The pass is complete when:

- Recommendation source order can be manually changed.
- Top three enabled sources use boosted caps.
- Other sources keep normal caps.
- For You enriches only a bounded number of weak-metadata candidates.
- No chapter fetches are introduced.
- Query strategy attempts are capped.
- Alias-aware filter matching works without a huge alias database.
- Source quality stats, if implemented, are tiny and cannot break recommendations.
- Duplicate suppression is conservative and deterministic.
- Focused tests pass.
- A debug APK builds successfully.
