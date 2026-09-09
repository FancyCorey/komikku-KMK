# KMK-Recs v0.8.2 - For You Display Count Plan

Status: **implemented and shipped in KMK-Recs v0.8.5**. See `KMK_RECS_V0_8_2_TO_V0_8_5_FOR_YOU_UI_AND_READING_TIMER_IMPLEMENTATION.md` for the implementation report.

## Scope

Add one validated user preference controlling the number of visible manga cards in ordinary For You source rows. This is a display/result-budget feature, not a source-count, scoring, enrichment, or Source Evaluation feature.

## Current code contract

Inspect these exact areas before editing:

- app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt
  - companion constants NORMAL_RESULTS_PER_SOURCE, MAX_VISIBLE_SOURCE_ROWS, MAX_SOURCE_ATTEMPTS, TOP_PICKS_ROW_CAP, TOP_PICKS_DETAIL_CAP;
  - source query orchestration and updateItem;
  - candidate visibility filtering;
  - cache read/write and cache fingerprint creation;
  - discovery-page merging and Top Picks accumulator.
- app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt
  - existing For You behavior/settings section and option-row patterns.
- app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt
  - preference flows and cache invalidation actions.
- SourcePreferences and its preference implementation.
- source recommendation cache schema/repository.
- existing tests for recommendation preferences, cache fingerprints, candidate visibility, and discovery.

Do not change MAX_SOURCE_ATTEMPTS, MAX_VISIBLE_SOURCE_ROWS, BOOSTED_SOURCE_COUNT, MAX_CROSS_EXTENSION_SOURCES, discovery-page caps, Source Evaluation caps, or Top Picks caps.

## Preference contract

Add a validated integer/enum preference named items-per-source with values 5, 10, 15, 20, 30. Default is 10. Use the repository's existing preference key namespace and accessor style. Invalid values, missing values, negative values, and values above the supported maximum resolve to 10 without crashing.

The setting must be included in the same cache fingerprint used to prevent stale For You results. A changed value must cause a new result request or a deterministic post-cache expansion path; it must never display a cache produced for a smaller configured count as though it were complete.

## Exact result behavior

For each ordinary source row:

1. Fetch only through the existing bounded source/search path.
2. Apply existing raw-result deduplication.
3. Enrich only according to the existing enrichment cap; do not multiply enrichment calls by the display setting.
4. Apply the shared RecommendationCandidateVisibilityPolicy.
5. Score using the existing scorer.
6. Deduplicate using the existing source/url identity policy.
7. Retain the best visible candidates up to the configured count.
8. Preserve existing source priority and row ordering.
9. Keep error, no-match, filtered, outdated, and hidden statuses truthful.

If the source API can return a requested page size, pass a bounded requested size. If it cannot, do not add an unbounded page loop. A 30-item setting may use existing bounded additional-page discovery only if the current discovery policy explicitly allows it and the progress table remains truthful.

Top Picks is independent: keep the inline Top Picks cap at 20 and detail cap at 50. Do not reinterpret the setting as a Top Picks setting.

## UI

Add the option inside the existing For You behavior/settings section. Use the repository's existing single-choice preference row/dropdown pattern, not a new custom picker. Display the current value and a short KMR-localized explanation that larger values may require more loading and storage. Keep the control usable on a phone and theme-aware.

Changing the value must update state immediately, dismiss the picker safely, and invalidate the For You cache. It must not automatically start multiple simultaneous network runs. The next normal refresh/load uses the new value.

## Tests

Add pure tests for fallback, supported values, cache fingerprint, post-visibility count, Top Picks independence, and unchanged source-attempt/boosted-source limits. Add a regression test proving a rated/known/Not Interested candidate does not consume a visible slot.

## Acceptance criteria

- Default behavior remains 10 visible results per ordinary source row.
- 5/15/20/30 work after app restart.
- No source receives more than the configured visible count.
- No hidden candidate consumes a slot.
- Top Picks remains 20/50.
- No unbounded additional search occurs.
- Offline, empty, and error states are unchanged except for the configured display count.
- KMR strings, preference backup/sync behavior, documentation, and tests are complete.



## Code-level implementation addendum from source review

The current display budget is calculated in searchSource and in the cache/memory path, not only in the composable. The same displayLimit is passed to RecommendationCandidateMemoryRanker.merge, PersonalRecommendationScorer.rankCandidates, and rawCap calculation. Therefore Claude must introduce one pure resolver, for example ForYouResultBudgetPolicy, and use it at every current displayLimit assignment. Do not edit only NORMAL_RESULTS_PER_SOURCE.

The existing boosted contract is BOOSTED_SOURCE_COUNT = 3, BOOSTED_RESULTS_PER_SOURCE = 20, and BOOSTED enrichment is separate. Preserve it as follows: the preference controls normal rows; boosted rows use max(configured normal value, 20), so selecting 5/10/15 cannot reduce the top-three rows below their existing 20-result contract, while selecting 30 makes boosted rows 30. The raw candidate cap and merge limit must use the resolved row budget, but enrichment must continue using the existing enrichment preference and its existing 2x boosted rule, never the row-count preference.

The fingerprint call is in the main For You run and currently includes taste, aliases, disabled sources, ordering, languages, known visibility, seen count, and minimum chapter count. Add the resolved row-budget preference to this fingerprint. Because the cache table stores a fixed result list, a cache created with 10 items cannot satisfy a later 30-item request. On preference increase, invalidate the affected recommendation cache and force the next normal run to fetch again; do not silently present an incomplete cached row. Candidate memory/progress must retain its existing bounded policy and must not be cleared unless the existing cache-reset contract requires it.

The cached path at searchSource and the live path after additional-page merge must use the same ForYouResultBudgetPolicy. Add tests for normal=5/10/15/20/30, boosted minimum 20, boosted=30, cache invalidation, and no enrichment multiplication.


