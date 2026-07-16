# KMK-Recs v0.7.7 Recommendation Quality On-Demand Fix â€” Implementation Report

Date: 2026-06-22

Status: implemented. Follow-up fix shipped within v0.7.8 APK (VERSION_CODE 780).

## Overview

This is a targeted follow-up to the recommendation-quality on-demand probe feature shipped in v0.7.7. The original `evaluateRecommendationQualityForPromising()` action immediately wrote an ERROR fit with the message "Extension not installed or source not found" for every non-installed promising source, because it only checked `extensionManager.installedExtensionsFlow.value`. Since STRONG_FIT and WORTH_TRYING extensions are typically non-installed after a Source Evaluation batch (they were temporarily installed, probed, and cleaned up), almost every on-demand re-check would fail immediately for these sources.

Two adjacent stale-state audits were also fixed in the same pass:

- **Audit 8.1** â€” `installedExtensionKeys` in `SourceEvaluationScreenModel` was loaded once at init time (snapshot) and never updated. Extensions installed or uninstalled while the screen was open would not update the installed-filter chip display until the screen was reopened.
- **Audit 8.2** â€” `visibleSources` in `RecommendationsSettingsScreenModel` was captured once at construction time. Extensions installed or uninstalled while the settings screen was open would not update the source priority list until the app was restarted.

## What Changed

### New File

**`app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityExtensionResolver.kt`**

Pure resolver object. `resolve(evaluation, available)` finds the best `Extension.Available` match for a given `SourceEvaluation` from the candidate pool.

Resolution order:
1. Exact `signatureHash` + `pkgName`
2. `pkgName` only (sig may differ after an update or signing key rotation)
3. `signatureHash` + extension name (unambiguous only)
4. Extension name + lang (unambiguous only)

Returns `ResolveResult.Found`, `ResolveResult.Ambiguous(matchCount, reason)`, or `ResolveResult.NotFound`. At any step where more than one extension matches, returns `Ambiguous` immediately so the caller does not guess.

No Android dependencies. No state. No throws.

### Modified: `SourceEvaluationScreenModel.kt`

**New imports added:**
- `eu.kanade.tachiyomi.extension.model.Extension`
- `eu.kanade.tachiyomi.extension.model.InstallStep`
- `eu.kanade.tachiyomi.source.CatalogueSource`
- `kotlinx.coroutines.CancellationException`
- `kotlinx.coroutines.flow.first`
- `kotlinx.coroutines.withTimeoutOrNull`
- `tachiyomi.domain.taste.model.TasteProfile`

**Audit 8.1 fix â€” `installedExtensionKeys` reactive:**

The one-time cursor/keys launch block was split: cursor loading stays as a separate one-time `launch {}`. A new `extensionManager.installedExtensionsFlow .onEach { } .launchIn(screenModelScope)` observer replaces the snapshot. It:
- runs immediately on subscription (providing the initial value that the one-time block previously provided),
- updates `installedExtensionKeys` whenever extensions are installed or uninstalled,
- calls `applyDisplayFilter()` on every update so the installed-filter chip and hidden-count update immediately.

**`evaluateRecommendationQualityForPromising()` enhanced (v0.7.7 follow-up):**

The per-source loop body now calls the new `evaluateOneForRecQuality()` suspend helper instead of inline extension/source lookup. The outer coroutine:
1. Loads `tasteProfile` and creates the `SourceRecommendationFitProbe` (unchanged).
2. Reads `availableExtensions` from `lastCandidatePool.value?.allEligible?.map { it.extension } ?: emptyList()`.
3. Computes `installerOverride` from `SourceEvaluationInstallerPolicy.effectiveInstallerOverride()` using `state.value.options.installerMode`, `basePreferences.extensionInstaller().get()`, and `state.value.privateAvailable`.
4. Delegates per-source evaluation to `evaluateOneForRecQuality()`.
5. Catches `CancellationException` (re-thrown) and other exceptions (writes error fit, continues batch).

**New private helpers:**

`evaluateOneForRecQuality(evaluation, probe, tasteProfile, availableExtensions, installerOverride)`:
- **Installed path**: checks `installedExtensionsFlow.value` for exact pkgName+sig match. If found, locates the `CatalogueSource` by sourceId (fallback: unambiguous source name), probes, persists. Returns without install/cleanup.
- **Non-installed path**:
  1. Calls `SourceRecommendationQualityExtensionResolver.resolve()`. If `NotFound` or `Ambiguous`, writes a descriptive error fit and returns.
  2. Calls `extensionManager.installExtension(availableExt, installerOverride).first { terminal }` with `withTimeoutOrNull(90_000L)`. Timeout or `InstallStep.Error` â†’ writes "Install failed or timed out", returns.
  3. Waits up to 20s for the extension to appear in `installedExtensionsFlow` via `flow.first { installed.any { ... } }`.
  4. Locates `CatalogueSource` in the loaded extension.
  5. Probes and persists inside `try { } finally { cleanupRecQualityExtension(availableExt) }` so cleanup always runs.

`findSourceInInstalledExt(installedExt, evaluation)`: finds `CatalogueSource` by exact `sourceId`; falls back to unambiguous source name match.

`cleanupRecQualityExtension(ext)`: reads current installed state, calls `SourceEvaluationCleanupPolicy.cleanupDecision(preExistingInstalled=false, ...)`. `RemovePrivateSilently` â†’ uninstalls. `PromptRequired` â†’ logs and skips (on-demand probe cannot show UI dialogs). `SkipPreExisting`/`NotNeeded` â†’ no-op. Exceptions are caught (except `CancellationException`).

`writeRecQualityErrorFit(evaluation, message)`: suspend wrapper around `buildRecQualityErrorFit` + `upsertSourceRecommendationFit.await()`. Catches all exceptions.

`buildRecQualityFitFromOutcome(evaluation, outcome)`: builds `SourceRecommendationFit` from a probe outcome (extracted from the inline code in the original function). Reduces duplication between installed and non-installed paths.

### Modified: `RecommendationsSettingsScreenModel.kt`

**Audit 8.2 fix â€” `visibleSources` no longer a field:**

Removed `private val visibleSources = sourceManager.getVisibleCatalogueSources()`.

Updated three call sites to call `sourceManager.getVisibleCatalogueSources()` inline:
- `init` block (renamed local to `initSources`)
- `recomputeSourcesForLanguages(languages)` (inline call replacing `visibleSources` reference)
- `confirmResetSourceOrder()` (inline call replacing `visibleSources` reference)

New private `refreshVisibleSources()` helper: reads a fresh source list, recomputes `filteredSources`, `allInOrder`, `availableLangs`, `boostedSourceIds` (using current `state.value.disabledSourceIds`), and updates state.

New reactive observer in `init`:
```kotlin
screenModelScope.launch {
    extensionManager.installedExtensionsFlow.collectLatest { refreshVisibleSources() }
}
```
This ensures `orderedSources` and `availableLanguages` update whenever extensions are installed or uninstalled while the screen is open.

### Modified: `KmkRecsReleaseNotes.kt`

Added 4 new user-facing bullet points at the top of the v0.7.8 section (before the "Find best version" bullet):
- Recommendation Quality checks now work for non-installed sources.
- The app temporarily loads extensions to run checks then cleans them up.
- Errors reflect real failure reasons instead of marking every non-installed source as "Error".
- Source Evaluation display updates immediately after install/uninstall.
- Recommendation Settings source list updates immediately after install/uninstall.

VERSION_CODE and VERSION_NAME unchanged (780 / "KMK-Recs v0.7.8").

## New Tests

**`SourceRecommendationQualityExtensionResolverTest.kt`** â€” 10 tests, all PASSED:

| Test | Verifies |
| --- | --- |
| `exact sig and pkg match returns Found` | Step 1: exact match |
| `pkgName-only fallback when sig differs returns Found` | Step 2: pkg-only fallback |
| `multiple extensions with same pkgName returns Ambiguous` | Step 2: ambiguous pkg |
| `sig plus name fallback returns Found` | Step 3: sig+name fallback |
| `multiple extensions with same sig and name returns Ambiguous` | Step 3: ambiguous sig+name |
| `name plus lang fallback unambiguous returns Found` | Step 4: name+lang fallback |
| `name plus lang fallback ambiguous returns Ambiguous` | Step 4: ambiguous name+lang |
| `no match at any step returns NotFound` | NotFound result |
| `empty available list returns NotFound` | Empty pool |
| `exact match takes priority over pkg-only match` | Step 1 before step 2 |

## Tests Run

- `SourceRecommendationQualityExtensionResolverTest` â€” 10 tests, all PASSED
- `:app:testDebugUnitTest` â€” BUILD SUCCESSFUL (full suite, no regressions)
- `:app:assembleDebug` â€” BUILD SUCCESSFUL

## APK

`Komikku-v1.13.6-kmk.7.8-debug.apk` (rebuilt with this fix, VERSION_CODE 780)

## Deferred

- **Re-run rec-quality probe after taste profile changes**: still no automatic trigger. Manual "Re-check all" required.
- **Evidence strings i18n**: still hardcoded English strings in `SourceEvaluationScreen.kt`. Deferred from v0.7.4.
- **Cleanup for PromptRequired extensions**: on-demand probe skips system-installed (non-private) extension cleanup to avoid showing uninstall dialogs. These extensions remain installed after the probe. This is a known limitation of the on-demand path.

