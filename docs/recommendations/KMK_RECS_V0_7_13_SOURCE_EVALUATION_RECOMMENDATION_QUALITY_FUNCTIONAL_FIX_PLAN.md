# KMK-Recs v0.7.13 Source Evaluation Recommendation Quality Functional Fix Plan

Date: 2026-06-27

Status: planning. Implementation is not approved until the user gives Claude this plan/prompt.

Target implementation pass: Claude Code.

Related documents:

- `docs/recommendations/KMK_RECS_V0_7_12_RECOMMENDATION_UX_AND_CROSS_EXTENSION_CONSOLIDATION_IMPLEMENTATION.md`
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/community/KMK_PHASE_6_7_RECOMMENDATION_UX_AND_CROSS_EXTENSION_CONSOLIDATION_PLAN.md`

## Purpose

KMK-Recs v0.7.12 made Source Evaluation recommendation-quality errors visible, but it did not fully solve the underlying functional issue.

Observed user behavior:

- In Source Evaluation, the "Evaluate recommendations" action for `Strong Fit` / `Worth Trying` sources still returns mostly `Error`.
- One source may return `No matches` / `No result`.
- The user needs to know whether this means:
  - the extension cannot be installed or loaded,
  - the source cannot be resolved after temporary install,
  - the source search endpoint is broken,
  - the source returns raw results but without enough metadata to score,
  - the current probe strategy is too narrow,
  - the source genuinely has weak recommendation behavior.

This pass must improve the functional recommendation-quality probe and not merely make failures prettier.

## Important Scope Limits

1. Keep this as `KMK-Recs v0.7.13`.
2. Do not jump to `v0.8.x`.
3. Do not implement the remaining broad Phase 6/7 items in this pass.
4. Do not change normal global search behavior.
5. Do not change For You recommendation ranking unless a small shared helper extraction is needed.
6. Do not auto-install permanent sources.
7. Do not auto-migrate, auto-favorite, or auto-mark manga.
8. Do not change OCR.
9. Keep all network work bounded.
10. Preserve temporary install/probe/cleanup behavior for non-installed promising sources.

## Current Technical Diagnosis

### Current rec-quality probe path

Main files:

- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt`
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationFitProbe.kt`
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationFitProbeOutcome.kt`
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationFitScorer.kt`
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityQueue.kt`
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityExtensionResolver.kt`
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityInstalledResolver.kt`
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationQualitySourceResolver.kt`

The current on-demand flow:

1. Selects promising evaluations from `SourceRecommendationQualityQueue`.
2. For installed sources, resolves the installed extension/source and probes directly.
3. For non-installed sources, resolves an available extension, temporarily installs it, resolves the source, probes, persists `SourceRecommendationFit`, and attempts cleanup.
4. `SourceRecommendationFitProbe` builds up to two `RecommendationQueryPlanner` plans from top taste tags.
5. It calls `source.getSearchManga(1, query, filters)`.
6. It converts raw `SManga` to domain manga using `toDomainManga(source.id)`.
7. It immediately scores with `PersonalRecommendationScorer.score(...)`.

### Likely root problem

For You does more than this.

In `BrowsePersonalRecommendationsScreenModel.searchSource(...)`, the For You path:

1. Calls `source.getSearchManga(...)`.
2. Keeps a `smangaByUrl` map.
3. Converts raw results through `networkToLocalManga(...)`.
4. Removes known manga when needed.
5. Uses `RecommendationCandidateEnricher.enrich(...)` to fetch `getMangaDetails(...)` for a bounded number of weak candidates.
6. Scores enriched results with `PersonalRecommendationScorer.rankCandidates(...)`.

The rec-quality probe does not do the enrichment step. Many extensions return search results with title/url only and no useful genres/tags until manga details are fetched. In that case:

- raw search succeeded,
- the source may be usable,
- but `PersonalRecommendationScorer` sees empty genre metadata,
- scores are zero,
- candidates get counted as filtered/weak/no matches.

This can make good sources look like bad recommenders.

## Required Implementation

### 1. Add a Reusable Probe Result Classification

Create a small pure helper to classify recommendation-quality failures and outcomes.

Suggested file:

```text
app/src/main/java/exh/recs/evaluation/SourceRecommendationFitFailureClassifier.kt
```

Possible categories:

```kotlin
enum class SourceRecommendationProbeFailureKind {
    NONE,
    NO_TASTE_EVIDENCE,
    AVAILABLE_EXTENSION_LIST_EMPTY,
    EXTENSION_NOT_FOUND,
    EXTENSION_MATCH_AMBIGUOUS,
    INSTALL_FAILED_OR_TIMED_OUT,
    INSTALLED_EXTENSION_DID_NOT_LOAD,
    SOURCE_NOT_FOUND,
    SOURCE_MATCH_AMBIGUOUS,
    SEARCH_UNSUPPORTED_OR_THROWING,
    SEARCH_TIMED_OUT,
    RAW_RESULTS_EMPTY,
    RAW_RESULTS_WITH_WEAK_METADATA,
    ALL_RESULTS_BLOCKED,
    ALL_RESULTS_FILTERED_OUT,
    CLEANUP_FAILED,
    UNKNOWN,
}
```

Do not over-engineer. If a full enum is too much for the existing model, a pure mapper that turns known messages/outcomes into stable display labels is acceptable. However, avoid only free-text string comparisons scattered across UI.

Use this classification to distinguish:

- infrastructure failure,
- temporary install/load failure,
- source resolution failure,
- search failure,
- search works but no raw results,
- search works but metadata is weak,
- search works but all candidates are blocked/filtered,
- genuinely weak recommendation quality.

### 2. Make `SourceRecommendationFitProbe` Closer To For You

The probe should reuse the same core behavior as For You where practical:

- Use `RecommendationQueryPlanner.buildPlans(...)`.
- Use `GenreFilterMapper.buildSearch(...)`.
- Use `PersonalRecommendationScorer.rankCandidates(...)`.
- Add bounded metadata enrichment before scoring.

Preferred approach:

1. Extract or reuse a small helper that can perform bounded candidate enrichment for probe use.
2. Do not duplicate large `BrowsePersonalRecommendationsScreenModel.searchSource(...)`.
3. Do not instantiate a full screen model from the probe.

Important current helper:

```text
app/src/main/java/exh/recs/RecommendationCandidateEnricher.kt
```

Current constructor:

```kotlin
RecommendationCandidateEnricher(
    networkToLocalManga: NetworkToLocalManga,
    coroutineDispatcher: CoroutineDispatcher,
)
```

The rec-quality probe currently does not have `NetworkToLocalManga`. It should receive it through dependency injection or the caller.

Suggested implementation:

- Add `NetworkToLocalManga` dependency to `SourceRecommendationFitProbe`, or pass a lightweight enrichment function into the probe constructor for tests.
- Convert raw results with `networkToLocalManga(...)`, not only `SManga.toDomainManga(...)`, so local manga rows and initialization state are consistent with For You.
- Use `RecommendationCandidateEnricher.enrich(...)` for a small cap.
- Suggested enrichment cap for rec-quality: 5 per plan or 8 total per source, whichever is simpler.
- Keep sequential enrichment to avoid multiplying network calls.

Reason:

This makes recommendation-quality evaluation measure the same kind of candidates that For You actually uses, without running the whole For You screen.

### 3. Expand Probe Outcome Metrics

Extend `SourceRecommendationFitProbeOutcome` enough to explain practical results.

Add fields if needed:

```kotlin
val enrichedCandidateCount: Int = 0
val weakMetadataCandidateCount: Int = 0
val unsupportedSearchCount: Int = 0
val timeoutCount: Int = 0
val allFilteredOutCount: Int = 0
val failureKind: SourceRecommendationProbeFailureKind = NONE
```

Keep fields minimal if schema churn becomes too large. Since `SourceRecommendationFit` already stores stats and `errorMessage`, not every internal counter must be persisted. But the persisted `errorMessage` should clearly communicate the main reason.

### 4. Improve `label()` Behavior

Current behavior in `SourceRecommendationFitProbeOutcome.label()`:

```kotlin
if (queryCount == 0) TOO_LITTLE_EVIDENCE
if (errorCount > 0 && querySuccessCount == 0) ERROR
if (rawResultCount == 0) NO_MATCHES
...
```

Keep the broad idea, but improve distinctions:

- No taste tags -> `TOO_LITTLE_EVIDENCE`
- Search throws/times out for every plan -> `ERROR`
- Search succeeds but raw results empty -> `NO_MATCHES`
- Raw results exist but no candidates can be scored because metadata is weak -> likely `WEAK` or `TOO_LITTLE_EVIDENCE`, not `ERROR`
- Raw results exist but all are blocked by user blocked tags -> `WEAK` with a reason like "All results blocked by tag filters"
- Raw results exist but all score zero after enrichment -> `WEAK` or `NO_MATCHES` depending on final semantics, but reason must say "results did not match profile"

The goal is to avoid using `ERROR` for "the source works but the results are not personally useful."

### 5. Persist Helpful Error/Reason Text

`buildRecQualityFitFromOutcome(...)` currently populates `errorMessage` only for `ERROR`.

Improve this:

- For `ERROR`, persist the failure reason.
- For `NO_MATCHES`, optionally persist a non-error reason such as "Search returned no raw results".
- For `WEAK`, optionally persist a reason such as "Raw results found, but none matched preferred tags after enrichment".
- UI can display reason text for non-error verdicts in subdued color, but keep it concise.

Do not turn the Source Evaluation row into a wall of text.

### 6. Add A Recommendation-Quality Diagnostics Summary

On the Source Evaluation screen, near the Recommendation Quality section, show a compact summary after checks run.

Examples:

- `Checked 12 promising sources`
- `4 install/load issues`
- `3 search errors`
- `2 no matches`
- `2 weak metadata`
- `1 good recommender`

This should help the user understand whether "everything is broken" or "these sources simply do not have useful recommendation/search behavior."

Implementation options:

- Compute from `recommendationFitsByEvalKey`.
- Prefer pure helper:

```text
SourceRecommendationQualityDiagnostics.kt
SourceRecommendationQualityDiagnosticsTest.kt
```

### 7. Keep Temporary Install Cleanup Safe

Do not weaken cleanup.

For non-installed promising sources:

1. Resolve available extension.
2. Temporarily install.
3. Wait for installed extension/source.
4. Probe.
5. Cleanup in `finally`.

If cleanup fails, do not crash. Record/log it. If possible, surface cleanup warning consistently with existing Source Evaluation cleanup warnings.

Do not uninstall pre-existing installed extensions.

### 8. Keep Network Work Bounded

Strict caps:

- Max two query plans per source, unless existing `RecommendationQueryPlanner` changes.
- Page 1 only.
- No chapter list fetch.
- No image fetch.
- Enrichment cap must be small and documented.
- Keep per-plan timeout.
- Keep per-extension install/load timeout.

### 9. Tests Required

Add or update focused tests.

Minimum tests:

- Probe returns `TOO_LITTLE_EVIDENCE` with no taste tags.
- Probe returns `ERROR` when all search plans throw.
- Probe returns `NO_MATCHES` when search succeeds but raw result list is empty.
- Probe handles raw results with weak metadata and reports a non-generic reason.
- Probe enrichment converts a weak raw result into a scored visible candidate when details provide matching genres.
- Failure classifier maps install/source/search errors to stable categories.
- Diagnostics helper counts categories correctly.
- Existing resolver tests still pass.

Use fake `CatalogueSource`, fake `NetworkToLocalManga`, and fake `GetTagAliases` rather than device/network tests.

### 10. Documentation Required

Update:

```text
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/KMK_RECS_V0_7_12_RECOMMENDATION_UX_AND_CROSS_EXTENSION_CONSOLIDATION_IMPLEMENTATION.md
```

Create:

```text
docs/recommendations/KMK_RECS_V0_7_13_SOURCE_EVALUATION_RECOMMENDATION_QUALITY_FUNCTIONAL_FIX_IMPLEMENTATION.md
```

The implementation note must explain:

- what was wrong in v0.7.12,
- why error display alone was insufficient,
- how rec-quality now differs from full For You,
- what it shares with For You,
- enrichment cap and network limits,
- what failure categories mean,
- files changed,
- tests added/run,
- manual QA steps,
- remaining limitations.

### 11. Versioning

Use:

```kotlin
KmkRecsReleaseNotes.VERSION_CODE = 713
KmkRecsReleaseNotes.VERSION_NAME = "KMK-Recs v0.7.13"
```

Android `versionCode` in `app/build.gradle.kts` must increase above 84. Suggested:

```kotlin
versionCode = 85 // KMK-Recs v0.7.13
```

Add What's New bullets only for user-facing changes:

- Recommendation Quality checks now use bounded metadata enrichment like For You, making results less likely to be falsely marked as no matches.
- Recommendation Quality rows now distinguish install/load/search/no-match/weak-metadata outcomes more clearly.
- Source Evaluation now shows a compact Recommendation Quality diagnostics summary after checks.

## Validation Commands

Run:

```text
.\gradlew.bat spotlessApply
.\gradlew.bat spotlessCheck
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat assembleDebug
```

If SQLDelight/domain model schema changes are made, run relevant SQLDelight generation/check tasks too. Prefer avoiding schema changes if the existing `SourceRecommendationFit.errorMessage` and stats fields are enough.

## Acceptance Criteria

This pass is complete only if:

- Recommendation-quality probing no longer depends only on raw search-result metadata.
- Bounded enrichment is used before scoring, matching For You behavior more closely.
- `ERROR` means a real failure, not just "results did not match profile."
- `NO_MATCHES`, weak metadata, all-filtered, and all-blocked states are distinguishable.
- The user can see a concise reason for recommendation-quality outcomes.
- Non-installed promising sources still install/probe/cleanup safely.
- Network calls remain bounded.
- Normal For You and global search behavior are preserved.
- Tests cover the new classification/enrichment behavior.
- Docs accurately state limitations.

## Manual QA Checklist

After installing the APK:

1. Open Recommendation Settings -> Experimental Source Evaluation.
2. Run "Evaluate recommendations" for existing Strong Fit / Worth Trying rows.
3. Confirm rows no longer all show unexplained `Error`.
4. Confirm error rows show a concrete reason.
5. Confirm no-match rows distinguish no raw results from filtered/weak metadata when possible.
6. Confirm non-installed promising sources are temporarily installed and cleaned up.
7. Confirm Source Evaluation remains usable after a failed source.
8. Confirm For You page still works normally.
9. Confirm normal global search is unchanged and uncapped.

