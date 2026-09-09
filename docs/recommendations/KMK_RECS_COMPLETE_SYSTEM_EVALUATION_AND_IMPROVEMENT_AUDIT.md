# KMK Recommendations Complete System Evaluation And Improvement Audit

Date: 2026-07-11

Status: complete read-only evaluation. This is not an implementation plan and does not authorize code changes.

Scope: the whole KMK recommendation-related surface, including manga-detail providers, Browse > For You, Top Picks, taste/rating data, source ordering, non-installed suggestions, temporary Source Evaluation, retrieval-quality probes, rated-group recommendations, cross-extension matching, caches, persistence, backup/sync, error handling, and tests.

Related focused audits:

- `KMK_RECS_FOR_YOU_FILTERING_AND_PERFORMANCE_FEASIBILITY_AUDIT.md`
- `KMK_RECS_SOURCE_CATALOGUE_FIT_AND_RETRIEVAL_QUALITY_FEASIBILITY_AUDIT.md`
- `CURRENT_STATE.md`
- `NEXT_WORK.md`

---

## Executive Assessment

The system has grown into several useful but only partly coordinated recommendation products. Its strongest foundation is local, user-controlled taste data: ratings, explicit tag preferences, blocked tags, seen state, source priorities, and confirmed cross-source links. Its weakest point is source intelligence: the app currently uses different evidence and different rules to decide that a source is a good catalogue, a good For You source, a good non-installed suggestion, or a good source-native recommender.

The result is understandable but misleading behavior:

- a source can be labelled Strong Fit because a targeted search returned matching tags even if its normal Latest/Popular catalogue is unsuitable;
- a source can display `Recommendations: Good` even though the app only measured Komikku's tag-search retrieval, not the website's own recommendations;
- a non-installed source can be suggested initially because its name resembles an installed source name, which is intentionally conservative but not content evidence;
- the For You feed can be slow or crowded because one manual refresh can fan out to many sources, pages, enrichment calls, and local database inserts;
- different recommendation entry points do not consistently honor the same source ordering, disabled-source policy, visibility rules, or deduplication strategy.

The right next step is not a larger scorer. It is an evidence model with clear boundaries:

1. Personal taste model: what the user likes, dislikes, blocks, has seen, and knows.
2. Source catalogue fit: whether Popular/Latest samples from a source match that taste.
3. For You retrieval compatibility: whether Komikku can reliably obtain suitable results through that source's filter/search API.
4. Candidate ranking: how a returned manga compares to the taste profile and selected filters.
5. Local observed outcomes: whether candidates from a source later receive positive or negative explicit user feedback.

Each layer should be visible, bounded, and independently named. No single layer should silently stand in for another.

---

## System Map

| Subsystem | Primary purpose | Current evidence | Main concern |
| --- | --- | --- | --- |
| Manga-detail recommendations | Find manga related to the currently viewed title | Tracker/provider results and cross-extension genre search | Separate from For You; quality/semantics differ by provider. |
| For You | Personal multi-source discovery feed | Ratings, tags, aliases, source priority, source search results, candidate memory | Candidate discovery is expensive and source-dependent. |
| Top Picks | Consolidated row from For You rows | For You results, conservative duplicate merge, occurrence/boost bonuses | Does not independently verify quality; representative source can be arbitrary. |
| Rated-group recommendations | Find manga like a Loved/Liked/Disliked linked group | Whole linked group metadata plus personal taste | Does not fully share For You source/visibility policy. |
| Cross-extension matching | Let the user identify the same manga across sources | Title variants and user confirmation | Bounded, but broad source fan-out and DB localization need scrutiny. |
| Source Evaluation | Determine whether an uninstalled source may suit the user | Popular, Latest, and targeted-search samples | Evidence origins are pooled, so catalogue fit is not isolated. |
| Retrieval-quality probe | Test whether For You-style tag queries work for a source | Up to two tag searches and bounded detail enrichment | Mislabelled as recommendation quality. |
| Sources To Try | Suggest uninstalled extensions | Name similarity, user preference, Source Evaluation verdict | Pre-evaluation suggestions are metadata/name-based, not content-based. |
| Source fit statistics | Show past For You operational success | Shown/no-match/error counts, Top Picks contribution | Measures availability, not user satisfaction or semantic quality. |

---

## What Is Working Well

### User ownership and local data model

- Manga ratings are persisted as a single row per manga ID. A new Love, Like, or Dislike overwrites the previous rating rather than creating overlapping categories.
- Batch rating of confirmed other versions uses the same `SetMangaTasteBatch` path.
- Seen/read behavior is distinct from dislike: seen manga are suppressed without becoming a negative genre signal.
- Explicit tag preferences, disliked tags, blocked tags, and aliases provide a genuinely useful local personalization basis.
- Taste, tags, aliases, disabled recommendation sources, and cross-source link groups are included in backup and sync paths. Derived candidate caches and discovery progress correctly remain local-only.

### Safety and boundedness around extensions

- Source Evaluation handles temporary extensions one at a time, uses timeouts, cancellation, connectivity checks, cleanup policy, crash markers, and unsafe-extension quarantine.
- The main evaluator isolates source failures so a single failure does not automatically abort the whole batch.
- Explicit/hentai and ecchi handling are distinct concepts in the model.
- Non-installed suggestions preserve the original extension identity for installation rather than constructing synthetic extension records.

### Conservative identity handling

- Cross-source matching allows the user to confirm potential equivalents instead of assuming that similar titles are identical.
- Cross-source link groups are persisted and can seed group recommendations.
- Top Picks only merges cross-source works when normalized title plus exact author or artist agrees. This avoids the dangerous title-only automatic merge.
- Normal global search remains separate from the bounded matching workflow.

### Useful performance protections already present

- For You work is I/O limited to five parallel tasks.
- For You source attempts, per-source result caps, query plans, additional pages, and enrichment are bounded.
- Top Picks reuses fetched results rather than launching its own crawl.
- Group-seeded recommendations have a total timeout, per-search and per-localization timeouts, early exit, and source-aware network-candidate deduplication.
- Offline detection and Retry exist for the For You screen.

### Test foundation

There are focused tests for many pure policy components: query planning, scoring, tag mapping, source ordering, visibility, Top Picks merge, matching selection, source-evaluation policies, migration safety, backup serialization, cache memory ranking, and group-loop bounds. This is a much better base than an untested UI-only feature set.

---

## Verified Design and Correctness Problems

### High - Source Evaluation cannot currently prove catalogue fit

The general evaluator requests Popular, Latest, and targeted search pages, then pools all titles/tags before calculating one `recommendationFitScore`.

Why this is wrong for source selection:

- Popular/Latest describe the source's natural surface.
- A targeted search describes only whether a user-specific query can retrieve something.
- Pooled evidence cannot tell which one caused the result.

Effect: a source can look like a Strong Fit because searches find matching tags despite its normal catalogue being low quality, old, unsuitable, or mostly unrelated. Conversely, a good catalogue source with a poor search implementation can be downgraded unfairly.

Required direction: replace the pooled score with separate catalogue-fit and retrieval-compatibility records. Do not merely add more weights to the current score.

### High - The recommendation-quality label is semantically incorrect

The second-stage probe performs For You-style searches. It does not inspect a website's native recommendation links, related-manga endpoint, user behavior, or catalogue surface.

Required direction: rename it to `For You retrieval`, `Tag search compatibility`, or similar. A source-native recommendation score is not universally feasible without source-specific adapters. Do not claim it exists.

### High - Source Evaluation and For You use inconsistent taste semantics

For You candidate scoring uses learned weights, explicit preferred/disliked tag preferences, blocked tags, and aliases. The general Source Evaluation scorer:

- uses only positive learned tag weights for positive fit;
- ignores explicit preferred tag rows;
- applies blocked groups but does not resolve source tags through saved aliases;
- stores a `likedTitleMatchCount` that is always zero.

Effect: the same user profile can judge a candidate positively in For You while judging its source weakly in Source Evaluation, for reasons unrelated to actual content.

Required direction: extract one source-independent `TasteAffinity` helper that resolves aliases and supports explicit/learned/blocked semantics. Candidate ranking may add source affinity later; non-installed catalogue evaluation must not.

### High - v0.7.39 rolling discovery has two verified behavior defects

1. When no previous memory exists, new extra-page candidates are persisted but are not merged into the visible list during that same refresh.
2. A failed additional-page request is recorded as evaluated, so a transient failure can cause a page to be skipped until the user clears discovery history.

These are documented in the focused For You audit and should be corrected before further discovery expansion.

### High - For You can persist a large volume of unwanted browse data

For You localizes network candidates through `NetworkToLocalManga`, which inserts them into the local manga database. A manual refresh can attempt up to 40 sources, two query plans per source, an additional page per source, and metadata enrichment.

Candidate-memory pruning is bounded per source, but it does not establish that non-library local manga rows are removed. This is a data-retention and performance concern, not merely an implementation detail.

Required direction: measure real DB growth and define a retention policy before increasing discovery breadth or adding expensive filters.

### High - Non-installed source suggestions are intentionally not content recommendations

Before Source Evaluation, `Sources To Try` is driven by conservative source-name similarity to installed sources or an explicit user source-like preference. Same language/repository are eligibility constraints, not positive proof.

This is safer than broad keyword matching, but it cannot tell whether an extension contains manga the user likes. It should never be presented as an evaluated content recommendation until a catalogue-fit evaluation exists.

Required direction: label pre-evaluation suggestions as `Needs testing` and make post-evaluation catalogue fit the primary content-based ranking signal.

### High - Group-seeded recommendations bypass important For You source policy

`GroupSeededRecommendationsScreenModel` takes the first five visible sources after language filtering. It does not apply the stored source priority order or disabled/disliked source exclusion used by For You. It also does not apply the same known/seen/rated visibility policy before showing candidates.

Effect: a user can disable or deprioritize a source for For You and still have it searched in a Loved-group recommendation flow. Known titles can also reappear unexpectedly.

Required direction: create a shared `RecommendationSourceSelector` and shared candidate-visibility policy used by For You, group-seeded recommendations, and any future source-specific drill-down. Preserve intentional exceptions explicitly rather than reimplementing selection in each screen.

### Medium - Same-manga matching has broad fan-out and localizes before applying the cap

`SameMangaCandidateSearcher` launches work for every matching source. Each source gathers results for up to three title queries, converts and localizes all returned candidates, then applies its per-source display cap.

Risks:

- many queued coroutines and source calls when many sources are installed;
- unnecessary local database rows for candidates that exceed the final cap;
- a fixed thread-pool dispatcher owned by the searcher without an explicit close lifecycle;
- no shared source-attempt budget or per-source timeout visible in this helper.

Required direction: cap raw candidates before localization, use a shared bounded dispatcher/lifecycle-managed executor, add per-source timeout/cancellation, and consider an overall source attempt budget. Preserve the normal global-search behavior separately.

### Medium - Source fit stats measure operational output, not recommendation quality

`SourceFitStats` counts shown/no-match/error/filtered runs and Top Picks contribution. It is useful operational telemetry, but a `Great Fit` label currently means a source frequently produced several visible candidates. It does not mean the user liked those candidates.

Required direction: rename UI wording to avoid overclaiming, or later augment it with explicitly observed rating outcomes after a minimum sample size. Never infer dislike from no click or scrolling.

### Medium - Candidate score eligibility differs between live and remembered paths

The immediate For You path can retain non-blocked candidates at zero or negative score when a source returns few results. Candidate-memory merge later removes scores at or below zero.

Effect: weak candidates can appear once and disappear on a later refresh with no preference change.

Required direction: one explicit eligibility threshold, plus an optional user setting if low-confidence exploration is desired.

### Medium - Cache/progress keys can become stale in the wrong way

Discovery progress is keyed by source and sorted top tags. It stores a profile fingerprint but does not use it to partition/invalidate progress. A meaningful taste change that retains the same top tag set can continue skipping pages evaluated under the old profile.

Conversely, page one is re-fetched on every forced refresh even when recent, which spends much of the refresh budget repeatedly before discovery advances.

Required direction: distinguish result freshness, discovery progress, and current-profile re-evaluation. Use TTL/retry policy and a targeted reset/reassess action, not a blanket reset on every rating.

### Medium - Top Picks combines useful evidence but has limited explainability

Top Picks uses each candidate's best personal score, a small repeated-source bonus, and a boosted-source bonus. It uses conservative duplicate merge rules, which is safe.

Gaps:

- the visible `PersonalRecommendation.score` remains the base score rather than the effective combined rank score;
- the user cannot see why a title won: personal tag match, multiple sources, source priority, or metadata richness;
- conservative duplicate handling leaves obvious alternate-title/translation duplicates separate, while broader matching could be unsafe.

Required direction: show bounded result reasons such as `matched: action, regression; found in 3 sources`. Do not loosen automatic duplicate matching without user confirmation or cross-source links.

### Medium - Result metadata quality remains the central limitation

Many extensions omit genres on search/list cards. For You enriches a bounded set; Source Evaluation generally does not enrich Popular/Latest samples; group recommendations enrich linked seed members but not all candidates; matching relies mostly on title variants.

The system must surface confidence and metadata coverage rather than treating missing tags as a content judgment.

### Medium - User-visible error handling is uneven

There are strong timeouts and isolation policies in Source Evaluation, but some recommendation paths still reduce a broad set of source problems to an empty list or a generic error. Some diagnostic strings and raw exception details remain hardcoded/deferred in the evaluation surface.

Required direction: standardize a small error taxonomy: offline, timeout, unsupported filter/search, source failure, missing metadata, unsafe/quarantined extension, and cancelled. Preserve technical detail in logs, not primary UI text.

---

## Efficiency Review

### For You cost model

In a worst practical forced refresh, For You may:

- attempt 40 sources in batches of five;
- use up to two query strategies per source;
- fetch page one and one additional discovery page;
- process up to 60 raw candidates for each boosted source and 30 for each normal source;
- run database known/history/chapter checks;
- enrich up to the configured metadata cap, with boosted sources doubling that cap;
- insert network candidates into the local database;
- update cache, candidate memory, discovery progress, source statuses, and serialized source-fit preferences.

This is bounded but not lightweight. Local scoring is cheap; network, enrichment, and DB localization are the expensive parts.

### Good current controls

- I/O limited parallelism avoids unbounded simultaneous calls.
- Fixed source/page/result caps prevent an accidental full-catalogue crawl.
- Manual source priority lets the user favor reliable sources.
- Top Picks does not launch a second crawl.

### Improvements that should precede more features

1. Correct v0.7.39 extra-page merge and transient-failure retry behavior.
2. Instrument duration, source attempts, strategy count, page probes, localizations, enrichment calls, DB growth, and cancellation in debug/internal diagnostics.
3. Introduce explicit refresh-effort presets or source-scope controls, after measurement.
4. Cache/reuse stable filter capability information where safe, rather than repeatedly obtaining it for each strategy.
5. Apply raw caps before localizing in matching and other non-feed workflows.
6. Define retention for derived network candidate rows and stale source statistics/progress.

---

## Filters: What Is Feasible and Honest

### Strong first candidates

- Status: include/exclude ongoing, completed, cancelled, hiatus, licensed when the source supplies a recognized status. Unknown should pass by default.
- Minimum score: hide weak/zero-confidence candidates consistently.
- Source scope: priority-only, enabled sources, or a curated subset.
- Discovery mode: retain best known, prefer newly discovered, or explicitly discover more.
- Existing controls: tags, blocked tags, languages, known/seen visibility, rated visibility, and local chapter minimum.

### Conditional or expensive controls

- Minimum chapter count: only universal after fetching chapter lists, so use only on a small high-scoring shortlist or keep the current local-data-only semantics clearly labelled.
- Last chapter/update age: source-specific and usually requires a detail/chapter request. Do not run broadly during every refresh.
- Publication year/series age: not universally available in `SManga`; do not claim a universal date filter.
- Image/translation quality: requires user-selected source comparison or future local quality signals; no generic metadata tells the app this reliably.

### Avoid

- Title heuristics as a proxy for age, quality, or genre.
- Treating absence of tags as a dislike.
- Automatic universal source-native recommendation support without source-specific adapters.
- Using implicit scrolling/non-action as negative user feedback.

---

## Recommended Target Architecture

### Shared helpers, not one giant engine

The system should share only genuinely common rules:

- `TasteAffinity`: aliases, explicit preferences, learned weights, blocked/disliked handling; source-independent.
- `RecommendationSourceSelector`: language, disabled/disliked status, user priority, intentional caps, and optional source scope.
- `RecommendationCandidateVisibility`: seen, known, rated visibility, library, chapter threshold, status filters, and confidence threshold.
- `SourceCatalogueSnapshot`: bounded Popular/Latest sample with provenance and metadata coverage.
- `RecommendationEvidence`: typed reason codes rather than ad hoc strings.

Keep these workflows separate:

- source catalogue evaluation;
- For You search/retrieval;
- manga-detail provider recommendations;
- cross-extension identity matching;
- best-version chapter comparison.

They have different inputs and failure modes. Sharing their network orchestration wholesale would make the system harder to reason about.

### Source selection evidence ladder

```text
manual source preference
  -> evaluated catalogue fit
  -> For You retrieval compatibility
  -> observed explicit user outcomes
```

Pre-evaluation name similarity is only an invitation to test a source. It should never outrank genuine post-evaluation evidence.

### Candidate selection evidence ladder

```text
source returns candidate
  -> metadata coverage sufficient?
  -> hard exclusions (blocked, seen, known, status)
  -> personal affinity score
  -> optional expensive shortlist checks
  -> cross-source dedupe / Top Picks explanation
```

---

## Suggested Improvement Roadmap

This is a prioritization recommendation, not approved scope.

### Phase 1 - Correctness and truthful labeling

- Fix the two v0.7.39 discovery defects.
- Rename recommendation-quality UI and documentation to retrieval compatibility.
- Apply one candidate eligibility threshold in live and memory paths.
- Route group-seeded recommendations through shared source selection and visibility policy.
- Add regression tests for each item.

### Phase 2 - Replace source-evaluation semantics

- Introduce provenance-aware Popular/Latest catalogue snapshots.
- Reuse a canonical alias-aware taste-affinity helper.
- Record metadata coverage and insufficient-evidence outcomes.
- Separate catalogue fit from retrieval compatibility in storage/UI/versioning.
- Increment evaluation version and support clean reassessment of old records.

### Phase 3 - Measured performance and retention

- Add debug/internal instrumentation.
- Benchmark Faster/Balanced/Thorough source scope proposals on phone and tablet.
- Define cache/memory/stale-local-manga retention behavior.
- Optimize matching localization and dispatcher ownership.

### Phase 4 - User controls and explainability

- Add compact status, source-scope, score threshold, and discovery-mode controls.
- Add explainable result reasons and source evidence summaries.
- Add targeted source reassessment rather than global resets.

### Phase 5 - Conservative outcome learning

- Record local For You impressions and later explicit rating/seen outcomes.
- Apply only after minimum evidence and make reset/privacy behavior explicit.
- Use it as a small ordering signal, never an automatic hard block.

---

## Required Test and Device Work

Existing pure-unit tests are helpful but insufficient for the integration-heavy workflows. Add or perform:

- fake-source integration tests for Popular/Latest/search provenance and sparse metadata;
- tests that the same source policy is respected by For You and group recommendations;
- extra-page immediate-display and retry/backoff tests;
- matcher raw-cap-before-localize and cancellation tests;
- cache/progress profile-change and source-uninstall retention tests;
- migration/reassessment tests for changed source-evaluation semantics;
- backup/sync tests for all user-owned taste/link data;
- real-device tests for offline recovery, slow source, extension crash/quarantine, background evaluation, tablet layout, and a high extension-count account;
- database-size and refresh-duration measurements before/after the performance changes.

---

## Final Recommendation

The system should move forward, but with consolidation rather than further feature accumulation. The highest-value change is to separate source catalogue fit from tag-search retrieval compatibility, because it will make every later source recommendation more trustworthy. In parallel, fix the known For You discovery defects and establish storage/performance measurement.

Do not attempt universal publication-date, native-site recommendation, image-quality, or chapter-freshness scoring until the underlying sources provide reliable data or the user explicitly chooses the small set of sources where source-specific adapters are worth maintaining.

