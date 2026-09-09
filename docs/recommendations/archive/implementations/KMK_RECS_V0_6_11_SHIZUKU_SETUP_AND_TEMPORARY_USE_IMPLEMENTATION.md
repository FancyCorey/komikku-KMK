# KMK-Recs v0.6.11: Shizuku Setup And Temporary Use Implementation

Date: 2026-06-19

Feature version: KMK-Recs v0.6.11

## Summary

Added Shizuku setup controls to Source Evaluation. Users can now install/open Shizuku, use it temporarily for one evaluation run, stop using it, and launch Android's uninstall confirmation for Shizuku â€” all from within the Source Evaluation screen.

Source Evaluation now reads real Shizuku status (installed, binder alive, permission granted) instead of always treating Shizuku as unavailable.

## What Android Does and Does Not Allow

### Komikku can:

- Detect whether the Shizuku package is installed.
- Open the official Shizuku download/setup page (`https://shizuku.rikka.app/download`).
- Open the Shizuku app if installed, so the user can start the Shizuku service manually.
- Launch Android's standard uninstall confirmation dialog for the Shizuku package.
- Set Source Evaluation's local installer mode away from `SHIZUKU`.
- Read `Shizuku.pingBinder()` and `Shizuku.checkSelfPermission()` to know binder and permission state.

### Komikku cannot:

- Silently install Shizuku without user confirmation.
- Silently uninstall Shizuku without user confirmation.
- Start or stop the Shizuku privileged service on its own.
- Revoke Shizuku permissions silently.
- Guarantee the Shizuku service is no longer running system-wide after Komikku stops using it.

The safety note shown to users: "Komikku can stop using Shizuku, but Android/Shizuku controls whether the Shizuku service keeps running."

## Files Changed

### New: `app/src/main/java/exh/recs/evaluation/ShizukuSetupHelper.kt`

Pure state query and shortcut helper. No Android dependencies on Shizuku API are called at class-load time â€” all calls are wrapped in `try/catch`.

- `ShizukuSetupHelper.State` data class: `installed`, `binderAlive`, `permissionGranted`.
- `readState(context)`: reads `context.isShizukuInstalled`, then conditionally tries `Shizuku.pingBinder()` and `Shizuku.checkSelfPermission()`.
- `openDownload(context)`: fires `Intent.ACTION_VIEW` for `https://shizuku.rikka.app/download`.
- `openApp(context)`: uses `getLaunchIntentForPackage("moe.shizuku.privileged.api")`; returns false if unavailable.
- `openUninstall(context)`: fires `Intent.ACTION_DELETE` with `package:moe.shizuku.privileged.api`; returns false on failure.
- `stopUsingFallbackMode(privateAvailable)`: returns `PRIVATE` when available, otherwise `CURRENT`.

### Updated: `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt`

- `State` gains `shizukuState: ShizukuSetupHelper.State` (default all-false).
- `refreshInstallerPolicy()` now calls `ShizukuSetupHelper.readState(context)` and stores result in state alongside policy. Replaces the previous hardcoded `shizukuBinderAlive = false` / `shizukuPermissionGranted = false`.
- New actions:
  - `useShizukuForEvaluation()` â€” sets `installerMode = SHIZUKU`, refreshes policy.
  - `stopUsingShizukuForEvaluation()` â€” reads `privateAvailable`, calls `ShizukuSetupHelper.stopUsingFallbackMode()`, sets mode, refreshes policy. Does NOT mutate the global `BasePreferences.extensionInstaller()` preference.
  - `openShizukuSetup()` â€” delegates to `ShizukuSetupHelper.openDownload()`.
  - `openShizukuApp()` â€” delegates to `ShizukuSetupHelper.openApp()`; falls back to `openDownload()` if not installed.
  - `uninstallShizuku()` â€” no-op when `queueState.isRunning`; otherwise calls `ShizukuSetupHelper.openUninstall()` then refreshes policy.
  - `refreshShizukuState()` â€” calls `refreshInstallerPolicy()`.

### Updated: `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt`

Added `ShizukuSetupCard` composable. Inserted as a new lazy list item (`key = "shizuku_setup"`) between `InstallerModeSelector` and the options toggles. Only shown while the evaluation queue is idle (the entire options section is already gated on `queueState.isIdle`).

Card shows one of five status strings:

- Not installed
- Installed but not running â€” open Shizuku to start it
- Running â€” grant Komikku permission in the Shizuku app
- Ready â€” tap "Use for this run" to use Shizuku for evaluation
- Komikku will use Shizuku for this evaluation run

Context-sensitive buttons:

- When not installed: `Install Shizuku` (opens download page)
- When installed: `Open Shizuku` always shown; `Use for this run` when not selected and binder is alive + permission granted; `Stop using` when selected; `Uninstall Shizuku` disabled while evaluation is running.

### Updated: `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`

- `VERSION_CODE` bumped from 610 to 611.
- `VERSION_NAME` changed to `"KMK-Recs v0.6.11"`.
- Added user-facing What's New entries.

### Updated: `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`

Added 12 new English strings under `<!-- KMK v0.6.11 Shizuku setup -->`:

- `shizuku_setup_title`
- `shizuku_status_not_installed`
- `shizuku_status_not_running`
- `shizuku_status_needs_permission`
- `shizuku_status_ready_not_selected`
- `shizuku_status_selected_for_run`
- `shizuku_action_install`
- `shizuku_action_open`
- `shizuku_action_use_for_run`
- `shizuku_action_stop_using`
- `shizuku_action_uninstall`
- `shizuku_safety_note`

### New: `app/src/test/java/exh/recs/evaluation/SourceEvaluationInstallerPolicyTest.kt`

8 unit tests covering pure logic:

- Shizuku not installed â†’ UNAVAILABLE
- Shizuku installed but binder dead â†’ UNAVAILABLE
- Shizuku running but permission missing â†’ NEEDS_PERMISSION
- Shizuku installed, running, permission granted â†’ READY
- stop-using fallback prefers PRIVATE when available
- stop-using fallback uses CURRENT when PRIVATE unavailable
- PRIVATE mode available â†’ READY
- PRIVATE mode unavailable â†’ UNAVAILABLE

## Existing Behavior Preserved

- Source Evaluation continues using temporary installer override via `SourceEvaluationInstallerPolicy.effectiveInstallerOverride()` â€” global installer preference is never mutated.
- `ShizukuInstaller.onDestroy()` unbinding behavior is unchanged.
- No changes to source evaluation scoring, batch sizes, or runner logic.
- v0.6.9 migration (`47.sqm`) and v0.6.10 DI fix remain intact.

## Tests Run

- `exh.recs.evaluation.SourceEvaluationInstallerPolicyTest` â€” 8 tests, all PASSED
- `:app:testDebugUnitTest` â€” BUILD SUCCESSFUL, all prior tests continue to PASS
- `:app:assembleDebug` â€” BUILD SUCCESSFUL

## APK

Generated: `app/build/outputs/apk/debug/app-universal-debug.apk`

Copied to: `Komikku-v1.13.6-kmk.6.11-debug.apk`

## Manual Verification

Not performed by this session. Recommended manual test steps are documented in the plan file `KMK_RECS_V0_6_11_SHIZUKU_SETUP_AND_TEMPORARY_USE_PLAN.md` (steps 1â€“15).

Key points to verify manually:

1. Source Evaluation shows Shizuku setup card.
2. "Install Shizuku" opens the Shizuku download page.
3. "Open Shizuku" launches the Shizuku app.
4. "Use for this run" becomes enabled only when Shizuku is running and permission is granted.
5. "Stop using" switches installer mode away from Shizuku without touching the global installer pref.
6. "Uninstall Shizuku" opens Android's uninstall confirmation.
7. Uninstall button is disabled while evaluation is running.

