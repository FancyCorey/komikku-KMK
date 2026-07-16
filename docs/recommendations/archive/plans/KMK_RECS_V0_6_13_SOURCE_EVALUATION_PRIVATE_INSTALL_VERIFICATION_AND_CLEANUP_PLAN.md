# KMK-Recs v0.6.13: Source Evaluation Private Install Verification And Cleanup Plan

Date: 2026-06-19

Feature version target: KMK-Recs v0.6.13

Status: implementation plan only. Do not modify application code until the user explicitly approves implementation.

## Goal

Verify and fix the Source Evaluation install/cleanup path so temporary extension evaluation uses the best installer mode for large-scale testing.

The user needs confidence on this specific question:

```text
Can Komikku evaluate fresh, never-before-installed extensions from the extension repo by privately installing them, loading their sources, probing them, then removing them without Android uninstall prompts?
```

This plan must not assume the answer without verification. It should verify the current code path, add diagnostics, and then make Source Evaluation prefer the lowest-friction safe path.

## Current Understanding

There are two separate concepts that must stay separate:

1. **Candidate discovery**
   - Which available non-installed extensions Source Evaluation chooses to test.
   - This was addressed by the v0.6.12 plan: Source Evaluation should use a broad candidate provider, not only Sources To Try suggestions.

2. **Installer mode**
   - How a chosen extension is temporarily installed so Komikku can load and evaluate its sources.
   - Options: `PRIVATE`, `SHIZUKU`, `CURRENT`.

Shizuku does not make candidate discovery broader. It only changes the install mechanism.

## Code Evidence

### Private install path

File:

```text
app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionInstaller.kt
```

When installer mode is `PRIVATE`, the downloaded APK is not sent to Android's package installer. Instead:

```kotlin
BasePreferences.ExtensionInstaller.PRIVATE -> {
    if (ExtensionLoader.installPrivateExtensionFile(context, tempFile)) {
        updateInstallStep(downloadId, InstallStep.Installed)
    } else {
        updateInstallStep(downloadId, InstallStep.Error)
    }
    tempFile.delete()
}
```

### Private extension storage

File:

```text
app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionLoader.kt
```

Private extension files are stored under:

```kotlin
File(context.filesDir, "exts")
```

with extension:

```kotlin
private const val PRIVATE_EXTENSION_EXTENSION = "ext"
```

Private install copies the downloaded APK into that directory:

```kotlin
val target = File(getPrivateExtensionDir(context), "${extension.packageName}.$PRIVATE_EXTENSION_EXTENSION")
file.copyAndSetReadOnlyTo(target, overwrite = true)
ExtensionInstallReceiver.notifyAdded(context, extension.packageName)
```

Private extensions are loaded through the same extension loading system:

```kotlin
privateExtPkgs = getPrivateExtensionDir(context)
    .listFiles()
    ?.filter { it.isFile && it.extension == PRIVATE_EXTENSION_EXTENSION }
    ?.mapNotNull { packageManager.getPackageArchiveInfo(path, PACKAGE_FLAGS) ... }
```

Sources are then instantiated from extension metadata/classes, so private extensions can still make normal web calls once loaded.

### Private uninstall path

File:

```text
app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionInstaller.kt
```

Uninstall behavior:

```kotlin
fun uninstallApk(pkgName: String) {
    if (context.isPackageInstalled(pkgName)) {
        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, "package:$pkgName".toUri())
        context.startActivity(intent)
    } else {
        ExtensionLoader.uninstallPrivateExtension(context, pkgName)
        ExtensionInstallReceiver.notifyRemoved(context, pkgName)
    }
}
```

Therefore:

- if the extension is system-installed, Android uninstall prompt is expected;
- if it exists only as a private extension file, cleanup should remove the file silently.

### Source Evaluation cleanup

File:

```text
app/src/main/java/exh/recs/evaluation/SourceEvaluationRunner.kt
```

Cleanup currently calls:

```kotlin
extensionManager.uninstallExtension(installedExt)
```

That delegates to `ExtensionInstaller.uninstallApk(extension.pkgName)`.

This should be silent for private-only extensions, but prompt for shared/system-installed extensions.

## Working Hypothesis

Private should be the preferred Source Evaluation installer mode because it matches the desired workflow:

```text
download extension -> install privately -> load sources -> probe/search -> delete private file
```

Shizuku should be fallback/advanced mode, not the default for source evaluation, because Shizuku/system install may still trigger Android uninstall prompts during cleanup.

However, this must be verified in the real Source Evaluation path because prompts are currently being observed.

## Key Questions To Verify

Claude must verify these before changing defaults:

1. Does Source Evaluation currently start with `PRIVATE` as default in `SourceEvaluationOptions`?
2. Does the UI or Shizuku setup flow accidentally leave `installerMode = SHIZUKU` selected?
3. Does `effectiveInstallerOverride()` pass `BasePreferences.ExtensionInstaller.PRIVATE` when mode is Private and Private is available?
4. Does `installAndWait()` receive that override and pass it to `ExtensionManager.installExtension()`?
5. After private install completes, does `installedExtensionsFlow` expose the extension with `isShared = false`?
6. Does cleanup see the private extension as not system-installed and remove the private file silently?
7. Are uninstall prompts happening only when `installedExt.isShared == true` or when `context.isPackageInstalled(pkgName) == true`?
8. Are there package-name collisions with already system-installed extensions causing shared/system package priority to override the private file?
9. Are some evaluation candidates actually already installed system-wide, despite being treated as non-installed candidates?
10. Does the v0.6.12 broad candidate provider exclude all installed extensions by extension key and package/signature correctly?

## Implementation Plan

### Phase 1: Add evaluation install-mode diagnostics

Update:

```text
app/src/main/java/exh/recs/evaluation/SourceEvaluationRunner.kt
```

Add diagnostic logging around each evaluation extension:

- extension name,
- package name,
- signature hash,
- requested `SourceEvaluationInstallerPolicy.InstallerMode`,
- resolved `installerOverride`,
- whether Private is available,
- whether the extension was already installed before evaluation,
- after install, whether the loaded extension is shared or private (`installedExt.isShared`),
- cleanup path used.

Use `logcat` with a stable prefix:

```text
KMK SourceEvaluation install:
```

Do not log sensitive user data. Extension names/package names are acceptable.

This is necessary because the user is seeing uninstall prompts, and the app needs to prove which path caused them.

### Phase 2: Track pre-existing installed state

Before installing each candidate in `evaluateExtension()`, record whether the extension package already exists in `installedExtensionsFlow`:

```kotlin
val preExistingInstalled = extensionManager.installedExtensionsFlow.value.find {
    it.pkgName == ext.pkgName && it.signatureHash == ext.signatureHash
}
```

If a matching extension is already installed before evaluation:

- do not treat it as a temporary evaluation install;
- do not uninstall it in cleanup;
- either skip it or evaluate it without cleanup.

Recommendation:

- For Source Evaluation of non-installed candidates, skip pre-existing installed extensions and record an error/status such as `"Already installed before evaluation"`.
- This avoids accidental uninstall prompts for real user-installed extensions.

This also protects against broad candidate provider mistakes.

### Phase 3: Make cleanup private-aware

Current cleanup calls `extensionManager.uninstallExtension(installedExt)` without checking whether the extension was private/shared.

Change cleanup to know:

- whether this evaluation run installed the extension,
- whether it was private (`installedExt.isShared == false`),
- whether it was shared/system (`installedExt.isShared == true`),
- whether it existed before evaluation.

Preferred behavior:

```text
if pre-existing installed:
    do not uninstall
else if installedExt.isShared == false:
    uninstall/remove private extension silently
else if installedExt.isShared == true:
    do not auto-uninstall in Source Evaluation unless user explicitly allowed prompt-heavy cleanup
    mark cleanup as requiring user action / prompt-heavy
```

If `ExtensionManager.uninstallExtension()` is the only public path, use it only when `installedExt.isShared == false`.

If needed, add a targeted extension manager method:

```kotlin
fun uninstallPrivateExtensionIfPresent(pkgName: String)
```

But prefer minimal change if current `uninstallExtension()` already silently removes private-only extensions.

Important:

- Do not launch Android uninstall prompts automatically for evaluation cleanup unless the user explicitly chose prompt-heavy mode.
- If cleanup cannot be silent, record a clear status and stop/skip rather than creating a prompt loop.

### Phase 4: Prefer Private for Source Evaluation

Ensure:

```text
SourceEvaluationOptions.installerMode = PRIVATE
```

remains the default.

In the UI, clearly label Private as recommended:

```text
Private (recommended)
```

Add summary:

```text
Best for evaluation. Installs extensions inside Komikku and cleans them up silently when possible.
```

For Shizuku:

```text
Shizuku
```

Add warning:

```text
May still require Android uninstall confirmation during cleanup. Use only if Private fails or is unavailable.
```

For Current:

```text
Current
```

Add warning:

```text
May require Android confirmation for install and uninstall. Use small batches.
```

### Phase 5: Add warning/confirmation before prompt-heavy evaluation

If user selects `SHIZUKU` or `CURRENT` and starts a batch larger than 1, show a confirmation dialog before starting:

```text
This installer may ask Android to confirm uninstall for each evaluated extension. Private installer is recommended for silent cleanup.
```

Actions:

- `Use Private`
- `Continue anyway`
- `Cancel`

If Private is unavailable, only show:

- `Continue anyway`
- `Cancel`

This prevents accidental prompt loops.

### Phase 6: Never silently fall back from Private to Shizuku

If Private install fails for an extension:

- record that extension as failed,
- move to the next candidate,
- do not automatically retry with Shizuku/system unless the user explicitly enabled a fallback option.

Do not add automatic Shizuku fallback in this pass.

Reason:

- automatic fallback could reintroduce uninstall prompts without the user realizing why.

Potential future option:

```text
Try Shizuku if private install fails
```

Default should be off.

### Phase 7: Add cleanup status to queue results

Update:

```text
app/src/main/java/exh/recs/evaluation/SourceEvaluationQueueState.kt
```

Add enough information to communicate cleanup result:

Possible fields in `EvaluationResult`:

```kotlin
val cleanupStatus: CleanupStatus = CleanupStatus.NotNeeded
```

Possible enum:

```kotlin
enum class CleanupStatus {
    NotNeeded,
    PrivateRemoved,
    SkippedPreExisting,
    PromptRequired,
    Failed,
}
```

If this is too broad, keep it internal/log-only for v0.6.13, but the user-facing summary should at least avoid endless unexplained prompts.

Recommendation:

- Add internal queue state and summary text if practical.
- Keep UI compact.

### Phase 8: Source Evaluation UI clarification

Update:

```text
app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt
```

Clarify installer mode text:

- Private is recommended for evaluation.
- Shizuku is optional and may prompt on cleanup.
- Current may prompt heavily.

If Shizuku is selected and ready, the Shizuku card should say:

```text
Shizuku is ready, but Private is recommended for silent evaluation cleanup.
```

If Private is available and Shizuku is selected, show a `Use Private instead` action.

### Phase 9: Fix reset priority footgun separately or document as separate plan

The user also reported `Reset priority` is too easy to hit and disruptive.

This plan should not mix source priority reset changes with install cleanup unless the user explicitly approves bundling them.

Recommended separate plan:

```text
KMK-Recs v0.6.14: Source Priority Reset Safety
```

Do not implement it as part of v0.6.13 unless instructed.

### Phase 10: Tests

Add or update tests around pure logic where possible.

Recommended pure helper:

```text
SourceEvaluationCleanupPolicy.kt
```

Suggested logic:

```kotlin
fun cleanupDecision(
    preExistingInstalled: Boolean,
    installedAfterEvaluation: Boolean,
    isShared: Boolean,
    promptHeavyCleanupAllowed: Boolean,
): CleanupDecision
```

Test cases:

- private installed by evaluation -> remove private silently.
- shared installed by evaluation, prompt not allowed -> skip prompt and record prompt required.
- shared installed by evaluation, prompt allowed -> allow Android uninstall.
- pre-existing installed -> do not uninstall.
- install failed / not installed after evaluation -> no cleanup needed.

Also test installer mode policy if modified:

- Private available -> default/recommended.
- Shizuku selected -> warning required.
- Current selected -> warning required.
- Private failure does not auto-fallback.

Run:

```text
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

### Phase 11: Manual verification required

This feature must be manually verified on a device/tablet because the issue is about Android prompts.

Manual test matrix:

#### Test A: Private evaluation of fresh extension

1. Choose an extension that is not installed.
2. Set Source Evaluation installer mode to Private.
3. Evaluate one extension.
4. Confirm no Android install prompt appears.
5. Confirm no Android uninstall prompt appears.
6. Confirm extension appears during evaluation.
7. Confirm extension is removed after evaluation.
8. Confirm logs show `isShared=false` after install and private cleanup.

#### Test B: Shizuku evaluation

1. Set Source Evaluation installer mode to Shizuku.
2. Evaluate one extension.
3. Confirm whether Android uninstall prompt appears.
4. Confirm UI warned before starting if prompt-heavy cleanup is possible.
5. Confirm logs show whether `isShared=true` or `isShared=false`.

#### Test C: Pre-existing extension protection

1. Install an extension normally.
2. Ensure Source Evaluation does not uninstall it during cleanup.
3. Confirm it is skipped or evaluated without cleanup.

#### Test D: Private failure

1. If a private install fails naturally, confirm the app records failure.
2. Confirm it does not automatically retry with Shizuku.

## Documentation and Versioning

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
docs/recommendations/KMK_RECS_V0_6_13_SOURCE_EVALUATION_PRIVATE_INSTALL_VERIFICATION_AND_CLEANUP_IMPLEMENTATION.md
```

Implementation report must include:

- whether Private was verified as able to evaluate fresh non-installed extensions,
- exact evidence from code and/or manual testing,
- whether Private cleanup avoided Android uninstall prompts,
- when Shizuku still prompts,
- any cases where Private failed,
- files changed,
- tests run,
- generated APK path/name,
- manual verification results.

User-facing What's New should only mention user-facing changes, for example:

```text
Source Evaluation now prefers Private installer for temporary extension testing and warns before prompt-heavy cleanup modes.
```

Do not include developer-only documentation notes in What's New.

## Acceptance Criteria

This work is complete only if:

- Source Evaluation clearly separates candidate discovery from installer mode.
- Private installer is verified or explicitly reported as not working for fresh temporary extension evaluation.
- If Private works, it is the recommended/default evaluation mode.
- Shizuku is shown as optional/fallback, not as the primary path for silent evaluation.
- Android uninstall prompts are not triggered automatically during Source Evaluation cleanup unless the user explicitly continues with a prompt-heavy mode.
- Pre-existing installed extensions are not uninstalled by evaluation cleanup.
- Private cleanup behavior is logged and/or surfaced enough to diagnose.
- Prompt-heavy modes warn before large batches.
- Tests cover cleanup decision logic.
- APK builds successfully.
- Documentation records the actual conclusion, not an assumption.

## Non-Goals

Do not implement:

- auto-clicking Android uninstall prompts,
- silent uninstall of system-installed extensions,
- root uninstall,
- Shizuku shell uninstall,
- broad candidate discovery changes already covered by v0.6.12 unless not yet implemented,
- source scoring changes,
- source priority reset safety changes.

## Recommendation

Proceed with verification-first implementation.

Based on the current code, Private appears architecturally designed for this exact temporary extension workflow: download extension APK, copy it into Komikku private extension storage, load its sources, and delete the private file afterward. But because the user is seeing uninstall prompts, the implementation must prove whether Source Evaluation is actually using Private and whether cleanup is hitting the private-only path.

If verification confirms Private works, make Private the clear recommended Source Evaluation mode and demote Shizuku to fallback/advanced use. If verification disproves it, document the actual limitation and adjust the future design accordingly.

