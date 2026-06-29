# KMK-Recs v0.6.14: Timeout Resilience and Priority Reset Safety Implementation

Date: 2026-06-19

Feature version: KMK-Recs v0.6.14

## Summary

Fixed two usability issues:

1. **Source Evaluation false cancellation**: One slow or hanging source (e.g., Asia2 at ProbingPopular) caused the entire evaluation batch to show `Cancelled` even though the user did not press Cancel. Root cause: `withTimeout()` throws `TimeoutCancellationException extends CancellationException`, which was rethrown by all catch blocks and set the batch status to `Cancelled`.

2. **Accidental source priority reset**: The `Reset priority` button was in the top app bar — one tap, no confirmation, no undo — making it easy to destroy a manually tuned source order.

## Part A: Timeout Resilience

### Root Cause

`TimeoutCancellationException` is a subtype of `CancellationException`. The catch chain in `SourceEvaluationRunner` was:

```
withTimeout() throws TimeoutCancellationException
→ probe catch: if (e is CancellationException) throw e
→ evaluateExtension catch: catch (e: CancellationException) { throw e }
→ start() catch: catch (e: CancellationException) { status = Cancelled }
```

This made one slow source look like the user cancelled the whole batch.

### Fix

Replaced all five `withTimeout()` calls with `withTimeoutOrNull()`. Timeouts now return `null` instead of throwing, making them local failures:

| Call site | Timeout | Null handling |
| --- | --- | --- |
| `installAndWait()` | 90s | log + `recordExtensionError("Install timed out after 90s")` + `return` |
| `installedExtensionsFlow.first {}` | 20s | log + `recordExtensionError("Loading sources timed out after 20s")` + `return` |
| `source.getPopularManga(1)` | 30s | log + `errorCount++` |
| `source.getLatestUpdates(1)` | 30s | log + `errorCount++` |
| `source.getSearchManga(...)` | 25s | log + `searchCount++` + `errorCount++` |

All timeouts log with prefix `"KMK SourceEvaluation timeout:"` including extension name, source name (where available), and timeout duration.

### Real User Cancellation Preserved

`withTimeoutOrNull()` only absorbs its own `TimeoutCancellationException` (matched by internal token). External `CancellationException` from `runJob.cancel()` propagates through `withTimeoutOrNull` unchanged. The `catch (e: CancellationException) { throw e }` guards in the probe catch blocks and `evaluateExtension` outer catch continue to rethrow real user-initiated cancellation.

### completedCount Fix

Previously `completedCount++` was placed AFTER the `try-finally` block, so early-return paths (install timeout, load timeout, no catalogue sources, etc.) never incremented it. The progress bar would never reach 100% if multiple extensions failed. Fixed by moving `completedCount++` into the `finally` block so it always fires for any extension that entered the evaluation attempt. Pre-existing skipped extensions return BEFORE the `try` block so they intentionally remain uncounted.

### installAndWait() Unchanged

`installAndWait()` still has `if (e is CancellationException) throw e` in its catch block. This is correct: when `withTimeoutOrNull` times out, it cancels its child with a specific `TimeoutCancellationException`. `installAndWait` rethrows it; `withTimeoutOrNull` recognizes it as its own and returns `null`. For real user cancellation, `installAndWait` rethrows an external `CancellationException`; `withTimeoutOrNull` does not absorb it; it propagates correctly.

## Part B: Priority Reset Safety

### Changes

Removed `TextButton(onClick = screenModel::resetSourceOrder)` from the `AppBar` `actions = {}` parameter.

Added to `RecommendationsSettingsScreenModel`:
- `showResetSourceOrderDialog: Boolean = false` in State
- `requestResetSourceOrder()` — sets `showResetSourceOrderDialog = true`
- `dismissResetSourceOrderDialog()` — sets `showResetSourceOrderDialog = false`
- `confirmResetSourceOrder()` — closes dialog, then runs the same reset logic previously in `resetSourceOrder()`

Added to `RecommendationsSettingsScreen`:
- `item(key = "source_reset_button")` after the source list, before Sources To Try: a `TextButton("Restore default source order")` disabled while `reorderableState.isAnyItemDragging`
- `AlertDialog` with title "Restore default source order?", body text explaining the impact, Restore confirm button, and Cancel dismiss button — shown when `state.showResetSourceOrderDialog`

## New Strings

Added under `<!-- KMK v0.6.14 -->` comment in `i18n-kmk/strings.xml`:

- `rec_restore_default_source_order` = "Restore default source order"
- `rec_reset_source_order_dialog_title` = "Restore default source order?"
- `rec_reset_source_order_dialog_message` = "This will replace your custom source priority order with the default order for the selected recommendation languages."
- `rec_reset_source_order_confirm` = "Restore"

## Files Changed

### Modified Files

- `app/src/main/java/exh/recs/evaluation/SourceEvaluationRunner.kt` — `withTimeoutOrNull` for all 5 timeout sites, `completedCount++` moved to `finally`, timeout diagnostics logging
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt` — `showResetSourceOrderDialog` State field, `requestResetSourceOrder()`, `dismissResetSourceOrderDialog()`, `confirmResetSourceOrder()`
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt` — remove top-bar reset action, add "Restore default source order" button, add reset confirmation `AlertDialog`
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` — VERSION_CODE=614, VERSION_NAME="KMK-Recs v0.6.14", What's New entry
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` — 4 new strings for v0.6.14
- `docs/recommendations/CURRENT_STATE.md` — updated version, APK, Source Evaluation section, Tests section
- `docs/recommendations/NEXT_WORK.md` — updated version
- `docs/recommendations/README.md` — added v0.6.14 implementation report entry
- `RECOMMENDATION_VERSIONING.md` — added v0.6.14 entry

### New Files

- `docs/recommendations/KMK_RECS_V0_6_14_TIMEOUT_RESILIENCE_AND_PRIORITY_RESET_SAFETY_IMPLEMENTATION.md` (this file)

## Tests

- `:app:testDebugUnitTest` — BUILD SUCCESSFUL (267 tests, 26 executed, 241 up-to-date)
- `:app:assembleDebug` — BUILD SUCCESSFUL

No new unit tests were added. `SourceEvaluationRunner` depends on Android extension/source infrastructure that cannot be easily unit-tested without mocks. The pure helper approach (e.g., `SourceEvaluationFailurePolicy`) was considered but deferred since all timeout behavior is now straightforwardly handled by `withTimeoutOrNull` returns — there is no decision logic to extract.

## APK

Generated: `app/build/outputs/apk/debug/app-universal-debug.apk`

Copied to: `Komikku-v1.13.6-kmk.6.14-debug.apk`

## Manual Verification Required

On-device verification is needed to confirm:

1. Run Source Evaluation with Private installer using a batch that includes Asia2 or another known hanging source.
2. Confirm that when Asia2 hangs at ProbingPopular and the timeout fires (30s), the status shows as an error/failed extension — not `Cancelled`.
3. Confirm evaluation continues to the next extension.
4. Confirm final status is `Completed` (or `Failed` only for genuine batch-level failure), not `Cancelled`.
5. Confirm pressing Cancel still produces `Cancelled` status.
6. Confirm progress bar (`completedCount / totalCount`) advances for failed extensions, reaching 100% when the batch finishes.
7. Open Recommendation Settings. Confirm no `Reset priority` action in top bar.
8. Scroll to Source Priority. Confirm "Restore default source order" button is present.
9. Drag a source — confirm reset button is disabled while dragging.
10. Tap "Restore default source order" — confirm dialog appears.
11. Tap Cancel — confirm order is unchanged.
12. Tap the button again, tap Restore — confirm order resets to default.
