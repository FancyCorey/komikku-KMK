# KMK-Recs v0.6.19 Follow-Up Implementation Notes

Date: 2026-06-20
APK: `Komikku-v1.13.6-kmk.6.19-debug.apk`
VERSION_CODE: 619 (same version â€” follow-up to base v0.6.19 implementation)

## Scope

This document covers the follow-up requirements from Part 4 and Part 5 of the v0.6.19 plan that were not implemented in the base v0.6.19 pass. The base pass is documented in `KMK_RECS_V0_6_19_SOURCE_EVALUATION_UX_AND_BACKGROUND_EXECUTION_IMPLEMENTATION.md`.

## Changes Implemented

### 1. Notification Deep Link

**Problem:** Tapping the Source Evaluation notification (progress or complete) did nothing.

**Solution:**
- Added `OPEN_SOURCE_EVALUATION = "eu.kanade.tachiyomi.OPEN_SOURCE_EVALUATION"` constant to `Constants.kt`.
- `SourceEvaluationNotifier` now creates a `PendingIntent.getActivity()` (`openSourceEvaluationIntent`) pointing to `MainActivity` with the new action. It is applied to both the progress notification builder and the complete notification.
- `MainActivity.handleIntentAction()` now handles `Constants.OPEN_SOURCE_EVALUATION`: calls `navigator.popUntilRoot()` then `navigator.push(SourceEvaluationScreen())` and returns `null` (no tab change needed).

**Files changed:**
- `core/common/src/main/kotlin/tachiyomi/core/common/Constants.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationNotifier.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt`

### 2. Remaining Unassessed Count â€” Wording

**Problem:** `CandidateDiagnosticsRow` always showed "N extensions eligible for evaluation" even when `skipAlreadyEvaluated` was enabled, at which point the visible count is specifically the *unassessed* remaining count â€” a more precise description.

**Solution:**
- Added `skipAlreadyEvaluated: Boolean = false` parameter to `CandidateDiagnosticsRow`.
- When `skipAlreadyEvaluated = true`, the count text uses new string `source_evaluation_candidates_unassessed_remaining` ("N unassessed extensions remaining") instead of `source_evaluation_candidates_available`.
- Call site in the `LazyColumn` now passes `skipAlreadyEvaluated = state.options.skipAlreadyEvaluated`.

**Note:** No new `CandidateDiagnostics` field was needed. `state.candidates.size` is already the filtered-remaining count produced by `applyOptions()`; the wording change is sufficient.

**Files changed:**
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt`
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` (1 new string)

### 3. Installed Extension Exclusion â€” Tests

The existing test `installed extension is excluded` already covers `buildPool()` excluding installed packages from `allEligible`. Three additional tests were added to strengthen coverage:

| Test | What it proves |
|---|---|
| `installed extension not counted in eligible pool even when in available list` | When an extension appears in both `available` and `installedPkgNames`, only the non-installed extension appears in `allEligible` |
| `installed extension not counted in disliked hidden when also disliked` | An installed extension that is also in the dislike list does not inflate `dislikedHiddenCount` â€” the installed check runs first |
| `already-evaluated extension excluded from candidates when skipAlreadyEvaluated is true` | `applyOptions(skipAlreadyEvaluated = true)` places the evaluated extension in `evaluatedHiddenCount` and excludes it from `candidates`; the unevaluated extension remains |

**Files changed:**
- `app/src/test/java/exh/recs/evaluation/SourceEvaluationCandidateFilterTest.kt` (3 new tests; 25 total)

### 4. Offline / Connectivity Guard

**Problem:** Starting evaluation when offline would silently start a job that would fail on the first network call or return misleading empty results.

**Solution:** Added `context.isOnline()` guards in `SourceEvaluationScreenModel`:
- `startEvaluation()` â€” checked before showing the prompt-heavy dialog or calling `launchEvaluation()`. Sets `screenErrorMessage` (the existing error banner pattern) and returns early.
- `confirmAndStartWithPrompts()` â€” re-checks at dialog confirmation time in case connectivity dropped while the dialog was open.

The existing `screenErrorMessage` / `source_evaluation_screen_error_title` dialog pattern is reused â€” no new error UI was added.

**Scope note:** Offline handling during active evaluation (mid-run disconnection) is handled by the existing per-extension `withTimeoutOrNull` + error recording in `SourceEvaluationRunner`. Extensions that fail due to connectivity are marked as errors and the run continues with the next candidate â€” this behavior is unchanged. The new guard only prevents *starting* an evaluation without any connectivity.

**Files changed:**
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt`

### 5. Extension Repo Handling

No code changes. Per the plan:
- No new repos are added by default.
- The existing extension repo management screen (`Browse > Extension Repos`) is the official path for adding repos.
- **Keiyoushi** (`https://keiyoushi.github.io/extensions/index.min.json`) is the known/compatible extension repo for English manga sources. It is not auto-added; users add it manually via the existing flow.
- `ExtensionApi.getExtensions()` still returns `emptyList()` per repo on any error (unchanged). Broader error surfacing for repo failures is deferred to a future version.

## New Strings (1)

| Key | Value |
|---|---|
| `source_evaluation_candidates_unassessed_remaining` | `%1$d unassessed extensions remaining` |

## Test Results

- `SourceEvaluationCandidateFilterTest` â€” 25 tests, all PASSED
- `:app:assembleDebug` â€” BUILD SUCCESSFUL

## Constraints Preserved

- v0.6.18 `KnownUnsafeExtensionPackages` static guard and `ExtensionLoader` filter â€” unchanged.
- Shizuku is not the default path â€” unchanged.
- No unvetted repos added by default â€” unchanged.
- Offline failures do not affect source quality scores â€” the offline guard only prevents the job from starting; per-extension errors during evaluation remain separate from the quality scorer.

