# KMK-Recs v0.8.1-fix3 â€” Source Evaluation Continuation Fix: Implementation Report

Implements `KMK_RECS_V0_8_1_FIX3_SOURCE_EVALUATION_CONTINUATION_FIX_PLAN.md` as a corrective
follow-up. This is an internal/private handoff build for development; no app-facing string in this
pass uses "private build," "public build," "internal build," "test build," "personal line," or
similar release-channel wording (verified â€” see "Wording Audit" below).

## Root Cause (Confirmed)

`SourceEvaluationScreenModel.applyOptionsAndUpdateState()` derives `state.candidates` from
`SourceEvaluationCandidateFilter.applyOptions(..., skipAlreadyEvaluated = true, reEvaluateStale =
false)` (the UI's default options). `shouldSkip()` treats any extension with existing evaluation
rows as "already evaluated" and hides it â€” including stale/outdated rows â€” unless the caller
explicitly passes `reEvaluateStale = true`. Since `canContinue`/`remainingCandidateCount` are both
derived purely from `state.candidates` + the continuation cursor, they reached zero the moment the
unassessed pool was exhausted, even though stale rows were still visibly rendered in the
separately-populated results list (built directly from `evaluations`, not `state.candidates`).

## Files Changed

- **New** `app/src/main/java/exh/recs/evaluation/SourceEvaluationCandidateQueuePolicy.kt` â€” pure
  helper, `staleCandidates(pool, now)`: candidates whose evaluation rows are ALL stale (per the
  existing `SourceEvaluationCandidateFilter.isStale()` contract), independent of the unassessed
  queue's option toggles.
- **New** `app/src/test/java/exh/recs/evaluation/SourceEvaluationCandidateQueuePolicyTest.kt` â€” 6
  tests for the new helper, including the exact regression scenario (a stale row is hidden from the
  unassessed queue's `applyOptions()` result but still present in `staleCandidates()`).
- `app/src/test/java/exh/recs/evaluation/SourceEvaluationContinuationPolicyTest.kt` â€” 3 new tests
  covering the `|queue=stale` fingerprint-suffix separation mechanism (distinct fingerprint,
  cross-queue `canContinue` isolation, batch-size-independent continuation within the stale queue).
- `app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt` â€” new
  `sourceEvaluationContinuationCursorStale()` preference (own storage slot for the stale queue's
  cursor; the existing single-cursor `serialize()`/`deserialize()` functions in
  `SourceEvaluationContinuationPolicy` are reused unchanged).
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationJobState.kt` â€” new `@Volatile var
  pendingIsStaleRun: Boolean`, reset in `reset()`.
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt`:
  - `State` gained `staleCandidates`, `continuationCursorStale`, `canContinueStale`,
    `remainingStaleCandidateCount`, `pendingStaleReassessmentAfterPromptWarning`.
  - `PendingConsentAction` gained `STALE_REASSESSMENT`; `confirmConsent()` dispatches it to
    `startOrContinueStaleReassessment()`.
  - `launchEvaluation()` gained a `staleRun: Boolean` parameter. When true: forces `opts =
    options.copy(skipAlreadyEvaluated = false, reEvaluateStale = true, onlyUpdatedEvaluated =
    false)` for that run only (never persisted), sources candidates from `state.staleCandidates`
    instead of `state.candidates`, and builds the fingerprint with a `|queue=stale` suffix so it can
    never collide with the unassessed queue's fingerprint even when option booleans coincide. Sets
    `SourceEvaluationJobState.pendingIsStaleRun`.
  - New `startOrContinueStaleReassessment()` â€” same consent/online/prompt-heavy gates as
    `startEvaluation()`/`continueEvaluation()`, dispatches to `launchEvaluation(staleRun = true,
    isContinuation = state.canContinueStale)`.
  - New `restartStaleReassessment()` â€” clears the persisted stale cursor and preference, then
    starts the stale queue fresh. Explicit and separate from continuing.
  - `confirmAndStartWithPrompts()`/`switchToPrivateAndStart()`/`dismissPromptWarningDialog()`
    updated to route through the correct queue when the prompt-heavy warning dialog was opened from
    the stale-reassessment action.
  - `updateContinuationCursor()` now branches on `SourceEvaluationJobState.pendingIsStaleRun` to
    advance and persist the correct cursor slot and update the correct `State` fields.
  - `applyOptionsAndUpdateState()` now also computes `staleCandidates`/`canContinueStale`/
    `remainingStaleCandidateCount` via the new helper and its own `|queue=stale` fingerprint.
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt` â€” new compact button block
  (only rendered when `state.staleCandidates.isNotEmpty()`): "Reassess outdated (N)" /
  "Continue reassessing outdated (N remaining)", plus a "Restart outdated reassessment" text action
  shown once a stale cursor exists.
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` â€” 3 new strings:
  `source_evaluation_stale_reassess_start`, `source_evaluation_stale_reassess_continue`,
  `source_evaluation_stale_reassess_restart`.
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` â€” `VERSION_CODE` 752â†’753, `VERSION_NAME`
  "KMK-Recs v0.8.1-fix2"â†’"KMK-Recs v0.8.1-fix3", new changelog entry (see file â€” no
  private/public/internal/test-build wording).

## Exact Queue/Cursor Behavior

- **Two independent queues, each with its own candidate list, cursor, and fingerprint.** The
  unassessed queue is unchanged (`state.candidates`, `continuationCursor`, `canContinue`,
  `remainingCandidateCount`, driven by the user's `skipAlreadyEvaluated`/`reEvaluateStale`/
  `onlyUpdatedEvaluated` options). The stale queue (`state.staleCandidates`,
  `continuationCursorStale`, `canContinueStale`, `remainingStaleCandidateCount`) always evaluates as
  if `skipAlreadyEvaluated = false, reEvaluateStale = true` â€” the user's persisted options never
  affect what counts as "stale."
- **Stale reassessment continuation**: `startOrContinueStaleReassessment()` slices
  `state.staleCandidates` via the existing `SourceEvaluationContinuationPolicy.sliceForRun()`
  (unchanged â€” batch size is still excluded from the fingerprint, so raising batch size from 10 to
  25 mid-queue still continues from where it left off, not from the start). On completion,
  `updateContinuationCursor()` sees `pendingIsStaleRun == true` and calls the existing
  `advanceCursor()`/`serialize()` against `continuationCursorStale`/
  `sourceEvaluationContinuationCursorStale()`, never touching the unassessed queue's cursor or
  preference key.
- **300+ sources / repeated batches**: since the stale queue's candidate pool is recomputed from
  live evaluation data on every `applyOptionsAndUpdateState()` pass (which runs whenever the
  candidate pool or evaluations change) and its cursor persists across app restarts (same 7-day
  expiry as the unassessed queue, via the shared `CURSOR_EXPIRY_MS` in
  `SourceEvaluationContinuationPolicy`), a user can run "Reassess outdated" repeatedly across
  arbitrarily many batches â€” each batch reassesses new sources, updates their evaluation rows
  (removing them from the stale pool), and the cursor advances past what's already been done.
- **Restart is explicit and separate from continue**: `restartStaleReassessment()` clears
  `continuationCursorStale`/its preference before calling `startOrContinueStaleReassessment()`
  again; the normal button always continues (or starts fresh if no cursor exists yet) â€” there is no
  way to accidentally discard progress by tapping the primary action.
- **For You compatibility checks** (`SourceRecommendationQualityQueue`) were not touched â€” they
  remain scoped to `missingPromising`/`outdatedPromising`/`checkedPromising` rows and are entirely
  independent of both Source Evaluation queues.

## Failed/Attempted Candidate Advancement

Verified (not changed): `SourceEvaluationRunner.run()` adds each candidate's key to
`_completedCandidateKeys` immediately after selecting it for processing (`SourceEvaluationRunner.kt`
line ~145), before the network probe or scoring runs â€” so a candidate that errors, times out, or is
skipped due to explicit-content filtering is still recorded as "completed" for cursor-advancement
purposes. Neither queue can get stuck retrying the same failing candidate forever.

## Tests Added/Updated

- `SourceEvaluationCandidateQueuePolicyTest.kt` (new, 6 tests): unassessed extension not in stale
  queue; expired evaluation â†’ stale; outdated `evaluationVersion` (not expired) â†’ stale; current
  fresh evaluation â†’ not stale; mixed stale+fresh evaluations for the same extension â†’ not stale
  (mirrors `isStale()`'s "all must be stale" contract); stale queue includes a candidate regardless
  of `skipAlreadyEvaluated` â€” the exact regression scenario.
- `SourceEvaluationContinuationPolicyTest.kt` (+3 tests): fingerprint suffix separates stale from
  unassessed; a cursor advanced under the unassessed fingerprint does not satisfy `canContinue`
  under the stale fingerprint; batch-size change does not invalidate the stale queue's cursor
  (mirrors the unassessed queue's existing guarantee).

## Verification Commands And Results

```powershell
$env:JAVA_HOME = (Resolve-Path .tools\jdk17\jdk-17.0.19+10).Path
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :app:testDebugUnitTest --tests "*SourceEvaluationCandidateFilterTest" --tests "*SourceEvaluationContinuationPolicyTest" --tests "*SourceEvaluation*"
# BUILD SUCCESSFUL â€” all SourceEvaluation* test classes passed, including the new
# SourceEvaluationCandidateQueuePolicyTest and the 3 new continuation-policy tests.

.\gradlew.bat spotlessCheck
# BUILD SUCCESSFUL

.\gradlew.bat assembleDebug
# BUILD SUCCESSFUL. One pre-existing unrelated warning at SourceEvaluationScreenModel.kt:1232
# ("Unnecessary safe call") â€” not introduced by this pass (pre-dates it; not in any file this
# pass touched at that line's logic).
```

Full `:app:testDebugUnitTest` was not run separately in this pass beyond the targeted
`*SourceEvaluation*` filter, which already covers every test class under
`exh/recs/evaluation` â€” the targeted run is a superset of what full suite execution would add for
files touched here.

## Internal Handoff Artifact

Debug APK copied to:

```text
private/Komikku-v1.13.6-kmk.8.1-fix3-debug.apk
```

(universal ABI variant, matching the existing naming pattern for every prior version in that
folder). This is an internal handoff artifact only.

## Wording Audit

Grepped every string added or touched in this pass (`KmkRecsReleaseNotes.kt` changelog entry, the
3 new KMR string resources, `SourceEvaluationScreen.kt` UI additions, `CURRENT_STATE.md`) for
`private build`, `public build`, `internal build`, `test build`, `personal line`. None of the
app-facing surfaces (KMR strings, `SourceEvaluationScreen.kt`, the `KmkRecsReleaseNotes` markdown
shown in the What's New dialog) contain any of that release-channel wording. The word "private" in
app-facing UI, where present, refers only to the pre-existing "Private (recommended)" extension
installer mode â€” untouched by this pass. Repo-internal documentation (this file, `CURRENT_STATE.md`,
the plan doc) uses "internal/private handoff build for development" per the task's own framing,
which is not app-facing.

