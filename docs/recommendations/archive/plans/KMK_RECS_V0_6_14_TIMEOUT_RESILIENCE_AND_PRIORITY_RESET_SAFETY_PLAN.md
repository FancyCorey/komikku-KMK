# KMK-Recs v0.6.14: Timeout Resilience And Priority Reset Safety Plan

Date: 2026-06-19

Feature version target: KMK-Recs v0.6.14

Status: implementation plan only. Do not modify application code until the user explicitly approves implementation.

## Goal

Fix two user-reported usability issues:

1. Source Evaluation can incorrectly cancel the entire batch when one extension/source hangs during a timed probe, such as an extension getting stuck at `ProbingPopular`.
2. The `Reset priority` action in Recommendation Settings is too easy to trigger accidentally and can destroy the user's carefully tuned source priority order.

This plan intentionally combines these because the user asked for the reset-priority safety fix to be included in the next implementation plan.

## Problem 1: Source Evaluation Timeout Cancels Whole Batch

### User-Observed Behavior

During private Source Evaluation, one source/extension, reported as `Asia2`, repeatedly gets stuck around `ProbingPopular`. After some time, the evaluation shows as `Cancelled` even though the user did not press Cancel.

### Likely Root Cause

File:

```text
app/src/main/java/exh/recs/evaluation/SourceEvaluationRunner.kt
```

The runner uses `withTimeout(...)` in several places:

```kotlin
val installSuccess = withTimeout(90_000L) {
    installAndWait(ext, installerOverride)
}
```

```kotlin
val installedExt = withTimeout(20_000L) {
    extensionManager.installedExtensionsFlow.first { ... }
}
```

```kotlin
val page = withTimeout(30_000L) { source.getPopularManga(1) }
```

```kotlin
val page = withTimeout(30_000L) { source.getLatestUpdates(1) }
```

```kotlin
val page = withTimeout(25_000L) {
    source.getSearchManga(1, query, source.getFilterList())
}
```

In Kotlin coroutines, `TimeoutCancellationException` is a subtype of `CancellationException`.

Current probe handling does this:

```kotlin
} catch (e: Exception) {
    if (e is CancellationException) throw e
    errorCount++
}
```

So a timeout inside `withTimeout` is treated like real cancellation and is rethrown. It bubbles up to:

```kotlin
} catch (e: CancellationException) {
    _state.update { it.copy(status = SourceEvaluationQueueState.Status.Cancelled) }
}
```

That makes one slow/broken source look like the user cancelled the entire batch.

### Correct Behavior

Timeouts caused by a source/extension being slow or broken should be local failures:

- popular probe timeout -> mark that source/probe as timeout/error and continue;
- latest probe timeout -> mark that source/probe as timeout/error and continue;
- search query timeout -> mark that search query as timeout/error and continue;
- extension install timeout -> mark that extension as failed and continue to next candidate;
- installed-extension load timeout -> mark that extension as failed and continue to cleanup/next candidate.

Only real user cancellation should end the batch as `Cancelled`.

## Problem 2: Reset Priority Is Too Easy To Trigger

### Current Code

File:

```text
app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt
```

Current top app bar action:

```kotlin
actions = {
    TextButton(onClick = screenModel::resetSourceOrder) {
        Text(stringResource(KMR.strings.rec_reset_source_order))
    }
}
```

File:

```text
app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt
```

Current reset:

```kotlin
fun resetSourceOrder() {
    sourceOrderPref.set("")
    val languages = state.value.recommendationLanguages
    val filteredSources = RecommendationSourceFilter.filterForRecommendations(visibleSources, languages.toSet())
    val fresh = filteredSources.toImmutableList()
    ...
    mutableState.update { it.copy(orderedSources = fresh, boostedSourceIds = boosted) }
}
```

The reset is:

- visible in the top bar,
- one tap,
- no confirmation,
- no undo,
- high impact.

This is a UX footgun for a manually tuned priority list.

## Implementation Plan

## Part A: Source Evaluation Timeout Resilience

### A1. Replace timeout exceptions with local timeout results

Update:

```text
app/src/main/java/exh/recs/evaluation/SourceEvaluationRunner.kt
```

Use `withTimeoutOrNull(...)` for operations where timeout should be local:

- install wait,
- installed extension load wait,
- popular probe,
- latest probe,
- search probe.

Preferred pattern:

```kotlin
val installSuccess = withTimeoutOrNull(90_000L) {
    installAndWait(ext, installerOverride)
}

if (installSuccess != true) {
    recordExtensionError(ext, "Install timed out or failed")
    return
}
```

For loading installed extension:

```kotlin
val installedExt = withTimeoutOrNull(20_000L) {
    extensionManager.installedExtensionsFlow.first { installed ->
        installed.any { it.pkgName == ext.pkgName && it.signatureHash == ext.signatureHash }
    }.find { it.pkgName == ext.pkgName && it.signatureHash == ext.signatureHash }
}

if (installedExt == null) {
    recordExtensionError(ext, "Extension did not appear in installed list after install")
    return
}
```

For probes:

```kotlin
val page = withTimeoutOrNull(30_000L) { source.getPopularManga(1) }
if (page == null) {
    errorCount++
    timeoutCount++
} else {
    ...
}
```

If a source call throws a non-timeout exception, still count it as an error and continue.

### A2. Preserve real user cancellation

Do not swallow actual user cancellation.

There are two safe patterns:

1. Check runner state before/after each extension/source/probe:

```kotlin
if (_state.value.status == SourceEvaluationQueueState.Status.Cancelling) throw CancellationException()
```

2. In catch blocks, only rethrow `CancellationException` if it is not a timeout and the run is actually cancelling:

```kotlin
if (e is CancellationException && _state.value.status == SourceEvaluationQueueState.Status.Cancelling) throw e
```

Recommendation: prefer `withTimeoutOrNull` so `TimeoutCancellationException` does not leak, and keep explicit state checks for real cancellation.

### A3. Add timeout-specific diagnostics

Add stable log messages:

```text
KMK SourceEvaluation timeout:
```

Include:

- extension name,
- source name when available,
- phase,
- timeout duration.

Examples:

```text
KMK SourceEvaluation timeout: Asia2 / Asia2Source popular probe timed out after 30000ms
```

Do not log URLs or user-sensitive query data unless already safe.

### A4. Store timeout as evaluation error/result

If an entire extension cannot install or load:

- call `recordExtensionError(ext, "Install timed out")` or `recordExtensionError(ext, "Loading sources timed out")`;
- increment failed count;
- cleanup if needed;
- continue to next extension.

If one source times out during probing:

- still create an evaluation record if possible using `SourceEvaluationScorer.score(...)` with higher `errorCount`, or create an `ERROR` record if there is not enough data.

Current `probeAndScore()` already accumulates `errorCount`. Extend it with clear timeout messages where practical.

If all probes for a source time out/fail:

- return an evaluation with verdict `ERROR` or `POOR_SEARCH`, depending on current scorer behavior;
- do not cancel the whole batch.

### A5. Consider adding timeout count

Optional, if low risk:

```kotlin
var timeoutCount = 0
```

But the current `SourceEvaluation` schema does not have a timeout count field. Do not add a database migration just for this unless necessary.

Recommendation:

- Fold timeout into `errorCount`.
- Include timeout text in `errorMessage` for extension-level failures.
- Avoid schema changes in this pass.

### A6. Add helper to avoid repeated boilerplate

Optional but recommended:

```kotlin
private suspend fun <T> timedOrNull(
    timeoutMs: Long,
    extName: String,
    sourceName: String?,
    phase: SourceEvaluationQueueState.Phase,
    block: suspend () -> T,
): T?
```

This helper should:

- use `withTimeoutOrNull`,
- log timeout if null,
- not swallow non-timeout exceptions;
- not change cancellation semantics for actual user cancellation.

If the helper makes the runner harder to read, keep explicit local `withTimeoutOrNull` calls instead.

### A7. Ensure completed count advances correctly

Currently `completedCount` increments at the end of `evaluateExtension()`.

Verify that extensions that:

- install timeout,
- load timeout,
- no catalogue sources,
- probe all sources with errors,
- cleanup with prompt skipped,

still advance `completedCount` once the extension has been handled.

Do not increment `completedCount` for pre-existing skipped candidates if the current UX treats them as skipped instead of completed; document whichever behavior is chosen.

### A8. Add tests for timeout behavior

Because `SourceEvaluationRunner` depends on Android-ish extension/source objects, direct unit tests may be difficult.

Preferred approach:

- extract pure timeout/cancellation decision logic into a small helper if needed.

Possible helper:

```text
SourceEvaluationFailurePolicy.kt
```

Test:

- timeout during probe -> local failure, continue batch;
- timeout during install -> extension failure, continue batch;
- user cancellation -> batch cancellation;
- ordinary source exception -> local failure.

If extracting pure logic is not worthwhile, add implementation notes explaining manual/device verification requirements.

Manual verification is required:

1. Run Source Evaluation with Private installer.
2. Include/encounter a source known to hang, such as Asia2.
3. Confirm it eventually records timeout/error.
4. Confirm evaluation continues to the next extension/source.
5. Confirm final status is `Completed` or `Failed` only for genuine batch-level failure, not `Cancelled`.
6. Confirm pressing Cancel still produces `Cancelled`.

## Part B: Source Priority Reset Safety

### B1. Remove reset action from top app bar

Update:

```text
app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt
```

Remove:

```kotlin
actions = {
    TextButton(onClick = screenModel::resetSourceOrder) {
        Text(stringResource(KMR.strings.rec_reset_source_order))
    }
}
```

The top app bar should no longer have a one-tap reset action.

### B2. Add explicit confirmation state

Update:

```text
app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt
```

Add a state field:

```kotlin
val showResetSourceOrderDialog: Boolean = false
```

Add actions:

```kotlin
fun requestResetSourceOrder()
fun dismissResetSourceOrderDialog()
fun confirmResetSourceOrder()
```

Move current reset logic into `confirmResetSourceOrder()`.

`requestResetSourceOrder()` only opens the dialog.

### B3. Move reset control into Source Priority section

In `RecommendationsSettingsScreen`, place a low-emphasis action near the source priority section summary, after `source_status_note` or after the source list.

Recommended label:

```text
Restore default source order
```

This is clearer than `Reset priority`.

Recommended style:

- `OutlinedButton` or `TextButton`;
- not in the app bar;
- not visually primary.

Suggested placement:

```text
Source priority
Top 3 sources get more results. Drag to reorder.
Statuses below are from the last For You refresh.
[Restore default source order]
```

### B4. Add confirmation dialog

Dialog title:

```text
Restore default source order?
```

Dialog body:

```text
This will replace your custom source priority order with the default order for the selected recommendation languages.
```

Confirm:

```text
Restore
```

Cancel:

```text
Cancel
```

Do not use alarming/destructive styling unless the app already has a destructive dialog style. This is high-impact but not data deletion.

### B5. Disable/hide reset while dragging

The `reorderableState.isAnyItemDragging` is available in the Composable.

Disable the reset button while dragging:

```kotlin
enabled = !reorderableState.isAnyItemDragging
```

If moving the button into a child composable, pass the dragging state in.

### B6. Optional undo support

Only add undo if there is an existing snackbar/undo pattern that is easy to reuse.

If no simple pattern exists:

- skip undo;
- rely on confirmation dialog;
- document that undo was deferred.

Do not invent a large snackbar infrastructure for this pass.

### B7. Tests for reset behavior

If there are existing tests for `RecommendationSourceOrdering` or `RecommendationsSettingsScreenModel`, add/extend tests around reset logic.

Possible tests:

- `requestResetSourceOrder()` opens dialog and does not change order.
- `dismissResetSourceOrderDialog()` closes dialog and does not change order.
- `confirmResetSourceOrder()` clears stored order and recomputes default order.

If screen model tests are hard because of Injekt dependencies, test pure helper logic only and document UI manual verification.

Manual verification:

1. Open Recommendation Settings.
2. Confirm no `Reset priority` action appears in top app bar.
3. Scroll to Source Priority.
4. Confirm `Restore default source order` appears near the section.
5. Tap it.
6. Confirm dialog appears.
7. Cancel -> order unchanged.
8. Tap again -> Restore -> order resets.
9. While dragging a source, confirm reset cannot be triggered.

## Documentation And Versioning

Update:

```text
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/README.md
RECOMMENDATION_VERSIONING.md
app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt
```

Create implementation report:

```text
docs/recommendations/KMK_RECS_V0_6_14_TIMEOUT_RESILIENCE_AND_PRIORITY_RESET_SAFETY_IMPLEMENTATION.md
```

Implementation report must include:

- root cause of false `Cancelled` status;
- how timeout handling changed;
- whether Asia2 or another hanging source was manually verified;
- how real Cancel behavior was preserved;
- reset priority UI change details;
- files changed;
- tests run;
- generated APK path/name;
- manual verification performed or not performed.

User-facing What's New should mention only user-facing changes:

```text
Source Evaluation now treats slow source timeouts as per-source failures instead of cancelling the whole run.
Source priority reset is now protected by a confirmation dialog and moved out of the top bar.
```

Do not include developer-only documentation changes in What's New.

## Test Commands

Run:

```text
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

If tests cannot run because of local environment issues, document the exact failure.

## Acceptance Criteria

This work is complete only if:

- a slow/hung popular/latest/search probe does not cancel the entire evaluation batch;
- install/load timeouts mark that extension as failed and continue to the next candidate;
- real user cancellation still sets status to `Cancelled`;
- timed-out sources/extensions produce useful error/status text;
- cleanup still runs after local timeout failures;
- `Reset priority` no longer appears as a one-tap top-bar action;
- source order reset requires confirmation;
- cancelling the reset dialog leaves the order unchanged;
- reset is disabled or inaccessible while dragging;
- docs/version/release notes are updated to v0.6.14;
- tests/build are run and documented.

## Non-Goals

Do not implement:

- source scoring changes;
- source evaluation candidate pool changes beyond preserving current v0.6.12 behavior;
- installer cleanup policy changes beyond preserving current v0.6.13 behavior;
- new database schema fields for timeout count;
- large snackbar/undo infrastructure if not already available;
- automatic retry logic for timed-out sources;
- source priority auto-reordering.

## Recommendation

Proceed with this as a focused resilience and safety pass.

The timeout fix addresses a real batch stability problem: one bad extension/source should not make the user think they cancelled the run. The reset-priority fix removes a high-impact accidental action from the top bar and makes source order restoration intentional.
