# KMK-Recs v0.4.3 Implementation Report

Date: 2026-06-14

Status: complete.

## Pre-Implementation State

- Feature version at start: KMK-Recs v0.4.2
- `RecommendationSourceRunStatus.kt` was already created (Phase 1 completed in a previous session)
- All other v0.4.3 changes were pending

## What Changed

### Phase 1 + 2: Source run status model and persistence

`RecommendationSourceRunStatus.kt` (new, created in prior session):

- `RecommendationSourceRunStatus` data class: `sourceId`, `status`, `visibleCount`, `updatedAt`
- `RecommendationSourceStatus` enum: `Shown`, `NoMatches`, `FilteredOut`, `Error`, `Disabled`, `OutsideAttemptLimit`
- `RecommendationSourceRunStatusStore` object: pipe+semicolon serializer/parser

`SourcePreferences.kt`:

- Added `fun recommendationLastSourceRunStatuses()` preference (`recommendation_last_source_run_statuses`, default `""`)

### Phase 3: Adaptive source fill

`BrowsePersonalRecommendationsScreenModel.kt` — major refactor:

- Replaced `orderedEnabledSources.take(MAX_SOURCES)` with adaptive batch loop
- Added `SourceSearchOutcome` private data class: source, result, successfulStrategy, status, isUseful
- Constants renamed/added: `MAX_SOURCES` → `MAX_VISIBLE_SOURCE_ROWS=20`, added `MAX_SOURCE_ATTEMPTS=40`, `BOOSTED_SOURCE_COUNT=3`, `SOURCE_BATCH_SIZE=5`, `TOP_PICKS_ROW_CAP=20`, `TOP_PICKS_DETAIL_CAP=50` (replacing `COMBINED_ROW_CAP`)
- `State` gains `combinedDetailResult: PersonalRecommendationResult?` and `sourceStatuses: PersistentMap<Long, RecommendationSourceRunStatus>`
- `searchSource()` now returns `SourceSearchOutcome` instead of `RecommendationQueryStrategyType?`; determines `Shown`/`NoMatches`/`FilteredOut`/`Error` from result; no longer calls `updateItem()` internally
- `updateItem()` gains optional `status` param, ranks Top Picks twice (row cap 20, detail cap 50), updates `combinedDetailResult` and `sourceStatuses` atomically
- `load()` batch loop: 5 sources per batch, stops when `usefulCount >= MAX_VISIBLE_SOURCE_ROWS` or all candidates attempted; persists statuses after run
- Boosted sources: fixed top-3 of `orderedEnabledSources`, not adaptive

Status determination in `searchSource()`:
- Cache hit with results → `Shown`
- Cache hit empty → `NoMatches`
- Live search: scored non-empty → `Shown`; `page.mangas` empty → `NoMatches`; raw non-empty but scored empty → `FilteredOut`; exception on all plans → `Error`

### Phase 4: Source status in Recommendation Settings

`RecommendationsSettingsScreenModel.kt`:

- Added `import`s for `RecommendationSourceRunStatus`, `RecommendationSourceRunStatusStore`, `ImmutableMap`, `persistentMapOf`, `toPersistentMap`
- Added `private val lastSourceStatusesPref`
- `State` gains `sourceStatuses: ImmutableMap<Long, RecommendationSourceRunStatus>`
- Init reads and parses `recommendationLastSourceRunStatuses()` into state

`RecommendationsSettingsScreen.kt`:

- Added `status: RecommendationSourceRunStatus?` param to `SourcePriorityItem`
- Subtitle line now shows `"${lang} · #$rank · $statusText"` — status text derived from `status` and `enabled`
- Call site passes `state.sourceStatuses[source.id]`
- Status strings from `KMR.strings.rec_source_status_*`

### Phase 5: Top Picks drill-down

`TopPicksScreen.kt` (new):

- `class TopPicksScreen(private val mangaIds: ArrayList<Long>) : Screen()`
- `TopPicksScreenModel`: fetches manga from DB by ID in `init` block
- UI: `Scaffold` with `AppBar("Top Picks")` + `LazyVerticalGrid(GridCells.Adaptive)` using `MangaItem`
- Tapping a manga pushes `MangaScreen(manga.id, true)`

`BrowsePersonalRecommendationsTab.kt`:

- Added `onClickTopPicks: () -> Unit` param to `PersonalRecommendationsContent`
- Top Picks header `onClick = {}` changed to `onClick = onClickTopPicks`
- `personalRecommendationsTab()` computes `onClickTopPicks`: extracts IDs from `state.combinedDetailResult`, pushes `TopPicksScreen`; no-op if detail result is empty

### Phase 6: What's New cleanup

`KmkRecsReleaseNotes.kt`:

- `VERSION_CODE = 403`, `VERSION_NAME = "KMK-Recs v0.4.3"`
- MARKDOWN updated: user-facing v0.4.3 bullets only; also includes v0.4.2 summary; documentation bullet removed

`WhatsNewScreen.kt`:

- `onOpenInBrowser: () -> Unit` → `onOpenInBrowser: (() -> Unit)? = null`
- Browser button wrapped in `if (onOpenInBrowser != null)` guard

`KmkRecsWhatsNewScreen.kt`:

- Removed `onOpenInBrowser = {}` argument; the screen now has no browser button

### Phase 7: Strings

`i18n-kmk/strings.xml`:

- Added: `rec_source_status_shown`, `rec_source_status_no_matches`, `rec_source_status_filtered`, `rec_source_status_error`, `rec_source_status_disabled`, `rec_source_status_not_searched_limit`, `rec_source_status_not_checked`

## Tests Added

`RecommendationSourceRunStatusStoreTest.kt` — 9 tests:

- serialize empty → empty string
- parse empty/blank → empty map
- single Shown round-trip
- multiple statuses round-trip
- malformed rows skipped without crash
- rows with too few fields skipped
- unknown enum value skipped
- visibleCount=0 round-trip
- duplicate sourceId behavior

## Tests Run

- `:app:testDebugUnitTest --tests "exh.recs.*"` — BUILD SUCCESSFUL
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL (all tests)
- `:app:assembleDebug` — BUILD SUCCESSFUL

## Known Limitations

- `HiddenByDuplicateHandling` status is not recorded. A source whose candidates are fully hidden by cross-source dedupe shows as `Shown` (it produced scored results). The plan noted this was deferred.
- Status for disabled sources relies on the Settings screen checking `state.disabledSourceIds` rather than a stored `Disabled` entry — disabled sources never appear in `eligibleSources` so they are never added to `allStatuses`.
- `TopPicksScreen` shows the Top Picks result available at the time the user taps the header. If For You is still loading, the list may be partial. This is expected behavior per the plan.
- The `progress/total` counter in `State` remains item-count based. With adaptive fill, `total` grows as batches are added.

## Deviations From Plan

- Plan suggested considering `Disabled` status for disabled sources via the stored map. Implementation instead derives "Disabled" display from `!enabled` in the settings UI — simpler and avoids enumerating disabled sources in `load()`.
- `SourceSearchOutcome.isUseful` defined as `result is Success && result.result.isNotEmpty()` (pre-dedupe as planned).
- Plan mentioned `OutsideVisibleLimit` enum value — not added; `OutsideAttemptLimit` covers sources not reached; sources within the attempt limit but not producing useful rows are shown with `NoMatches`/`FilteredOut`/`Error`.
