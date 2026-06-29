# KMK-Recs v0.6.19: Source Evaluation UX and Background Execution Plan

Date: 2026-06-20

Status: implementation plan, awaiting user approval before coding

Feature version: KMK-Recs v0.6.19

Expected APK name: `Komikku-v1.13.6-kmk.6.19-debug.apk`

## Summary

v0.6.19 should improve Source Evaluation usability after the v0.6.18 crash-safety work. It should keep the normal evaluation flow calm, avoid making Shizuku feel required when the user is using Private/recommended/default paths, move quarantine/blocked extension controls out of the first visible position, and begin making Source Evaluation able to continue outside the Source Evaluation screen.

The work has five parts:

1. Shizuku prompt visibility cleanup.
2. Quarantine/blocked extension UI demotion into diagnostics/details.
3. Background Source Evaluation execution with persistent progress and cancellation.

The first two are low-risk UI/state changes. The third is a larger architecture change and should be implemented carefully using Komikku's existing WorkManager/foreground notification patterns or a staged app-scoped runner if WorkManager proves too large for this pass.

## Current Code Findings

### Source Evaluation Screen

Main file:

`app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt`

Current structure:

- `SourceEvaluationScreen` creates `SourceEvaluationScreenModel(context)` with `rememberScreenModel`.
- It observes `state` and renders a `LazyColumn`.
- At the top of the list, before progress/options/results, it currently renders:
  - `screen_error`
  - `unsafe_sources` card
  - `blocked_packages` card
- Then it renders progress/summary/options.

This means quarantine and blocked extension cards are highly visible and visually dominate the page whenever they exist. That was useful during crash debugging, but it is too distracting as a normal UI.

### Shizuku Card

Current file:

`app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt`

Current behavior:

- `ShizukuSetupCard(...)` is always rendered as an item under options when the queue is idle.
- `ShizukuSetupCard` always computes a status string from `shizukuState`, regardless of selected installer mode.
- If Shizuku is not installed, it shows the not-installed state and install/refresh controls even when the user is using `PRIVATE` or `CURRENT`.

This makes the screen imply Shizuku is required even when Private is the recommended path.

### Installer Policy

Current file:

`app/src/main/java/exh/recs/evaluation/SourceEvaluationInstallerPolicy.kt`

Current modes:

```kotlin
enum class InstallerMode { CURRENT, PRIVATE, SHIZUKU }
```

Current behavior:

- `PRIVATE` can be ready without Shizuku.
- `SHIZUKU` checks installed/running/permission.
- `CURRENT` can indirectly use global Shizuku if the global extension installer is Shizuku.

Important nuance:

- If user explicitly chooses `SHIZUKU`, Shizuku status and setup controls should be shown.
- If user chooses `PRIVATE`, Shizuku status should not be shown.
- If user chooses `CURRENT` and the global installer is Shizuku, Shizuku status may be relevant, but the UI should explain this as a Current-installer consequence, not as a general requirement.

### Source Evaluation Runner

Current files:

- `SourceEvaluationScreenModel.kt`
- `SourceEvaluationRunner.kt`

Current behavior:

- `SourceEvaluationScreenModel.launchEvaluation(...)` creates a new `SourceEvaluationRunner(context)`.
- It subscribes to `newRunner.state` inside `screenModelScope`.
- It stores the runner in a private screen-model field:

```kotlin
private var runner: SourceEvaluationRunner? = null
```

- `SourceEvaluationRunner` owns its own `CoroutineScope(SupervisorJob() + Dispatchers.IO)`.
- Runner state is a private in-memory `MutableStateFlow`.
- If the screen model is destroyed, the runner may continue for a while because its scope is independent, but the UI loses its reference. There is no durable run ID, WorkManager job, notification, or repository-backed progress state.

This is not reliable enough for background execution.

### Existing Background Patterns

Relevant existing files:

- `app/src/main/java/eu/kanade/tachiyomi/data/sync/SyncDataJob.kt`
- `app/src/main/java/eu/kanade/tachiyomi/util/system/WorkManagerExtensions.kt`
- `app/src/main/java/eu/kanade/tachiyomi/data/notification/Notifications.kt`

Current app patterns:

- Long-running background work uses `CoroutineWorker`.
- Jobs call `setForegroundSafely()`.
- Jobs expose `getForegroundInfo()` with a `ForegroundInfo` notification.
- `Notifications.kt` defines channel IDs and notification IDs centrally.
- `Context.workManager` and `WorkManager.isRunning(tag)` helpers already exist.

This is the correct pattern if Source Evaluation needs to continue after leaving the screen or switching apps.

## Goals

0. Make Source Evaluation honest about taste-profile confidence when the user has not rated enough manga/tags yet.
1. Make Private/recommended/current evaluation paths avoid unnecessary Shizuku prompts.
2. Only show Shizuku install/open/refresh/setup controls when Shizuku is explicitly selected or relevant through Current using global Shizuku.
3. Move quarantine and blocked extension controls out of the top of the page.
4. Keep safety diagnostics accessible but secondary.
5. Allow Source Evaluation to continue when the user leaves the Source Evaluation screen.
6. Make the Source Evaluation foreground notification take the user directly back to the Source Evaluation/progress screen.
7. Show a simple count of not-installed and not-yet-evaluated extension candidates remaining.
8. Keep installed extensions out of the Source Evaluation suggestion/evaluation list.
9. Clearly report offline/network failures and allow retry after connectivity returns without requiring a full app restart.
10. Preserve v0.6.18 crash protection and package-level blocking behavior.
11. Keep versioning under `v0.6.x` because this is still Source Evaluation / extension work.
## Non-Goals

- Do not remove quarantine or blocked extension functionality.
- Do not make Shizuku the default path.
- Do not auto-install, auto-uninstall, or auto-start Shizuku.
- Do not silently change the global extension installer preference.
- Do not redesign the whole Source Evaluation screen.
- Do not change For You recommendation scoring in this pass.
- Do not weaken the v0.6.18 DigitalComicMuseum safety block.
- Do not add random third-party extension repos by default without validating compatibility, signing metadata, and maintenance status.
- Do not make network/offline failures count as poor source quality. Offline interruptions are run-level failures, not evidence that a source is bad.

## Taste Evidence Requirement

Source Evaluation scores depend on the user's local taste profile: loved, liked, and disliked manga/tags. If the user has not rated enough manga, or if the rated manga do not provide enough usable tags, the system should not imply that the source evaluation is a strong match to the user's taste.

Required behavior:

- Detect when the taste profile has insufficient evidence before or during Source Evaluation.
- Show a clear but non-blocking message such as: `Not enough rated manga/tags yet for strong personalization. Evaluation can still run, but source-fit scores may be less accurate.`
- Do not block evaluation entirely unless the scorer cannot work at all.
- Use weaker/default scoring when taste evidence is thin.
- Prefer language/search reliability/general quality signals more heavily when personalized tag evidence is insufficient.
- Do not label a source as a strong personalized fit purely from weak or empty taste data.

Suggested evidence thresholds:

- at least 3-5 rated manga total across Love/Like/Dislike;
- at least 5-10 usable positive or negative tags after normalization;
- at least one positive signal and one negative/blocking signal if possible.

These exact thresholds can be adjusted in implementation, but the UI must communicate low confidence when the user has not provided enough rating/tag data.

Implementation notes:

- Add a helper around `GetTasteProfile` or `TasteProfile` such as `TasteProfileConfidence`.
- Expose fields like `ratedMangaCount`, `usablePositiveTagCount`, `usableNegativeTagCount`, and `isSufficientForPersonalizedEvaluation` if those can be derived cheaply.
- Add the confidence status to `SourceEvaluationScreenModel.State`.
- Show the warning near candidate diagnostics or near the Start Evaluation button, not as a disruptive top-of-screen card.
- Include confidence status in copied diagnostics.
- Add unit tests for low-evidence and sufficient-evidence cases.
## Part 1: Shizuku Prompt Visibility Cleanup

### Required Behavior

The Source Evaluation screen should not show Shizuku setup/status prompts by default when the user is using Private or a non-Shizuku Current installer.

Show Shizuku status/setup controls only when one of these is true:

1. `state.options.installerMode == InstallerMode.SHIZUKU`
2. `state.options.installerMode == InstallerMode.CURRENT` and the current global installer is `BasePreferences.ExtensionInstaller.SHIZUKU`
3. the user explicitly expands/opens an advanced Shizuku setup section

Recommended default:

- Hide `ShizukuSetupCard` unless Shizuku is selected/relevant.
- Add a small secondary action near the installer selector if needed:
  - `Shizuku setup`
  - or an expandable `Advanced installer setup`

### Specific Code Changes

#### `SourceEvaluationScreenModel.State`

Add a UI state flag:

```kotlin
val showShizukuSetup: Boolean = false
```

Add actions:

```kotlin
fun showShizukuSetup()
fun hideShizukuSetup()
fun toggleShizukuSetup()
```

When `setInstallerMode(SHIZUKU)` is called:

- set `showShizukuSetup = true`
- refresh installer policy

When `setInstallerMode(PRIVATE)` is called:

- set `showShizukuSetup = false`
- refresh installer policy

When `setInstallerMode(CURRENT)` is called:

- if global installer is Shizuku, set `showShizukuSetup = true`
- otherwise leave false or hide it

#### `SourceEvaluationScreen.kt`

Replace unconditional rendering:

```kotlin
item(key = "shizuku_setup") {
    ShizukuSetupCard(...)
}
```

with conditional rendering:

```kotlin
val currentUsesShizuku = state.options.installerMode == InstallerMode.CURRENT && state.installerPolicy indicates Shizuku global mode
val shouldShowShizukuSetup = state.options.installerMode == InstallerMode.SHIZUKU || currentUsesShizuku || state.showShizukuSetup

if (shouldShowShizukuSetup) {
    item(key = "shizuku_setup") { ShizukuSetupCard(...) }
} else {
    item(key = "shizuku_setup_toggle") {
        TextButton(onClick = screenModel::showShizukuSetup) { Text("Shizuku setup") }
    }
}
```

Use localized strings rather than hardcoded display text.

### Installer Policy Message Handling

Currently `InstallerMode.CURRENT` returns Shizuku-related messages if global installer is Shizuku and Shizuku is unavailable.

Keep that behavior only if Current actually maps to global Shizuku.

Do not show Shizuku messages for:

- `PRIVATE`
- `CURRENT` with global `PRIVATE`
- `CURRENT` with global `LEGACY` / `PACKAGEINSTALLER`

### User-Facing Copy

Add strings similar to:

- `source_evaluation_shizuku_setup_toggle`: `Shizuku setup`
- `source_evaluation_shizuku_setup_hidden_summary`: `Private installer is recommended for evaluation. Shizuku setup is only needed if you choose Shizuku.`

Keep wording short and non-alarming.

## Part 2: Demote Quarantine and Blocked Extension UI

### Required Behavior

Do not show quarantine/blocked extension cards at the very top by default.

Instead, show a compact secondary section under the core options, preferably below:

- batch size
- installer mode
- skip already evaluated
- include explicit
- candidate diagnostics

Recommended UI:

A compact row or section titled:

- `Safety diagnostics`

It should show small counts:

- `Quarantined sources: N`
- `Blocked extensions: N`

Actions:

- `Show quarantined sources`
- `Show blocked extensions`

Only show the full cards/dialogs after the user taps the relevant action.

### Specific Code Changes

#### Remove top-level card placement

In `SourceEvaluationScreen.kt`, remove or relocate the top-of-list blocks:

```kotlin
if (state.unsafeSources.isNotEmpty()) {
    item(key = "unsafe_sources") { UnsafeSourcesCard(...) }
}

if (state.blockedPackages.isNotEmpty()) {
    item(key = "blocked_packages") { BlockedPackagesCard(...) }
}
```

Do not delete the card/dialog composables unless replacing them with a cleaner diagnostics row. The dialogs remain useful.

#### Add diagnostics row

Add a new composable:

```kotlin
@Composable
private fun SafetyDiagnosticsRow(
    unsafeCount: Int,
    unsafeHiddenCount: Int,
    blockedCount: Int,
    onViewUnsafe: () -> Unit,
    onViewBlocked: () -> Unit,
)
```

Render it below candidate diagnostics, or immediately below skip/include options.

Only render if either count is greater than zero, or render as a compact expandable row if always useful.

Recommended default:

- If both counts are zero, do not show the row.
- If either count is nonzero, show one compact row, not two cards.

#### Keep Dialogs

Keep:

- `state.showUnsafeSourcesDialog`
- `state.showBlockedPackagesDialog`
- clear confirmation dialogs
- row actions for remove/allow

But ensure clearing/allowing dangerous blocks still has strong confirmation where appropriate.

### User-Facing Copy

Add strings:

- `source_evaluation_safety_diagnostics_title`: `Safety diagnostics`
- `source_evaluation_safety_quarantined_count`: `Quarantined sources: %d`
- `source_evaluation_safety_blocked_count`: `Blocked extensions: %d`
- `source_evaluation_safety_show_quarantined`: `Show quarantined`
- `source_evaluation_safety_show_blocked`: `Show blocked`

## Part 3: Background Source Evaluation Execution

### Product Requirement

The user should be able to start Source Evaluation, leave the Source Evaluation screen, read manga, or switch apps while evaluation continues. Returning to Source Evaluation should show the current progress/results. The user should be able to cancel the evaluation.

### Recommended Architecture

Use a WorkManager-backed foreground job if feasible in this pass.

Suggested new files:

- `app/src/main/java/exh/recs/evaluation/SourceEvaluationJob.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationNotifier.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationRunStore.kt` or repository-backed run state

Use existing patterns from:

- `SyncDataJob.kt`
- `WorkManagerExtensions.kt`
- `Notifications.kt`

### Why WorkManager Is Preferred

A screen-model coroutine is not enough because:

- leaving the screen destroys the UI state reference;
- reading manga inside Komikku may detach the Source Evaluation screen model;
- switching apps may put the process under memory pressure;
- there is no notification or durable progress;
- the user has no reliable cancel entry point outside the screen.

WorkManager with foreground notification gives:

- progress that survives screen navigation;
- a visible Android notification while work is running;
- cancel support;
- compatibility with existing Komikku job patterns.

### Minimal Viable Background Implementation

The smallest useful implementation should support:

1. Start evaluation as unique work.
2. Store a durable run ID.
3. Store selected options for the run.
4. Store the candidate list snapshot or enough keys to reconstruct it safely.
5. Worker runs one extension at a time using existing `SourceEvaluationRunner` logic or an extracted engine.
6. Worker writes progress to a durable state store.
7. Source Evaluation screen observes durable state instead of only in-memory runner state.
8. Notification shows progress and cancel action.
9. Cancel action cancels WorkManager job and triggers cleanup where possible.

### Candidate Snapshot Strategy

Do not pass large candidate lists through WorkManager input data.

Recommended approach:

Add a SQLDelight table for run candidates:

```sql
CREATE TABLE source_evaluation_run_candidate(
    run_id TEXT NOT NULL,
    position INTEGER NOT NULL,
    pkg_name TEXT NOT NULL,
    signature_hash TEXT NOT NULL,
    extension_name TEXT NOT NULL,
    lang TEXT NOT NULL,
    repo_name TEXT,
    is_nsfw INTEGER AS Boolean NOT NULL,
    PRIMARY KEY(run_id, position)
);
```

Add run state table:

```sql
CREATE TABLE source_evaluation_run_state(
    run_id TEXT NOT NULL PRIMARY KEY,
    status TEXT NOT NULL,
    total_count INTEGER NOT NULL,
    completed_count INTEGER NOT NULL,
    failed_count INTEGER NOT NULL,
    skipped_count INTEGER NOT NULL,
    installer_mode TEXT NOT NULL,
    batch_size INTEGER NOT NULL,
    prompt_heavy_cleanup_allowed INTEGER AS Boolean NOT NULL,
    current_extension_name TEXT,
    current_source_name TEXT,
    current_phase TEXT,
    error_message TEXT,
    started_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    finished_at INTEGER
);
```

The worker can reconstruct `Extension.Available` from current `availableExtensionsFlow` by package + signature. If not found, record an error for that candidate and continue.

Alternative if SQLDelight scope is too large:

- Use app-state preferences / JSON for the active run snapshot.
- This is acceptable only as a temporary v0.6.19 bridge, but SQLDelight is cleaner and testable.

### Extract Runner Engine

Current `SourceEvaluationRunner` owns both execution and in-memory state. For WorkManager, extract reusable execution logic.

Suggested refactor:

- Keep `SourceEvaluationRunner` as a UI-facing wrapper only if needed.
- Extract core logic into:

```kotlin
class SourceEvaluationEngine(...)
```

Responsibilities:

- evaluate a list of candidates one at a time;
- install/wait/probe/score/cleanup;
- write probe markers;
- upsert evaluation records;
- call a progress callback after state changes.

Progress callback:

```kotlin
interface SourceEvaluationProgressSink {
    suspend fun update(state: SourceEvaluationQueueState)
    suspend fun isCancelled(): Boolean
}
```

Screen runner sink:

- updates `MutableStateFlow`.

Worker sink:

- writes to `source_evaluation_run_state`;
- updates notification progress;
- checks WorkManager cancellation.

This avoids duplicating the install/probe/scoring logic.

### SourceEvaluationJob

Implement a `CoroutineWorker` similar to `SyncDataJob`.

Skeleton behavior:

```kotlin
class SourceEvaluationJob(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        setForegroundSafely()
        val runId = inputData.getString(KEY_RUN_ID) ?: return Result.failure()
        // load run state + candidates
        // run SourceEvaluationEngine
        // mark complete/failed/cancelled
    }

    override suspend fun getForegroundInfo(): ForegroundInfo { ... }
}
```

Companion helpers:

```kotlin
fun start(context: Context, runId: String)
fun cancel(context: Context)
fun isRunning(context: Context): Boolean
```

Use unique work:

- tag: `SourceEvaluationJob`
- unique name: `SourceEvaluationJob:active`
- policy: `KEEP` or `REPLACE` depending on desired behavior

Recommended:

- Use `KEEP` to prevent accidental double runs.
- If user taps start while a run is active, show existing progress instead of starting another.

### Notification

Add notification IDs/channel use.

Recommended:

- Reuse `Notifications.CHANNEL_COMMON` or add a low-importance Source Evaluation channel.
- Add IDs:

```kotlin
const val ID_SOURCE_EVALUATION_PROGRESS = -801
const val ID_SOURCE_EVALUATION_COMPLETE = -802
```

If adding a channel:

```kotlin
const val CHANNEL_SOURCE_EVALUATION = "source_evaluation_channel"
```

Notification should show:

- title: `Source evaluation running`
- text: current extension/source or `Evaluating sources...`
- progress: completed / total if total known
- action: Cancel
- content intent: open Source Evaluation screen if easy; otherwise open app main activity

If cancel action is too much for this pass, at least make cancellation available from the Source Evaluation screen and keep notification dismiss behavior safe.

### Screen Integration

`SourceEvaluationScreenModel` should no longer own the only active runner state.

Recommended behavior:

- On init, observe active run state from repository.
- If a run is active, show progress card even after returning to the screen.
- Start button creates run snapshot + enqueues worker.
- Cancel button cancels worker.
- Reset clears completed run state only when not running.

`SourceEvaluationQueueState` can remain the UI model, but it should be mapped from stored run state.

### Reading Manga While Evaluation Runs

Once evaluation is WorkManager-backed, leaving Source Evaluation to read manga should not stop the job. The notification remains visible, and returning to Source Evaluation shows current progress.

Important: evaluation installs/removes extensions and performs network probes. It may still consume bandwidth and device resources. Keep the one-at-a-time evaluation behavior and existing delays/timeouts.

### App Switching

A foreground WorkManager job should continue while the app is backgrounded, subject to Android restrictions. Use `setForegroundSafely()` like `SyncDataJob`.

If Android refuses foreground work, the job should fail gracefully and write a clear error state rather than leaving the UI stuck on running.


## Part 4: Notification Deep Link, Remaining Count, and Installed Exclusion

### Notification Tap Behavior

Current implementation finding:

- `SourceEvaluationNotifier.buildProgressNotification(...)` builds a notification but does not set a `contentIntent`.
- Tapping the Source Evaluation notification therefore cannot reliably return the user to the Source Evaluation screen.

Required behavior:

- While Source Evaluation is running, tapping the Android notification must open Komikku directly to the Source Evaluation screen or the closest available navigation route that immediately shows Source Evaluation progress.
- If a run is active, the screen should show the active progress state.
- If the run already completed or failed, tapping should still open Source Evaluation and show the latest summary/results when possible.

Implementation options:

1. Preferred: add an internal deep-link route for Source Evaluation.
   - Add an app-internal URI such as `komikku://kmk/source-evaluation`.
   - Update `MainActivity` deep-link handling to navigate to Browse > For You settings > Source Evaluation, or directly push `SourceEvaluationScreen` if the navigator setup allows it.
   - Add a helper such as `NotificationReceiver.openSourceEvaluationPendingActivity(context)` if that matches existing notification conventions.
2. Acceptable fallback: make the notification open Komikku main activity with an explicit extra such as `EXTRA_OPEN_SOURCE_EVALUATION = true`, then route after startup.

Notification details:

- Use `PendingIntent.FLAG_UPDATE_CURRENT`.
- Use immutable pending intents on modern Android.
- Preserve the existing cancel/dismiss behavior.
- Do not start a new evaluation when the notification is tapped.

### Remaining Uninstalled/Unevaluated Count

Required behavior:

- Show a simple numeric count for extensions that are:
  - available from enabled extension repos;
  - not currently installed;
  - not untrusted;
  - allowed by the selected recommendation languages;
  - allowed by NSFW/explicit filters;
  - not disliked/blocked/quarantined;
  - not already evaluated, unless `reEvaluateStale` makes the prior evaluation eligible again.
- The count should update after each evaluation batch completes because newly evaluated extensions should no longer count as unevaluated when `skipAlreadyEvaluated` is enabled.

Recommended wording:

- `Unassessed extensions remaining: %d`
- If `skipAlreadyEvaluated` is disabled, use wording that does not imply the count is hiding evaluated items, such as `Eligible extensions available: %d`.

Implementation notes:

- Do not compute this count independently from scratch in UI code.
- Use the same filtering path as `SourceEvaluationCandidateFilter.buildPool(...)` and `SourceEvaluationCandidateFilter.applyOptions(...)`.
- The current `buildPool(...)` already excludes installed package names via `if (ext.pkgName in installedPkgNames) continue`. Preserve this.
- Add a field to `SourceEvaluationScreenModel.CandidateDiagnostics`, for example:

```kotlin
val uninstalledUnevaluatedRemainingCount: Int = 0
```

- Populate it from the same `FilterResult`/candidate list used by the Start Evaluation button.
- Show it inside `CandidateDiagnosticsRow(...)` near the existing available/evaluated-hidden counts.
- Include the count in copied diagnostics.

### Installed Extensions Must Not Appear In Source Evaluation Suggestions

Current implementation finding:

- `GetSourceEvaluationCandidates` combines `availableExtensionsFlow`, `installedExtensionsFlow`, and `untrustedExtensionsFlow`.
- `SourceEvaluationCandidateFilter.buildPool(...)` receives `installedPkgNames` and skips installed packages.

Required behavior:

- Preserve that installed-package exclusion.
- Ensure every new Source Evaluation suggestions/count UI uses the shared candidate filter result.
- Do not show installed extensions in the Source Evaluation section, even if they still exist in `availableExtensionsFlow`.
- If an extension is installed after being suggested, it should disappear from Source Evaluation suggestions after the extension list refreshes.

Test requirements:

- Add/update `SourceEvaluationCandidateFilter` tests proving installed package names are excluded.
- Add a test for the remaining-count calculation proving installed packages are not counted.
- Add a test proving an already evaluated extension is not counted when `skipAlreadyEvaluated = true`.

## Part 5: Connectivity Recovery and Extension Repo Handling

### Offline / Internet Disconnect Handling

Current implementation findings:

- Komikku already has `ExceptionFormatter.formattedMessage`, which maps `UnknownHostException` plus `!context.isOnline()` to the existing `exception_offline` string.
- Some screens already expose retry actions for paged source errors.
- `ExtensionApi.getExtensions(extRepo)` currently catches `Throwable`, logs the repo failure, and returns `emptyList()`. This makes repo/offline failures look like no available extensions instead of a clear network problem.

Required behavior:

- If Wi-Fi/internet disconnects while using internet-backed features, the app should show a clear offline message rather than silently showing missing/empty results.
- Affected flows should include at least:
  - extension repo refresh;
  - extension search/list loading;
  - Source Evaluation candidate loading;
  - Source Evaluation probing;
  - recommendation/global/source searches where practical in this pass.
- When connectivity returns, the user should be able to refresh/retry without force-closing and reopening Komikku.

Implementation guidance:

- Add a lightweight shared connectivity observer/helper if one does not already exist, backed by `ConnectivityManager.NetworkCallback`.
- Keep using `context.isOnline()` and `ExceptionFormatter` where synchronous formatting is enough.
- For extension repos, do not let `ExtensionApi.getExtensions(...)` reduce all failures to `emptyList()` without surfacing error state somewhere. Add one of:
  - a result wrapper containing repo successes and repo failures; or
  - a separate repo failure diagnostics flow/state in `ExtensionManager`/screen model; or
  - explicit offline detection before/after `findExtensions()` and a user-visible error in the relevant screen.
- If all repos fail because the device is offline, show `No Internet connection` plus a retry/refresh action.
- If one repo fails but others succeed, show a non-blocking partial-failure message where appropriate.

Source Evaluation specifics:

- Do not start a new evaluation batch while offline. Show a clear message and keep the Start button disabled or guarded.
- If connectivity drops mid-run:
  - stop or pause the run cleanly;
  - write a run-level error such as `No Internet connection`;
  - do not score the current source as poor purely because the network is offline;
  - keep already completed evaluations intact;
  - allow retry once connection returns.
- If WorkManager is used, the worker can return retry/failure depending on whether the state can be resumed safely. For this project, a clear failed/paused state with manual retry is preferable to an uncontrolled automatic retry loop.

Manual test requirements:

1. Open Extensions / Source Evaluation while connected.
2. Disable Wi-Fi/internet.
3. Refresh extension list or start Source Evaluation.
4. Confirm visible offline message appears.
5. Re-enable Wi-Fi/internet.
6. Tap retry/refresh.
7. Confirm results load without restarting the app.

### Extension Repository Handling

Known repo:

- Keiyoushi index URL: `https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json`

Current implementation findings:

- Komikku already supports extension repository management.
- `CreateExtensionRepo.await(indexUrl)` accepts only HTTPS URLs ending in `/index.min.json`.
- It derives the base URL by removing `/index.min.json`.
- It validates repo details by fetching repo metadata from the derived base URL.
- Existing UI exists in `ExtensionReposScreen` and related components.
- Backups already include `BackupExtensionRepos`.

Required behavior:

- Do not assume Keiyoushi is the only possible compatible repo.
- Do not add unknown repos by default without validation.
- Make existing manual repo management discoverable enough that the user can add a compatible repo URL.
- If adding preset/default repos, only include repos that have been explicitly vetted for:
  - HTTPS `index.min.json`;
  - compatible `repo.json` metadata;
  - compatible extension package format/lib version;
  - signing key fingerprint availability;
  - reasonable maintenance/reliability.

Recommended implementation:

- Keep Keiyoushi as the known/default repo unless a vetted alternative list is provided.
- Improve the repo screen or Source Evaluation settings link to expose:
  - current enabled repos;
  - disabled repos;
  - manual add repo action;
  - repo refresh action;
  - clear error if a repo is unreachable/offline/invalid.
- Add optional repo diagnostics:
  - repo name;
  - base URL;
  - last refresh status;
  - extension count loaded from that repo if cheaply available.
- If a future vetted preset list is added, keep it small and transparent. Do not silently add all presets.

Documentation requirement:

- Record that alternative repos are supported through the existing Extension Repo system, but no broad default repo list should be shipped without validation.
- If Claude finds and verifies any reliable compatible repo, document the exact URL, metadata compatibility, signing fingerprint behavior, and reason it is safe/useful.

## Suggested Implementation Order

### Phase 1: Shizuku UX Cleanup

Files:

- `SourceEvaluationScreen.kt`
- `SourceEvaluationScreenModel.kt`
- `SourceEvaluationInstallerPolicy.kt` if needed
- `i18n-kmk/strings.xml`
- tests for installer-policy visibility helper if extracted

Steps:

1. Add `showShizukuSetup` state.
2. Show Shizuku setup card only when selected/relevant/expanded.
3. Add compact Shizuku setup toggle.
4. Ensure Private mode does not show Shizuku not-installed messaging.
5. Ensure Current mode only shows Shizuku issues if global installer is Shizuku.

### Phase 2: Safety Diagnostics UI Demotion

Files:

- `SourceEvaluationScreen.kt`
- `SourceEvaluationScreenModel.kt` only if new state is needed
- `i18n-kmk/strings.xml`

Steps:

1. Remove `unsafe_sources` and `blocked_packages` cards from the top of the list.
2. Add compact `SafetyDiagnosticsRow` below core options/candidate diagnostics.
3. Keep dialogs and clear/remove actions.
4. Confirm screen opens with the main controls first.

### Phase 3: Background Execution

Files likely affected:

- `SourceEvaluationRunner.kt`
- new `SourceEvaluationEngine.kt`
- new `SourceEvaluationJob.kt`
- new `SourceEvaluationNotifier.kt`
- `SourceEvaluationScreenModel.kt`
- `SourceEvaluationScreen.kt`
- SQLDelight schema/migrations for run state and candidates
- data/domain repositories/interactors for run state if SQLDelight is used
- `Notifications.kt`
- `NotificationReceiver.kt` if adding cancel action
- `i18n-kmk/strings.xml`

Steps:

1. Add run state/candidate storage.
2. Extract engine from runner.
3. Implement WorkManager job.
4. Add notification progress.
5. Make screen start/cancel/reset talk to job/repository.
6. Preserve old runner only if needed for tests, or remove after migration.
7. Add tests for run state mapping, start gating, and cancellation policy.


### Phase 4: Notification Deep Link and Candidate Count

Files likely affected:

- `SourceEvaluationNotifier.kt`
- `SourceEvaluationJob.kt`
- `NotificationReceiver.kt` or `MainActivity.kt`
- `SourceEvaluationScreenModel.kt`
- `SourceEvaluationScreen.kt`
- `SourceEvaluationCandidateFilter.kt`
- `SourceEvaluationCandidateFilterTest.kt`
- `i18n-kmk/strings.xml`

Steps:

1. Add a notification content intent that opens Source Evaluation.
2. Add route/deep-link handling if no suitable route exists.
3. Add remaining uninstalled/unevaluated count to candidate diagnostics.
4. Ensure count and displayed suggestions use the same shared candidate filter.
5. Add regression tests for installed exclusion and count behavior.

### Phase 5: Connectivity Recovery and Repo UX

Files likely affected:

- `ExtensionApi.kt`
- `ExtensionManager.kt` or extension list screen model if needed
- `ExtensionReposScreen.kt`
- `ExtensionReposScreenModel.kt`
- `SourceEvaluationScreenModel.kt`
- `SourceEvaluationRunner.kt` / `SourceEvaluationJob.kt`
- shared connectivity helper under `util/system` or presentation layer
- `ExceptionFormatter.kt` only if formatting gaps are found
- `i18n-kmk/strings.xml`

Steps:

1. Identify where extension repo failures are swallowed as empty lists.
2. Surface offline/repo-failure state to UI without breaking partial repo success.
3. Add retry/refresh behavior after reconnect.
4. Guard Source Evaluation start while offline.
5. Stop/pause Source Evaluation cleanly if offline mid-run.
6. Expose existing extension repo management clearly; do not add unvetted repos by default.
7. Document any verified alternative repos if found.

## Testing Plan

### Unit Tests

Add/update tests for:

0. Taste profile confidence:
   - empty profile reports insufficient evidence;
   - few rated manga/tags reports insufficient evidence;
   - enough rated manga/tags reports sufficient evidence;
   - source evaluation can still run with insufficient evidence but labels confidence as low.

1. Shizuku visibility policy:
   - Private mode hides Shizuku setup by default.
   - Shizuku mode shows Shizuku setup.
   - Current mode with global Shizuku shows Shizuku setup/status.
   - Current mode with global Private does not show Shizuku status.

2. Installer policy:
   - Private readiness ignores Shizuku state.
   - Shizuku readiness still requires installed/running/permission.

3. Safety diagnostics display helper if extracted:
   - zero counts hides row.
   - unsafe count shows quarantined action.
   - blocked count shows blocked action.

4. Background run state mapping:
   - stored run state maps to `SourceEvaluationQueueState` correctly.
   - completed run can reset.
   - running run cannot start a duplicate.

5. Candidate snapshot:
   - preserves order.
   - caps to selected batch size or stores full list with worker cap.
   - handles missing current available extension metadata gracefully.

### Manual Tests

#### Shizuku UX

1. Open Source Evaluation with Private selected and Shizuku not installed.
2. Confirm no prominent `Shizuku not installed` prompt appears.
3. Select Shizuku.
4. Confirm Shizuku setup/status appears.
5. Select Private again.
6. Confirm Shizuku card hides or becomes secondary.

#### Safety Diagnostics

1. Have quarantined/blocked entries present.
2. Open Source Evaluation.
3. Confirm main evaluation controls appear first.
4. Confirm safety diagnostics are accessible lower on the page.
5. Open quarantined/blocked dialogs from diagnostics.
6. Confirm clear/remove/allow actions still work.

#### Background Evaluation

1. Start a small evaluation batch.
2. Navigate away to another Komikku screen.
3. Confirm evaluation continues.
4. Open a manga and read briefly.
5. Return to Source Evaluation.
6. Confirm progress/results are current.
7. Start a batch and switch to another Android app.
8. Confirm notification remains visible and progress continues.
9. Cancel from Source Evaluation and confirm cleanup occurs.
10. If notification cancel action is implemented, cancel from notification and confirm UI reflects cancellation.
11. Tap the running Source Evaluation notification and confirm it opens Source Evaluation/progress.
12. Install a suggested extension, refresh candidate state, and confirm it no longer appears in Source Evaluation suggestions.
13. Confirm unassessed remaining count decreases after a completed evaluation batch.
14. Disconnect Wi-Fi while on Extensions and Source Evaluation screens, then reconnect and retry without restarting the app.

## Risk Assessment

### Shizuku UI Cleanup Risk

Low. Mostly conditional rendering and state cleanup.

Main risk: hiding Shizuku controls too aggressively when Current uses global Shizuku.

Mitigation: explicitly detect Current + global Shizuku and show Shizuku setup/status then.

### Safety Diagnostics UI Risk

Low. Functionality remains; only placement changes.

Main risk: users may not notice a block that matters.

Mitigation: keep compact count row visible when nonzero, just not as large cards at the top.

### Background Execution Risk

Medium to high. It touches runner lifetime, progress state, cancellation, notifications, and potentially DB schema.

Main risks:

- duplicate active runs;
- cleanup not running on cancellation;
- WorkManager input too large if candidates are passed incorrectly;
- notification/channel mistakes;
- source evaluation continuing with stale candidates after extension repo changes;
- app process restrictions when trying to set foreground work.

Mitigations:

- use unique WorkManager work;
- store candidate snapshots in SQLDelight rather than input data;
- keep one-at-a-time execution;
- reuse `setForegroundSafely()`;
- preserve existing timeouts and probe markers;
- add tests around run state and duplicate-start prevention.

## Recommendation

Proceed with v0.6.19 in staged order.

The Shizuku and safety-diagnostics UI changes should definitely be implemented. They are small and directly address the current UX complaints.

Background execution is valuable and feasible, but it is the only large part of this plan. If implementation time or risk becomes too high, Claude should complete Phases 1 and 2 first, document Phase 3 as partially implemented or deferred, and not ship a fragile half-background runner. A robust WorkManager-backed design is better than a screen-owned coroutine that merely appears to keep running.

## Documentation Requirements

After implementation, Claude must create:

`docs/recommendations/KMK_RECS_V0_6_19_SOURCE_EVALUATION_UX_AND_BACKGROUND_EXECUTION_IMPLEMENTATION.md`

The implementation report must include:

- exact files changed;
- whether background execution was fully implemented or deferred/staged;
- tests run and results;
- APK name/path;
- user-facing behavior changes;
- any known limitations.

Also update:

- `docs/recommendations/README.md`
- `docs/recommendations/NEXT_WORK.md`
- `RECOMMENDATION_VERSIONING.md`
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`

User-facing What's New should mention only actual app features, not internal documentation work.

If these follow-up requirements are implemented after the first v0.6.19 APK, Claude should either:

- update the existing v0.6.19 implementation report with a clearly dated `Follow-up fixes` section; or
- create `docs/recommendations/KMK_RECS_V0_6_19_SOURCE_EVALUATION_UX_AND_BACKGROUND_EXECUTION_FOLLOWUP_IMPLEMENTATION.md`.

Do not create a new major feature number for this work. It remains part of the v0.6 Source Evaluation / extension workstream.



