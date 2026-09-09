# KMK-Recs v0.6.15: Source Evaluation Results Stability and Sort Plan

Date: 2026-06-19

Feature version: KMK-Recs v0.6.15

Status: planning. Do not implement until approved by the user.

## User Report

The Source Evaluation workflow is now useful with the Private installer:

- batch evaluation runs;
- evaluated sources are categorized as strong fit / weak fit / errors;
- the data seems meaningful enough to guide source discovery.

The current problem is stability after a larger run:

- the user ran a batch of 50;
- the batch finished;
- afterward, opening or interacting with the Source Evaluation page can crash the app;
- Android shows the generic message: "Komikku closed because this app has a bug. Try clearing the app's cache first and then reopen the app."

The user also noted that a sort feature would be useful for the evaluated results.

## Important Scope Boundary

This is not a new source-scoring algorithm pass.

This patch should focus on:

1. making the Source Evaluation screen safe after large result sets;
2. making stored evaluation results deterministic and sortable;
3. preventing one malformed or unexpected stored evaluation row from crashing the whole screen;
4. preserving the current Private installer and v0.6.14 timeout/cancellation behavior.

Do not change:

- For You ranking logic;
- Sources To Try scoring logic;
- source evaluation probe strategy;
- source evaluation installer mode semantics;
- source preference like/dislike semantics.

## Current Code Findings

### Source Evaluation Screen

File:

`app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt`

The past evaluations section currently renders every stored evaluation directly:

```kotlin
items(
    items = state.evaluations,
    key = { it.evaluationKey },
) { evaluation ->
    EvaluationResultRow(
        evaluation = evaluation,
        modifier = Modifier.animateItem(),
    )
}
```

The row currently shows only:

- `evaluation.sourceName`;
- `evaluation.extensionName`;
- `evaluation.verdict`.

There is no:

- sort selector;
- filter selector;
- defensive row sanitization;
- fallback key if `evaluationKey` is empty or otherwise unsafe;
- explicit order at the SQL query level.

### Source Evaluation Screen Model

File:

`app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt`

The model subscribes to stored evaluations:

```kotlin
getSourceEvaluations.subscribeAll()
    .catch { e ->
        logcat(LogPriority.ERROR, e) { "source_evaluation table unavailable in SourceEvaluationScreen" }
        emit(emptyList())
    }
    .onEach { evals ->
        mutableState.update { it.copy(evaluations = evals) }
    }
    .launchIn(screenModelScope)
```

This protects against flow/query failure, but it does not protect the composable from bad values after the list is emitted.

### Repository and SQL

Files:

- `data/src/main/java/tachiyomi/data/taste/SourceEvaluationRepositoryImpl.kt`
- `data/src/main/sqldelight/tachiyomi/data/source_evaluation.sq`

The table has `evaluation_key TEXT NOT NULL PRIMARY KEY`, so SQLite should prevent duplicate keys in normal operation.

However, `getAll` and `getAllAsFlow` currently have no explicit order:

```sql
getAll:
SELECT *
FROM source_evaluation;

getAllAsFlow:
SELECT *
FROM source_evaluation;
```

Without an `ORDER BY`, SQLite is free to return rows in any physical order. That is not ideal for Compose lists, especially once rows are updated during or after a batch.

### Current Crash Log Caveat

The local crash log file available during inspection:

`C:\Users\USER\Downloads\extracted_komikku_crash_logs.txt`

does not contain the new post-50-evaluation crash. It contains older already-addressed issues:

- source priority reorder `IndexOutOfBoundsException`;
- missing `source_evaluation` table;
- missing `GetNonInstalledSourceSuggestions` Injekt registration.

Therefore, this plan includes a required verification step to capture the exact current crash after implementation if it still occurs.

## Likely Failure Modes

Because the new crash happens after a larger result set, the most likely categories are:

1. **Compose list key instability**
   - The UI uses raw `evaluationKey` as the `LazyColumn` key.
   - The database should enforce uniqueness, but the UI should still protect itself from blank or malformed keys.

2. **Unstable ordering during recomposition**
   - `getAllAsFlow` has no `ORDER BY`.
   - Rows may be updated while the screen is open, and Compose can animate item movement using `animateItem()`.
   - A deterministic order reduces the chance of list identity churn.

3. **Unexpected row values from extension metadata**
   - Source and extension names come from external extension metadata.
   - Empty, very long, odd, or malformed values should not crash or break layout.

4. **Large unfiltered result list**
   - A 50-extension batch can create more than 50 source rows, because one extension can expose multiple catalogue sources.
   - The UI currently dumps all rows directly into one list.

5. **Old/stale row shape or corrupted local data**
   - The evaluation table is recoverable cache data.
   - If any row is bad, the screen should skip or quarantine it instead of crashing.

## Implementation Goals

### Goal 1: Make Result Ordering Deterministic

Add explicit ordering to SQLDelight queries.

Preferred default order:

1. useful verdicts first;
2. higher recommendation fit score first;
3. higher quality/search score as tie-breakers;
4. newest evaluated records next;
5. source/extension name as final stable tie-breakers.

Because SQL ordering by custom enum rank is awkward, use one of these approaches:

Option A, SQL-level basic order:

```sql
ORDER BY evaluated_at DESC, recommendation_fit_score DESC, quality_score DESC, source_name COLLATE NOCASE ASC
```

Then apply richer sorting in Kotlin.

Option B, Kotlin-only order:

Leave SQL broad but sort before storing in screen state. This is easier to test and more flexible.

Recommended path:

- Add `ORDER BY evaluated_at DESC` to `getAll` and `getAllAsFlow` as a baseline.
- Add a pure Kotlin sorter for all UI modes.

### Goal 2: Add a Pure Result Sanitizer and Sorter

Create a pure helper near the evaluation feature:

`app/src/main/java/exh/recs/evaluation/SourceEvaluationResultList.kt`

Suggested contents:

```kotlin
object SourceEvaluationResultList {
    enum class SortMode {
        BEST_FIT,
        NEWEST,
        SOURCE_NAME,
        EXTENSION_NAME,
        SEARCH_RELIABILITY,
        EXPLICIT_RISK,
    }

    fun sanitize(evaluations: List<SourceEvaluation>): List<SourceEvaluation>

    fun sort(
        evaluations: List<SourceEvaluation>,
        mode: SortMode,
    ): List<SourceEvaluation>

    fun stableUiKey(
        evaluation: SourceEvaluation,
        index: Int,
    ): String
}
```

Keep this helper pure so it can be unit-tested without Android.

#### Sanitizer Requirements

`sanitize()` should:

- drop rows with a blank `evaluationKey`;
- drop exact duplicate keys defensively with `distinctBy { it.evaluationKey }`;
- optionally drop rows with blank `extensionPkgName` or `signatureHash` if such rows are impossible by schema and indicate corruption;
- keep rows with blank `sourceName` or `extensionName`, but UI should show a safe fallback label;
- never throw.

#### Stable UI Key Requirements

`stableUiKey()` should:

- use `evaluation.evaluationKey` when non-blank;
- include a prefix, e.g. `"source-evaluation-${evaluation.evaluationKey}"`;
- fallback to a composite string only if needed:

```kotlin
"source-evaluation-fallback-${evaluation.extensionPkgName}-${evaluation.signatureHash}-${evaluation.sourceId ?: "no-source"}-$index"
```

The fallback is not expected in valid data, but it prevents a single malformed row from crashing Compose.

### Goal 3: Add Sort Controls to the Source Evaluation Screen

The user specifically said a sort feature would be good.

Add sort controls above the past evaluations list, near the "Past results" header.

Recommended modes:

- `Best fit`
- `Newest`
- `Source name`
- `Extension name`
- `Search reliability`
- `Explicit risk`

Default:

- `Best fit`

The default must prioritize source usefulness:

1. `STRONG_FIT`
2. `WORTH_TRYING`
3. `NEUTRAL`
4. `NEEDS_MANUAL_REVIEW`
5. `WEAK`
6. `POOR_SEARCH`
7. `ECCHI_HEAVY`
8. `EXPLICIT_HEAVY`
9. `REJECTED`
10. `ERROR`

Within each verdict group:

1. `recommendationFitScore` descending;
2. `qualityScore` descending;
3. `searchReliabilityScore` descending;
4. `evaluatedAt` descending;
5. `sourceName` case-insensitive ascending;
6. `extensionName` case-insensitive ascending.

### Goal 4: Show More Diagnostic Data Per Result

To make the sort useful and reduce confusion, enhance `EvaluationResultRow` slightly.

Do not make it visually heavy. The row should remain compact.

Suggested subtitle:

```text
Extension Name â€¢ EN â€¢ fit 82% â€¢ search 70%
```

If the result is an error:

```text
Extension Name â€¢ Error: <short message>
```

Also consider showing:

- `sampleCount`;
- `searchSuccessCount / searchCount`;
- `explicitScore` only when high or when sorting by explicit risk.

Long error messages must be truncated or ellipsized. Do not render raw huge text in full.

### Goal 5: Make the Screen Fail Open

The screen should not crash if a row is malformed.

Implementation options:

1. Prefer data normalization in `SourceEvaluationScreenModel`:

```kotlin
.onEach { evals ->
    mutableState.update {
        it.copy(
            evaluations = SourceEvaluationResultList.sanitize(evals),
        )
    }
}
```

2. Apply sort/sanitize at render time using `remember`.

Recommended:

- sanitize in the screen model;
- sort in the screen/model based on selected `SortMode`;
- use stable fallback keys in UI.

Do not catch broad exceptions inside every composable row unless there is no cleaner option. Compose row try/catch tends to hide real bugs and is harder to reason about.

### Goal 6: Preserve Evaluation Data but Add Recovery Actions

Source evaluation data is rebuildable cache. If bad data causes trouble, the user needs safe controls.

Keep existing `Clear all` action.

Add or improve confirmation if needed:

- "Clear all evaluation results?"
- Explain this only clears source-evaluation cache, not installed extensions, library, ratings, or For You preferences.

If the existing `clearAllEvaluations` action has no confirmation, add confirmation in this patch because the button sits next to a now more important results list.

### Goal 7: Versioning and Documentation

This remains extension/source-evaluation work, so continue the `0.6.x` line.

Use:

- `KMK-Recs v0.6.15`
- `VERSION_CODE = 615`
- APK name pattern:
  - `Komikku-v1.13.6-kmk.6.15-debug.apk`

Update:

- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`
- `RECOMMENDATION_VERSIONING.md`
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/README.md`

What's New should include only user-facing changes, for example:

```markdown
## KMK-Recs v0.6.15

- Fixed Source Evaluation results stability after larger evaluation batches.
- Added sorting for Source Evaluation results, including Best fit, Newest, Source name, and Search reliability.
- Improved Source Evaluation result rows with compact score details.
```

Do not mention internal planning docs in What's New.

## Detailed Implementation Steps

### Step 1: Add Result List Helper

Create:

`app/src/main/java/exh/recs/evaluation/SourceEvaluationResultList.kt`

Add:

- `SortMode`;
- `sanitize`;
- `sort`;
- `stableUiKey`;
- private verdict rank helper.

Keep it Android-free.

### Step 2: Add Unit Tests

Create:

`app/src/test/java/exh/recs/evaluation/SourceEvaluationResultListTest.kt`

Test:

1. blank `evaluationKey` row is removed;
2. duplicate `evaluationKey` rows are deduped;
3. Best fit sort uses verdict rank before score;
4. Best fit sort uses score tie-breakers;
5. Newest sort orders by `evaluatedAt DESC`;
6. Source name sort is case-insensitive;
7. Explicit risk sort places high explicit scores first;
8. `stableUiKey` never returns blank.

### Step 3: Add Sort State to Screen Model

In:

`app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt`

Add to `State`:

```kotlin
val resultSortMode: SourceEvaluationResultList.SortMode = SourceEvaluationResultList.SortMode.BEST_FIT
```

Add:

```kotlin
fun setResultSortMode(mode: SourceEvaluationResultList.SortMode)
```

When evaluations are emitted:

```kotlin
val safe = SourceEvaluationResultList.sanitize(evals)
mutableState.update { it.copy(evaluations = safe) }
```

Do not store sorted results permanently unless simple. It is acceptable for UI to sort from `state.evaluations` and `state.resultSortMode`.

### Step 4: Render Sorted Safe List

In:

`app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt`

Compute:

```kotlin
val sortedEvaluations = remember(state.evaluations, state.resultSortMode) {
    SourceEvaluationResultList.sort(state.evaluations, state.resultSortMode)
}
```

Use:

```kotlin
itemsIndexed(
    items = sortedEvaluations,
    key = { index, evaluation ->
        SourceEvaluationResultList.stableUiKey(evaluation, index)
    },
)
```

If `animateItem()` appears implicated in crash after sorting/reordering, remove it from past evaluation rows. Animation is nice but not necessary for this list. Stability matters more.

### Step 5: Add Sort UI

Add a small row under the past-results header:

- label: "Sort"
- chips or compact dropdown:
  - Best fit
  - Newest
  - Source
  - Extension
  - Search
  - Explicit

If chips are too wide, use a dropdown menu to avoid cramped layout.

Recommended for phone/tablet:

- use a compact `OutlinedButton`/dropdown;
- avoid a long horizontal chip row that can overflow.

### Step 6: Improve Result Row Text

Update `EvaluationResultRow`:

- fallback `sourceName.ifBlank { "Unknown source" }`;
- fallback `extensionName.ifBlank { "Unknown extension" }`;
- show compact scores;
- cap error message length.

Suggested helper:

```kotlin
private fun Double.percentText(): String =
    "${(this.coerceIn(0.0, 1.0) * 100).roundToInt()}%"
```

Clamp score values before display. External/generated rows should not be able to produce weird percentages.

### Step 7: Add Clear Confirmation

Add state:

```kotlin
val showClearEvaluationsDialog: Boolean = false
```

Add methods:

- `requestClearAllEvaluations()`
- `dismissClearAllEvaluations()`
- `confirmClearAllEvaluations()`

Wire the existing `Clear all` button to request confirmation instead of immediate deletion.

### Step 8: Optional SQL Ordering

Update:

`data/src/main/sqldelight/tachiyomi/data/source_evaluation.sq`

Change:

```sql
getAll:
SELECT *
FROM source_evaluation
ORDER BY evaluated_at DESC;

getAllAsFlow:
SELECT *
FROM source_evaluation
ORDER BY evaluated_at DESC;
```

This does not require a new migration because it changes generated query code, not schema.

### Step 9: Verify Existing Crash Fixes Remain Intact

Make sure v0.6.14 behavior is preserved:

- one slow source times out locally;
- batch does not become Cancelled unless the user taps Cancel;
- `completedCount` progresses for handled failures;
- Private remains recommended.

### Step 10: Documentation

Create implementation report after coding:

`docs/recommendations/KMK_RECS_V0_6_15_SOURCE_EVALUATION_RESULTS_STABILITY_AND_SORT_IMPLEMENTATION.md`

Update index/current-state/version docs listed above.

## Testing Plan

Run:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Manual tablet verification:

1. Install/update to v0.6.15 debug APK.
2. Open Recommendation Settings > Source Evaluation.
3. Confirm the screen opens with old evaluation data.
4. Run a Private batch of 50.
5. Confirm batch completes.
6. Leave and reopen Source Evaluation.
7. Scroll through all past results.
8. Change sort to each mode.
9. Tap/scroll around rows.
10. Confirm no crash.
11. Confirm `Clear all` asks for confirmation.
12. Confirm clearing results does not affect ratings, library, installed extensions, source preferences, or For You settings.

If the app still crashes, immediately collect a fresh crash log from that v0.6.15 build. The old `extracted_komikku_crash_logs.txt` does not contain the new post-50-evaluation crash.

## Recommendation

Proceed with this as a focused stability-and-usability patch.

The Source Evaluation engine itself appears to be useful now. The weak point is the results screen: it is currently too trusting for a cache built from many third-party extension probes. A small pure sanitizer/sorter plus stable UI keys and deterministic ordering should make the page much harder to crash and much easier to use.

