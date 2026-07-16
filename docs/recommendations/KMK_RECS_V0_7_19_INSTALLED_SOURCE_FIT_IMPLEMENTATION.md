# KMK-Recs v0.7.19 â€” Installed Source Fit

Date: 2026-06-28

Status: implemented and tested.

## Summary

Phase 6 of the deferred feature plan. Upgrades the source fit badge from single-run status to rolling multi-run stats, and adds a "Suggest priority order based on fit" button in Recommendation Settings.

---

## What Changed

### Before v0.7.19

The source fit badge in the Source Priority list showed the LAST For You run's status only: Great fit (â‰¥5 visible), Good fit (â‰¥2), Low fit (â‰¥1), No matches, Often filtered, Often errors, Deduplicated. One bad or one good run could flip the label completely.

### After v0.7.19

- Rolling stats accumulated across all For You runs: run count, shown count, no-match count, filtered count, error count, hidden-by-duplicate count, total visible candidates, timestamps.
- Fit labels computed from rolling history after â‰¥3 runs: Great fit, Good fit, Mixed, No matches, Often filtered, Often errors. Sources with fewer than 3 runs fall back to single-run status badge (existing behavior).
- A "Suggest priority order based on fit" button appears when â‰¥3 sources have â‰¥3 run history. Clicking it moves highest-fit sources to the top; sources with no data stay at the bottom. The user can reorder further after applying.

---

## Fit Label Algorithm

```
runCount < 3 â†’ TooLittleData (no badge; falls back to single-run if available)
errorRate > 0.6 â†’ OftenErrors
shownRate > 0.5 && avgVisible â‰¥ 3.0 â†’ GreatFit
shownRate > 0.3 â†’ GoodFit
noMatchRate > 0.5 â†’ NoMatchesRecently
filteredRate > 0.4 â†’ OftenFiltered
else â†’ Mixed
```

where:
- `errorRate = errorCount / runCount`
- `shownRate = shownCount / runCount`
- `avgVisible = totalVisibleCandidates / max(1, shownCount)`

---

## Files Changed

### New

- `app/src/main/java/exh/recs/SourceFitStats.kt` â€” `SourceFitStats` data class (rolling stats + `fitLabel` computed property + `merge()`), `SourceFitLabel` enum (with `fitScore` for ordering), `SourceFitStatsStore` object (serialize/parse/mergeRun)

### Modified

- `app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt` â€” added `recommendationSourceFitStats()` preference
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt` â€” merges `allStatuses` into rolling fit stats after each run
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt` â€” loads `sourceFitStats`, live-updates on changes, `applyFitSuggestedOrder()` action, `suggestFitOrderAvailable` computed state property
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt` â€” `SourcePriorityItem` accepts `fitStats` parameter; badge uses rolling label when available; "Suggest priority order" button + explanatory note
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` â€” 4 new strings: `rec_source_fit_mixed`, `rec_source_fit_too_little_data`, `rec_suggest_source_order_button`, `rec_suggest_source_order_note`
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` â€” v0.7.19 entry

---

## Suggest Order Logic

Sources with `runCount >= 3` are sorted by `fitLabel.fitScore` descending:
```
GreatFit(6) > GoodFit(5) > Mixed(3) > NoMatchesRecently(2) > OftenFiltered(1) > OftenErrors(0)
```
Within the same fit score, existing priority order is preserved (stable sort via `indexOf`). Sources with insufficient data (`runCount < 3`) are appended at the bottom in their current order.

The button only appears when `â‰¥3` sources have `â‰¥3` runs. Applies via the existing `setSourceOrder()` action which also persists the new order.

---

## Rollup Behavior

After each For You run, `SourceFitStatsStore.mergeRun()` is called with the finalized (post-dedupe-adjusted) statuses. Disabled and OutsideAttemptLimit statuses are skipped â€” only sources that were actually searched contribute to the rolling count.

The updated stats are written to `recommendationSourceFitStats` preference. `RecommendationsSettingsScreenModel` subscribes to `sourceFitStatsPref.changes()` and updates state reactively so the badges refresh as soon as a run completes.

---

## Test Results

```
BUILD SUCCESSFUL
:app:testDebugUnitTest â€” all existing tests PASSED
:app:assembleDebug â€” BUILD SUCCESSFUL
```

## APK Naming

`Komikku-v1.13.6-kmk.7.19-debug.apk`

