# KMK Source Catalogue Fit And Retrieval Quality Feasibility Audit

Date: 2026-07-11

Status: feasibility and code audit only. No implementation is approved by this document.

Purpose: determine how the current source-evaluation and source-recommendation labels are actually produced, why they can disagree with a user's real preferences, and what a more trustworthy and efficient model should measure.

Related documents:

- `CURRENT_STATE.md`
- `KMK_RECS_V0_7_6_SOURCE_EVALUATION_CONTINUATION_AND_RECOMMENDATION_QUALITY_PLAN.md`
- `KMK_RECS_V0_7_13_SOURCE_EVALUATION_RECOMMENDATION_QUALITY_FUNCTIONAL_FIX_IMPLEMENTATION.md`
- `KMK_RECS_FOR_YOU_FILTERING_AND_PERFORMANCE_FEASIBILITY_AUDIT.md`

---

## Executive Conclusion

The concern is substantially correct, with one important clarification.

The general Source Evaluation does call `getPopularManga(1)` and, when supported, `getLatestUpdates(1)`. It is not search-only. However, it then pools Popular, Latest, and up to three tag searches into one undifferentiated title/tag list. The resulting source-fit score cannot tell whether a source's normal catalogue surface fits the user's taste, whether only a carefully targeted search fit it, or whether the extension returned usable metadata at all.

The separate UI label currently called recommendation quality is even more limited: it does not ask a website for related manga or inspect a source's own recommendation system. It runs up to two For You-style searches using the user's strongest tags and measures whether the returned results score positively. It is a search/filter retrieval compatibility test, not a source recommendation-quality test.

Therefore, a source can appear Strong Fit or have a Good recommendation-quality label even when its ordinary Latest/Popular catalogue is full of content the user does not want. Conversely, a source with excellent catalogue content can look weak when its search result cards omit genres until detail pages are fetched.

The correct long-term model has three distinct measurements:

1. Catalogue fit: does the source's normal Popular/Latest surface contain content aligned with the user's taste?
2. For You retrieval compatibility: can Komikku's tag query strategy retrieve suitable candidates from that source?
3. Actual user outcome: after a source is shown in For You, do the userâ€™s later ratings/seen actions indicate that its surfaced results were useful?

These must not be merged into one opaque label.

---

## What The Current Code Does

### General Source Evaluation

`SourceEvaluationRunner.probeAndScore()` temporarily installs an extension and evaluates every catalogue source it exposes, one extension at a time.

For each source it requests:

| Probe | Current cap | Purpose in current code |
| --- | ---: | --- |
| Popular page 1 | 15 manga | Adds titles and any list-card tags to one pooled sample. |
| Latest page 1 | 10 manga, only if `supportsLatest` | Adds titles and any list-card tags to the same pooled sample. |
| Search pages | Up to 3 queries, 8 manga each | Searches the strongest learned taste tags and adds returned titles/tags to the same pool. |

Popular and Latest calls each have a 30-second timeout; each search has a 25-second timeout. The runner catches failures so one source failure does not necessarily end the batch.

`SourceEvaluationScorer` then computes:

- sample quality from the pooled title count;
- search reliability from search success count;
- explicit/ecchi signals from pooled title/tag text;
- source fit from the count of pooled tags that exactly normalize to positive learned tag keys, minus blocked-tag penalties.

It does not retain which sample came from Popular, Latest, or Search. It does not score each sample item individually. It does not compute a share of catalogue candidates that match, a share that conflict with dislikes, or a confidence measure based on metadata coverage.

### Current "Recommendations" / recommendation-quality label

`SourceRecommendationFitProbe` runs only after a source was initially judged Strong Fit or Worth Trying. It uses `RecommendationQueryPlanner` and `GenreFilterMapper` to run up to two searches based on the top learned taste tags.

For each query it:

1. gets the source filter list;
2. maps matching tag filters or falls back to text;
3. searches page 1;
4. takes a small raw result set;
5. fetches manga details for up to five results lacking genres;
6. scores the resulting manga with `PersonalRecommendationScorer`.

The score rewards visible positive candidates and average candidate score. It penalizes empty searches, filtered/blocked results, and errors.

This is useful information, but its correct meaning is:

> "Can this source respond usefully to Komikku's current tag-based For You search?"

It does not measure:

- a website's native related-manga/recommendation links;
- whether Popular or Latest content suits the user;
- long-term user satisfaction with candidates shown from that source;
- publication date, chapter freshness, translation quality, image quality, or update reliability.

---

## Verified Problems

### P0 - Catalogue evidence and targeted-search evidence are mixed

Popular, Latest, and search results all enter `sampledTitles` and `sampledTags` before a single score is calculated. A source can pass because targeted search finds a few matching tags even if its ordinary catalogue surface is a poor fit. The reverse can also happen when a good source has weak search implementation.

Required direction: preserve provenance for every sampled candidate and calculate catalogue fit separately from search retrieval compatibility.

### P0 - The "recommendation quality" label overstates what is measured

The second-stage probe tests Komikku's query path, not a website's recommendation engine. Calling the result "Recommendations: Good" makes a reasonable user expect that the source naturally recommends related manga well. The code cannot support that claim universally.

Required direction: rename the label to something accurate, such as `For You retrieval: Good`, `Tag search compatibility: Good`, or `For You query fit: Good`. Reserve "recommendations" for a real source-native recommendation adapter or measured user outcomes.

### P0 - General source fit ignores explicit preferred-tag rows

`SourceEvaluationScorer` derives positive tags only from `TasteProfile.learnedTagWeights`. It does not incorporate `TasteProfile.explicitTagPreferences` where the user has manually marked a tag as preferred. It does use blocked groups for a penalty.

Effect: a userâ€™s direct positive preferences can influence For You candidate scoring but not the general source-evaluation fit verdict.

Required direction: use the same canonical taste-affinity inputs across catalogue-fit and candidate scoring, while keeping source affinity out of a non-installed sourceâ€™s catalogue score.

### P0 - General source fit ignores tag aliases

The source scorer normalizes sampled tags but does not apply the saved alias-to-group mapping used elsewhere. For example, a source using `Yuri` may not be recognized as matching a taste group configured as `Girls Love`, even though the For You query mapper knows the relationship.

Required direction: resolve sampled tags through the existing alias/synonym rules before score calculation. The implementation must avoid maintaining a second normalization map.

### P1 - General source list-card metadata is too sparse to support a confident negative verdict

Many extensions return list cards with `genre = null` and only populate tags after `getMangaDetails`. The general Source Evaluation does not enrich Popular or Latest samples. It can therefore classify a metadata-poor source by absence of evidence rather than evidence of mismatch.

Required direction: record metadata coverage independently. A sample with too few usable genre-bearing items must become `insufficient evidence`, not Weak or Poor Fit.

### P1 - Liked-title matching is an advertised but unimplemented signal

`likedTitleMatchCount` is permanently set to zero in `SourceEvaluationScorer`. The comment acknowledges that full title data was not implemented.

This should either be removed from the displayed/stored evaluation claim or implemented only for a narrowly defined purpose such as detecting known loved manga availability. It should not be used as a fuzzy taste proxy.

### P1 - Current counts do not represent ratios or diversity

The score uses raw matching tag counts. A duplicate tag repeated on several list cards can score more strongly than a smaller but richer sample. It does not calculate:

- proportion of candidates with positive affinity;
- proportion with negative or blocked affinity;
- median/mean candidate score;
- tag metadata coverage;
- uniqueness/deduplication across Popular and Latest;
- evidence confidence by origin.

Required direction: build a small, provenance-aware sample model and score normalized ratios rather than raw pooled counts.

### P1 - Search score cannot solve old, axed, or stale catalogue content

The current search test proves only that a source can return tag-matching manga. It has no date, chapter freshness, completion, cancellation, or quality signal. A query can retrieve old or axed manga with matching tags and still be marked useful.

This reinforces the need to keep catalogue fit and later For You candidate filters separate.

---

## Proposed Evidence Model

### A. Catalogue snapshot: the source itself

Take a small, bounded snapshot from each source during temporary evaluation:

- Popular page 1: up to 10 candidates.
- Latest page 1: up to 10 candidates if supported.
- Deduplicate by source URL across both lists, retaining origin flags (`POPULAR`, `LATEST`, or both).
- Do not use targeted taste searches in this first score.
- Enrich only enough sparse candidates to reach a minimum usable metadata threshold, for example 6 candidates with genres, with a strict total detail-call and timeout budget.
- If coverage remains below the threshold, report `Insufficient metadata` rather than inventing a negative verdict.

For every usable candidate, resolve tags through the existing alias/synonym rules and calculate the same content affinity used for recommendations, excluding source-affinity because the source is not yet trusted/installed.

Suggested catalogue metrics:

| Metric | Meaning |
| --- | --- |
| `popularAffinity` | Average/median personal-tag affinity of Popular candidates. |
| `latestAffinity` | Average/median personal-tag affinity of Latest candidates. |
| `positiveShare` | Percentage of usable candidates with score above the eligibility threshold. |
| `negativeShare` | Percentage with negative affinity. |
| `blockedShare` | Percentage containing blocked tags. |
| `metadataCoverage` | Usable tagged candidates divided by sampled candidates. |
| `sampleDiversity` | Optional diagnostic: unique normalized tags/candidates, not a direct quality score. |
| `statusCoverage` | How often recognized ongoing/completed/cancelled/hiatus status is supplied. |

The initial source catalogue verdict should use these metrics and make the evidence visible:

```text
Catalogue fit: Strong
Popular: 7/10 positive; Latest: 5/8 positive
Tag metadata: 15/18 usable
Blocked-content share: 0%
```

This lets the user understand why a source is being recommended and distinguishes sparse metadata from poor catalogue fit.

### B. For You retrieval compatibility: Komikku's query path

Keep the current bounded search probe, but describe it accurately and make it secondary.

It should answer:

```text
Given this user's positive tags and Komikku's current query planner,
can the source retrieve enough positively scored results without frequent failures?
```

Recommended controls:

- Run only for sources whose catalogue snapshot has enough evidence and is at least Worth Trying.
- Keep at most two query plans and a small raw cap.
- Retain enrichment only for a strict small number of candidates.
- Do not let this score improve a poor catalogue verdict into Strong Fit.
- Permit it to warn that an otherwise good catalogue source has weak tag-search compatibility.

Suggested label:

```text
For You retrieval: Good
```

not `Recommendations: Good`.

### C. Actual outcome learning: the user's observed response

This is the only layer that can eventually tell whether a source's For You row is genuinely useful to this user.

When a For You candidate is exposed, record a bounded local impression with source, candidate key, score bucket, and timestamp. Later user actions can provide conservative signals:

- Love/Like: positive outcome.
- Dislike: negative outcome.
- Seen/read: neutral suppression, not negative genre evidence.
- No action: no conclusion.

Do not infer dislike from scrolling, time spent, or a candidate merely remaining unread. Those signals would be noisy and invasive.

Use the outcome rate only after a minimum sample size, with confidence displayed. It should adjust source ordering slightly rather than hiding sources automatically. This layer is local-only and should have a clear reset/privacy control.

---

## Why This Is More Efficient Than Search-Led Evaluation

The current general evaluation can make:

- one Popular request;
- one Latest request;
- up to three search requests;
- and, for qualifying sources, a further two recommendation-quality search requests plus bounded detail calls.

The proposed staged model makes catalogue fit the first gate:

1. Request Popular and Latest once each.
2. Enrich only a small subset until metadata coverage is adequate or the budget is exhausted.
3. Stop and store `Insufficient metadata` when necessary.
4. Run the separate retrieval probe only for evidence-backed, promising sources.

This removes three targeted searches from the initial decision and avoids treating search behavior as catalogue quality. It also makes the expensive work useful: detail calls are spent obtaining metadata from the source's actual surfaced catalogue, not only validating a query that was constructed from the same taste tags it will later score.

The exact timeout and sample constants require device testing. Extension methods are site-dependent, so all calls remain sequential per temporarily installed extension and bounded with cancellation-aware timeouts.

---

## Publication Date, Latest Chapters, and Status

Popular/Latest is a good representation of what a source currently surfaces, but neither is a universal publication-date signal.

- `Latest` means the site considers an entry recently updated; it does not consistently expose a timestamp or prove that a series is new.
- `Popular` measures site popularity, not quality or recency.
- `SManga.status` can be useful when a source supplies it, but unknown status must not be treated as cancelled or old.
- Chapter number and latest chapter date require a detail/chapter-list call per candidate, which is too costly for broad source evaluation.

Therefore, catalogue evaluation should use Popular/Latest only to assess source surface fit. Later For You filtering can use status cheaply where provided, and can run chapter/update checks only on a small high-scoring shortlist when the user explicitly enables those expensive filters.

---

## Data and Versioning Requirements If Approved

This is not a small scorer tweak. It changes the meaning of persisted source evaluations.

- Increment `SourceEvaluationKeys.CURRENT_VERSION` so old pooled/search-led records are visibly stale and eligible for reassessment.
- Do not overwrite old fields with new semantics silently. Either add clearly named catalogue/retrieval fields or create a versioned evidence structure.
- Persist only bounded diagnostics needed for user explanation, not full descriptions or unbounded content lists.
- Separate derived local evidence from backup/sync unless the user explicitly wants source-evaluation history to travel with a profile.
- Add migration, mapper, repository, restore/sync, reset, and expiry decisions to the future implementation plan.

---

## Required Tests Before Release

The future implementation must test at least:

- Popular-only, Latest-only, and Popular+Latest source behavior.
- URL deduplication while retaining origin flags.
- Alias mapping such as `Girls Love` / `Yuri` and user-defined aliases.
- Explicit preferred tags affecting catalogue fit.
- Blocked-tag candidates being a hard negative/evidence signal.
- Sparse list metadata producing insufficient evidence rather than Weak.
- A source with poor catalogue fit but strong search retrieval remaining poor/mixed overall.
- A source with strong catalogue fit but weak search retrieval being labelled correctly, not discarded.
- Timeout, cancellation, partial success, extension cleanup, and no-network paths.
- Evaluation-version invalidation and migration.
- No recommendation-quality label claiming source-native recommendations.

---

## Recommended Decision

Do not add more source-ranking heuristics to the current pooled scorer. Replace the evaluation semantics deliberately in a focused later version.

Priority order:

1. Finish the application-wide audit baseline first.
2. Create a dedicated implementation plan for the provenance-aware catalogue snapshot and label correction.
3. Implement and test catalogue fit before changing For You source ordering.
4. Keep retrieval compatibility separate and optional.
5. Add local observed-outcome learning only after the first two layers have been stable in daily use.

This will make source suggestions explainable and aligned with what the user actually sees in a source, rather than awarding a source for successfully answering a query whose results may still be poor manga.

