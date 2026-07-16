# KMK-Recs v0.6.22 Source Evaluation Continuation And Recommendation Quality Plan

Date: 2026-06-22

Status: implementation plan only. Do not implement until the user approves and requests a Claude Code prompt.

## Purpose

Improve Source Evaluation in three focused, implementable ways:

1. **Continuation batches**: Source Evaluation should not keep restarting at the first 10/25/50/100 eligible candidates. It should support continuing through later eligible candidates over multiple runs.
2. **Past-results display filtering**: installed extensions/sources should not crowd the main past-evaluations list by default when the screen is mainly being used to decide which non-installed sources are worth trying.
3. **Second-stage recommendation-quality evaluation**: only sources already judged `STRONG_FIT` or `WORTH_TRYING` should receive an additional bounded For You-like probe that distinguishes a good catalogue/source from a good recommendation source.

This plan intentionally does **not** include automatic chapter/image-quality comparison. That topic needs separate research and should not be mixed into this pass.

## Version Line

Use:

```text
KMK-Recs v0.6.22
```

Reasoning: this is source/extension/source-evaluation behavior, so it belongs under the `0.6.x` source-evaluation line. The already documented v0.7.4 implementation touched source evaluation, but the user's long-term versioning preference is to keep extension/source-evaluation work under major `0.6`.

If Claude finds that the repository's current release notes force a different next patch number, document the conflict before changing it.

## Required Reading Before Coding

Claude must read:

```text
docs/recommendations/README.md
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/DOCUMENTATION_RULES.md
docs/recommendations/KMK_RECS_V0_6_21_SOURCE_EVALUATION_UPDATE_REASSESSMENT_AND_RECOMMENDATION_FIT_PLAN.md
docs/recommendations/KMK_RECS_V0_7_4_SOURCE_EVALUATION_UPDATE_REASSESSMENT_AND_RECOMMENDATION_FIT_IMPLEMENTATION.md
docs/recommendations/KMK_RECS_V0_6_22_SOURCE_EVALUATION_CONTINUATION_AND_RECOMMENDATION_QUALITY_PLAN.md
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

### Source Evaluation Batches

`SourceEvaluationRunner.start(candidates, options)` currently does:

```kotlin
for (candidate in candidates.take(options.batchSize)) { ... }
```

This means a run evaluates only the first `batchSize` candidates in the passed filtered list. It does not persist a continuation cursor or start offset. If the eligible candidate order remains the same, the user can repeatedly hit the same leading slice unless skip/evaluated filters remove them.

This is especially limiting for reassessment modes, updated-only modes, or any mode where the user wants to continue through later eligible candidates over time.

### Past Evaluation Results

`SourceEvaluationScreenModel` subscribes to `getSourceEvaluations.subscribeAll()` and stores sanitized evaluations directly in state. `SourceEvaluationScreen` sorts `state.evaluations` and renders them. There is no default UI filter that hides rows whose extension/source is now installed.

Installed sources can remain in the DB for history, but they do not need to occupy the main "what should I try next?" visual space by default.

### Recommendation Quality

Current general evaluation probes:

- popular manga,
- latest updates if supported,
- search queries derived from taste profile,
- sampled titles/tags,
- search success/error counts.

This answers:

```text
Does this source appear to contain content aligned with my taste?
```

It does not yet answer:

```text
Does this source produce good For You recommendations?
```

The pure helper `SourceRecommendationFitScorer` already exists and is documented as deferred execution. `SourceRecommendationFitEligibility` already gates `STRONG_FIT` / `WORTH_TRYING` with minimum sample count. The missing part is actual bounded probe execution and persistence/UI display.

### Source API Limits Relevant To Future Quality Work

The Tachiyomi/Mihon-style API exposes source methods such as:

- `getPopularManga(page)`
- `getSearchManga(page, query, filters)`
- `getLatestUpdates(page)`
- `getMangaDetails(manga)`
- `getChapterList(manga)`
- `getPageList(chapter)`

The API does not expose a universal chapter/image-quality score. Chapter/image quality comparison should be separate research because it may require fetching chapter lists, page lists, image URLs, image headers, or even sample image bytes. That is too expensive and risky to fold into Source Evaluation continuation/recommendation-quality work.

## Non-Goals

Do not include:

- chapter/image-quality scoring,
- downloading sample pages to compare resolution,
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

### Problem

The user can choose 10, 25, 50, or 100 candidates, but Source Evaluation does not remember where it stopped. It cannot naturally continue through the next batch.

### Required Behavior

Add an explicit continuation workflow:

- show how many eligible candidates remain;
- after a batch completes, allow `Continue next batch`;
- continuation should start after the last successfully considered candidate for the same evaluation mode/filter fingerprint;
- if the filter options change, reset or invalidate the cursor;
- if candidate ordering changes because repos/extensions update, handle gracefully and avoid crashes;
- skip candidates already evaluated when the option is enabled;
- do not silently reprocess the first page of candidates unless the user explicitly starts over or changes options.

### Recommended Model

Create a pure cursor helper:

```text
app/src/main/java/exh/recs/evaluation/SourceEvaluationContinuationPolicy.kt
app/src/test/java/exh/recs/evaluation/SourceEvaluationContinuationPolicyTest.kt
```

Suggested data:

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

If source-level continuation is needed, use:

```text
signatureHash|pkgName|sourceId
```

But the runner evaluates extension candidates, so extension-level cursor is probably enough for v0.6.22.

### Storage Options

Preferred first pass:

- store cursor in `SourcePreferences` as a compact serialized string or JSON.

Avoid a new SQL table unless current project patterns make preference storage awkward.

Possible preference keys:

```text
sourceEvaluationContinuationCursor
sourceEvaluationContinuationModeKey
sourceEvaluationContinuationUpdatedAt
```

If `SourcePreferences` does not have a clean place for this, create a small preference wrapper/helper rather than scattering string parsing across screen model code.

### Filter Fingerprint

Cursor must be tied to option state, otherwise "continue" could skip the wrong sources.

Include:

- recommendation languages,
- include explicit,
- skip already evaluated,
- re-evaluate stale,
- only updated evaluated,
- block explicit preference,
- disliked/unsafe filters if practical,
- current evaluation mode key.

Do not include batch size. Changing from 10 to 25 should continue the same queue with a larger slice.

### UI

In `SourceEvaluationScreen`:

- show candidate count as today;
- add text such as:

```text
Next batch starts after the last completed source.
```

only when a valid cursor exists.

Buttons:

- `Start evaluation`: starts or restarts depending on current cursor state.
- `Continue next batch`: visible when a valid cursor exists and there are remaining candidates.
- `Start over`: optional, confirmation required, clears cursor.

Keep wording simple. Do not add a large new panel.

### Runner / Job Integration

`SourceEvaluationScreenModel.launchEvaluation()` currently passes `s.candidates` directly into `SourceEvaluationJobState.pendingCandidates`.

Recommended change:

1. Build the full filtered candidate list.
2. Use `SourceEvaluationContinuationPolicy.sliceForRun(...)` to select the next batch.
3. Pass only that slice to the job.
4. On job completion, update the cursor with completed candidate keys.
5. If cancelled, preserve completed keys only for candidates actually completed if the queue state can report them. If not, preserve cursor before run and let the user retry safely.

The current `SourceEvaluationQueueState.completedCount` is count-based, not candidate-key-based. If reliable per-candidate completion keys are not exposed, add lightweight tracking to `SourceEvaluationQueueState` or `SourceEvaluationJobState`.

### Edge Cases

Handle:

- candidate removed from repo between runs,
- candidate installed by user between runs,
- candidate quarantined between runs,
- explicit filter toggled,
- language filter changed,
- cursor points to a candidate no longer present,
- empty next slice,
- job cancellation,
- app restart.

Expected behavior:

- no crash;
- invalid cursor clears or resets with a message;
- user can start over.

## Part 2: Hide Installed Sources From Past Evaluations By Default

### Problem

Past evaluations currently show installed and non-installed rows together. Installed rows can take up visual space from non-installed `STRONG_FIT` / `WORTH_TRYING` sources the user is actively deciding whether to install.

### Required Behavior

Past evaluations should hide installed sources/extensions by default.

Requirements:

- Installed evaluations remain in the database.
- Installed evaluations are hidden from the main past-results list by default.
- User can toggle them back on.
- Strong/Worth Trying non-installed rows should be easier to see.
- The filter must be display-only and must not delete rows.

### Installed Detection

Use `ExtensionManager.installedExtensionsFlow` or `SourceManager.getVisibleCatalogueSources()` to build installed identities.

Prefer extension identity where possible:

```text
signatureHash|pkgName
```

Also compare `sourceId` when available.

Reason: an extension may contain multiple sources, and source IDs may be present in evaluation rows.

### UI

Add a compact filter toggle near the past-results sort dropdown:

```text
Show installed
```

Default:

```text
false
```

Also show small summary:

```text
Hidden installed: N
```

if any installed rows are hidden.

Do not place installed rows in a large top card.

### Sorting

Apply filtering before sorting. Then sort remaining rows by selected sort mode.

If `Show installed` is enabled, sort all rows using the existing selected sort.

### Tests

Add pure helper if practical:

```text
SourceEvaluationDisplayFilter.kt
SourceEvaluationDisplayFilterTest.kt
```

Test:

- installed extension key hidden by default;
- installed source ID hidden by default;
- uninstalled strong/worth-trying rows remain visible;
- showInstalled=true includes all rows;
- display filter does not mutate source list;
- blank/null source IDs are handled safely.

## Part 3: Second-Stage Recommendation-Quality Evaluation

### Problem

General source evaluation and recommendation quality are different.

A source can have good content but poor recommendation usefulness if its search results are noisy, tags are sparse, filters are poor, or most results get filtered out.

### Required Behavior

Add a second-stage bounded recommendation-quality probe for non-installed sources that are already:

```text
STRONG_FIT
WORTH_TRYING
```

and pass `SourceRecommendationFitEligibility`.

This should produce a separate recommendation-quality label, not replace the general source verdict.

Example labels:

```text
Recommendations: Great
Recommendations: Good
Recommendations: Mixed
Recommendations: Weak
Recommendations: No matches
Recommendations: Error
Recommendations: Too little evidence
```

### Important Distinction

Do not make source quality worse because recommendation quality is weak.

Display them separately:

```text
Source fit: Strong Fit
Recommendations: Mixed
```

or:

```text
Source fit: Worth Trying
Recommendations: Great
```

### Probe Scope

Run the recommendation-quality probe only while the extension is already temporarily installed during Source Evaluation.

Do not reinstall extensions solely for this probe unless the user starts a new evaluation/reassessment run.

For each eligible source:

- max 2 query plans,
- page 1 only,
- low raw result cap,
- no chapter list fetch,
- no page list fetch,
- no image fetch,
- per-source timeout,
- errors are recorded as recommendation probe errors, not batch failures.

### Reuse For You Logic

The probe should reuse, not duplicate, as much of the For You query/scoring logic as practical:

- `RecommendationQueryPlanner`
- `GenreFilterMapper`
- `PersonalRecommendationScorer`
- `RecommendationCandidateEnricher` if bounded safely

Do **not** instantiate or copy the whole `BrowsePersonalRecommendationsScreenModel`.

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

Map this to existing `SourceRecommendationFitScorer.Outcome`.

### Persistence

Current `source_evaluation` table already has:

```text
recommendation_fit_score REAL NOT NULL
```

But that field currently belongs to general source evaluation scoring, not the new second-stage recommendation-quality probe.

Avoid overloading it silently.

Preferred option:

1. Add a separate table:

```text
source_recommendation_fit
```

2. Store the probe result separately.

Suggested columns:

```sql
fit_key TEXT NOT NULL PRIMARY KEY,
evaluation_key TEXT,
source_id INTEGER,
extension_pkg_name TEXT NOT NULL,
signature_hash TEXT NOT NULL,
extension_name TEXT NOT NULL,
source_name TEXT NOT NULL,
lang TEXT NOT NULL,
evaluated_at INTEGER NOT NULL,
query_count INTEGER NOT NULL,
query_success_count INTEGER NOT NULL,
raw_result_count INTEGER NOT NULL,
visible_candidate_count INTEGER NOT NULL,
filtered_out_count INTEGER NOT NULL,
blocked_tag_candidate_count INTEGER NOT NULL,
matched_group_count INTEGER NOT NULL,
top_picks_contribution INTEGER NOT NULL,
no_matches_count INTEGER NOT NULL,
error_count INTEGER NOT NULL,
avg_candidate_score REAL NOT NULL,
recommendation_quality_score REAL NOT NULL,
verdict TEXT NOT NULL,
reasons_json TEXT,
error_message TEXT
```

Use next migration number after current latest.

If Claude determines adding a table is too much for this pass, it may add nullable columns to `source_evaluation`, but must document the tradeoff. The separate table is preferred because it keeps general source evaluation distinct from recommendation-quality evaluation.

### Domain Layer

Suggested additions:

```text
domain/src/main/java/tachiyomi/domain/taste/model/SourceRecommendationFit.kt
domain/src/main/java/tachiyomi/domain/taste/repository/SourceRecommendationFitRepository.kt
domain/src/main/java/tachiyomi/domain/taste/interactor/GetSourceRecommendationFit.kt
domain/src/main/java/tachiyomi/domain/taste/interactor/UpsertSourceRecommendationFit.kt
data/src/main/java/tachiyomi/data/taste/SourceRecommendationFitRepositoryImpl.kt
```

Register in `KMKDomainModule`.

### UI

In `EvaluationResultRow`, append compact recommendation-quality label when fit data exists:

```text
Recommendations: Good
```

Do not make the row too long. If needed, replace some subtitle detail with a second line or concise chip.

Add sort mode if the data exists:

```text
Recommendation quality
```

But keep existing sort modes.

### Sources To Try

If a non-installed source has recommendation-quality data:

- use it as stronger evidence than metadata-only suggestions;
- show a concise reason;
- do not show weak recommendation-quality sources as top recommendations unless source fit is still strong and user wants to inspect.

Do not make this part too broad. If Sources To Try integration is risky, defer it and only show the label in Source Evaluation.

## Part 4: User Controls

Add controls only if they stay small:

1. `Continue next batch`
2. `Start over`
3. `Show installed`
4. `Run recommendation-quality check for promising sources`

The fourth control can be:

- automatic during evaluation for eligible sources, or
- a toggle in Source Evaluation options.

Recommended default:

```text
Run recommendation-quality check for Strong/Worth Trying sources: enabled
```

Reason: the probe is bounded and only runs while an extension is already installed.

If performance becomes a concern, expose it as a toggle and default it off for the first APK.

## Part 5: Exception Handling

Handle:

- source probe timeout,
- recommendation-fit probe timeout,
- source returning malformed/empty manga data,
- source filter list throwing,
- network loss mid-probe,
- source manager or extension manager failures,
- DB table missing/migration failure fallback,
- invalid cursor,
- app restart during background evaluation,
- cancellation while running,
- installed-source detection failure.

Expected behavior:

- no app crash;
- current extension/source records an error where possible;
- batch continues to next candidate where safe;
- cursor remains recoverable or resets with a clear message;
- UI shows safe fallback state.

## Part 6: Tests

Add focused unit tests.

Required:

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

Test cases:

- continuation returns first batch when no cursor exists;
- continuation returns next batch after completed keys;
- continuation ignores batch size in fingerprint;
- continuation resets on filter fingerprint mismatch;
- continuation handles missing last candidate safely;
- installed evaluations hidden by default;
- showInstalled=true includes installed evaluations;
- strong/worth-trying eligible for recommendation probe;
- weak/rejected/error not eligible;
- insufficient sample count prevents probe;
- recommendation score rewards visible high-score results;
- recommendation score penalizes no matches/errors/blocked-tag noise;
- probe handles thrown filter list;
- probe handles thrown search;
- probe handles empty results;
- probe caps query count and raw results;
- result UI helper maps score to Great/Good/Mixed/Weak labels.

Manual tests:

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

Suggested focused test command:

```text
./gradlew :app:testDebugUnitTest --tests "*SourceEvaluation*" --tests "*SourceRecommendationFit*"
```

If schema/domain changes are broad, also run:

```text
./gradlew :app:testDebugUnitTest
```

## Part 7: Documentation

After implementation, Claude must create:

```text
docs/recommendations/KMK_RECS_V0_6_22_SOURCE_EVALUATION_CONTINUATION_AND_RECOMMENDATION_QUALITY_IMPLEMENTATION.md
```

Update:

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

Keep these out of v0.6.22:

- automatic chapter/image-quality comparison across sources,
- best chapter version detection,
- sample image dimension/hash comparison,
- chapter-count-based best-version scoring,
- source-specific website recommendation scraping,
- broad source-quality learning from long-term For You history if not needed for the bounded probe.

The next research topic can investigate whether same-manga/global-search candidates can be compared for chapter/image quality safely and cheaply.

## Summary Recommendation

This pass is feasible and should be implemented before researching chapter/image quality.

The clean scope is:

1. make Source Evaluation resumable across batches,
2. hide installed past evaluations by default,
3. run a bounded recommendation-quality probe only for Strong/Worth Trying sources,
4. keep the result separate from general source quality,
5. document and test all failure paths.

This directly addresses current usability issues without turning evaluation into an unbounded crawler.

