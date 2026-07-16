# KMK-Recs v0.7.42-fix2 Source Evidence Display, Sorting, and State Follow-Up Plan

Date: 2026-07-12

Status: implementation plan. Do not implement until the user explicitly asks Claude to implement it.

Version family: `KMK-Recs v0.7.42` corrective follow-up. This is `v0.7.42-fix2`, not a new feature phase and not `v0.7.43`.

## 1. Release Identity

Use the canonical rule in `RECOMMENDATION_VERSIONING.md`.

- `KmkRecsReleaseNotes.VERSION_CODE`: `744`.
- `KmkRecsReleaseNotes.VERSION_NAME`: `KMK-Recs v0.7.42-fix2`.
- APK handoff name: `Komikku-v1.13.6-kmk.7.42.2-debug.apk`.
- Do not change Android `versionCode` or `versionName` merely for this local KMK feature release marker.
- Preserve historical version records; do not renumber older releases.

## 2. Why This Follow-Up Exists

v0.7.42 separated two distinct measurements:

1. **Catalogue fit**: how the source's Popular/Latest samples match the user's taste.
2. **For You search compatibility**: how useful that source is when a bounded, dedicated search probe is run.

v0.7.42-fix1 correctly unified automatic evaluation, the manual queue, diagnostics, fit staleness, and the catalogue row subtitle. A code review after that fix found the remaining presentation/action paths still carrying old assumptions:

- `SourceEvaluationResultList.SortMode.SEARCH_RELIABILITY` sorts a retired field which v0.7.42 intentionally writes as `0.0`.
- `BEST_FIT` still uses that retired field as a tie-breaker.
- Individual row labels use a separate Strong Fit/Worth Trying test rather than `SourceRecommendationFitEligibility.isProbeEligible`.
- Individual rows can display an expired or older-version `SourceRecommendationFit` as a current result.
- The action area can evaluate missing fits or re-check every current fit, but cannot target only stale fits.
- `CURRENT_STATE.md` retains a pre-v0.7.42 eligibility description without a clear historical qualifier.

The goal is not to invent a third score. The goal is to make every displayed state and user action faithfully use the already-existing two evidence layers.

## 3. Goals

1. Remove the unusable Search Reliability sort and replace it with a truthful **For You compatibility** sort.
2. Make catalogue ordering and For You compatibility ordering explicitly distinct.
3. Give every eligible source an honest compatibility state: not checked, outdated, no matches, search error, weak, mixed, good, or great.
4. Ensure queue, diagnostics, row labels, sort comparators, and actions share one policy for eligibility and staleness.
5. Add a bounded **Recheck outdated** action that touches only eligible sources with stale compatibility results.
6. Keep explicit/ecchi/error/rejected source-evaluation verdicts excluded from compatibility actions and compatibility-result labels.
7. Update documentation and versioning so the current system has one clear source of truth.

## 4. Non-Goals

- Do not merge catalogue fit and search compatibility into one score.
- Do not automatically re-probe all sources on app startup, refresh, or a score-version change.
- Do not make source evaluation query every source, page, or manga without the existing user-triggered bounds.
- Do not change source-evaluation installer behavior, extension evaluation batches, database schema, backup, sync, or the For You recommendation pipeline.
- Do not remove historical results merely because they are stale.

## 5. Required Inspection

Before editing, read and confirm current behavior in:

- `app/src/main/java/exh/recs/evaluation/SourceRecommendationFitEligibility.kt`
- `SourceRecommendationQualityQueue.kt`, `SourceRecommendationQualityDiagnostics.kt`, `SourceEvaluationResultList.kt`, `SourceEvaluationScreen.kt`, and `SourceEvaluationScreenModel.kt`
- `domain/src/main/java/tachiyomi/domain/taste/model/SourceRecommendationFit.kt`
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`
- relevant tests under `app/src/test/java/exh/recs/evaluation/`
- `docs/KMK_MARKDOWN_ENCYCLOPEDIA.md`, `RECOMMENDATION_VERSIONING.md`, `CURRENT_STATE.md`, `NEXT_WORK.md`, and both v0.7.42 implementation reports.

If any issue is already corrected, document the verification rather than duplicating work.

## 6. Shared Compatibility Display Policy

Create one Android-free helper in `exh/recs/evaluation/`, for example `SourceRecommendationFitDisplayPolicy.kt`. It must reuse `SourceRecommendationFitEligibility.isProbeEligible` and `isFitCurrent`; it must not duplicate verdict, confidence, version, or expiry logic.

Resolve `(SourceEvaluation, SourceRecommendationFit?, now)` to exactly one state:

| State | Conditions | Meaning |
|---|---|---|
| `INELIGIBLE` | probe eligibility is false | Do not offer or imply a compatibility probe. |
| `NOT_CHECKED` | eligible and fit absent | It can be checked but has no result. |
| `OUTDATED` | eligible and fit exists but is stale | Historical result exists; recheck before trusting it. |
| `GREAT`, `GOOD`, `MIXED`, `WEAK` | eligible + current fit | Current measured outcome. |
| `NO_MATCHES` | eligible + current `NO_MATCHES` or `TOO_LITTLE_EVIDENCE` | Probe ran but yielded no useful candidate evidence. |
| `ERROR` | eligible + current `ERROR` | Probe ran but failed. |

Requirements:

- `OUTDATED` must never display as Not checked, Weak, No matches, Error, or a current positive result.
- Ineligible rows must not receive compatibility status labels.
- The helper may expose the current fit or `recommendationQualityScore` for sorting, but must not access DB, Compose, Android, or network APIs.
- Queue, diagnostics, row labels, sorting, and actions must agree with this policy.

## 7. Queue and Actions

### Queue buckets

Extend `SourceRecommendationQualityQueue.QueueResult` to distinguish:

- `missingPromising`: eligible + no fit.
- `outdatedPromising`: eligible + stale fit.
- `checkedPromising`: eligible + current fit.
- `ineligible`: not eligible.

Buckets must be mutually exclusive and collectively cover the input. Add named derived properties when useful, such as `needsCheckPromising`; do not collapse missing and outdated where the UI needs to explain the difference.

### Screen-model actions

In `SourceEvaluationScreenModel`:

- Keep the normal check action for **missing only**.
- Add `recheckOutdatedRecommendationQuality()` or an equivalent method, targeting only `outdatedPromising`.
- Keep explicit **Recheck all** as the only action targeting all eligible rows: missing + outdated + current.
- Prevent all actions while `recQualityRunning` is true.
- Empty targets must safely do nothing without starting a coroutine or reporting false success.
- Use one consistent `now` for a queue/action calculation.

### UI actions

In `SourceEvaluationScreen`:

- Show separate missing and outdated counts when present.
- Show **Check search compatibility** only for missing rows.
- Show **Recheck outdated** only for stale rows.
- Keep **Recheck all** only if one or more eligible rows have current fits.
- Add KMR string resources for every new visible label and count. Keep the section compact.

## 8. Individual Row Labels

Update `EvaluationResultRow` to use the shared display policy, replacing its local Strong Fit/Worth Trying condition.

- Eligible/no fit: `For You search: Not checked`.
- Eligible/stale fit: `For You search: Outdated - recheck`.
- Eligible/current fit: existing localized Great/Good/Mixed/Weak/No matches/Error outcome label.
- Ineligible: no compatibility label.
- Keep current error-kind diagnostics only for a **current** error fit; stale errors display only as outdated.
- Preserve the catalogue subtitle independently from the compatibility label.

## 9. Sorting

### Retire Search Reliability

Replace `SortMode.SEARCH_RELIABILITY` with `SortMode.FOR_YOU_COMPATIBILITY`.

- Remove all `searchReliabilityScore` reads from `SourceEvaluationResultList` comparators.
- Remove `source_evaluation_sort_search_reliability` only after confirming no references remain.
- Add a localized `For You compatibility` sort label.

### Add fit context

Change `SourceEvaluationResultList.sort(...)` to accept the fits map and a consistent time, for example:

```kotlin
fun sort(
    evaluations: List<SourceEvaluation>,
    fitsByEvalKey: Map<String, SourceRecommendationFit>,
    mode: SortMode,
    now: Long = System.currentTimeMillis(),
): List<SourceEvaluation>
```

Update the screen `remember` keys/call site so changed fits trigger a re-sort. Capture `now` once per composition or action; do not make one comparator time-dependent during a sort.

### For You compatibility ordering

Use the shared policy and order buckets:

1. Current `GREAT`, `GOOD`, `MIXED`, then `recommendationQualityScore` descending.
2. Current `WEAK`.
3. Current `NO_MATCHES`.
4. Current `ERROR`.
5. `OUTDATED`.
6. `NOT_CHECKED`.
7. `INELIGIBLE`.

Within equal buckets: current fit score where available, then newest evaluation, case-insensitive source name, then extension name. Stale and unprobed must never be treated as weak.

### Best Fit ordering

Keep Best Fit catalogue-first:

1. Existing catalogue verdict rank.
2. Existing catalogue recommendation-fit score.
3. Existing catalogue quality score.
4. Current For You compatibility as a tie-breaker only; current better outcomes outrank current worse outcomes, while outdated/not-checked/ineligible are neutral ties.
5. Newest evaluation, source name, extension name.

Do not use `searchReliabilityScore` anywhere in this comparator.

## 10. Tests

All tests must be pure JVM tests: no network, Android, Compose, extension install, or database setup.

Create `SourceRecommendationFitDisplayPolicyTest` covering:

- Strong/Worth Trying and low/unknown-confidence fail-open candidates with no fit -> `NOT_CHECKED`.
- Explicit/Ecchi/Error/Rejected remain `INELIGIBLE` regardless of confidence or a persisted fit.
- Missing, stale-version, exact-expiry, and future-expiry behavior.
- Every current fit verdict maps correctly.
- A stale error maps to `OUTDATED`, not `ERROR`.

Update queue/diagnostic tests to prove the four buckets are complete and exclusive, stale results count as not checked, and outdated-only target logic excludes current/missing rows.

Update `SourceEvaluationResultListTest` to prove:

- For You compatibility ordering correctly orders current positive, weak, no-match, error, outdated, missing, and ineligible states.
- Current compatibility outranks stale compatibility.
- Stale/missing never equal weak.
- Best Fit uses compatibility only after equal catalogue evidence.
- Rows differing only by retired `searchReliabilityScore` use defined deterministic ties instead.

## 11. Documentation and Release Notes

Create:

`docs/recommendations/KMK_RECS_V0_7_42_SOURCE_EVIDENCE_DISPLAY_AND_SORT_FOLLOWUP_IMPLEMENTATION.md`

It must document confirmed findings, exact code/resource/test/doc changes, test results, release identity, APK path, and remaining deferred work.

Update:

- `RECOMMENDATION_VERSIONING.md`: add `v0.7.42-fix2`, code `744`, APK `.7.42.2`.
- `CURRENT_STATE.md`: add fix2 authoritative behavior; amend or label the pre-v0.7.42 eligibility paragraph as historical; remove any claim that Search Reliability is an active meaningful sort.
- `NEXT_WORK.md`: mark this inconsistency resolved after verification; do not add unrelated feature work.
- `docs/recommendations/README.md`: move this plan to implemented after the report is created.
- `KmkRecsReleaseNotes.kt`: user-facing notes only: truthful compatibility sorting/statuses and targeted outdated rechecks.

## 12. Verification

Use the repo-local JDK 17 documented in `docs/KMK_MARKDOWN_ENCYCLOPEDIA.md`:

```powershell
$env:JAVA_HOME='C:\Users\USER\Downloads\Komikku\komikku-source\.tools\jdk17\jdk-17.0.19+10'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
```

Run all of:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*.SourceRecommendationFitDisplayPolicyTest"
.\gradlew.bat :app:testDebugUnitTest --tests "*.SourceRecommendationQualityQueueTest"
.\gradlew.bat :app:testDebugUnitTest --tests "*.SourceRecommendationQualityDiagnosticsTest"
.\gradlew.bat :app:testDebugUnitTest --tests "*.SourceEvaluationResultListTest"
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat spotlessCheck
.\gradlew.bat assembleDebug
```

Do not claim completion if any required check is skipped. Report blockers plainly.

## 13. Acceptance Criteria

1. No Source Evaluation UI sort/comparator reads `searchReliabilityScore`.
2. The menu exposes `For You compatibility`, not `Search reliability`.
3. Queue, diagnostics, rows, sort, and actions use one eligibility/staleness interpretation.
4. Stale fits visibly say outdated and are eligible for targeted rechecking.
5. Missing fits visibly say not checked and are not treated as weak.
6. Ineligible sources are excluded from all compatibility actions and labels.
7. Best Fit remains catalogue-first; compatibility is a true tie-breaker only.
8. All new visible strings use KMR resources.
9. Release/docs/version identity is `v0.7.42-fix2` / code `744` / APK `.7.42.2`.
10. Focused tests, full unit suite, formatting check, and debug build pass.

## 14. Claude Guardrails

- Follow existing Komikku structure and formatting; run `spotlessCheck` before the final build.
- No schema migration is expected. Add one only if inspected code proves it necessary, and document why.
- Do not refactor unrelated source evaluation, recommendation, installer, or cross-extension code.
- Do not use raw English UI strings.
- Preserve `CancellationException` propagation and existing safe error boundaries.
- Build and hand off the final APK only after every plan item and verification step is complete.
