# KMK-Recs v0.7.6 Source Evaluation Continuation And Recommendation Quality Plan

Date: 2026-06-22

Status: active implementation plan. This supersedes the same plan previously named `KMK_RECS_V0_6_22_SOURCE_EVALUATION_CONTINUATION_AND_RECOMMENDATION_QUALITY_PLAN.md`.

## Versioning Correction

The user clarified that this Source Evaluation Continuation and Recommendation Quality work should be implemented as:

```text
KMK-Recs v0.7.6
```

The next implementation after this should be:

```text
KMK-Recs v0.7.7
```

Do not implement this as `v0.6.22`. The old v0.6.22 plan remains useful as source material, but all implementation reports, release notes, APK names, and versioning documentation for this pass must use `v0.7.6`.

## Purpose

Improve Source Evaluation in three focused, implementable ways:

1. **Continuation batches**: Source Evaluation should not keep restarting at the first 10/25/50/100 eligible candidates. It should support continuing through later eligible candidates over multiple runs.
2. **Past-results display filtering**: installed extensions/sources should not crowd the main past-evaluations list by default when the screen is mainly being used to decide which non-installed sources are worth trying.
3. **Second-stage recommendation-quality evaluation**: only sources already judged `STRONG_FIT` or `WORTH_TRYING` should receive an additional bounded For You-like probe that distinguishes a good catalogue/source from a good recommendation source.

This plan intentionally does **not** include automatic chapter/image-quality comparison. That topic is now planned separately as `KMK-Recs v0.7.7`.

## Required Reading Before Coding

Claude must read:

```text
docs/recommendations/README.md
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/DOCUMENTATION_RULES.md
docs/recommendations/KMK_RECS_V0_6_22_SOURCE_EVALUATION_CONTINUATION_AND_RECOMMENDATION_QUALITY_PLAN.md
docs/recommendations/KMK_RECS_V0_7_6_SOURCE_EVALUATION_CONTINUATION_AND_RECOMMENDATION_QUALITY_PLAN.md
docs/recommendations/KMK_RECS_V0_7_4_SOURCE_EVALUATION_UPDATE_REASSESSMENT_AND_RECOMMENDATION_FIT_IMPLEMENTATION.md
docs/recommendations/INSTALLED_SOURCE_FIT_FEASIBILITY_RESEARCH.md
RECOMMENDATION_VERSIONING.md
```

Claude must inspect current code before editing:

```text
app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt
app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt
app/src/main/java/exh/recs/evaluation/SourceEvaluationRunner.kt
app/src/main/java/exh/recs/evaluation/SourceEvaluationJob.kt
app/src/main/java/exh/recs/evaluation/SourceEvaluationJobState.kt
app/src/main/java/exh/recs/evaluation/SourceEvaluationCandidateFilter.kt
app/src/main/java/exh/recs/evaluation/GetSourceEvaluationCandidates.kt
app/src/main/java/exh/recs/evaluation/SourceEvaluationResultList.kt
app/src/main/java/exh/recs/evaluation/SourceRecommendationFitEligibility.kt
app/src/main/java/exh/recs/evaluation/SourceRecommendationFitScorer.kt
app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt
app/src/main/java/exh/recs/RecommendationQueryPlanner.kt
app/src/main/java/exh/recs/sources/GenreFilterMapper.kt
app/src/main/java/exh/recs/PersonalRecommendationScorer.kt
app/src/main/java/exh/recs/RecommendationCandidateEnricher.kt
data/src/main/sqldelight/tachiyomi/data/source_evaluation.sq
domain/src/main/java/tachiyomi/domain/taste/model/SourceEvaluation.kt
domain/src/main/java/tachiyomi/domain/taste/repository/SourceEvaluationRepository.kt
```

## Current Verified Facts

- `SourceEvaluationRunner.start(candidates, options)` currently evaluates `candidates.take(options.batchSize)`.
- There is no persisted continuation cursor or start offset.
- Reassessment and repeated evaluation can therefore get stuck near the first 10/25/50/100 candidates depending on filters.
- Past evaluations are stored and displayed together; installed sources can take visual space from non-installed sources the user still needs to consider.
- `SourceRecommendationFitEligibility` and `SourceRecommendationFitScorer` already exist and are tested, but actual bounded recommendation-fit probe execution is not wired into `SourceEvaluationRunner`.
- General source evaluation answers "does this source contain content aligned with my taste?"
- Recommendation-quality probing should answer "does this source produce useful For You-like recommendations?"

## Non-Goals

Do not include:

- chapter/image-quality scoring,
- downloading sample pages,
- evaluating every manga in a source,
- crawling full sites,
- changing normal global search,
- changing For You's normal recommendation behavior,
- auto-reordering source priority,
- expanding batch sizes to 500/1000,
- new extension repositories,
- exporting/importing recommendation JSON,
- unrelated Loved Manga changes.

## Part 1: Continuation Batches

### Required Behavior

Add an explicit continuation workflow:

- show how many eligible candidates remain;
- after a batch completes, allow `Continue next batch`;
- continuation should start after the last successfully considered candidate for the same evaluation mode/filter fingerprint;
- if filter options change, reset or invalidate the cursor;
- if candidate ordering changes because repos/extensions update, handle gracefully and avoid crashes;
- skip candidates already evaluated when that option is enabled;
- do not silently reprocess the first page of candidates unless the user explicitly starts over or changes options.

### Recommended Helper

Create a pure cursor helper:

```text
app/src/main/java/exh/recs/evaluation/SourceEvaluationContinuationPolicy.kt
app/src/test/java/exh/recs/evaluation/SourceEvaluationContinuationPolicyTest.kt
```

Suggested model:

```kotlin
data class SourceEvaluationCursor(
    val modeKey: String,
    val filterFingerprint: String,
    val lastCandidateKey: String?,
    val completedCandidateKeys: Set<String>,
    val updatedAt: Long,
)
```

Candidate key should use stable extension identity:

```text
signatureHash|pkgName
```

### Storage

Preferred first pass:

- store cursor in `SourcePreferences` as compact serialized string or JSON;
- avoid a new SQL table unless current patterns strongly favor it;
- keep parsing in a helper rather than scattering string parsing through screen model code.

Possible preference keys:

```text
sourceEvaluationContinuationCursor
sourceEvaluationContinuationUpdatedAt
```

### Filter Fingerprint

Include:

- recommendation languages,
- include explicit setting,
- skip already evaluated,
- re-evaluate stale,
- only updated evaluated,
- block explicit preference,
- disliked/unsafe filters if practical,
- evaluation mode key.

Do not include batch size. Changing from 10 to 25 should continue the same queue with a larger slice.

### UI

In `SourceEvaluationScreen`:

- show remaining candidate count;
- add `Continue next batch` when a valid cursor exists;
- optionally add `Start over`, guarded by confirmation;
- keep wording compact.

### Job Integration

Recommended flow:

1. Build full filtered candidate list.
2. Use `SourceEvaluationContinuationPolicy.sliceForRun(...)`.
3. Pass only that slice to the job.
4. On job completion, update cursor with completed candidate keys.
5. On cancellation, preserve only reliable completed keys. If reliable keys are unavailable, keep the previous cursor and let the user retry safely.

If `SourceEvaluationQueueState` does not expose completed candidate keys, add lightweight key tracking.

## Part 2: Hide Installed Sources From Past Evaluations By Default

### Required Behavior

- Past evaluations should hide installed extensions/sources by default.
- Installed evaluations remain in the database.
- User can toggle installed rows back on.
- Strong/Worth Trying non-installed rows should be easier to see.
- Filtering is display-only and must not delete rows.

### Detection

Use installed extension/source identity from `ExtensionManager.installedExtensionsFlow` or `SourceManager.getVisibleCatalogueSources()`.

Prefer extension identity when possible:

```text
signatureHash|pkgName
```

Also compare `sourceId` where available.

### UI

Add compact filter near the past-results sort control:

```text
Show installed
```

Default:

```text
false
```

Show summary such as:

```text
Hidden installed: N
```

when applicable.

### Helper And Tests

Create if practical:

```text
SourceEvaluationDisplayFilter.kt
SourceEvaluationDisplayFilterTest.kt
```

Test installed hidden by default, `showInstalled=true`, uninstalled rows remain visible, null/blank IDs do not crash.

## Part 3: Second-Stage Recommendation-Quality Evaluation

### Required Behavior

Run a bounded recommendation-quality probe only for sources already judged:

```text
STRONG_FIT
WORTH_TRYING
```

and passing `SourceRecommendationFitEligibility`.

Display a separate recommendation-quality label. Do not replace general source verdict.

Example:

```text
Source fit: Strong Fit
Recommendations: Good
```

### Probe Limits

Run only while the extension is already temporarily installed during Source Evaluation.

For each eligible source:

- max 2 query plans,
- page 1 only,
- low raw result cap,
- no chapter list fetch,
- no page list fetch,
- no image fetch,
- per-source timeout,
- errors recorded as probe errors, not batch failures.

### Reuse For You Logic

Reuse bounded pieces:

- `RecommendationQueryPlanner`
- `GenreFilterMapper`
- `PersonalRecommendationScorer`
- `RecommendationCandidateEnricher` only if safely bounded

Do not instantiate or copy the whole `BrowsePersonalRecommendationsScreenModel`.

Create a small probe helper:

```text
app/src/main/java/exh/recs/evaluation/SourceRecommendationFitProbe.kt
app/src/test/java/exh/recs/evaluation/SourceRecommendationFitProbeTest.kt
```

Suggested output:

```kotlin
data class SourceRecommendationFitProbeOutcome(
    val queryCount: Int,
    val querySuccessCount: Int,
    val rawResultCount: Int,
    val visibleCandidateCount: Int,
    val filteredOutCount: Int,
    val blockedTagCandidateCount: Int,
    val matchedGroupCount: Int,
    val topPicksContribution: Int,
    val noMatchesCount: Int,
    val errorCount: Int,
    val avgCandidateScore: Double,
    val reasons: List<String>,
)
```

Map this to `SourceRecommendationFitScorer.Outcome`.

### Persistence

Do not silently overload existing general evaluation score fields.

Preferred option:

```text
source_recommendation_fit
```

Suggested columns:

```text
fit_key
evaluation_key
source_id
extension_pkg_name
signature_hash
extension_name
source_name
lang
evaluated_at
query_count
query_success_count
raw_result_count
visible_candidate_count
filtered_out_count
blocked_tag_candidate_count
matched_group_count
top_picks_contribution
no_matches_count
error_count
avg_candidate_score
recommendation_quality_score
verdict
reasons_json
error_message
```

If adding a table is too large, nullable columns on `source_evaluation` may be used, but Claude must document the tradeoff. Separate storage is preferred because source fit and recommendation quality are different concepts.

### UI

In `EvaluationResultRow`, show compact recommendation-quality label when data exists:

```text
Recommendations: Great
Recommendations: Good
Recommendations: Mixed
Recommendations: Weak
Recommendations: No matches
Recommendations: Error
Recommendations: Too little evidence
```

Optionally add sort mode:

```text
Recommendation quality
```

Only if simple and low-risk.

## Part 4: Exception Handling

Handle as safe UI/probe state:

- source probe timeout,
- recommendation-fit probe timeout,
- malformed/empty manga data,
- source filter list throwing,
- network loss mid-probe,
- source manager/extension manager failures,
- DB table missing/migration failure fallback,
- invalid cursor,
- app restart during/background evaluation,
- cancellation while running,
- installed-source detection failure.

Expected behavior:

- no app crash;
- current extension/source records an error where possible;
- batch continues to next candidate where safe;
- cursor remains recoverable or resets with a clear message.

## Tests

Required tests:

```text
SourceEvaluationContinuationPolicyTest
SourceEvaluationDisplayFilterTest
SourceRecommendationFitEligibilityTest
SourceRecommendationFitScorerTest
SourceRecommendationFitProbeTest
SourceEvaluationCandidateFilterTest
SourceEvaluationResultListTest
```

If a new DB table is added:

```text
SourceRecommendationFitRepositoryImplTest
```

Run:

```text
./gradlew :app:testDebugUnitTest --tests "*SourceEvaluation*" --tests "*SourceRecommendationFit*"
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Document any skipped tests and why.

## Manual QA

1. Start evaluation with batch size 10.
2. Confirm completion.
3. Tap `Continue next batch`.
4. Confirm the next batch is not the same first 10.
5. Restart app and confirm continuation state is retained or safely reset.
6. Install a previously evaluated source and confirm it is hidden from past results by default.
7. Toggle `Show installed` and confirm it appears.
8. Evaluate a Strong/Worth Trying source and confirm recommendation-quality label appears.
9. Confirm Weak/Error sources do not run recommendation-quality probe.
10. Disable internet during a probe and confirm safe error behavior.

## Documentation After Implementation

Claude must create:

```text
docs/recommendations/KMK_RECS_V0_7_6_SOURCE_EVALUATION_CONTINUATION_AND_RECOMMENDATION_QUALITY_IMPLEMENTATION.md
```

Claude must update:

```text
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/README.md
RECOMMENDATION_VERSIONING.md
app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt
```

Implementation report must include:

- files changed,
- migration number if any,
- cursor behavior,
- installed-result display filter behavior,
- recommendation-quality probe limits,
- recommendation-quality labels,
- exception handling paths,
- tests added/run,
- APK path if built,
- anything deferred.

## Explicitly Deferred After This Plan

Keep these out of v0.7.6:

- automatic chapter/image-quality comparison across sources,
- best chapter version detection,
- sample image dimension/hash comparison,
- chapter-count-based best-version scoring,
- source-specific website recommendation scraping,
- broad source-quality learning from long-term For You history if not needed for the bounded probe.

Those belong to the later best-version/image-quality work, currently planned as `KMK-Recs v0.7.7`.

