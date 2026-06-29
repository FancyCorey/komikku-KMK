# KMK-Recs v0.6.19 Implementation Notes

Date: 2026-06-20
APK: `Komikku-v1.13.6-kmk.6.19-debug.apk`
VERSION_CODE: 619

## Problem

Source Evaluation had several UX issues accumulated after v0.6.18:

1. **Shizuku prompts intrude when not needed** — Shizuku setup card and status messages appeared for users on PRIVATE or CURRENT installer who never selected Shizuku mode.
2. **Quarantined/blocked cards dominate the screen** — `UnsafeSourcesCard` and `BlockedPackagesCard` sat at the top of the page, overshadowing normal evaluation controls.
3. **No persistence across screen navigation** — leaving Source Evaluation cancelled the evaluation run.
4. **No taste-profile confidence signal** — evaluation ran with the same confidence regardless of how sparse the taste profile was.

## Solution

Four phases implemented in one pass.

### Phase 0 — Taste Profile Confidence

`TasteProfileConfidence` domain model derived cheaply from `TasteProfile` (no extra DB query):

- `isSufficientForPersonalizedEvaluation`: `(positive + negative) >= 5 AND positive >= 2`
- `usablePositiveTagCount`: `learnedTagWeights` positive + `explicitTagPreferences` positive
- `usableNegativeTagCount`: `learnedTagWeights` negative + `explicitTagPreferences` negative + `blockedGroups.size`
- `hasSourceAffinity`: any `sourceAffinity` value > 0
- `EMPTY` constant for fallback

When `!isSufficientForPersonalizedEvaluation`, a low-confidence `InfoCard` appears below safety diagnostics and above the start button. Evaluation still runs.

### Phase 1 — Shizuku UX Cleanup

`showShizukuSetup: Boolean` state field in `SourceEvaluationScreenModel.State` (default `false`).

Auto-shown when:
- `setInstallerMode(SHIZUKU)` is called, or
- `refreshInstallerPolicy()` detects CURRENT+global Shizuku (`autoShowShizuku = true`)

OR-logic in `refreshInstallerPolicy()` preserves manual expansion:
```kotlin
showShizukuSetup = s.showShizukuSetup || autoShowShizuku
```

When hidden, a compact "Shizuku setup" `TextButton` is shown in the list instead of the full card. `showShizukuSetup()` / `hideShizukuSetup()` ScreenModel actions allow manual toggle.

### Phase 2 — Safety Diagnostics Demotion

Removed `UnsafeSourcesCard` and `BlockedPackagesCard` from top-of-page `LazyColumn` positions.

Added `SafetyDiagnosticsRow` composable below `candidate_diagnostics` item. Shows:
- "Safety diagnostics" section title
- Quarantined count as `TextButton` (opens existing `UnsafeSourcesDialog`)
- Blocked count as `TextButton` in error color (opens existing `BlockedPackagesDialog`)
- Hidden when both counts are zero

Dialogs, `UnsafeSourcesCard`, `BlockedPackagesCard`, `UnsafeSourceRow`, `BlockedPackageRow` composables are unchanged — only their entry points changed from inline cards to dialog triggers.

### Phase 3 — Background Execution

**`SourceEvaluationJobState`** (process-scoped singleton):
- `val activeQueueState: MutableStateFlow<SourceEvaluationQueueState?>`
- `@Volatile var pendingCandidates: List<EvaluationCandidate>?`
- `@Volatile var pendingOptions: SourceEvaluationOptions?`
- `@Volatile var activeRunner: SourceEvaluationRunner?`
- `fun reset()` — clears all fields and state

**`SourceEvaluationJob`** (WorkManager `CoroutineWorker`):
- Reads candidates/options from singleton (fails gracefully on process restart)
- Calls `setForegroundSafely()` for foreground service
- Creates `SourceEvaluationRunner`, stores in singleton, starts it
- Collects `runner.state` via `.onEach { ... }.first { it.isTerminal }` — updates singleton state and notification per emission
- `CancellationException` catch: cancels runner, sets Cancelled state, dismisses notification, returns `Result.success()`
- `finally` block: clears singleton runner and pending data
- `ExistingWorkPolicy.KEEP` — prevents double-enqueue if screen is opened twice
- `onStopped()` NOT overridden (final in `CoroutineWorker`; cancellation handled via CancellationException)

**`SourceEvaluationNotifier`**:
- `buildProgressNotification()` — ongoing notification with progress bar and current extension name
- `updateProgress()` — posts to `ID_SOURCE_EVALUATION_PROGRESS`
- `dismissProgress()` — cancels progress notification
- `showComplete(strongFitCount)` — auto-cancel complete notification with strong-fit count
- Uses `context.stringResource(KMR.strings.xxx)` (moko-resources) — not `context.getString(R.string.xxx)`

**ScreenModel changes**:
- Removed `private var runner: SourceEvaluationRunner?` field
- `init` block: loads `TasteProfileConfidence` and reconnects to `SourceEvaluationJobState.activeQueueState`
- `launchEvaluation()`: sets singleton state, enqueues `SourceEvaluationJob` via WorkManager
- `cancelEvaluation()`: calls `SourceEvaluationJob.cancel(context)` + null-safe Cancelled state update
- `resetEvaluation()`: calls `SourceEvaluationJobState.reset()`

**`isTerminal` property** on `SourceEvaluationQueueState`:
```kotlin
val isTerminal: Boolean
    get() = status == Status.Completed || status == Status.Cancelled || status == Status.Failed
```
Needed because `isRunning` only returns `true` for `Status.Running`, not `Status.Cancelling`.

## Key Design Decisions

- **No DB schema changes for Phase 3** — process-scoped singleton is safe because WorkManager runs in-process. On process restart, the job fails with a descriptive error: "Evaluation state was lost (app process restarted). Please start again." No migration needed.
- **No notification cancel action** — cancellation is available from the Source Evaluation screen via the Cancel button.
- **Shizuku OR-logic** — `showShizukuSetup = s.showShizukuSetup || autoShowShizuku` ensures a user who manually expands the card is not collapsed by mode refresh.
- **v0.6.18 safety behavior intact** — `KnownUnsafeExtensionPackages` static guard, `ExtensionLoader` filter, and `BlockedPackagesCard` dialogs all remain. Only the placement changed (dialogs instead of inline cards).

## Files Added

| File | Purpose |
| --- | --- |
| `domain/src/main/java/tachiyomi/domain/taste/model/TasteProfileConfidence.kt` | Domain model for taste profile confidence signal |
| `app/src/main/java/exh/recs/evaluation/SourceEvaluationJobState.kt` | Process-scoped singleton bridging WorkManager job and ScreenModel |
| `app/src/main/java/exh/recs/evaluation/SourceEvaluationJob.kt` | WorkManager CoroutineWorker for background evaluation |
| `app/src/main/java/exh/recs/evaluation/SourceEvaluationNotifier.kt` | Android notification builder for progress/complete |
| `app/src/test/java/exh/recs/evaluation/TasteProfileConfidenceTest.kt` | 11 unit tests |

## Files Modified

| File | Change |
| --- | --- |
| `app/src/main/java/exh/recs/evaluation/SourceEvaluationQueueState.kt` | Added `isTerminal` property |
| `app/src/main/java/eu/kanade/tachiyomi/data/notification/Notifications.kt` | Added `CHANNEL_SOURCE_EVALUATION`, `ID_SOURCE_EVALUATION_PROGRESS`, `ID_SOURCE_EVALUATION_COMPLETE`; registered channel in `createChannels()` |
| `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt` | Added TasteProfileConfidence loading, JobState reconnect, showShizukuSetup state, WorkManager launch/cancel, reset |
| `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt` | Conditional Shizuku card/toggle, SafetyDiagnosticsRow composable, taste confidence InfoCard |
| `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` | VERSION_CODE=619, VERSION_NAME="KMK-Recs v0.6.19", what's new entry |
| `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` | 8 new v0.6.19 strings |

## New Strings (8)

- `source_evaluation_low_confidence_warning` — low-confidence taste profile banner
- `source_evaluation_shizuku_setup_toggle` — compact toggle link text
- `source_evaluation_safety_diagnostics_title` — SafetyDiagnosticsRow section header
- `source_evaluation_safety_quarantined_count` — "Quarantined: %1$d"
- `source_evaluation_safety_blocked_count` — "Blocked: %1$d"
- `source_evaluation_safety_show_quarantined` — TextButton label
- `source_evaluation_safety_show_blocked` — TextButton label
- `source_evaluation_job_notification_title` — "Source evaluation running"

## Bug Fixed During Implementation

`onStopped()` in `CoroutineWorker` is `final` and cannot be overridden. Removed the override. Cancellation is handled via `CancellationException` in `doWork()` catch block, which calls `runner.cancel()` and sets Cancelled state before returning.

## Build Status

- `TasteProfileConfidenceTest` — 11 tests, all PASSED
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL
- `:app:assembleDebug` — BUILD SUCCESSFUL (127 MB)

APK: `Komikku-v1.13.6-kmk.6.19-debug.apk`
