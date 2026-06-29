# KMK-Recs v0.6.8 Evaluation Installer Override Plan

Status: planning. Do not implement until the user explicitly approves or provides this plan to Claude for implementation.

Related plan:

`docs/recommendations/KMK_RECS_V0_6_8_SOURCE_EVALUATION_QUEUE_PLAN.md`

## Purpose

Define how the source evaluation queue should use Shizuku or Private installer modes safely without permanently changing the user's normal Komikku extension installer preference.

The source evaluation queue may need to temporarily install and remove many candidate extensions. Normal Android package installer modes can require repeated confirmation prompts, which makes long evaluation runs impractical. Shizuku or Private installer modes are much better suited for this workflow.

However, the app should not silently leave Komikku configured to use Shizuku after evaluation unless the user explicitly chooses that as their normal installer.

## Key Question

Can Komikku add a `Use Shizuku installer` button before evaluation, use Shizuku for the evaluation run, then disable Shizuku afterward?

Answer:

- Yes, Komikku can stop using Shizuku as its selected extension installer after evaluation.
- No, Komikku should not try to stop the Shizuku service, uninstall Shizuku, or revoke Shizuku permission. That belongs to Android/Shizuku, not Komikku.

The correct interpretation of "disable Shizuku after done" is:

- restore Komikku's previous extension installer mode, or
- better, use Shizuku only as a temporary installer override for the evaluation queue and never change the global installer preference.

## Current Code Context

Existing preference:

`app/src/main/java/eu/kanade/domain/base/BasePreferences.kt`

```kotlin
fun extensionInstaller() = ExtensionInstallerPreference(context, preferenceStore)

enum class ExtensionInstaller {
    LEGACY,
    PACKAGEINSTALLER,
    SHIZUKU,
    PRIVATE,
}
```

Existing preference wrapper:

`app/src/main/java/eu/kanade/domain/base/ExtensionInstallerPreference.kt`

Important behavior:

- `SHIZUKU` is accepted only if Shizuku is installed.
- If Shizuku is not installed, setting/getting Shizuku falls back to the default installer.
- Default installer is usually `PACKAGEINSTALLER`, or `LEGACY` on MIUI package installer devices.

Existing installer:

`app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionInstaller.kt`

Current install path reads:

```kotlin
private val extensionInstaller = Injekt.get<BasePreferences>().extensionInstaller()
...
when (val installer = extensionInstaller.get()) {
    BasePreferences.ExtensionInstaller.LEGACY -> ...
    BasePreferences.ExtensionInstaller.PRIVATE -> ...
    else -> ExtensionInstallService.getIntent(..., installer)
}
```

This means the existing installer always uses the global installer preference.

## Preferred Design

Use a **per-evaluation installer override**, not a permanent global preference switch.

### Why This Is Better

Temporary override:

- does not change normal Komikku extension install behavior;
- does not leave the app stuck in Shizuku mode after a crash;
- avoids surprising the user;
- is easier to reason about in docs and UI;
- lets evaluation use Shizuku/Private while normal installs remain whatever the user selected.

Global preference switching is possible but should be fallback only.

## User-Facing Behavior

In the Source Evaluation screen, add installer mode controls:

- `Use current installer`
- `Use Private installer for this evaluation`
- `Use Shizuku installer for this evaluation`

Recommended default:

- If `PRIVATE` is available and stable: preselect `Private for this evaluation`.
- Else if Shizuku is installed/running/permission-ready: preselect `Shizuku for this evaluation`.
- Else use current installer with warnings and restrict batch size.

The UI should clearly say:

- `This only applies to this evaluation run. Your normal extension installer setting will not be changed.`

If temporary override cannot be implemented in first pass:

- use global preference switching with safe restore,
- show a clear message,
- persist enough state to restore on next app launch if possible.

## Shizuku Availability Checks

Before allowing `Use Shizuku installer for this evaluation`, verify:

- Shizuku app or Sui is installed:
  - current helper: `Context.isShizukuInstalled`
- Shizuku binder is available:
  - `Shizuku.pingBinder()`
- Komikku has permission:
  - `Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED`

If Shizuku is installed but permission is not granted:

- show a `Grant Shizuku permission` action;
- use the existing Shizuku permission request path where practical;
- do not start a large evaluation until permission is granted.

If Shizuku is not installed/running:

- disable Shizuku evaluation option;
- show explanatory text;
- optionally link to Shizuku setup/download, matching existing Advanced settings behavior.

## Private Installer Availability Checks

Before allowing `Use Private installer for this evaluation`, verify:

- `BasePreferences.ExtensionInstaller.PRIVATE` is present in `basePreferences.extensionInstaller().entries`;
- the current build supports Private installer;
- private extension loading/unloading works without restart, or document restart/refresh requirement.

Important:

- Private installer may be debug-only or hidden in stable release builds.
- For the user's debug APK workflow, Private may be ideal if it works reliably.

## Implementation Options

### Option A: True Temporary Installer Override

Recommended.

Modify the install path to allow an optional installer override for evaluation installs.

Potential changes:

1. Add overload to `ExtensionManager`:

```kotlin
fun installExtension(
    extension: Extension.Available,
    installerOverride: BasePreferences.ExtensionInstaller? = null,
): Flow<InstallStep>
```

2. Add overload to `ExtensionInstaller.downloadAndInstall`:

```kotlin
fun downloadAndInstall(
    url: String,
    extension: Extension,
    installerOverride: BasePreferences.ExtensionInstaller? = null,
): Flow<InstallStep>
```

3. In `ExtensionInstaller.installApk`, use:

```kotlin
val installer = installerOverride ?: extensionInstaller.get()
```

This may require threading the override through the download job because install happens after download.

Recommended shape:

```kotlin
fun downloadAndInstall(url: String, extension: Extension, installerOverride: ExtensionInstaller? = null): Flow<InstallStep> {
    ...
    val effectiveInstaller = installerOverride ?: extensionInstaller.get()
    ...
    installApk(downloadId, tmpFile, effectiveInstaller)
}

private fun installApk(
    downloadId: Long,
    tempFile: File,
    installer: BasePreferences.ExtensionInstaller,
) { ... }
```

Pros:

- cleanest behavior;
- no global preference mutation;
- survives cancellation better;
- no need to restore preference;
- less surprising.

Cons:

- requires modifying core extension install path carefully;
- must ensure normal install/update behavior is unchanged.

### Option B: Scoped Preference Switch With Finally Restore

Acceptable fallback if Option A is too invasive.

Process:

```kotlin
val previousInstaller = basePreferences.extensionInstaller().get()
try {
    basePreferences.extensionInstaller().set(BasePreferences.ExtensionInstaller.SHIZUKU)
    runEvaluationQueue()
} finally {
    basePreferences.extensionInstaller().set(previousInstaller)
}
```

Required safeguards:

- store previous installer in queue state;
- restore in `finally`;
- restore on cancellation;
- restore on app restart if queue was interrupted;
- show UI message that normal installer is temporarily being changed.

Pros:

- less code change to installer internals;
- uses existing install paths.

Cons:

- global app behavior changes during evaluation;
- crash can leave preference in Shizuku unless restore state exists;
- normal extension install launched during evaluation may use temporary mode;
- more surprising.

Recommendation:

- Use Option A if possible.
- Use Option B only for a first proof-of-concept if Option A is too risky.

## Uninstall / Cleanup After Evaluation

Clarify "disable Shizuku after done":

- For temporary installer override, no restore is needed.
- For preference switching, restore previous installer preference.
- Do not try to shut down Shizuku itself.

Cleanup of trial extensions:

### If Installed Via Private Installer

Preferred cleanup:

- remove private extension file using existing private extension removal logic;
- notify extension/source manager;
- refresh available/installed source state.

This should avoid Android uninstall prompts.

### If Installed Via Shizuku/System Package

Current uninstall behavior:

`ExtensionInstaller.uninstallApk(pkgName)` uses Android uninstall intent if package is installed.

That may still cause uninstall prompts.

Options:

1. Accept prompt-based uninstall for Shizuku/system-installed trial extensions.
2. Add Shizuku uninstall support if safe and available.
3. Strongly prefer Private installer for evaluation cleanup.

Recommended first implementation:

- prefer Private installer for evaluation;
- use Shizuku primarily if Private is unavailable;
- document that Shizuku install may still require manual/system uninstall handling unless a Shizuku uninstall path is implemented.

## Evaluation Queue Installer Policy

Before starting a queue:

1. Determine requested evaluation installer mode:
   - current
   - private override
   - Shizuku override

2. Validate mode:
   - Private available?
   - Shizuku installed/running/permission granted?
   - current installer prompt-heavy?

3. Apply batch size rules:
   - Private: allow 10/25/50/100.
   - Shizuku: allow 10/25/50, maybe 100 with warning.
   - PackageInstaller/Legacy: allow 5/10 only by default with warning.

4. Explain cleanup behavior:
   - Private: automatic cleanup expected.
   - Shizuku/system: install smoother, uninstall may still prompt unless supported.
   - Legacy/PackageInstaller: may prompt repeatedly.

## UI Copy Suggestions

Installer section title:

`Evaluation installer`

Options:

- `Use current installer`
- `Use Private installer for this run`
- `Use Shizuku installer for this run`

Helper text:

`Private or Shizuku is recommended for source evaluation. System installers may ask for confirmation for every extension.`

Temporary override note:

`This only applies to this evaluation run. Your normal extension installer setting will not be changed.`

Shizuku unavailable:

`Shizuku is not available. Install/start Shizuku and grant permission to use it for evaluation.`

Prompt-heavy warning:

`This installer may require manual confirmation for each install or uninstall. Use a small batch or switch to Private/Shizuku.`

## Tests

Add tests if practical for:

- temporary override chooses requested installer without changing global preference;
- global preference remains unchanged after evaluation run;
- fallback mode rejects Shizuku when unavailable;
- batch size restrictions differ by installer mode;
- preference-switch fallback restores previous installer on success;
- preference-switch fallback restores previous installer on failure/cancellation.

If direct installer tests are too Android-dependent:

- extract installer policy into a pure helper and test that helper.

Suggested helper:

```kotlin
object SourceEvaluationInstallerPolicy {
    fun validate(...)
    fun maxRecommendedBatchSize(...)
    fun requiresPromptWarning(...)
}
```

## Documentation Updates When Implemented

Update:

- `docs/recommendations/KMK_RECS_V0_6_8_SOURCE_EVALUATION_QUEUE_IMPLEMENTATION.md`
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/README.md`
- `RECOMMENDATION_VERSIONING.md`
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`

Document:

- whether Option A or Option B was used;
- how Shizuku availability is checked;
- whether Private installer is supported;
- whether uninstall cleanup is automatic;
- whether Shizuku uninstall exists;
- whether global installer preference is changed;
- how preference restore is guaranteed if Option B is used.

## Acceptance Criteria

This installer override work is complete only when:

- source evaluation can request Private or Shizuku installer behavior;
- normal Komikku installer preference is not permanently changed;
- if global preference switching is used, it is restored after success/failure/cancel;
- Shizuku availability and permission are checked before large runs;
- Private installer availability is checked before use;
- prompt-heavy installer modes warn and restrict large batches;
- documentation clearly distinguishes "stop using Shizuku" from "stop/revoke Shizuku itself";
- normal extension install/update behavior remains unchanged.

