# Cross-Extension Identity Feasibility Research

Date: 2026-06-16

Status: research assessment only. Do not implement from this file without a separate user-approved implementation plan.

## Question

Can Komikku realistically identify the same manga across different source extensions well enough to support:

- cross-extension recommendations,
- rating synchronization,
- favorite synchronization,
- source/extension discovery,
- or future cross-source identity groups?

## Short Answer

Yes, but only if the feature is framed as a confidence-based, user-confirmed workflow rather than a universal automatic identity system.

Komikku can already search across installed catalogue sources, compare candidates, and ask the user to confirm matches. That is enough for useful cross-extension rating/favorite workflows and future saved identity groups.

Komikku cannot reliably know every equivalent manga across every extension automatically because each extension is only a wrapper around a separate website. Search behavior, title naming, aliases, tags, authors, chapter numbering, and recommendation quality vary heavily by website.

The practical path is staged:

1. Use installed-source global search and migration logic for candidate discovery.
2. Rank candidates using conservative signals.
3. Require user confirmation before syncing ratings/favorites.
4. Save confirmed links locally so future actions become faster and safer.
5. Later, optionally add source-specific profiles for high-value sources.

## Evidence From Current Komikku/KMK-Recs

### Normal Global Search

Relevant file:

```text
app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/globalsearch/SearchScreenModel.kt
```

Current behavior:

- Searches selected visible catalogue sources.
- Calls each source's `getSearchManga(1, query, source.getFilterList())`.
- Converts returned network manga into local manga rows through `NetworkToLocalManga`.
- Runs sources concurrently with a fixed thread pool.
- Normal global search is intentionally uncapped unless a subclass opts in through `perSourceResultLimit`.

This is useful for candidate discovery but does not itself prove identity. It only answers "what did this source return for this query?"

### Current Cross-Extension Matching

Relevant files:

```text
app/src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt
app/src/main/java/exh/recs/matching/CrossExtensionMatchScreen.kt
domain/src/main/java/tachiyomi/domain/taste/interactor/SetMangaTasteBatch.kt
```

Current behavior as of KMK-Recs v0.5.1:

- Manga detail rating dropdown includes "Love/Like/Dislike other versions."
- Matching workflow searches installed recommendation sources by the current manga title.
- Results are capped at 2 per source.
- Origin manga is filtered out.
- Non-origin candidates are selected by default.
- User can deselect bad matches before applying the rating.
- Batch rating writes use `(source, url)` identity.

This proves that a bounded, user-confirmed workflow is already feasible.

### Migration Smart Search

Relevant files:

```text
app/src/main/java/mihon/feature/migration/list/search/BaseSmartSearchEngine.kt
app/src/main/java/mihon/feature/migration/list/search/SmartSourceSearchEngine.kt
app/src/main/java/mihon/feature/migration/list/search/SourceMatchScorer.kt
app/src/main/java/mihon/feature/migration/list/MigrationListScreenModel.kt
```

Migration already solves part of this problem. It searches other sources for a replacement manga and can score matches by:

- normalized title similarity,
- shared tags,
- chapter coverage,
- latest chapter number,
- status match.

The current `SourceMatchScorer` weights title similarity, tag overlap, chapter coverage, and status. `BaseSmartSearchEngine` also includes a deep-search mode that searches the cleaned title, largest words, and first words to handle noisy titles.

This is the strongest local evidence that cross-source matching can be improved without inventing everything from scratch.

Important limitation: migration chooses a target for a replacement/copy workflow. It does not maintain a permanent identity graph across sources.

### Manga-Detail Cross-Extension Genre Recommendations

Relevant file:

```text
app/src/main/java/exh/recs/sources/CrossExtensionGenreSearchSource.kt
```

This system searches a single installed catalogue source by genre filters or title fallback, enriches a capped number of results with details, and lets the recommendation scorer compare them.

It is useful for recommendations, but it is not an identity resolver. It finds "similar manga," not necessarily "the same manga."

### Browse > For You / Top Picks

Relevant docs:

```text
docs/recommendations/CURRENT_STATE.md
docs/recommendations/TOP_PICKS_FILTERING_AND_EXCEPTION_HANDLING_IMPLEMENTATION.md
docs/recommendations/KMK_RECS_V0_4_3_ADAPTIVE_FILL_SOURCE_STATUS_AND_TOP_PICKS_IMPLEMENTATION.md
```

Current For You already has:

- explicit ratings and tag preferences,
- source priority,
- source statuses,
- Top Picks ranking,
- conservative duplicate handling by exact normalized title plus exact author or artist,
- known-manga filtering from local DB signals.

This helps recommendation quality, but conservative duplicate handling is not enough to safely synchronize favorites/ratings across sources.

## Evidence From Upstream And Related Ecosystem

### Mihon / Komikku / Tachiyomi-Family Migration

Mihon's public migration guide says source migration performs a global search across installed and enabled sources when migrating from a series or source. This confirms that the ecosystem's accepted migration model is search-and-select, not a universal cross-source identity database.

Source: https://mihon.app/docs/guides/source-migration

### Mihon Extension Model

Mihon documents that it does not provide or associate with extension repositories, and warns that repositories/extensions are third-party. It also states sources may be slow, down, missing chapters, or have subpar image quality. This supports the conclusion that source behavior cannot be treated as uniform or centrally guaranteed.

Source: https://mihon.app/docs/faq/browse/extensions

### Keiyoushi Extension Repository

Keiyoushi provides an extension repository used by modern variants including Mihon, TachiyomiSY, Komikku, Yokai, TachiyomiJ2K, and TachiyomiAZ.

Source: https://keiyoushi.github.io/docs/guides/getting-started

Its repo index exposes extension/source metadata such as package name, apk, language, version, nsfw flag, and source names. This helps extension discovery, but it does not provide a universal manga catalog or canonical cross-source manga IDs.

Source: https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json

### Source API Shape

Mihon's source API exposes a generic `CatalogueSource` with `getSearchManga(page, query, filters)`, `getFilterList()`, and basic manga detail/chapter methods.

Sources:

- https://raw.githubusercontent.com/mihonapp/mihon/main/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/CatalogueSource.kt
- https://raw.githubusercontent.com/mihonapp/mihon/main/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/Source.kt
- https://raw.githubusercontent.com/mihonapp/mihon/main/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/model/SManga.kt

The API gives each source freedom to implement search and filters differently. `SManga` has title, author, artist, description, genre, status, URL, and thumbnail fields, but no required canonical external manga ID.

### Neko / MangaDex Example

Neko is a special case because it is an unofficial MangaDex reader rather than a general multi-extension reader. Its README describes native MangaDex login, MangaDex syncing, and similar-manga recommendations. This is easier because MangaDex provides a single canonical catalog and stable MangaDex identity.

Source: https://github.com/nekomangaorg/Neko

That model does not directly transfer to Komikku's multi-extension world, where each source has its own website and URL identity.

### Fork Landscape

Mihon's endorsed forks page lists TachiyomiJ2K, TachiyomiSY, TachiyomiAZ, Yokai, and Komikku as alternative versions with their own feature sets. This supports researching the ecosystem, but the common migration pattern still appears to be global search plus user choice.

Source: https://mihon.app/forks/

## Is This Actually Possible?

Yes, in three levels:

### Level 1: Candidate Discovery

Already possible.

Use installed-source global search and migration smart search to find possible same-manga candidates.

Confidence: high.

### Level 2: User-Confirmed Matching

Already partially implemented for rating sync.

Search across installed sources, preselect likely candidates, let user deselect wrong matches, then apply action.

Confidence: high.

### Level 3: Automatic Universal Identity

Not realistically reliable across all extensions.

There is no mandatory canonical ID across websites. Some sources use English titles, some romanized titles, some aliases, some translated names, and some poor search behavior. Tags and authors may be missing or inconsistent.

Confidence: low.

## Is It Practical To Maintain Long-Term?

Practical if implemented as generic matching plus optional source profiles.

Not practical if implemented as hand-maintained source-specific logic for hundreds of extensions.

Maintainable approach:

- Reuse generic global-search/migration matching.
- Keep source-specific overrides optional and sparse.
- Store user-confirmed links locally.
- Learn from user confirmations.
- Keep all network work bounded and explicit.

Unmaintainable approach:

- Create custom matching rules for every source.
- Maintain a full local catalog of all extension websites.
- Crawl non-installed extensions.
- Automatically sync favorites/ratings without confirmation.

## Existing Mechanisms That Support Part Of This

### Global Search

Provides broad candidate discovery across installed/enabled sources.

### Migration

Provides a richer candidate matching pattern, especially smart/deep search and chapter-aware scoring.

### Manga Detail Cross-Extension Recommendations

Provides genre/filter-based candidate discovery and limited detail enrichment.

### For You / Top Picks

Provides personal preference scoring, source priority, source status, and conservative duplicate handling.

### Local DB Signals

Provides local knowledge about favorites, ratings, history, read state, and known manga, but only for entries Komikku has already seen.

## Major Technical Limitations

### No Universal Manga ID

Most source results are identified by `(source, url)`, not a canonical manga ID shared across websites.

### Search Quality Is Source-Dependent

The source website determines search behavior. Some sources return poor or unrelated results. Some require alternate title spellings.

### Metadata Is Inconsistent

Title, author, artist, genre, status, description, and chapter counts may be missing, stale, translated differently, or formatted differently.

### Detail Enrichment Costs Network Calls

Calling `getMangaDetails()` and `getChapterList()` improves confidence but can be slow, trigger rate limits, or increase failure rates.

### Non-Installed Extensions Cannot Be Fully Tested

Repo metadata can show extension/source names and languages, but without installing a source, Komikku generally cannot run its search code or inspect its live catalog.

### Source-Specific Logic Does Not Scale

Hundreds of sources and changing websites make comprehensive custom rules fragile.

### Website Recommendation Systems Are Uneven

Some websites may expose useful "related" or "recommended" sections, but extension APIs do not provide a standard field for this. Leveraging site recommendations would require source-specific parsing or extension changes.

## Most Efficient And Realistic Approaches

### Approach A: Strengthen User-Confirmed Matching

This should be the main path.

Use a matching workflow that:

- searches installed sources,
- caps candidates per source,
- ranks candidates with title/tag/chapter/status confidence,
- preselects high-confidence candidates,
- lets user deselect wrong matches,
- saves confirmed actions.

This supports rating sync and favorite sync without pretending the app knows everything automatically.

### Approach B: Saved Cross-Source Link Groups

After the user confirms that several entries are the same manga, persist that relationship.

Future actions can then:

- apply ratings/favorites to already-linked versions,
- preselect linked versions first,
- avoid repeating the search every time,
- backup/restore/sync the confirmed links.

This is the most valuable long-term addition.

### Approach C: Reuse Migration Scoring

Move or reuse the strongest migration scoring concepts:

- title similarity,
- deep-search query variants,
- tag overlap,
- chapter coverage,
- status match.

Do not make chapter fetching mandatory for every candidate. Use it only when the user requests stronger confidence or when the result is about to be committed.

### Approach D: Source Profiles, Not Source-Specific Rules Everywhere

Maintain lightweight source profiles:

- search quality,
- tag quality,
- frequency of wrong matches,
- user-confirmed success rate,
- typical content type,
- source language.

These profiles should be learned locally where possible and manually curated only for a small set of important sources.

### Approach E: Website Recommendations As Optional Boosts

If a source website exposes related/recommended manga and the extension already parses it, those suggestions could boost candidate confidence.

But this should be optional and per-source. It should not be required for the generic system.

## Staged Rollout Recommendation

### Stage 1: Make Existing Matching More Intelligent

Scope:

- Keep installed sources only.
- Reuse migration scorer concepts.
- Show confidence/reasons.
- Keep user confirmation.
- Do not persist identity groups yet unless the user explicitly confirms action.

Risk: low to medium.

### Stage 2: Add Favorite Mode

Scope:

- Add "Favorite other versions" using the same matching workflow.
- Extract add-to-library/favorite logic into a safe domain helper.
- Keep per-source cap small.
- Require confirmation.

Risk: medium.

### Stage 3: Add Cross-Source Link Groups

Scope:

- Persist confirmed identity groups.
- Let rating/favorite workflows use saved links.
- Add backup/restore/sync.
- Add an explicit "Link other versions" action.

Risk: medium to high, mostly due to schema, backup, and sync requirements.

### Stage 4: Source Fit / Extension Discovery

Scope:

- Installed-source quality learning first.
- Non-installed extension suggestions based only on repo metadata and source profiles.
- Clearly label non-installed suggestions as "likely fits," not confirmed matches.

Risk: medium.

### Stage 5: Curated Website-Level Profiles

Scope:

- Start with a small set of popular English sources.
- Document search quality, tag quality, and matching reliability.
- Expand gradually.

Risk: high if treated as comprehensive; manageable if treated as curated optional knowledge.

## Should This Be Implemented?

Recommendation: move forward, but modify the feature goal.

Do not attempt "automatic same manga across all extensions."

Instead, implement:

```text
User-confirmed cross-source identity matching
```

This fits the actual evidence:

- current Komikku can search installed sources,
- migration already has useful matching/scoring logic,
- KMK-Recs already has a working bounded rating workflow,
- users can correctly identify matches when the app cannot,
- saved links can reduce future repeated searches.

The feature should be designed around confidence and correction:

- "Possible matches"
- "Selected by default"
- "Deselect wrong versions"
- "Remember these links"
- "Apply rating/favorite"

That is realistic, useful, and maintainable.

## Recommendation For Next Planning Step

If the user approves moving forward, the next implementation plan should not target non-installed extension discovery yet.

The next practical plan should be:

```text
KMK-Recs Cross-Source Identity Groups And Favorite Matching Plan
```

Minimum scope:

1. Reuse the existing `CrossExtensionMatchScreen`.
2. Add optional match confidence/reasons using migration scorer concepts.
3. Add favorite mode only if add-to-library logic can be extracted safely.
4. Add a user-confirmed link-group table only if backup/restore/sync scope is accepted.
5. Keep normal global search unchanged.
6. Keep all source searches bounded.
7. Keep all automatic identity decisions reversible or user-confirmed.

## Sources

- Mihon source migration guide: https://mihon.app/docs/guides/source-migration
- Mihon extensions FAQ: https://mihon.app/docs/faq/browse/extensions
- Mihon endorsed forks: https://mihon.app/forks/
- Keiyoushi getting started: https://keiyoushi.github.io/docs/guides/getting-started
- Keiyoushi repo index: https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json
- Mihon `CatalogueSource` API: https://raw.githubusercontent.com/mihonapp/mihon/main/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/CatalogueSource.kt
- Mihon `Source` API: https://raw.githubusercontent.com/mihonapp/mihon/main/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/Source.kt
- Mihon `SManga` model: https://raw.githubusercontent.com/mihonapp/mihon/main/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/model/SManga.kt
- Neko README: https://github.com/nekomangaorg/Neko

