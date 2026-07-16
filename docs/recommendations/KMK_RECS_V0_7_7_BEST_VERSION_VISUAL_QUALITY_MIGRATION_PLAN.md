# KMK-Recs v0.7.7 Best Version / Visual Quality Migration Plan

Date: 2026-06-22

Status: future implementation plan. Do not implement until v0.7.6 is completed and the user explicitly approves this next pass.

Supersedes the mistakenly versioned draft:

```text
docs/recommendations/KMK_RECS_V0_7_6_BEST_VERSION_VISUAL_QUALITY_MIGRATION_PLAN.md
```

The best-version / visual-quality workflow should be implemented as:

```text
KMK-Recs v0.7.7
```

The v0.7.6 number is reserved for:

```text
KMK_RECS_V0_7_6_SOURCE_EVALUATION_CONTINUATION_AND_RECOMMENDATION_QUALITY_PLAN.md
```

## Purpose

Add a bounded, user-controlled `Find best version` workflow for a manga. The user should be able to search likely same-manga entries across installed sources, confirm which entries are actually the same manga, visually sample pages from a selected chapter, choose the best-looking source/version, and then migrate or copy using Komikku's existing migration behavior.

This must not become automatic global image-quality ranking. It should be manual, capped, cancellable, and safe when individual sources fail.

## Required Reading Before Coding

Read after v0.7.6 is implemented:

```text
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/DOCUMENTATION_RULES.md
docs/recommendations/CHAPTER_IMAGE_QUALITY_GLOBAL_SEARCH_FEASIBILITY_RESEARCH.md
docs/recommendations/KMK_RECS_V0_7_7_BEST_VERSION_VISUAL_QUALITY_MIGRATION_PLAN.md
RECOMMENDATION_VERSIONING.md
```

Inspect:

```text
app/src/main/java/exh/recs/matching/CrossExtensionMatchScreen.kt
app/src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt
app/src/main/java/exh/recs/matching/CrossExtensionMatchQueryPlanner.kt
app/src/main/java/exh/recs/matching/CrossExtensionMatchRouteMode.kt
app/src/main/java/mihon/feature/migration/list/MigrationListScreenModel.kt
app/src/main/java/mihon/feature/migration/list/search/SmartSourceSearchEngine.kt
app/src/main/java/mihon/feature/migration/list/search/SourceMatchScorer.kt
app/src/main/java/mihon/domain/migration/usecases/MigrateMangaUseCase.kt
app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt
source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/Source.kt
source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/online/HttpSource.kt
source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/model/Page.kt
app/src/main/java/eu/kanade/tachiyomi/data/download/Downloader.kt
app/src/main/java/eu/kanade/domain/manga/interactor/GetPagePreviews.kt
app/src/main/java/eu/kanade/tachiyomi/data/cache/PagePreviewCache.kt
app/src/main/java/eu/kanade/tachiyomi/data/coil/PagePreviewFetcher.kt
```

## User Requirements

- Add a manga-detail action such as `Find best version`.
- Use installed sources only.
- Do not change normal global search behavior.
- Search likely same-manga candidates across sources using existing cross-extension matching as the foundation.
- Do not trust source search blindly; the user must confirm or deselect wrong matches.
- Add a reversible setting for whether same-manga results are selected by default.
- Add a configurable same-manga per-source result cap: `1`, `2`, `5`, or `10`.
- Apply that cap to all bounded same-manga workflows: Love, Like, Dislike, Seen, Favorite, and Find best version.
- Default cap should remain `2`.
- Default auto-selection should remain enabled.
- User should select chapter before visual preview.
- Default chapter should be latest read/in-progress chapter when available.
- If latest read chapter is unavailable, fall back to latest available origin chapter.
- User can change chapter before or after previewing.
- Do not default to first pages because some sources prepend ads/promotional pages.
- Add preview sample size options: `2`, `5`, or `10` pages.
- Default preview sample size should be `5`.
- Add an option to avoid first pages by default.
- Let user choose the best candidate, then show migration/copy confirmation.
- Reuse existing migration/copy logic.
- Store a local quality signal for future source-quality learning, but do not wire it into Source Evaluation scoring in this pass.

## New Preferences

Add to `SourcePreferences`:

```kotlin
fun sameMangaMatchResultsPerSource() = preferenceStore.getInt("same_manga_match_results_per_source", 2)
fun sameMangaMatchPreselectResults() = preferenceStore.getBoolean("same_manga_match_preselect_results", true)
fun bestVersionPreviewSampleSize() = preferenceStore.getInt("best_version_preview_sample_size", 5)
fun bestVersionAvoidFirstPages() = preferenceStore.getBoolean("best_version_avoid_first_pages", true)
```

Clamp result cap to `1`, `2`, `5`, or `10`; invalid value falls back to `2`.

Clamp preview size to `2`, `5`, or `10`; invalid value falls back to `5`.

## Architecture Recommendation

Preferred:

1. Extract shared candidate-search behavior from `CrossExtensionMatchScreenModel` into a helper/service, such as:

```text
SameMangaCandidateSearcher
SameMangaMatchSettings
SameMangaCandidateResult
```

2. Update existing CrossExtensionMatch workflows to use the new cap and auto-select preferences.

3. Add a dedicated best-version flow:

```text
BestVersionCompareScreen
BestVersionCompareScreenModel
```

Use primitive Voyager route args only:

```kotlin
BestVersionCompareScreen(originMangaId: Long)
```

Do not store sealed mode objects, source objects, manga objects, chapter objects, page objects, lambdas, or large lists in screen constructors.

## Candidate Search Flow

1. User opens manga detail page.
2. User taps `Find best version`.
3. App opens `BestVersionCompareScreen(originMangaId)`.
4. Screen model loads origin manga.
5. It searches matching installed sources using `CrossExtensionMatchQueryPlanner.buildQueries(originManga)`.
6. Search respects recommendation languages and source priority order.
7. Origin manga is filtered out before applying cap.
8. Cap comes from `sameMangaMatchResultsPerSource()`.
9. Candidates are selected by default only if `sameMangaMatchPreselectResults()` is true.
10. User confirms candidates before chapter/page probing starts.

## Chapter And Page Preview

After candidate confirmation:

- fetch chapter lists for origin and selected candidates;
- default to latest read/in-progress origin chapter when possible;
- otherwise default to latest origin chapter;
- match candidate chapters by `chapterNumber` first;
- fallback to normalized chapter name/number;
- never match by list index;
- show `Chapter unavailable` per candidate when needed.

Page preview:

- use sample size `2`, `5`, or `10`;
- avoid first 1-2 pages and final page when enabled and possible;
- sample roughly the middle portion of the chapter;
- preserve actual sampled indexes per candidate;
- let user change chapter/sample before or after preview.

## Visual Preview

Preview cards should show:

- source name,
- manga title,
- matched chapter,
- page count,
- sampled page images,
- per-page/per-candidate loading and error state,
- evidence such as `Loaded 5/5 sample pages`.

Limits:

- selected candidates only;
- limited concurrency;
- timeouts;
- cancellable;
- no full chapter download;
- no background automatic preview;
- store metadata only, not full images beyond existing cache.

## Migration / Copy

After user chooses a best candidate:

- show confirmation;
- offer migration/copy consistent with Komikku migration;
- reuse `MigrateMangaUseCase` if possible;
- add a small preparation helper only if needed to fetch details/sync chapters before invoking migration.

Do not silently migrate.

## Local Quality Signal

Add local-only persistence for future source-quality learning:

```text
manga_source_quality_signal
```

Suggested fields:

```text
id
origin_source_id
origin_url
origin_title
selected_source_id
selected_url
selected_title
selected_source_name
compared_candidates_json
chapter_number
chapter_name
sample_size
sampled_pages_json
selected_at
quality_signal_version
```

Initial behavior:

- write a signal after user chooses a best candidate and confirms migration/copy or explicitly saves the choice;
- do not feed into Source Evaluation or For You ranking yet;
- backup/restore can be deferred unless simple.

## Error Handling

Convert to UI state, not crashes:

- source search failure,
- details fetch failure,
- chapter list failure,
- no matching chapter,
- page list failure,
- image URL/image load failure,
- timeout,
- network disconnect,
- source uninstalled mid-flow,
- origin manga missing,
- selected target missing,
- selected target has no chapters.

## Non-Goals

Do not implement:

- automatic global image-quality ranking,
- quality probing for non-installed extensions,
- mass sampling all sources,
- OCR,
- translation-quality scoring,
- watermark detection,
- image sharpness scoring,
- source-specific scraping rules,
- normal global search limit changes,
- Browse > For You ranking changes,
- automatic migration without confirmation.

## Tests

Add tests for:

- cap preference defaults/clamping;
- cap values `1`, `2`, `5`, `10`;
- origin filtered before cap;
- auto-select true/false;
- manual deselection preserved while results update;
- latest read chapter default;
- latest origin fallback;
- candidate chapter matching by chapter number;
- page sample helper avoids first pages when possible;
- page sample helper handles tiny chapters;
- sample sizes `2`, `5`, `10`;
- local quality signal insert/query if DB added;
- migration target preparation success/failure.

Run:

```text
:app:testDebugUnitTest --tests "*CrossExtensionMatch*"
:app:testDebugUnitTest --tests "*LovedManga*"
:app:testDebugUnitTest --tests "*Migration*"
:app:testDebugUnitTest
:app:assembleDebug
```

## Documentation After Implementation

Claude must create:

```text
docs/recommendations/KMK_RECS_V0_7_7_BEST_VERSION_VISUAL_QUALITY_MIGRATION_IMPLEMENTATION.md
```

Claude must update:

```text
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/README.md
RECOMMENDATION_VERSIONING.md
```

If an APK is built, expected naming:

```text
Komikku-v1.13.6-kmk.7.7-debug.apk
```

