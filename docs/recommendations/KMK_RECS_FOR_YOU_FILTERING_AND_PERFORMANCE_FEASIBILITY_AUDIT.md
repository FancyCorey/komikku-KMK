# KMK For You Filtering And Performance Feasibility Audit

Date: 2026-07-11

Status: feasibility and quality audit only. This document does not authorize implementation.

Scope:

- Verify the current KMK-Recs v0.7.39 For You implementation against its documentation.
- Identify current performance, storage, quality, and test limitations.
- Assess which filters can honestly address old, cancelled, discontinued, or unsuitable manga.
- Recommend a bounded next direction without changing app code.

Related records:

- `CURRENT_STATE.md`
- `KMK_RECS_V0_7_38_RECOMMENDATION_DISCOVERY_MEMORY_AND_GROUP_SEED_ENRICHMENT_IMPLEMENTATION.md`
- `KMK_RECS_V0_7_39_FOR_YOU_ROLLING_DISCOVERY_AND_RATED_GROUP_RECOMMENDATIONS_IMPLEMENTATION.md`
- `NEXT_WORK.md`

---

## Executive Conclusion

The For You feature is viable, and v0.7.39 materially improved its ability to discover candidates beyond a source's first page. The main current limitation is not that the scorer seeks an absolute match. Candidate scoring is additive: preferred tags, learned positive and negative tag weights, blocked tags, and a small source-affinity signal determine each returned candidate's ordering.

The real constraint is candidate discovery. The app can only score manga that an extension returns from its own search/filter API. It currently builds at most two tag-oriented queries per source, searches page one on every forced refresh, and progressively probes one additional page per source. A source with weak tags, unreliable search, no useful filters, or an old-first catalogue can therefore still feed poor candidates into an otherwise reasonable scorer.

Filtering old manga is only partly feasible without source-specific work or an external metadata provider. Komikku's common `SManga` model exposes title, author, artist, description, genres, status, thumbnail, and initialized state. It does not expose a universal original-publication date, series start date, or latest chapter upload timestamp for network results. A universal "published after year X" control would either be inaccurate or exclude every source that lacks that metadata.

The sensible next direction is a compact, two-layer For You control model:

1. First fix the discovered correctness and refresh-cost issues.
2. Add only universal filters that use metadata actually available from all/most source responses, with conservative fail-open behavior for unknown data.
3. Treat recency as a source-capability feature, not a universal age promise.

---

## What The Current For You Feed Actually Does

### Profile and score inputs

`GetTasteProfile` builds the profile from all persisted manga ratings, explicit tag preferences, and tag aliases.

- Love contributes `+2` to every available genre/tag on that manga.
- Like contributes `+1`.
- Dislike contributes `-2`.
- Explicit preferred tags add `+3` per matching group at candidate score time.
- Explicit disliked tags subtract `-2` per matching group.
- Explicit blocked tags hard-reject a candidate when any resolved genre matches.
- Source affinity is intentionally weak: net positive ratings from a source times `0.1`.
- Learned tag weights are clamped to `[-10, 10]` per tag group.

`PersonalRecommendationScorer` has no exact-match requirement. A candidate can rank well through partial positive overlap; negative tags lower its score; blocked tags reject it. The scorer currently does not use title similarity, author, publication year, current chapter count, quality, popularity, or external tracker score.

### Candidate discovery and source querying

`BrowsePersonalRecommendationsScreenModel` derives up to five positive top tags and constructs at most two plans for each source:

1. the last successful strategy, or `TOP_TAGS_FILTER` by default;
2. one fallback: usually a tag-pair query, otherwise a text-only query.

For each live plan, the app loads the source filter list, maps tags/aliases to available source filters where possible, performs page-one search, localizes returned manga, applies local filters, optionally enriches sparse manga detail, ranks candidates, and persists a cache/memory result.

The feed searches sources in priority order, in batches of five concurrent sources. It can attempt up to 40 sources to obtain 20 useful visible rows. The first three eligible sources have a 20-result cap; other sources have a 10-result cap. Top Picks is synthesized from the already fetched row results and launches no second crawl.

### Existing controls already present

- Recommendation languages.
- Source priority and three boosted sources.
- Per-source disable/dislike behavior.
- Preferred, disliked, blocked, and alias tag preferences.
- Rated-manga visibility (`hide all rated`, `hide disliked only`, or show rated).
- Hide known manga (rated, library/favorite, started, read, or local history).
- Always hide explicitly marked Seen manga.
- Minimum locally known chapter count: Off, 5, 10, 20, or 50.
- Metadata enrichment cap: default 5 candidates per normal source, doubled for boosted sources, configurable from 1 to 20.
- Reset For You discovery history.

The chapter-count control is not a reliable global chapter-count filter. It only filters when a chapter count already exists in the local database; untracked remote candidates have no stored chapters and intentionally pass through.

---

## Verified v0.7.39 Improvements

The current worktree includes the v0.7.39 discovery-memory and progress implementation described in its record.

- `recommendation_candidate_memory` retains discovered candidates locally, capped at 500 entries per source.
- `recommendation_discovery_progress` separately records evaluated pages, including empty and filtered pages.
- An additional page is probed gradually, at most one extra page per source per forced refresh, with a cumulative cap of 20 pages for a source/query.
- Older candidates are re-scored against the current taste profile when they can be resolved from the local database.
- Group-seeded rated-manga recommendations already use cross-source link groups and can enrich sparse linked metadata under bounded timeouts.

These are sound foundations. They prevent the old v0.7.38 behavior where an empty or filtered page was endlessly retried because only pages with stored candidates counted as discovered.

---

## Verified Imperfections and Risks

### P0 - Newly discovered candidates can be invisible until a later refresh

In `searchSource`, page-one recommendations and `additionalResults` are collected, but if `resolvedMemory` is empty the returned list is only `recommendations` from page one. The additional-page candidates are saved to memory but excluded from that first result.

Effect: on the first discovery pass for a source, a potentially better page-two candidate cannot appear until another refresh, despite already having been fetched and stored. This weakens the intended "compare the best from all evaluated pages" behavior.

Required correction before broader feature work: merge `recommendations + additionalResults` even when prior memory is empty, then rank and apply the same filters once.

### P0 - Failed extra-page requests are treated as permanently evaluated

The v0.7.39 implementation report states that a request failing before results arrive will be retried. The code does not currently implement that distinction for extra pages: `discoverAdditionalPage()` catches a request failure, records `STATUS_ERROR`, and the planner advances to the next page on later refreshes.

Effect: a temporary outage, Cloudflare event, or source-side transient failure can permanently skip a page until the user resets discovery history. It can also produce gaps in the candidate pool.

Required correction: distinguish stable unsupported/error outcomes from retryable transport failures. Store retry metadata such as attempt count and `retryAfter`, use bounded exponential backoff, and only permanently skip a page when the failure is classified as non-retryable or exhausts a small retry budget.

### P1 - A forced refresh is still intentionally expensive

Manual refresh bypasses the 24-hour cache. In the worst practical case it can:

- attempt 40 sources;
- issue up to two page-one search strategies per source;
- probe one further page per source;
- process up to 60 raw candidates for each boosted source and 30 for each normal source;
- write localized network manga to the local manga database;
- enrich up to the configured detail cap per source, with boosted sources using twice that cap.

The coroutine dispatcher limits I/O work to five parallel tasks, which protects the device from unbounded concurrency, but it also means a slow or unreliable source batch delays subsequent batches. A page that yields too few useful results causes its fallback plan to run, increasing calls further.

This behavior explains a slow, crowded refresh better than local score calculation does. The current design is safe in the sense that it is bounded, but the high defaults are not lightweight for a tablet with many installed extensions.

### P1 - Candidate localization creates persistent local rows

`NetworkToLocalManga` delegates to `MangaRepository.insertNetworkManga`. For You calls it for all fetched page-one and additional-page candidates before ranking. Therefore, discovery is not only an in-memory operation; exploring many sources/pages grows the local manga table even when the user never opens or favorites those manga.

This is compatible with the app's existing browse/search architecture, but it should be treated as a cache/storage cost. Candidate-memory pruning does not necessarily delete those local manga rows.

Needed before aggressive discovery expansion: measure retained non-library manga growth and confirm the project's normal cleanup behavior. Do not add larger page/batch defaults until this is understood.

### P1 - Ranking semantics differ between immediate and remembered candidates

The immediate `rankCandidates()` path filters only blocked candidates; zero or negative score candidates may remain when a source returns little else. The later memory merge additionally drops `score <= 0.0`.

Effect: the same candidate can be visible immediately but disappear after a later refresh without any taste change. This can make source rows feel inconsistent and lets weak/old candidates occupy slots during the first fetch.

Recommended correction: define one explicit eligibility threshold and use it in both live and remembered paths. A conservative baseline is `score > 0`, with a documented fallback only when the user asks to see low-confidence results.

### P1 - Discovery progress is keyed too broadly for changing tastes and source behavior

Progress is keyed by source and sorted top-tag query. It records a profile fingerprint but does not use that fingerprint to invalidate or partition progress. A user can substantially change ratings while retaining the same top tags, and old page progress will still prevent those pages from being revisited. Similarly, source catalogue order or filter behavior can change while prior page outcomes remain trusted.

Recommended direction: retain the current history by default, but add bounded refresh policies:

- automatically reconsider stale progress after a reasonable age;
- provide a targeted "re-evaluate discovery for current taste" action;
- optionally reset only selected sources, not all source history.

### P2 - Page one is re-fetched on every manual refresh

The rolling system advances only through forced refreshes, but every forced refresh also re-fetches page one for every attempted source. This is useful for freshness, yet it means the common path repeatedly spends most of its budget on the same first page before it reaches one new page.

Potential improvement: separate a normal "refresh results" action from an explicit "discover more" action, or use a per-source freshness TTL so a source with a recent successful page one can spend the next refresh budget on its next page instead.

### P2 - Filtering and source capability are conflated by query-time tag mapping

The query planner maps top tags to each source's filters only when the source exposes compatible labels. Otherwise it falls back to text. This is a reasonable best-effort strategy, but it cannot infer missing metadata or repair a source whose catalogue/search endpoint is poor.

This is why no universal tag query can guarantee an equally good result from every extension. The current alias/synonym map helps common labels such as Yuri/Girls Love or Sci-Fi/Science Fiction, but source-specific taxonomies remain inherently uneven.

### Test gaps found in this audit

Current planner and ranker tests cover page progression and basic memory merging. They do not directly cover:

- newly fetched additional-page candidates appearing in the same refresh with initially empty memory;
- retryable network failure versus permanent/unsupported page outcomes;
- consistent score-threshold eligibility in live and remembered paths;
- profile or source-catalogue change policy for progress records;
- bounded storage behavior caused by repeated `NetworkToLocalManga` discovery;
- end-to-end refresh budget: source count, plan count, page count, enrichment count, and cancellation.

---

## Feasibility of User-Facing Filters

| Filter idea | Feasible universally? | Recommendation |
| --- | --- | --- |
| Hide known / seen / rated manga | Yes; already present | Keep. Clarify the interaction of rated visibility versus hide-known in UI copy. |
| Block tag / prefer tag / dislike tag | Yes, best effort across tags | Keep. Query-time mapping is source-capability dependent; post-fetch scoring remains essential. |
| Minimum chapter count | Partly | Keep as an optional local-data filter. Do not describe it as universal for unseen remote manga. |
| Hide cancelled / on hiatus / licensed | Partly | Feasible from `SManga.status` when supplied. Unknown status must pass by default so metadata-poor sources are not emptied. |
| Only ongoing / only completed | Partly | Same status limitation. Good as an optional display filter with an "unknown included" default. |
| Published after year / avoid old manga | No, not universally | Do not implement as a universal hard filter. The common network model lacks release dates. |
| Prefer recently updated series | Not universally | Possible only as a source-capability-aware option if a source exposes an update sort/filter or trustworthy latest data. |
| Prefer recently discovered candidates | Yes, but not content age | Could be a ranking tie-breaker; label it honestly as discovery freshness, not manga recency. |
| Hide low-confidence candidates | Yes | Recommended. Requires a single eligibility threshold plus clear empty-state behavior. |
| Limit sources / refresh effort | Yes | Strongly recommended. This is the most direct practical performance control. |
| Include only selected sources | Yes | Existing priorities/disable controls cover most of this; a compact "active For You sources" subset could reduce clutter further. |

### Why a real age filter is not currently honest

`SManga` has no date field. Local `Manga.lastUpdate` and `dateAdded` describe the app database record, not reliable series publication or chapter-release age for a newly returned network candidate. Querying detail/chapter data for every candidate solely to infer recency would be much slower and source-specific, exactly the kind of overhead this audit should avoid.

A future date filter would need one of these foundations:

1. a reliable per-source capability adapter that identifies a supported "latest" sort/filter and metadata semantics;
2. a centrally licensed/maintained metadata provider with stable external IDs; or
3. explicit user curation by source.

None should be silently approximated from title, local insertion time, or page number.

---

## Recommended Product Direction

### First: stabilize and simplify the current refresh pipeline

Before adding new filters, fix the P0/P1 correctness issues above. They directly affect both quality and load, and they are much smaller than an age system.

Then introduce a small "For You controls" surface that does not overload the main feed:

- Result status: All, Ongoing only, Completed only, Exclude cancelled/hiatus/licensed.
- Discovery mode: Best known, Prefer new discoveries, and Discover more.
- Refresh effort: Balanced (default), Faster, Thorough.
- Source scope: current enabled sources, with a concise count and shortcut to source priority.

The controls should be persisted in recommendation preferences, included in the cache/profile fingerprint where they alter returned results, and visible in a compact filter summary/chip rather than permanently consuming vertical page space.

### Suggested effort presets

| Preset | Source attempts | Additional pages | Enrichment | Intended use |
| --- | --- | --- | --- |
| Faster | 10-15 | none or one total | 1-2 per source | quick check while reading |
| Balanced | 20 | one only for sources that need it | 3-5 per source | normal default |
| Thorough | current 40 maximum | one per eligible source, bounded | current configurable cap | explicit user-requested exploration |

These are product targets, not approved constants. The final values should be benchmarked on a midrange phone and the user's tablet. A manual refresh should cancel cleanly, preserve existing successful rows while new batches load, and never require full-page blanking to begin a new discovery pass.

### Status filtering policy

Status can help with cancelled, hiatus, and completed manga. It cannot reliably identify age. The safe rule is:

- Filter only when a source explicitly returns a recognized status.
- Keep unknown-status results by default.
- Show a small explanation that some sources do not provide status metadata.
- Apply the filter after fetch initially; only push it into a source query when the source filter capability is positively identified.

### Recommendation quality policy

Use ranking as comparison, not replacement. A refresh should retain strong candidates from every previously evaluated page, while new pages add challengers. It should not replace the existing best set merely because it explored a later page.

To avoid a feed full of weak old material:

- use one score eligibility rule across live/memory paths;
- make "show lower-confidence matches" an explicit opt-in if needed;
- prefer user-selected source priority and recent discovery as tie-breakers, not as a false claim of publication recency;
- preserve all existing hard exclusions: blocked tags, seen, known/rated visibility, source exclusions, and min-chapter behavior.

---

## Recommended Implementation Sequencing (Not Yet Approved)

1. Correct v0.7.39 discovery handling and add the missing tests.
2. Instrument and benchmark the refresh budget on actual devices. Record source attempts, plans used, pages probed, candidates localized, details enriched, duration, cancellation, and failures. This should be developer diagnostics, not permanent user-facing noise.
3. Add the universal status filters and a shared eligibility threshold.
4. Add effort presets and a source-scope control, then benchmark again.
5. Evaluate optional source-capability-aware "latest" support for a small, high-value source set. Keep it staged and opt-in. Do not introduce a fake universal year/recency filter.

This sequence prevents the app from becoming more complicated before we have fixed the known causes of slow refreshes and inconsistent discovery.

---

## Decision Needed Before an Implementation Plan

No code should be changed from this audit alone. The next implementation plan should be selected after deciding:

- whether to prioritize the v0.7.39 correctness/performance fixes alone first; or
- whether to combine them with a small first filter set: status filtering, eligibility threshold, and refresh-effort presets.

My recommendation is to do the correctness/performance fixes and instrumentation first, then make the filter UI based on measured behavior. It is the shortest path to a For You feed that feels faster without turning the settings into an elaborate promise the underlying extensions cannot satisfy.

