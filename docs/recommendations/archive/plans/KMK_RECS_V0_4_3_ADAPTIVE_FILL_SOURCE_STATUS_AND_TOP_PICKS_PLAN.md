# KMK-Recs v0.4.3 Adaptive Fill, Source Status, And Top Picks Drill-Down Plan

Date: 2026-06-14

Status: implementation plan only. Do not implement until the user explicitly approves and requests coding.

Target feature version: `KMK-Recs v0.4.3`

Expected APK naming after implementation:

```text
Komikku-v1.13.6-kmk.4.3-debug.apk
Komikku-v1.13.6-kmk.4.3-release.apk
```

## Purpose

KMK-Recs v0.4.2 works, but the user still cannot tell why a preferred source disappears from Browse > For You. A boosted source may be searched, return no usable matches, be filtered out, or be hidden by duplicate handling, and the UI currently gives no clear explanation.

This pass should make For You source behavior more transparent and more useful:

- empty or filtered sources should not permanently consume the final visible 20 source-row slots,
- Recommendation Settings should show the latest For You status for each source,
- Top Picks should be tappable and expandable to a full list of up to 50 ranked picks,
- local KMK-Recs What's New notes should contain only user-facing changes.

This pass must stay efficient. Do not add a separate background catalogue crawl. Do not make Recommendation Settings run extension searches. Do not add tracker/AniList per-result lookups. Do not fetch chapter lists purely for filtering.

## Current Baseline

Current implemented feature version:

```text
KMK-Recs v0.4.2
```

Relevant implemented behavior:

- Browse > For You picks the first 20 enabled, language-filtered sources:

```kotlin
val sources = orderedEnabledSources.take(MAX_SOURCES)
```

- Top three selected sources receive higher caps:

```kotlin
private const val NORMAL_RESULTS_PER_SOURCE = 10
private const val BOOSTED_RESULTS_PER_SOURCE = 20
```

- Top Picks row shows at most 20 results:

```kotlin
private const val COMBINED_ROW_CAP = 20
```

- Empty source rows are hidden in `BrowsePersonalRecommendationsTab.kt`:

```kotlin
val visibleOrderedSources = state.sourceOrder.filter { source ->
    val result = dedupedMap[source]
    result != null && (result !is PersonalRecommendationResult.Success || !result.isEmpty)
}
```

- Top Picks header is a no-op:

```kotlin
GlobalSearchResultItem(
    title = stringResource(KMR.strings.rec_top_picks_title),
    subtitle = subtitle,
    onClick = {},
)
```

- `KmkRecsReleaseNotes.MARKDOWN` currently includes a development/documentation bullet:

```text
- Updated recommendation documentation for future handoffs.
```

That line should not be user-facing.

## Product Decisions

### Source rows

Final visible For You source rows should be capped at 20 successful/useful source rows.

Sources that return no usable recommendation matches should not consume a final visible row slot. Since Komikku cannot know this until it searches the source, the implementation should use adaptive fill:

1. Search priority sources in order.
2. If a searched source produces no usable visible row, record why.
3. Try the next source from priority order.
4. Continue until 20 useful source rows are available, the eligible source list is exhausted, or a safety attempt cap is reached.

### Boosted sources

The top three priority sources remain the boosted sources.

Do not automatically transfer boosted status to source #4 if a boosted source has no matches. The user chose the top three; if one fails or returns nothing, settings should clearly show that it was boosted but had no matches.

### Top Picks row and detail

For You should still show 20 Top Picks inline.

Tapping the Top Picks header/arrow should open a full Top Picks detail screen showing up to 50 ranked candidates.

This detail screen must use candidates already collected by the For You search pass. It must not launch a second recommendation crawl.

## Approved Scope For v0.4.3

### Phase 1: Add source run status model

Goal: record why each eligible source did or did not appear in For You.

Add a lightweight status model in `exh.recs`, for example:

```kotlin
data class RecommendationSourceRunStatus(
    val sourceId: Long,
    val status: RecommendationSourceStatus,
    val rawCount: Int = 0,
    val localizedCount: Int = 0,
    val knownFilteredCount: Int = 0,
    val scoredCount: Int = 0,
    val visibleCount: Int = 0,
    val query: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

enum class RecommendationSourceStatus {
    Queued,
    Searching,
    Shown,
    NoMatches,
    FilteredOut,
    HiddenByDuplicateHandling,
    Error,
    Disabled,
    OutsideAttemptLimit,
    OutsideVisibleLimit,
}
```

Claude may choose simpler names, but the statuses should cover:

- searched and shown,
- searched and no raw matches,
- searched and all candidates filtered out,
- searched and hidden by cross-source duplicate handling,
- searched and errored,
- enabled but not reached because of safety cap,
- enabled but not in the final visible 20,
- disabled.

Keep this model small and local. Do not create a database table unless clearly necessary.

### Phase 2: Persist last For You source status

Goal: Recommendation Settings can show status from the latest For You run without triggering new searches.

Add a new preference to `SourcePreferences`:

```kotlin
fun recommendationLastSourceRunStatuses() =
    preferenceStore.getString("recommendation_last_source_run_statuses", "")
```

Store a compact serialized representation, for example:

```text
sourceId|status|raw|localized|knownFiltered|scored|visible|updatedAt;...
```

or JSON if an existing JSON helper is convenient. Prefer a tiny parser/serializer object if the codebase does not already have a clean JSON dependency in this layer.

Recommended helper:

```kotlin
object RecommendationSourceRunStatusStore {
    fun serialize(statuses: Collection<RecommendationSourceRunStatus>): String
    fun parse(value: String): Map<Long, RecommendationSourceRunStatus>
}
```

Rules:

- Parse failures should return an empty map or skip bad rows.
- Do not crash settings because of malformed status data.
- Do not include manga titles, URLs, or personally sensitive reading data.
- Keep only source-level counts/statuses.
- Status data is diagnostic only and can be overwritten every For You refresh.

### Phase 3: Adaptive source fill

Goal: final For You display should contain up to 20 useful source rows, not merely the first 20 attempted sources.

Current code:

```kotlin
val sources = orderedEnabledSources.take(MAX_SOURCES)
```

Replace this with an adaptive attempt approach.

Recommended constants:

```kotlin
private const val MAX_VISIBLE_SOURCE_ROWS = 20
private const val MAX_SOURCE_ATTEMPTS = 40
private const val BOOSTED_SOURCE_COUNT = 3
```

Keep:

```kotlin
private const val NORMAL_RESULTS_PER_SOURCE = 10
private const val BOOSTED_RESULTS_PER_SOURCE = 20
```

Recommended behavior:

1. Build `eligibleSources` from language-filtered, manually ordered, enabled sources.
2. Compute boosted source IDs from the first three eligible sources, not from the adaptive moving window.
3. Attempt sources in priority order.
4. Keep existing parallelism limit (`Dispatchers.IO.limitedParallelism(5)`).
5. Continue launching source searches until either:
   - 20 useful source rows are found,
   - `MAX_SOURCE_ATTEMPTS` have been attempted,
   - or no eligible sources remain.

Important definition:

```text
Useful source row = source produced a non-empty scored recommendation list before cross-source display dedupe.
```

Reasoning:

- A source should not be considered useless just because another higher-ranked source wins duplicate-display handling later.
- If dedupe hides all of a source's cards, status should become `HiddenByDuplicateHandling`, but the source did provide matches.

Implementation guidance:

- Extract the result data from `searchSource()` so it can return more than just the successful query strategy.
- Example:

```kotlin
private data class SourceSearchOutcome(
    val source: CatalogueSource,
    val result: PersonalRecommendationResult,
    val successfulStrategy: RecommendationQueryStrategyType?,
    val status: RecommendationSourceRunStatus,
)
```

- `searchSource()` can return `SourceSearchOutcome` instead of `RecommendationQueryStrategyType?`.
- `updateItem(source, result)` can still handle UI state and Top Picks accumulation.
- Store status after each source completes and persist the final statuses after the run.

Avoid this anti-pattern:

```kotlin
settings screen opens -> run searches to determine status
```

Settings must only display last run status.

Potential scheduling options:

Option A: sequential batches of up to 5.

- Attempt 5 at a time.
- Count useful results.
- Launch more until enough useful rows exist.
- Simpler and predictable.

Option B: rolling queue with up to 5 active jobs.

- More efficient but more complex.

Recommendation: Option A is acceptable for v0.4.3. It respects the current parallelism limit, avoids complex cancellation bugs, and is easier to test.

Pseudo-flow:

```kotlin
val eligibleSources = orderedEnabledSources
val boostedSourceIds = eligibleSources.take(BOOSTED_SOURCE_COUNT).map { it.id }.toSet()
val attempted = mutableListOf<CatalogueSource>()
var usefulCount = 0

for (batch in eligibleSources.take(MAX_SOURCE_ATTEMPTS).chunked(SOURCE_BATCH_SIZE)) {
    val outcomes = batch.map { source -> async { searchSourceOutcome(source, ...) } }.awaitAll()
    for (outcome in outcomes) {
        attempted += outcome.source
        updateItem(outcome.source, outcome.result)
        updateStatus(outcome.status)
        if (outcome.result is Success && outcome.result.result.isNotEmpty()) usefulCount++
    }
    if (usefulCount >= MAX_VISIBLE_SOURCE_ROWS) break
}
```

Then state should render only up to `MAX_VISIBLE_SOURCE_ROWS` useful source rows.

State changes:

- `sourceOrder` should probably represent attempted sources, not only initial 20.
- Add `sourceStatuses: PersistentMap<Long, RecommendationSourceRunStatus>`.
- Add `topPicksFullResult: PersonalRecommendationResult?` or similar for Top Picks drill-down.

Completion behavior:

- `progress` and `total` currently derive from `items.size`; adaptive fill makes total dynamic.
- Replace or supplement with explicit progress fields if needed:

```kotlin
val attemptedCount: Int
val maxAttemptCount: Int
val usefulSourceCount: Int
val targetVisibleSourceCount: Int
```

Keep the UI simple. Existing loading spinners are acceptable.

### Phase 4: Source status in Recommendation Settings

Goal: make it obvious why a boosted or priority source is not appearing in For You.

`RecommendationsSettingsScreenModel` should parse `recommendationLastSourceRunStatuses()` and include it in state:

```kotlin
val sourceStatuses: ImmutableMap<Long, RecommendationSourceRunStatus>
```

`SourcePriorityItem` should display a compact subtitle/status line.

Suggested display:

- enabled + boosted + shown:

```text
EN · #1 · Boosted · Shown: 20 matches
```

- boosted but empty:

```text
EN · #1 · Boosted · No matches in last For You run
```

- filtered:

```text
EN · #4 · Filtered out by ratings/known/blocked tags
```

- duplicate-hidden:

```text
EN · #8 · Hidden by duplicate handling
```

- not reached:

```text
EN · #24 · Not searched: attempt limit reached
```

- no status yet:

```text
EN · #12 · Not checked yet
```

Do not clutter the list with too much data. Counts are enough.

Recommended extra strings in `i18n-kmk`:

- `rec_source_status_not_checked`
- `rec_source_status_searching`
- `rec_source_status_shown`
- `rec_source_status_no_matches`
- `rec_source_status_filtered`
- `rec_source_status_duplicate_hidden`
- `rec_source_status_error`
- `rec_source_status_not_searched_limit`
- `rec_source_status_disabled`
- `rec_source_status_matches`

Exact wording can be adjusted, but it should be user-readable and not overly technical.

### Phase 5: Top Picks drill-down to 50

Goal: tapping Top Picks opens a full list of up to 50 ranked recommendations based on the same For You search run.

Current Top Picks inline cap remains 20.

Add:

```kotlin
private const val TOP_PICKS_ROW_CAP = 20
private const val TOP_PICKS_DETAIL_CAP = 50
```

Replace `COMBINED_ROW_CAP` with clearer naming if practical. Internal `CombinedPicksAccumulator` can remain as-is to avoid churn.

In `updateItem()`:

- rank Top Picks twice:

```kotlin
val rowRanked = combinedAccumulator.rank(currentBoostedSourceIds, TOP_PICKS_ROW_CAP)
val detailRanked = combinedAccumulator.rank(currentBoostedSourceIds, TOP_PICKS_DETAIL_CAP)
```

- store row result for inline display,
- store detail result for full screen navigation.

Add a new screen, for example:

```kotlin
class TopPicksScreen(
    private val mangaIds: List<Long>,
) : Screen()
```

or:

```kotlin
class TopPicksScreen(
    private val recommendations: List<PersonalRecommendation>,
) : Screen()
```

Prefer passing stable manga IDs or a small serializable payload if Voyager serialization requires it. If passing full domain `Manga` is not safe, pass IDs and use `GetManga` in the screen model.

UI options:

Option A: simple LazyColumn list.

- Easier and reliable.
- Use existing manga list item components.

Option B: grid matching browse source.

- More visual.
- Slightly more implementation work.

Recommendation: use the existing browse/library manga item components and match Komikku style. Claude should inspect `BrowseSourceScreen` and related browse components to choose the least invasive native-looking list/grid.

Behavior:

- App bar title: `Top Picks`.
- Subtitle or empty state: optional.
- Show up to 50 ranked manga.
- Tapping manga opens `MangaScreen(manga.id, true)`.
- No extra source searches.
- If user taps Top Picks before detail results are available, either do nothing or show current available results.

In `BrowsePersonalRecommendationsTab.kt`, change Top Picks header:

```kotlin
onClick = { onClickTopPicks() }
```

`onClickTopPicks` should push the new screen with the latest full Top Picks list.

### Phase 6: Clean local KMK-Recs What's New

Goal: user-facing changelog should only contain actual user-facing app changes.

Update `KmkRecsReleaseNotes.kt`:

- bump to:

```kotlin
const val VERSION_CODE = 403
const val VERSION_NAME = "KMK-Recs v0.4.3"
```

- remove development/documentation-only bullets.
- include only user-facing items.

Suggested v0.4.3 notes:

```markdown
## KMK-Recs v0.4.3

- For You now keeps filling from your source priority list so empty sources do not take up final recommendation slots.
- Recommendation Settings now shows the latest For You status for each source, including no matches, filtered results, errors, and duplicate-hidden rows.
- Top Picks can now be opened to view up to 50 ranked recommendations.
- KMK-Recs What's New now only shows user-facing recommendation changes.
```

Do not mention:

- documentation updates,
- implementation reports,
- internal handoff notes,
- tests,
- development-only cleanup.

Also fix the local What's New no-op browser button:

Current `KmkRecsWhatsNewScreen` passes:

```kotlin
onOpenInBrowser = {}
```

Better options:

1. Make `eu.kanade.presentation.more.WhatsNewScreen` accept nullable `onOpenInBrowser: (() -> Unit)? = null` and only show the button when non-null.
2. Create a small local KMK-specific screen that renders markdown without the browser button.

Recommendation: Option 1 is cleaner if it does not disturb upstream call sites. Existing upstream calls can pass the function normally. KMK local notes can pass `null`.

Acceptance criteria:

- No useless browser button on local KMK notes.
- Upstream Komikku What's New still has the browser/open-release button.
- KMK notes only list user-visible changes.

### Phase 7: Documentation and versioning

Create after implementation:

```text
docs/recommendations/KMK_RECS_V0_4_3_ADAPTIVE_FILL_SOURCE_STATUS_AND_TOP_PICKS_IMPLEMENTATION.md
```

Update:

- `docs/recommendations/README.md`
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `RECOMMENDATION_VERSIONING.md`

Also organize stale planning/history documents so future agents can quickly distinguish current truth from old context.

Recommended approach:

1. Create an archive folder if it does not already exist:

```text
docs/recommendations/archive/
```

2. Move or copy superseded recommendation planning documents into the archive only if doing so will not break active references. If moving root-level historical files feels risky, leave the file in place and add a clear "Superseded / historical reference" note at the top instead.

3. Add or update:

```text
docs/recommendations/archive/README.md
```

The archive README should explain:

- these files are historical context,
- current source of truth starts at `docs/recommendations/README.md`,
- old plans should not be treated as active instructions unless a newer plan references them.

4. Keep current implementation reports easy to find. Do not bury the latest implementation report in the archive.

5. Do not delete historical markdown files. Preserve them for audit/history.

6. If any files are moved, update all links in `docs/recommendations/README.md`, `NEXT_WORK.md`, and the new implementation report.

Implementation report must include:

- pre-implementation verification,
- files changed,
- behavior changed,
- tests run,
- known limitations,
- deviations from this plan.

## Explicitly Deferred

Do not implement these in v0.4.3 unless separately approved:

- source quality learning,
- automatic disabling or demotion of poor sources,
- AniList/tracker known-list cache,
- minimum chapter count filter,
- query-time blocked tag exclusion,
- Local Source recommendation support,
- pull-to-refresh,
- background source scans,
- full recommendation database.

## Risk Notes

### Adaptive fill risk

This changes search scheduling. Keep strict caps:

- max 20 visible source rows,
- max 40 attempted sources,
- max 5 concurrent source searches.

If many sources are slow, this can still take longer than v0.4.2 because it may try more than 20 sources. That is expected, but bounded.

### Status accuracy risk

Do not overclaim. If exact reason is unclear, use a broad status like `Filtered out` or `No visible matches`.

### Duplicate handling risk

Cross-source dedupe can hide a source after it produced matches. Record that separately as `HiddenByDuplicateHandling` so the user understands the source is not broken.

### Settings performance risk

Settings must parse a small serialized status string only. It must not search extensions.

### Top Picks detail risk

Do not launch another crawl. The detail screen should show the best 50 from already fetched/cached For You results.

## Required Validation

Run at minimum:

```text
./gradlew :app:testDebugUnitTest
```

If practical:

```text
./gradlew :app:assembleDebug
```

Recommended focused tests:

- parser/serializer tests for `RecommendationSourceRunStatusStore`,
- adaptive fill helper tests if scheduling is extracted into a pure helper,
- Top Picks cap behavior if there is an easy unit-test seam.

Manual verification:

1. Put a known weak/empty source in the top 3.
2. Refresh For You.
3. Confirm the weak source does not consume a final visible row if it has no matches.
4. Open Recommendation Settings.
5. Confirm the weak source shows `No matches`, `Filtered out`, or `Error`.
6. Confirm top three sources still show boosted labels.
7. Tap Top Picks and confirm a full screen opens with up to 50 recommendations.
8. Open KMK-Recs What's New and confirm no development/documentation-only bullets appear.
9. Confirm the local What's New screen has no no-op browser button.

## Summary For Claude

Implement KMK-Recs v0.4.3 as a bounded improvement to For You transparency and navigation:

- adaptively fill For You source rows so empty sources do not consume the final 20 visible source slots,
- persist lightweight last-run source statuses and show them in Recommendation Settings,
- keep top three priority sources boosted with 20 results,
- keep inline Top Picks at 20 results,
- add a Top Picks detail screen showing up to 50 ranked results from already-fetched candidates,
- clean KMK-Recs What's New so it only contains user-facing changes and remove the no-op browser button.

Keep the implementation efficient, local, bounded, and aligned with existing Komikku/KMK patterns.
