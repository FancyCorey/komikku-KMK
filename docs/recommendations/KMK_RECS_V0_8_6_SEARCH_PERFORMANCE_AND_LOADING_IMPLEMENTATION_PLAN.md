# KMK-Recs v0.8.6 Search Performance And Loading Implementation Plan

**Status:** Implemented and verified in code (2026-07-15), with explicit manual-QA caveats — not
"shipped" in the sense of device-verified. Budget policy, load-context enum, bounded (4-permit)
concurrency + 20s per-row timeout (extracted into a tested `GroupPreviewLoadCoordinator`),
cancellation/fatal-error correctness fix, `screenModelScope` lifecycle fix, a generation-token
staleness guard (extracted into a tested `GenerationGuard`), the GROUP_PREVIEW cache (wired into the
load path — lookup before fetch, store after budget truncation), lightweight diagnostics logging, and
the "Initial results per extension" settings UI are all implemented, unit-tested (104 test result
files in the `exh.recs` package pass with 0 failures/errors, including this feature's new
`GroupPreviewBudgetPolicyTest` (8), `GroupPreviewCacheTest` (6), `GroupPreviewLoadCoordinatorTest`
(6, including a shared-enrichment-semaphore proof) + `GenerationGuardTest` (3) in the same file,
`GroupPreviewVisibilityFingerprintTest` (11), and `RecommendationLoadContextTest` (1)), and verified
via `spotlessCheck`, full `:app:testDebugUnitTest`, and `assembleDebug`. A post-implementation
reviewer audit of the actual code (not just the self-report) additionally found and fixed two real
correctness bugs: nested enrichment was bounded per source instance only (up to 40 concurrent detail
requests possible instead of one shared budget — fixed with a shared `Semaphore` passed down from
`GroupPreviewLoadCoordinator`), and the GROUP_PREVIEW cache key was missing disabled-source/source-
order/source-quality/seen/taste state (fixed with a new, directly-tested
`GroupPreviewVisibilityFingerprint`). Both fixes are verified and covered by new tests. Two items remain
open by design/finding, not oversight: (1) `RecommendationQueryPlanner`/`RecommendationQueryAttemptPolicy`
were audited and found to already satisfy the plan's dedup/fallback requirements, so they were left
unmodified; (2) `RecommendsScreenModel` itself still has no direct unit test coverage (it requires
mocking ~10 Injekt dependencies with no existing harness) — the concurrency/timeout/generation logic
it delegates to is tested in isolation via the extracted coordinator/guard classes instead, per an
explicit scope decision documented in the implementation report. Device/manual QA (phone/tablet
layout, real network conditions, on-device budget verification) was not performed — no physical
device was available in this environment; see
`docs/recommendations/KMK_RECS_V0_8_6_SEARCH_PERFORMANCE_AND_LOADING_IMPLEMENTATION.md` for the
complete, itemized list of what remains manual-QA-only.

**Target line:** KMK-Recs v0.8.x, next release v0.8.6 after the currently shipped v0.8.5 work.

**Scope:** Recommendation preview loading, group recommendation loading, result-budget configuration, cancellation, bounded concurrency, cache reuse, and general search-performance hardening.

**Primary source tree:** komikku-source/

**Build handoff:** The final APK must be produced only after code, tests, documentation, and verification are complete, then copied to C:\Users\USER\Downloads\Komikku\private\Komikku-v1.13.6-kmk.8.6-debug.apk. Internal build-channel terminology must not appear in user-facing strings or What's New.

## 1. Objective

Group recommendation searches currently multiply work across eligible sources, query fallbacks, and detail enrichment. The apparent exponential slowness is a request fan-out problem:

    eligible sources x query attempts x detail requests x UI/state updates

This plan makes the work bounded, cancellable, progressive, cache-aware, and explainable. It must improve first-result time without weakening recommendation quality or changing normal global search.

The implementation must:

1. Show a configurable initial preview from each recommendation source, defaulting to 10.
2. Allow the user to open a source row and continue loading more through existing paging.
3. Bound source-level and nested enrichment concurrency.
4. Cancel stale work when leaving, refreshing, or changing the recommendation seed.
5. Isolate source failures, timeouts, malformed responses, and unsupported filters.
6. Preserve For You ranking/filter semantics unless a measured optimization is behavior-neutral.
7. Leave normal global search uncapped and behavior-compatible.

## 2. Mandatory preflight

Before editing, Claude must read AGENTS.md, docs/recommendations/DOCUMENTATION_RULES.md, the encyclopedia/index, CURRENT_STATE.md, NEXT_WORK.md, and the latest v0.8.5 implementation report.

Then verify these live paths and symbols:

- app/src/main/java/exh/recs/RecommendsScreenModel.kt
- app/src/main/java/exh/recs/sources/RecommendationPagingSource.kt
- app/src/main/java/exh/recs/sources/CrossExtensionGenreSearchSource.kt
- app/src/main/java/exh/recs/BrowseRecommendsScreenModel.kt
- app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt
- app/src/main/java/exh/recs/ForYouResultBudgetPolicy.kt
- app/src/main/java/exh/recs/RecommendationQueryPlanner.kt
- app/src/main/java/exh/recs/RecommendationQueryAttemptPolicy.kt
- app/src/main/java/exh/recs/sourceeval/SourceRecommendationFitProbe.kt
- app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/globalsearch/SearchScreenModel.kt

Record any discrepancy in the v0.8.6 implementation report. Do not guess line numbers or create a parallel helper before searching for an existing equivalent.

## 3. Existing behavior to preserve

### 3.1 Group recommendations

RecommendsScreenModel currently builds group seeds from confirmed linked versions, loads aliases and visibility state, selects eligible sources through the existing source policy, creates up to 20 cross-extension rows, and starts row requests concurrently. CrossExtensionGenreSearchSource performs the query attempt chain, title fallback, and detail enrichment. RecommendationPagingSource owns row creation and paging.

Preserve group titles, tags, aliases, source order, language filters, disabled-source filtering, quality dislikes, seen/known/rated/favorite visibility, minimum chapter filtering, scoring, and dedupe behavior.

### 3.2 For You

BrowsePersonalRecommendationsScreenModel already has bounded source attempts, result budgets, cache fingerprints, progressive updates, and source priority. ForYouResultBudgetPolicy already owns its supported values and boosted top-three behavior. Audit these paths and reuse safe helpers, but do not make group-preview settings silently alter For You.

### 3.3 Normal global search

SearchScreenModel owns a cancellable search job and explicitly supports an uncapped result contract. The new recommendation preview cap must never be added to SearchScreenModel, global search adapters, or normal exploration results. The five-results-per-extension rule discussed earlier applies only to the matching workflow; this plan's ten-result rule applies only to recommendation previews.

## 4. Explicit load contexts

Introduce one small context/policy model, following local naming conventions, rather than Boolean flags:

    FOR_YOU
    GROUP_PREVIEW
    FULL_SOURCE

FOR_YOU keeps the current behavior. GROUP_PREVIEW uses the new initial budget. FULL_SOURCE uses existing source paging and must not inherit the preview cap. Normal global search is outside this model and remains uncapped.

## 5. Configurable group-preview budget

First determine whether an existing preference has exactly the same meaning. Do not overload the For You row budget if that would couple unrelated UI behavior.

Preferred behavior:

- new narrowly scoped group-preview initial-result preference;
- default 10;
- supported bounded values 5, 10, 15, 20, and 30 unless existing project conventions require another set;
- invalid and migrated values normalize safely to the default;
- setting appears near recommendation result controls;
- copy explicitly says this controls the initial preview and that opening a source continues loading;
- use existing preference, backup, sync, and KMR/i18n conventions;
- no database migration unless the current preference architecture requires one.

Create or extend one pure RecommendationPreviewBudgetPolicy. It owns supported values, default, clamping, preview candidate budget, enrichment budget, and expansion eligibility. Do not repeat numeric validation in Compose, screen models, and source classes.

The cap must be applied in this order:

1. Fetch the bounded provider page already supported by the extension.
2. Normalize and remove exact duplicate URLs within that source.
3. Apply existing visibility policy.
4. Apply existing scoring.
5. Retain the top preview budget for the initial row.
6. Enrich only the candidates necessary for the preview, subject to the shared request budget.
7. Keep full paging available for the expanded view.

Do not blindly take the first provider items before scoring if that would hide better matches later in the returned page.

## 6. Query strategy and heavy tags

Retain current fallback behavior for recall. Improve efficiency by:

- normalizing and deduplicating identical query plans;
- executing the primary plan first;
- using fallbacks only when the prior plan has no usable candidates or an explicitly classified unsupported-filter response;
- retaining title fallback but its existing maximum;
- never retrying the same plan indefinitely;
- preserving cancellation;
- keeping alias-aware and group-aware scoring;
- avoiding combinatorial tag-pair generation;
- using the existing maximum tag counts.

Preview mode may use a smaller query-effort budget, but it must remain a closest-match search, not an absolute tag-match requirement. Full-source mode retains the complete recall-oriented chain.

## 7. Bounded concurrency

The current row-level async fan-out and nested detail enrichment must use structured bounded concurrency.

Required starting design:

- retain up to 20 eligible sources;
- permit at most 4 active GROUP_PREVIEW source operations at once;
- use a shared Semaphore or limitedParallelism(4), verified against local conventions;
- do not create an unbounded dispatcher per source;
- update rows progressively as each source finishes;
- release permits in finally blocks;
- ensure nested enrichment is included in the total request budget, not independently multiplied per row;
- reuse existing SourceRecommendationFitProbe and source-evaluation timeout/concurrency patterns.

Single-manga recommendations must remain compatible. Full-source paging can use existing behavior but still needs request cancellation and per-request timeouts.

## 8. Timeouts and isolation

Add explicit per-source group-preview bounds where missing, following existing project values after verification. Intended starting bounds are 20 seconds for one preview source operation and 10 seconds for one detail request.

Required behavior:

- timeout becomes a row status;
- unsupported filters fall through once;
- malformed responses affect only that source;
- one exception never cancels siblings;
- CancellationException is rethrown/propagated, never rendered as an error row;
- fatal VM errors are not caught as ordinary recoverable errors;
- no retry loop continues after cancellation;
- stale results from an old seed or refresh generation are ignored.

Use supervisorScope or equivalent structured isolation. Do not add GlobalScope, a detached process-wide queue, or an always-running service.

## 9. Lifecycle and progressive state

RecommendsScreenModel needs an explicit load job/generation model:

1. A new load cancels the previous load.
2. Leaving the screen cancels in-flight preview work.
3. Opening a full source cancels or safely supersedes the relevant preview work.
4. New seed, rating group, language, source order, visibility preference, or preview-budget changes invalidate the generation.
5. State updates verify coroutine activity and generation before mutation.
6. Refresh replaces rather than appends stale rows.
7. Loading, empty, timeout, error, and cancelled states remain distinct.
8. Completed rows render immediately instead of waiting for all sources.

## 10. Cache and duplicate-work reduction

Before adding a table, inspect existing recommendation caches. If they cannot safely key a group seed, use a bounded in-memory GROUP_PREVIEW cache for this version.

Cache key must include:

- stable confirmed-group fingerprint;
- source ID;
- recommendation language;
- normalized seed titles/tags;
- visibility-policy fingerprint;
- preview budget;
- query-policy version.

Cache only normalized preview data and minimal status. Use a short TTL, bounded entry count, oldest-entry eviction, and invalidation on refresh, taste/group change, language/source-order change, disabled-source change, or process death. Never cache cookies, credentials, full HTML, or private reader content. Do not mix group, For You, and global-search caches.

Audit For You cache fingerprints to confirm all ranking-affecting inputs are present. Preserve rolling discovery: a new batch must not discard a better prior candidate solely because it came from an earlier batch.

Within one source request, exact URL duplication may be removed. Do not globally merge same-title entries across sources.

## 11. UI requirements

Add compact, phone-safe UI:

- setting label: Initial results per extension;
- supporting copy: controls the first preview; open the source to continue;
- default visibly resolves to 10;
- change applies on next load and invalidates group preview cache;
- each source row shows its own loading, empty, timeout, or error state;
- retry uses existing patterns;
- source row header/arrow is the discoverable expansion action; do not require long-press;
- full-source view preserves current navigation/paging;
- use existing typography, spacing, icons, and KMR strings;
- no always-visible row of text buttons;
- no internal build-channel terminology in app-visible strings.

Normal global search must not show, inherit, or mention this setting.

## 12. Diagnostics

Add lightweight local diagnostics using the existing logging abstraction:

- load context;
- source ID/name only if permitted by current debug logging;
- cache hit/miss;
- query-plan count and plan types;
- candidates before/after visibility and scoring;
- enrichment count;
- timeout/cancellation/error category;
- per-source and total elapsed time;
- maximum observed source/enrichment concurrency.

Do not log cookies, authorization headers, full HTML, or private query parameters by default.

## 13. Exact implementation sequence

### Phase A: preflight and policy

- verify all symbols and existing helpers;
- document the architecture discrepancy, if any;
- implement the pure budget policy and context model;
- wire the preference through existing storage and settings;
- add policy, clamping, persistence, and context-separation tests.

### Phase B: source/query execution

- pass GROUP_PREVIEW policy through RecommendationPagingSource;
- make CrossExtensionGenreSearchSource preview-aware;
- retain group metadata and alias scoring;
- bound candidate/enrichment work in the correct order;
- deduplicate plans and preserve fallbacks;
- add timeout/error classification;
- ensure FULL_SOURCE does not inherit the preview limit.

### Phase C: coordinator/lifecycle

- replace row-wide unbounded async fan-out with the four-permit structured coordinator;
- include nested enrichment in the request budget;
- add generation tokens and cancellation;
- preserve progressive state updates;
- verify no hidden work remains after navigation or replacement.

### Phase D: cache/diagnostics/UI

- reuse or add bounded group-preview cache;
- add invalidation/key/TTL tests;
- add safe diagnostics;
- add setting, row status, and expansion UX;
- keep phone layouts compact.

### Phase E: release documentation and verification

- update KmkRecsReleaseNotes.kt to v0.8.6 and add a complete v0.8.6 What's New entry while preserving every existing v0.8.x entry;
- update docs/recommendations/CURRENT_STATE.md, NEXT_WORK.md, README.md, the encyclopedia/index, and create the v0.8.6 implementation report;
- link the plan to the report after implementation;
- build only after all work is complete.

## 14. Required tests

Add/update tests under app/src/test/java/exh/recs/ for:

- default, supported, invalid, migrated preview budgets;
- no cap in normal global-search context;
- query-plan normalization and bounded fallback;
- visibility/scoring before preview truncation;
- full-source expansion beyond preview budget;
- cache key, TTL, eviction, and invalidation;
- maximum active source operations;
- maximum nested enrichment operations;
- progressive completion when one source is slow;
- sibling isolation on timeout, malformed response, and exception;
- cancellation propagation;
- stale-generation protection;
- title fallback maximum and no duplicate plans.

Regression coverage must verify For You budgets/boosting/cache, group visibility and aliases, single-manga recommendations, ordinary source browsing, and uncapped global search.

Run from komikku-source using the repository-approved commands, at minimum:

    ./gradlew spotlessCheck
    ./gradlew :app:testDebugUnitTest
    ./gradlew assembleDebug

Record the exact results. Device QA must cover phone and tablet, budgets 5/10/20/30, heavy tags, repeated refresh, leaving during load, network loss/recovery, expanded source paging, and global search.

## 15. Security, stability, and non-goals

Do not add permissions, Shizuku behavior, installer behavior, extension evaluation, or background crawling. Do not add a database table solely for transient previews. Do not weaken explicit-source blocking, disliked-source handling, candidate visibility, or recommendation scoring. Do not globally deduplicate manga by title. Do not make source speed equal source quality. Do not catch fatal VM errors as recoverable source errors.

This plan does not cap normal global search, rewrite the scoring model, reduce the eligible-source count as a substitute for concurrency control, remove recall fallbacks, or change For You semantics.

## 16. Acceptance criteria

The work is complete only when:

1. Group previews show at most the configured initial number per extension, default 10.
2. Opening a source can continue to full results.
3. Normal global search remains uncapped.
4. Source and nested enrichment concurrency have a measured upper bound.
5. Rows render progressively and one slow source cannot block siblings.
6. Navigation, refresh, seed changes, and connectivity changes cannot leave stale work mutating state.
7. Heavy-tag plans do not repeat or retry forever.
8. Cache reuse reduces repeat work without cross-context contamination.
9. UI works on phone and tablet.
10. Tests, build output, diagnostics, and manual QA are recorded.
11. v0.8.6 documentation and What's New are accurate and prior v0.8.x history remains intact.
12. The final APK is copied to C:\Users\USER\Downloads\Komikku\private\Komikku-v1.13.6-kmk.8.6-debug.apk.

## 17. Claude handoff contract

Treat this document as an implementation contract, not a summary. Verify every referenced symbol against the live tree, follow AGENTS.md and official Komikku/Mihon conventions, reuse existing helpers, and document any unavoidable deviation. You may work in internal ordered phases, but do not produce the final APK until all code, tests, documentation, static checks, build checks, and device-QA notes are complete. Report changed files, exact verification commands/results, residual manual-QA limitations, final version metadata, and the exact APK path.
