# KMK-Recs v0.8.1-fix3 Source Evaluation Continuation Fix Plan

Status: implementation plan for Claude Code. Do not modify app code until the user explicitly approves the implementation prompt.

Target line: KMK-Recs v0.8.1 corrective follow-up.

Build handling: this is an internal/private handoff build for the user's current app line. This wording is for development coordination only. Do not put "private build", "public build", "internal build", or similar development labels in any in-app UI, KMK What's New entry, dialog, subtitle, toast, string resource, or user-facing release note.

## User-Observed Problem

After the source-evaluation tag enrichment/scoring improvements, the first reassessment batch works:

- the first batch of 100 sources runs;
- metadata/tag detail is now visibly better;
- newly useful sources appear because tags are actually being detected;
- For You compatibility remains correctly limited to promising fit rows.

The broken part is continuation after that first batch:

- the Source Evaluation screen reports `0 unassessed extensions remaining`;
- the Start button becomes disabled with `Start evaluation (0 sources)`;
- the list still shows many existing rows as `Outdated -- reassess needed`;
- those stale/outdated rows are not being offered as the next actionable batch;
- the user cannot continue from the first 100 into the next stale/reassessment group.

This is not a complaint that the first 100 failed. The first 100 did reassess. The problem is that source evaluation cannot proceed past that first set even though many stale rows still need reassessment.

## Evidence From Current Code

Relevant files:

- `app/src/main/java/exh/recs/evaluation/SourceEvaluationCandidateFilter.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationContinuationPolicy.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationQueueState.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationUpdatePolicy.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationDisplayPolicy.kt`
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityQueue.kt`
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityJobState.kt`
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityJob.kt`

Current behavior seen in code:

- `SourceEvaluationOptions.skipAlreadyEvaluated` defaults to `true`.
- `SourceEvaluationOptions.reEvaluateStale` defaults to `false`.
- `SourceEvaluationCandidateFilter.shouldSkip(...)` skips any extension with existing evaluation rows when `skipAlreadyEvaluated == true`, unless `reEvaluateStale == true && isStale(evals, now)`.
- The current UI prominently exposes "Skip already-evaluated sources", but does not clearly expose or automatically enter a stale-reassessment continuation mode when visible rows say `Outdated -- reassess needed`.
- `SourceEvaluationContinuationPolicy` already exists and can slice a candidate list by cursor.
- `SourceEvaluationScreenModel.launchEvaluation(isContinuation = true)` can use the cursor for continuation.
- `SourceEvaluationScreenModel.continueEvaluation()` exists, but it depends on `state.canContinue`, which is computed from the currently-filtered `state.candidates`.
- Because stale rows are filtered out as already evaluated when `reEvaluateStale == false`, `state.candidates` can become empty and `canContinue` can become false, even though stale rows still exist.

Therefore the main bug is candidate-queue semantics:

> Stale/outdated rows that require reassessment are display-visible as stale work, but option filtering treats them as completed work unless an internal stale-reassessment flag is enabled. The UI and action model do not make stale reassessment a first-class continuation queue.

## Goals

1. Allow Source Evaluation to continue through all stale/outdated reassessment work in batches.
2. Do not regress the working first-batch reassessment behavior.
3. Preserve the existing never-assessed source flow.
4. Make the UI truthful: if stale rows are visible as `reassess needed`, the user must have a visible action to reassess them.
5. Keep "Start from beginning / reassess all" clearly separate from "continue next batch".
6. Ensure For You compatibility checks keep their existing promising-source scope, but also have truthful counts and do not imply full source reassessment.
7. Keep all development/build-line wording out of app-facing strings.

## Non-Goals

- Do not redesign source scoring again.
- Do not change the tag enrichment/scoring model from v0.7.47/v0.8.1-fix1 unless needed to support stale continuation.
- Do not make For You compatibility run for weak/non-promising sources by default.
- Do not add source-specific hardcoded rules.
- Do not expose developer-only wording such as "private build" or "public build" in app UI.

## Required Behavior

### 1. Separate Candidate Queues

Claude should make the source-evaluation screen distinguish at least these actionable groups:

- `unassessed`: eligible non-installed sources with no current evaluation record;
- `stale`: eligible non-installed sources whose evaluations are expired or have `evaluationVersion < SourceEvaluationKeys.CURRENT_VERSION`;
- `updated`: eligible evaluated extensions whose available extension metadata changed since their last evaluation;
- `current`: evaluated rows that are current and should be skipped by default;
- `hidden`: explicit/blocked/quarantined/disliked/installed rows that are not candidates for the current run.

Implementation should prefer a pure helper rather than ad-hoc UI logic. Recommended helper:

```kotlin
object SourceEvaluationCandidateQueuePolicy {
    data class QueueSummary(
        val unassessed: List<EvaluationCandidate>,
        val stale: List<EvaluationCandidate>,
        val updated: List<EvaluationCandidate>,
        val current: List<EvaluationCandidate>,
        val explicitHiddenCount: Int,
        val blockedHiddenCount: Int,
        val unsafeHiddenCount: Int,
    )
}
```

It can live near `SourceEvaluationCandidateFilter` or replace/extend that helper. If Claude chooses a different structure, it must keep the logic pure and unit-testable.

### 2. Fix `skipAlreadyEvaluated` Semantics

`skipAlreadyEvaluated = true` should mean:

- skip current already-evaluated rows;
- do not skip stale/outdated rows when the active mode is stale reassessment;
- do not label stale rows merely as hidden "already evaluated" work.

The current `evaluatedHiddenCount` is misleading when it includes stale rows. Split this into:

- `currentEvaluatedHiddenCount`;
- `staleReassessmentCount`;
- optionally `updatedEvaluatedCount`.

Do not show `367 already-evaluated hidden` as the only explanation when those rows include stale/outdated items that need reassessment.

### 3. Add An Explicit Stale Continuation Action

When stale/outdated rows exist, the primary action should be one of:

- `Reassess next 100 outdated sources`; or
- `Continue reassessment (N remaining)`.

The exact wording should follow existing Komikku/KMR style, but it must be user-facing and clear.

The action must:

- run only the next batch of stale/outdated candidates;
- use the selected batch size;
- update the stale continuation cursor after completion;
- allow the next batch to be started without restarting from the first stale group;
- work with the private/internal extension installer mode currently recommended by Source Evaluation.

### 4. Keep Fresh Evaluation Continuation

For never-assessed sources, keep the existing continuation behavior:

- `Start evaluation` or `Evaluate next N unassessed sources` should run the next unassessed batch.
- If a valid cursor exists, the screen should make continuing the remaining unassessed sources obvious.
- Restarting from the beginning should be an explicit secondary action, not the default accidental path.

### 5. Cursor Scope

The current cursor fingerprint includes:

- languages;
- includeExplicit;
- skipAlreadyEvaluated;
- reEvaluateStale;
- onlyUpdatedEvaluated;
- blockExplicit.

This is fragile because fresh, stale, and updated queues need independent continuation.

Claude should introduce a queue mode into the cursor/fingerprint model, for example:

```kotlin
enum class SourceEvaluationQueueMode {
    UNASSESSED,
    STALE_REASSESSMENT,
    UPDATED_REASSESSMENT,
    FULL_REASSESSMENT,
}
```

Then either:

- store separate cursor strings per mode; or
- include mode in the serialized cursor and clear/replace only the active mode.

Preferred approach: separate preference keys or one serialized map keyed by mode, whichever best matches the app's preference style. Keep it simple and testable.

Important: changing batch size must not invalidate continuation. Changing language, explicit filtering, blocked-explicit state, or queue mode may invalidate continuation.

### 6. Completion And Refresh Semantics

After a stale reassessment batch completes:

- updated rows should become current under the new `SourceEvaluationKeys.CURRENT_VERSION`;
- the stale queue should shrink;
- the counter should update without leaving and reopening the screen;
- the next action should continue with the next stale rows, not restart from the first stale rows;
- when no stale rows remain, the action should disappear or become disabled with a clear state.

If some sources fail during reassessment:

- failed rows should still count as processed for the cursor if they were attempted;
- they should remain visible as error rows in past evaluations;
- the app should not get stuck retrying the same failed first batch forever unless the user explicitly chooses a retry-all/errors action.

### 7. For You Compatibility Section

The user confirmed the current For You compatibility check intentionally applies only to fit/promising rows. Keep that.

But the UI should not confuse this with source reassessment:

- make it clear that For You compatibility is a secondary check for promising source rows;
- do not use it as the continuation mechanism for catalogue/source reassessment;
- ensure its counts still distinguish checked, outdated, missing, weak, no-results, errors, and good/great outcomes.

If For You compatibility has missing/outdated promising sources, keep its existing check/recheck actions. Do not make it run against all stale weak rows by default.

### 8. Development Wording Must Not Leak To UI

The user explicitly clarified that "private" and "public" are development/handoff terms only.

Claude must audit any app-facing changes touched in this implementation:

- KMR strings;
- KMK What's New entries;
- Source Evaluation labels;
- About/version subtitles;
- dialogs;
- toasts/snackbars;
- release-note text shown in app.

Do not add or retain wording such as:

- "private build";
- "public build";
- "private release";
- "public release";
- "internal build";
- "test build";
- "personal line".

Allowed user-facing wording:

- installer mode labels that already exist as functional settings, such as `Private (recommended)`, because that describes the extension installer mode, not the APK release channel;
- normal feature wording like "Source Evaluation", "Reassess outdated sources", "Continue next batch".

Internal documentation and Claude/Codex prompts may still refer to private handoff builds.

## Specific Code Areas To Inspect And Change

### `SourceEvaluationCandidateFilter.kt`

Current issue:

```kotlin
if (reEvaluateStale && isStale(evals, now)) return false
return true
```

This only lets stale rows pass when `reEvaluateStale` is true, but the UI does not make stale reassessment a normal first-class queue. Claude should not merely toggle `reEvaluateStale` globally; it should make stale reassessment an explicit queue mode.

Expected changes:

- split stale/current filtering;
- expose stale counts separately;
- ensure stale rows can be selected for `STALE_REASSESSMENT` batches;
- add tests proving stale rows are not counted as current hidden rows when stale reassessment is available.

### `SourceEvaluationContinuationPolicy.kt`

Expected changes:

- include queue mode in cursor identity;
- support continuation for stale and unassessed queues independently;
- keep batch size out of the fingerprint;
- add tests:
  - first stale batch returns first N stale candidates;
  - second stale batch returns next N stale candidates after cursor advance;
  - unassessed cursor does not interfere with stale cursor;
  - changing batch size keeps continuation valid;
  - changing language/filter/mode invalidates appropriately.

### `SourceEvaluationScreenModel.kt`

Expected changes:

- compute separate queue summaries from the candidate pool;
- expose state fields for:
  - unassessed remaining;
  - stale reassessment remaining;
  - current evaluated hidden;
  - explicit hidden;
  - quarantined/blocked hidden;
  - active queue mode;
- add methods like:
  - `startUnassessedEvaluation()` or keep `startEvaluation()` but make it clearly mode-aware;
  - `continueUnassessedEvaluation()`;
  - `startOrContinueStaleReassessment()`;
  - `startUpdatedReassessment()` if current update reassessment remains separate;
  - `restartFullReassessment()` only as explicit secondary/destructive action.
- make `launchEvaluation(...)` accept an explicit queue mode and candidate list instead of relying on `state.candidates` ambiguity.

Do not let `state.candidates` mean both "unassessed candidates" and "stale reassessment candidates" without naming. That ambiguity is why the current screen can say zero while stale work exists.

### `SourceEvaluationScreen.kt`

Expected changes:

- show separate counters:
  - `N unassessed sources remaining`;
  - `N outdated sources need reassessment`;
  - `N current evaluated sources hidden`;
  - `N explicit candidates hidden`;
  - quarantine/blocked counts as already present.
- show primary action depending on available work:
  - if unassessed remain: evaluate/continue unassessed;
  - if no unassessed remain but stale remain: reassess/continue outdated;
  - if both exist, show both but avoid crowding on phone screens.
- keep phone layout compact. Prefer one primary button plus secondary text/outlined actions, not a dense row of many full-width controls.
- avoid user-facing private/public build wording.

### `SourceEvaluationJobState.kt` / `SourceEvaluationJob.kt`

Expected changes:

- carry active queue mode through the job state;
- advance the correct cursor after completion;
- ensure failed/attempted candidates advance cursor as processed so one bad source does not trap the queue;
- preserve existing process-death honest failure behavior unless Claude can safely improve it without broad scope.

### `SourceEvaluationDiagnosticsBuilder.kt`

Expected changes:

- include queue mode;
- include unassessed/stale/current hidden counts;
- include cursor state summary;
- keep diagnostics copy useful for future Codex/Claude analysis.

## Tests Required

Claude must add or update tests. Suggested tests:

### Candidate Filtering

File likely:

- `app/src/test/java/exh/recs/evaluation/SourceEvaluationCandidateFilterTest.kt`

Tests:

1. current evaluated rows are skipped in unassessed mode;
2. stale rows are not treated as current hidden rows;
3. stale reassessment mode returns stale rows even when skip-already-evaluated is true;
4. explicit hidden rows remain hidden unless include explicit is enabled;
5. updated-only mode still returns only updated evaluated rows.

### Continuation

File likely:

- `app/src/test/java/exh/recs/evaluation/SourceEvaluationContinuationPolicyTest.kt`

Tests:

1. unassessed first batch then next batch;
2. stale first batch then next batch;
3. stale cursor and unassessed cursor do not collide;
4. completed failed candidates still advance;
5. batch size change does not reset cursor;
6. queue mode/filter change invalidates cursor.

### ScreenModel

If practical in existing test style:

- verify `SourceEvaluationScreenModel` exposes non-zero stale count when stale rows exist and unassessed count is zero;
- verify start action is disabled only when there is no actionable unassessed/stale/updated work;
- verify after job completion the next stale batch remains available until stale queue is exhausted.

### UI Strings

Search/audit:

- no user-facing "private build", "public build", "internal build", "test build", "personal line";
- no developer-only implementation notes in KMK What's New.

## Verification Commands

Claude should run at minimum:

```powershell
$env:JAVA_HOME = (Resolve-Path .tools\jdk17\jdk-17.0.19+10).Path
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :app:testDebugUnitTest --tests "*SourceEvaluationCandidateFilterTest" --tests "*SourceEvaluationContinuationPolicyTest" --tests "*SourceEvaluation*"
.\gradlew.bat spotlessCheck
.\gradlew.bat assembleDebug
```

If full `:app:testDebugUnitTest` is feasible, run it too. If any command cannot be run due environment limitations, document the exact failure.

## Manual QA Checklist

On device/tablet:

1. Open Source Evaluation.
2. Set batch size to 100.
3. Run stale/current-taste reassessment.
4. Confirm first 100 run.
5. Return to Source Evaluation.
6. Confirm stale/outdated count decreases but remains non-zero if more stale rows exist.
7. Confirm visible action says to continue/reassess next stale batch, not `Start evaluation (0 sources)`.
8. Run next batch.
9. Confirm it does not repeat the exact same first 100 unless the user explicitly selected restart/reassess all.
10. Confirm when all stale rows are done, stale action disappears or clearly says none remain.
11. Confirm For You compatibility still only applies to promising/current fit rows and remains separate from stale source reassessment.
12. Confirm KMK What's New and Source Evaluation UI do not mention "private/public build" wording.

## Documentation Requirements

Claude must create an implementation report:

`docs/recommendations/KMK_RECS_V0_8_1_FIX3_SOURCE_EVALUATION_CONTINUATION_FIX_IMPLEMENTATION.md`

It must record:

- files changed;
- exact queue/cursor behavior implemented;
- how stale reassessment continuation works;
- how failed candidates advance;
- tests added/updated;
- verification commands and results;
- APK path copied for the internal handoff build;
- confirmation that app-facing strings do not expose private/public build terminology.

Claude must update:

- `docs/recommendations/README.md`;
- `docs/recommendations/CURRENT_STATE.md`;
- `docs/KMK_MARKDOWN_ENCYCLOPEDIA.md`;
- any active versioning/handoff documentation used by this repo.

Do not edit archived plans unless correcting a clearly harmful reference.

## Expected Result

After this fix:

- reassessing 100 sources should not trap the user at `0 sources`;
- stale/outdated rows should remain actionable until reassessed;
- the app should support repeated batches across 300+ sources;
- continuation should be obvious and safe;
- For You compatibility remains separate and scoped;
- app UI should avoid development-channel wording.


