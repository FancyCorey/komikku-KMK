# KMK Recommendation Centralization Phase 1 Target-Code Audit

Date: 2026-07-11

Status: detailed audit only. This file identifies the exact code affected by the first centralization work. It is not an approved implementation plan.

Parent audit:

- `KMK_RECS_CENTRALIZATION_AND_DATA_LIFECYCLE_AUDIT.md`

## Objective

Prepare a safe, code-specific basis for future implementation plans. The immediate target areas are:

1. Correct v0.7.39 For You discovery merge and retry behavior.
2. Apply shared source selection to rated-group recommendations.
3. Apply shared candidate visibility to rated-group recommendations.
4. Split source catalogue fit from tag-search retrieval compatibility.
5. Bound candidate localization and define derived recommendation data lifecycle.

No implementation plan may combine all five into one unreviewed code pass. Items 1-3 can potentially form one focused compatibility release after detailed planning. Item 4 requires a schema/evaluation-version design. Item 5 requires storage measurement and a separate retention decision.

---

## A. For You Discovery Correctness

### Current code map

| File | Symbol / current location | Current responsibility |
| --- | --- | --- |
| `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt` | `refresh()` around line 183 | Starts a forced live load. |
| same | `load(forceRefresh)` around line 187 | Builds profile/source list and runs source batches. |
| same | `searchSource(...)` around line 369 | Cache/live search, scoring, memory/progress, source status. |
| same | `mergedRecommendations` branch around line 616 | Omits `additionalResults` when previous resolved memory is empty. |
| same | `discoverAdditionalPage(...)` around line 822 | Fetches one new page and records progress. |
| `app/src/main/java/exh/recs/memory/RecommendationDiscoveryPlanner.kt` | `nextPageToProbe()` around line 39 | Picks max evaluated page + 1, capped at 20. |
| `app/src/main/java/exh/recs/memory/RecommendationDiscoveryProgressStore.kt` | `recordProgress()` around line 22 | Persists all outcomes with a status string. |
| `data/src/main/sqldelight/tachiyomi/data/recommendation_discovery_progress.sq` | progress table / `getEvaluatedPagesBySourceQuery` | Progress is keyed by source, query signature, page. |

### Verified defects

#### A1. Immediate additional-page visibility defect

Current sequence in `searchSource`:

1. Search page 1 and create `recommendations`.
2. Call `discoverAdditionalPage` and obtain `additionalResults`.
3. Persist `additionalResults` into candidate memory.
4. Resolve memory loaded at the beginning of the method.
5. If that previously resolved memory list is empty, return only `recommendations`.

The just-fetched extra-page results are therefore not eligible for display until a future refresh. This violates the intended best-of-evaluated-pages behavior.

#### A2. Retry classification defect

`discoverAdditionalPage` catches all non-cancellation failures, assigns `STATUS_ERROR`, and calls `recordProgress`. `RecommendationDiscoveryPlanner` treats any stored page as permanently evaluated and advances to the next page.

This means a temporary DNS/network/server failure can skip a page indefinitely until manual discovery reset. The existing v0.7.39 implementation report says transient failures should be retried, but the code does not make that distinction.

### Required future implementation-plan decisions

- Define one merge function that accepts resolved remembered entries plus every current-refresh candidate before final score/visibility filtering. It must be used even when prior memory is empty.
- Define typed page outcomes, not free-form status strings alone: success, empty, filtered, duplicate-only, unsupported, retryable failure, permanent failure, exhausted.
- Define retry policy: maximum attempts, retry time/backoff, which exception classes are retryable, and what reset does.
- Preserve cancellation: `CancellationException` must always escape without recording an error/progress result.
- Decide whether page-one cache refresh and extra-page discovery are separate user actions or remain coupled; document the final budget.
- Update profile/query invalidation policy so changed taste, source extension version, source filter behavior, and stale progress are not conflated.

### Required tests

- First refresh with no memory: page-one and page-two candidates both compete for visible slots.
- Existing memory plus current page-one/page-two candidates: one final ranking, no duplicate rows.
- Retryable timeout: same page is retried only after backoff; it is not skipped permanently.
- Unsupported/permanent failure: page is not retried continuously.
- Cancellation: no progress row is written after cancellation.
- Profile/source-version change: documented re-evaluation behavior.

---

## B. Shared Source Selection for Rated-Group Recommendations

### Current code map

| File | Symbol / current location | Current responsibility |
| --- | --- | --- |
| `app/src/main/java/exh/recs/RecommendationSourceFilter.kt` | `filterForRecommendations()` around line 28 | Language and Local Source filtering. |
| `app/src/main/java/exh/recs/RecommendationSourceOrdering.kt` | `apply()` around line 30 | Manual source ordering and disabled-source exclusion. |
| `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt` | `load()` around lines 215-236 | Correctly combines language filter, stored order, disabled and disliked sources. |
| `app/src/main/java/exh/recs/group/GroupSeededRecommendationsScreenModel.kt` | `load()` around line 81 | Builds rated-group seed and selects sources. |
| same | `eligibleSources` around line 115 | Calls language/local filter, then takes first five raw visible sources. |

### Verified divergence

The group flow does not call `RecommendationSourceOrdering.apply()`. It does not read source priority, disabled recommendation sources, or disliked installed source preferences. It also passes `emptySet()` to the language normalizer, which resolves to the default language behavior rather than the user's current setting.

### Required future implementation-plan decisions

- Introduce a source selector with a small typed policy object or equivalent existing-project pattern.
- The selector must return both included sources and exclusion diagnostics for settings/debug use.
- For You policy: current recommendation languages, Local Source excluded, disabled/disliked sources excluded, saved priority applied, boost data available, maximum attempts configurable by refresh mode.
- Rated-group policy: same user language/priority/disabled/disliked rules as For You, but a smaller fixed source cap and no requirement to show 20 source rows.
- Matching policy: explicitly decide whether it includes disabled recommendation sources. It must not accidentally inherit one policy without documentation.
- The selector must be source-manager live: extension install/uninstall changes must re-evaluate the list, rather than storing stale source objects.

### Required tests

- Group flow honors priority ordering.
- Group flow excludes disabled and disliked sources.
- Group flow honors the user's selected recommendation languages.
- Local Source remains excluded where appropriate.
- A source removed during a session is skipped safely.

---

## C. Shared Candidate Visibility for Rated-Group Recommendations

### Current code map

| File | Symbol / current location | Current responsibility |
| --- | --- | --- |
| `BrowsePersonalRecommendationsScreenModel.kt` | `shouldHideForYou()` around line 104 | Rated visibility rule. |
| same | live candidate filtering around line 494 | Favorite/rated/seen/local chapter/known filters. |
| same | cached filtering around line 690 | Reapplies visibility/seen/known filters. |
| same | extra-page filtering around line 867 | Reapplies primary filters. |
| `domain/.../GetKnownRecommendationMangaIds.kt` | `await(...)` | Local known/library/history lookup. |
| `GroupSeededRecommendationsScreenModel.kt` | `buildRecommendations()` around line 136 | Localizes and scores candidates, but does not use shared visibility filters. |

### Verified divergence

Group recommendations only reject blocked tags and non-positive scores. They can show candidates that the user marked seen, has already rated, has in their library/history, or excluded through current For You visibility choices.

### Required future implementation-plan decisions

- Extract a candidate-visibility evaluator from the For You screen model into a testable, source-independent helper/interactor boundary following Komikku conventions.
- The evaluator must return typed filter reasons, not only `Boolean`, so diagnostics and cache policy are consistent.
- It must accept explicit context: `forYou`, `ratedGroup`, or another named purpose. Any difference must be an intentional policy value, not duplicated filtering code.
- Preserve fail-open behavior for database lookup failure only where current For You does so, and surface an internal warning/log.
- Chapter/status/date rules must be opt-in and data-availability-aware; no implied universal remote data lookup.
- Group seed members must always be excluded independently of normal visibility.

### Required tests

- Seen, rated, favorite, history/known, blocked-tag, and seed-member exclusions.
- Each `RatedMangaVisibility` mode.
- Failure of known-manga lookup preserves current documented fail-open behavior.
- Same candidate receives the same verdict in For You and rated-group context when their policies are equal.

---

## D. Source Catalogue Fit Versus Retrieval Compatibility

### Current code map

| File | Symbol / current location | Current responsibility |
| --- | --- | --- |
| `SourceEvaluationRunner.kt` | `probeAndScore()` around line 396 | Collects Popular, Latest, and search samples in one pooled list. |
| same | Popular probe around line 416 | Fetches 15 list cards. |
| same | Latest probe around line 439 | Fetches 10 list cards when supported. |
| same | search probe around line 464 | Runs up to three raw top-tag searches. |
| same | `buildSearchQueries()` around line 579 | Builds text queries from positive learned weights only. |
| `SourceEvaluationScorer.kt` | `score()` around line 45 | Scores pooled tags/titles; lacks aliases/explicit preferred tags. |
| same | `likedTitleMatchCount = 0` around line 109 | Persists an unimplemented signal. |
| `SourceRecommendationFitProbe.kt` | `probe()` around line 69 | Uses For You-style mapped filters and candidate scoring. |
| `SourceRecommendationFitScorer.kt` | `score()` | Maps search outcomes to the UI's recommendation-quality label. |
| `data/.../source_evaluation.sq` | `source_evaluation` | Current pooled evidence storage. |
| `data/.../source_recommendation_fit.sq` | `source_recommendation_fit` | Current retrieval probe storage. |

### Required target semantics

#### D1. Catalogue fit

Question answered:

> Does this source's natural Popular/Latest catalogue surface contain enough manga aligned with the user's taste to be worth installing or prioritizing?

Inputs:

- deduplicated Popular and Latest page-one sample;
- sample origin flags;
- bounded detail enrichment only until minimum tag metadata coverage is reached;
- canonical alias-aware taste-affinity result;
- metadata/status coverage;
- blocked/negative/positive proportions.

It must not include targeted search results in the catalogue-fit score.

#### D2. Retrieval compatibility

Question answered:

> Can Komikku's current For You query planner retrieve suitable candidates from this source?

Inputs:

- two bounded For You-style query plans;
- filter-mapping outcome;
- raw/result metadata coverage;
- candidate affinity outcome;
- source failure/timeout/unsupported capability outcome.

It must not be labelled source-native recommendation quality.

### Required schema and migration decisions

A future detailed plan must decide whether to:

1. add versioned/provenance columns to `source_evaluation` and rename its semantics; or
2. add a new `source_catalogue_fit` table and preserve legacy `source_evaluation` records as historical/obsolete.

The plan must include:

- a new migration number after the current latest migration;
- SQLDelight query file, indexes, mapper, repository interface/implementation, domain model, interactor, and Injekt binding;
- evaluation-version increment from `SourceEvaluationKeys.CURRENT_VERSION`;
- stale-record handling and explicit reassessment UI;
- reset/delete behavior;
- no accidental backup/sync inclusion unless user-owned evidence is deliberately promoted to profile data.

### Required tests

- Popular-only, Latest-only, both-origin, and duplicate-origin samples.
- Explicit preferred tags and aliases affect catalogue affinity.
- Sparse/no-tag samples return insufficient evidence, not a poor source verdict.
- Strong retrieval with weak catalogue cannot become Strong Catalogue Fit.
- Strong catalogue with weak retrieval remains install-worthy but reports weak retrieval compatibility.
- Explicit/ecchi classification remains separate from taste fit.
- Old pooled records are marked stale after evaluation-version change.

---

## E. Candidate Localization and Derived Data Lifecycle

### Current code map

| File | Symbol / current location | Current responsibility |
| --- | --- | --- |
| `domain/.../NetworkToLocalManga.kt` | `invoke(List<Manga>)` | Inserts network manga through `MangaRepository.insertNetworkManga`. |
| `BrowsePersonalRecommendationsScreenModel.kt` | live and additional-page paths | Localizes many For You candidates before final display. |
| `SameMangaCandidateSearcher.kt` | fixed dispatcher around line 36 | Creates a fixed thread pool per searcher instance. |
| same | `search()` around line 57 | Launches an async task for every matching source. |
| same | `searchOneSource()` around line 74 | Localizes all resolved results before per-source cap check. |
| `recommendation_candidate_memory.sq` | candidate memory | 500-entry per-source pruning only. |
| `recommendation_discovery_progress.sq` | discovery progress | no expiry/stale source cleanup query. |

### Required future decisions

- Establish a raw-result cap before every localization call in matching, group, and For You workflows.
- Decide whether `NetworkToLocalManga` is required for all display candidates or whether a transient result model can avoid DB insertion until user interaction. Do not change this without confirming navigation and manga-detail expectations.
- Define how orphaned/non-library network manga rows are retained or pruned. This needs measurement first; do not add a destructive cleanup based on assumptions.
- Replace unmanaged helper-owned executor creation with a lifecycle-safe dispatcher arrangement conforming to project patterns.
- Add per-source and overall timeout/cancellation bounds to same-manga matching.
- Add stale-source cleanup for candidate memory/progress and source-fit preference data after extension uninstall/repository removal.

### Required measurement before implementation

- Database row growth after 10, 50, and 100 For You refreshes with a representative extension set.
- Database row growth after same-manga matching and group-seeded recommendation workflows.
- Duration, memory, CPU, and cancellation time on the user's tablet and a midrange phone.
- Source search failures, timeout distribution, and retry behavior.

---

## Future Plan Split

The next implementation plans must be separate documents in this order:

1. `For You discovery correctness and candidate visibility centralization`.
2. `Rated-group source selection and visibility alignment`.
3. `Source catalogue fit and retrieval compatibility semantic migration`.
4. `Recommendation derived-data retention, localization bounds, and matching dispatcher lifecycle`.
5. `For You controls, explainability, and measured effort presets` only after the above are verified.

Each plan must satisfy the implementation-plan contract in `KMK_RECS_CENTRALIZATION_AND_DATA_LIFECYCLE_AUDIT.md`.

## Approval Gate

Before the first implementation plan is written, decide whether Phase 1 should be limited to For You/rated-group correctness, or whether source-evaluation semantics should be planned in parallel as a separate Claude phase. The source-evaluation migration is broader and should not delay correcting visible For You defects.

