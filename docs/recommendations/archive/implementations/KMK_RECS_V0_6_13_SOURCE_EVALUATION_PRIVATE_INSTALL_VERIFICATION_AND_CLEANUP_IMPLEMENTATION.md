# KMK-Recs v0.6.13: Source Evaluation Private Install Verification and Cleanup Implementation

Date: 2026-06-19

Feature version: KMK-Recs v0.6.13

## Summary

Verified and fixed the Source Evaluation install/cleanup path. Private installer (`isShared=false`) extensions are confirmed to clean up silently. System-installed extensions (`isShared=true`, from Shizuku/Current modes) now skip automatic uninstall unless the user explicitly enables prompt-heavy cleanup. Pre-existing extensions are detected before install and skipped to avoid accidental uninstall. Added diagnostics logging, a confirmation dialog for prompt-heavy modes, and UI labels clarifying Private as the recommended mode.

## Verification Results

### Private install path (verified via code analysis)

`ExtensionInstaller.uninstallApk(pkgName)` checks `context.isPackageInstalled(pkgName)`:
- Private extensions are NOT registered with Android's package manager → `isPackageInstalled()` returns `false` → silent private file deletion via `ExtensionLoader.uninstallPrivateExtension()`.
- System-installed extensions ARE registered → `isPackageInstalled()` returns `true` → Android `ACTION_UNINSTALL_PACKAGE` intent (shows dialog).

`Extension.Installed.isShared`:
- `true` for system/package-manager installs (Shizuku, PackageInstaller).
- `false` for private file installs (`filesDir/exts/*.ext`).

### Root cause of Android prompts

Prior to v0.6.13, `cleanupExtension()` called `extensionManager.uninstallExtension(installedExt)` without checking `installedExt.isShared`. If the evaluation used Shizuku or Current (non-private) installer, the extension was system-installed → `isShared=true` → `isPackageInstalled()=true` → Android uninstall dialog.

Additionally, pre-existing user-installed extensions could be uninstalled if they appeared in `installedExtensionsFlow` during evaluation cleanup (race condition or candidate pool bug).

### Private install confirmed to work for fresh extensions

For extensions not already system-installed:
1. Private install writes to `filesDir/exts/pkgName.ext`.
2. `loadExtensions()` finds the file, sets `isShared=false`.
3. `cleanupDecision(false, true, false)` → `RemovePrivateSilently`.
4. `uninstallApk()` sees `!isPackageInstalled()` → deletes private file silently. ✓

## Architecture: SourceEvaluationCleanupPolicy

New pure object at `exh/recs/evaluation/SourceEvaluationCleanupPolicy.kt`. No Android dependencies. Fully testable.

```kotlin
fun cleanupDecision(
    preExistingInstalled: Boolean,
    installedAfterEvaluation: Boolean,
    isShared: Boolean,
): CleanupDecision
```

Decision tree:
1. `!installedAfterEvaluation` → `NotNeeded` (install failed or already cleaned up)
2. `preExistingInstalled` → `SkipPreExisting` (protect user-installed extensions)
3. `!isShared` → `RemovePrivateSilently` (private file, silent deletion)
4. `isShared` → `PromptRequired` (system-installed, would trigger Android dialog)

The Runner handles `PromptRequired` based on `SourceEvaluationOptions.promptHeavyCleanupAllowed`.

## Changes to SourceEvaluationRunner

- Pre-existing detection: before installing, check `installedExtensionsFlow.value` for the candidate. If found, skip with `recordExtensionError("Already installed before evaluation", CleanupStatus.SkippedPreExisting)`.
- Diagnostics logging with prefix `"KMK SourceEvaluation install:"`: ext name, pkg, sig, mode, override, privateAvailable, wasPreExisting, post-install isShared, cleanup decision, promptAllowed.
- `cleanupExtension()` now returns `CleanupStatus` and accepts `wasPreExisting` and `promptHeavyCleanupAllowed` parameters.
- `PromptRequired` + `!promptHeavyCleanupAllowed` → skip uninstall, log the skip, record `CleanupStatus.PromptRequired`.
- `PromptRequired` + `promptHeavyCleanupAllowed` → call `extensionManager.uninstallExtension()` (Android dialog), record `CleanupStatus.PromptRequired`.
- `RecordExtensionError()` accepts optional `cleanupStatus` parameter.

## Changes to SourceEvaluationInstallerPolicy

- SHIZUKU ready case: `requiresPromptWarning = true` (was wrongly `false`). Message updated to: "Shizuku installs extensions as system packages. Cleanup will show Android uninstall prompts. Use Private for silent cleanup."
- CURRENT/SHIZUKU ready case: `requiresPromptWarning = true` for this subcase too.
- PRIVATE ready case: added message "Best for evaluation. Extensions are installed inside Komikku and cleaned up silently."

## Changes to SourceEvaluationScreenModel

- `State` gains: `showPromptHeavyWarningDialog: Boolean = false`, `privateAvailable: Boolean = false`.
- `startEvaluation()`: if `policy.requiresPromptWarning && batchSize > 1`, sets `showPromptHeavyWarningDialog = true` and returns; otherwise calls `launchEvaluation(promptHeavyCleanupAllowed=false)`.
- Added: `confirmAndStartWithPrompts()`, `switchToPrivateAndStart()`, `switchToPrivate()`, `dismissPromptWarningDialog()`, `launchEvaluation(promptHeavyCleanupAllowed)`.
- `refreshInstallerPolicy()` now populates `state.privateAvailable`.

## Changes to SourceEvaluationScreen

- Added `AlertDialog` for the prompt-heavy warning: shows title, message, and three actions — "Use Private" (if privateAvailable), "Cancel" (dismiss), "Continue anyway" (confirmAndStartWithPrompts).
- `InstallerModeSelector` chip for PRIVATE now shows `source_evaluation_installer_private_label` = "Private (recommended)".
- `ShizukuSetupCard` gains `privateAvailable` and `onUsePrivateInstead` parameters.
  - Status text: when `isUsingShizuku && privateAvailable`, shows `shizuku_status_selected_ready_private_recommended` = "Shizuku is ready and selected. Private is recommended for silent cleanup."
  - Button row: when `isUsingShizuku && privateAvailable`, shows "Use Private" button.

## Changes to SourceEvaluationQueueState

- `CleanupStatus` enum added: `NotNeeded, PrivateRemoved, SkippedPreExisting, PromptRequired, Failed`.
- `EvaluationResult` gains `cleanupStatus: CleanupStatus = CleanupStatus.NotNeeded`.
- `SourceEvaluationOptions` gains `promptHeavyCleanupAllowed: Boolean = false`.
- Computed `promptRequiredCleanupCount` on `SourceEvaluationQueueState`.

## New Strings (i18n-kmk, under KMK v0.6.13 comment)

- `source_evaluation_installer_private_label` = "Private (recommended)"
- `source_evaluation_prompt_warning_title` = "Installer may show Android prompts"
- `source_evaluation_prompt_warning_message` = "This installer mode system-installs extensions, so Android may show an uninstall confirmation for each extension during cleanup. Use Private installer for automatic silent cleanup."
- `source_evaluation_use_private_instead` = "Use Private"
- `source_evaluation_continue_with_prompts` = "Continue anyway"
- `source_evaluation_prompt_warning_cancel` = "Cancel"
- `shizuku_status_selected_ready_private_recommended` = "Shizuku is ready and selected. Private is recommended for silent cleanup."

## Files Changed

### New Files

- `app/src/main/java/exh/recs/evaluation/SourceEvaluationCleanupPolicy.kt`
- `app/src/test/java/exh/recs/evaluation/SourceEvaluationCleanupPolicyTest.kt`
- `docs/recommendations/KMK_RECS_V0_6_13_SOURCE_EVALUATION_PRIVATE_INSTALL_VERIFICATION_AND_CLEANUP_IMPLEMENTATION.md`

### Modified Files

- `app/src/main/java/exh/recs/evaluation/SourceEvaluationQueueState.kt` — CleanupStatus, EvaluationResult.cleanupStatus, promptHeavyCleanupAllowed
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationRunner.kt` — pre-existing detection, diagnostics, private-aware cleanup
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationInstallerPolicy.kt` — SHIZUKU requiresPromptWarning fix, PRIVATE description
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt` — dialog state, launchEvaluation, switchToPrivate
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt` — AlertDialog, chip label, ShizukuSetupCard updates
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` — VERSION_CODE=613
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` — 7 new strings
- `docs/recommendations/CURRENT_STATE.md` — updated version, APK, Source Evaluation section, test counts
- `docs/recommendations/NEXT_WORK.md` — updated version
- `docs/recommendations/README.md` — added v0.6.13 implementation report entry
- `RECOMMENDATION_VERSIONING.md` — added v0.6.13 entry

## Tests

- `exh.recs.evaluation.SourceEvaluationCleanupPolicyTest` — 6 tests, all PASSED
  - NotNeeded when not installed after evaluation
  - SkipPreExisting when already installed before evaluation (isShared=true)
  - SkipPreExisting when already installed before evaluation (isShared=false)
  - RemovePrivateSilently when evaluation installed a private extension
  - PromptRequired when evaluation installed a system-shared extension
  - NotNeeded when preExisting=true but extension not found after evaluation
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL, all prior tests continue to PASS
- `:app:assembleDebug` — BUILD SUCCESSFUL

## APK

Generated: `app/build/outputs/apk/debug/app-universal-debug.apk`

Copied to: `Komikku-v1.13.6-kmk.6.13-debug.apk`

## Manual Verification Required

Manual on-device verification is needed to confirm no Android prompts appear during Private-mode evaluation. Recommended test matrix from the plan:

- **Test A**: Private evaluation of fresh non-installed extension → confirm no install or uninstall prompt; confirm logcat shows `isShared=false` and `decision=RemovePrivateSilently`.
- **Test B**: Shizuku evaluation → confirm warning dialog appears before start; confirm logcat shows `isShared=true` and `decision=PromptRequired`; confirm extension is NOT uninstalled if "Continue anyway" was not chosen.
- **Test C**: Pre-existing protection → install extension normally; ensure Source Evaluation skips it and does not uninstall it.
- **Test D**: Private failure → confirm no automatic Shizuku fallback.

## Verification Conclusion

Based on code analysis:
- Private installer IS verified to work architecturally for fresh temporary extension evaluation — it installs without a prompt and removes silently.
- The prior uninstall prompts were caused by SHIZUKU/CURRENT mode (not PRIVATE) — those modes system-install extensions, causing `isShared=true` and an Android dialog during cleanup.
- With v0.6.13, cleanup is gated on `isShared`: only private-only extensions are automatically removed. System-installed extensions from Shizuku/Current are left in place unless `promptHeavyCleanupAllowed=true`.
- SHIZUKU mode correctly shows a warning dialog before large batches.
- Private is now the clear default and labeled as recommended.
