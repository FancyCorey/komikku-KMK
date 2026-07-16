# KMK-Recs v0.7.41 Discovery Policy Corrections And Source Evidence Roadmap

Date: 2026-07-11

Status: Â§2â€“Â§4 corrections COMPLETE and verified (KMK-Recs v0.7.41; APK `Komikku-v1.13.6-kmk.7.41-debug.apk`; spotlessApply/spotlessCheck/:app:testDebugUnitTest/assembleDebug all passed on JDK 17.0.19). A Codex review of the Â§2.A extra-page path found one remaining known-context gap (`discoverAdditionalPage()` called the shared policy with `knownIds = emptySet()`); that gap was fixed and verified the same day (2026-07-11) â€” see the "Follow-up" section of `KMK_RECS_V0_7_41_DISCOVERY_POLICY_CORRECTIONS_IMPLEMENTATION.md`. Â§7 Source Evidence Redesign remains planning scope only â€” NOT implemented.

Version family: KMK-Recs `v0.7.41`.

## 1. Purpose

This is a corrective release for verified gaps in v0.7.40. It must make the existing For You and rated-group recommendation behavior internally consistent before the project redesigns Source Evaluation evidence.

It also records the next separate phase, so future work does not confuse source catalogue fit with search compatibility or claim that a source's own recommendation system has been measured when it has not.

## 2. Verified v0.7.40 Corrections

### A. One candidate-visibility contract in every For You path

The live page-one and extra-page paths call `RecommendationCandidateVisibilityPolicy`, but these paths still duplicate a subset of its checks:

```text
app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt
  loadFromCache(...)
app/src/main/java/exh/recs/memory/RecommendationCandidateMemoryRanker.kt
  merge(...)
```

Neither current cache nor memory merging receives minimum-chapter context. Therefore a candidate hidden by the live `minChapterCount` filter can reappear through cache or discovery memory.

Required implementation:

1. Make `RecommendationCandidateVisibilityPolicy` the sole visibility decision-maker in page-one, extra-page, cache, and memory merge paths.
2. Extend `RecommendationCandidateMemoryRanker.merge()` with `minChapterCount` and `chapterCounts` arguments; it must call the policy rather than calling `shouldHideForYou` directly.
3. In `loadFromCache()`, load chapter counts only when the configured minimum is positive, using one batched `GetChapterCountsByMangaIds.await(ids)` call. Use the policy for every resolved cached manga.
4. Preserve current fail-open behavior: failed known-ID or chapter-count lookups log a warning and use empty data. Unknown chapter counts remain visible.
5. Do not change normal global search, library filtering, cross-extension matching, or the user preference meanings.

### B. Group recommendations must not exhaust their collection budget on hidden entries

`GroupSeededRecommendationsScreenModel.buildRecommendations()` currently counts a candidate after scoring and stops at `TARGET_RESULTS * 2`, but only applies rated/seen/known/chapter visibility after collection. A set dominated by known/rated items can therefore return far fewer than 20 results despite later valid source results.

Required implementation:

1. Preserve all hard limits: max five sources, eight raw results per source/plan, 12-second search timeout, 5-second localization timeout, 45-second total timeout, and final cap of 20 displayed results.
2. Keep database work batched. Do not introduce per-candidate known-ID or chapter-count queries.
3. Rework collection into bounded chunks: collect localized/scored candidates for a source/plan chunk, run one batch lookup for that chunk plus pending candidates, apply `RecommendationCandidateVisibilityPolicy`, then continue until 20 visible candidates are found or the existing source/plan limits are exhausted.
4. Seed-member, favorite, rated, seen, known, and minimum-chapter exclusions must not increment the visible-result target.
5. Deduplicate before final display by localized manga ID and retain the highest score for duplicate IDs.
6. On lookup failure, fail open and retain the candidate; log the failure once per batch.

### C. Retry state must be truthful and bounded

Current implementation stores an exhausted retry as `STATUS_ERROR` with `attemptCount == 3`, even though the approved contract requires a terminal `STATUS_EXHAUSTED` record retaining the diagnostic. It also checks the maximum page before retrying, so a due retryable failure on page 20 is never retried.

Required implementation:

1. Keep `MAX_ATTEMPTS = 3`, five-minute initial delay, bounded exponential backoff, 24-hour maximum delay, and no background retry loop.
2. In `discoverAdditionalPage()`, when a retryable probe fails for the final allowed time, persist `status = STATUS_EXHAUSTED`, `attemptCount = MAX_ATTEMPTS`, the original/truncated diagnostic, `failureKind = FAILURE_KIND_RETRYABLE`, and `nextRetryAt = null`.
3. In `RecommendationDiscoveryPlanner.nextPageToProbe()`, first select the lowest due retryable-error page below the cap; do not advance past a retryable error waiting for its retry time; treat exhausted/permanent/unsupported/success/empty/filtered/duplicate records as advanceable; and apply the page cap only to a *new* page. A due retry of page 20 is allowed; page 21 is not.
4. Preserve cancellation: rethrow `CancellationException`; do not write progress on cancellation.
5. Add no speculative database index. Existing source/query lookup retrieves the bounded record set and is sufficient.

### D. Conservative unknown-error classification

`RecommendationRetryClassifier.classify()` currently marks unknown extension exceptions retryable. This contradicts the documented safety rule and can repeatedly call broken extension code.

Required implementation:

1. Retry only connectivity/I/O/timeout failures with evidence of transience: `IOException`, `UnknownHostException`, socket timeout/connectivity equivalents, and the explicit local probe timeout path.
2. Mark unsupported-operation behavior, HTTP 4xx equivalents, and unknown extension/runtime exceptions permanent.
3. Keep the original exception message as a bounded diagnostic. Do not surface raw stack traces in normal For You UI.
4. Never classify or suppress cancellation.

## 3. Exact Target Files

Inspect these files before editing; use existing names rather than creating parallel systems:

```text
app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt
app/src/main/java/exh/recs/RecommendationCandidateVisibilityPolicy.kt
app/src/main/java/exh/recs/memory/RecommendationCandidateMemoryRanker.kt
app/src/main/java/exh/recs/memory/RecommendationDiscoveryPlanner.kt
app/src/main/java/exh/recs/memory/RecommendationRetryClassifier.kt
app/src/main/java/exh/recs/group/GroupSeededRecommendationsScreenModel.kt
app/src/main/java/exh/recs/group/GroupRecommendationLoopPolicy.kt
app/src/test/java/exh/recs/RecommendationCandidateVisibilityPolicyTest.kt
app/src/test/java/exh/recs/memory/RecommendationCandidateMemoryRankerTest.kt
app/src/test/java/exh/recs/memory/RecommendationDiscoveryPlannerTest.kt
app/src/test/java/exh/recs/group/GroupRecommendationLoopPolicyTest.kt
```

Only change database/domain files if tests demonstrate that an additional state constant cannot be represented by the existing `status` string. `STATUS_EXHAUSTED` already exists, so migration 59 must not be added merely for this release.

## 4. Required Regression Tests

Add or extend pure tests to prove:

1. Cached candidate below the configured minimum chapter count is hidden.
2. Memory candidate below the configured minimum chapter count is hidden.
3. Unknown/missing chapter count remains visible.
4. Cache, memory, and live candidates yield the same visibility result for favorite, rated-visibility modes, seen, known, and chapter rules.
5. Group collection continues after hidden candidates and can return later visible candidates without exceeding the established source/raw/timeout bounds.
6. Due retryable page 20 is selected; a new page 21 is never selected.
7. Third retryable failure records `STATUS_EXHAUSTED` and preserves its diagnostic.
8. Exhausted/permanent/unsupported records advance; a retryable failure before its due time blocks advancement.
9. Unknown extension exception is permanent, while I/O and timeout failures are retryable.
10. Cancellation creates no additional discovery-progress record.

Tests must use project test conventions. Do not add brittle UI/device tests as substitutes for these pure behavior tests.

## 5. Verification And Delivery Gate

The current workstation exposes Java 8 only. Komikku's Gradle build requires JDK 17 or later. Before claiming verification:

1. Install or configure a JDK 17+ explicitly for the Gradle invocation.
2. Confirm `java -version` used by Gradle is 17+.
3. Run:

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat spotlessCheck
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat assembleDebug
```

4. Record exact outcomes, JDK version, changed files, and APK path in `docs/recommendations/KMK_RECS_V0_7_41_DISCOVERY_POLICY_CORRECTIONS_IMPLEMENTATION.md`.

Do not create/copy the final APK before all commands pass. If JDK installation or a test is blocked, stop and document the blocker rather than marking the work complete.

## 6. Documentation Requirements

After successful implementation only:

1. Update `CURRENT_STATE.md` with corrected shared-policy and retry semantics.
2. Update `NEXT_WORK.md` and `README.md` with v0.7.41 status.
3. Preserve v0.7.40 as historically implemented, but label its discovered behavioral gaps as corrected by v0.7.41.
4. Do not claim that Source Evaluation redesign, source-native recommendation quality, or user-outcome learning shipped in v0.7.41.

## 7. Next Separate Phase: Source Evidence Redesign (v0.7.42 Planning Scope)

This section is a handoff boundary, not authorization to implement it inside v0.7.41.

### Goal

Make Source Evaluation honest about what it measured. It must no longer pool Popular/Latest catalogue samples with tag-search results and call the mixture a single recommendation-quality score.

### Required design work before code

1. **Catalogue fit**: sample Popular and Latest separately, score how closely their available metadata matches the user's taste, and record provenance/count/confidence independently.
2. **For You retrieval compatibility**: retain targeted query-plan probes as a separate measurement: whether this source can retrieve candidates for the user's tags/aliases using the For You query strategy. It is not source-native website recommendation quality.
3. **Shared taste affinity**: source evaluation must resolve normalized tags, user aliases, built-in synonyms where justified, explicit preferred/disliked/blocked preferences, and learned rating weights through one shared contract rather than its current learned-weight-only approximation.
4. **Metadata confidence**: distinguish missing tags, partial tags, successful metadata enrichment, unsupported filters, timeouts, and errors. Missing metadata must lower confidence, not silently count as dislike or a bad catalogue.
5. **Staleness/reassessment**: version existing `source_evaluation` and `source_recommendation_fit` semantics. Do not silently reinterpret old values. Define a migration/version invalidation strategy, extension-version expiry, taste-profile reassessment trigger, and targeted clear/recheck actions.
6. **Naming/UI**: labels must distinguish "Catalogue fit", "For You search compatibility", and any future user-outcome evidence. Do not label tag-search success as "Recommendations: Good".

### Explicit non-goals for v0.7.42 unless separately approved

- Website-specific recommendation adapters.
- Universal publication-date, remote chapter-count, or image-quality claims.
- Background crawling of whole catalogues.
- Evaluating every source recommendation for every manga.
- New automatic preference changes from browsing behavior.

### Entry criteria

Do not begin this redesign until v0.7.41 passes all verification gates and the follow-up plan has audited `SourceEvaluationRunner`, `SourceEvaluationScorer`, `SourceRecommendationFitProbe`, `source_evaluation.sq`, and `source_recommendation_fit.sq` against this model.

## 8. Deferred Until After Truthful Source Evidence

Only consider these after v0.7.42 establishes separate evidence layers:

- broader For You filters such as publication/latest-update age, status, and metadata-confidence controls where metadata supports them;
- local chapter-count controls beyond the existing minimum;
- refresh-effort modes and source-scope controls;
- catalogue freshness and retention cleanup for localized network manga rows;
- local user-outcome learning from exposed results, opens, dismissals, ratings, and source contribution outcomes;
- optional source-specific adapters for a small, maintained set of websites.


## 9. Post-Implementation Review Follow-Up: Additional-Page Known Context

Codex review after the v0.7.41 implementation found one remaining context mismatch. Unit tests pass with the repo-local JDK 17, but `BrowsePersonalRecommendationsScreenModel.discoverAdditionalPage()` still calls `RecommendationCandidateVisibilityPolicy.evaluate(...)` with `knownIds = emptySet()`.

The final merge path later filters additional-page candidates using known IDs, so this is not expected to leak visible known manga into the For You rows. However, progress status, visible/localized counts, and memory upsert can still treat known-only additional pages as successful. That is less truthful than the v0.7.41 contract and should be corrected before starting v0.7.42.

Required follow-up:

1. Pass `hideKnownManga` into `discoverAdditionalPage()`.
2. After localizing extra-page candidates, batch `GetKnownRecommendationMangaIds.await(localizedIds)` when hide-known is enabled.
3. Call `RecommendationCandidateVisibilityPolicy.evaluate(...)` with those known IDs, not `emptySet()`.
4. Base additional-page `localizedCount`, `filteredCount`, `scoredCount`, `visibleCount`, `progressStatus`, returned recommendations, and `memoryStore.upsertBatch(...)` eligibility on the fully filtered result.
5. Preserve fail-open behavior: if the known lookup fails, log a warning and pass `emptySet()`.
6. Add a focused regression test if the path can be extracted or tested through an existing pure helper. At minimum, document the verification in the follow-up implementation report.

This follow-up is part of the shared-candidate-policy cleanup and should be completed before the Source Evidence Redesign.
