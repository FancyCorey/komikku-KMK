# KMK Real-Device Source Evaluation Diagnostic

Date: 2026-07-12

Status: diagnostic findings from a read-only `app.komikku.dev` database pull.

Scope: source evaluation, recommendation-quality probes, and why some sources that look useful in practice are currently ranked as weak.

## Privacy Boundary

This diagnostic used a local read-only copy of the debug app database:

- `tachiyomi_kmk_debug_diagnostic.db`
- `tachiyomi_kmk_debug_diagnostic.db-wal`
- `tachiyomi_kmk_debug_diagnostic.db-shm`

Those files are local diagnostic artifacts and may contain personal manga/rating/search state. They must not be committed, shared, pasted into prompts, or included in public release material. The findings below intentionally avoid dumping the user's full library or rating history.

## Executive Summary

The source evaluation database is functioning: the app is recording source evaluations, recommendation-fit probes, candidate memory, discovery progress, ratings, cross-source links, and OCR rows.

The main quality problem is not that the database is empty or that source evaluation is completely broken. The main problem is that the current scoring still treats missing source metadata as weak fit too aggressively.

The concrete example is Elf Toon:

- The source was evaluated successfully.
- It produced a strong catalogue sample count.
- The sampled titles look highly relevant to the user's taste in practice.
- `quality_score` is high.
- `catalogue_metadata_confidence` is `unknown`.
- `sampled_tags_json` is missing.
- `preferred_tag_match_count` is `0`.
- The source is therefore marked `weak`.

That means the app currently says "weak" for at least some sources where the more honest label is closer to:

> "Promising catalogue, but too little usable tag metadata to score automatically."

## Database Snapshot

Important table counts observed from the diagnostic copy:

| Table | Rows | Meaning |
| --- | ---: | --- |
| `source_evaluation` | 416 | Catalogue/source fit records |
| `source_recommendation_fit` | 303 | For You search-compatibility / recommendation-quality probe records |
| `recommendation_candidate_memory` | 1082 | For You candidate memory |
| `recommendation_discovery_progress` | 139 | Rolling discovery progress |
| `manga_taste` | 1258 | User rating records |
| `tag_taste` | 9 | Explicit tag preferences |
| `manga_cross_source_link` | 1327 | Confirmed cross-source manga links/groups |
| `manga_source_quality_signal` | 5 | Best-version/source-quality signals |
| `ocr_indexed_page` | 218 | OCR indexed page rows |

This confirms the live app is persisting the expected recommendation system data.

## Current Source Evaluation Path

The current source evaluation path in `SourceEvaluationRunner` installs or loads a candidate source, samples page 1 of Popular capped at 15 entries and page 1 of Latest capped at 10 entries if supported.

Those catalogue samples are converted to domain `Manga` objects and passed to `SourceEvaluationScorer`.

`SourceEvaluationScorer` then extracts titles and tags, detects explicit/ecchi signals, scores each sample with `PersonalRecommendationScorer`, counts positive taste matches, computes metadata confidence, and assigns the source verdict.

The important detail: catalogue fit currently depends heavily on `Manga.genre`. If the source gives good manga titles but does not expose genre/tag metadata in Popular/Latest results, `PersonalRecommendationScorer` has little or nothing to score.

## Current Recommendation-Quality Probe Path

The current recommendation-quality path uses `SourceRecommendationFitProbe`.

It takes top learned taste tags, builds up to three query plans, searches page 1 only, caps raw results at 20, enriches up to 5 candidates per plan with `getMangaDetails`, scores candidates with `PersonalRecommendationScorer`, and stores counts/reasons in `source_recommendation_fit`.

This is bounded and safer than earlier versions, but it still has the same core limitation: if a source's search results and detail pages do not expose genre metadata, the scorer can receive many raw results and still produce zero visible candidates.

## Elf Toon Evidence

The diagnostic database includes an Elf Toon row:

| Field | Observed value |
| --- | --- |
| `extension_pkg_name` | `eu.kanade.tachiyomi.extension.en.elftoon` |
| `source_name` | `Elf Toon` |
| `base_url` | `https://elftoon.com` |
| `sample_count` | 28 |
| `popular_count` | 15 |
| `latest_count` | 10 |
| `preferred_tag_match_count` | 0 |
| `blocked_tag_match_count` | 0 |
| `quality_score` | 0.9 |
| `recommendation_fit_score` | 0.35 |
| `search_reliability_score` | 0.3333333333 |
| `catalogue_metadata_confidence` | `unknown` |
| `sampled_tags_json` | `null` |
| `verdict` | `weak` |

The sampled title list included many titles that look relevant to the user's known preference pattern, such as cultivation, system/progression, martial, assassin, and fantasy-power titles. The app did collect those titles, but the catalogue scorer did not have tags to turn them into positive taste matches.

The related `source_recommendation_fit` row showed:

| Field | Observed value |
| --- | --- |
| `query_count` | 3 |
| `query_success_count` | 1 |
| `raw_result_count` | 15 |
| `visible_candidate_count` | 0 |
| `filtered_out_count` | 15 |
| `blocked_tag_candidate_count` | 0 |
| `avg_candidate_score` | 0.0 |
| `recommendation_quality_score` | 0.0 |
| `verdict` | `weak` |
| `error_count` | 0 |

The stored reasons included:

- `Plan TOP_TAGS_FILTER: no raw results`
- `Plan TAG_PAIR: 15 results, all had no genre metadata`
- `Plan TEXT_ONLY_TOP_TAGS: no raw results`

This is the key diagnostic finding. Elf Toon is not failing because it errors. It is failing because the current automatic scorer cannot evaluate metadata-poor results.

## What Is Working

1. The source evaluation tables exist and are populated.
2. The app is correctly separating catalogue evaluation from recommendation-quality search compatibility.
3. Temporary source evaluation records include source names, package names, source IDs, extension versions, sample counts, scores, verdicts, and metadata confidence.
4. Recommendation-quality probes are recorded with raw result counts, visible candidate counts, filtered counts, and reasons.
5. `SourceRecommendationFitEligibility` already attempts to fail open for low/unknown catalogue metadata confidence, so weak catalogue verdicts caused by sparse metadata can still be probed.
6. The current code already recognizes "all had no genre metadata" as a diagnostic condition.

## What Is Misleading

### `weak` conflates bad fit with unscorable metadata

For Elf Toon, `weak` means enough catalogue titles were sampled, no blocked tags were detected, no explicit/ecchi verdict applied, no usable tags were available, and no candidate survived the tag scorer.

That is not the same as "this source has bad manga for the user."

### `preferred_tag_match_count = 0` can mean "no tags", not "no fit"

Because `PersonalRecommendationScorer` scores tags, a metadata-poor source receives no positive matches even if its titles strongly suggest relevant content.

### Search compatibility can look worse than it is

For Elf Toon, one query returned 15 results, but all 15 were filtered because no genre metadata survived enrichment. The source had search output, but the scoring layer had no usable tags.

## Code-Level Cause

The core chain is:

1. `SourceEvaluationRunner.probeAndScore()` samples Popular/Latest.
2. Samples are converted with `toDomainManga(source.id)`.
3. `SourceEvaluationScorer.score()` builds `sampledTags` from `Manga.genre`.
4. It scores samples through `PersonalRecommendationScorer.score()`.
5. `PersonalRecommendationScorer` uses candidate genres/tags, explicit tag preferences, learned tag weights, and source affinity.
6. If `genre` is empty, score is usually `0.0`.
7. `preferredTagMatchCount` remains `0`.
8. `recommendationFitScore` falls to the default low baseline.
9. Verdict becomes `WEAK`.

The recommendation-quality probe has a similar path: raw search results are enriched and scored through `PersonalRecommendationScorer`; candidates with score `<= 0.0` are filtered; metadata-poor results become invisible even when raw results exist.

## Comparison Against What Works In Practice

The user found sources such as Elf Toon practically useful despite weak automatic scoring. The database supports that observation:

- The source has high catalogue sample coverage.
- Its title sample contains many relevant themes.
- It is not marked explicit-heavy or ecchi-heavy.
- It is not erroring.
- It is not blocked by disliked tags.
- It is metadata-poor.

Therefore, the app's current source evaluation is better understood as a metadata-dependent classifier, not a fully reliable source-quality classifier.

## Recommended Fix Direction

Do not simply lower every threshold. That would make genuinely weak or noisy sources look better.

Instead, split the evidence model more clearly.

### 1. Add an "Unscorable / Metadata Sparse" display state

When a source has good sample count, low/unknown metadata confidence, no explicit/ecchi/rejected/error verdict, and low positive tag matches, the UI should not display it as simply `Weak`.

Suggested user-facing labels:

- `Needs Review`
- `Sparse Metadata`
- `Promising, metadata limited`
- `Manual Review`

The existing `SourceEvaluationVerdict.NEEDS_MANUAL_REVIEW` appears to exist in the UI enum path, so this may be usable instead of adding a new verdict. Confirm the domain enum and SQL adapters before changing persisted values.

### 2. Add title-affinity as a secondary signal

For metadata-poor sources, the app should calculate a bounded title-affinity score from sampled titles.

This should be lightweight and local:

- tokenize sampled titles;
- compare against tokenized loved/liked manga titles and group-linked titles;
- compare against a small set of learned theme tokens from positively rated titles;
- ignore very common words;
- cap contribution so title affinity cannot overpower explicit blocked tags.

This should not become an AI model or external lookup.

### 3. Treat raw-result/no-metadata as a separate recommendation-quality outcome

In `SourceRecommendationFitProbe`, raw results with no visible candidates because of missing genre metadata should not be equivalent to irrelevant results.

Persist/display a separate compatibility state such as:

- `Search works, metadata missing`
- `Unscorable results`
- `Needs manual review`

### 4. Learn source quality from local outcomes

The app should eventually learn that a source is good if the user opens results from that source, rates manga from that source as liked/loved, selects that source in best-version workflow, or manually marks the source as good/reliable.

This should be separate from catalogue tag evidence.

### 5. Show evidence details, not just verdicts

The Source Evaluation row/detail should let the user see sample count, metadata confidence, sampled title examples, sampled tag examples if available, raw search result count, visible candidate count, reason text, and whether the verdict is metadata-limited.

### 6. Keep explicit/porn/hentai filtering conservative

Metadata-sparse sources should never bypass safety checks. Title/package explicit classifiers and explicit sampled tags should still dominate.

## Proposed Future Implementation Scope

This should be a future implementation plan, not a quick patch.

Recommended version target: `v0.8.x`, because it changes source-evaluation semantics.

Suggested phases:

1. Diagnostic/UI truth fix: display `Weak (metadata sparse)` or `Needs Review` for strong-sample/low-metadata sources.
2. Pure title-affinity helper: unit-tested tokenizer/scorer using only local titles and sampled titles.
3. Source evaluation scorer integration: add title-affinity fields only if persistence is needed.
4. Recommendation-fit outcome refinement: distinguish no results, errors, blocked results, metadata-missing results, and genuinely low-score results.
5. Local outcome learning: add source outcome boosts from later user behavior.
6. Documentation and tests: update current state, next work, source evaluation docs, and encyclopedia.

## Recommendation

Move forward with a source-evaluation refinement, but do not frame it as "make weak sources stronger." Frame it as:

> "Separate source catalogue quality from metadata availability, then add a bounded local title-affinity fallback for sources that do not expose tags."

That directly explains why Elf Toon can be valuable in practice while still scoring weak today.

## Files Most Relevant For A Future Fix

Code:

- `app/src/main/java/exh/recs/evaluation/SourceEvaluationRunner.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScorer.kt`
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationFitProbe.kt`
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationFitScorer.kt`
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationFitEligibility.kt`
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationFitDisplayPolicy.kt`
- `app/src/main/java/exh/recs/PersonalRecommendationScorer.kt`
- `app/src/main/java/exh/recs/RecommendationQueryPlanner.kt`

Tests:

- `app/src/test/java/exh/recs/evaluation/`
- `app/src/test/java/exh/recs/PersonalRecommendationScorerTest.kt`
- `app/src/test/java/exh/recs/RecommendationScorerTest.kt`

Docs:

- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/KMK_RECS_SOURCE_CATALOGUE_FIT_AND_RETRIEVAL_QUALITY_FEASIBILITY_AUDIT.md`
- `docs/KMK_MARKDOWN_ENCYCLOPEDIA.md`

