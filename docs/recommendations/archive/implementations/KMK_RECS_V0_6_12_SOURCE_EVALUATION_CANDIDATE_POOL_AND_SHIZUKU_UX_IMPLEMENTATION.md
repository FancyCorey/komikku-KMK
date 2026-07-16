# KMK-Recs v0.6.12: Source Evaluation Candidate Pool and Shizuku UX Implementation

Date: 2026-06-19

Feature version: KMK-Recs v0.6.12

## Summary

Fixed Source Evaluation to evaluate the full broad pool of available non-installed extensions (matching language, NSFW, and dislike preferences), instead of only the small selective Sources To Try candidate list. Also fixed a skip-already-evaluated bug that silently matched nothing due to using the wrong key format. Added candidate diagnostics to the UI. Polished Shizuku UX with lifecycle-based state refresh, a manual Refresh button, and clearer status strings.

## Root Cause of Candidate Pool Bug

`SourceEvaluationScreenModel` was subscribing to `GetNonInstalledSourceSuggestions.subscribe()`, which is the Sources To Try provider. That provider requires `SimilarToInstalledSource` evidence â€” meaning a non-installed extension is only suggested if it strongly resembles an already-installed extension by name. Result: Source Evaluation only saw the same 1â€“5 candidates that Sources To Try was already surfacing.

## Root Cause of Skip-Evaluated Bug

`startEvaluation()` built `evaluatedKeys = s.evaluations.map { it.evaluationKey }` and then checked `signatureHash|pkgName in evaluatedKeys`. But `evaluationKey` for a multi-source extension is `signatureHash|pkgName|sourceId`, not `signatureHash|pkgName`. The plain `signatureHash|pkgName` format never matched any evaluationKey, so skipAlreadyEvaluated silently had no effect. Fixed by using `it.extensionKey` (`signatureHash|extensionPkgName`) which is defined as an extension-level property on `SourceEvaluation`.

## Architecture: SourceEvaluationCandidateFilter

New pure object at `exh/recs/evaluation/SourceEvaluationCandidateFilter.kt`. No Android dependencies at class-load time. Fully testable.

Two-step design:

1. `buildPool(available, installedPkgNames, untrustedPkgNames, recLanguages, nsfwEnabled, blockExplicit, dislikedKeys, evaluations): CandidatePoolResult`
   â€” Applies broad eligibility: deduplicates by `signatureHash|pkgName`, excludes installed/untrusted by pkgName, excludes language mismatches (`ext.lang.lowercase()` vs normalized recLanguages), excludes NSFW when disabled, excludes disliked (key format `a|signatureHash|pkgName`). Tracks `explicitExtensionKeys` and `dislikedHiddenCount`. Groups evaluations by `extensionKey`.

2. `applyOptions(pool, includeExplicit, skipAlreadyEvaluated, reEvaluateStale, now): FilterResult`
   â€” Applies option-based filters: explicit hiding (`blockExplicit && !includeExplicit`), skip-already-evaluated using `shouldSkip()`, stale detection using `isStale()`. Returns filtered candidate list with `evaluatedHiddenCount` and `explicitHiddenCount`.

Helper functions:
- `isStale(evaluations, now)`: returns true if all evaluations for an extension are expired (`expiresAt <= now`) or version-outdated (`evaluationVersion < CURRENT_VERSION`).
- `shouldSkip(extKey, evaluationsByExtKey, skipAlreadyEvaluated, reEvaluateStale, now)`: returns true if extension should be skipped. Respects `reEvaluateStale` override.

## Architecture: GetSourceEvaluationCandidates

New reactive class at `exh/recs/evaluation/GetSourceEvaluationCandidates.kt`.

Combines 3 flows:
- `extensionTripleFlow` = combine(available, installed, untrusted) â†’ deduplicated extension state
- `prefTriggerFlow` = combine(recLanguages.changes(), showNsfwSource.changes(), blockExplicit.changes(), disliked.changes()) â†’ Unit trigger; prefs read synchronously inside the outer combine
- `safeEvaluationsFlow` = `getSourceEvaluations.subscribeAll()` with `.catch` fallback (same defensive pattern as v0.6.9)

Registered in `KMKDomainModule.kt` as `addFactory { GetSourceEvaluationCandidates(get(), get(), get()) }`.

## Screen Model Changes

`SourceEvaluationScreenModel`:
- Removed `getNonInstalled: GetNonInstalledSourceSuggestions` dependency; added `getSourceEvaluationCandidates: GetSourceEvaluationCandidates`.
- Added `private val lastCandidatePool = MutableStateFlow<CandidatePoolResult?>(null)` to hold the latest emitted pool.
- Added `private fun applyOptionsAndUpdateState()` â€” reads `lastCandidatePool` and current `state.options`, calls `SourceEvaluationCandidateFilter.applyOptions()`, updates `state.candidates` and `state.candidateDiagnostics`.
- Option setters `setSkipAlreadyEvaluated`, `setIncludeExplicit`, `setReEvaluateStale` now call `applyOptionsAndUpdateState()` after mutating options â€” candidate count reacts immediately without waiting for a new flow emission.
- `startEvaluation()` uses `state.candidates` directly (already option-filtered). Removed the broken `skipAlreadyEvaluated` filter block.
- Added `data class CandidateDiagnostics(totalEligible, evaluatedHiddenCount, explicitHiddenCount, dislikedHiddenCount)`.
- `State` gains `val candidateDiagnostics: CandidateDiagnostics = CandidateDiagnostics()`.

## Screen Changes

`SourceEvaluationScreen`:
- Added `DisposableEffect(lifecycleOwner.lifecycle)` with `DefaultLifecycleObserver.onStart` â†’ calls `screenModel.refreshShizukuState()`. Screen auto-refreshes Shizuku state when the user returns from Shizuku app or device setup.
- Added `CandidateDiagnosticsRow` composable (inserted as lazy list item `key = "candidate_diagnostics"` between option toggles and start button). Shows eligible count, evaluated-hidden count, explicit-hidden count. Hidden counts only shown when > 0.
- `ShizukuSetupCard` gains `onRefresh: () -> Unit` parameter. A "Refresh status" `TextButton` always appears in the button row (outside the `if (!shizukuState.installed)` branch so it appears in all states).
- Status text logic updated: `shizuku_status_selected_ready` when binder+permission ready and installer mode is SHIZUKU; `shizuku_status_selected_not_ready` when SHIZUKU mode is selected but binder/permission not ready. "Selected but not ready" is checked before the generic "not running" / "needs permission" messages so the user knows Shizuku is chosen even when it isn't working yet.

## What Did NOT Change

- `GetNonInstalledSourceSuggestions` and its DI registration are untouched. Sources To Try in Recommendation Settings still uses it.
- No changes to `SourceEvaluationRunner`, evaluation scoring, verdict storage, or batch-size logic.
- No changes to v0.6.9 migration (47.sqm).
- No changes to the installer policy validation.
- The runner's own explicit filter (`!options.includeExplicitCandidates && blockExplicit && isExplicitExtension`) remains as a redundant safety check.

## Files Changed

### New Files

- `app/src/main/java/exh/recs/evaluation/SourceEvaluationCandidateFilter.kt`
- `app/src/main/java/exh/recs/evaluation/GetSourceEvaluationCandidates.kt`
- `app/src/test/java/exh/recs/evaluation/SourceEvaluationCandidateFilterTest.kt`

### Modified Files

- `app/src/main/java/eu/kanade/domain/KMKDomainModule.kt` â€” import + addFactory for GetSourceEvaluationCandidates
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt` â€” new provider, CandidateDiagnostics, applyOptionsAndUpdateState, fixed startEvaluation
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt` â€” lifecycle effect, CandidateDiagnosticsRow, ShizukuSetupCard onRefresh, status text updates
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` â€” VERSION_CODE=612
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` â€” 7 new strings
- `docs/recommendations/CURRENT_STATE.md` â€” updated version, APK, Source Evaluation section, test counts
- `docs/recommendations/NEXT_WORK.md` â€” updated version date
- `docs/recommendations/README.md` â€” added v0.6.12 implementation report entry
- `RECOMMENDATION_VERSIONING.md` â€” added v0.6.12 entry

## New Strings (i18n-kmk)

Under `<!-- KMK v0.6.12 Source Evaluation candidate pool and Shizuku UX -->`:

- `shizuku_action_refresh_status` = "Refresh status"
- `shizuku_status_selected_ready` = "Shizuku is ready and selected. You can start evaluation now."
- `shizuku_status_selected_not_ready` = "Shizuku is selected, but it is not ready yet."
- `source_evaluation_candidates_loading` = "Loading candidatesâ€¦"
- `source_evaluation_candidates_available` = "%1$d extensions eligible for evaluation"
- `source_evaluation_candidates_evaluated_hidden` = "%1$d already-evaluated hidden"
- `source_evaluation_candidates_explicit_hidden` = "%1$d explicit candidates hidden"

## Tests Run

- `exh.recs.evaluation.SourceEvaluationCandidateFilterTest` â€” 18 tests, all PASSED
  - eligible extension included, installed excluded, untrusted excluded, disliked excluded+counted, language mismatch excluded, nsfw excluded/included, deduplication, explicit tracked in pool, explicit hidden when blockExplicit=true, explicit included when includeExplicit=true, extensionKey skip, stale re-eval included, non-stale still skipped, isStale with past/future expiresAt, isStale with outdated version, isStale with empty list
- `:app:testDebugUnitTest` â€” BUILD SUCCESSFUL, all prior tests continue to PASS
- `:app:assembleDebug` â€” BUILD SUCCESSFUL

## APK

Generated: `app/build/outputs/apk/debug/app-universal-debug.apk`

Copied to: `Komikku-v1.13.6-kmk.6.12-debug.apk`

## Manual Verification

Not performed by this session. Recommended manual test steps:

1. Open Source Evaluation. Verify candidate count is large (dozens+), not 3â€“5.
2. Toggle "Skip already-evaluated": count changes reactively (if you have past evaluations).
3. Toggle "Include explicit candidates": count changes reactively (if block-explicit is on).
4. With Shizuku installed and running: status shows "Shizuku is ready and selected" after tapping "Use for this run".
5. With Shizuku selected but stopped: status shows "Shizuku is selected, but it is not ready yet."
6. Tap "Refresh status" to force a state re-read.
7. Leave Source Evaluation and return: Shizuku state auto-refreshes on resume.
8. Start an evaluation with skipAlreadyEvaluated=true after doing one run: evaluated extensions correctly excluded from next batch.

