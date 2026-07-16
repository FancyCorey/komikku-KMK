# KMK-Recs v0.6.21 Source Evaluation Update Reassessment And Recommendation Fit Plan

Date: 2026-06-21

Status: implementation plan only. Do not implement until the user approves this plan and requests a Claude Code prompt.

## Purpose

Improve Source Evaluation without turning it into an unbounded crawler.

This pass should answer two practical questions:

1. Which already-evaluated extensions need reassessment because the extension itself changed?
2. Which evaluated sources are actually good recommendation sources for the user's For You taste profile, not merely sources with generally decent manga?

The implementation must stay under the source/extension/recommendation-source version line:

```text
KMK-Recs v0.6.21
```

This is source evaluation and source quality work, not Loved Manga `v0.7.x` work and not cross-extension matching `v0.5.x` work.

## Required Reading Before Coding

Claude must read these files before implementation:

```text
docs/recommendations/README.md
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/DOCUMENTATION_RULES.md
docs/recommendations/KMK_RECS_DEFERRED_FEATURE_MASTER_IMPLEMENTATION_PLAN.md
docs/recommendations/INSTALLED_SOURCE_FIT_FEASIBILITY_RESEARCH.md
docs/recommendations/KMK_RECS_V0_6_20_SOURCE_ORDER_REASSESSMENT_AND_DOC_CLEANUP_IMPLEMENTATION.md
docs/recommendations/KMK_RECS_V0_6_19_SOURCE_EVALUATION_UX_AND_BACKGROUND_EXECUTION_IMPLEMENTATION.md
docs/recommendations/KMK_RECS_V0_6_19_SOURCE_EVALUATION_UX_AND_BACKGROUND_EXECUTION_FOLLOWUP_IMPLEMENTATION.md
RECOMMENDATION_VERSIONING.md
```

Claude must inspect the current code before editing. Do not rely only on the docs, because some deferred notes are stale.

## Deferred-Feature Audit

The current docs show a mix of true deferred items and stale deferred items.

### Confirmed Still Deferred Or Partially Deferred

These are still relevant and should be considered in this plan:

- Source quality learning / installed source fit learning.
- Better distinction between general source quality and recommendation quality.
- Updated-extension reassessment beyond "100 new manga ratings since baseline."
- Broader extension repo failure surfacing.
- Better mid-run connectivity loss reporting/retry behavior.
- Hidden-source state separate from dislike.
- UI path to manage/reset hidden or disliked non-installed suggestions.
- Evidence strength string resource hookup / i18n cleanup.
- Source status timestamps / source-evaluation freshness polish.
- Backup/restore for seen manga.
- Query-time blocked tag exclusion.
- Pull-to-refresh.
- Local Source product decision.
- Larger Source Evaluation batches, currently still intentionally deferred.
- AniList/tracker known-list cache.

### Stale Or Likely Already Implemented

These should be audited and corrected in documentation rather than rebuilt:

- **Favorite other versions**: code contains `CrossExtensionMatchMode.Favorite`, route handling, manga-page entry point, and matching screen labels. `NEXT_WORK.md` still says Favorite mode was deferred. Claude should verify behavior and update docs accordingly.
- **Alternate-title cross-extension matching**: code contains `CrossExtensionMatchQueryPlanner.buildQueries(manga)` and `CrossExtensionMatchScreenModel` uses it. `NEXT_WORK.md` still lists bounded alternate-query matching as deferred. Claude should verify current behavior and update docs accordingly.
- **Cross-source link groups**: already implemented and audited in v0.7.2. Do not reimplement.
- **Loved Manga view**: already implemented through v0.7.3. Do not reimplement.

### Items Not Included In v0.6.21

Do not include these in this pass unless the user explicitly expands scope:

- Backup/restore for seen manga.
- Query-time blocked tag exclusion.
- Pull-to-refresh.
- AniList/tracker known-list cache.
- Local Source support.
- Larger 500/1000 evaluation batches.
- Link group management UI.
- Loved Manga sorting/live-refresh polish.

Reason: v0.6.21 should stay focused on Source Evaluation reassessment and source recommendation-fit learning. Mixing unrelated deferred items increases risk and makes tablet testing harder.

## Current Implementation Facts

### Source Evaluation

Current behavior from docs and code:

- `SourceEvaluationScreen` is the UI surface.
- `SourceEvaluationScreenModel` owns screen state, options, current evaluations, management actions, reassessment prompt, and WorkManager launch.
- `SourceEvaluationRunner` temporarily installs non-installed extensions one at a time, probes popular/latest/search, scores each source, writes `SourceEvaluation`, and cleans up.
- `SourceEvaluationJob` runs background evaluation through WorkManager.
- `SourceEvaluationCandidateFilter` builds the candidate pool and applies options like skip-already-evaluated, include-explicit, and stale reevaluation.
- `source_evaluation.sq` stores evaluated source records.
- Existing fields include `quality_score`, `recommendation_fit_score`, `search_reliability_score`, `verdict`, sampled title/tag JSON, and probe counts.
- Existing `SourceEvaluation` rows are keyed by `signatureHash|pkgName|sourceId` or extension key fallback.
- Existing table does **not** store extension `versionName`, `versionCode`, or `apkName`.
- Current stale logic only checks `expiresAt` and `evaluationVersion < SourceEvaluationKeys.CURRENT_VERSION`.

### For You

Current behavior from code:

- `BrowsePersonalRecommendationsScreenModel` performs actual For You source searches.
- It already knows per-source outcomes:
  - `Shown`
  - `NoMatches`
  - `FilteredOut`
  - `Error`
  - `HiddenByDuplicateHandling`
- It builds Top Picks from already-fetched source results through `CombinedPicksAccumulator`.
- It stores last-run statuses in `SourcePreferences.recommendationLastSourceRunStatuses()`.
- It does not currently persist rolling source-fit statistics.
- It does not currently feed For You run quality back into Source Evaluation records.

### Recommendation Quality Problem

The current source evaluation can say:

```text
This source appears to contain manga/tags/titles that match your taste profile.
```

But it does not yet reliably say:

```text
This source produces good For You recommendation rows when searched with the actual For You query strategy.
```

Those are different. A source may have good manga but poor search behavior, poor tags, unreliable results, noisy results, or bad recommendation fit.

## Goals

1. Add updated-extension-aware reassessment.
2. Add a bounded recommendation-quality evaluation layer for sources already judged promising.
3. Keep evaluation efficient and non-repetitive.
4. Avoid evaluating recommendation quality for every extension/source.
5. Use existing Source Evaluation and For You search infrastructure where practical.
6. Keep the user in control; do not silently reorder priority.
7. Correct stale docs so future Codex/Claude passes do not chase already-implemented work.

## Non-Goals

- Do not run recommendation-quality evaluation against every manga.
- Do not run recommendation-quality evaluation against every available extension.
- Do not crawl whole websites.
- Do not use website-specific related/recommendation parsing.
- Do not fetch chapter lists for source quality.
- Do not automatically install extensions outside the existing Source Evaluation flow.
- Do not change normal global search.
- Do not change Loved Manga behavior.
- Do not auto-reorder the user's Source Priority list.

## Part 1: Documentation Audit And Cleanup

Before app code changes, Claude should verify and fix stale docs.

### Verify Implemented Features

Inspect:

```text
app/src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt
app/src/main/java/exh/recs/matching/CrossExtensionMatchRouteMode.kt
app/src/main/java/exh/recs/matching/CrossExtensionMatchQueryPlanner.kt
app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreen.kt
app/src/main/java/eu/kanade/presentation/manga/components/MangaInfoHeader.kt
docs/recommendations/NEXT_WORK.md
docs/recommendations/CURRENT_STATE.md
docs/recommendations/KMK_RECS_DEFERRED_FEATURE_MASTER_IMPLEMENTATION_PLAN.md
RECOMMENDATION_VERSIONING.md
```

Expected corrections:

- If Favorite other versions is confirmed working, remove or revise `NEXT_WORK.md` section claiming it is deferred.
- If alternate-query matching is confirmed working, remove or revise `NEXT_WORK.md` section claiming it is deferred.
- Update master deferred plan baseline from v0.6.20 where appropriate, or clearly mark older phase entries as historical/stale.
- Do not remove historical plans. Archive or annotate only if needed.

Documentation must state what remains deferred after v0.6.21.

## Part 2: Updated-Extension-Aware Reassessment

### Problem

The current reassessment prompt is based on taste changes:

```text
currentRatedCount - reassessmentBaselineCount >= 100
```

That is useful, but it does not handle extension changes. If an extension updates, its website parser, source list, search behavior, supported filters, NSFW metadata, or source IDs may change. The old evaluation may no longer be valid.

### Required Behavior

Add a way to reassess only sources/extensions whose extension package/version changed since their last evaluation.

The Source Evaluation screen should support:

- existing "Reassess using current tastes" / "Reassess sources";
- new "Reassess updated extensions" or "Reassess updated only" action when applicable;
- count of updated evaluated extensions/sources when known;
- no prompt when there are no updated evaluated extensions;
- no broad reevaluation of everything unless the user chooses the existing full reassessment action.

### Data Model

Add extension version metadata to `source_evaluation`.

Suggested new nullable columns:

```sql
extension_version_name TEXT;
extension_version_code INTEGER;
extension_apk_name TEXT;
```

Use the next SQLDelight migration number after the current latest migration. At the time of this plan, the latest known migration is:

```text
50.sqm
```

So this likely requires:

```text
data/src/main/sqldelight/tachiyomi/migrations/51.sqm
```

Claude must confirm the current latest migration before creating a new one.

### Domain Model Updates

Update:

```text
domain/src/main/java/tachiyomi/domain/taste/model/SourceEvaluation.kt
data/src/main/java/tachiyomi/data/taste/SourceEvaluationRepositoryImpl.kt
data/src/main/sqldelight/tachiyomi/data/source_evaluation.sq
```

Add fields to `SourceEvaluation`:

```kotlin
val extensionVersionName: String?
val extensionVersionCode: Long?
val extensionApkName: String?
```

Keep them nullable so old rows migrate safely.

### Writing Version Metadata

Update `SourceEvaluationRunner` and `SourceEvaluationScorer` call sites so every new source evaluation stores:

- `ext.versionName`
- `ext.versionCode`
- `ext.apkName`

For installed extension values after temporary install, prefer the `Extension.Available` metadata because it is the repo candidate being evaluated. If Claude discovers installed metadata is more reliable, document why.

### Updated Detection Helper

Create a pure helper:

```text
app/src/main/java/exh/recs/evaluation/SourceEvaluationUpdatePolicy.kt
app/src/test/java/exh/recs/evaluation/SourceEvaluationUpdatePolicyTest.kt
```

Suggested model:

```kotlin
data class EvaluationVersionSnapshot(
    val extensionKey: String,
    val pkgName: String,
    val signatureHash: String,
    val versionName: String?,
    val versionCode: Long?,
    val apkName: String?,
)

data class AvailableExtensionSnapshot(
    val extensionKey: String,
    val pkgName: String,
    val signatureHash: String,
    val versionName: String?,
    val versionCode: Long?,
    val apkName: String?,
)
```

Detection rules:

1. If there is no prior evaluation, it is not an "updated evaluated extension"; it is simply unevaluated.
2. If prior evaluation version metadata is missing, treat as update-unknown and eligible for optional updated reassessment, but label carefully.
3. If available `versionCode` is greater than stored `versionCode`, mark updated.
4. If `versionCode` is equal but `versionName` or `apkName` changed, mark changed/possibly updated.
5. If signature hash changes, treat as a different extension key unless existing code already treats it as same extension family. Do not merge silently.

### Candidate Filtering

Extend `SourceEvaluationCandidateFilter.applyOptions()` or add a new filter mode without overloading `skipAlreadyEvaluated`.

Suggested option:

```kotlin
val onlyUpdatedEvaluated: Boolean = false
```

Behavior:

- normal Start Evaluation:
  - respects existing `skipAlreadyEvaluated`;
- full reassessment:
  - sets `skipAlreadyEvaluated = false`;
- updated-only reassessment:
  - includes only candidates with prior evaluations where version policy says updated or update-unknown;
  - excludes never-evaluated candidates unless user starts normal evaluation;
  - respects language, NSFW, explicit, disliked, installed, and unsafe filters.

Do not repurpose `reEvaluateStale` unless it remains semantically clear. Updated extension reassessment is not the same as expiry/version of scorer logic.

### UI

In `SourceEvaluationScreen`:

- show compact text such as:

```text
3 evaluated extensions have updates available.
```

- show button:

```text
Reassess updated extensions
```

- button disabled if count is 0;
- if version metadata is missing for old rows, label as:

```text
Some older evaluations need version refresh.
```

Use existing `screenErrorMessage` pattern for failures.

### Baseline Interaction

When updated-only reassessment completes:

- do **not** reset the 100-rating reassessment baseline unless Claude confirms the run covered the same scope as full reassessment.
- It is safer to update a separate timestamp:

```text
sourceEvaluationLastUpdatedExtensionReassessmentAt
```

If adding a preference is too much, document that updated-only reassessment does not affect taste reassessment baseline.

## Part 3: Recommendation-Quality Evaluation For Strong/Worth-Trying Sources Only

### User Requirement

Do not evaluate recommendation quality for every manga or every source.

The correct shape is:

1. Run general Source Evaluation first.
2. Identify evaluated sources with verdict:

```text
STRONG_FIT
WORTH_TRYING
```

3. Only for those promising sources, perform a bounded recommendation-quality check.
4. Use that check to distinguish:

```text
good source overall
```

from:

```text
good recommendation source for For You
```

### Why This Matters

A source can have a high general evaluation score because popular/latest/search samples match preferred tags, but it may still be weak for For You because:

- search returns noisy results;
- filters are not respected;
- results are mostly blocked tags;
- results are duplicates of better sources;
- metadata is too sparse to score well;
- the source returns no useful results for actual For You query strategies;
- the source is slow or error-prone.

### Data Model Options

Prefer a separate table instead of expanding `source_evaluation` too much.

Suggested table:

```sql
CREATE TABLE source_recommendation_fit (
    fit_key TEXT NOT NULL PRIMARY KEY,
    source_id INTEGER,
    extension_pkg_name TEXT NOT NULL,
    signature_hash TEXT NOT NULL,
    extension_name TEXT NOT NULL,
    source_name TEXT NOT NULL,
    lang TEXT NOT NULL,
    evaluated_at INTEGER NOT NULL,
    source_evaluation_key TEXT,
    source_evaluation_verdict TEXT,
    query_count INTEGER NOT NULL,
    query_success_count INTEGER NOT NULL,
    raw_result_count INTEGER NOT NULL,
    visible_result_count INTEGER NOT NULL,
    top_pick_count INTEGER NOT NULL,
    filtered_out_count INTEGER NOT NULL,
    duplicate_hidden_count INTEGER NOT NULL,
    blocked_tag_result_count INTEGER NOT NULL,
    disliked_result_count INTEGER NOT NULL,
    average_candidate_score REAL NOT NULL,
    average_matched_group_count REAL NOT NULL,
    recommendation_quality_score REAL NOT NULL,
    reliability_score REAL NOT NULL,
    noise_score REAL NOT NULL,
    confidence_score REAL NOT NULL,
    verdict TEXT NOT NULL,
    reasons_json TEXT,
    error_message TEXT
);
```

Possible verdicts:

```text
GREAT_RECOMMENDATIONS
GOOD_RECOMMENDATIONS
MIXED_RECOMMENDATIONS
WEAK_RECOMMENDATIONS
NO_RECOMMENDATION_MATCHES
RECOMMENDATION_ERROR
INSUFFICIENT_EVIDENCE
```

Create a pure model under:

```text
domain/src/main/java/tachiyomi/domain/taste/model/SourceRecommendationFit.kt
domain/src/main/java/tachiyomi/domain/taste/repository/SourceRecommendationFitRepository.kt
data/src/main/java/tachiyomi/data/taste/SourceRecommendationFitRepositoryImpl.kt
```

Register interactors in `KMKDomainModule`.

### Bounded Recommendation-Fit Probe

Do not install/evaluate all extensions again from scratch.

Recommended approach:

#### For Already Installed Sources

Use actual For You run data where possible. Add a rolling stats accumulator that observes outcomes from `BrowsePersonalRecommendationsScreenModel.searchSource()`.

Do not add network calls solely for stats if For You already ran.

Track:

- status: Shown/NoMatches/FilteredOut/Error/HiddenByDuplicateHandling;
- visible count;
- average candidate score;
- matched group count;
- Top Picks contribution count;
- later user Like/Love/Dislike if easy to connect by `(source,url)`;
- updated timestamp.

This becomes long-term installed-source fit learning.

#### For Non-Installed Strong/Worth-Trying Sources

During Source Evaluation, after general `probeAndScore()` returns `STRONG_FIT` or `WORTH_TRYING`, run a **small additional recommendation-fit probe while the extension is already temporarily installed**.

Important: only do this while the extension is already installed in the current evaluation pass. Do not reinstall just to probe recommendation fit unless the user explicitly starts updated/full reassessment.

Bounded limits:

- max 2 query plans per source;
- max first page only;
- max raw candidates around existing For You raw cap style, but lower if needed;
- no chapter list fetch;
- no manga-detail enrichment beyond what existing evaluation already does unless using a very small cap;
- per-source timeout with `withTimeoutOrNull`;
- error becomes `RECOMMENDATION_ERROR`, not batch failure.

### Reuse Existing For You Planning

Inspect and reuse where safe:

```text
app/src/main/java/exh/recs/RecommendationQueryPlanner.kt
app/src/main/java/exh/recs/sources/GenreFilterMapper.kt
app/src/main/java/exh/recs/PersonalRecommendationScorer.kt
app/src/main/java/exh/recs/RecommendationCandidateEnricher.kt
app/src/main/java/exh/recs/CombinedPicksAccumulator.kt
app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt
```

Do not copy the whole For You screen model into Source Evaluation.

Preferred design:

```text
app/src/main/java/exh/recs/evaluation/SourceRecommendationFitProbe.kt
app/src/main/java/exh/recs/evaluation/SourceRecommendationFitScorer.kt
app/src/test/java/exh/recs/evaluation/SourceRecommendationFitScorerTest.kt
```

The probe should be a small service/helper that accepts:

- `CatalogueSource`
- `TasteProfile`
- tag aliases / alias candidates
- known taste/seen settings if needed
- small bounded options

and returns a pure-ish result object that the scorer can persist.

### Strong/Worth-Trying Gate

Implement a pure helper:

```text
app/src/main/java/exh/recs/evaluation/SourceRecommendationFitEligibility.kt
app/src/test/java/exh/recs/evaluation/SourceRecommendationFitEligibilityTest.kt
```

Rules:

- `STRONG_FIT` -> eligible.
- `WORTH_TRYING` -> eligible.
- `ECCHI_HEAVY` -> not eligible unless user explicitly includes ecchi and source is not explicit-heavy.
- `EXPLICIT_HEAVY` -> not eligible when explicit block is enabled.
- `REJECTED`, `ERROR`, low confidence -> not eligible.
- if sample count/search count is too low, return `INSUFFICIENT_EVIDENCE` rather than probing.

This prevents the app from doing recommendation-quality probes against every source.

### Scoring

Recommendation-quality score should include:

Positive:

- visible candidates from actual For You-like queries;
- high average candidate score;
- useful matched groups;
- Top Picks contribution;
- search success;
- later user Love/Like if available.

Negative:

- NoMatches;
- FilteredOut;
- Error;
- HiddenByDuplicateHandling;
- blocked-tag candidates;
- disliked/rated-hidden candidates;
- low metadata quality;
- very low confidence.

Use confidence adjustment. Do not let one result define the source forever.

Suggested formula shape:

```text
recommendationQuality =
    confidence *
    (
        visibleResultScore
        + candidateScore
        + matchedGroupScore
        + topPickScore
        + userPositiveScore
        - noisePenalty
        - errorPenalty
    )
```

Keep exact weights in a pure helper with tests.

### UI

In Source Evaluation past results:

- continue showing general source evaluation verdict;
- add compact recommendation-fit label when available:

```text
Recommendations: Great fit
Recommendations: Good
Recommendations: Mixed
Recommendations: No matches
Recommendations: Too little evidence
```

Do not clutter rows. A short subtitle is enough.

In Source Evaluation sort dropdown:

- consider adding "Recommendation quality" sort if the data exists.

In Sources To Try:

- if a source has recommendation-fit data, use it as stronger evidence than metadata-only suggestions;
- if no recommendation-fit data exists, keep existing labeling like "Install to test with For You."

### Installed Source Priority Suggestions

This pass may add a **suggestion-only** priority helper if it can be done safely:

```text
Suggest priority order
```

Rules:

- never auto-apply;
- never reorder silently;
- only suggest after enough recommendation-fit data exists;
- exclude disabled/disliked sources;
- keep manually disliked sources out;
- show concise reasons.

If this is too large, document as follow-up under Source Fit.

## Part 4: Repo Failure And Connectivity Handling

This should be included if small enough, because it is still deferred and directly affects Source Evaluation trust.

### Repo Failure Surfacing

Current deferred note:

```text
ExtensionApi.getExtensions() still returns emptyList() per repo on error; no UI.
```

Claude should inspect:

```text
app/src/main/java/eu/kanade/tachiyomi/extension/api/ExtensionApi.kt
app/src/main/java/exh/recs/evaluation/GetSourceEvaluationCandidates.kt
app/src/main/java/exh/recs/discovery/GetNonInstalledSourceSuggestions.kt
```

Desired behavior:

- distinguish "repo has zero candidates" from "repo failed";
- show non-blocking warning in Source Evaluation and/or Sources To Try;
- do not treat repo failure as proof there are no good sources;
- keep installed extension behavior unchanged.

If current API structure swallows failures too early, add the smallest safe result wrapper or document why this remains deferred.

### Mid-Run Connectivity Loss

Current state:

- offline start guard exists;
- per-extension timeouts exist;
- mid-run connectivity loss beyond timeout/error remains deferred.

Desired behavior:

- if connectivity drops during evaluation, visible message appears;
- current source may be marked as error/paused;
- user can retry without force-closing;
- For You/extension searches should not silently look empty if the problem is internet.

Do not implement a broad app-wide networking rewrite. Use existing utilities if present. Prefer small error-state improvements in Source Evaluation first.

## Part 5: Evidence Strings And Timestamps

This is polish but cheap and reduces stale-doc drift.

### Evidence Strings

Current limitation:

- `source_evaluation_evidence_*` string keys exist;
- labels are hardcoded in `SourceEvaluationScreen.kt`.

Hook the labels to string resources if straightforward.

### Timestamps

Source Evaluation rows already show "Last evaluated today / N days ago."

For Recommendation Settings source statuses:

- consider adding compact "Last checked" using existing `RecommendationSourceRunStatus.updatedAt`.
- only add if it does not clutter the source priority/status UI.

## Part 6: Tests

Add focused unit tests. Avoid relying only on manual tablet testing.

Required pure-helper tests:

```text
SourceEvaluationUpdatePolicyTest
SourceRecommendationFitEligibilityTest
SourceRecommendationFitScorerTest
```

Update existing tests if signatures change:

```text
SourceEvaluationCandidateFilterTest
SourceEvaluationResultListTest
RecommendationSourceRunStatusStoreTest
```

Possible DB/repository tests if project patterns exist:

```text
SourceRecommendationFitRepositoryImplTest
SourceEvaluationRepositoryImpl migration/upsert coverage
```

Minimum test cases:

- old evaluation with same version is not updated;
- old evaluation with lower versionCode is updated;
- old evaluation with missing version metadata is update-unknown and optionally eligible;
- never-evaluated extension is not included by updated-only reassessment;
- strong fit is recommendation-fit eligible;
- worth trying is recommendation-fit eligible;
- rejected/error are not eligible;
- explicit-heavy is not eligible when explicit block is enabled;
- recommendation quality score rewards visible high-score results;
- recommendation quality score penalizes no matches/errors/blocked-tag noise;
- low sample count becomes insufficient evidence;
- docs stale feature audit does not remove implemented reports.

Manual checks:

1. Existing evaluated extension with no version change is skipped by updated-only reassessment.
2. A known updated extension appears in updated-only count.
3. Updated-only reassessment does not include never-evaluated candidates.
4. General evaluation still works with Private installer.
5. Strong/Worth-Trying source can receive recommendation-fit label.
6. Weak/Error source does not trigger recommendation-fit probe.
7. Sources To Try still works when no fit data exists.
8. For You normal refresh still works.
9. Normal global search remains unchanged.

## Part 7: Documentation And Versioning Updates

When implemented, Claude must create:

```text
docs/recommendations/KMK_RECS_V0_6_21_SOURCE_EVALUATION_UPDATE_REASSESSMENT_AND_RECOMMENDATION_FIT_IMPLEMENTATION.md
```

Update:

```text
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/README.md
docs/recommendations/KMK_RECS_DEFERRED_FEATURE_MASTER_IMPLEMENTATION_PLAN.md
RECOMMENDATION_VERSIONING.md
app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt
```

Documentation must clearly list:

- what was implemented;
- what was intentionally deferred;
- whether Favorite other versions is confirmed implemented;
- whether alternate-query matching is confirmed implemented;
- whether source recommendation-fit probing is implemented for strong/worth-trying only;
- whether repo failure surfacing was implemented or remains deferred;
- whether mid-run connectivity handling was improved or remains deferred;
- test commands run;
- APK path if built.

## Expected User-Facing Summary

After implementation, the user should be able to understand:

- Source Evaluation can reassess only extensions whose version changed.
- General source quality and recommendation quality are now distinguished.
- Recommendation quality is only checked for promising evaluated sources, not every source.
- Better sources can be recommended or prioritized with clearer evidence.
- The system remains bounded and should not bog down the tablet by crawling everything.

## Recommended Implementation Order

1. Audit docs/code mismatch and correct stale deferred labels.
2. Add extension version metadata to source evaluations.
3. Add updated-extension detection helper and tests.
4. Add updated-only reassessment UI/action.
5. Add recommendation-fit eligibility helper and tests.
6. Add bounded recommendation-fit probe/scorer for Strong/Worth-Trying sources.
7. Surface recommendation-fit labels/sort where useful.
8. Add repo/connectivity handling only if it remains small and safe.
9. Hook evidence strings/timestamps if straightforward.
10. Update docs/versioning/release notes and run tests.

## Approval Summary

This plan is feasible, but it is bigger than a tiny bugfix. The core safe implementation is:

- updated-extension reassessment;
- recommendation-quality fit for Strong/Worth-Trying only;
- documentation cleanup for stale deferred items.

Repo failure surfacing, mid-run connectivity handling, and source priority suggestions are useful but can be trimmed if implementation becomes too large. The key rule is to keep the system bounded: first evaluate broadly but cheaply, then only perform recommendation-quality checks on sources that already have enough evidence to be worth trying.

