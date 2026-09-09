# KMK-Recs v0.6.15: Source Evaluation Results Stability and Sort Implementation

Date: 2026-06-20

Feature version: KMK-Recs v0.6.15

## Summary

Fixed a crash that could occur after opening or interacting with Source Evaluation after a larger batch (50+ extensions). Added sorting controls for past evaluation results. Improved result row information.

## Root Causes Addressed

1. **Compose list key instability**: Raw `evaluationKey` was used directly as the `LazyColumn` key without sanitization. Blank or duplicate keys cause undefined list diffing behavior.
2. **Recomposition churn from `animateItem()`**: `animateItem()` on a list that may reorder or update during/after a batch adds unnecessary recomposition risk.
3. **Unstable SQL ordering**: `getAll` and `getAllAsFlow` had no `ORDER BY`, so rows could arrive in any physical order per SQLite page layout, changing on upsert.
4. **No row-level sanitization**: The screen was trusting every row from the flow; one bad row could affect the whole list.

## Changes

### New File: `app/src/main/java/exh/recs/evaluation/SourceEvaluationResultList.kt`

Pure Android-free helper object (created in the previous session step). Contains:

- `SortMode` enum: `BEST_FIT`, `NEWEST`, `SOURCE_NAME`, `EXTENSION_NAME`, `SEARCH_RELIABILITY`, `EXPLICIT_RISK`.
- `sanitize()`: drops rows with blank `evaluationKey`, deduplicates by `evaluationKey`, never throws.
- `sort()`: applies the selected `SortMode` using stable pre-built `Comparator` values.
- `stableUiKey()`: returns `"source-evaluation-$key"` for valid keys; fallback composite string for malformed rows.
- `verdictRank()` (internal): maps `SourceEvaluationVerdict` to integer rank 0 (STRONG_FIT) through 9 (ERROR) for BEST_FIT sorting.

### New File: `app/src/test/java/exh/recs/evaluation/SourceEvaluationResultListTest.kt`

13 unit tests (JUnit 5) covering:

- blank `evaluationKey` row removed by `sanitize()`
- duplicate `evaluationKey` rows deduped, first kept
- empty list passthrough
- BEST_FIT sort uses verdict rank before score
- BEST_FIT sort uses `recommendationFitScore` as tie-breaker
- all 10 verdict ranks are distinct, STRONG_FIT = 0, ERROR = 9
- NEWEST sort orders by `evaluatedAt` descending
- SOURCE_NAME sort is case-insensitive
- EXTENSION_NAME sort is case-insensitive
- EXPLICIT_RISK sort places high `explicitScore` first
- `stableUiKey` returns non-blank for valid key and contains key value
- `stableUiKey` fallback for blank key includes pkgName, signatureHash, sourceId, index
- `stableUiKey` is never null or empty

### Modified: `data/src/main/sqldelight/tachiyomi/data/source_evaluation.sq`

Added `ORDER BY evaluated_at DESC` to `getAll` and `getAllAsFlow`. No schema change; no migration needed.

### Modified: `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt`

State additions:

```kotlin
val resultSortMode: SourceEvaluationResultList.SortMode = SourceEvaluationResultList.SortMode.BEST_FIT,
val showClearEvaluationsDialog: Boolean = false,
```

`onEach` now sanitizes:

```kotlin
mutableState.update { it.copy(evaluations = SourceEvaluationResultList.sanitize(evals)) }
```

Replaced `clearAllEvaluations()` with:

- `requestClearAllEvaluations()` â€” sets dialog visible
- `dismissClearAllEvaluations()` â€” closes dialog
- `confirmClearAllEvaluations()` â€” closes dialog and runs `clearSourceEvaluations.await()`

Added `setResultSortMode(mode)` â€” updates `resultSortMode` in state.

### Modified: `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt`

New imports: `itemsIndexed`, `ArrowDropDown` (filled), `DropdownMenu`, `DropdownMenuItem`, `remember`, `mutableStateOf`, `setValue`, `TextOverflow`, `roundToInt`, `MR`.

Content() changes:

- `val sortedEvaluations = remember(state.evaluations, state.resultSortMode) { SourceEvaluationResultList.sort(...) }` computed before the `LazyColumn`.
- Clear confirmation `AlertDialog` shown when `state.showClearEvaluationsDialog`.
- Clear button changed to `screenModel::requestClearAllEvaluations`.
- Added `eval_sort` list item: `TextButton` showing current sort mode label + `ArrowDropDown` icon, backed by a `DropdownMenu` with 6 mode entries.
- Replaced `items(..., key = { it.evaluationKey }) { evaluation -> EvaluationResultRow(evaluation, Modifier.animateItem()) }` with `itemsIndexed(sortedEvaluations, key = { index, eval -> SourceEvaluationResultList.stableUiKey(eval, index) }) { _, evaluation -> EvaluationResultRow(evaluation) }`.
- `EvaluationResultRow` updated: defensive display values (`ifBlank { "Unknown source" / "Unknown extension" }`); compact subtitle showing extension name, lang, fit%, search% or truncated error message; `maxLines = 1` + `TextOverflow.Ellipsis` on both text fields.

### Modified: `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`

Added under `<!-- KMK v0.6.15 -->`:

- `source_evaluation_sort_label` = "Sort:"
- `source_evaluation_sort_best_fit` = "Best fit"
- `source_evaluation_sort_newest` = "Newest"
- `source_evaluation_sort_source_name` = "Source name"
- `source_evaluation_sort_extension_name` = "Extension name"
- `source_evaluation_sort_search_reliability` = "Search reliability"
- `source_evaluation_sort_explicit_risk` = "Explicit risk"
- `source_evaluation_clear_confirm_title` = "Clear all evaluation results?"
- `source_evaluation_clear_confirm_message` = "This clears cached source evaluation data only. Your library, ratings, preferences, and installed extensions are not affected."
- `source_evaluation_clear_confirm` = "Clear"

### Modified: `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`

- `VERSION_CODE = 615`
- `VERSION_NAME = "KMK-Recs v0.6.15"`
- Added v0.6.15 What's New entry.

## v0.6.14 Behavior Preserved

- `withTimeoutOrNull()` timeout resilience in `SourceEvaluationRunner` â€” unchanged.
- `completedCount` in `finally` block â€” unchanged.
- `requestResetSourceOrder()` / confirm / dismiss â€” unchanged.
- All existing Source Evaluation probe, installer, and cleanup logic â€” unchanged.

## Files Changed

### New Files

- `app/src/main/java/exh/recs/evaluation/SourceEvaluationResultList.kt`
- `app/src/test/java/exh/recs/evaluation/SourceEvaluationResultListTest.kt`
- `docs/recommendations/KMK_RECS_V0_6_15_SOURCE_EVALUATION_RESULTS_STABILITY_AND_SORT_IMPLEMENTATION.md` (this file)

### Modified Files

- `data/src/main/sqldelight/tachiyomi/data/source_evaluation.sq`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt`
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/README.md`
- `RECOMMENDATION_VERSIONING.md`

## Tests

- `exh.recs.evaluation.SourceEvaluationResultListTest` â€” 13 tests, all PASSED
- `:app:testDebugUnitTest` â€” BUILD SUCCESSFUL
- `:app:assembleDebug` â€” BUILD SUCCESSFUL

## APK

Generated: `app/build/outputs/apk/debug/app-universal-debug.apk`

Copied to: `Komikku-v1.13.6-kmk.6.15-debug.apk`

## Manual Verification Required

On-device:

1. Install v0.6.15 APK.
2. Open Recommendation Settings > Source Evaluation.
3. Confirm existing past results display correctly with compact subtitle (fit %, search %).
4. Try each sort mode â€” confirm list reorders appropriately.
5. Tap "Clear all" â€” confirm confirmation dialog appears. Tap Cancel â€” confirm results remain. Tap Clear â€” confirm list clears.
6. Run a batch of 10â€“25 with Private installer.
7. Confirm batch completes and past results appear.
8. Leave and re-open Source Evaluation â€” confirm no crash.
9. Scroll through all past results â€” confirm no crash.
10. Confirm v0.6.14 timeout behavior is unchanged: run with a known-slow source and verify batch completes (not Cancelled) with the slow source showing as Error.

