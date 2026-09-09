# Chapter / Image Quality Global Search Feasibility Research

Date: 2026-06-22

Status: research and feasibility assessment. This is not an implementation plan.

## Question

Can Komikku/KMK compare same-manga results across extensions and identify which source has the best chapter coverage or best page/image quality?

The desired outcome is a global-search-like workflow that helps the user find the best version of a manga across sources, especially when:

- the same manga appears in multiple extensions,
- chapters differ by source,
- some sources have missing chapters,
- some chapters have lower-quality scans/images,
- the user wants to choose the best source/version before reading or migrating.

## Summary Answer

This is **partially feasible**, but only with careful limits.

The app can feasibly compare:

- whether candidate manga resolve successfully,
- manga metadata,
- chapter count,
- latest chapter number,
- chapter list availability,
- page count for a small sampled set of chapters,
- image URL availability,
- page/image fetch success,
- basic downloaded image metadata for a small sample.

The app cannot reliably or cheaply know universal image quality across every extension without fetching image data. Source APIs do not expose a standard quality score.

Recommended direction:

```text
Build a manual, bounded "Compare versions" / "Find best version" workflow.
Do not run image-quality probing automatically across all global search results.
```

## Current Code Capabilities

### Source API

The Komikku source API exposes these core methods:

```text
Source.getMangaDetails(manga)
Source.getChapterList(manga)
Source.getPageList(chapter)
HttpSource.getImageUrl(page)
HttpSource.getImage(page)
```

Relevant files:

```text
source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/Source.kt
source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/online/HttpSource.kt
source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/model/Page.kt
```

Meaning:

- It is possible to fetch chapter lists for candidate manga.
- It is possible to fetch page lists for candidate chapters.
- It is possible to fetch image URLs and image responses for pages.
- It is not possible to ask a source for "quality" directly.

### Migration Already Compares Chapter Coverage

`MigrationListScreenModel` already performs source searches and can fetch candidate details/chapters:

```text
app/src/main/java/mihon/feature/migration/list/MigrationListScreenModel.kt
```

Important existing behavior:

- `SmartSourceSearchEngine` searches candidate sources.
- `SourceMatchScorer.score(...)` receives current and candidate chapter count/latest chapter data.
- `migrationPrioritizeByChapters` can prioritize candidates by chapter coverage.

This is the strongest existing foundation for a "best version" workflow.

What it already helps with:

- finding equivalent manga,
- comparing chapter count,
- comparing latest chapter,
- avoiding candidates with zero chapters.

What it does not do:

- compare page/image quality,
- compare page count per chapter,
- compare image resolution,
- compare scan completeness,
- compare translation quality,
- detect watermarks/cropping/low-res scans.

### Downloader Already Fetches Page Lists And Images

`Downloader` already:

- calls `source.getPageList(chapter)`,
- resolves missing `page.imageUrl` via `source.getImageUrl(page)`,
- downloads images through `source.getImage(page)`,
- uses `ImageUtil` to determine image extension,
- can split tall images.

Relevant file:

```text
app/src/main/java/eu/kanade/tachiyomi/data/download/Downloader.kt
```

This proves the app can technically fetch page/image data. However, downloader behavior is for user-requested downloads, not cheap scoring.

### Page Preview Exists But Is Source-Limited

The app has `PagePreviewSource` support:

```text
source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/PagePreviewSource.kt
app/src/main/java/eu/kanade/domain/manga/interactor/GetPagePreviews.kt
app/src/main/java/eu/kanade/tachiyomi/data/cache/PagePreviewCache.kt
app/src/main/java/eu/kanade/tachiyomi/data/coil/PagePreviewFetcher.kt
```

This is useful but limited:

- only sources implementing `PagePreviewSource` can provide page previews,
- it is not a universal extension capability,
- it is better for UI previews than global quality scoring.

### Reader/Image Utilities Can Inspect Images

The codebase already uses image utilities and decoders in reader/downloader paths. That suggests a future quality sampler could inspect image metadata such as:

- width,
- height,
- aspect ratio,
- file type,
- byte size,
- decode success.

But this requires fetching image bytes or at least headers/partial data. Many image hosts may not provide enough useful metadata from HEAD requests alone.

## External Ecosystem Findings

Mihon/Tachiyomi-style APIs expose the same basic source contract: search, details, chapters, page lists, image URL/image response. They do not expose a standard "quality" field.

Mihon's extension FAQ explicitly says Mihon does not provide/host content and is not responsible for slow, down, missing chapters, or subpar image quality of sources. This supports the conclusion that quality varies by source website and cannot be centrally guaranteed.

Reference:

```text
https://mihon.app/docs/faq/browse/extensions
```

Mihon source API references:

```text
https://raw.githubusercontent.com/mihonapp/mihon/main/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/Source.kt
https://raw.githubusercontent.com/mihonapp/mihon/main/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/CatalogueSource.kt
https://raw.githubusercontent.com/mihonapp/mihon/main/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/model/Page.kt
```

## Feasible Quality Signals

### Safe / Cheap Signals

These are feasible and should be used first:

1. **Manga resolves**
   - Can fetch details without error.

2. **Chapter count**
   - `getChapterList()` count.
   - Already similar to migration logic.

3. **Latest chapter number**
   - Compare `chapterNumber`.
   - Existing migration code already computes latest chapter.

4. **Chapter list freshness**
   - Candidate has newer/latest chapters.

5. **Source/search reliability**
   - Candidate source returns data without errors/timeouts.

6. **Existing user-confirmed link groups**
   - If versions were already confirmed as same manga, confidence improves.

7. **Already downloaded/read source success**
   - If local history/downloads show repeated failures or success, use as weak local signal.

### Medium-Cost Signals

These require more network calls and should be bounded:

1. **Page count for selected chapters**
   - Fetch `getPageList()` for first/latest/sample chapters.
   - Compare whether chapters have expected non-zero page counts.

2. **Image URL availability**
   - Resolve `getImageUrl()` if `Page.imageUrl` is missing.
   - Counts failures.

3. **Page fetch success**
   - Try fetching first page or a small sample.
   - Do not download entire chapters automatically.

4. **Content length**
   - If available in response headers, approximate file size.
   - Not always reliable.

### Expensive / Risky Signals

These should not run automatically across many results:

1. **Image dimensions**
   - Requires fetching image bytes and decoding bounds.
   - Useful but network/storage/CPU expensive.

2. **Average chapter image resolution**
   - Requires sampling multiple pages.

3. **Image sharpness/compression detection**
   - Requires decoding and image analysis.
   - Risky, slow, likely overkill.

4. **Watermark/cropping detection**
   - Not realistic without complex image analysis.

5. **Translation quality**
   - Not realistically detectable.

## Practical Approaches

### Approach A: Metadata-Only Best Version

Use only:

- exact/smart same-manga match confidence,
- chapter count,
- latest chapter number,
- source reliability,
- user source preference,
- installed/source evaluation score.

Pros:

- fast,
- safe,
- low network cost,
- similar to migration logic,
- feasible now.

Cons:

- does not know image quality,
- may recommend a complete but lower-resolution source.

Recommendation:

```text
Good first implementation.
```

### Approach B: Bounded Chapter/Page Probe

After global search finds likely equivalent manga, user can tap:

```text
Compare versions
```

The app fetches chapter lists and page lists for a small number of selected candidate sources.

Sample strategy:

- max 5 candidate manga,
- max 3 chapters per candidate,
- sample latest chapter, first chapter, and one middle chapter if available,
- get page list only,
- do not fetch image bytes yet.

Signals:

- chapter count,
- latest chapter,
- sampled page counts,
- page list errors/timeouts,
- missing page URLs.

Pros:

- better than metadata only,
- still bounded,
- no image downloads required.

Cons:

- page count is not image quality,
- still network-heavy,
- some sources may fail or rate limit.

Recommendation:

```text
Best practical second step.
```

### Approach C: Manual Image Sample Probe

After Approach B, user can optionally run:

```text
Sample image quality
```

The app fetches a tiny number of image samples.

Suggested hard limits:

- max 3 candidate sources,
- max 1 chapter per source,
- max 2 pages per source,
- fetch first page and one middle page,
- decode bounds only when possible,
- cache results,
- user-initiated only.

Signals:

- fetch success,
- image byte size,
- image dimensions,
- file type,
- decode success,
- maybe bytes-per-pixel as a rough compression signal.

Pros:

- can actually estimate image quality.

Cons:

- consumes bandwidth,
- may trigger source protections,
- can be slow,
- dimensions do not guarantee translation/scan quality,
- some images are long-strip webtoon pages where width/height comparisons are tricky.

Recommendation:

```text
Feasible only as an optional manual deep check, not automatic background evaluation.
```

### Approach D: Automatic Global Quality Ranking

Automatically run all of this during global search across many extensions.

Pros:

- convenient if it worked.

Cons:

- too slow,
- too much bandwidth,
- likely to trigger rate limits,
- too many extension-specific failures,
- dangerous for app stability,
- poor UX if every search becomes heavy.

Recommendation:

```text
Do not implement.
```

## Suggested Product Shape

### Feature Name

Possible labels:

- `Compare versions`
- `Find best version`
- `Compare source quality`

Avoid promising:

- `Find perfect quality`
- `Best scan automatically`

because the result is probabilistic.

### Entry Points

Good entry points:

1. Manga detail page:
   - `Find best version`
   - uses current manga as origin.

2. Cross-extension matching screen:
   - after likely matches are found, add `Compare versions`.

3. Migration/search result screen:
   - add quality badges beside candidates.

4. Global search result:
   - long press or overflow: `Compare versions`.

### Workflow

Recommended workflow:

```text
User opens manga
-> taps Find best version
-> app runs existing cross-extension/global matching
-> user confirms likely same-manga candidates
-> app fetches chapter lists for selected candidates
-> app ranks chapter coverage
-> user may optionally run Sample image quality
-> app samples 1-2 pages from top candidates
-> app shows recommendation with evidence
```

This keeps expensive work user-controlled.

## Scoring Model

### Best Version Score

Separate scores:

```text
identityConfidence
chapterCoverageScore
pageAvailabilityScore
imageSampleScore
sourceReliabilityScore
userSourcePreferenceScore
```

Do not collapse everything into one opaque number only. Show reasons.

Example row:

```text
Asura Scans
Best coverage Â· 124 chapters Â· Latest 124 Â· Sample pages loaded Â· 2 image samples: 1600px wide
```

### Identity Confidence

Use:

- confirmed cross-source link group,
- exact title + author/artist,
- exact/similar title + description,
- user-confirmed match,
- existing cross-extension matching.

Do not compare chapter quality for candidates that are not likely the same manga.

### Chapter Coverage Score

Signals:

- candidate chapter count vs max chapter count among candidates,
- latest chapter number,
- missing/zero chapters,
- chapter list fetch success.

### Page Availability Score

Signals:

- sampled chapter page list count,
- page count is non-zero,
- image URL present or resolvable,
- no page-list timeout/error.

### Image Sample Score

Only for manual deep check.

Signals:

- image fetch succeeds,
- response content type is image,
- image dimensions can be decoded,
- width/height reasonable,
- byte size non-trivial,
- no decode error.

Possible rough formula:

```text
sampleScore =
    dimensionScore
    + byteSizeScore
    + fetchReliability
    - decodeErrorPenalty
```

But show it as evidence, not as absolute truth.

## Storage / Cache

Add cache only after proving the workflow.

Possible table:

```text
manga_version_quality_probe
```

Key:

```text
origin_source|origin_url|candidate_source|candidate_url|probe_version
```

Fields:

- candidate source/name,
- chapter count,
- latest chapter,
- sampled chapter URLs,
- sampled page counts,
- image sample widths/heights,
- image sample byte sizes,
- errors,
- score,
- evaluatedAt,
- expiresAt.

TTL:

- chapter/page probe: 7 days,
- image sample probe: 14 days,
- invalidate manually via refresh.

Do not store full images unless using existing cache paths. Store only metadata.

## Risks

### Network / Performance

Fetching chapter lists and page lists across many sources is expensive.

Mitigation:

- user-initiated only,
- max candidate count,
- max sampled chapters,
- max sampled pages,
- timeouts,
- cancellation,
- progress UI.

### Source Protections / Rate Limits

Some sources may block frequent page/image requests.

Mitigation:

- no automatic global runs,
- delays,
- per-source timeout,
- clear errors.

### False Confidence

Higher resolution does not always mean better translation or cleaner pages.

Mitigation:

- call it `sample quality`, not guaranteed quality,
- show evidence,
- let user choose.

### Webtoon vs Page Manga

Long-strip webtoon images have different dimensions from page manga.

Mitigation:

- compare candidates only within same manga/format,
- use width and fetch success more than total pixels,
- avoid over-penalizing very tall pages.

### Extension Diversity

Extensions behave differently.

Mitigation:

- no source-specific logic unless absolutely necessary,
- fail gracefully,
- cache results,
- let user manually confirm.

## Existing Functionality To Reuse

Use:

- cross-extension matching for candidate discovery,
- smart migration search for same-manga search/matching,
- `SourceMatchScorer` for title/chapter-count matching ideas,
- `MigrationListScreenModel` chapter count flow as reference,
- `Downloader` page/image fetch behavior as reference,
- `PagePreviewSource` when available,
- `PagePreviewCache` / existing cache patterns if sampling previews,
- cross-source link groups for known identity.

Do not reuse by copy/pasting large screen models. Extract small helpers if this becomes implementation.

## Feasibility Verdict

### Metadata/chapter-count comparison

```text
Feasible and useful.
```

This can be built from existing migration/source APIs.

### Page-list comparison

```text
Feasible if bounded.
```

Useful for detecting broken/empty chapters and rough completeness.

### Image sample comparison

```text
Feasible only as optional manual deep check.
```

Should be user-initiated, capped, cancellable, and cached.

### Fully automatic global quality ranking

```text
Not recommended.
```

Too expensive, too fragile, and too likely to make search slow or unstable.

## Recommended Next Step

Before implementation, create a focused plan for:

```text
KMK-Recs v0.5.x or v0.7.x: Compare Versions / Best Version Finder
```

Recommended first implementation scope:

1. Use existing cross-extension matching/global search to find likely same-manga candidates.
2. User confirms candidates.
3. Fetch chapter lists only.
4. Rank by chapter count/latest chapter/source reliability.
5. Show evidence and let user pick.

Then a later optional phase can add:

1. sample page-list checks,
2. then manual image sample checks.

Do not start with image sampling. Start with the cheaper chapter-coverage comparison first.

