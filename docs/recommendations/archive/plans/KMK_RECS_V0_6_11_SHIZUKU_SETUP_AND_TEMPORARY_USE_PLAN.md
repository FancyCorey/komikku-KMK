# KMK-Recs v0.6.11: Shizuku Setup And Temporary Use Plan

Date: 2026-06-19

Feature version target: KMK-Recs v0.6.11

Status: implementation plan only. Do not modify application code until the user explicitly approves implementation.

## Goal

Improve the Source Evaluation / extension installation workflow by making Shizuku easier to set up, use temporarily, and stop using when finished.

The user goal is:

1. Install or open Shizuku when extension installation/evaluation needs it.
2. Use Shizuku temporarily during extension installation or source evaluation.
3. Stop Komikku from using Shizuku afterward.
4. Optionally uninstall Shizuku through Android's normal uninstall confirmation.

This must be implemented honestly within Android's security model. Komikku should not pretend it can silently install, uninstall, start, stop, or revoke Shizuku outside user-controlled Android/Shizuku flows.

## Current Code State

### Existing Shizuku installer support

Komikku already supports Shizuku as an extension installer mode.

Relevant files:

```text
app/src/main/java/eu/kanade/domain/base/BasePreferences.kt
app/src/main/java/eu/kanade/domain/base/ExtensionInstallerPreference.kt
app/src/main/java/eu/kanade/tachiyomi/extension/installer/ShizukuInstaller.kt
app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionInstallService.kt
app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionInstaller.kt
app/src/main/java/exh/recs/evaluation/SourceEvaluationInstallerPolicy.kt
app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt
app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt
```

`BasePreferences.ExtensionInstaller` includes:

```kotlin
SHIZUKU(MR.strings.ext_installer_shizuku, false)
```

`ExtensionInstallService` creates `ShizukuInstaller` when the installer mode is `SHIZUKU`.

`ShizukuInstaller` already:

- checks `Shizuku.pingBinder()`,
- requests Shizuku permission if needed,
- binds Komikku's Shizuku user service,
- unbinds Komikku's Shizuku user service in `onDestroy()`.

### Existing Advanced Settings behavior

File:

```text
app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsAdvancedScreen.kt
```

The extension installer preference already checks whether Shizuku is installed. If the user selects Shizuku while it is missing, an alert appears and can open:

```text
https://shizuku.rikka.app/download
```

Current limitation: this is only a basic missing-Shizuku dialog. It does not provide a dedicated setup/status section or an easy "stop using Shizuku" action.

### Existing Source Evaluation behavior

File:

```text
app/src/main/java/exh/recs/evaluation/SourceEvaluationInstallerPolicy.kt
```

The current policy correctly states:

```text
"stop using Shizuku after evaluation" means Komikku stops using Shizuku as its
installer mode for the run. It does NOT mean stopping the Shizuku app/service or revoking
Shizuku permission - those belong to Android, not Komikku.
```

This principle should remain.

File:

```text
app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt
```

The current Shizuku readiness check only checks whether the package exists:

```kotlin
context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
```

It currently passes:

```kotlin
shizukuBinderAlive = false
shizukuPermissionGranted = false
```

That makes Shizuku effectively unavailable in the Source Evaluation policy even when installed, because the model is not using the Shizuku API to check binder and permission state.

## Android Reality Check

### Feasible

Komikku can:

- detect whether Shizuku is installed,
- open Shizuku's official download/setup page,
- open the installed Shizuku app,
- open Android's uninstall confirmation for the Shizuku package,
- set Komikku's extension installer preference away from Shizuku,
- use Shizuku as a temporary installer override for Source Evaluation runs,
- unbind Komikku's Shizuku user service after installs through existing `ShizukuInstaller.onDestroy()`,
- show clear readiness/status UI.

### Not feasible or not appropriate

Komikku should not claim it can:

- silently install Shizuku without Android/user confirmation,
- silently uninstall Shizuku without Android/user confirmation,
- reliably start Shizuku's privileged service by itself,
- reliably stop Shizuku's privileged service by itself,
- revoke Shizuku permissions silently,
- guarantee Shizuku is no longer running system-wide after Komikku stops using it.

The app can provide shortcuts and state changes, but final install/uninstall/start/stop control belongs to Android and Shizuku.

## Product Behavior

### In Source Evaluation

Add a compact Shizuku setup/status section near the installer mode selector.

It should show one of these states:

- `Not installed`
- `Installed but not running`
- `Running, permission needed`
- `Ready`
- `Selected for this run`
- `Not being used by Komikku`

Actions should be context-sensitive:

- If Shizuku is not installed:
  - show `Install Shizuku`
  - opens official Shizuku download page or a safe external install route.

- If Shizuku is installed:
  - show `Open Shizuku`
  - launches package `moe.shizuku.privileged.api` if available.

- If Shizuku is installed but Komikku is not using it:
  - show `Use Shizuku for this evaluation`
  - sets Source Evaluation `installerMode` to `SHIZUKU`, not the global installer preference.

- If Komikku is currently set to use Shizuku for evaluation:
  - show `Stop using Shizuku`
  - sets Source Evaluation `installerMode` back to `PRIVATE` if available, otherwise `CURRENT`.

- If Shizuku is installed:
  - optionally show `Uninstall Shizuku`
  - launches Android's uninstall confirmation for package `moe.shizuku.privileged.api`.
  - after returning, refresh Shizuku state and ensure Source Evaluation is not still set to Shizuku if the package is gone.

### In Advanced Settings

Optionally add a small `Shizuku setup` text preference under the existing Extensions group.

This should not duplicate Source Evaluation controls too heavily. The Source Evaluation screen is the primary workflow location because the user's use case is temporary source evaluation and extension installation.

Advanced Settings can provide:

- current Shizuku installed/running status,
- open setup/download,
- stop using Shizuku globally by setting extension installer to default/private/package installer,
- uninstall shortcut.

If this becomes too much for one pass, defer the Advanced Settings section and focus on Source Evaluation first.

## Implementation Plan

### Phase 1: Add a Shizuku state helper

Create a small helper so Shizuku status logic is not duplicated across screens.

Recommended file:

```text
app/src/main/java/exh/recs/evaluation/ShizukuSetupHelper.kt
```

or, if it should be shared beyond recommendations:

```text
app/src/main/java/eu/kanade/tachiyomi/util/system/ShizukuUtil.kt
```

Recommendation: use the broader util path only if the helper is also used by Advanced Settings in this pass. If it is Source Evaluation only, keep it under `exh/recs/evaluation`.

Suggested state model:

```kotlin
data class ShizukuSetupState(
    val installed: Boolean,
    val binderAlive: Boolean,
    val permissionGranted: Boolean,
    val canOpenApp: Boolean,
)
```

Suggested helper functions:

```kotlin
fun readState(context: Context): ShizukuSetupState
fun openDownload(context: Context)
fun openApp(context: Context): Boolean
fun openUninstall(context: Context): Boolean
fun stopUsingAsGlobalInstaller(basePreferences: BasePreferences)
```

Implementation details:

- Installed check can reuse `context.isShizukuInstalled`.
- Binder check can use `rikka.shizuku.Shizuku.pingBinder()` but must be wrapped in `try/catch`.
- Permission check can use `Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED`, also wrapped in `try/catch`.
- `openDownload` should use the existing official URL:

```text
https://shizuku.rikka.app/download
```

- `openApp` should use `packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")`.
- `openUninstall` should use Android's standard package uninstall intent:

```kotlin
Intent(Intent.ACTION_DELETE).apply {
    data = Uri.parse("package:moe.shizuku.privileged.api")
}
```

Add `FLAG_ACTIVITY_NEW_TASK` only if launching from a non-activity context requires it.

Do not attempt shell uninstall, root uninstall, or background service control.

### Phase 2: Use real Shizuku readiness in Source Evaluation policy

Update:

```text
app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt
```

Replace the hardcoded:

```kotlin
shizukuBinderAlive = false
shizukuPermissionGranted = false
```

With values from the helper:

```kotlin
val shizukuState = ShizukuSetupHelper.readState(context)
```

Then pass:

```kotlin
shizukuInstalled = shizukuState.installed
shizukuBinderAlive = shizukuState.binderAlive
shizukuPermissionGranted = shizukuState.permissionGranted
```

This is important because otherwise any Shizuku UI will still show unavailable even when the user has installed and started Shizuku.

Add a method:

```kotlin
fun refreshShizukuState()
```

or reuse `refreshInstallerPolicy()` after returning from setup/uninstall flows.

### Phase 3: Add temporary Source Evaluation actions

Update:

```text
app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt
```

Add methods:

```kotlin
fun useShizukuForEvaluation()
fun stopUsingShizukuForEvaluation()
fun openShizukuSetup()
fun openShizukuApp()
fun uninstallShizuku()
```

Behavior:

- `useShizukuForEvaluation()` sets `options.installerMode = SHIZUKU` and refreshes policy.
- `stopUsingShizukuForEvaluation()` sets `options.installerMode = PRIVATE` if private is available, otherwise `CURRENT`, then refreshes policy.
- `openShizukuSetup()` opens official setup/download page.
- `openShizukuApp()` opens Shizuku package if installed; if not installed, fallback to setup/download.
- `uninstallShizuku()` launches Android uninstall confirmation. It should not assume uninstall completed immediately.

Safety:

- If the evaluation queue is running, disable setup/uninstall actions except perhaps `Open Shizuku`.
- Do not allow `uninstallShizuku()` while source evaluation is running.
- After uninstall intent returns or when screen resumes/recomposes, refresh policy.

### Phase 4: Add Source Evaluation UI

Update:

```text
app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt
```

Add a new card below `InstallerModeSelector` and above the Start button:

```text
Shizuku setup
```

Do not overbuild the UI. It should be a compact utility card.

Suggested content:

- status text:
  - "Not installed"
  - "Installed but not running"
  - "Running - permission needed"
  - "Ready"
  - "Komikku is not using Shizuku"
  - "Komikku will use Shizuku for this evaluation run"

- buttons:
  - `Install Shizuku` when not installed
  - `Open Shizuku` when installed
  - `Use for this run` when installed and not selected
  - `Stop using` when selected
  - `Uninstall Shizuku` when installed and evaluation is not running

Use existing Material components:

- `Card`
- `Text`
- `TextButton`
- `OutlinedButton`
- `Button` only for the primary context action.

Avoid in-app text that over-explains every Android limitation, but include one concise safety note:

```text
Komikku can stop using Shizuku, but Android/Shizuku controls whether the Shizuku service keeps running.
```

If localization work is too large, add base English strings first in KMR/base resources following the project's existing KMK string pattern, and document that translations are base-only.

### Phase 5: Global installer stop-using behavior

If the user's global extension installer preference is currently `SHIZUKU` and Shizuku is uninstalled or the user taps a global `Stop using Shizuku` action, set it to a safe fallback:

Preferred fallback:

1. `PRIVATE`, if available in this build.
2. `PACKAGEINSTALLER`, if available and not blocked by MIUI check.
3. `LEGACY`.

Do not blindly set `PRIVATE` in release builds if `ExtensionInstallerPreference.entries` excludes it.

`ExtensionInstallerPreference.get()` already falls back if Shizuku is selected but not installed:

```kotlin
if (!context.isShizukuInstalled) return defaultValue()
```

The new action should make this explicit and user-controlled instead of relying only on lazy fallback.

### Phase 6: Keep Source Evaluation temporary by default

Do not mutate the global installer preference just because the user selects Shizuku for Source Evaluation.

Source Evaluation should continue using:

```kotlin
ExtensionManager.installExtension(ext, installerOverride)
```

where `installerOverride` comes from `SourceEvaluationInstallerPolicy.effectiveInstallerOverride()`.

This keeps Shizuku temporary for evaluation runs.

After a run completes/cancels/fails:

- Source Evaluation should not change global installer preference.
- If the local Source Evaluation mode is still `SHIZUKU`, it may remain selected on screen, but it should be clear that this is only for evaluation.
- If the user taps `Stop using Shizuku`, local mode should switch away from `SHIZUKU`.

Optional: after a completed Shizuku evaluation run, show a small post-run action:

```text
Stop using Shizuku
```

This is useful because it matches the user's desired workflow.

### Phase 7: Permission handling

`ShizukuInstaller` already requests permission when an install starts:

```kotlin
Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
```

For this pass, do not create a second permission request system unless necessary.

Instead:

- show `permission needed`,
- let `Open Shizuku` and the first Shizuku-backed install trigger/resolve permission naturally through existing installer behavior,
- if adding an explicit `Request permission` button, ensure it uses a separate request code and listener safely, and document it.

Recommendation: do not add explicit request-permission button in the first pass. It is a nice-to-have and can create lifecycle complexity.

### Phase 8: Documentation and versioning

Update:

```text
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/README.md
RECOMMENDATION_VERSIONING.md
app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt
```

Create implementation report after coding:

```text
docs/recommendations/KMK_RECS_V0_6_11_SHIZUKU_SETUP_AND_TEMPORARY_USE_IMPLEMENTATION.md
```

User-facing What's New should only describe the actual feature:

```text
Added Shizuku setup controls for Source Evaluation, including install/open shortcuts, temporary use for evaluation, stop-using action, and uninstall shortcut through Android.
```

Do not mention developer documentation changes in What's New.

## Tests

Add or update unit tests for pure logic where possible.

Recommended tests:

```text
app/src/test/java/exh/recs/evaluation/SourceEvaluationInstallerPolicyTest.kt
```

Test cases:

- Shizuku not installed -> unavailable.
- Shizuku installed but binder dead -> unavailable.
- Shizuku running but permission missing -> needs permission.
- Shizuku installed, running, permission granted -> ready.
- stop-using fallback prefers Private when available.
- stop-using fallback avoids Private when it is not in entries.

If `ShizukuSetupHelper` is pure enough, test its fallback selection logic separately. Do not try to unit-test Android package manager intents unless this project already has a pattern for it.

Run:

```text
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Manual verification:

1. Install APK.
2. Open Recommendation Settings.
3. Open Source Evaluation.
4. Confirm Shizuku setup card appears.
5. With Shizuku not installed, tap Install Shizuku and confirm it opens setup/download.
6. Install Shizuku manually.
7. Return to Komikku and confirm status refreshes.
8. Tap Open Shizuku and confirm Shizuku opens.
9. Start Shizuku manually from the Shizuku app.
10. Return to Komikku and confirm status becomes running/ready if permission is granted.
11. Select Use for this run.
12. Confirm Source Evaluation policy allows Shizuku when ready.
13. Tap Stop using Shizuku and confirm Source Evaluation mode changes away from Shizuku.
14. Tap Uninstall Shizuku and confirm Android uninstall confirmation appears.
15. If Shizuku is uninstalled, confirm Komikku no longer shows Shizuku as ready and does not keep using it.

## Acceptance Criteria

The feature is complete only if:

- Source Evaluation exposes clear Shizuku setup/status controls.
- Komikku can open the Shizuku download/setup page when missing.
- Komikku can open the installed Shizuku app.
- Komikku can launch Android's uninstall confirmation for Shizuku.
- Komikku can stop using Shizuku by switching Source Evaluation away from `SHIZUKU`.
- If global installer is `SHIZUKU`, a stop-using action can move it to a safe fallback.
- Source Evaluation uses real Shizuku binder/permission state instead of hardcoded false values.
- Evaluation still uses temporary installer override and does not silently mutate global installer settings.
- Running evaluation cannot be disrupted by uninstall/setup actions.
- Tests/build are run and documented.
- Implementation report is created.

## Non-Goals

Do not implement:

- silent Shizuku installation,
- silent Shizuku uninstallation,
- silent Shizuku service shutdown,
- silent Shizuku permission revocation,
- root shell management,
- ADB pairing setup automation,
- broad extension installer redesign,
- source-evaluation scoring changes.

## Risk Notes

- Android package uninstall is user-confirmed and may fail or be cancelled.
- Shizuku can be installed but not running; Komikku must show this clearly.
- `Shizuku.pingBinder()` and `Shizuku.checkSelfPermission()` must be wrapped in `try/catch` because Shizuku may not be installed, initialized, or reachable.
- If the user uninstalls Shizuku while Komikku is in `SHIZUKU` global installer mode, Komikku must not remain stuck in that mode.
- Source Evaluation should prefer `PRIVATE` where available because it better matches temporary extension evaluation and cleanup.

## Recommendation

Implement this as a Source Evaluation setup/hardening feature, not as a promise that Komikku can fully control Shizuku.

The best user experience is:

```text
Komikku helps install/open/use/uninstall Shizuku,
but Android/Shizuku remains responsible for starting, stopping, granting, and uninstall confirmation.
```

This gives the user the temporary workflow they want while keeping the implementation accurate, safe, and maintainable.

