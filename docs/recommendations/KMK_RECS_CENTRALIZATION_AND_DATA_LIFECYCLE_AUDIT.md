# KMK Recommendation Centralization And Data Lifecycle Audit

Date: 2026-07-11

Status: design and code audit only. Do not implement from this document without a separately approved, focused implementation plan.

Purpose: identify the recommendation rules, data, and workflows that are currently siloed; define the minimal shared contracts that should unify them; and establish the implementation-plan standard required for future Claude Code work.

Companion documents:

- `KMK_RECS_COMPLETE_SYSTEM_EVALUATION_AND_IMPROVEMENT_AUDIT.md`
- `KMK_RECS_FOR_YOU_FILTERING_AND_PERFORMANCE_FEASIBILITY_AUDIT.md`
- `KMK_RECS_SOURCE_CATALOGUE_FIT_AND_RETRIEVAL_QUALITY_FEASIBILITY_AUDIT.md`
- `../community/KMK_APPLICATION_WIDE_EFFICIENCY_AND_STANDARDIZATION_AUDIT_PLAN.md`

---

## Core Decision

The recommendation system should be centralized around shared decisions and shared data contracts, not replaced with one universal search engine.

Different workflows have legitimate differences:

- For You discovers unfamiliar manga across installed sources.
- Source Evaluation assesses a temporarily installed source.
- Rated-group recommendations use one confirmed manga group as an additional seed.
- Cross-extension matching finds equivalent versions, not similar manga.
- Manga-detail recommendations use provider-specific recommendations.
- Best Version compares already matched versions at chapter/page level.

They should not share one network pipeline. They must share the same understanding of user taste, source eligibility, candidate visibility, alias normalization, evidence confidence, data retention, and error categories whenever those concepts mean the same thing.

---

## Current Silo Map

| Concern | Current implementations | Verified divergence | Required central contract |
| --- | --- | --- | --- |
| Taste affinity | `GetTasteProfile`, `PersonalRecommendationScorer`, `SourceEvaluationScorer`, `GroupSeedRecommendationScorer` | Source Evaluation ignores explicit positive tag preferences and aliases; group scoring adds seed weight separately | `TasteAffinity` with explicit, learned, alias, disliked, blocked, and optional seed components |
| Source selection | `RecommendationSourceFilter`, `RecommendationSourceOrdering`, `BrowsePersonalRecommendationsScreenModel`, `GroupSeededRecommendationsScreenModel`, `SameMangaCandidateSearcher` | Group recommendations use first five language-matching sources rather than priority/disabled policy | `RecommendationSourceSelector` with named policy options |
| Candidate visibility | `shouldHideForYou`, `GetKnownRecommendationMangaIds`, seen store, min-chapter filtering, group screen local checks | Group recommendations do not use the For You visibility/known/seen policy | `RecommendationCandidatePolicy` with typed filter reasons |
| Query planning | `RecommendationQueryPlanner`, `GenreFilterMapper`, `SourceEvaluationRunner.buildSearchQueries`, group seeded search, matching query planner | General evaluation makes raw text searches; For You/probe map filters and aliases; matching is title-based | Separate named planners for content discovery, source retrieval, and identity matching, all using shared tag normalization |
| Source evidence | `SourceEvaluation`, `SourceRecommendationFit`, `SourceFitStats`, non-installed scorer | Catalogue samples and targeted searches are pooled; operational success is called fit; pre-install name similarity is a different evidence type | `SourceEvidence` with provenance and distinct labels |
| Cross-source identity | link table, matching workflow, Loved duplicate grouper, Top Picks conservative work key | Confirmed links, exact author/title work keys, and title-query candidates use different confidence levels | `MangaIdentityConfidence` and explicit use rules |
| Caching/storage | recommendation cache, candidate memory, discovery progress, local manga database, source-fit preference serialization | Candidate discovery persists network manga; derived-state expiry/pruning is inconsistent | `DerivedRecommendationData` lifecycle registry |
| Error states | exception-specific helpers, generic `Error`, raw reason strings, queue diagnostics | Similar failure categories are represented as unrelated strings/enums | `RecommendationFailure` taxonomy |

---

## Shared Contract 1: Taste Affinity

### Current target symbols

- `domain/src/main/java/tachiyomi/domain/taste/interactor/GetTasteProfile.kt`
- `app/src/main/java/exh/recs/PersonalRecommendationScorer.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScorer.kt`
- `app/src/main/java/exh/recs/group/GroupSeedRecommendationScorer.kt`
- `app/src/main/java/exh/recs/sources/GenreFilterMapper.kt`

### Required invariant

For a candidate with usable tags, every feature that says it is evaluating personal content fit must resolve tags the same way:

1. normalize source tag;
2. resolve user aliases;
3. resolve small universal synonyms;
4. apply explicit preferred/disliked/blocked preferences;
5. apply learned rating weights;
6. report typed reasons and confidence.

### What must remain separate

- Source affinity is appropriate only where the candidate belongs to an already installed/observed source. It must not make an unknown source look good merely because another source was liked.
- Group seed similarity is a contextual boost for a specific Loved/Linked manga group. It must not silently alter the userâ€™s permanent profile.
- A blocked tag remains a hard exclusion in candidate display; a source-level catalogue report may record its share as evidence without treating every source containing one blocked title as permanently bad.

### Required output shape for future plans

Any new shared helper must return more than a raw number:

```kotlin
data class TasteAffinityResult(
    val score: Double,
    val blocked: Boolean,
    val matchedPreferredGroups: Set<String>,
    val matchedLearnedGroups: Set<String>,
    val matchedDislikedGroups: Set<String>,
    val missingMetadata: Boolean,
)
```

This is pseudocode, not approved production code. A future plan must use the repository's existing domain model naming and package conventions after inspecting the current tree.

---

## Shared Contract 2: Source Selection

### Current target symbols

- `app/src/main/java/exh/recs/RecommendationSourceFilter.kt`
- `app/src/main/java/exh/recs/RecommendationSourceOrdering.kt`
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt`
- `app/src/main/java/exh/recs/group/GroupSeededRecommendationsScreenModel.kt`
- `app/src/main/java/exh/recs/matching/SameMangaCandidateSearcher.kt`

### Required invariant

Every recommendation workflow must state its source-selection policy explicitly rather than taking a raw `getVisibleCatalogueSources()` list and applying an ad hoc subset.

Minimum policy inputs:

- selected languages;
- local-source inclusion/exclusion;
- disabled/disliked recommendation sources;
- saved manual priority order;
- maximum attempted sources;
- whether top-three boost applies;
- whether a workflow intentionally ignores user preference and why.

### Current verified violation

`GroupSeededRecommendationsScreenModel` calls `RecommendationSourceFilter.filterForRecommendations(...)` and takes five sources directly. It does not apply stored priority ordering or disabled/disliked source exclusions. This must be corrected before adding more group-recommendation behavior.

### Required exception policy

- Normal global search remains unbounded and retains current behavior. It is an exploration tool, not a personalized recommendation workflow.
- Cross-extension identity matching can use a broader source list, but its plan must state whether disabled recommendation sources are included and why. The user must not be surprised by that choice.
- Source Evaluation works on available, not-installed extensions and is not governed by installed-source priority.

---

## Shared Contract 3: Candidate Visibility and Filtering

### Current target symbols

- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt` (`shouldHideForYou`, chapter/known/seen filters)
- `domain/src/main/java/tachiyomi/domain/taste/interactor/GetKnownRecommendationMangaIds.kt`
- `app/src/main/java/exh/recs/memory/RecommendationCandidateMemoryRanker.kt`
- `app/src/main/java/exh/recs/group/GroupSeededRecommendationsScreenModel.kt`

### Required invariant

For personalized recommendations, a candidate passes only after the same ordered policy is applied:

```text
candidate returned by source
-> metadata sufficient for selected filters?
-> blocked tags
-> seen/explicitly suppressed
-> rated visibility setting
-> hide-known setting
-> status filter (when data exists)
-> chapter/update filter (only if selected and data is available)
-> score eligibility threshold
-> duplicate/identity policy
```

Each filter must return a reason code. This enables source diagnostics, cache correctness, and user explanations without scraping exception text or reconstructing logic in the UI.

### Important semantics

- Unknown metadata must fail open unless the user selected a strict filter explicitly requiring it.
- Seen is title-specific suppression, not a negative taste signal.
- Dislike is a negative taste signal and title suppression.
- Hide known must consistently cover rated/library/history state in every personalized flow.
- A minimum chapter setting based only on locally stored chapters must be labelled as such; it is not a universal remote chapter-count guarantee.

---

## Shared Contract 4: Source Evidence

### Current target symbols

- `app/src/main/java/exh/recs/evaluation/SourceEvaluationRunner.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScorer.kt`
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationFitProbe.kt`
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationFitScorer.kt`
- `app/src/main/java/exh/recs/discovery/NonInstalledSourceSuggestionScorer.kt`
- `app/src/main/java/exh/recs/SourceFitStats.kt`

### Evidence classes that must not be merged

| Evidence | What it can support | What it cannot support |
| --- | --- | --- |
| Name similarity | "This uninstalled source may be worth testing" | Catalogue fit or recommendation quality |
| Popular/Latest snapshot | Catalogue-surface fit to the user | Search/retrieval compatibility or publication date |
| Tag-search probe | For You retrieval compatibility | Website-native recommendations or whole-catalogue quality |
| For You operational stats | Reliability/availability | User satisfaction or content quality |
| Explicit later user rating | Personal satisfaction with an exposed manga | Broad genre rejection unless the rating is Dislike |
| Confirmed cross-source group | Same-work identity and richer seed metadata | Proof that a new title is equivalent |

### Required future persistence model

Future source-evaluation changes must preserve provenance:

```text
SourceCatalogueSnapshot
  source identity
  sampled candidate identity
  origin: popular/latest/both
  metadata coverage
  affinity summary
  blocked/negative shares
  evaluated at / source version / evaluation version

SourceRetrievalCompatibility
  source identity
  query-plan outcomes
  result metadata coverage
  positive-result share
  timeout/error classes
  evaluated at / evaluation version
```

Existing `source_evaluation` and `source_recommendation_fit` records should not be silently reinterpreted with new fields. A future plan must explicitly choose a migration/version strategy, mark current records stale, and provide reassessment.

---

## Shared Contract 5: Cross-Source Identity

### Current identities have different confidence

| Mechanism | Confidence | Appropriate use |
| --- | --- | --- |
| User-confirmed `manga_cross_source_link` | High | Group seed, group display, suppress already known exact versions |
| Exact normalized title + author/artist | Medium-high | Top Picks conservative display merge |
| Title/alternate-title search candidate | Low | Preselected but user-reviewable matching workflow |
| Fuzzy title/description similarity | Low-medium | Optional suggestion only, never automatic grouping/removal |

### Required invariant

Only high-confidence confirmed links may cause automatic cross-source grouping or cross-version rating/seen propagation. Lower-confidence matches may help rank or preselect a review list but must remain reversible user decisions.

This avoids the serious failure mode where unrelated works with similar titles disappear from the userâ€™s list.

---

## Derived Data Lifecycle

### Current data categories

| Data | Owner | Backup/sync | Current bound | Required policy |
| --- | --- | --- | --- | --- |
| Manga ratings, tag preferences, aliases, confirmed links | User-owned profile | Yes | Grows with user activity | Preserve, restore safely, allow reset/edit |
| Disabled recommendation sources | User-owned preference | Yes | Small | Preserve, expose management UI |
| Source priority/like/dislike/preferences | User preference | Preference backup behavior must be verified per key | Small but can become stale | Normalize and prune removed-source keys safely |
| Source Evaluation/retrieval results | Derived personal evidence | Local by current design | No explicit expiry beyond reassessment | Add source-version/evaluation-version expiry and targeted reset |
| For You cache | Derived | No | TTL 24 hours | Keep TTL/clear action |
| Candidate memory/progress | Derived | No | 500 memory entries per source; progress page cap | Add stale-source cleanup and retry policy |
| Localized network manga rows | Derived but written into main manga DB | No special separate lifecycle | No demonstrated bound | Measure and define retention before expanding discovery |
| OCR index | User-derived content index | Separate OCR policy | User-visible storage | Maintain explicit storage/reset control |

### Required direction

Do not build a full offline catalogue of every manga from every extension. The database should be primed only by bounded source snapshots and candidates that have a concrete purpose. Every derived data store needs:

- owner;
- key;
- maximum size;
- expiry/revalidation condition;
- cleanup trigger;
- backup/sync decision;
- user reset path;
- privacy/logging classification.

---

## Error and Capability Centralization

### Required capability record

Source behavior varies. A shared capability/evidence record should distinguish observed facts from assumptions:

- supports latest listing;
- supports a usable filter list;
- which selected tags mapped to source filters;
- list-card metadata coverage;
- detail metadata coverage;
- search reliability under bounded probes;
- known unsafe/quarantined state;
- last observed extension version and expiry.

Do not manually map every extension forever. Start with generic observation from the extension API and use source-specific adapters only where a high-value source genuinely warrants one.

### Required error taxonomy

All recommendation paths should map failures to a common internal category:

- offline;
- cancelled;
- timeout;
- source/network failure;
- unsupported capability;
- missing metadata;
- extension installation/load failure;
- quarantined/unsafe extension;
- malformed local data.

UI should show a concise category and retry action where meaningful. Logs can retain the original technical exception. New KMR strings belong in `i18n-kmk` base resources only.

---

## Performance Rules

### No unbounded candidate localization

`NetworkToLocalManga` persists results. Every workflow must cap raw candidates before localizing and must never localize a full source search page when only two or ten display candidates are possible.

### Shared dispatcher ownership

Long-lived helper classes must not silently create unmanaged fixed thread pools. A future audit/fix must inspect `SameMangaCandidateSearcher` and move its dispatcher ownership to an application-appropriate scope or a closeable lifecycle.

### Refresh modes, not hidden behavior changes

The user should be able to choose a bounded effort level:

- Faster: small source scope and minimal enrichment.
- Balanced: normal source scope and conservative discovery.
- Thorough: explicit user-requested wider exploration.

These modes must define source attempts, query plans, page probes, enrichment cap, timeout budget, and cache behavior. They should not be described as quality tiers unless measured.

---

## Required Implementation-Plan Contract for Claude Code

Every future implementation plan must include all sections below. A plan lacking any applicable section is incomplete.

### 1. Scope and non-goals

- Exact user problem and acceptance criteria.
- Explicit exclusions to prevent scope drift.
- Version family and target version.

### 2. Current code map

- Absolute repository-relative file paths.
- Exact class/object/function/property/SQL query symbols to read before editing.
- Current data/control flow with concrete conditions, caps, and preference keys.
- Existing tests and documentation that establish current behavior.

### 3. Target design

- New or changed models, fields, preference keys, SQL tables/columns/indexes, and their ownership.
- Function-by-function changes, including which helpers are reused and why.
- Pseudocode for nontrivial algorithms, with inputs, outputs, bounds, and cancellation behavior.
- UI location, states, actions, copy/resource ownership, accessibility, and screen restoration behavior.

### 4. Compatibility and lifecycle

- Migration number and SQLDelight requirements when schema changes.
- Evaluation/cache version invalidation.
- Backup, restore, sync, export/import, reset, retention, and cleanup behavior.
- Public/private build, application ID, signing, telemetry, and updater implications when relevant.

### 5. Reliability and security

- Every network call, dispatcher, timeout, retry/backoff, concurrency cap, and cancellation path.
- Failure taxonomy and UI fallback.
- Extension installer/cleanup/quarantine behavior when extensions are involved.
- Data sensitivity and logging/redaction decisions.

### 6. Verification

- Exact unit/integration/migration tests to add or adjust.
- Device scenarios required to validate what static tests cannot prove.
- Mandatory command sequence from `AGENTS.md`: Spotless, tests, assembly.
- Clear build handoff rule: do not create the final APK until every approved phase passes.

### 7. Documentation

- Implementation report path.
- Current-state, next-work, encyclopedia, security/database audit, release notes, and versioning updates required.
- Mark superseded plans as implemented/archived only after verification.

### 8. Claude stop conditions

Claude must stop and report rather than guess when:

- the actual code differs materially from the plan;
- a migration/backup/sync contract is unclear;
- a source API capability is not universal;
- a proposed change would require broad source-specific maintenance;
- a required test cannot be run or reproduces a regression.

---

## Next Audit Before Any New Feature Plan

The next focused audit should produce an exact target-code map for these first centralization fixes:

1. v0.7.39 discovery immediate-merge and retry semantics.
2. Shared source selector adoption by group-seeded recommendations.
3. Shared candidate visibility policy adoption by group-seeded recommendations.
4. Source Evaluation catalogue fit versus retrieval compatibility split.
5. Candidate/local-manga retention and raw-cap-before-localization review.

That audit should then be split into small implementation plans. It must not become one massive Claude prompt or one risky all-in-one APK.

