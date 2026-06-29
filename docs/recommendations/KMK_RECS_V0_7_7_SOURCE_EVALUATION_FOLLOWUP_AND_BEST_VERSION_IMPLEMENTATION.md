# KMK-Recs v0.7.7: Source Evaluation Follow-Up and Best-Version Implementation

Date: 2026-06-22

Status: implemented.

## Overview

v0.7.7 is a follow-up pass on the Source Evaluation screen, fixing five issues found during tablet testing of v0.7.6. No new database schema. No new interactors. All fixes are in the screen layer, the screen model, and a new pure helper.

## Background: What v0.7.6 Added

v0.7.6 shipped three features on the Source Evaluation screen:

1. **Batch continuation** — "Continue next batch (N remaining)" button appears after a completed batch so the user can evaluate the next slice without restarting.
2. **Hide/Show installed toggle** — Past evaluation results hide extensions whose package is currently installed by default. "Show installed" reveals them. "Hidden installed: N" shows the count.
3. **Second-stage recommendation-quality probe** — After a source evaluation batch, `SourceRecommendationFitProbe` runs a bounded 2-query probe for STRONG_FIT and WORTH_TRYING sources only. Results are scored by `SourceRecommendationFitScorer` and stored in the `source_recommendation_fit` table via `UpsertSourceRecommendationFit`. Each past result row shows a third line: "Recommendations: Great", "Recommendations: Mixed", etc.

Key new files added in v0.7.6:

- `exh/recs/evaluation/SourceEvaluationDisplayFilter.kt` — pure display-only filter returning `visible: List<SourceEvaluation>` and `hiddenInstalledCount: Int`
- `exh/recs/evaluation/SourceRecommendationFitProbe.kt` — bounded probe with 30s timeout per plan
- `tachiyomi/domain/taste/model/SourceRecommendationFit.kt` — domain model
- `tachiyomi/domain/taste/model/RecommendationQualityVerdict.kt` — enum: GREAT, GOOD, MIXED, WEAK, NO_MATCHES, ERROR, TOO_LITTLE_EVIDENCE
- `tachiyomi/domain/taste/interactor/GetSourceRecommendationFit.kt`
- `tachiyomi/domain/taste/interactor/UpsertSourceRecommendationFit.kt`
- `exh/recs/evaluation/SourceEvaluationDisplayFilterTest.kt` — 7 tests (plus 3 new tests added in v0.7.7)

Pre-existing helpers reused by v0.7.6's probe:

- `SourceRecommendationFitEligibility` (v0.7.4)
- `SourceRecommendationFitScorer` (v0.7.4)

## Five Fixes Required by v0.7.7

### Fix 1: Show/Hide Installed Toggle Always Reversible

**Problem:** When installed rows are visible (`showInstalled=true`), `hiddenInstalledCount` is 0, so the old condition `hiddenInstalledCount > 0 || !state.showInstalled` evaluated to `false`, hiding the chip entirely. The user had no way to hide installed rows again without leaving and reopening the screen.

**Root cause:** The visibility condition conflated "are there hidden rows to show?" with "should the chip appear?"

**Fix in `SourceEvaluationScreen.kt`:**

```kotlin
// Before (v0.7.6 bug)
if (state.hiddenInstalledCount > 0 || !state.showInstalled)

// After (v0.7.7 fix)
if (state.hiddenInstalledCount > 0 || state.showInstalled)
```

The chip is shown whenever any installed extensions are hidden OR when installed rows are currently visible (so the user can hide them again).

**Dynamic chip label:**

```kotlin
FilterChip(
    selected = state.showInstalled,
    onClick = { screenModel.setShowInstalled(!state.showInstalled) },
    label = {
        Text(
            if (state.showInstalled) {
                stringResource(KMR.strings.source_evaluation_hide_installed)
            } else {
                stringResource(KMR.strings.source_evaluation_show_installed)
            },
        )
    },
)
```

New string added to `i18n-kmk/strings.xml`: `source_evaluation_hide_installed` = "Hide installed".

### Fix 2: Visible Recommendation Quality Section for Promising Sources

**Problem:** Users with STRONG_FIT or WORTH_TRYING sources in their past evaluations had no visible workflow to trigger the recommendation-quality probe. The probe only ran as a post-batch side effect. After the batch completed, there was no button to run it for newly visible promising sources or to re-run it after taste profile changes.

**Solution:** A new `Recommendation Quality` section is rendered inside the lazy list before the evaluation sort header. It appears whenever `recQualityQueue.totalPromising > 0`.

Section content:

- Title: "Recommendation Quality" (bold `titleSmall`)
- When running: "Checking recommendation quality… (N/M)" progress text
- When idle with missing sources: "N promising source(s) not yet checked" + "Evaluate recommendations" `OutlinedButton`
- When idle with already-checked sources: "Re-check all" `TextButton`

New strings added to `i18n-kmk/strings.xml`:

- `source_evaluation_rec_quality_section_title` = "Recommendation Quality"
- `source_evaluation_rec_quality_missing` = "%1$d promising source(s) not yet checked"
- `source_evaluation_rec_quality_evaluate` = "Evaluate recommendations"
- `source_evaluation_rec_quality_recheck_all` = "Re-check all"
- `source_evaluation_rec_quality_running` = "Checking recommendation quality… (%1$d/%2$d)"

### Fix 3: Source Fit Score and Recommendation Quality Stay Separate

This was already correct in v0.7.6. The source fit score (`qualityScore`/`recommendationFitScore` in `SourceEvaluation`) is computed during source evaluation and reflects how well the source matches the taste profile. The recommendation quality result (`SourceRecommendationFit.verdict`) is a separate second-stage probe that only runs for promising sources and reflects how well the source's actual recommendations score.

No code changes in v0.7.7 for this point. The separation is preserved by design.

### Fix 4: "Not Checked" Label for Promising Rows Without a Result

**Problem:** Promising rows without a `SourceRecommendationFit` record showed nothing on the third line. Users had no indication that a quality check was available but hadn't run yet.

**Fix in `SourceEvaluationScreen.kt` (`EvaluationResultRow`):**

```kotlin
val isPromising = evaluation.verdict == SourceEvaluationVerdict.STRONG_FIT ||
    evaluation.verdict == SourceEvaluationVerdict.WORTH_TRYING
val recQualityLabel = if (recFit != null) {
    stringResource(
        KMR.strings.source_evaluation_rec_quality_label,
        stringResource(
            when (recFit.verdict) {
                RecommendationQualityVerdict.GREAT -> KMR.strings.source_evaluation_rec_quality_great
                RecommendationQualityVerdict.GOOD  -> KMR.strings.source_evaluation_rec_quality_good
                // ... other verdicts
            },
        ),
    )
} else if (isPromising) {
    stringResource(
        KMR.strings.source_evaluation_rec_quality_label,
        stringResource(KMR.strings.source_evaluation_rec_quality_not_checked),
    )
} else {
    null
}
```

New string: `source_evaluation_rec_quality_not_checked` = "Not checked".

Non-promising rows (REJECTED, EXPLICIT_HEAVY, WEAK, etc.) still show nothing on the third line.

### Fix 5: Reuse Existing Bounded Probe Logic

The `evaluateRecommendationQualityForPromising` action in `SourceEvaluationScreenModel` reuses all existing infrastructure:

- `SourceRecommendationFitEligibility` — gates STRONG_FIT/WORTH_TRYING + MIN_SAMPLE_COUNT check
- `SourceRecommendationFitProbe` — runs the bounded 2-plan probe with 30s timeout per plan
- `SourceRecommendationFitScorer` — scores the probe `Outcome` to `[0.0, 1.0]`
- `GetSourceRecommendationFit` — read path for loading existing fits into screen state
- `UpsertSourceRecommendationFit` — write path for storing new probe results

No new interactors. No new DB tables. No schema changes.

## New Pure Helper: SourceRecommendationQualityQueue

`app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityQueue.kt`

A stateless object that partitions evaluations into three buckets:

```kotlin
object SourceRecommendationQualityQueue {

    data class QueueResult(
        val missingPromising: List<SourceEvaluation>,   // promising, no fit stored
        val checkedPromising: List<SourceEvaluation>,   // promising, fit stored
        val ineligible: List<SourceEvaluation>,         // not promising
    ) {
        val missingCount: Int get() = missingPromising.size
        val totalPromising: Int get() = missingPromising.size + checkedPromising.size
    }

    fun compute(
        evaluations: List<SourceEvaluation>,
        fitsByEvalKey: Map<String, SourceRecommendationFit>,
    ): QueueResult
}
```

Rules:

- Promising verdicts: `STRONG_FIT` and `WORTH_TRYING` only.
- Missing = promising AND `fitsByEvalKey[evaluation.evaluationKey] == null`.
- Checked = promising AND a fit record exists.
- Ineligible = not promising (shown in list but no quality section interaction).

The screen model computes `evaluateRecommendationQualityForPromising(reCheckAll: Boolean)`:

- `reCheckAll = false` → targets `missingPromising` only.
- `reCheckAll = true` → targets `missingPromising + checkedPromising`.

## ScreenModel Changes

### New Constructor Params (v0.7.7)

```kotlin
private val upsertSourceRecommendationFit: UpsertSourceRecommendationFit = Injekt.get(),
private val getTagAliases: GetTagAliases = Injekt.get(),
```

### New State Fields (v0.7.7)

```kotlin
val recQualityRunning: Boolean = false,
val recQualityProgress: Int = 0,
val recQualityTotal: Int = 0,
```

### New Action: evaluateRecommendationQualityForPromising

Runs an inline coroutine in `screenModelScope`. For each target evaluation:

1. Finds the installed `CatalogueSource` via `extensionManager.installedExtensionsFlow.value`.
2. If source not found, writes an ERROR fit and continues.
3. Otherwise, calls `SourceRecommendationFitProbe(getTagAliases).probe(source, tasteProfile)`.
4. Scores via `SourceRecommendationFitScorer.score(outcome.toScorerOutcome())`.
5. Persists `SourceRecommendationFit` via `upsertSourceRecommendationFit.await(fit)`.
6. Updates `recQualityProgress` after each source.

Error handling:

- `CancellationException` is always rethrown.
- All other exceptions: logs, writes an ERROR fit, continues to next source.
- `finally` block: calls `loadRecommendationFits()` and resets `recQualityRunning = false`.

### Composable Context Fix

`remember` requires a `@Composable` function context. `LazyListScope` is not `@Composable`. The `recQualityQueue` value is computed **outside** the `LazyColumn`, at the same level as `sortedEvaluations`:

```kotlin
val sortedEvaluations = remember(state.evaluations, state.resultSortMode) { ... }
// KMK --> v0.7.7: must be outside LazyColumn (remember is @Composable)
val recQualityQueue = remember(state.evaluations, state.recommendationFitsByEvalKey) {
    SourceRecommendationQualityQueue.compute(state.evaluations, state.recommendationFitsByEvalKey)
}
// KMK <--

LazyColumn {
    // uses recQualityQueue without remember
}
```

## Files Changed

### New Files

| File | Description |
| --- | --- |
| `app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityQueue.kt` | Pure queue helper — partitions evaluations into missing/checked/ineligible |
| `app/src/test/java/exh/recs/evaluation/SourceRecommendationQualityQueueTest.kt` | 8 unit tests for queue computation |

### Modified Files

| File | Changes |
| --- | --- |
| `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` | +7 new strings (Hide installed, section title, missing count, evaluate, re-check all, not checked, running progress) |
| `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt` | +2 constructor params, +3 state fields, +`evaluateRecommendationQualityForPromising()`, +`buildRecQualityErrorFit()` |
| `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt` | Fix toggle condition, dynamic chip label, add rec-quality section item, add "Not checked" label, move `remember` outside `LazyColumn` |
| `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` | VERSION_CODE=770, VERSION_NAME="KMK-Recs v0.7.7", v0.7.7 What's New entry |
| `app/src/test/java/exh/recs/evaluation/SourceEvaluationDisplayFilterTest.kt` | +3 toggle behavior tests (v0.7.7 section) |

## Tests

### New Tests (SourceRecommendationQualityQueueTest — 8 tests)

1. `promising sources without fit are counted as missing`
2. `promising sources with fit are not counted as missing`
3. `non-promising sources are excluded from promising buckets`
4. `action targets only missing promising rows`
5. `re-check-all mode targets all promising including already checked`
6. `totalPromising is sum of missing and checked`
7. `empty evaluations returns empty result`
8. `mixed evaluations partitioned correctly`

### Added to Existing Test File (SourceEvaluationDisplayFilterTest — 3 new tests in v0.7.7 section)

1. `toggling showInstalled true then false hides installed rows immediately`
2. `hiddenInstalledCount is zero when showInstalled is true`
3. `hiddenInstalledCount updates correctly when showInstalled changes`

### Test Results

```
SourceRecommendationQualityQueueTest — 8 tests, all PASSED
SourceEvaluationDisplayFilterTest — 10 tests, all PASSED
:app:testDebugUnitTest — BUILD SUCCESSFUL
:app:assembleDebug — BUILD SUCCESSFUL
```

## APK

```
Komikku-v1.13.6-kmk.7.7-debug.apk
VERSION_CODE = 770
VERSION_NAME = KMK-Recs v0.7.7
```

## What Was Not Changed

- No new database tables or migrations.
- `SourceRecommendationFitEligibility`, `SourceRecommendationFitScorer`, `SourceRecommendationFitProbe`, `GetSourceRecommendationFit`, `UpsertSourceRecommendationFit` — unchanged; reused as-is.
- Batch continuation behavior from v0.7.6 — unchanged.
- `SourceEvaluationDisplayFilter` pure helper — unchanged; the bug was in the screen's condition that controlled chip visibility, not in the filter itself.
- v0.7.6 rec-quality probe logic running after a batch — unchanged; v0.7.7 adds an additional on-demand trigger from the screen.
