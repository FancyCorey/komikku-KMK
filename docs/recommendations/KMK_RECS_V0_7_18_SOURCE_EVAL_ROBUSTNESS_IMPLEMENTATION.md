# KMK-Recs v0.7.18 — Source Evaluation Robustness

Date: 2026-06-28

Status: implemented and tested.

## Summary

Five features implemented in v0.7.18, all targeting Source Evaluation reliability and UX gaps identified as Phase 1 deferred items:

1. **R-019 remainder**: `ScreenErrorKey` typed sealed interface replaces `screenErrorMessage: String?`
2. **Phase 1 — Connectivity loss mid-run**: Runner detects offline state between extensions and stops cleanly
3. **Phase 1 — Repo failure surfacing**: Zero-candidate warning when extension repo is unavailable
4. **Phase 1 — Hidden suggestion management UI**: Clear dismissed/disliked suggestion buttons in management section
5. **Phase 1 — Version-aware quarantine**: Newer-version hint in Blocked Packages dialog

---

## Task 1 — R-019 Remainder: ScreenErrorKey

### Problem

`SourceEvaluationScreenModel.State.screenErrorMessage: String?` stored resolved English strings:
- `"Failed to load candidates: ${e.message}"` — hardcoded
- `context.stringResource(KMR.strings.source_evaluation_offline_error)` — pre-resolved at set time

Both approaches are non-localizable in State.

### Solution

Added `sealed interface ScreenErrorKey` at file level in `SourceEvaluationScreenModel.kt` (3 variants):

```kotlin
sealed interface ScreenErrorKey {
    data object Offline : ScreenErrorKey
    data class CandidateLoadFailed(val detail: String?) : ScreenErrorKey
    data class CrashRecovery(val extensionName: String, val phase: String) : ScreenErrorKey
}
```

Changed `State.screenErrorMessage: String?` → `State.screenError: ScreenErrorKey?`.

Added `@Composable fun ScreenErrorKey.toLocalString()` in `SourceEvaluationScreen.kt` mapping each variant to a KMR string.

**KMR strings added (2):**
- `source_evaluation_error_candidate_load_failed` = "Failed to load candidates: %1$s"
- `source_evaluation_error_candidate_load_failed_unknown` = "Failed to load candidates"

`source_evaluation_offline_error` and `source_evaluation_crash_recovery_marked_unsafe` already existed.

**Impact:** removed `tachiyomi.core.common.i18n.stringResource` and `tachiyomi.i18n.kmk.KMR` imports from `SourceEvaluationScreenModel.kt` — it no longer resolves strings.

### Files Changed

- `SourceEvaluationScreenModel.kt` — added `ScreenErrorKey`, changed 6 setters, removed 2 imports
- `SourceEvaluationScreen.kt` — added `toLocalString()`, updated display

---

## Task 2 — Connectivity Loss Mid-Run

### Problem

`SourceEvaluationRunner.start()` had no connectivity check inside the candidate loop. If the device went offline mid-batch, the runner would hang on network I/O during install/probe phases, producing per-extension timeout errors rather than a clear "connection lost" outcome.

### Solution

Added `SourceEvaluationQueueState.Status.ConnectivityLost` to the Status enum.

At the top of the candidate loop (after the `Cancelling` check), added:

```kotlin
if (!context.isOnline()) {
    logcat(LogPriority.WARN) { "KMK SourceEvaluation: connectivity lost mid-run after ${_state.value.completedCount} extensions" }
    _state.update { it.copy(status = SourceEvaluationQueueState.Status.ConnectivityLost) }
    return@supervisorScope
}
```

Guarded the `Completed` assignment so it only runs when `status == Running` (not `ConnectivityLost`):

```kotlin
if (_state.value.status == SourceEvaluationQueueState.Status.Running) {
    _state.update { it.copy(status = Status.Completed) }
}
```

Added `ConnectivityLost` to `isIdle` and `isTerminal` computed properties so:
- Options section re-appears (user can retry)
- Summary card shows with error icon and "Connection lost" text

The `SourceEvaluationJob` already handles non-`Completed` terminal states by dismissing the notification — no job changes needed.

**KMR string added (1):**
- `source_evaluation_connectivity_lost` = "Connection lost mid-run. Completed extensions are saved. Retry when online."

### Files Changed

- `SourceEvaluationQueueState.kt` — added `ConnectivityLost`, updated `isIdle`/`isTerminal`
- `SourceEvaluationRunner.kt` — added `import isOnline`, connectivity check, guarded `Completed` update
- `SourceEvaluationScreen.kt` — added `ConnectivityLost` case to summary card icon and text

---

## Task 3 — Repo Failure Surfacing

### Problem

When extension repositories are unavailable, `extensionManager.availableExtensionsFlow` is empty. `GetSourceEvaluationCandidates` returns an empty pool — the user sees "No candidates" with no indication of why. The underlying cause (extension repo unreachable) is invisible.

### Solution

Added `repoUnavailableWarning: Boolean = false` to `State`.

In `applyOptionsAndUpdateState()` (called whenever candidates reload):

```kotlin
val repoUnavailable = result.candidates.isEmpty() &&
    pool.allEligible.isEmpty() &&
    extensionManager.availableExtensionsFlow.value.isEmpty()
// ...
repoUnavailableWarning = repoUnavailable && s.screenError == null,
```

In `SourceEvaluationScreen`, shows a non-blocking `InfoCard` (not error) above the progress section when `state.repoUnavailableWarning`:

```
Extension list unavailable. Check your internet connection or verify extension repositories are configured in Browse → Extensions.
```

**KMR string added (1):**
- `source_evaluation_repo_unavailable_warning`

### Files Changed

- `SourceEvaluationScreenModel.kt` — added `repoUnavailableWarning` to State, set in `applyOptionsAndUpdateState()`
- `SourceEvaluationScreen.kt` — added repo warning InfoCard

---

## Task 4 — Hidden Suggestion Management UI

### Problem

Users who dismiss or dislike non-installed source suggestions have no way to clear those lists from Source Evaluation. The only recourse was navigating to Recommendation Settings.

### Solution

Added `dismissedSuggestionCount: Int = 0` and `dislikedSuggestionCount: Int = 0` to `State`.

In `init`, loads counts from preferences:

```kotlin
val dismissedRaw = sourcePreferences.dismissedNonInstalledRecommendationSources().get()
val dislikedRaw = sourcePreferences.dislikedRecommendationSourceKeys().get()
val dismissedCount = dismissedRaw.split(";").count { it.isNotBlank() }
val dislikedCount = dislikedRaw.split(";").count { it.isNotBlank() }
```

Added `CLEAR_DISMISSED_SUGGESTIONS` and `CLEAR_DISLIKED_SUGGESTIONS` to `ManagementAction` enum.

In `confirmManagementAction()`, new cases clear the preferences and reset the counts to 0 in State.

In `SourceEvaluationScreen`, the management section shows conditional `TextButton` items when counts > 0:
- "Clear N dismissed suggestion(s)" → `CLEAR_DISMISSED_SUGGESTIONS`
- "Clear N disliked suggestion source(s)" → `CLEAR_DISLIKED_SUGGESTIONS`

Both go through the same confirm dialog as existing management actions.

**KMR strings added (2):**
- `source_evaluation_clear_dismissed_suggestions` = "Clear %1$d dismissed suggestion(s)"
- `source_evaluation_clear_disliked_suggestion_sources` = "Clear %1$d disliked suggestion source(s)"

### Files Changed

- `SourceEvaluationScreenModel.kt` — new State fields, new ManagementAction cases, init loading
- `SourceEvaluationScreen.kt` — 2 new conditional management section items

---

## Task 5 — Version-Aware Quarantine

### Problem

`UnsafeExtensionPackage` does not store the version code that was quarantined. The Blocked Packages dialog shows each quarantined extension with an "Allow" button but gives no indication whether a newer, possibly fixed version is available.

### Solution

Added `blockedPackagesWithNewerAvailable: Set<String> = emptySet()` to `State` (set of pkgNames).

In the blocked packages observer:

```kotlin
val availablePkgNames = extensionManager.availableExtensionsFlow.value.map { it.pkgName }.toSet()
val withNewer = blocked.filter { it.pkgName in availablePkgNames }.map { it.pkgName }.toSet()
mutableState.update { it.copy(blockedPackages = blocked, blockedPackagesWithNewerAvailable = withNewer) }
```

In `BlockedPackageRow`, added a `hasNewerAvailable: Boolean = false` parameter. When true, shows a `Text` in `colorScheme.primary`:

> Newer version available — remove block to test

The existing "Allow" (remove block) button already handles the re-test action.

**KMR string added (1):**
- `source_evaluation_blocked_package_newer_available` = "Newer version available — remove block to test"

### Files Changed

- `SourceEvaluationScreenModel.kt` — new State field, updated blocked packages observer
- `SourceEvaluationScreen.kt` — updated `BlockedPackageRow` signature and call site

---

## Test Results

```
BUILD SUCCESSFUL
:app:testDebugUnitTest — all existing tests PASSED
:app:assembleDebug — BUILD SUCCESSFUL
```

No new unit tests in this pass (all 5 features involve UI/state logic or external IO with no pure seams amenable to unit testing without Android mocks).

## APK Naming

After `assembleDebug`, copy output to:
`Komikku-v1.13.6-kmk.7.18-debug.apk`
