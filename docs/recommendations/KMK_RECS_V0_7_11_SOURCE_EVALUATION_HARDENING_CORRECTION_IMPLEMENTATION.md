# KMK-Recs v0.7.11 Source Evaluation Hardening Correction

Date: 2026-06-27

Status: COMPLETE. KMK-Recs v0.7.11. APK: `Komikku-v1.13.6-kmk.7.11-debug.apk`. versionCode 83.

This document covers the Phase 5 corrective follow-up for Source Evaluation hardening. It supersedes the "v0.8.0" versioning applied in the previous session and corrects several bugs and versioning inconsistencies introduced there.

See also: `KMK_RECS_SOURCE_EVALUATION_COMMUNITY_HARDENING_IMPLEMENTATION.md` (initial Phase 5 implementation, which applied the strings and cleanup warnings correctly but had the bugs described below).

---

## What Was Wrong

### 1. Consent view-only path could accidentally start evaluation

`showConsentWarning()` opened the same dialog as the pre-evaluation consent check, and `confirmConsent()` always called `startEvaluation()`. Result: tapping "View evaluation warning" and confirming the dialog would start an evaluation run â€” opposite of the intended behavior.

### 2. `startReassessUpdated()` skipped the consent gate entirely

The Phase 5 implementation added the consent check only to `startEvaluation()` and `continueEvaluation()` was not checked at all. The implementation doc claimed reassess-updated also checked consent, but the code did not.

Additionally, `confirmConsent()` always routed to `startEvaluation()`, so even if a user reached the consent dialog via reassess-updated, confirming it would run a regular evaluation instead of the reassess-updated path.

### 3. Versioning drift

- `app/build.gradle.kts` had `versionCode = 82 // KMK-Recs v0.8.0`
- `KmkRecsReleaseNotes.kt` had `VERSION_CODE = 800` but `VERSION_NAME = "KMK-Recs v0.7.10"` (stale name, wrong code)
- Docs said v0.8.0
- No What's New notes had been added for the Phase 5 user-facing changes

### 4. Process-death documentation overclaim

R-002 in the risk register was marked "MITIGATED v0.8.0" but the honest recovery is an in-memory state that is lost on process kill. The fix is an honest failure message, not durable resume.

---

## What Changed

### Consent flow â€” `PendingConsentAction` enum

Added `PendingConsentAction` enum to `SourceEvaluationScreenModel`:

```kotlin
enum class PendingConsentAction {
    START_EVALUATION,
    REASSESS_UPDATED,
    CONTINUE_EVALUATION,
    VIEW_ONLY,
}
```

Added `pendingConsentAction: PendingConsentAction? = null` to `State`.

`showConsentWarning()` now sets `pendingConsentAction = VIEW_ONLY`.

`startEvaluation()` sets `pendingConsentAction = START_EVALUATION`.

`startReassessUpdated()` sets `pendingConsentAction = REASSESS_UPDATED`.

`continueEvaluation()` sets `pendingConsentAction = CONTINUE_EVALUATION`.

`confirmConsent()` now reads `pendingConsentAction` from state, clears both `showConsentDialog` and `pendingConsentAction`, then dispatches:
- `START_EVALUATION` â†’ `startEvaluation()`
- `REASSESS_UPDATED` â†’ `doReassessUpdated()`
- `CONTINUE_EVALUATION` â†’ `continueEvaluation()`
- `VIEW_ONLY` / null â†’ do nothing (dialog closes, no evaluation starts)

`dismissConsent()` now also clears `pendingConsentAction`.

### `startReassessUpdated()` consent check added

`startReassessUpdated()` now checks `SourceEvaluationConsentPolicy.isConsentRequired()` before calling any evaluation logic. The actual reassess logic was extracted into a private `doReassessUpdated()` so `confirmConsent()` can dispatch to it directly.

### `continueEvaluation()` consent check added

`continueEvaluation()` now checks consent before the online/prompt-heavy checks. This guards the edge case where a user somehow reaches "Continue" before ever seeing the warning.

### Versioning corrected to v0.7.11

- `app/build.gradle.kts`: versionCode 82 â†’ 83, comment â†’ `KMK-Recs v0.7.11`
- `KmkRecsReleaseNotes.kt`: VERSION_CODE 800 â†’ 711, VERSION_NAME "v0.7.10" â†’ "v0.7.11"; v0.7.11 What's New section added
- `SourceEvaluationConsentPolicy.kt`: version comment v0.8.0 â†’ v0.7.11
- `SourceEvaluationConsentPolicyTest.kt`: version comment v0.8.0 â†’ v0.7.11
- `SourceEvaluationScreenModel.kt`: KMK marker comments v0.8.0 â†’ v0.7.11

---

## Modified Files

| File | Change |
|---|---|
| `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt` | +`PendingConsentAction` enum; +`pendingConsentAction` to State; fix `showConsentWarning`/`dismissConsent`/`confirmConsent`; add consent check to `startReassessUpdated` + extract `doReassessUpdated`; add consent check to `continueEvaluation`; v0.8.0 â†’ v0.7.11 comments |
| `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` | VERSION_CODE 800 â†’ 711; VERSION_NAME v0.7.10 â†’ v0.7.11; added v0.7.11 What's New section |
| `app/build.gradle.kts` | versionCode 82 â†’ 83; comment v0.8.0 â†’ v0.7.11 |
| `app/src/main/java/exh/recs/evaluation/SourceEvaluationConsentPolicy.kt` | version comment v0.8.0 â†’ v0.7.11 |
| `app/src/test/java/exh/recs/evaluation/SourceEvaluationConsentPolicyTest.kt` | version comment v0.8.0 â†’ v0.7.11 |

---

## Build Results

```
.\gradlew.bat spotlessApply        â†’ BUILD SUCCESSFUL
.\gradlew.bat :app:testDebugUnitTest â†’ BUILD SUCCESSFUL (267 tasks)
.\gradlew.bat assembleDebug        â†’ BUILD SUCCESSFUL
```

Output APK: `Komikku-v1.13.6-kmk.7.11-debug.apk` (162.4 MB)

---

## Remaining Deferred Items

- Structured Source Evaluation error categories (install timeout, source not found, network unavailable shown as distinct UI labels) â€” no change in this pass.
- Network-loss retry/refresh UI during a running batch â€” no change in this pass.
- Evidence strength strings full i18n hookup (`source_evaluation_evidence_*` keys exist but labels still hardcoded in `evidenceStrengthLabel()` / `lastEvaluatedLabel()`) â€” no change in this pass.
- Process-death durable resume: still in-memory only. Honest failure message is in place.

---

## Process-Death Behavior Clarification

`SourceEvaluationJobState.pendingCandidates` and `pendingOptions` are `@Volatile` in-memory fields. If the app process is killed between enqueuing the job and `doWork()` starting, they become null and the job writes a `source_evaluation_state_lost_error` failure to the queue state. The user sees a clear failure message and can restart or continue using the existing batch continuation flow.

This is honest recovery, not durable resume. Process-death is handled by failing clearly, not by persisting the candidate queue to disk.

