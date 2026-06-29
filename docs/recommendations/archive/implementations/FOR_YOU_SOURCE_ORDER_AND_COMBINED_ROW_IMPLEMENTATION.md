# For You Source Order And Combined Row Implementation

Date: 2026-06-14

Version: KMK-Recs v0.4.0

APK: `Komikku-v1.13.6-kmk.4.0-debug.apk`

## Pre-Implementation Verification

Before coding, the following checks were performed:

- **For You row ordering bug confirmed**: `BrowsePersonalRecommendationsScreenModel.kt` contained `newItems.toSortedMap(compareBy { s -> s.name })` which forced alphabetical display order regardless of source priority.
- **No hidden combined row implementation found**: No existing disconnected combined/local row code was found in `BrowsePersonalRecommendationsTab.kt` or `BrowsePersonalRecommendationsScreenModel.kt`. The plan correctly identified this as a new feature.
- **PersistentMap ordering**: Verified that `kotlinx.collections.immutable.PersistentMap` is hash-based and does NOT preserve insertion order. This required using `PersistentList<CatalogueSource>` for stable ordered display rather than a sorted map.
- **Local Source exclusion still intentional**: `RecommendationSourceFilter` excludes `id == 0L` via `includeLocal = false`. This is correct — Local Source is for local manga files, not a recommendation engine. Phase 3 (real Local Source) was not implemented.
- **No newer markdown files changed scope**: Only the plan document and this session's audit document existed.
- **Race condition identified**: `updateItem()` was reading `state.value.items` outside `mutableState.update {}`, which could lose concurrent updates. Fixed in this implementation.

## User-Approved Scope

Phase 1: Fix For You row ordering to match source priority order.

Phase 2: Add synthetic Combined Picks row derived from already-fetched source results.

Phase 3 (real Local Source) not included per plan.

## What Changed

### Phase 1 — Row Ordering Fix

**`BrowsePersonalRecommendationsScreenModel.kt`**

- Added `sourceOrder: PersistentList<CatalogueSource>` and `combinedResult: PersonalRecommendationResult?` fields to `State`.
- `load()` now sets `sourceOrder = sources.toPersistentList()` in the initial state update.
- `updateItem()` was rewritten: items mutation moved inside `mutableState.update {}` lambda to fix race condition.

**`BrowsePersonalRecommendationsTab.kt`**

- Replaced `visibleItems.forEach { (source, result) -> }` with `visibleOrderedSources.forEach { source -> }` where `visibleOrderedSources` is derived from `state.sourceOrder` filtered to non-empty results.
- Source rows now render in the same priority order used for source selection.

### Phase 2 — Combined Picks Row

**`CombinedPicksAccumulator.kt`** (new file)

- Pure class, not thread-safe internally — callers synchronize with `accumulatorLock`.
- Keyed by `"${manga.source}:${manga.url}"` — same-title manga from different source+url are never merged.
- Ranking: `bestScore + (occurrenceCount - 1) * OCCURRENCE_BONUS + (if boosted) BOOSTED_BONUS`.
- `OCCURRENCE_BONUS = 0.3`, `BOOSTED_BONUS = 0.2`.

**`BrowsePersonalRecommendationsScreenModel.kt`**

- Added `accumulatorLock`, `combinedAccumulator`, `currentBoostedSourceIds` instance fields.
- `load()` resets accumulator and sets `currentBoostedSourceIds = boostedSourceIds`.
- `updateItem()` feeds each successful result into the accumulator (synchronized), then ranks to produce `combinedResult`.
- Added `COMBINED_ROW_CAP = 20` constant.

**`BrowsePersonalRecommendationsTab.kt`**

- Combined Picks row renders before all per-source rows.
- Uses `key = "combined_picks"` for LazyColumn stable identity.
- Row header click is no-op (first pass — drill-down not implemented).
- Individual manga tap works normally.
- Combined row is only shown when `combinedResult` is a non-empty `Success`.
- Combined row is NOT passed through `dedupedItems()` — each row dedupes independently.

**`i18n-kmk/strings.xml`**

- `rec_combined_picks_title` = `"Combined Picks"`
- `rec_combined_picks_subtitle` = `"Ranked across selected sources"`
- `rec_combined_picks_matched` = `"Matched: %1$s"`

## Files Changed

- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt`
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsTab.kt`
- `app/src/main/java/exh/recs/CombinedPicksAccumulator.kt` (new)
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`
- `RECOMMENDATION_VERSIONING.md`
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/SOURCE_LIST_DIAGNOSIS.md`

## Tests Run

- `CombinedPicksAccumulatorTest.kt` — 12 new tests, all pass
- Full `:app:testDebugUnitTest` — BUILD SUCCESSFUL

## Known Limitations

- Combined Picks header click is a no-op. Drill-down screen not implemented in this pass.
- Combined Picks is rebuilt from scratch on each `load()` — it does not cache independently. If per-source rows load from cache, Combined Picks is rebuilt from those cached results. This is correct behavior per plan.
- Combined Picks deduplication is by `(source, url)`, not by title. Two entries with the same title but different source+url remain as separate items.

## Deviations From Plan

- Plan suggested `sourceOrderIds: PersistentList<Long>`. Implementation uses `sourceOrder: PersistentList<CatalogueSource>` directly, which allows the UI to access source name and lang without a separate lookup. This is strictly better.
- Plan listed a `sortItemsBySourceOrder()` helper. Implementation stores the list directly in `State` and filters it in the UI instead, which is simpler and avoids a separate helper function.
- No separate settings toggle added for Combined Picks (plan listed this as optional and recommended enabling by default).
