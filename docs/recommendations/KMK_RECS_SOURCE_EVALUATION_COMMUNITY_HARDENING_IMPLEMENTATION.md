# KMK-Recs Source Evaluation Community Hardening Implementation

Date: 2026-06-26

Status: SUPERSEDED. This document covers the initial Phase 5 work. The versioning was corrected to KMK-Recs v0.7.11 (versionCode 83, APK `Komikku-v1.13.6-kmk.7.11-debug.apk`) in a follow-up session. The consent flow also had bugs that were fixed in the same follow-up. See `KMK_RECS_V0_7_11_SOURCE_EVALUATION_HARDENING_CORRECTION_IMPLEMENTATION.md` for the corrective implementation.

This document covers Phase 5: Source Evaluation Hardening (initial implementation â€” strings, cleanup warnings, KMR string migration).

---

## What Changed

### 1. Pre-run consent dialog

Before any evaluation can start, the user must acknowledge a one-time warning. On first tap of "Start evaluation", a dialog is shown:

- **Title**: "Source Evaluation â€” Advanced Feature"
- **Body**: Explains install/probe/cleanup/mode behavior; notes Private mode is recommended; notes library and already-installed extensions are not changed; notes the warning is accessible again from the Copy Diagnostics area.
- **"I understand, continue"**: persists consent (`source_evaluation_consent_given` = true) and proceeds.
- **Cancel**: dismisses without starting.

Once acknowledged, consent is persisted. The dialog never blocks again unless the preference is cleared.

The same dialog is shown when tapping **"View evaluation warning"** â€” a new TextButton added to the diagnostics row so the user can re-read the warning at any time.

Consent is also checked in `startReassessUpdated()` (reassess-updated-only evaluation start), so the first run of either path shows the dialog.

**Files:** `SourceEvaluationConsentPolicy.kt` (new pure helper), `SourcePreferences.kt` (+preference), `SourceEvaluationScreenModel.kt` (consent state + actions), `SourceEvaluationScreen.kt` (dialog + "View warning" button).

**Test:** `SourceEvaluationConsentPolicyTest.kt` â€” 3 tests.

---

### 2. Process-death state-lost error now uses KMR string

`SourceEvaluationJob.doWork()` previously wrote a hardcoded English error message when `pendingCandidates` was null (process killed while evaluation was pending). Now uses `context.stringResource(KMR.strings.source_evaluation_state_lost_error)`.

**File:** `SourceEvaluationJob.kt`.

---

### 3. Crash recovery message now uses KMR string

`SourceEvaluationScreenModel.init` previously wrote a hardcoded English crash quarantine message. Now uses `context.stringResource(KMR.strings.source_evaluation_crash_recovery_marked_unsafe, extensionName, phase)`.

**File:** `SourceEvaluationScreenModel.kt`.

---

### 4. All offline error messages now use KMR string

Four separate hardcoded "No internet connection..." strings replaced with `KMR.strings.source_evaluation_offline_error`:

- `startReassessUpdated()`
- `startEvaluation()`
- `confirmAndStartWithPrompts()`
- `continueEvaluation()`

**File:** `SourceEvaluationScreenModel.kt`.

---

### 5. Cleanup outcome warnings in EvaluationSummaryCard

`EvaluationSummaryCard` (shown after completed/failed/cancelled runs) now shows:

- **"N extension(s) require a manual uninstall prompt. Open Browse > Extensions to complete cleanup."** â€” shown in `MaterialTheme.colorScheme.error` when `promptRequiredCleanupCount > 0`.
- **"N extension(s) cleanup failed. Check Browse > Extensions."** â€” shown in `MaterialTheme.colorScheme.error` when `cleanupFailedCount > 0`.

`cleanupFailedCount` computed property added to `SourceEvaluationQueueState`.

**Files:** `SourceEvaluationQueueState.kt` (+cleanupFailedCount), `SourceEvaluationScreen.kt` (+warnings).

---

### 6. Pre-existing lint issues fixed

Three pre-existing ktlint violations blocked `spotlessApply`. Fixed as part of this pass:

- `SourceEvaluationCandidateFilter.kt`: `import exh.recs.evaluation.SourceEvaluationUpdatePolicy` was inside KMK comment markers, breaking ktlint import ordering. Moved into regular import block.
- `OcrSkipLogicTest.kt`: `CURRENT_IDENTITY`, `CURRENT_ENGINE_VERSION` renamed to `currentIdentity`, `currentEngineVersion` (property-naming rule).
- `SourceEvaluationStartupRecoveryTest.kt`: `RECENT_MS`, `STALE_MS` renamed to `recentMs`, `staleMs` (property-naming rule).

---

## New Files

| File | Purpose |
|---|---|
| `app/src/main/java/exh/recs/evaluation/SourceEvaluationConsentPolicy.kt` | Pure stateless consent gate. `isConsentRequired(consentGiven: Boolean): Boolean` |
| `app/src/test/java/exh/recs/evaluation/SourceEvaluationConsentPolicyTest.kt` | 3 unit tests for consent gate |

---

## Modified Files

| File | Change |
|---|---|
| `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` | +8 KMR strings (consent title/message/confirm, view warning, offline error, state lost error, cleanup prompt warning, cleanup failed warning) |
| `app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt` | +`sourceEvaluationConsentGiven()` preference |
| `app/src/main/java/exh/recs/evaluation/SourceEvaluationQueueState.kt` | +`cleanupFailedCount` computed property |
| `app/src/main/java/exh/recs/evaluation/SourceEvaluationJob.kt` | State-lost error now uses KMR string |
| `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt` | +consent State field, +consent methods, +offline error KMR, +crash recovery KMR, +import stringResource/KMR |
| `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt` | +consent dialog, +"View warning" button, +cleanup outcome warnings in EvaluationSummaryCard |
| `app/src/main/java/exh/recs/evaluation/SourceEvaluationCandidateFilter.kt` | Import ordering fix (lint) |
| `app/src/test/java/exh/ocr/OcrSkipLogicTest.kt` | Property naming fix (lint) |
| `app/src/test/java/exh/recs/evaluation/SourceEvaluationStartupRecoveryTest.kt` | Property naming fix (lint) |
| `app/build.gradle.kts` | versionCode 81 â†’ 82 |

---

## KMR Strings Added

```xml
<!-- KMK v0.8.0: source evaluation hardening -->
<string name="source_evaluation_consent_title">Source Evaluation â€” Advanced Feature</string>
<string name="source_evaluation_consent_message">...</string>
<string name="source_evaluation_consent_confirm">I understand, continue</string>
<string name="source_evaluation_view_warning">View evaluation warning</string>
<string name="source_evaluation_offline_error">No internet connection. Please check your connection and try again.</string>
<string name="source_evaluation_state_lost_error">Evaluation state was lost (app process restarted). Please start evaluation again.</string>
<string name="source_evaluation_cleanup_prompt_required_warning">%1$d extension(s) require a manual uninstall prompt. Open Browse > Extensions to complete cleanup.</string>
<string name="source_evaluation_cleanup_failed_warning">%1$d extension(s) cleanup failed. Check Browse > Extensions.</string>
```

---

## Build Results

```
.\gradlew.bat spotlessApply     â†’ BUILD SUCCESSFUL
.\gradlew.bat spotlessCheck     â†’ (implied by spotlessApply success)
.\gradlew.bat :app:testDebugUnitTest â†’ BUILD SUCCESSFUL (267 tasks)
.\gradlew.bat assembleDebug     â†’ BUILD SUCCESSFUL
```

Output APK: `Komikku-v1.13.6-kmk.8.0-debug.apk` (162.4 MB)

---

## What Remains Deferred

The following Phase 5 goals were already fully implemented in prior versions and required no changes:

- Process-death honest recovery: already in `SourceEvaluationJob.doWork()` (only string hardcoding fixed).
- Notification tap returning to SE screen: already via `Constants.OPEN_SOURCE_EVALUATION` deep link.
- Reactive installed-extension filtering: already in v0.7.10.
- Show/hide installed toggle reversibility: already fixed in v0.7.7.
- Batch continuation cursor: already in v0.7.6.
- Rec-quality probe for non-installed sources: already in v0.7.10.
- Network check before start (`context.isOnline()`): already present.
- Quarantine UI not dominant: already in `SafetyDiagnosticsRow`.
- CleanupStatus tracked per result: already in v0.6.13.

The following items were discussed in the Phase 5 specification but are explicitly deferred to a future pass:

- Per-source error category standardization (install timeout, source not found, network unavailable categories shown distinctly in UI).
- Network-loss retry/refresh UI (mid-run connectivity detection beyond per-extension timeout).
- Quarantine collapsed-by-default count chip expansion.
- `startReassessUpdated()` consent check: currently checks consent before starting, which is correct; no special bypass needed.

