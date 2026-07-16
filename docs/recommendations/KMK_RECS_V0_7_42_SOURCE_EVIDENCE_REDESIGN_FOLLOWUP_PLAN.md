# KMK-Recs v0.7.42 Source Evidence Redesign Follow-Up Plan

Date: 2026-07-12

Status: implementation plan. Do not implement until explicitly approved by the user.

Version family: KMK-Recs `v0.7.42` corrective follow-up. This is not a new feature phase; it fixes the v0.7.42 Source Evidence Redesign so every Source Evaluation UI path uses the same source-evidence model.

Recommended release labeling for the implementation:

- `KmkRecsReleaseNotes.VERSION_CODE`: bump from `742` to `743` so the local KMK What's New dialog can appear for users who already saw v0.7.42.
- `KmkRecsReleaseNotes.VERSION_NAME`: use the canonical corrective label `KMK-Recs v0.7.42-fix1`.
- APK handoff name: `Komikku-v1.13.6-kmk.7.42.1-debug.apk`.
- `RECOMMENDATION_VERSIONING.md`: add a `KMK-Recs v0.7.42 follow-up / v0.7.42-fix1` entry. Apply the canonical versioning rule in that file: a corrective follow-up keeps its parent feature family, gets the next monotonic KMK release-note code, and uses a matching `.1` APK suffix. Do not document this as v0.7.43 unless the user explicitly decides to start a new feature version.

## 1. Background

v0.7.42 intentionally redesigned Source Evaluation:

- `SourceEvaluationScorer` now scores catalogue fit from Popular/Latest samples only.
- The scorer's old internal search probe was removed.
- `PersonalRecommendationScorer` is reused for per-catalogue-item taste matching.
- `catalogueMetadataConfidence` was added so sparse Popular/Latest metadata is not misread as poor taste fit.
- `SourceRecommendationFitEligibility` now admits sources for For You search compatibility probing when:
  - the catalogue verdict is `STRONG_FIT` or `WORTH_TRYING` with enough evidence, or
  - catalogue metadata confidence is `LOW`/`UNKNOWN` with at least minimal evidence.
- `source_recommendation_fit` gained `evaluation_version` and `expires_at` for staleness parity.
- UI strings were relabeled from "Recommendations" to "For You search" where the value represents search compatibility.

Codex review after Claude's v0.7.42 implementation found that the core scorer/schema/test work is build-healthy, but several downstream UI and queue consumers still use pre-v0.7.42 assumptions.

## 2. Goals

1. Make manual Source Evaluation "For You search compatibility" checks use the same eligibility policy as automatic v0.7.42 Source Evaluation.
2. Treat stale `SourceRecommendationFit` rows as missing/unchecked so old compatibility results do not suppress re-checking.
3. Make Source Evaluation diagnostics count the same eligible source set as the queue/action buttons.
4. Remove or replace misleading `search 0%` catalogue-row subtitle text now that `SourceEvaluation.searchReliabilityScore` is intentionally always `0.0`.
5. Reconcile stale v0.7.41/v0.7.42 documentation contradictions and add a proper version record for this follow-up.

## 3. Non-Goals

- Do not redesign Source Evaluation again.
- Do not add new database tables.
- Do not add migrations unless a real schema change becomes unavoidable; expected implementation should not need one.
- Do not change the For You recommendation feed retrieval loop.
- Do not change `SourceEvaluationScorer` thresholds unless required by tests proving a current bug.
- Do not implement broader filters, refresh-effort modes, source-scope controls, chapter/update checks, or local outcome learning in this pass.

## 4. Findings To Fix

### F1. Manual queue uses old "promising" rule

Current code:

- `app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityQueue.kt`
- `SourceRecommendationQualityQueue.compute(...)`
- `PROMISING_VERDICTS = { STRONG_FIT, WORTH_TRYING }`

Problem:

v0.7.42 introduced `SourceRecommendationFitEligibility.check(evaluation)`. A source with `WEAK` or `NEUTRAL` catalogue verdict but `LOW`/`UNKNOWN` `catalogueMetadataConfidence` may be eligible for a For You search compatibility probe. The automatic evaluation path uses that new policy, but the manual queue still ignores it.

Effect:

The UI can say there are no promising sources to check even though v0.7.42 considers some rows eligible because catalogue evidence is inconclusive.

Required fix:

- Replace the hardcoded `PROMISING_VERDICTS` gate with `SourceRecommendationFitEligibility.check(evaluation)`.
- A row is probe-eligible when the result is `EligibilityResult.ELIGIBLE`.
- A row with `INSUFFICIENT_EVIDENCE` or `INELIGIBLE_VERDICT` should remain outside `missingPromising`/`checkedPromising`.
- Consider renaming internal queue buckets from "promising" to "eligible" if this reduces confusion, but do not churn user-facing strings unless useful.

### F2. Stale fit rows are not treated as missing

Current code:

- `SourceRecommendationQualityQueue.compute(...)`
- `fitsByEvalKey[it.evaluationKey] == null`

Problem:

v0.7.42 added:

- `SourceRecommendationFit.evaluationVersion`
- `SourceRecommendationFit.expiresAt`
- `SourceRecommendationFit.CURRENT_VERSION = 1`
- migration 60 defaults historical fit rows to `evaluation_version = 0`

But the queue only checks whether a row exists. It does not check whether the existing row is stale.

Effect:

Old search compatibility results can be treated as checked even when they were computed before v0.7.42's source-evidence redesign.

Required fix:

- Add a pure stale-check helper for `SourceRecommendationFit`, preferably near the model or the queue:
  - stale if `fit.evaluationVersion < SourceRecommendationFit.CURRENT_VERSION`
  - stale if `fit.expiresAt != null && fit.expiresAt <= now`
- Update `SourceRecommendationQualityQueue.compute(...)` to accept `now: Long = System.currentTimeMillis()` or an injected `now` parameter for tests.
- Treat missing or stale fit as `missingPromising` / missing eligible.
- Treat only non-stale fit as checked.

### F3. Diagnostics use old promising-source rule

Current code:

- `app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityDiagnostics.kt`
- `promising = evaluations.filter { verdict == STRONG_FIT || verdict == WORTH_TRYING }`

Problem:

Diagnostics still count only hardcoded Strong Fit/Worth Trying rows, not the v0.7.42 eligibility model.

Effect:

The diagnostics line can disagree with the action queue and with the automatic evaluation behavior.

Required fix:

- Make diagnostics use the same eligibility/staleness helper as `SourceRecommendationQualityQueue`.
- Avoid duplicating logic.
- Prefer a shared pure helper such as:
  - `SourceRecommendationQualityEligibility.isEligible(evaluation)`
  - `SourceRecommendationQualityEligibility.isFitCurrent(fit, now)`
  - or keep it inside `SourceRecommendationQualityQueue` if that file becomes the single source of truth.
- Update diagnostics tests to cover:
  - low-confidence weak row counts as eligible/missing;
  - stale fit counts as not checked;
  - explicit/error/rejected rows remain excluded.

### F4. Source Evaluation row subtitle still shows `search 0%`

Current code:

- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt`
- row subtitle builds:
  - `fit = evaluation.recommendationFitScore`
  - `search = evaluation.searchReliabilityScore`

Problem:

As of v0.7.42, `SourceEvaluation.searchReliabilityScore` is intentionally always `0.0` because Source Evaluation no longer measures search compatibility directly. Search compatibility lives in `SourceRecommendationFit` / "For You search" labels.

Effect:

Rows can show `search 0%` even if their separate For You search compatibility is Good/Great. This is confusing and contradicts the redesign.

Required fix:

- Remove `search %` from the Source Evaluation row subtitle.
- Replace it with one of the following:
  - `fit % - metadata: High/Moderate/Low/Unknown - evidence - last evaluated ...`
  - or `catalogue fit % - confidence ... - evidence ...`
- Use `KMR.strings` for any new user-facing strings.
- Do not hardcode English labels in composables.
- The `For You search: Good/Great/...` label should remain separate and should continue to come from `SourceRecommendationFit`.

### F5. v0.7.41 documentation contradicts v0.7.42 shipped state

Current stale docs:

- `docs/recommendations/KMK_RECS_V0_7_41_DISCOVERY_POLICY_CORRECTIONS_IMPLEMENTATION.md`

Problem:

The v0.7.41 implementation report still says v0.7.42 was "NOT implemented" and "planning-only". That was true at the time of v0.7.41, but is now stale after the approved v0.7.42 implementation.

Required fix:

- Preserve historical context without creating contradiction.
- Suggested wording:
  - "At the time of v0.7.41, the v0.7.42 Source Evidence Redesign was not started. It was later implemented separately in `KMK_RECS_V0_7_42_SOURCE_EVIDENCE_REDESIGN_IMPLEMENTATION.md`."
- Update all stale occurrences in that file.
- Do not rewrite the entire v0.7.41 report.

## 5. Implementation Steps For Claude

### Step 1. Audit the current files before editing

Claude must first inspect:

- `app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityQueue.kt`
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityDiagnostics.kt`
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationFitEligibility.kt`
- `domain/src/main/java/tachiyomi/domain/taste/model/SourceRecommendationFit.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt`
- relevant tests under `app/src/test/java/exh/recs/evaluation/`
- `docs/recommendations/KMK_RECS_V0_7_42_SOURCE_EVIDENCE_REDESIGN_IMPLEMENTATION.md`
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `RECOMMENDATION_VERSIONING.md`

Claude should confirm whether the code still matches the findings before changing it. If a finding has already been fixed, document that in the implementation report and do not duplicate the change.

### Step 2. Create one shared eligibility/staleness contract

Implement one pure helper contract that both queue and diagnostics use.

Acceptable designs:

1. Extend `SourceRecommendationFitEligibility` with fit-current helpers, or
2. Add `SourceRecommendationQualityEligibility` as a small pure object, or
3. Add pure private/internal helpers inside `SourceRecommendationQualityQueue` and expose the computed result for diagnostics.

Preferred contract:

```kotlin
fun isProbeEligible(evaluation: SourceEvaluation): Boolean =
    SourceRecommendationFitEligibility.check(evaluation) ==
        SourceRecommendationFitEligibility.EligibilityResult.ELIGIBLE

fun isFitCurrent(fit: SourceRecommendationFit?, now: Long): Boolean =
    fit != null &&
        fit.evaluationVersion >= SourceRecommendationFit.CURRENT_VERSION &&
        (fit.expiresAt == null || fit.expiresAt > now)
```

Then queue logic becomes:

```kotlin
val eligible = evaluations.filter { isProbeEligible(it) }
val ineligible = evaluations.filterNot { isProbeEligible(it) }
val missing = eligible.filter { !isFitCurrent(fitsByEvalKey[it.evaluationKey], now) }
val checked = eligible.filter { isFitCurrent(fitsByEvalKey[it.evaluationKey], now) }
```

Avoid multiple slightly different definitions across files.

### Step 3. Update `SourceRecommendationQualityQueue`

Required behavior:

- Eligible source with no fit -> missing.
- Eligible source with stale fit -> missing.
- Eligible source with current fit -> checked.
- `WEAK`/`NEUTRAL` with `LOW`/`UNKNOWN` metadata confidence and enough sample count -> eligible.
- `EXPLICIT_HEAVY`, `ECCHI_HEAVY`, `ERROR`, `REJECTED` -> ineligible even with low metadata confidence.
- Insufficient-evidence rows -> ineligible or a separate non-actionable bucket; do not queue them.

Keep return shape compatible unless tests or UI make a rename easy. If keeping `missingPromising`/`checkedPromising`, add KDoc explaining that "promising" now means "eligible for For You search compatibility under the v0.7.42 policy", not only Strong/Worth Trying.

### Step 4. Update `SourceRecommendationQualityDiagnostics`

Required behavior:

- Use the same eligible/current-fit logic as the queue.
- `notCheckedCount` should count eligible rows with no current fit.
- `checkedCount` should count eligible rows with current fit.
- Stale fits should not contribute to Good/Weak/Error/No results counts.
- Ineligible rows should not be counted in the compatibility diagnostics.

### Step 5. Update Source Evaluation row subtitle

Required behavior:

- Remove `searchReliabilityScore` from the row subtitle.
- Do not show `search 0%` from `SourceEvaluation`.
- Show catalogue-specific evidence instead.

Suggested display:

```text
{extension} - {lang} - catalogue fit {fit}% - metadata {confidence} - {evidence} - Last evaluated {relative time}
```

If space is tight, use:

```text
{extension} - {lang} - fit {fit}% - {confidence} metadata - {evidence} - Last evaluated ...
```

Implementation details:

- Add or reuse KMR strings for metadata confidence labels.
- Keep error rows using the existing error subtitle path.
- The separate `For You search: ...` row must remain and should not be merged into the catalogue subtitle.

### Step 6. Update tests

Required test updates/additions:

1. `SourceRecommendationQualityQueueTest`
   - `WEAK` + `LOW` confidence + sample count >= fail-open minimum -> missing when no fit.
   - `NEUTRAL` + `UNKNOWN` confidence + sample count >= fail-open minimum -> missing when no fit.
   - `WEAK` + `MODERATE` confidence -> ineligible.
   - `EXPLICIT_HEAVY` + `UNKNOWN` confidence -> ineligible.
   - stale fit with `evaluationVersion = SourceRecommendationFit.CURRENT_VERSION - 1` -> missing.
   - expired fit with `expiresAt <= now` -> missing.
   - current fit -> checked.

2. `SourceRecommendationQualityDiagnosticsTest`
   - diagnostics counts match queue eligibility for low-confidence eligible rows.
   - stale fits are counted as not checked, not checked good/weak/error.
   - ineligible rows are excluded.

3. UI subtitle pure test if one exists.
   - If subtitle building is already embedded in Compose, extract a small pure helper only if it follows project style and keeps the composable cleaner.
   - Test that the subtitle no longer includes `search 0%`.
   - Test that confidence/evidence appears instead.

Do not add fragile screenshot tests for this pass.

### Step 7. Update docs and versioning

Update:

- `docs/recommendations/CURRENT_STATE.md`
  - Add a small v0.7.42 follow-up note explaining queue/diagnostics now share `SourceRecommendationFitEligibility` and stale-fit handling.
  - Update test list.

- `docs/recommendations/NEXT_WORK.md`
  - Remove or mark resolved the known limitation:
    - "`SourceRecommendationQualityQueue.compute()` not updated."
  - If no other v0.7.42 follow-up item remains, do not leave it listed as open.

- `docs/recommendations/KMK_RECS_V0_7_42_SOURCE_EVIDENCE_REDESIGN_IMPLEMENTATION.md`
  - Add an addendum section for the follow-up fix, or create a separate implementation report after coding.

- `docs/recommendations/KMK_RECS_V0_7_41_DISCOVERY_POLICY_CORRECTIONS_IMPLEMENTATION.md`
  - Fix stale historical wording as described in F5.

- `RECOMMENDATION_VERSIONING.md`
  - Add the canonical versioning section if it is not already present; preserve previous
    release records as historical rather than renumbering them.
  - Add a `KMK-Recs v0.7.42 follow-up / v0.7.42-fix1` entry.

- `docs/recommendations/README.md`
  - Add the implementation report once Claude creates it.
  - Keep this plan listed as active until implemented, then mark implemented.

Create a new implementation report:

```text
docs/recommendations/KMK_RECS_V0_7_42_SOURCE_EVIDENCE_REDESIGN_FOLLOWUP_IMPLEMENTATION.md
```

The report must include:

- What changed.
- Exact files changed.
- Tests run and results.
- APK handoff name if built.
- Confirmation that no database migration was added.

### Step 8. Release notes

Update `KmkRecsReleaseNotes.kt`:

- `VERSION_CODE = 743`
- `VERSION_NAME = "KMK-Recs v0.7.42-fix1"`.
- Add a short top entry:
  - For You search compatibility checks now use the same v0.7.42 source-evidence eligibility as automatic Source Evaluation.
  - Stale compatibility results are now rechecked instead of silently treated as current.
  - Source Evaluation rows no longer show the misleading `search 0%` catalogue subtitle.

Keep user-facing notes only. Do not mention internal markdown/documentation cleanup in What's New.

## 6. Verification Requirements

Claude must run:

```bash
./gradlew :app:testDebugUnitTest --tests "*.SourceRecommendationQualityQueueTest"
./gradlew :app:testDebugUnitTest --tests "*.SourceRecommendationQualityDiagnosticsTest"
./gradlew :app:testDebugUnitTest
./gradlew spotlessCheck
./gradlew assembleDebug
```

If Windows/PowerShell is used, use the repo-local JDK 17 pattern already documented in the encyclopedia/current workflow:

```powershell
$env:JAVA_HOME='C:\Users\USER\Downloads\Komikku\komikku-source\.tools\jdk17\jdk-17.0.19+10'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :app:testDebugUnitTest
```

If any command cannot run, Claude must say exactly why and must not claim the build is verified.

## 7. Acceptance Criteria

This follow-up is complete only when:

1. Manual Source Evaluation compatibility queue uses `SourceRecommendationFitEligibility.check(...)`.
2. Queue and diagnostics share the same eligibility/staleness rule.
3. Stale `SourceRecommendationFit` rows are treated as unchecked/missing.
4. Source Evaluation rows no longer display `search 0%` from the catalogue evaluation record.
5. For You search compatibility labels still display separately and correctly.
6. Unit tests cover low-confidence eligible rows, stale fit rows, current fit rows, and blocked/ineligible rows.
7. v0.7.41 stale documentation is corrected without rewriting history.
8. Versioning and release notes clearly mark this as a v0.7.42 follow-up/fix.
9. Full unit tests, spotless, and debug build pass.

## 8. Claude Guardrails

- Do not implement unrelated feature work.
- Do not start broader filter/refresh-effort/source-scope/local-learning phases.
- Do not add migrations unless the code absolutely requires it and the reason is documented.
- Do not delete historical docs; update or archive only when clearly necessary.
- Do not leave v0.7.42 contradictions in `NEXT_WORK.md`, `CURRENT_STATE.md`, or the implementation reports.
- If a planned finding is already fixed when Claude inspects the code, document it as already resolved and move on.

