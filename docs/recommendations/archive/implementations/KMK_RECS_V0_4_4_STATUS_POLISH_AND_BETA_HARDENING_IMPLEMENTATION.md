# KMK-Recs v0.4.4 Status Polish and Beta Hardening — Implementation Report

Date: 2026-06-14

Status: implemented and tested.

## Summary

v0.4.4 adds `HiddenByDuplicateHandling` source status, live settings freshness, Top Picks loading/empty/partial states, and a status note header in Recommendation Settings.

## Changes

### Phase 1 — HiddenByDuplicateHandling status

Added `HiddenByDuplicateHandling` to the `RecommendationSourceStatus` enum.

Extracted post-dedupe status correction into a pure internal function `adjustStatusesForDedupe()` in `RecommendationStatusAdjuster.kt`. This function accepts:
- `statuses: Map<Long, RecommendationSourceRunStatus>` — current per-source statuses after the For You batch loop
- `sourceHasVisibleResults: Map<Long, Boolean>` — whether each source has at least one card visible after cross-source display dedupe

Only sources with `status == Shown` are adjusted. If a Shown source has no visible post-dedupe results, its status is changed to `HiddenByDuplicateHandling` and `visibleCount` is zeroed. All other statuses are unchanged.

Called in `BrowsePersonalRecommendationsScreenModel.load()` after the batch loop completes. The deduped map is computed from `mutableState.value.dedupedItems()` and the `sourceHasVisible` map is derived from it. Adjusted statuses are persisted to `recommendationLastSourceRunStatuses` alongside the existing strategy persistence.

`RecommendationsSettingsScreen.kt` adds a `when` branch for `HiddenByDuplicateHandling` that maps to the `rec_source_status_duplicate_hidden` string.

### Phase 2 — Live settings freshness

`RecommendationsSettingsScreenModel` now subscribes to `lastSourceStatusesPref.changes()` in `init` using `collectLatest`. When `For You` finishes a run and writes the updated statuses, the settings screen model parses the new value and updates `sourceStatuses` in State without any user action required.

### Phase 3 — Status note wording

A new text item is rendered above the source priority list in `RecommendationsSettingsScreen` using the string `rec_source_status_note` ("Statuses below are from the last For You refresh."). This item has no action.

### Phase 4 — Top Picks polish

`TopPicksScreen` (added in v0.4.3) updated in v0.4.4:

- Added `isPartial: Boolean = false` constructor parameter.
- `TopPicksScreenModel.State` adds `isLoading: Boolean = true` — starts true until manga IDs are loaded from the DB.
- Content renders three states: loading spinner, empty state with `rec_top_picks_empty` string, or the manga grid.
- When `isPartial == true`, a full-width `rec_top_picks_partial` banner appears above the grid items.

`BrowsePersonalRecommendationsTab.kt` computes `isPartial = state.total > 0 && state.progress < state.total` and passes it to `TopPicksScreen`.

### Versioning

- `KmkRecsReleaseNotes.VERSION_CODE = 404`
- `KmkRecsReleaseNotes.VERSION_NAME = "KMK-Recs v0.4.4"`
- Release notes include v0.4.4 bullets and v0.4.3/v0.4.2 history.

## Files Changed

- `app/src/main/java/exh/recs/RecommendationSourceRunStatus.kt` — added `HiddenByDuplicateHandling` enum value
- `app/src/main/java/exh/recs/RecommendationStatusAdjuster.kt` (new) — pure `adjustStatusesForDedupe()` helper
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt` — post-dedupe adjustment after batch loop
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt` — `changes()` live subscription in init
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt` — `rec_source_status_note` item + `HiddenByDuplicateHandling` case
- `app/src/main/java/exh/recs/TopPicksScreen.kt` — `isPartial` param, loading state, empty state, partial banner
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsTab.kt` — `isPartial` passed to `TopPicksScreen`
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` — VERSION_CODE=404
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` — 4 new strings: `rec_source_status_duplicate_hidden`, `rec_source_status_note`, `rec_top_picks_partial`, `rec_top_picks_empty`
- `app/src/test/java/exh/recs/RecommendationSourceRunStatusStoreTest.kt` — added `HiddenByDuplicateHandling` round-trip test (10 tests total)
- `app/src/test/java/exh/recs/AdjustStatusesForDedupeTest.kt` (new) — 9 tests

## Tests Run

- `exh.recs.RecommendationSourceRunStatusStoreTest` — 10 tests, all PASSED
- `exh.recs.AdjustStatusesForDedupeTest` — 9 tests, all PASSED
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.4.4-debug.apk`
