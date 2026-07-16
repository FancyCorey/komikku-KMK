# Installed Source Fit Feasibility Research

Date: 2026-06-16

Status: research assessment only. Do not implement from this file without a separate user-approved implementation plan.

## Research Question

Can Komikku use the user's currently installed extensions/sources to identify which sources are actually useful, reliable, and worth prioritizing for Browse > For You recommendations?

This is different from cross-extension identity matching.

The goal is not primarily:

```text
Is this manga the same manga on another source?
```

The goal is:

```text
Which installed sources produce the best recommendation results for this user's taste profile?
```

## Short Answer

Yes, this is feasible for installed sources.

It is not feasible as a perfect universal quality detector across every extension, but it is realistic to build a local, source-fit system that learns from recommendation runs, user ratings, source errors, filtering outcomes, duplicates, and source-result usefulness.

The best implementation would be:

```text
Installed Source Fit / Best Sources For You
```

It should evaluate only sources the app can actually search. It should learn gradually from real For You runs and user behavior. It should not require crawling whole websites or maintaining custom logic for every extension.

## Current Komikku/KMK-Recs Support

### Existing For You Source Pipeline

Relevant file:

```text
app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt
```

Current Browse > For You already:

- builds a taste profile from explicit ratings and tag preferences,
- filters sources by recommendation language,
- applies manual source priority,
- excludes disabled recommendation sources,
- searches sources in bounded batches,
- attempts up to 40 sources,
- stops when 20 useful rows are found,
- gives the top 3 priority sources larger result/enrichment caps,
- records per-source last-run status,
- builds Top Picks from already-fetched per-source results,
- caches source recommendation rows using a profile fingerprint.

This is already most of the infrastructure needed to evaluate installed source usefulness.

### Existing Source Run Statuses

Relevant file:

```text
app/src/main/java/exh/recs/RecommendationSourceRunStatus.kt
```

Current statuses:

- `Shown`
- `NoMatches`
- `FilteredOut`
- `Error`
- `Disabled`
- `OutsideAttemptLimit`
- `HiddenByDuplicateHandling`

These are currently diagnostic and last-run only. They already answer basic questions such as:

- Did the source return anything?
- Did the source produce visible recommendations?
- Were all results filtered out?
- Did the source fail?
- Was the source skipped because the attempt limit was reached?
- Did another source beat it through duplicate handling?

This can be extended into longer-term source-fit scoring.

### Existing Per-Source Recommendation Data

The For You pipeline already knows, per source:

- raw search results existed or did not exist,
- visible recommendation count,
- whether results survived blocked/rated/favorite/known filtering,
- whether the source errored,
- the query strategy that succeeded,
- candidate scores,
- matched preference groups,
- whether the source contributed to Top Picks,
- whether candidates were hidden by cross-source dedupe.

This is enough to compute useful local source quality stats.

### Existing Cache

The recommendation cache stores per-source recommendation rows. It is versioned and fingerprinted by relevant profile inputs.

This helps source-fit evaluation because cached rows can avoid repeated network calls, but source quality stats should not rely only on cache hits. They should distinguish:

- live run success,
- cached success,
- live errors,
- no-match outcomes,
- filtered-out outcomes.

### Existing User Signals

The taste system already stores:

- explicit Love/Like/Dislike ratings,
- explicit preferred/disliked/blocked tag groups,
- learned tag weights,
- source affinity in the taste profile.

Those can support source-fit scoring by answering:

- Did the user later love/like a manga from this source?
- Did the user dislike candidates from this source?
- Does this source repeatedly produce blocked-tag results?
- Does this source contribute candidates matching high-weight tags?

## Evidence From Related Ecosystem

### Mihon / Forks Use Installed-Source Global Search

Mihon's source migration guide states that migration performs a global search of installed and enabled sources when migrating a series/source. This confirms the ecosystem's practical model: installed sources can be queried and compared; non-installed sources generally cannot be evaluated with the same certainty.

Source: https://mihon.app/docs/guides/source-migration

### Extensions Are Third-Party And Source Behavior Is Not Uniform

Mihon's extension FAQ says Mihon does not provide extensions or repositories, and notes that source slowness, downtime, missing chapters, and quality problems are outside the app's control.

Source: https://mihon.app/docs/faq/browse/extensions

This supports the need for local observed quality scoring instead of assumptions about all extensions.

### Keiyoushi Provides Extension Metadata, Not Quality

Keiyoushi supports modern variants such as TachiyomiSY, Komikku, Yokai, TachiyomiJ2K, and TachiyomiAZ.

Source: https://keiyoushi.github.io/docs/guides/getting-started

The extension repo index can expose metadata such as extension package, source names, language, version, and APK information. This helps list available/installed sources but does not tell Komikku whether a source returns good recommendations for this user.

Source: https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json

### Source API Gives Search/Filter/Detail, Not Quality Metrics

Mihon's `CatalogueSource` API exposes:

- `getPopularManga(page)`,
- `getSearchManga(page, query, filters)`,
- `getLatestUpdates(page)`,
- `getFilterList()`.

Source: https://raw.githubusercontent.com/mihonapp/mihon/main/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/CatalogueSource.kt

The `SManga` model exposes metadata fields such as title, author, artist, description, genre, status, thumbnail, and URL.

Source: https://raw.githubusercontent.com/mihonapp/mihon/main/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/model/SManga.kt

This means Komikku can compare observed results, but extensions do not expose a standard "recommendation quality" score.

### Neko Is A Useful Contrast, Not A Direct Model

Neko is a MangaDex-specific app. Its README describes custom similar manga recommendations and user recommendations from external databases, plus MangaDex sync.

Source: https://github.com/nekomangaorg/Neko

That works better because MangaDex is a single canonical source. Komikku's installed extensions represent many separate websites, so source fit must be learned empirically.

## Can Installed Extensions Be Evaluated For Recommendation Quality?

Yes.

For installed sources, Komikku can actually search them and observe outcomes. This makes source-fit learning realistic.

Strong local signals:

- source returned visible recommendations,
- source returned no matches,
- source returned results but they were filtered out,
- source errored,
- source repeatedly produced candidates matching preferred tags,
- source repeatedly produced candidates with blocked/disliked tags,
- source contributed candidates to Top Picks,
- source produced candidates later rated Love/Like/Dislike,
- source produced candidates hidden as duplicates of higher-ranked sources,
- source required fallback query strategies often,
- source returned metadata-rich candidates after enrichment.

Weak or risky signals:

- source name alone,
- extension popularity,
- repo metadata alone,
- one successful run,
- one failed run,
- title-only duplicate frequency,
- raw result count without quality scoring.

## Do Different Extensions Expose Enough Useful Data?

Enough for a local source-fit score, not enough for a universal quality score.

Commonly available through the source API:

- search results,
- filters,
- basic manga metadata,
- manga details,
- chapter lists,
- popular/latest pages.

Not guaranteed:

- meaningful tags,
- reliable author/artist,
- accurate status,
- search relevance,
- aliases,
- website recommendations,
- full chapter counts without extra calls,
- source-specific "related manga" sections.

Conclusion: the app can compare observed recommendation outcomes, but should not assume all extensions expose the same quality of metadata.

## Can Source Quality Be Learned Locally?

Yes. This is the strongest path.

Suggested local stats:

```text
source_id
run_count
shown_count
no_match_count
filtered_out_count
error_count
hidden_by_duplicate_count
visible_candidate_count
top_picks_contribution_count
liked_candidate_count
loved_candidate_count
disliked_candidate_count
blocked_tag_candidate_count
avg_candidate_score
avg_matched_group_count
last_success_at
last_error_at
updated_at
```

These can produce useful derived scores:

- reliability score,
- recommendation fit score,
- noise score,
- blocked-tag risk,
- metadata quality,
- Top Picks contribution rate,
- user-positive rate,
- user-negative rate.

This should be local and bounded. It should not require uploading user behavior or crawling entire source catalogs.

## Can Websites Behind Extensions Provide Useful Recommendation Behavior?

Sometimes, but not universally.

Some websites provide:

- related manga sections,
- genre pages,
- popular/trending lists,
- user recommendation widgets,
- tag filters,
- advanced search.

But the extension API does not standardize "related manga." Using website-native recommendation sections would require either:

- source-specific extension support,
- custom parsers per source,
- or brittle web scraping.

Recommendation: do not make website-native recommendation behavior part of the core source-fit system. Treat it as optional future enhancement for a small number of high-value sources.

## Can This Be Done Universally?

Partially.

Universal generic source-fit scoring is feasible if it uses common observed outcomes:

- success/no match/error,
- visible result count,
- filter survival,
- candidate scores,
- user ratings,
- Top Picks contribution.

Universal source-specific quality parsing is not feasible. The app should not try to maintain custom rules for every extension.

## Is Source-Specific Logic Maintainable?

Not at large scale.

Reasons:

- many extensions,
- websites change frequently,
- search behavior differs by source,
- tag/filter names differ,
- some sources disappear or break,
- source-specific logic would need constant maintenance.

Maintainable compromise:

- generic scoring for every installed source,
- optional curated source profiles for only important/popular sources,
- user-controlled override and manual priority always available,
- learned source-fit score should suggest, not silently force.

## Most Realistic Implementation Options

### Option A: Last-Run Diagnostics Only

Already mostly implemented.

Shows whether a source was useful in the last For You refresh.

Pros:

- low risk,
- already exists,
- easy to understand.

Cons:

- not enough to recommend source ordering over time.

Feasibility: already done.

### Option B: Rolling Source Fit Stats

Persist rolling source stats across For You runs.

This is the recommended next step.

Pros:

- uses data the app already collects,
- no extra crawling,
- works across installed sources,
- can gradually learn which sources are best for this user.

Cons:

- needs careful scoring,
- needs decay/windowing so old behavior does not dominate,
- must avoid punishing a source too strongly for temporary downtime.

Feasibility: high.

### Option C: Source Priority Suggestions

Use source-fit scores to suggest changes to the user's priority list.

Example:

```text
Suggested top sources: Asura Scans, MangaFire, QI Scans.
Apply suggested order?
```

Pros:

- useful and understandable,
- keeps user control,
- avoids surprising automatic reorder.

Cons:

- requires clear explanation/reasons.

Feasibility: high after Option B.

### Option D: Automatic Source Boosting

Automatically boost high-fit sources in For You.

Pros:

- improves results with less user work.

Cons:

- can confuse the user if manual priority appears ignored,
- needs clear settings and explainability.

Feasibility: medium. Should come after source priority suggestions.

### Option E: Installed Source Audit Screen

Add a settings screen or panel showing:

- best sources,
- reliable sources,
- noisy sources,
- sources with repeated no matches,
- sources with frequent errors,
- sources that contribute to Top Picks.

Pros:

- excellent transparency,
- helps the user decide what to disable/reorder.

Cons:

- UI work,
- stats need enough runs before being meaningful.

Feasibility: high after Option B.

### Option F: Website-Native Recommendation Support

Use source websites' related/recommendation sections.

Pros:

- potentially strong for some sources.

Cons:

- no standard API,
- source-specific and fragile,
- not realistic for all extensions.

Feasibility: low universally; medium for a small curated set.

## Recommended Scoring Model

Use a conservative rolling score rather than one absolute number.

Suggested components:

### Reliability

Positive:

- successful runs,
- recent successful runs,
- stable response without errors.

Negative:

- repeated errors,
- repeated no matches,
- very stale success.

### Fit

Positive:

- high average candidate score,
- many matched preferred groups,
- candidates entering Top Picks,
- user later rates candidates Like/Love.

Negative:

- candidates repeatedly disliked,
- candidates repeatedly blocked,
- results filtered out often.

### Noise

Negative:

- many raw results but few visible results,
- duplicate-hidden often,
- poor metadata causing weak scoring,
- blocked-tag candidates often.

### Confidence

Do not strongly rank a source until enough observations exist.

Example:

```text
confidence = min(run_count / 5.0, 1.0)
final_score = confidence_adjusted(reliability + fit - noise)
```

This prevents one lucky run from making a source look perfect.

## Efficient Data Collection

Do not run extra searches just to score sources.

Use:

- For You live runs,
- For You cached results where appropriate,
- user ratings/favorites,
- Top Picks accumulation,
- existing source statuses.

Avoid:

- background crawling all installed sources,
- fetching all pages,
- fetching chapter lists for source scoring,
- per-source website scraping,
- network calls just to update stats.

## Staged Rollout

### Stage 1: Research/Design

Completed by this document.

### Stage 2: Persist Source Fit Stats

Add a local source-fit stats store updated after each For You run.

No UI changes required beyond optional debug/logging.

### Stage 3: Show Source Fit In Recommendation Settings

Add compact labels:

- Great fit,
- Reliable,
- No matches recently,
- Often filtered,
- Often errors,
- Too little data.

### Stage 4: Suggest Source Priority

Offer a manual "Apply suggested priority" action.

Do not auto-reorder by default.

### Stage 5: Use Fit As A Soft Boost

Optionally let high-fit sources receive boosted priority, but only with a setting and clear explanation.

### Stage 6: Optional Curated Source Profiles

Only for a small set of popular sources. Do not make this required for the generic system.

## Final Feasibility Rating

### Installed-source quality learning

Feasibility: high.

Reason: the app already searches installed sources and records enough outcomes to build rolling stats.

### Best source suggestions for current user

Feasibility: high.

Reason: source-fit scoring can combine observed recommendation quality and user feedback.

### Automatic perfect source ordering

Feasibility: medium to low.

Reason: sources are unstable and user preference can shift. Suggestions should remain user-approved.

### Non-installed extension recommendation

Feasibility: low to medium.

Reason: repo metadata can suggest likely candidates, but the app cannot evaluate live recommendation quality until installed.

### Website-native recommendation extraction

Feasibility: low universally, medium for selected sources.

Reason: no standard API exists across extensions.

### Universal source-specific logic

Feasibility: low.

Reason: too many extensions and websites to maintain rules for all of them.

## Recommendation

Move forward, but modify the feature goal to:

```text
Installed Source Fit Learning
```

Do not attempt:

```text
Perfect extension finder across all installed and non-installed extensions
```

The most practical feature is:

```text
Best Sources For You
```

It should:

- evaluate installed sources from real For You runs,
- persist rolling source-fit stats,
- explain why a source is good/bad,
- suggest priority changes,
- keep manual user control,
- avoid extra network load,
- avoid universal source-specific rules.

This feature should move forward after the current recommendation system is stable.

## Sources

- Mihon source migration guide: https://mihon.app/docs/guides/source-migration
- Mihon extensions FAQ: https://mihon.app/docs/faq/browse/extensions
- Keiyoushi getting started: https://keiyoushi.github.io/docs/guides/getting-started
- Keiyoushi repo index: https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json
- Mihon `CatalogueSource` API: https://raw.githubusercontent.com/mihonapp/mihon/main/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/CatalogueSource.kt
- Mihon `SManga` model: https://raw.githubusercontent.com/mihonapp/mihon/main/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/model/SManga.kt
- Neko README: https://github.com/nekomangaorg/Neko


