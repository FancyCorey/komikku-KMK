# Recommendation and Cross-Extension Search Research

Date: 2026-06-07

Status: research, implementation notes, and forward plan. Earlier sections document the current cross-extension recommendation implementation; the final section documents the proposed personal recommendations / taste profile feature.

## Goal

Research whether Komikku can support two related, but separate, assistant systems:

1. A recommendation system that can rank manga by shared tags/genres, tracker/community scores, and source metadata.
2. A cross-extension search and "best version" finder that can detect likely same-series matches across installed sources, then rank candidate source versions by title similarity, tags, chapters, and completeness.

The user specifically uses extensions from Keiyoushi, so this document also records how Keiyoushi extensions enter Komikku and what data is available from that path.

## High-Level Conclusion

This is feasible in Komikku, but only if it is treated as an enrichment/ranking layer on top of existing source APIs, not as a universal index that already exists.

Komikku can already:

- Load Keiyoushi and other extension repositories.
- Install extension APKs.
- Load installed extensions into concrete `Source` / `CatalogueSource` objects.
- Search all visible catalogue sources by text query.
- Browse a single source using popular/latest/search listings and source-specific filters.
- Fetch full manga details from a source.
- Fetch chapter lists from a source.
- Sync chapter lists into the local DB and parse chapter numbers.
- Store manga genres/tags after metadata is fetched.
- Store tracker records, tracker scores, total tracker chapters, and tracker URLs.
- Generate recommendations from tracker/community/source recommendation providers.
- Run smart migration search and optionally prioritize candidates by chapters.

Komikku does not currently have:

- A universal cross-extension tag index.
- A universal tag-filter API that works the same for all sources.
- Cross-source result grouping in global search.
- "Best source/version" ranking for same-series candidates.
- Tag-overlap recommendation ranking.
- Tracker average-score ranking as a recommendation score.
- Automatic detail/chapter enrichment for every global search result.

The core implementation challenge is cost and consistency. Title search is cheap and supported everywhere. Tags and chapter counts usually require source-specific filters or extra network calls to fetch manga details and chapter lists.

## External Sources Checked

- Keiyoushi GitHub organization: https://github.com/keiyoushi
- Keiyoushi extension APK repository: https://github.com/keiyoushi/extensions
- Keiyoushi extension source repository: https://github.com/keiyoushi/extensions-source
- Keiyoushi repo metadata: https://raw.githubusercontent.com/keiyoushi/extensions/repo/repo.json
- Keiyoushi extension index URL: https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json
- Komikku getting started / global search docs: https://komikku-app.github.io/docs/guides/getting-started

Useful external findings:

- Keiyoushi is an extension repository for Mihon and variants.
- `keiyoushi/extensions` is the distribution repo with `apk/`, `icon/`, `index.json`, `index.min.json`, `index.pb`, and `repo.json`.
- `keiyoushi/extensions-source` is the source-code repo for those extensions.
- The Keiyoushi README points users to the repository URL `https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json`.
- Keiyoushi `repo.json` exposes repo-level metadata: name, website, and signing-key fingerprint.
- Komikku docs describe the current user flow as bring-your-own sources/extensions, then using source browse or global search.

## Local Codebase Context

Repository path:

- `C:\Users\USER\Downloads\Komikku\komikku-source`

Source files inspected:

- `domain/src/main/java/mihon/domain/extensionrepo/interactor/CreateExtensionRepo.kt`
- `domain/src/main/java/mihon/domain/extensionrepo/service/ExtensionRepoService.kt`
- `domain/src/main/java/mihon/domain/extensionrepo/service/ExtensionRepoDto.kt`
- `app/src/main/java/eu/kanade/tachiyomi/extension/api/ExtensionApi.kt`
- `app/src/main/java/eu/kanade/tachiyomi/extension/ExtensionManager.kt`
- `app/src/main/java/eu/kanade/tachiyomi/extension/model/Extension.kt`
- `domain/src/main/java/tachiyomi/domain/source/service/SourceManager.kt`
- `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/Source.kt`
- `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/CatalogueSource.kt`
- `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/model/Filter.kt`
- `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/model/SManga.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/globalsearch/SearchScreenModel.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/browse/BrowseSourceScreenModel.kt`
- `data/src/main/java/tachiyomi/data/source/SourcePagingSource.kt`
- `data/src/main/java/tachiyomi/data/source/SourceRepositoryImpl.kt`
- `app/src/main/java/exh/recs/RecommendsScreenModel.kt`
- `app/src/main/java/exh/recs/sources/RecommendationPagingSource.kt`
- `app/src/main/java/exh/recs/sources/AniListPagingSource.kt`
- `app/src/main/java/exh/recs/sources/MangaUpdatesPagingSource.kt`
- `app/src/main/java/exh/recs/batch/RecommendationSearchHelper.kt`
- `app/src/main/java/mihon/feature/migration/list/search/BaseSmartSearchEngine.kt`
- `app/src/main/java/mihon/feature/migration/list/search/SmartSourceSearchEngine.kt`
- `app/src/main/java/mihon/feature/migration/list/MigrationListScreenModel.kt`
- `app/src/main/java/eu/kanade/domain/chapter/interactor/SyncChaptersWithSource.kt`
- `domain/src/main/java/tachiyomi/domain/chapter/service/MissingChapters.kt`
- `data/src/main/sqldelight/tachiyomi/data/mangas.sq`
- `data/src/main/sqldelight/tachiyomi/data/manga_sync.sq`

## How Keiyoushi Extensions Enter Komikku

Keiyoushi is not a runtime search engine. It is a repository and source-code ecosystem for extension APKs.

Komikku's repo flow:

1. User adds an extension repository URL ending in `/index.min.json`.
2. `CreateExtensionRepo.await()` validates that URL shape.
3. Komikku strips `/index.min.json` to get the repo base URL.
4. `ExtensionRepoService.fetchRepoDetails()` requests `$repo/repo.json`.
5. Repo metadata is saved in the `extension_repos` database table.
6. `ExtensionApi.findExtensions()` loads all enabled extension repos.
7. For each repo, `ExtensionApi.getExtensions()` fetches `$repoBaseUrl/index.min.json`.
8. Each entry becomes an `Extension.Available`.
9. Installing an available extension downloads `${extension.repoUrl}/apk/${extension.apkName}`.
10. Once installed and trusted, `ExtensionLoader` loads the extension APK and produces `Extension.Installed` with concrete `Source` objects.

Important distinction:

- Available extension metadata includes source id/name/lang/base URL, APK name, icon URL, version, NSFW flag, repo name, and signature.
- Installed extensions expose real `Source` objects. Only those can search, fetch details, fetch chapters, or expose filters.

Implication for the requested system:

- Cross-extension intelligent search can work across installed sources.
- It cannot deeply search uninstalled extensions unless Komikku first installs them or some separate external index exists.
- Keiyoushi itself does not provide a universal manga/tag/chapter index in the repo files Komikku consumes.

## How Komikku Renders Source and Extension Pages

Komikku uses standard source APIs:

- `CatalogueSource.getPopularManga(page)`
- `CatalogueSource.getLatestUpdates(page)`
- `CatalogueSource.getSearchManga(page, query, filters)`
- `CatalogueSource.getFilterList()`
- `Source.getMangaDetails(manga)`
- `Source.getChapterList(manga)`
- `Source.getPageList(chapter)`

Single-source browse:

- `BrowseSourceScreenModel` owns the source browse state.
- It waits briefly if the source is not loaded yet.
- It initializes filters from `source.getFilterList()`.
- It creates a pager from `createSourcePagingSource(listing.query ?: "", listing.filters)`.
- `SourceRepositoryImpl.search()` returns `SourceSearchPagingSource` for normal sources.
- `SourceSearchPagingSource.requestNextPage()` calls `source.getSearchManga(currentPage, query.sanitize(), filters)`.
- `BaseSourcePagingSource` maps returned `SManga` objects into local `Manga` rows through `networkToLocalManga`.
- Paging uses `mangasPage.hasNextPage` to decide whether there is another page.

What "number of pages" means:

- Search/listing pagination is controlled by `MangasPage.hasNextPage`, returned by each extension.
- Manga chapter counts are not the same thing. They require `source.getChapterList(manga)` or already-synced local chapters.

## How Current Global Search Works

Current global search is in `SearchScreenModel`.

Flow:

1. Determine enabled sources from `sourceManager.getVisibleCatalogueSources()`.
2. Filter by enabled languages and disabled sources.
3. Optionally restrict to pinned sources.
4. Optionally restrict to an extension package by matching installed extension sources.
5. For each selected source, launch a parallel search.
6. Call `source.getSearchManga(1, query.sanitize(), source.getFilterList())`.
7. Convert result `SManga` objects to domain `Manga`.
8. Deduplicate by URL within that source result.
9. Show results grouped by source.

Current limitations:

- It uses page 1 only.
- It uses each source's default filter list.
- It sends the same text query to every source.
- It does not fetch full details for every result.
- It does not fetch chapter lists for every result.
- It does not group probable same-series results across sources.
- It does not rank results by tags, tracker score, or chapter completeness.

Implication:

- Global search is fast because it avoids expensive enrichment.
- Smart cross-extension search should keep this fast base search and add optional enrichment after candidates are found.

## How Tag Search Works Today

There is no universal tag-search API across all sources.

Available mechanisms:

1. Source filters:
   - Extensions can expose filters through `getFilterList()`.
   - Filter types include `Header`, `Separator`, `Select`, `Text`, `CheckBox`, `TriState`, `Group`, `Sort`, and SY `AutoComplete`.
   - `BrowseSourceScreenModel.searchGenre(genreName)` scans a single source's filters for a matching filter name or select value. If found, it enables that filter. If not found, it falls back to a text query.

2. Manga details:
   - `SManga.genre` is a comma-separated string.
   - `SManga.getGenres()` splits it into a distinct list.
   - Local DB stores `mangas.genre` as `List<String>`.
   - But genres/tags are often only available after `getMangaDetails()`, not necessarily in search result listings.

3. Enhanced metadata:
   - Some sources return `MetadataMangasPage` or store richer EH/SY metadata in `search_metadata`, `search_tags`, and `search_titles`.
   - This is not general across all normal extensions.

Feasibility:

- Single-source tag search is already partially possible through source filters.
- Multi-source tag search is possible only as a best-effort adapter:
  - For each source, inspect `getFilterList()`.
  - Try to map desired tags to source filter names/values.
  - If not possible, fall back to text search or skip tag filtering for that source.
- Universal tag recommendations are more reliable if based on local/fetched manga details rather than live source filter matching.

Recommendation:

- Do not assume "tag X" can be queried across all extensions.
- Build tag comparison against cached/fetched metadata first.
- Add source-filter tag search later as an optional per-source capability.

## How Chapter Counts and Completeness Work

Chapter lists:

- A source exposes chapters through `Source.getChapterList(manga)`.
- `SyncChaptersWithSource.await()` converts source chapters into DB chapters.
- It deduplicates by chapter URL.
- It sanitizes chapter names.
- It parses chapter numbers with `ChapterRecognition.parseChapterNumber`.
- It updates existing chapters, adds new chapters, and removes missing DB chapters.

Local chapter counts:

- Once chapters are synced, local counts can be read from the DB.
- `mangas.sq` already has a `getDuplicateLibraryManga` query that joins chapter counts.
- Migration code uses local chapter counts and latest chapter numbers.

Missing chapter logic:

- `missingChaptersCount()` counts missing integer chapter numbers from a list.
- `calculateChapterGap()` compares two recognized chapter numbers.
- Reader UI already uses chapter gaps to warn about missing chapters.

Migration/best-version precedent:

- `MigrationListScreenModel` can search candidate sources.
- It can enable `prioritizeByChapters`.
- It can fetch `source.getChapterList(candidate.toSManga())`.
- It syncs chapters, then ranks by latest chapter.

Implication:

- The "best version" feature should reuse this pattern.
- It should not fetch chapter lists for every global search result eagerly.
- It should enrich a narrowed candidate set, then rank.

## How Current Recommendations Work

The existing recommendation system is in `exh/recs`.

Recommendation sources include:

- AniList recommendations.
- MyAnimeList recommendations.
- MangaUpdates community recommendations.
- MangaUpdates similar/category recommendations.
- MangaDex similar/related, only for MangaDex-based sources.
- Comick recommendations, only for Comick-based sources.

`RecommendsScreenModel`:

- Builds recommendation source objects for the current manga.
- Fetches page 1 from each recommendation source.
- If a recommendation is associated with a real source id, converts results to local manga for that source.
- Otherwise, stores results under a synthetic recommendation source; clicking later can prompt source selection/search.

`RecommendationSearchHelper`:

- Batch-processes multiple library manga.
- Can include source recommendations and tracker recommendations depending on flags.
- Can hide already-library results.
- Ranks by repeated occurrence within each recommendation provider via URL occurrence count.
- Uses throttling and wake/wifi locks, which is good precedent for long-running enrichment jobs.

Current recommendation limitations:

- No shared-tag similarity scoring.
- No tracker average-score ranking.
- No cross-source duplicate grouping by title/tags/chapters.
- No "best source/version" scoring.
- No unified ranking formula across recommendation providers.

## How Tracker Scoring Works

Tracker records are stored in `manga_sync`.

Fields include:

- `sync_id`
- `remote_id`
- `title`
- `last_chapter_read`
- `total_chapters`
- `status`
- `score`
- `remote_url`

Trackers expose `TrackSearch` objects:

- `TrackSearch.score`
- `TrackSearch.total_chapters`
- `TrackSearch.tracking_url`
- `TrackSearch.title`
- cover and summary fields where available.

Observed tracker score behavior:

- AniList stores score in a raw numeric representation and display conversion is handled by score type.
- MAL search parses mean score into `TrackSearch.score`.
- Kitsu search maps average rating into score.
- MangaUpdates rating exists in its DTO conversion.
- Stored local track score may represent the user's score, not always a community average.

Important distinction:

- "Tracker score in the local DB" is often the user's personal score for a tracked manga.
- "Average/community score" usually requires tracker search/details API data, and differs by tracker.

Implication:

- Recommendation ranking by "best manga according to AniList/MAL/etc." should explicitly fetch/search tracker metadata and normalize external average scores.
- It should not blindly treat local `manga_sync.score` as the global average.

### AniList API Notes

AniList should be treated as optional enrichment, not as a hard dependency for search or recommendations.

Current official AniList API rate-limit guidance says:

- Normal GraphQL API rate limit is 90 requests per minute.
- The API is currently documented as degraded and temporarily limited to 30 requests per minute.
- Responses include `X-RateLimit-Limit` and `X-RateLimit-Remaining`.
- Rate-limit responses include `Retry-After` and `X-RateLimit-Reset`.

Implementation implications:

- Cache AniList lookups by AniList media id and normalized title.
- Batch manga fields into one GraphQL query where possible.
- Respect `Retry-After` before retrying.
- Let ranking continue without AniList when the API is unavailable or rate-limited.
- Prefer using an existing attached AniList track id before falling back to title search.

## Search vs Recommendation: Required Separation

Search assistant:

- Starts from a user query or known manga title.
- Searches installed sources.
- Finds candidate same-series matches.
- Enriches candidates with details and chapters.
- Scores source versions for likely identity and quality.
- Output: "These entries are probably the same manga; this source/version looks best."

Recommendation assistant:

- Starts from a known manga or library/tag profile.
- Finds manga that are similar or highly rated.
- Uses tags, tracker recommendations, source recommendations, and community scores.
- May later resolve recommendations to installed sources.
- Output: "These are good manga to read next, ranked by why they match."

These should share lower-level services:

- Title normalization.
- Tag normalization.
- Candidate enrichment.
- Tracker metadata lookup.
- Chapter completeness scoring.
- Caching and throttling.

But they should not be the same UI flow or the same score formula.

## Feasibility Matrix

| Feature | Feasibility | Notes |
| --- | --- | --- |
| Search all installed extensions by title | Already exists | Global search calls page 1 of each selected source. |
| Search all installed extensions by tag | Partial | Requires per-source filter mapping or fallback text search. No universal tag API. |
| Compare shared tags for local library manga | High | Tags stored in `mangas.genre` once metadata exists. |
| Compare shared tags for remote search results | Medium | Requires `getMangaDetails()` enrichment; may be slow. |
| Find same manga under alternate titles | Medium-high | Existing smart search and title normalization can be reused, but aliases are source/tracker dependent. |
| Compare chapter counts across sources | High after enrichment | Requires `getChapterList()` for each candidate. |
| Detect missing chapters | High after enrichment | Existing chapter parsing and missing-count helpers exist. |
| Rank best source/version | High after candidate narrowing | Use title similarity + tags + chapters + status + source prefs. |
| Rank recommendations by AniList/MAL average score | Medium | Requires tracker API calls and score normalization. |
| Use Keiyoushi repo as universal manga index | Low | Repo index is extension/APK metadata, not manga catalog metadata. |
| Support uninstalled sources in smart search | Low without installing | Available metadata has source info, not searchable source code execution. |

## Proposed Architecture

### Shared Core Concepts

Create a small feature area, likely under Komikku-specific code, with service classes such as:

- `SmartMangaCandidate`
- `SmartMangaEvidence`
- `SmartMangaEnrichment`
- `SmartSearchRanker`
- `RecommendationRanker`
- `TagNormalizer`
- `TitleNormalizer`
- `ChapterCompletenessScorer`
- `TrackerScoreProvider`

Keep the UI thin. Most intelligence should be testable Kotlin logic.

### Candidate Model

Suggested candidate fields:

- `mangaId`
- `sourceId`
- `sourceName`
- `extensionPackage`
- `title`
- `originalTitle`
- `url`
- `thumbnailUrl`
- `status`
- `tags`
- `authors`
- `description`
- `chapterCount`
- `latestChapter`
- `missingChapterCount`
- `trackerMatches`
- `scores`
- `evidence`

Suggested evidence fields:

- `titleSimilarity`
- `alternateTitleMatched`
- `sharedTags`
- `sharedTagCount`
- `tagScore`
- `chapterCountDelta`
- `latestChapterDelta`
- `statusMatch`
- `sourceSearchRank`
- `trackerAverageScore`
- `trackerPopularity`
- `recommendationProviderHits`

### Best-Version Scoring Draft

For a known manga, candidate score could combine:

- Title similarity: strong weight.
- Alias/synonym match: strong weight when available.
- Shared tags: medium/high weight.
- Chapter coverage:
  - higher latest chapter is good.
  - fewer missing chapters is good.
  - chapter count close to known canonical/tracker count is good.
- Manga status match: mild weight.
- Source quality/preference:
  - installed and enabled source.
  - pinned/migration-preferred source.
  - user-defined preference later.
- Tracker match:
  - same AniList/MAL/MangaUpdates id if available is very strong.

The score should always show "why" so the user can trust it.

### Recommendation Scoring Draft

For recommendations, scoring should combine:

- Shared tag count and weighted tag rarity.
- Tracker/community average score after normalization.
- Recommendation provider occurrence count.
- Source availability in installed extensions.
- Completion/ongoing status preference.
- Exclude already-library results if enabled.

Tag rarity is important. A rare tag match should count more than a generic tag like "Action" or "Romance".

## Suggested Phased Plan

### Phase 0: Research and Design

This document.

Open questions:

- Which sources/extensions are most important to support first?
- Should the first working version be manga-page based or global-search based?
- Should enrichment be opt-in only to avoid slow searches?
- Should source preferences/migration sources define "best version" priority?

### Phase 1: Internal Candidate Enrichment Prototype

No major UI.

Build a service that:

1. Takes a known manga.
2. Searches selected sources with existing smart search/title queries.
3. Fetches details for top candidates only.
4. Fetches chapter lists for top candidates only.
5. Produces a ranked report object with evidence.

This can initially be triggered from a debug/dev path or unit tests.

Why first:

- It proves feasibility without committing to UI.
- It reuses migration code patterns.
- It reveals which extensions are expensive or inconsistent.

### Phase 2: Best-Version Finder

Add a user-facing entry from a manga page or migration flow.

Output:

- Candidate source versions.
- Chapter count.
- Latest chapter.
- Missing chapters.
- Shared tags.
- Confidence label.
- Recommended version.

This should be opt-in per manga because it may call many sources.

### Phase 3: Tag-Based Local Recommendations

Start with local library/fetched metadata only.

Output:

- Recommended manga from known local/cached manga.
- Shared tag count.
- Shared tags.
- Optional tracker/community score if already available.

Why:

- Safe and fast.
- Does not hammer extension sites.
- Gives immediate value if metadata exists.

### Phase 4: Remote Recommendation Enrichment

Merge existing recommendation providers with tag/scoring logic.

Flow:

1. Fetch existing tracker/source recommendations.
2. Resolve to installed sources when possible.
3. Enrich selected candidates with details/tags/chapters.
4. Rank by combined recommendation score.

### Phase 5: Multi-Source Tag Search

Attempt universal-ish tag search across selected sources.

Approach:

- Inspect each source `FilterList`.
- Match tag names to `TriState`, `CheckBox`, `Select`, `Group`, or `AutoComplete`.
- If matchable, search with filters.
- If not matchable, fall back to text query or skip.
- Cache per-source tag-filter capability.

This should come later because it is source-dependent and fragile.

## Risks and Constraints

- Source inconsistency: extensions expose filters/tags differently.
- Metadata availability: many listings do not include full tags.
- Network load: fetching details and chapters for many results can be slow and may trigger rate limits.
- Tracker meaning: local stored tracker scores may be user scores, not community averages.
- Title ambiguity: common titles or translated titles can produce false positives.
- Adult/source blacklist settings must be respected.
- Enabled language, disabled source, pinned source, and incognito/source preferences should be respected.
- Extension ABI should not be broken; avoid changing `source-api` unless absolutely necessary.

## Implementation Principles

- Keep current global search fast.
- Make deep matching/enrichment explicit and cancellable.
- Reuse existing `CatalogueSource`, `NetworkToLocalManga`, `SyncChaptersWithSource`, and smart migration code.
- Add caching so repeated enrichment does not refetch everything.
- Show evidence, not just a score.
- Treat tags as best-effort and source-dependent.
- Separate search assistant scoring from recommendation assistant scoring.
- Start with installed sources only.

## Current Working Theory

The best first functional system is not a broad "universal tag search" immediately.

The best first system is a manga-page based "Find best source/version" assistant:

1. It starts with a known manga.
2. It searches selected installed sources using existing smart search.
3. It enriches likely candidates only.
4. It ranks candidates using chapters and metadata.
5. It documents why each candidate matched.

After that works, tag-based recommendations can be built more safely on the same enrichment/ranking foundation.

## Questions for User Review

1. Should the first prototype focus on a known manga page, not the global search screen?
2. Should the source set be all enabled sources, pinned sources, or migration sources?
3. Which tracker should be the first normalized score provider: AniList, MAL, MangaUpdates, or all available?
4. Should "best version" prefer most chapters, fewest missing chapters, best scan quality/source preference, or tracker identity match?
5. Should tag recommendations include only local/cached manga first, or should they fetch remote details from sources?

## Implementation Log

### First implementation pass

- Added `mihon.feature.migration.list.search.SourceMatchScorer`.
- Wired the scorer into `MigrationListScreenModel` when migration search is configured to prioritize by chapters.
- The "best version" search now ranks candidate source matches with:
  - normalized title similarity;
  - normalized tag overlap from manga genres/tags;
  - chapter count and latest chapter coverage;
  - status match.
- Candidate details are fetched before scoring when possible, so source-provided tags/status can contribute to ranking.
- Existing migration behavior, source preferences, cancellation, concurrency, and manual fallback remain in place.
- Added focused unit tests in `SourceMatchScorerTest`.

### Second implementation pass — recommendation ranking

Date: 2026-06-07

#### Goal

Rank the results inside each recommendation provider's result list by similarity to the manga the user currently has open. The existing flow (AniList, MangaUpdates, MAL, MangaDex, Comick) fetches recommendations and displays them in arbitrary provider order. Results that share more genre/tag overlap with the source manga should appear first so the most relevant suggestions are visible without scrolling.

#### Why a separate scorer and not SourceMatchScorer

`SourceMatchScorer` (in `mihon.feature.migration.list.search`) answers the migration question: "Is this entry the same manga, and how complete is this source's version?" Its weights reflect that: chapter count and chapter coverage together account for 50% of the score, status match is included, and the formula is tuned to identify the best copy of a known work.

Recommendation ranking asks a different question: "Is this a good manga to read next because of what it has in common with what I just read?" Chapter count is irrelevant — a short completed manga can be an excellent recommendation. Status is irrelevant — ongoing work can still be highly similar. The meaningful signals are shared theme (tags/genres) and surface title similarity, which helps when providers return results for the right work but with title variants.

Putting recommendation scoring inside `SourceMatchScorer` would require adding flags or mode parameters to a class that already has a clear, correct purpose. A separate object keeps both scorers independently testable and avoids coupling two very different ranking goals.

#### What was added and where

**New file: `app/src/main/java/exh/recs/RecommendationScorer.kt`**

Package `exh.recs`, `internal object RecommendationScorer`.

The scorer exposes one public function:

```kotlin
fun score(source: Manga, candidate: Manga): Double
```

It returns a value in `[0.0, 1.0]` where 1.0 means maximum similarity.

Weight breakdown:

| Signal | Weight | Reason |
|--------|--------|--------|
| Title similarity (NormalizedLevenshtein) | 60% | Tracker recommendation APIs (AniList, MAL) return results for the correct source manga; title similarity confirms the provider matched correctly and provides a baseline for partial title variants. |
| Tag/genre Jaccard overlap | 40% | The primary signal for "feels similar to read." Jaccard (shared / union) is used so that a match on 3 out of 3 common tags scores higher than 3 out of 10. |
| Chapter count | 0% | Not a signal for recommendation. A one-volume manga and a long-running series can both be excellent recommendations. |
| Status | 0% | Whether a manga is ongoing or completed does not make it more or less similar. |

Fallback when genres are missing: many recommendation results (especially from AniList) carry only `title`, `url`, and `thumbnail_url` — no genre data. If either `source.genre` or `candidate.genre` is null or empty, the scorer falls back to title-only (returns `titleSimilarity` directly, not a blended score). This ensures results without metadata are still ranked by title and not artificially penalized with a zero tag score.

Normalization applied to both titles and tags before comparison:
- Lowercase (Locale.ROOT).
- Strip content inside brackets/parentheses — removes volume/season qualifiers like `(Vol. 2)` or `[Sequel]`.
- Replace all non-alphanumeric, non-letter characters with a space.
- Collapse consecutive spaces and trim.

This means `"Attack on Titan"` and `"attack on titan"` are identical after normalization, and `"Sci-Fi"` and `"sci fi"` match in the tag set.

**Modified file: `app/src/main/java/exh/recs/RecommendsScreenModel.kt`**

`RecommendsScreenModel.init` already fetched the source `Manga` object for the `SingleSourceManga` case to populate the screen title. That object is now also retained as `sourceManga` (a nullable local val, null for `MergedSourceMangas`).

After each recommendation provider's result list is resolved to `List<Manga>` (via `networkToLocalManga` or `toDomainManga`), a `.let` block sorts the list:

```kotlin
.let { list ->
    val ref = sourceManga
    if (ref != null) {
        list.sortedByDescending { RecommendationScorer.score(ref, it) }
    } else {
        list
    }
}
```

Why here and not elsewhere:
- This is the earliest point where results are `Manga` objects (not raw `SManga`) and already deduplicated by URL. Sorting before `updateItem` means the `RecommendationItemResult.Success` stored in state already has the ranked order.
- The `RecommendsContent` composable renders each provider's result list directly via `GlobalSearchCardRow`. No UI changes are required; the ranked list flows through the existing display path unchanged.
- `MergedSourceMangas` (used when a manga is read across multiple sources simultaneously) has no single reference manga to score against, so sorting is skipped and provider order is preserved.

`SourceMatchScorer` in `mihon.feature.migration.list.search` is untouched and continues to be used by `MigrationListScreenModel`.

#### Tests

**New file: `app/src/test/java/exh/recs/RecommendationScorerTest.kt`**

8 tests covering the scoring contract:

| Test | What it verifies |
|------|-----------------|
| Exact title and identical tags score highest | Score of 1.0 when everything matches. |
| Tag overlap raises score above title-only match | A candidate with shared tags outranks one with no tags when title is the same. |
| Title-only fallback when candidate has no tags | Score equals title similarity (1.0 for identical title) when candidate genre is null/empty. |
| Title-only fallback when source has no tags | Same fallback applies symmetrically when the source manga has no genres. |
| Completely different title and no shared tags scores near zero | Unrelated manga scores below 0.2. |
| Case and punctuation differences are normalized | `"attack on titan"` and `"Attack on Titan"` score 1.0. |
| Partial tag overlap scores between zero and full match | Full overlap > half overlap > no overlap, in the correct order. |
| Both title and tags empty scores as maximum similarity | Two metadata-empty entries do not score zero against each other. |

Tests use `Manga.create().copy(ogTitle = ..., ogGenre = ...)` matching the style in `SourceMatchScorerTest`.

#### Verification (2026-06-07)

All steps passed after this pass:

```
spotlessApply
spotlessCheck
app:testDebugUnitTest --tests exh.recs.RecommendationScorerTest   (8/8 PASSED)
app:testDebugUnitTest --tests mihon.feature.migration.list.search.SourceMatchScorerTest   (2/2 still passing)
assembleDebug
```

### Third implementation pass — cross-extension genre search (PLAN REVISED AFTER ONLINE RESEARCH)

Date: 2026-06-07

Status: plan researched online against the live Komikku GitHub repository and Keiyoushi extension source. Several assumptions in the first draft of this plan were wrong. Corrections are recorded below before any code is written. No code written yet.

---

#### Online sources consulted for this plan

The following were fetched and read directly before writing this plan:

- `komikku-app/komikku` — `RecommendsScreenModel.kt`, `RecommendationPagingSource.kt`, `RecommendsScreen.kt`, `BrowseRecommendsScreen.kt`, `BrowseRecommendsScreenModel.kt`, `RecommendsScreen.kt` (composable), `SearchScreenModel.kt`, `BrowseSourceScreenModel.searchGenre()`, `SearchFlags.kt`, `SourcePreferences.kt`, `RecommendationSearchBottomSheetDialog.kt`, `RecommendationSearchProgressDialog.kt`, `SmartLibrarySearchEngine.kt`
- `keiyoushi/extensions-source` — CONTRIBUTING.md, filter type documentation, genre filter implementation patterns
- `i18n-sy/strings.xml` — all existing `rec_` strings and naming convention
- `i18n-kmk/strings.xml` — all existing KMR strings and naming convention

Everything in this plan is grounded in those actual files. Any assumption that could not be verified online is flagged explicitly.

---

#### Why the second pass produced no visible change

This is documented here as context before the new plan, because it directly explains why the new plan is necessary.

The second pass assumed that recommendation result `Manga` objects would carry genre data that `RecommendationScorer` could compare. After testing on device, it was confirmed this is not the case. Every recommendation provider — AniList, MangaUpdates, MAL, Comick, MangaDex — builds its result `SManga` objects with only three fields populated:

- `title`
- `url`
- `thumbnail_url`

This was confirmed by reading the parsing code directly:

- `AniListPagingSource` builds `SManga(title = ..., thumbnail_url = ..., url = ...)`. No genre.
- `MangaUpdatesPagingSource` builds `SManga(title = ..., url = ..., thumbnail_url = ...)`. No genre.
- `ComickPagingSource` explicitly sets `initialized = false`, signalling that genre/detail data is intentionally absent and expected to be fetched separately.

Because both sides of the tag comparison are empty for every result, `RecommendationScorer` falls back to title-only similarity in every case. Since all results within a provider row have different titles from the source manga (they are recommendations, not the same work), the title similarity scores are all low and roughly equal, producing no meaningful reordering.

The scorer itself is correct. The data it needs is simply not present in the recommendation flow as it currently runs.

---

#### What the user wants

The user wants the recommendations screen to behave as a cross-extension genre search engine. Specifically:

1. Open a manga (e.g. "Overlord" — tagged Isekai, Action, Dark Fantasy, Magic).
2. Press AZ Recommends.
3. Komikku should search across all installed extensions for manga that share the most genres with "Overlord."
4. Results should be ranked by how much genre overlap they have with the source manga.
5. This is in addition to, not replacing, the existing tracker/community recommendation providers (AniList, MAL, etc.).

This is fundamentally different from what the existing recommendation screen does today. The existing screen asks external community websites "what do your users recommend alongside this manga?" The new behaviour asks all installed extensions directly "find me manga that match these genres."

---

#### Corrections to the first draft of this plan

The first draft was written before reading the live codebase online. The following assumptions were wrong and must be corrected before coding begins.

**Wrong assumption 1: Add the flag to `SearchFlags` and `RecommendationSearchBottomSheetDialog`.**

`SearchFlags` and `RecommendationSearchBottomSheetDialog` belong exclusively to the **batch recommendation search** — the feature where Komikku searches recommendations for many library manga at once, triggered from a library screen. That flow is entirely separate from the single-manga `RecommendsScreen` we are modifying.

The single-manga recommendations screen has no settings dialog at all. Adding the cross-extension toggle to `SearchFlags` would put it in the wrong flow and the wrong UI.

Correction: use a standalone boolean preference in `SourcePreferences` (e.g. `recommendationCrossExtensionSearch()`), defaulting to `false` (opt-in). The toggle is exposed directly on the `RecommendsScreen` AppBar or as a chip/button below the title, not in any dialog.

**Wrong assumption 2: `recommendationSearchFlags()` defaults to `Int.MAX_VALUE`, so a new bitmask flag would be on by default.**

Confirmed from reading `SourcePreferences.kt` online: `recommendationSearchFlags()` returns `preferenceStore.getInt("rec_search_flags", Int.MAX_VALUE)`. Every bit set means all existing flags are enabled by default for batch search. If we added a new bit here, it would also default to on — the opposite of what we want. This confirms the standalone boolean preference approach above.

**Wrong assumption 3: Multiple `CrossExtensionGenreSearchSource` instances can be identified by class name for the drill-down browse flow.**

`BrowseRecommendsScreenModel` re-creates all recommendation sources and then finds the right one with:

```kotlin
RecommendationPagingSource.createSources(manga, recommendationSource)
    .first { it::class.qualifiedName == args.recommendationSourceName }
```

If we add one `CrossExtensionGenreSearchSource` per installed source (20+ instances), every one of them has the same qualified class name. `.first { ... }` would always return the first one — wrong source, wrong results.

Correction: cross-extension source rows do **not** use the existing `SingleSourceManga` drill-down path. When a user taps a cross-extension row header, they are navigated directly to `BrowseSourceScreen` for that catalogue source with the source manga's genres pre-applied as filters — the same result as the user tapping that source in Browse and manually applying genre filters. This is more useful than re-showing the same recommendation results and avoids the class-name collision entirely.

When a user taps an individual manga card within a cross-extension row, the existing `onClickItem` handler already navigates to `MangaScreen(manga.id, true)` because the result has a real `sourceId` set via `networkToLocalManga`. No change needed there.

**Wrong assumption 4: The new source needs a new category string in `i18n-kmk`.**

Confirmed by reading `i18n-sy/strings.xml` online: the existing category string `similar_titles` (`"Similar titles"`) and `community_recommendations` (`"Community recommendations"`) are in `SYMR` (`i18n-sy`). Cross-extension results are most accurately described as similar titles found in extensions, which fits under `similar_titles`. We can reuse the existing `SYMR.strings.similar_titles` rather than adding a new string. A new string is only needed if the category needs to be distinguished from the existing similar-titles providers.

Decision: add one new KMR string `rec_extension_search` = `"Extension search"` as the category, to make it clearly distinct from the tracker-based similar title providers. This is one string addition, not a new category concept.

---

#### Core technical challenge: no universal genre filter API

Every extension exposes genres differently. There is no standard field name, no standard filter type, and no guarantee a genre is filterable at all. The existing `BrowseSourceScreenModel.searchGenre()` method already solves this for a single source. It:

1. Calls `source.getFilterList()` to get all filter definitions.
2. Walks the filter tree looking for a `Group` whose children include a `TriState` or `CheckBox` whose name matches the desired genre (case-insensitive).
3. If found, sets that filter to included and searches with it active.
4. Falls back to a plain text query using the genre name if no matching filter is found.

This per-source, best-effort mapping is the correct model for a cross-extension system. It will not work perfectly for every source, but it degrades gracefully to a text search rather than failing silently.

The new system must replicate and extend this logic across all visible catalogue sources in parallel.

---

#### Why network calls are unavoidable

The user asked whether network calls are required. They are, for the following reasons:

- Installed extension sources are runtime code loaded from APKs. They do not expose a local genre index or cache. The only way to ask a source for manga matching a genre is to call `source.getSearchManga()` with the appropriate filter or query.
- Even if a source's filter list can be inspected locally (no network needed for `getFilterList()`), actually retrieving results requires a search call.
- `getMangaDetails()` is additionally needed if the search results do not include genre data, which is common.

Expected call volume per recommendation lookup:

| Step | Calls | Notes |
|------|-------|-------|
| `source.getFilterList()` | 1 per source | Local/fast. No network in most extensions. |
| `source.getSearchManga()` per genre tag | 1 per source (batched into one search with multiple filters if the source supports it) | Network call. Required. |
| `source.getMangaDetails()` per candidate | Up to N per source, capped | Only needed if the search result lacks genre data. |

If the user has 20 installed extensions and the source manga has 5 genre tags:
- 20 filter list inspections (fast, mostly local).
- 20 search calls (one per source, with genre filters or text query fallback).
- Up to 20 × top-K details calls if search results lack genres (K capped at e.g. 5 per source).

That is 20–120 network calls. This is why throttling, concurrency limits, and a visible loading/progress state are non-negotiable.

---

#### Existing infrastructure that can be reused

The following already exists in the codebase and should be reused rather than reimplemented:

| Component | Location | Reuse |
|-----------|----------|-------|
| `BrowseSourceScreenModel.searchGenre()` | `app/.../ui/browse/source/browse/BrowseSourceScreenModel.kt` | The genre-to-filter-mapping logic should be extracted into a standalone utility and reused. |
| `sourceManager.getVisibleCatalogueSources()` | `domain/.../source/service/SourceManager.kt` | Provides the list of all installed, enabled sources to search across. |
| `NetworkToLocalManga` | `domain/.../manga/interactor/NetworkToLocalManga.kt` | Already used in `RecommendsScreenModel` to persist results. Reuse for cross-extension results. |
| `RecommendationScorer` | `app/.../exh/recs/RecommendationScorer.kt` (added in second pass) | The scoring function is correct. It just needs real genre data to work. |
| `ThrottleManager` | `app/.../exh/util/ThrottleManager.kt` | Already used in `RecommendationSearchHelper` for rate-limiting. Reuse directly. |
| `RecommendationSearchHelper` wake/wifi lock pattern | `app/.../exh/recs/batch/RecommendationSearchHelper.kt` | The wake lock + wifi lock + cancellation pattern is exactly what a long-running cross-extension search needs. |
| `RecommendationItemResult` sealed interface | `app/.../exh/recs/RecommendsScreenModel.kt` | Loading/Success/Error states already exist. The new source section fits into this model. |
| `RecommendationPagingSource` | `app/.../exh/recs/sources/RecommendationPagingSource.kt` | The `createSources()` factory pattern should be extended, not replaced. |
| `SearchFlags` | `app/.../exh/recs/batch/SearchFlags.kt` | User preference flags for what to include in recommendation searches. May need a new flag for cross-extension search. |

---

#### Architecture of the new system (corrected)

The new system adds one new recommendation source type to the existing provider list. It does not replace AniList, MAL, MangaUpdates, Comick, or MangaDex. It runs in parallel with them and contributes one result row per installed source to the recommendations screen.

The new source type is `CrossExtensionGenreSearchSource`. It is a `RecommendationPagingSource` subclass. One instance is created per installed `CatalogueSource` when the feature is enabled.

High-level flow when the user opens the recommendations screen with the feature enabled:

```
RecommendsScreenModel.init()
  │
  ├── [existing] AniListPagingSource.requestNextPage()         → results (no genres — unchanged)
  ├── [existing] MangaUpdatesCommunityPagingSource             → results (no genres — unchanged)
  ├── [existing] MangaUpdatesSimilarPagingSource               → results (no genres — unchanged)
  ├── [existing] MyAnimeListPagingSource                       → results (no genres — unchanged)
  ├── [existing] MangaDexSimilarPagingSource (if MD source)    → results (partial — unchanged)
  ├── [existing] ComickPagingSource (if Comick source)         → results (no genres — unchanged)
  │
  └── [NEW — only if recommendationCrossExtensionSearch pref is true]
      for each source in sourceManager.getVisibleCatalogueSources():
        CrossExtensionGenreSearchSource(manga, catalogueSource).requestNextPage()
          │
          ├── 1. Read source manga genres from local DB (already in Manga.genre — no network)
          ├── 2. Call catalogueSource.getFilterList() (local call into extension APK — no network)
          ├── 3. Pass genres + filterList to GenreFilterMapper
          │     → returns modified FilterList with genre filters enabled
          │     → returns fallback text query for any genres with no matching filter
          ├── 4. Call catalogueSource.getSearchManga(page=1, fallbackQuery, mappedFilterList)
          │     (one network call per source)
          ├── 5. For each result SManga where genre is null or empty:
          │     Call catalogueSource.getMangaDetails(smanga)
          │     Cap at MAX_ENRICH_PER_SOURCE = 10 per source
          ├── 6. Call networkToLocalManga() to persist results
          ├── 7. Score each result with RecommendationScorer.score(sourceManga, candidate)
          └── 8. Return sorted by score descending, un-enriched results at the end
```

All sources (existing and new) run inside the existing `async { ... }.awaitAll()` loop in `RecommendsScreenModel.init()`, subject to `Dispatchers.IO.limitedParallelism(5)`. No structural change to `RecommendsScreenModel` is needed.

---

#### How row click is handled for cross-extension sources (corrected)

This is the key correction from online research. The existing drill-down path in `RecommendsScreen` works by storing the paging source's qualified class name in `BrowseRecommendsScreen.Args.SingleSourceManga`, then re-creating all sources in `BrowseRecommendsScreenModel` and finding the matching one with `.first { it::class.qualifiedName == name }`.

This breaks for `CrossExtensionGenreSearchSource` because every instance of it has the same class name, regardless of which `CatalogueSource` it wraps. `.first` would always find the wrong one.

The resolution: cross-extension source rows use a **different click behaviour**, not the existing `BrowseRecommendsScreen` drill-down.

- **Tapping an individual manga card** within a cross-extension row: unchanged. The manga has a real `sourceId` (set via `networkToLocalManga`), so `onClickItem` already navigates to `MangaScreen(manga.id, true)`. No change needed.

- **Tapping the row header** (source name / "see all"): instead of pushing `BrowseRecommendsScreen`, navigate to `BrowseSourceScreen` for that `CatalogueSource` with the source manga's genres pre-applied as filters. This is done by setting the browse listing to `Listing.Search(query = fallbackQuery, filters = mappedFilterList)`. The user lands directly on the extension's browse page, already filtered by genre — which is exactly what they would want if they tapped the row to explore further.

Implementation: `CrossExtensionGenreSearchSource` stores the `CatalogueSource` ID in its `associatedSourceId` property. `RecommendsScreen.onClickSource` is modified with an additional branch: if the paging source is a `CrossExtensionGenreSearchSource`, push `BrowseSourceScreen(sourceId)` with pre-applied genre filters, rather than `BrowseRecommendsScreen`.

---

#### Genre-to-filter mapping: detailed design

This is the most complex part because every extension exposes genres differently. The mapping logic is verified against the Keiyoushi contributing guide and `BrowseSourceScreenModel.searchGenre()` which already solves this for a single genre.

The mapping lives in a new stateless object: `GenreFilterMapper`.

```kotlin
object GenreFilterMapper {
    fun buildSearch(
        filterList: FilterList,
        desiredGenres: List<String>,
    ): SearchParams // returns modified FilterList + optional text query
}
```

Mapping rules, applied per desired genre, in this order:

1. Walk the `FilterList` for any `Filter.Group<*>` whose children include a `Filter.TriState` or `Filter.CheckBox` whose `.name` matches the desired genre (case-insensitive, normalized).
   - Match found: set `TriState.state = Filter.TriState.STATE_INCLUDE` or `CheckBox.state = true`.
   - Mark genre as mapped.

2. Walk for any `Filter.Select<*>` whose `.values` array contains the desired genre string.
   - Match found: set `Select.state` to that index.
   - Mark genre as mapped.

3. Walk for any `Filter.AutoComplete` (SY extension type) and add the genre to its `state` list if it appears in `values`.
   - Mark genre as mapped.

4. Any genre still unmapped: append to a fallback text query string (space-separated).

5. If zero genres were mapped at all (source has no recognisable genre filters): fall back to a text query using the source manga's title instead of genres. This mirrors the exact fallback in `BrowseSourceScreenModel.searchGenre()`.

Normalization for name matching:
- Lowercase both filter name and desired genre (Locale.ROOT).
- Strip non-alphanumeric characters, collapse spaces.
- `"Sci-Fi"` matches `"sci fi"`, `"SciFi"`, `"sci-fi"`.

This normalization reuses the same regex already defined in `RecommendationScorer` so there is no duplicated logic.

Why the order above: `Group > TriState/CheckBox` is by far the most common pattern across Keiyoushi extensions (confirmed in the contributing guide and issue discussions). `Select` is used in some sources for a single-genre filter. `AutoComplete` is rare and extension-specific.

---

#### Result enrichment: when to call getMangaDetails

`source.getSearchManga()` results commonly carry only title, URL, and thumbnail. This is confirmed by inspecting `AniListPagingSource` and `MangaUpdatesPagingSource` locally, and by the Keiyoushi contributing guide which notes that `initialized = false` signals incomplete data.

Enrichment strategy inside `CrossExtensionGenreSearchSource.requestNextPage()`:

1. Receive `MangasPage` from `getSearchManga()`.
2. For each `SManga` where `.genre.isNullOrEmpty()` is true (or `initialized == false`): call `source.getMangaDetails(smanga)` and copy returned fields back.
3. Cap enrichment at `MAX_ENRICH_PER_SOURCE = 10`. Results beyond that cap are scored on title only and sorted after the enriched results.
4. Run enrichment calls with `async/awaitAll` within the source instance. These count against the overall `limitedParallelism(5)` dispatcher since the source already runs inside that context.

Why 10: at a typical network latency of ~500ms per details call, 10 calls takes ~1–5 seconds (partially parallelised). 30 calls would be unacceptable without explicit user consent.

---

#### Scoring after enrichment

`RecommendationScorer.score(sourceManga, candidate)` is called for each result after enrichment. This scorer was added in the second pass. No changes to the scorer are needed.

The scorer already handles missing genres by falling back to title similarity. The enrichment step above attempts to fill genres before scoring, so the fallback is less frequently needed for cross-extension results than for tracker-API results.

Results are stored in `RecommendationItemResult.Success(sortedList)` exactly as existing providers do.

---

#### UI integration (corrected)

New cross-extension rows appear in the existing `RecommendsContent` lazy column automatically. The composable iterates `state.filteredItems` which is the full `PersistentMap<RecommendationPagingSource, RecommendationItemResult>`. New entries in that map produce new rows with no composable changes.

Each cross-extension row is labelled with the source name (from `catalogueSource.name`) and category `KMR.strings.rec_extension_search` ("Extension search").

**Opt-in toggle placement**: a chip or icon button in the existing `AppBar` of `RecommendsScreen`. When the user taps it, the `recommendationCrossExtensionSearch` preference is toggled and `RecommendsScreenModel` re-initialises with or without the cross-extension sources. This matches Komikku's existing pattern of per-screen filter controls.

**Progress**: the existing `progress`/`total` fields in `State` already count all sources. Adding cross-extension sources increases `total` and the spinner/progress bar reflects the actual count. No UI change required.

**Empty result rows**: the existing `filteredItems` computed property already filters out empty Success results when `onlyShowHasResults` is true. Cross-extension sources that return zero results above the score threshold are automatically hidden.

---

#### New files to be created

| File | Purpose |
|------|---------|
| `app/src/main/java/exh/recs/sources/CrossExtensionGenreSearchSource.kt` | New `RecommendationPagingSource` subclass. One instance per installed `CatalogueSource`. Owns the search, enrichment, scoring, and result sorting for one extension. |
| `app/src/main/java/exh/recs/sources/GenreFilterMapper.kt` | Stateless utility. Maps a list of genre strings to a source's `FilterList`. Returns modified filters and optional text fallback. No Android dependencies — fully unit-testable. |
| `app/src/test/java/exh/recs/sources/GenreFilterMapperTest.kt` | Unit tests: TriState group match, CheckBox group match, Select match, AutoComplete match, no-match text fallback, mixed (some mapped some not), normalization (case/punctuation). |

---

#### Files to be modified

| File | Change | Reason |
|------|--------|--------|
| `app/src/main/java/exh/recs/sources/RecommendationPagingSource.kt` | In `createSources()`, add cross-extension sources when `recommendationCrossExtensionSearch` preference is true. Inject `SourceManager` and `SourcePreferences`. | Factory for all recommendation sources. |
| `app/src/main/java/exh/recs/RecommendsScreen.kt` | In `onClickSource`, add branch: if source is `CrossExtensionGenreSearchSource`, push `BrowseSourceScreen(source.associatedSourceId!!)` instead of `BrowseRecommendsScreen`. | Fixes class-name collision in drill-down navigation. |
| `app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt` | Add `fun recommendationCrossExtensionSearch() = preferenceStore.getBoolean("rec_cross_extension_search", false)`. | Standalone opt-in preference, default false. |
| `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` | Add `<string name="rec_extension_search">Extension search</string>`. Use `KMR` class, `i18n-kmk` module, base locale only. | New category label for cross-extension rows. Per AGENTS.md: Komikku-only strings go in `i18n-kmk`, never `i18n` or `i18n-sy`. |

**Files not modified:**
- `SearchFlags.kt` — batch search only, does not touch single-manga recommendations flow.
- `RecommendationSearchBottomSheetDialog.kt` — batch search dialog, unrelated.
- `RecommendsScreenModel.kt` — no structural change needed; new sources enter the existing loop automatically.
- `exh/recs/components/RecommendsScreen.kt` — the composable renders all sources from state without source-type awareness. No change needed.

---

#### Concurrency and rate limiting

`RecommendsScreenModel` uses `Dispatchers.IO.limitedParallelism(5)`. `SearchScreenModel` (global search) uses `Executors.newFixedThreadPool(5)`. Both achieve the same effective cap of 5 concurrent network operations. We reuse the existing dispatcher in `RecommendsScreenModel` — no new thread pool.

If the user has 30 installed sources, sources are queued through the 5-slot dispatcher. Existing providers (AniList, MAL, etc.) consume some slots. Expect cross-extension sources to finish after the tracker-based providers in most cases. This is acceptable.

`ThrottleManager` is not added in this pass. Its incremental back-off is designed for the batch flow where many manga are processed in sequence. For a single-manga recommendations screen, the 5-slot parallelism limit is sufficient. If rate-limit errors are observed on specific sources during testing, a per-source `delay` can be added inside `CrossExtensionGenreSearchSource.requestNextPage()` without affecting other sources.

---

#### What is explicitly out of scope for this pass

- Chapter list fetching (`getChapterList()`). Not a recommendation signal.
- Cross-source deduplication. If "One Piece" appears in both MangaDex and a scan extension row, it shows in both. Deduplication is a future pass.
- Session caching of results. Results are fetched fresh each time the screen opens.
- Uninstalled extensions. `getVisibleCatalogueSources()` returns installed, enabled, non-disabled sources only.
- Tag rarity weighting. Jaccard overlap in `RecommendationScorer` partially handles common tags already.
- Any modification to the batch recommendation search (`RecommendationSearchHelper`, `SearchFlags`, `RecommendationSearchBottomSheetDialog`).

---

#### Risks specific to this pass

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| `getFilterList()` throws for some extensions | Low | Per-source try/catch. Fall back to title text search. Log at error level. Row still appears with text-search results. |
| Genre names do not match any filter name in a source | High — expected | Text query fallback. Results may be lower relevance but the source is not silently skipped. |
| `getMangaDetails()` returns no genres | Medium | Score on title similarity only. Place below scored results. Do not discard. |
| Source returns very large result list | Medium | Enrichment capped at 10. Score those 10; put the rest after with title-only score. |
| Low-relevance text-fallback rows appear cluttering the UI | High for sources without genre filters | After enrichment and scoring, if the top result's score is below 0.15, drop the row from state entirely. Threshold is tunable. |
| Multiple instances of same extension with different language variants appear as separate rows | Medium | Each `CatalogueSource` ID is unique even if the extension package is the same. Expected behaviour. User can disable specific sources in Komikku settings. |
| User has 30+ sources and the screen takes 30+ seconds to fully populate | Medium | Opt-in flag is off by default. Sources resolve independently so fast sources appear first. Consider a future cap on total cross-extension sources (e.g. pinned sources only). |

---

#### Step-by-step implementation order

These steps are in dependency order. Each step is independently verifiable before the next begins.

**Step 1 — `GenreFilterMapper` and tests**

Create `GenreFilterMapper.kt` and `GenreFilterMapperTest.kt`. This is pure Kotlin logic with no Android or network dependencies. Run `app:testDebugUnitTest --tests exh.recs.sources.GenreFilterMapperTest` to verify. No APK build needed.

Tests must cover:
- TriState inside a Group: genre name matches → `STATE_INCLUDE` set.
- CheckBox inside a Group: genre name matches → `state = true`.
- Select: genre is a value in the array → correct index set.
- AutoComplete: genre in values list → added to state.
- No match at all → returned text query equals the genre name.
- Mixed: some genres mapped to filters, others fall back to text.
- Normalization: `"Sci-Fi"` matches filter named `"sci fi"`.
- Multiple genres: two mapped to filters, one not → filter list has two activations, text query has one term.

**Step 2 — `CrossExtensionGenreSearchSource`**

Create `CrossExtensionGenreSearchSource.kt`. Constructor takes `manga: Manga` and `catalogueSource: CatalogueSource`. Implements `requestNextPage()` as described in the architecture section. Override `associatedSourceId` to return `catalogueSource.id`. Override `name` to return `catalogueSource.name`. Override `category` to return `KMR.strings.rec_extension_search`.

**Step 3 — Preference**

Add `recommendationCrossExtensionSearch()` to `SourcePreferences.kt`. One line. Default false.

**Step 4 — String resource**

Add `rec_extension_search` to `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`. One line. Verify it is in `i18n-kmk` not `i18n` or `i18n-sy`.

**Step 5 — Wire into `createSources()`**

Modify `RecommendationPagingSource.createSources()` to inject `SourcePreferences` and `SourceManager`, check the preference, and add one `CrossExtensionGenreSearchSource` per visible source when enabled.

**Step 6 — Fix row click in `RecommendsScreen`**

Modify `onClickSource` in `RecommendsScreen.kt` to branch on `CrossExtensionGenreSearchSource` and push `BrowseSourceScreen` instead of `BrowseRecommendsScreen`.

**Step 7 — Opt-in toggle: AppBar + Settings**

Two entry points for the same preference:

**(a) AppBar icon button on `RecommendsScreen.kt`** — the primary control. An icon button (filter icon from Material3, already available in the imports) calls:
```kotlin
sourcePreferences.recommendationCrossExtensionSearch().set(!currentValue)
navigator.replace(RecommendsScreen(args))
```
`navigator.replace()` is confirmed available and used throughout the codebase (`SmartSearchScreen.kt`, `MigrationConfigScreen.kt`, `DeepLinkScreen.kt`, `GlobalSearchScreen.kt`, `MangaScreen.kt`). The fresh `RecommendsScreen` instance creates a fresh `RecommendsScreenModel` which reads the updated preference at `init` time. The re-init is complete, no stale state.

**(b) `SettingsBrowseScreen.kt` SwitchPreference** — secondary location for discoverability in Settings search. Confirmed location: the existing `// KMK -->` block at lines 63–87 of `SettingsBrowseScreen.kt`, immediately after the `showHomeOnRelatedMangas` SwitchPreference (line 86) and before the `// KMK <--` marker (line 87). The block already groups `relatedMangas`, `expandRelatedMangas`, `relatedMangasInOverflow`, and `showHomeOnRelatedMangas` — all `SourcePreferences` values controlling extension-driven on-manga-page content. `recommendationCrossExtensionSearch` belongs in this group logically.

Both use the same `sourcePreferences.recommendationCrossExtensionSearch()` preference object. No state duplication.

**Step 8 — Verify**

Run in order:
```
spotlessApply
spotlessCheck
app:testDebugUnitTest --tests exh.recs.sources.GenreFilterMapperTest
app:testDebugUnitTest --tests exh.recs.RecommendationScorerTest
app:testDebugUnitTest --tests mihon.feature.migration.list.search.SourceMatchScorerTest
assembleDebug
```

All existing tests must still pass. New tests must all pass. Build must succeed with no warnings beyond the existing `AppUpdateDownloadJob.kt` delicate API warning already present.

**Step 9 — Device test**

Install debug APK. Open a manga that has genres populated in the local DB (open its detail page and let it fetch if needed). Open AZ Recommends. Toggle the cross-extension feature on. Confirm:
- New rows appear per installed source.
- Each row shows a loading spinner then resolves to manga cards.
- Results within each row are ordered by relevance (most shared genres first).
- Tapping a card opens `MangaScreen`.
- Tapping a row header opens `BrowseSourceScreen` filtered by genre.
- Toggle off removes the cross-extension rows and returns to the original providers only.

### Third pass — final pre-coding verification (2026-06-07)

All unknowns from the plan review are now resolved. The following were confirmed by reading live files:

| Item | Status | Evidence |
|------|--------|----------|
| `BrowseSourceScreen(sourceId, listingQuery=null, filtersJson=serialized)` works | ✅ Confirmed | `Listing.valueOf(null)` → `Search(query=null)`; `filtersJson` path calls `search(filters=deserialized)` immediately on init; `query ?: input.query` preserves null — no accidental override |
| `navigator.replace()` available on Voyager `Navigator` | ✅ Confirmed | Used in `SmartSearchScreen`, `MigrationConfigScreen`, `DeepLinkScreen`, `GlobalSearchScreen`, `MangaScreen` |
| Settings location for `recommendationCrossExtensionSearch` toggle | ✅ Confirmed | `SettingsBrowseScreen.kt` lines 63–87, inside existing `// KMK -->` block after `showHomeOnRelatedMangas`; all neighbouring prefs are `SourcePreferences` values controlling extension-driven per-manga content — this preference is logically identical in category |
| `createSources()` can use `Injekt.get<T>()` | ✅ Confirmed | Pattern established throughout `exh/recs/sources/` — `ComickPagingSource` uses `Injekt.get<NetworkHelper>()`, `injectLazy<Json>()` |
| `SearchFlags` / `RecommendationSearchBottomSheetDialog` scope | ✅ Confirmed NOT our concern | Batch library search only; triggered from `LibraryTab`; `LibraryScreenModel.Dialog.RecommendationSearchSheet` branch shows the dialog — no overlap with `RecommendsScreen` |

No outstanding unknowns. All plan assumptions are grounded in local file reads.

### Third implementation pass — implementation (2026-06-07)

Date: 2026-06-07

#### Goal

Make the recommendations screen actually search across installed extensions by genre, rank the results by how much they match the source manga, and give the user a toggle to enable or disable the feature. The second pass showed that tracker/community recommendation providers return no genre data, so `RecommendationScorer` had nothing meaningful to compare. The only way to get real genre data into the result set is to search the extensions directly by genre.

#### Why this approach and not alternatives

**Why not enrich the existing tracker results with `getMangaDetails`?**

The existing providers (AniList, MAL, MangaUpdates, Comick, MangaDex) return results as external URLs that belong to those websites, not to installed extensions. Calling `getMangaDetails` on an AniList URL would need to route back through the AniList extension — but the result is tied to a specific extension ID and the user may not have that extension installed. The problem is structural: tracker results and extension search results are fundamentally different data sources. Adding genre enrichment to tracker results without knowing which extension to call would require guessing, which is fragile and wrong.

**Why a new `RecommendationPagingSource` subclass rather than a separate screen or feature?**

The existing `RecommendsScreenModel.init()` already runs all sources in an `async/awaitAll` loop with `Dispatchers.IO.limitedParallelism(5)`. Adding new sources to the list created by `createSources()` means they run in parallel with the existing providers at no structural cost. The UI (`RecommendsContent`) already renders any number of source rows from state without source-type awareness. New rows appear automatically. No screen model changes, no composable changes, no new navigation.

**Why one source instance per extension rather than one combined source?**

Each extension is searched independently. A combined source would either serialize searches (slower) or need its own internal parallel machinery (reimplementing what the screen model already does). Per-extension instances also mean each extension's row loads independently — a slow extension does not block fast ones. The user sees fast extensions populate first, which is a better experience.

**Why `BrowseSourceScreen` for row-header navigation instead of `BrowseRecommendsScreen`?**

`BrowseRecommendsScreen` identifies the correct paging source by class name via `.first { it::class.qualifiedName == name }`. When there are 20 `CrossExtensionGenreSearchSource` instances, every one has the same class name. `.first` would always return the first instance — the wrong source. `BrowseSourceScreen` takes a `sourceId: Long` directly and also accepts `filtersJson: String?` to pre-apply filters, which is exactly what is needed: open that extension's browse page already filtered by genre.

**Why a standalone boolean preference and not a flag in `SearchFlags`?**

`SearchFlags` and `RecommendationSearchBottomSheetDialog` belong exclusively to the batch recommendation search — the library-level feature triggered from `LibraryTab`. The single-manga `RecommendsScreen` has no connection to that flow. Also, `recommendationSearchFlags()` defaults to `Int.MAX_VALUE` (all bits set), so any new bitmask flag there would default to on — the opposite of the wanted opt-in behaviour. A standalone `Boolean` preference defaulting to `false` is the only correct choice.

**Why `navigator.replace(RecommendsScreen(args))` for the toggle?**

`RecommendsScreenModel` is created once by `rememberScreenModel` and cached for the lifetime of the screen. Its `init` block runs once and reads the preference at that point. There is no way to re-run `init` or add/remove sources from outside the model. The cleanest solution is to replace the screen with a fresh instance: `navigator.replace` swaps the current back-stack entry, the new screen creates a fresh model, and the model reads the updated preference. This pattern is used in `SmartSearchScreen`, `MigrationConfigScreen`, `DeepLinkScreen`, and `MangaScreen` throughout the codebase.

---

#### What was added and where

**New file: `app/src/main/java/exh/recs/sources/GenreFilterMapper.kt`**

Package `exh.recs.sources`, `internal object GenreFilterMapper`.

Maps a list of genre strings to a source's `FilterList`, enabling the closest-matching filter entries and collecting any unmatched genres into a text query fallback. Returns a `SearchParams(filters: FilterList, textQuery: String)` data class.

Mapping priority per genre, in order:
1. `Filter.Group<*>` children of type `Filter.TriState` — name match (case-insensitive, normalized) → `state = STATE_INCLUDE`.
2. `Filter.Group<*>` children of type `Filter.CheckBox` — name match → `state = true`.
3. `Filter.Select<*>` — value match → `state = matchingIndex`.
4. `Filter.AutoComplete` — value match → appended to `state` list.
5. Unmatched → appended to `textQuery`, space-separated.

Normalization: lowercase (Locale.ROOT), non-alphanumeric characters replaced with spaces, consecutive spaces collapsed, trimmed. `"Sci-Fi"` and `"sci fi"` and `"SciFi"` all normalize to `"sci fi"` and match each other. This reuses the same regex pattern already in `RecommendationScorer` (no new logic invented).

The priority order matches how Keiyoushi extensions structure their filters in practice: `Group` + `TriState/CheckBox` is by far the most common pattern, `Select` is used for single-value genre picks in some sources, and `AutoComplete` is the SY extension type used in a small number of sources.

An important Kotlin detail: the filter objects (`TriState`, `CheckBox`, `Select`, `AutoComplete`) are abstract classes in `Filter.kt` — extensions subclass them with concrete implementations. The `is Filter.TriState` / `is Filter.CheckBox` pattern-match works on any subclass. `state` is `var` on `Filter<T>`, so mutations in-place persist in the same `FilterList` instance, which is then returned in `SearchParams.filters` and can be serialized.

**New file: `app/src/main/java/exh/recs/sources/CrossExtensionGenreSearchSource.kt`**

Package `exh.recs.sources`, `internal class CrossExtensionGenreSearchSource`.

A `RecommendationPagingSource` subclass. Constructor takes `manga: Manga` (the source manga) and `catalogueSource: CatalogueSource` (the extension to search). Passes `RecommendationSource(catalogueSource.id)` to the parent constructor so the base class pagination machinery works correctly.

Key property overrides:
- `name` → `catalogueSource.name` (displayed as the row title on the recommendations screen)
- `category` → `KMR.strings.rec_extension_search` ("Extension search") — distinguishes these rows from the community/tracker rows
- `associatedSourceId` → `catalogueSource.id` (non-null, so `RecommendsScreenModel` routes manga card taps directly to `MangaScreen` rather than the SmartSearch picker)

Two cached properties set during `requestNextPage()` and read by `RecommendsScreen.onClickSource` when the user taps the row header:
- `cachedFiltersJson: String?` — the JSON-serialized `FilterList` with genre states set, produced by `FilterSerializer().serialize(mappedFilters).toString()`. Used as the `filtersJson` argument to `BrowseSourceScreen`.
- `cachedTextQuery: String` — the text fallback for genres that could not be mapped to filters. Used as `listingQuery` to `BrowseSourceScreen`.

`requestNextPage()` flow:

```
1. Read manga.genre — if empty, fall back to title text search (no genre data = can't genre-search)
2. catalogueSource.getFilterList() — local call, no network. Wrapped in try/catch; falls back to FilterList() on error.
3. GenreFilterMapper.buildSearch(filterList, desiredGenres) — maps genres to filters, collects unmapped genres as text
4. Serialize the mapped FilterList to JSON and store in cachedFiltersJson
5. catalogueSource.getSearchManga(1, textQuery, mappedFilters) — one network call per source
6. If results are empty → throw NoResultsException (row hidden by filteredItems)
7. For each SManga in top MAX_ENRICH_PER_SOURCE (10) where genre is null/empty:
   → async { catalogueSource.getMangaDetails(smanga) } — fills smanga.genre, .description, .status in-place
   → capped at 10 to limit network calls (at ~500ms each, 10 = 1–5 seconds with parallelism)
8. Return MangasPage(enriched results, false)
```

After `requestNextPage()` returns, `RecommendsScreenModel` handles the rest exactly as it does for any other source: `page.mangas.map { it.toDomainManga(sourceId) }` → `networkToLocalManga()` → `distinctBy { it.url }` → `RecommendationScorer.score(sourceManga, it)` sorting. The genre data set in step 7 flows through `toDomainManga` into the `Manga` domain object, so the scorer has real genre data to compare. No changes to `RecommendsScreenModel` are needed.

`FilterSerializer` is imported from `xyz.nulldev.ts.api.http.serializer.FilterSerializer` — the same class already used in `BrowseSourceScreenModel`, `SourceFeedScreenModel`, `FeedScreenModel`, `GetExhSavedSearch`, and `SYDomainModule`. No new dependency.

`JsonArray.toString()` (from kotlinx.serialization) returns the compact JSON string representation directly — no `Json.encodeToString()` call needed, no `Json` injection needed.

**Modified file: `app/src/main/java/exh/recs/sources/RecommendationPagingSource.kt`**

`createSources()` companion function extended inside the `// KMK -->` block:

```kotlin
val sourcePreferences: SourcePreferences = Injekt.get()
if (sourcePreferences.recommendationCrossExtensionSearch().get()) {
    val sourceManager: SourceManager = Injekt.get()
    sourceManager.getVisibleCatalogueSources()
        .take(MAX_CROSS_EXTENSION_SOURCES)
        .forEach { catalogueSource ->
            add(CrossExtensionGenreSearchSource(manga, catalogueSource))
        }
}
```

`MAX_CROSS_EXTENSION_SOURCES = 20` constant added to the companion object. `SourcePreferences` import added. `SourceManager` was already imported. `Injekt.get()` is the established pattern for this function (used by all other Injekt accesses in the same file).

`getVisibleCatalogueSources()` returns all installed, enabled, non-disabled `CatalogueSource` objects — confirmed by reading `SearchScreenModel.kt` line 96 which uses the same call for global search.

The new sources are appended inside `buildList` before the final `.sortedWith(compareBy({ it.name }, { it.category.resourceId }))`. Cross-extension rows therefore sort alphabetically by source name alongside the existing tracker rows, not appended at the end.

**Modified file: `app/src/main/java/exh/recs/RecommendsScreen.kt`** (the Screen)

Two changes inside `// KMK -->` blocks:

**(1) `onClickSource` branch for `CrossExtensionGenreSearchSource`:**

```kotlin
if (pagingSource is CrossExtensionGenreSearchSource) {
    navigator.push(
        BrowseSourceScreen(
            sourceId = pagingSource.associatedSourceId!!,
            listingQuery = pagingSource.cachedTextQuery.ifBlank { null },
            filtersJson = pagingSource.cachedFiltersJson,
        ),
    )
} else {
    // existing BrowseRecommendsScreen path unchanged
}
```

`cachedTextQuery.ifBlank { null }` ensures a blank fallback query becomes `null`, which `BrowseSourceScreen` treats as "no text query". `cachedFiltersJson` may also be `null` if serialization failed — `BrowseSourceScreen` handles `null` filtersJson by doing a plain popular listing, which degrades gracefully.

**(2) AppBar toggle button:**

```kotlin
actions = {
    IconButton(
        onClick = {
            sourcePreferences.recommendationCrossExtensionSearch().set(!crossExtensionEnabled)
            navigator.replace(RecommendsScreen(args))
        },
    ) {
        Icon(
            imageVector = Icons.Outlined.TravelExplore,
            tint = if (crossExtensionEnabled) MaterialTheme.colorScheme.primary else Color.Unspecified,
        )
    }
},
```

`Icons.Outlined.TravelExplore` (globe with magnifying glass) is confirmed used in `SourcesTab.kt` — no new icon dependency. Tint switches to `MaterialTheme.colorScheme.primary` (blue) when enabled, `Color.Unspecified` (default grey) when disabled — a clear on/off visual distinction without adding any new composable component. `crossExtensionEnabled` is collected from the preference as `State<Boolean>` via `tachiyomi.presentation.core.util.collectAsState`.

New imports added to `RecommendsScreen.kt`:
- `androidx.compose.material.icons.outlined.TravelExplore`
- `androidx.compose.material3.Icon`, `IconButton`, `MaterialTheme`
- `androidx.compose.runtime.remember`
- `androidx.compose.ui.graphics.Color`
- `eu.kanade.domain.source.service.SourcePreferences`
- `eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen`
- `exh.recs.sources.CrossExtensionGenreSearchSource`
- `tachiyomi.i18n.kmk.KMR`
- `tachiyomi.presentation.core.util.collectAsState`
- `uy.kohesive.injekt.Injekt`, `uy.kohesive.injekt.api.get`

Both `androidx.compose.runtime.collectAsState` (for `StateFlow`) and `tachiyomi.presentation.core.util.collectAsState` (for `Preference`) are imported and needed. They apply to different receiver types and do not conflict.

**Modified file: `app/src/main/java/exh/recs/components/RecommendsScreen.kt`** (the composable)

Added `actions: @Composable RowScope.() -> Unit = {}` parameter to the `RecommendsScreen` composable (defaulting to empty so existing call sites without actions continue to compile). The parameter is forwarded directly to `AppBar(actions = actions)`. One import added: `androidx.compose.foundation.layout.RowScope`.

**Modified file: `app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt`**

Inside the `// KMK -->` block, after `relatedMangas()`:

```kotlin
fun recommendationCrossExtensionSearch() = preferenceStore.getBoolean("rec_cross_extension_search", false)
```

Key: `"rec_cross_extension_search"`. Default: `false` (opt-in). This is a `SourcePreferences` function because it controls source-extension-driven behaviour on a manga-specific screen, consistent with `relatedMangas()`, `expandRelatedMangas()`, and `showHomeOnRelatedMangas()` in the same block.

**Modified file: `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`**

Four strings added in the `<!-- Browse settings -->` section, under a new `<!-- Recommendations -->` comment:

```xml
<string name="rec_extension_search">Extension search</string>
<string name="pref_rec_cross_extension_search">Search extensions for recommendations</string>
<string name="pref_rec_cross_extension_search_summary">Search installed extensions by genre to find similar manga (network calls per extension)</string>
<string name="action_toggle_extension_search">Toggle extension search</string>
```

All four use the `KMR` resource class (`i18n-kmk` module). Per `AGENTS.md` hard rules: never added to `i18n/` (MR) or `i18n-sy/` (SYMR). No non-base locale files were touched.

`rec_extension_search` — category label displayed as the subtitle under each cross-extension row's source name on the recommendations screen. Distinguishes these rows from the existing `similar_titles` (SYMR) and `community_recommendations` (SYMR) tracker-based rows.

`pref_rec_cross_extension_search` / `pref_rec_cross_extension_search_summary` — title and subtitle for the `SwitchPreference` in `SettingsBrowseScreen`.

`action_toggle_extension_search` — `contentDescription` for the AppBar `IconButton`.

**Modified file: `app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsBrowseScreen.kt`**

One `SwitchPreference` added inside the existing `// KMK -->` block, after `showHomeOnRelatedMangas`:

```kotlin
Preference.PreferenceItem.SwitchPreference(
    preference = sourcePreferences.recommendationCrossExtensionSearch(),
    title = stringResource(KMR.strings.pref_rec_cross_extension_search),
    subtitle = stringResource(KMR.strings.pref_rec_cross_extension_search_summary),
),
```

Placed in the same group as `relatedMangas`, `expandRelatedMangas`, `relatedMangasInOverflow`, and `showHomeOnRelatedMangas` — all `SourcePreferences` values that control extension-driven per-manga content. This gives the setting a logical home in the settings hierarchy and makes it discoverable via Settings search.

---

#### Tests

**New file: `app/src/test/java/exh/recs/sources/GenreFilterMapperTest.kt`**

8 tests, JUnit 5 (Jupiter), matching the style of `RecommendationScorerTest` and `SourceMatchScorerTest`. Uses anonymous concrete subclasses of the abstract `Filter` types to avoid the need for a full Android environment.

| Test | What it verifies |
|------|-----------------|
| `triState in group is set to INCLUDE when genre matches` | `Filter.TriState.state` becomes `STATE_INCLUDE` on name match. Unmatched filter stays `STATE_IGNORE`. |
| `checkBox in group is set to true when genre matches` | `Filter.CheckBox.state` becomes `true` on name match. Unmatched filter stays `false`. |
| `select is set to matching index when genre matches` | `Filter.Select.state` set to the correct index of the matching value string. |
| `autoComplete value is added to state when genre matches` | Matched value is appended to `Filter.AutoComplete.state` list. |
| `unmatched genre is added to text query` | Genre with no matching filter goes into `SearchParams.textQuery`. |
| `mixed genres — some matched via filter, others fall back to text` | Both paths work simultaneously: some genres activate filters, remaining go to text query. |
| `normalization — sci-fi matches filter named sci fi` | Punctuation differences do not prevent matching. |
| `empty genre list produces empty text query and unmodified filters` | Empty input produces empty output, no filter side-effects. |

All 8 passed on first run.

#### Verification (2026-06-07)

All steps passed:

```
spotlessApply
spotlessCheck
app:testDebugUnitTest --tests exh.recs.sources.GenreFilterMapperTest           (8/8 PASSED)
app:testDebugUnitTest --tests exh.recs.RecommendationScorerTest                (8/8 PASSED — no regression)
app:testDebugUnitTest --tests mihon.feature.migration.list.search.SourceMatchScorerTest   (2/2 PASSED — no regression)
assembleDebug                                                                   (exit code 0)
```

APK output:
- `app/build/outputs/apk/debug/app-universal-debug.apk` — 121 MB (all architectures)
- `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` — 69 MB (64-bit ARM, most modern tablets)

Note: the three test suites must be run in a single Gradle invocation on this machine. Running them as separate `--no-daemon` invocations in parallel corrupts the build cache (`mergeDebugResources` → `EOFException`) because multiple Gradle processes race for the same cache files. Always run: `.\gradlew.bat :app:testDebugUnitTest --tests A --tests B --tests C --no-daemon`.

### Current verification status

- A project-local Android SDK was assembled under `.tools/android-sdk`:
  - `platforms/android-36`
  - Gradle later auto-installed another copy as `platforms/android-36-2` because the manually unpacked package did not include SDK metadata in the expected shape.
  - Gradle also auto-installed `build-tools/35.0.0`.
  - `build-tools/35.0.1`
  - `platform-tools/37.0.0`
- Portable Gradle 9.3.1 was extracted under `C:\Users\USER\Downloads\Komikku\gradle-dist`.
- Portable JDK 17 and JDK 21 were downloaded under `.tools`; JDK 21 is the working build JDK.
- `sdkmanager` could not fetch packages because Java HTTPS validation fails with `PKIX path building failed`.
- Manual official SDK package downloads with `curl.exe --ssl-no-revoke` worked.
- Gradle needed to run outside the sandbox to avoid Windows file-lock `AccessDeniedException` failures.
- Gradle dependency resolution needed `GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT` so Java could use Windows trusted certificates.
- Verification passed (first pass):
  - `spotlessApply`
  - `spotlessCheck`
  - `app:testDebugUnitTest --tests mihon.feature.migration.list.search.SourceMatchScorerTest`
  - `assembleDebug`
- Verification passed (second pass, 2026-06-07):
  - `spotlessApply`
  - `spotlessCheck`
  - `app:testDebugUnitTest --tests exh.recs.RecommendationScorerTest` (8/8 PASSED)
  - `app:testDebugUnitTest --tests mihon.feature.migration.list.search.SourceMatchScorerTest` (2/2 still passing)
  - `assembleDebug`
- Debug APKs were generated under `app/build/outputs/apk/debug/`.
- Device install/smoke test is deferred because the phone was disconnected after the build work started.

### Third pass verification (2026-06-07)

All steps passed:

```
spotlessApply                                                           PASSED
spotlessCheck                                                           PASSED
app:testDebugUnitTest --tests exh.recs.sources.GenreFilterMapperTest   8/8 PASSED
app:testDebugUnitTest --tests exh.recs.RecommendationScorerTest        8/8 PASSED (no regression)
app:testDebugUnitTest --tests ...SourceMatchScorerTest                 2/2 PASSED (no regression)
assembleDebug                                                           exit code 0
```

APK output: `app/build/outputs/apk/debug/app-universal-debug.apk` (121 MB).

Device smoke test pending — install APK, open a manga with genres, press AZ Recommends, tap globe icon in AppBar to enable extension search.

---

## Personal Recommendations / Taste Profile Plan

Date: 2026-06-10

This section documents the next requested feature direction after the cross-extension manga-page recommendation work.

The user does **not** want to depend on keeping manga in the library. Their library is intentionally kept small because a crowded library creates mental load. The new recommendation system should remember taste even when manga are removed from the library or were never added to the library.

The target feature is a private, local recommendation system for Komikku:

- The user can mark manga as Dislike, Like, or Love.
- The user can explicitly prefer, dislike, or block tags/genres.
- Komikku builds a local taste profile from those signals.
- A new Browse-level Recommendations tab searches installed extensions and ranks results against that taste profile.
- Results are capped and cached so the feature is useful without creating unbounded network load.
- Taste data should be included in Komikku backup/restore.

### Important Distinction From Manga Rock

Manga Rock could provide strong "people who read this also read..." recommendations because it controlled one central catalog and one central user-behavior dataset.

Komikku cannot naturally reproduce that:

- It uses independent installed extensions.
- There is no universal manga identity across all sources.
- There is no shared user-behavior graph.
- Sources do not expose a global catalog or recommendation index.
- Keiyoushi is an extension repository, not a manga metadata database.

The feasible replacement is a local recommendation engine:

- explicit user ratings;
- explicit tag preferences;
- tag/genre matching;
- source search;
- optional tracker enrichment;
- result caching.

This will not be collaborative filtering, but it can still be useful because new manga inherit score from their metadata.

### AniList / MAL Position

AniList and MyAnimeList should **not** be core dependencies for this feature.

Reasons:

- AniList recommendations are sparse and inconsistent.
- MAL manga coverage is weak for many Korean and Chinese titles.
- Neither service reliably maps every extension result back to a tracker entry.
- API lookups add latency and rate-limit risk.
- No official complete AniList public database dump with scores was found.
- A bundled score database would become stale and increase storage/APK footprint.

Use tracker data only as optional enrichment:

- query only after high-confidence title matching;
- cache any matched tracker ids locally;
- never block recommendation results on tracker availability;
- never require the user to add manga to a tracker.

### Feasibility Assessment

This is feasible in Komikku's current architecture.

Existing infrastructure that helps:

- `mangas` table already persists non-library manga.
- `mangas.genre` already stores source tags/genres.
- `NetworkToLocalManga` already inserts extension search results into local DB.
- `BrowseTab.kt` centralizes Browse tabs.
- Manga detail screens already have action rows and overflow actions.
- SQLDelight is already used for persistent app data.
- Backup/restore already serializes manga-related domain data.
- Claude's cross-extension work already added:
  - `RecommendationScorer`;
  - `GenreFilterMapper`;
  - `CrossExtensionGenreSearchSource`;
  - source-row drill-down into `BrowseSourceScreen` with filters/query.

Practical scope:

- Database and domain work: medium.
- Manga-page rating UI: small to medium.
- Browse Recommendations tab: medium to large.
- Caching and backup/restore: medium.
- Overall feature: medium-large, but not risky-large if implemented in phases.

Main risk is not storage or UI. The main risks are:

- source network latency;
- inconsistent source filter/tag names;
- low-quality or missing genre data;
- too many extension calls;
- scoring that feels opaque or wrong.

### Product Goals

The feature should answer:

> "Based on what I like, dislike, and block, what manga across my installed extensions should I try next?"

It should not require:

- adding manga to library;
- adding manga to AniList/MAL;
- keeping a large library;
- external accounts;
- a central server;
- universal source support.

It should allow:

- private local taste tracking;
- manual adjustment of tag preferences;
- hard-blocking unwanted tags;
- per-source opt-out;
- opening a source row to see more results;
- backup/restore of taste data.

### User-Confirmed Constraints

These constraints should be treated as design requirements:

1. Browse Recommendations should use installed extension searches. There is no practical way around that without maintaining/downloading a separate manga database.
2. The feature may take time to load. Quality is more important than aggressively reducing searches.
3. Default eligible sources should be capped to the first 20 visible catalogue sources.
4. Each source row should show at most 10 recommendations.
5. Each extension should have bounded search work; do not let one extension run unlimited searches.
6. The user should be able to disable specific extensions from the Recommendations tab.
7. Tapping a source row should open the source browse/search page with the recommendation filters/query applied, so the user can explore beyond the first 10.
8. Taste ratings and tag preferences should be included in Komikku backup/restore.
9. Android uninstall/reinstall normally clears app-local preferences/data unless restored; this is expected behavior. The app should still have a sane default state after fresh install: first 20 visible sources eligible.

### Data Model Proposal

Add new SQLDelight tables.

#### `manga_taste`

Stores direct user rating for a manga.

Suggested schema:

```sql
CREATE TABLE manga_taste (
    manga_id INTEGER NOT NULL PRIMARY KEY,
    source INTEGER NOT NULL,
    url TEXT NOT NULL,
    title TEXT NOT NULL,
    rating INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY(manga_id) REFERENCES mangas(_id)
    ON DELETE CASCADE
);

CREATE UNIQUE INDEX manga_taste_source_url_index ON manga_taste(source, url);
CREATE INDEX manga_taste_rating_index ON manga_taste(rating);
```

Suggested rating values:

- `-1` = Dislike
- `1` = Like
- `2` = Love

Avoid a neutral row. Clearing taste should delete the row.

Why include `manga_id` plus source/url/title snapshots:

- non-library manga already exists in `mangas`;
- `NetworkToLocalManga` can create local manga rows for search results;
- existing genre/title/source/url metadata stays in one place;
- snapshot fields help backup/restore and debugging;
- cascade delete prevents orphan taste rows when manga is purged.

Open issue:

- If a non-library manga row is purged, the taste row will be deleted. If taste must persist forever independent of manga rows, remove cascade or make source/url the primary identity. First pass can use cascade and rely on backup/restore plus normal DB retention.

#### `tag_taste`

Stores explicit user preference for a normalized tag.

Suggested schema:

```sql
CREATE TABLE tag_taste (
    normalized_tag TEXT NOT NULL PRIMARY KEY,
    display_name TEXT NOT NULL,
    preference INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE INDEX tag_taste_preference_index ON tag_taste(preference);
```

Suggested preference values:

- `-2` = Block
- `-1` = Dislike
- `1` = Prefer

Hard block is stronger than dislike. If a candidate has a blocked tag or blocked alias group, it should be hidden before scoring.

#### `tag_alias`

Stores tag alias groups.

Suggested schema:

```sql
CREATE TABLE tag_alias (
    alias TEXT NOT NULL PRIMARY KEY,
    normalized_alias TEXT NOT NULL,
    group_key TEXT NOT NULL,
    display_name TEXT NOT NULL
);

CREATE INDEX tag_alias_group_key_index ON tag_alias(group_key);
```

Example built-in groups:

- group `girls_love`: `Yuri`, `Girls Love`, `GL`
- group `boys_love`: `Yaoi`, `Boys Love`, `BL`

Do not overbuild the alias list. Start small and user-editable later.

#### `recommendation_cache`

Stores cached result rows for the Browse Recommendations tab.

Suggested schema:

```sql
CREATE TABLE recommendation_cache (
    cache_key TEXT NOT NULL PRIMARY KEY,
    source_id INTEGER NOT NULL,
    profile_fingerprint TEXT NOT NULL,
    query_key TEXT NOT NULL,
    result_manga_ids TEXT NOT NULL,
    result_scores TEXT,
    result_reasons TEXT,
    created_at INTEGER NOT NULL,
    expires_at INTEGER NOT NULL
);

CREATE INDEX recommendation_cache_source_index ON recommendation_cache(source_id);
CREATE INDEX recommendation_cache_expires_index ON recommendation_cache(expires_at);
```

Use JSON strings for ordered lists if SQLDelight adapters are inconvenient.

Cache should store the ranked local result list, not raw website responses. Full manga data already lives in `mangas` after `NetworkToLocalManga`.

Cache key should include:

- source id;
- taste profile fingerprint/version;
- query/tag set used;
- filters used if available;
- algorithm version.

#### `recommendation_disabled_source`

Optional SQL-backed disabled source list.

```sql
CREATE TABLE recommendation_disabled_source (
    source_id INTEGER NOT NULL PRIMARY KEY,
    created_at INTEGER NOT NULL
);
```

SQL is preferable if disabled sources should be backed up. A preference set is simpler but less consistent with backup/restore.

### Preferences Proposal

Add preferences for cache behavior and defaults.

Possible preferences:

- `recommendationCacheTtlHours(): Preference<Int>` default `24`
- `recommendationHideAlreadyRated(): Preference<Boolean>` default `true`
- `recommendationMaxSources(): Preference<Int>` default `20`
- `recommendationMaxResultsPerSource(): Preference<Int>` default `10`

For source disabling, prefer the SQL table above if backup/restore matters.

### Scoring Model

Scoring must be explainable and testable.

Hard filter:

1. Normalize candidate tags through alias groups.
2. If any candidate tag maps to a blocked tag group, hide the candidate.
3. If manga itself is disliked and `hideAlreadyRated` is true, hide it.

Soft score:

```text
score =
  explicit_tag_score
  + learned_tag_score
  + source_affinity_score
  + title_similarity_bonus
  + optional_tracker_bonus
```

#### Explicit tag score

User-selected tag preferences are the strongest normal signal.

- Preferred tag group: positive boost.
- Disliked tag group: negative penalty.
- Blocked tag group: hard reject before scoring.

Example:

- prefer `Villainess`: +3
- prefer `Regression`: +3
- dislike `Harem`: -2
- block `Girls Love/Yuri/GL`: reject

#### Learned tag score from manga ratings

Build weights from `manga_taste`:

- Love manga tags contribute strong positive weight.
- Like manga tags contribute smaller positive weight.
- Dislike manga tags contribute negative weight.

Example weights:

- Love: +2 per tag
- Like: +1 per tag
- Dislike: -2 per tag

Normalize repeated tags so one large group does not dominate forever. A simple cap per tag is enough in first pass.

Important: manga ratings do not directly recommend already-rated manga. They teach the profile which tags/sources tend to be liked.

#### Source affinity score

Source should be weak, not dominant.

Examples:

- If many loved/liked manga came from source X, source X gets a small boost.
- If many disliked manga came from source X, source X gets a small penalty.

Keep this weaker than tag preference because users may dislike a source's catalog quality but still want some results.

#### Title similarity

Title similarity is weak for new recommendations. It is more useful for:

- finding variants;
- source migration;
- same-series matching;
- sequel/spinoff title overlap.

For Browse Recommendations, use a small bonus only, or skip initially.

#### Tracker score

AniList/MAL/MangaUpdates scores are optional enrichment only.

Do not query tracker data for every candidate in first pass. It is too slow and unreliable. Add later only with:

- high-confidence mapping;
- local cache;
- rate-limit awareness.

### Tag Normalization / Alias Plan

Normalization should be centralized and shared by:

- `GenreFilterMapper`;
- recommendation scoring;
- tag preference UI;
- cache query key generation.

Current `GenreFilterMapper` normalization:

- lowercase;
- replace non-alphanumeric with spaces;
- collapse spaces;
- trim.

That is a good start.

Add alias lookup after normalization:

1. Normalize raw tag.
2. Find alias row by `normalized_alias`.
3. If found, use `group_key`.
4. If not found, use normalized raw tag as its own group key.

Examples:

```text
"Yuri"       -> normalized "yuri"       -> group "girls_love"
"Girls Love" -> normalized "girls love" -> group "girls_love"
"GL"         -> normalized "gl"         -> group "girls_love"
"Yaoi"       -> normalized "yaoi"       -> group "boys_love"
"Boys Love"  -> normalized "boys love"  -> group "boys_love"
"BL"         -> normalized "bl"         -> group "boys_love"
```

Do not make aliases too aggressive. Some tags are related but not equivalent:

- `Isekai` and `Reincarnation` are related, not always identical.
- `Villainess` and `Otome Isekai` are related, not always identical.
- `Full Color` and `Webtoon` overlap but are not identical.

Future enhancement: related-tag weights. First pass should only support exact alias groups.

### Browse Recommendations Tab

Add a tab under Browse near Sources/Feed/Extensions/Migrate.

Possible title: `Recommendations`

Existing insertion point:

- `app/src/main/java/eu/kanade/tachiyomi/ui/browse/BrowseTab.kt`
- It currently builds `tabs` with `sourcesTab()`, `feedTab(...)`, `extensionsTab(...)`, `migrateSourceTab()`.

Tab behavior:

1. Load taste profile.
2. If no ratings and no tag preferences exist, show an empty state explaining that user should Like/Love/Dislike manga or set tag preferences.
3. Build top recommendation query tags from:
   - explicit preferred tags;
   - strongest learned positive tags;
   - excluding blocked/disliked tags.
4. Select eligible sources:
   - `sourceManager.getVisibleCatalogueSources()`;
   - exclude disabled source ids;
   - take first 20;
   - maybe exclude local/stub sources.
5. For each source:
   - check cache;
   - if cache hit, show cached row immediately;
   - if cache miss, perform bounded extension search;
   - convert results to local manga;
   - enrich top results if genre missing;
   - hard-filter blocked tags;
   - score;
   - show top 10.
6. Tapping row header opens `BrowseSourceScreen` with the filters/query used for that source.
7. Tapping a manga opens `MangaScreen(manga.id, true)`.
8. Manual refresh invalidates cache for current profile/source set.

The tab can reuse `GlobalSearchResultItem` and `GlobalSearchCardRow` patterns, similar to:

- `FeedScreen`;
- `GlobalSearchScreen`;
- `exh.recs.components.RecommendsScreen`.

### Extension Search Limits

Do not run unbounded searches.

Required limits:

- Max eligible sources: 20.
- Max displayed results per source: 10.
- Max search attempts per source: 10.

Recommended first-pass search strategy:

1. Use top 3-5 positive tag groups from profile.
2. Use `GenreFilterMapper` to map tags into source filters.
3. Prefer one combined filtered search per source if filters can represent multiple tags.
4. If no filters match, fallback to text queries for top tags.
5. Cap fallback text searches at 10 per source.
6. Deduplicate by source/url.
7. Enrich only the top N raw candidates per source. Suggested N: 10 or 20.

Important: "10 searches per extension" should mean search attempts, not displayed results. Displayed results are separately capped at 10.

### Cache Plan

Extension searches can be cached because Komikku receives concrete result lists.

Cache does **not** store entire website/API responses. It stores the ordered list of local manga ids produced by the search.

Flow:

1. Build profile fingerprint from:
   - all tag preferences;
   - all manga taste rows;
   - alias table version;
   - algorithm version.
2. For each source, build query key from:
   - source id;
   - profile fingerprint;
   - selected query tags;
   - filters/text query;
   - max-result/search settings.
3. Check `recommendation_cache`.
4. If cache exists and `expires_at > now`, load manga ids and display immediately.
5. If cache miss, run extension search and save result ids/scores/reasons.
6. Manual refresh deletes/ignores cache for current profile.

Default TTL:

- 24 hours.

Invalidation:

- profile fingerprint change invalidates automatically;
- manual refresh invalidates explicitly;
- expired rows can be pruned opportunistically.

User concern addressed:

The result came from extension web/API calls, but once Komikku has the results, it can cache the list locally just like it stores manga/search results locally.

### Manga Detail UI

Add taste control to manga detail.

Recommended UI:

- Do not add three large permanent buttons if it crowds the existing action row.
- Prefer one compact action in the action row or toolbar overflow:
  - shows current state;
  - opens menu: Dislike, Like, Love, Clear.

Possible icons:

- Like: heart or thumb up.
- Love: filled favorite/star.
- Dislike: thumb down.
- Clear: close/remove.

Existing insertion points:

- `MangaActionRow` in `app/src/main/java/eu/kanade/presentation/manga/components/MangaInfoHeader.kt`
- `MangaToolbar` overflow in `app/src/main/java/eu/kanade/presentation/manga/components/MangaToolbar.kt`
- `MangaScreenModel` for actions/state.

Design rule:

- This rating is separate from library favorite.
- It should work whether manga is in the library or not.
- It should persist after the manga is removed from library as long as the manga row remains in DB.

### Tag Preferences UI

Add a way to manage explicit tag preferences.

First-pass UI options:

1. Inside Browse Recommendations tab:
   - AppBar action: `Tag preferences`.
   - Opens screen/dialog for preferred/disliked/blocked tags.
2. From manga detail tags:
   - Long-press a tag or use tag menu to Prefer/Dislike/Block.
3. Settings:
   - Optional later.

Minimum viable UI:

- list current preferred tags;
- list current disliked tags;
- list current blocked tags;
- add tag manually;
- remove tag;
- change preference.

Future UI:

- discovered tags from rated manga;
- suggested alias grouping;
- editable alias groups.

### Source Controls

The user wants the ability to disable certain extensions from the Recommendations tab.

First-pass model:

- default: all visible catalogue sources eligible;
- exclude disabled source ids;
- take first 20.

UI options:

1. Recommendations tab AppBar action: `Sources`.
2. Screen lists visible catalogue sources with switches.
3. Switch off means excluded from personal recommendations.

This should not change normal Browse sources, migration sources, feed, or extension installation.

Storage:

- SQL table `recommendation_disabled_source(source_id INTEGER PRIMARY KEY)` if backup is required;
- preference set if speed is preferred.

SQL is easier to include in backup later. Preference is simpler.

### Backup / Restore

The user explicitly wants taste data exported with Komikku backup.

Include:

- manga taste ratings;
- explicit tag preferences;
- tag alias customizations if user-editable;
- disabled recommendation sources if stored as durable user setting.

Do not backup:

- `recommendation_cache`.

Reason:

- cache is transient;
- backing it up bloats backups;
- cache depends on installed sources and current network state.

Backup model changes likely touch:

- `app/src/main/java/eu/kanade/tachiyomi/data/backup/models`
- backup creator files under `app/src/main/java/eu/kanade/tachiyomi/data/backup/create`
- restore files under `app/src/main/java/eu/kanade/tachiyomi/data/backup/restore`

Use new proto numbers safely. Do not reuse existing proto fields.

### Implementation Order

Recommended order for Claude:

#### Phase 0 - Cleanup / Guardrails

1. Decide whether to keep or remove earlier migration scoring changes.
   - Current user goal is recommendations, not migration.
   - Migration changes add extra detail fetching and can slow migration.
   - If unrelated, remove them before building personal recommendations.
2. Keep existing cross-extension recommendation feature unless user asks to remove.
3. Ensure branch remains private/local.

#### Phase 1 - Database and Domain

1. Add SQLDelight schema files:
   - `manga_taste.sq`
   - `tag_taste.sq`
   - `tag_alias.sq`
   - `recommendation_cache.sq`
   - optional `recommendation_disabled_source.sq`
2. Add migration file after current latest `45.sqm`, likely `46.sqm`.
3. Add data/domain models:
   - `MangaTaste`
   - `TagTaste`
   - `TagAlias`
   - `RecommendationCacheEntry`
4. Add repositories/interactors:
   - `TasteRepository`
   - `GetMangaTaste`
   - `SetMangaTaste`
   - `ClearMangaTaste`
   - `GetTasteProfile`
   - `SetTagTaste`
   - `GetTagTaste`
   - `UpsertRecommendationCache`
   - `GetRecommendationCache`
   - `ClearRecommendationCache`
5. Register repository/interactors in domain module.

Verification:

- SQLDelight compile.
- Unit tests for profile building.

#### Phase 2 - Normalization and Scoring

1. Extract/centralize tag normalization.
2. Implement alias resolution.
3. Implement `PersonalRecommendationScorer`.
4. Scorer input:
   - taste profile;
   - candidate manga;
   - source id;
   - optional tracker score later.
5. Scorer output:
   - score;
   - blocked/hide flag;
   - reason strings or matched tag groups.

Unit tests:

- blocked tag hides candidate;
- `Yuri`, `Girls Love`, `GL` map to same group;
- liked/loved manga teach positive tag weights;
- disliked manga teaches negative tag weights;
- explicit tag preference outranks learned tag preference;
- source affinity is weak;
- candidate with missing tags does not crash.

#### Phase 3 - Manga Detail Taste UI

1. Add taste state to `MangaScreenModel.State.Success`.
2. Subscribe to taste changes for the current manga.
3. Add action handler to set/clear taste.
4. Add UI control in action row or overflow.
5. Add strings in `i18n-kmk` base:
   - `taste_dislike`
   - `taste_like`
   - `taste_love`
   - `taste_clear`
   - `taste_rating`

Verification:

- Mark manga Love/Like/Dislike.
- Leave and re-open manga page; state persists.
- Remove from library; taste remains if manga row remains.

#### Phase 4 - Browse Recommendations Tab

1. Add `RecommendationsTab` under Browse.
2. Add screen model:
   - builds taste profile;
   - selects eligible sources;
   - uses cache;
   - runs bounded source search;
   - updates rows independently as they load.
3. Reuse existing UI patterns:
   - `GlobalSearchResultItem`;
   - `GlobalSearchCardRow`;
   - loading/error rows.
4. Enforce caps:
   - first 20 eligible sources;
   - max 10 visible results per source;
   - max 10 search attempts per source.
5. Tapping source row opens `BrowseSourceScreen` with filters/query.
6. Tapping manga opens `MangaScreen`.

Verification:

- Empty state when no profile exists.
- Shows rows after setting preferences.
- Source cap enforced.
- Result cap enforced.
- Disabled sources do not show.
- Manual refresh works.

#### Phase 5 - Cache

1. Show fresh cache immediately.
2. Search only when cache missing/expired/manual refresh.
3. Default TTL 24 hours.
4. Do not backup cache.

Verification:

- First open performs searches.
- Second open shows cached rows without network work.
- Manual refresh updates cache.
- Taste/profile change invalidates old cache.

#### Phase 6 - Tag Preferences and Source Controls

1. Add tag preference management screen/dialog.
2. Add source inclusion/exclusion screen/dialog.
3. Add basic built-in aliases.
4. Optional: allow user alias edits.

Verification:

- Add blocked tag; results with matching alias disappear.
- Disable source; row disappears.
- Re-enable source; row returns.

#### Phase 7 - Backup / Restore

1. Extend backup models with taste/tag/source preference data.
2. Extend backup creation.
3. Extend restore.
4. Do not backup recommendation cache.

Verification:

- Create backup with ratings/tag prefs.
- Restore into clean install.
- Ratings and tag prefs return.
- Recommendations tab uses restored profile.

### Testing Plan

Run focused unit tests first:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests exh.recs.RecommendationScorerTest --tests exh.recs.sources.GenreFilterMapperTest --tests <new personal recommender tests> --no-daemon
```

Then:

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat spotlessCheck
.\gradlew.bat assembleDebug
```

On this Windows machine, Gradle has previously needed:

- `JAVA_HOME=C:\Users\USER\Downloads\Komikku\komikku-source\.tools\jdk21\jdk-21.0.11+10`
- `ANDROID_HOME=C:\Users\USER\Downloads\Komikku\komikku-source\.tools\android-sdk`
- `ANDROID_SDK_ROOT=C:\Users\USER\Downloads\Komikku\komikku-source\.tools\android-sdk`
- `GRADLE_USER_HOME=C:\Users\USER\Downloads\Komikku\komikku-source\.tools\gradle-home`
- `GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT`
- Gradle executable: `C:\Users\USER\Downloads\Komikku\gradle-dist\gradle-9.3.1\bin\gradle.bat`

Run test suites in a single Gradle invocation if possible. Earlier separate parallel Gradle runs caused cache/resource race failures.

### UX Notes

Recommendations should be understandable. Show small reason text/chips:

- `Matched: Villainess, Regression, Revenge`
- `From liked tags: Action + Fantasy`
- `Personal recommendations`

Avoid making the Recommendations tab look like a landing page. It is a working browse surface.

Suggested row structure:

- Row title: source name.
- Subtitle: `Personal recommendations`.
- Cards: manga covers/titles.
- Optional chips/reasons under card if existing card design can support it without clutter.

### Open Decisions Before Coding

1. Should `manga_taste` cascade delete when non-library manga is purged, or preserve source/url/title snapshots independently?
2. Should disabled recommendation sources be preference-backed or SQL-backed?
3. Should alias groups be user-editable in first pass, or built-in only?
4. Should the personal Recommendations tab include the current manga-page cross-extension recommendations, or remain separate?
5. Should already loved/liked manga be hidden by default in Browse Recommendations?
6. Should manual refresh be per-source row, whole tab, or both?
7. Should explicit blocked tags apply globally everywhere in Komikku or only in Recommendations?

Recommended answers for first implementation:

1. Use snapshot columns plus `manga_id` for `manga_taste`.
2. SQL-backed disabled sources if backup is required; preference-backed if speed is preferred.
3. Built-in aliases first; user-editable later.
4. Keep manga-page recommendations and personal Browse recommendations separate.
5. Hide already disliked; optionally hide already liked/loved via setting.
6. Whole-tab refresh first.
7. Apply blocked tags only in Recommendations for now.

### Summary For Claude

Implement a new private personal recommendation system, not a tracker-based recommendation system.

Core must-have:

- persistent manga taste ratings;
- explicit tag preferences with hard blocks;
- basic tag alias normalization;
- Browse Recommendations tab;
- capped extension searches;
- cached result rows;
- source exclusion controls;
- backup/restore for taste data.

Do not rely on AniList/MAL. Treat tracker score as future optional enrichment only.

Do not use library membership as the taste source of truth.

Do not cache raw website/API responses. Cache ordered local manga result ids and scoring metadata.

Keep cross-extension source calls bounded:

- 20 eligible sources max by default;
- 10 visible results per source;
- 10 search attempts per source max.

The implementation should be phased and test-backed. The database/domain/scoring foundation should land before building the full Browse tab.

---

## Phase 2 Implementation — Normalization Consolidation and PersonalRecommendationScorer

Date: 2026-06-10

Status: COMPLETE — all tests pass.

### Goal

Centralize tag normalization so all scoring, filter mapping, and cache-key generation use identical logic. Implement `PersonalRecommendationScorer` that turns a `TasteProfile` into a scored + ranked candidate list.

### Why

Phase 1 left `GenreFilterMapper` with its own private `normalize()` that duplicated the logic in `TagNormalization.normalizeTag()`. Unifying them ensures "Sci-Fi" from a source filter and "Sci-Fi" from a manga genre always resolve to the same normalized form.

`PersonalRecommendationScorer` is the core decision engine. All later phases (Browse tab, cache) call it to decide which candidates to show and in what order.

### What was added and where

#### `app/src/main/java/exh/recs/sources/GenreFilterMapper.kt` (MODIFIED)

Removed the private `normalize()` / `nonWordRegex` / `multiSpaceRegex` block. Replaced with a one-line delegation to the shared extension:

```kotlin
import tachiyomi.domain.taste.model.normalizeTag
// ...
internal fun String.normalize(): String = normalizeTag()
```

The public API (`buildSearch`, `SearchParams`) is unchanged.

#### `app/src/main/java/exh/recs/PersonalRecommendationScorer.kt` (CREATED)

```kotlin
// KMK -->
internal object PersonalRecommendationScorer {
    data class ScoredCandidate(
        val manga: Manga, val score: Double, val blocked: Boolean,
        val matchedGroups: List<String>, val reasons: List<String>
    )

    fun score(candidate: Manga, profile: TasteProfile, aliasMap: Map<String, String>): ScoredCandidate
    fun rankCandidates(candidates: List<Manga>, profile: TasteProfile, aliasMap: Map<String, String>, limit: Int = 10): List<ScoredCandidate>
}
// KMK <--
```

**Hard filter:** Any candidate tag that resolves (via `aliasMap`) to a group in `blockedGroups` immediately returns `blocked = true`, `score = 0.0`. Blocking happens before any arithmetic.

**Soft score:**
- Explicit prefer: +3 per matched group in `explicitTagPreferences` with PREFER
- Explicit dislike: -2 per matched group in `explicitTagPreferences` with DISLIKE
- Learned weight: full `learnedTagWeights[group]` added per matching group
- Source affinity: `sourceAffinity[candidate.source]` (weak; typical value ±0.1–0.5)

**`rankCandidates`:** Maps all candidates through `score()`, filters blocked ones, sorts descending, takes `limit`.

**Reasons:** Human-readable strings included in `ScoredCandidate.reasons`, e.g. `"Preferred tags: villainess, regression"`, `"Liked tags: action, fantasy"`. Used in Phase 4/5 UI chips.

### Tests

| File | Tests | Result |
|---|---|---|
| `app/src/test/java/exh/recs/PersonalRecommendationScorerTest.kt` | 12 | ALL PASS |

Scenarios covered:
- Blocked tag causes hard rejection (score=0, blocked=true)
- Blocked group via alias (`Yuri` → `girls_love`, profile blocks `girls_love`)
- Preferred tag gives positive score
- Disliked tag gives negative score
- Explicit prefer outweighs learned weight alone
- Learned positive weight raises score
- Learned negative weight lowers score
- Source affinity adds small bonus; is weaker than tag weight
- No-genre candidate scores zero from tag components
- Yuri and Girls Love score identically via alias to same group key
- `rankCandidates` sorts descending and respects limit
- `rankCandidates` excludes blocked candidates

### Verification

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "exh.taste.*" --tests "exh.recs.*" --no-daemon
# All 35 tests pass (12 personal scorer + 8 genre filter mapper + 8 taste profile + 7 normalization)
# (Gradle shows only re-run tests; check build/test-results/testDebugUnitTest/*.xml for full counts)
```

---

## Phase 1 Implementation — Database and Domain Foundation

Date: 2026-06-10

Status: COMPLETE — all tests pass, SQLDelight codegen succeeds.

### Goal

Lay the persistent data layer, domain models, and interactors for the personal recommendation system. No UI or scoring logic yet.

### Why

The rest of the feature (scorer, Browse tab, backup/restore) all depend on a working DB layer and repository/interactor contracts. Building this first means later phases can focus on behavior, not data wiring.

### What was added and where

#### SQLDelight schema files (`data/src/main/sqldelight/tachiyomi/data/`)

Five new `.sq` files, each `// KMK --> ... // KMK <--` wrapped:

- **`manga_taste.sq`** — `manga_taste` table. PK on `manga_id`, FK CASCADE to `mangas._id`. Snapshot columns `source`, `url`, `title` for backup/debugging. Queries: `getByMangaId`, `getAll`, `upsert`, `delete`, `deleteAll`.
- **`tag_taste.sq`** — `tag_taste` table. PK on `normalized_tag`. `preference` column stores `-2` (block) / `-1` (dislike) / `+1` (prefer). Queries: `getByNormalizedTag`, `getAll`, `getAllAsFlow`, `upsert`, `delete`, `deleteAll`.
- **`tag_alias.sq`** — `tag_alias` table. PK on `alias`. Stores `normalized_alias` and `group_key` for synonym resolution. Queries: `getByNormalizedAlias`, `getAll`, `upsert`, `delete`.
- **`recommendation_cache.sq`** — `recommendation_cache` table. PK on `cache_key`. Stores serialized `result_manga_ids`/`result_scores`/`result_reasons` JSON, `profile_fingerprint`, `expires_at`. Queries: `getByCacheKey`, `getBySourceId`, `upsert`, `deleteByCacheKey`, `deleteBySourceId`, `deleteExpired`, `deleteAll`.
- **`recommendation_disabled_source.sq`** — `recommendation_disabled_source` table. PK on `source_id`. Queries: `getAll`, `getAllAsFlow`, `insert`, `delete`, `deleteAll`.

#### Migration (`data/src/main/sqldelight/tachiyomi/migrations/46.sqm`)

Creates all five tables and their indexes. Also seeds built-in alias groups for common tag synonyms:
- `girls_love`: Yuri, Girls Love, GL, Shoujo Ai
- `boys_love`: Yaoi, Boys Love, BL, Shounen Ai
- `sci_fi`: Sci-Fi, Science Fiction, Sci Fi

#### Tag normalization (`domain/src/main/java/tachiyomi/domain/taste/model/TagNormalization.kt`)

Centralizes `String.normalizeTag()` as a top-level extension. Steps: lowercase (ROOT locale), replace non-alphanumeric with spaces, collapse spaces, trim. Shared by scoring, filter mapping, and cache key generation. `GenreFilterMapper` can be updated to use this in Phase 2.

#### Domain models (`domain/src/main/java/tachiyomi/domain/taste/model/`)

- `MangaTaste.kt` — data class + `MangaRating` enum (DISLIKE=-1, LIKE=1, LOVE=2).
- `TagTaste.kt` — data class + `TagPreference` enum (BLOCK=-2, DISLIKE=-1, PREFER=1).
- `TagAlias.kt` — data class.
- `RecommendationCacheEntry.kt` — data class.
- `TasteProfile.kt` — computed profile: `learnedTagWeights: Map<String, Double>`, `explicitTagPreferences: Map<String, Int>`, `sourceAffinity: Map<Long, Double>`, `blockedGroups: Set<String>`. Has `isEmpty()` helper and `EMPTY` companion.

#### Repository interfaces (`domain/src/main/java/tachiyomi/domain/taste/repository/`)

- `TasteRepository.kt` — covers all four tables: manga_taste, tag_taste, tag_alias, recommendation_disabled_source.
- `RecommendationCacheRepository.kt` — covers recommendation_cache.

#### Interactors (`domain/src/main/java/tachiyomi/domain/taste/interactor/`)

| Interactor | Description |
|---|---|
| `GetMangaTaste` | `await(mangaId)`, `subscribe(mangaId)`, `awaitAll()` |
| `SetMangaTaste` | `await(mangaId, source, url, title, rating)` — sets `created_at` / `updated_at` |
| `ClearMangaTaste` | `await(mangaId)` |
| `GetTagTaste` | `await(tag)`, `awaitAll()`, `subscribeAll()` |
| `SetTagTaste` | `await(displayName, preference)` — normalizes tag before storing |
| `ClearTagTaste` | `await(normalizedTag)` |
| `GetTagAliases` | `awaitAll()`, `awaitByNormalized(alias)`, `awaitAliasMap()` |
| `UpsertTagAlias` | `await(alias, groupKey, displayName)` |
| `GetTasteProfile` | `await()` — builds `TasteProfile` from taste rows + MangaRepository genres |
| `GetDisabledRecommendationSources` | `await()`, `subscribe()` |
| `SetRecommendationSourceEnabled` | `await(sourceId, enabled: Boolean)` |
| `GetRecommendationCache` | `await(cacheKey)`, `awaitForSource(sourceId)` |
| `UpsertRecommendationCache` | `await(entry)` |
| `ClearRecommendationCache` | `awaitAll()`, `awaitForSource(sourceId)`, `awaitExpired(now)` |

#### `GetTasteProfile` logic

Injects `TasteRepository` and `MangaRepository`. For each taste row:
1. Fetches manga genres via `MangaRepository.getMangaById()`. Missing rows are skipped (`runCatching`).
2. Normalizes each genre via `normalizeTag()`.
3. Resolves to group key via alias map (falls back to normalized string if no alias).
4. Accumulates: Love=+2/tag, Like=+1/tag, Dislike=-2/tag.
5. Caps each group key weight to [-10, 10] to prevent one large-genre manga from dominating.
6. Builds source affinity at 0.1 per net positive source rating.
7. Collects blocked groups from `tag_taste` rows where `preference == -2`.

#### Data implementations (`data/src/main/java/tachiyomi/data/taste/`)

- `TasteRepositoryImpl.kt` — uses `DatabaseHandler`. Query objects accessed as `manga_tasteQueries`, `tag_tasteQueries`, `tag_aliasQueries`, `recommendation_disabled_sourceQueries` (SQLDelight uses underscore names matching the table names).
- `RecommendationCacheRepositoryImpl.kt` — uses `recommendation_cacheQueries`.

#### DI registration (`app/src/main/java/eu/kanade/domain/KMKDomainModule.kt`)

Added inside `// KMK -->` block:
- `addSingletonFactory<TasteRepository>` → `TasteRepositoryImpl(get())`
- `addFactory` for all 14 interactors above
- `addSingletonFactory<RecommendationCacheRepository>` → `RecommendationCacheRepositoryImpl(get())`

### Tests

| File | Tests | Result |
|---|---|---|
| `app/src/test/java/exh/taste/TagNormalizationTest.kt` | 7 | ALL PASS |
| `app/src/test/java/exh/taste/GetTasteProfileTest.kt` | 8 | ALL PASS |
| `app/src/test/java/exh/taste/StubMangaRepository.kt` | (helper for tests) | — |

`GetTasteProfileTest` scenarios:
- Loved manga tags contribute strong positive weight
- Liked manga tags contribute smaller weight than loved
- Disliked manga tags contribute negative weight
- Blocked tag group appears in `blockedGroups` set
- Alias resolves Yuri and Girls Love to the same group key `girls_love`
- Explicit preferred tag appears in `explicitTagPreferences`
- Learned weights are capped at 10 even with 20 loved manga sharing the same tag
- Missing manga row does not crash profile building

### Verification

```powershell
.\gradlew.bat :data:generateSqlDelightInterface --no-daemon
# → BUILD SUCCESSFUL

.\gradlew.bat :app:testDebugUnitTest --tests "exh.taste.*" --tests "exh.recs.*" --no-daemon
# → 23 tests passed (8 taste profile, 7 normalization, 8 genre filter mapper)
```

### Known limitations / deferred to later phases

- `GetTasteProfile` makes N individual `getMangaById` calls (one per rated manga). Acceptable for typical user scale (10-100 ratings). Can be optimized later if needed.
- `GenreFilterMapper.normalize()` is currently separate from `TagNormalization.normalizeTag()`. Phase 2 will consolidate them.
- Tag alias seeds are in the migration (`46.sqm`). User-editable aliases are deferred to Phase 6.
- `recommendation_disabled_source` is SQL-backed (supports backup/restore). Interactors expose it; UI is deferred to Phase 6.
