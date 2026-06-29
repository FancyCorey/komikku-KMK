# KMK-Recs v0.7.6 Best Version / Visual Quality Migration Plan

Date: 2026-06-22

Status: active implementation plan. Do not implement until explicitly instructed by the user.

Supersedes:

```text
docs/recommendations/KMK_RECS_V0_5_5_BEST_VERSION_VISUAL_QUALITY_MIGRATION_PLAN.md
```

The earlier v0.5.5 draft contains the same feature direction, but the user has decided this implementation must be versioned as `KMK-Recs v0.7.6`. The next implementation after this should be versioned as `KMK-Recs v0.7.7`.

## Purpose

Implement a bounded, user-controlled "Find best version" workflow for a manga. The user should be able to search likely same-manga entries across installed sources, confirm which entries are actually the same manga, visually sample pages from a selected chapter, choose the best-looking source/version, and then migrate or copy using Komikku's existing migration behavior.

This must not become automatic global image-quality ranking. It should be manual, capped, cancellable, and safe when individual sources fail.

## Versioning Requirements

- Use `KMK-Recs v0.7.6` for this implementation.
- If an APK is built, use the existing local APK naming pattern, for example:

```text
Komikku-v1.13.6-kmk.7.6-debug.apk
```

- Reserve `KMK-Recs v0.7.7` for the next approved implementation pass.
- Update `RECOMMENDATION_VERSIONING.md` with a v0.7.6 entry after implementation.
- Update local KMK What's New with user-facing feature notes only.

## Required Reading Before Coding

Read these first:

```text
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/DOCUMENTATION_RULES.md
docs/recommendations/CHAPTER_IMAGE_QUALITY_GLOBAL_SEARCH_FEASIBILITY_RESEARCH.md
docs/recommendations/KMK_RECS_V0_7_6_BEST_VERSION_VISUAL_QUALITY_MIGRATION_PLAN.md
RECOMMENDATION_VERSIONING.md
```

Also inspect the actual code before making changes:

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
- Search likely same-manga candidates across sources using the existing cross-extension matching logic as the foundation.
- Do not trust source search blindly; the user must confirm or deselect wrong matches.
- Add a reversible setting for whether same-manga results are selected by default.
- Add a configurable same-manga per-source result cap: `1`, `2`, `5`, or `10`.
- Apply that cap to all bounded same-manga workflows:
  - Love other versions
  - Like other versions
  - Dislike other versions
  - Seen other versions
  - Favorite other versions
  - Find best version
- Default cap should remain `2`.
- Default auto-selection should remain enabled to preserve the current UX.
- The user should be able to select the chapter before visual preview.
- Default chapter should be the latest read/in-progress chapter when available.
- If latest read chapter is unavailable, fall back to the latest available origin chapter.
- User must be able to change the chapter before or after previewing.
- Do not default to the first pages because some sources prepend ads/promotional pages.
- Add preview sample size options: `2`, `5`, or `10` pages.
- Default preview sample size should be `5`.
- Add an option to avoid first pages by default.
- Let the user choose the best candidate, then show migration/copy confirmation.
- Reuse existing migration/copy logic. Do not silently migrate.
- Store a local quality signal for future source-quality learning, but do not wire it into Source Evaluation scoring in this pass.

## New Preferences

Add to `SourcePreferences`.

### Same Manga Result Cap

```kotlin
fun sameMangaMatchResultsPerSource() = preferenceStore.getInt("same_manga_match_results_per_source", 2)
```

Allowed values:

```text
1, 2, 5, 10
```

Clamp any invalid value to `2`.

### Same Manga Auto-Select

```kotlin
fun sameMangaMatchPreselectResults() = preferenceStore.getBoolean("same_manga_match_preselect_results", true)
```

When true, candidates start selected by default. When false, candidates start unselected. Manual selection/deselection must still work. The origin manga must never be selected.

### Best Version Preview Sample Size

```kotlin
fun bestVersionPreviewSampleSize() = preferenceStore.getInt("best_version_preview_sample_size", 5)
```

Allowed values:

```text
2, 5, 10
```

Clamp invalid values to `5`.

### Avoid First Pages

```kotlin
fun bestVersionAvoidFirstPages() = preferenceStore.getBoolean("best_version_avoid_first_pages", true)
```

When enabled, automatic preview sampling should avoid the first 1-2 pages and final page when the chapter has enough pages.

## Settings UI

Add a clear settings section, preferably in Recommendation Settings:

```text
Same manga matching
```

Controls:

- `Same manga results per source`: choices `1`, `2`, `5`, `10`
- `Select matches by default`: switch
- `Best version preview pages`: choices `2`, `5`, `10`
- `Avoid first pages in preview`: switch

Make clear that the result cap applies only to bounded same-manga workflows, not normal global search.

## Architecture Recommendation

Do not overload `CrossExtensionMatchScreenModel.applyRating()` with the whole quality workflow.

Preferred approach:

1. Extract shared candidate-search behavior from `CrossExtensionMatchScreenModel` into a small helper/service, for example:

```text
SameMangaCandidateSearcher
SameMangaMatchSettings
SameMangaCandidateResult
```

2. Update existing CrossExtensionMatch workflows to use:
   - preference-backed per-source cap,
   - preference-backed auto-selection.

3. Add a dedicated best-version screen/model, for example:

```text
BestVersionCompareScreen
BestVersionCompareScreenModel
```

The screen constructor must use primitive route args only, for example:

```kotlin
BestVersionCompareScreen(originMangaId: Long)
```

Do not store sealed mode objects, source objects, manga objects, chapter objects, page objects, lambdas, or large lists in Voyager screen constructors.

## Candidate Search Flow

Flow:

1. User opens manga detail page.
2. User taps `Find best version`.
3. App opens `BestVersionCompareScreen(originMangaId)`.
4. Screen model loads origin manga.
5. It searches matching installed sources using `CrossExtensionMatchQueryPlanner.buildQueries(originManga)`.
6. Search respects recommendation languages and source priority order.
7. Origin manga is filtered out before applying the per-source cap.
8. Per-source cap comes from `sameMangaMatchResultsPerSource()`.
9. Candidates are selected by default only if `sameMangaMatchPreselectResults()` is true.
10. User confirms selected candidates before chapter/page probing begins.

Do not run page preview loading until after the user confirms candidate matches.

## Chapter Selection

After candidate confirmation:

1. Fetch chapter lists for the origin and selected candidates.
2. Default selected chapter should be:
   - latest read/in-progress chapter of the origin manga if available,
   - otherwise latest origin chapter.
3. Match candidate chapters by `chapterNumber` first.
4. Fall back to normalized chapter name/number extraction if needed.
5. Do not match by list index.
6. If a candidate does not have the selected chapter, show `Chapter unavailable` for that candidate.

User must be able to:

- change chapter before preview,
- change chapter after preview,
- retry preview after changing chapter.

## Page Sample Selection

After chapter selection:

1. Fetch page lists for the selected candidate chapters.
2. Use the configured sample size: `2`, `5`, or `10`.
3. If page count is below sample size, show all available pages.
4. If avoiding first pages and the chapter has enough pages:
   - skip first 1-2 pages,
   - avoid final page where practical,
   - sample from the middle portion of the chapter.

Suggested automatic range:

```text
roughly 30% to 75% through the chapter
```

Preserve actual sampled page indexes per candidate because page counts differ across sources.

## Visual Preview

Preview cards should show:

- source name,
- manga title,
- matched chapter,
- page count,
- sampled page images,
- loading/error state per page or per candidate,
- concise evidence such as `Loaded 5/5 sample pages`.

Limits:

- Only preview selected candidates.
- Use limited concurrency.
- Use timeouts.
- Make preview cancellable.
- Do not download full chapters.
- Do not run automatic preview in the background.
- Store metadata only, not full images, beyond existing image cache behavior.

## Migration / Copy

After the user chooses a best candidate:

1. Show confirmation.
2. Offer migration/copy behavior consistent with existing Komikku migration.
3. Reuse `MigrateMangaUseCase` if possible.
4. If necessary, add a small preparation helper that fetches details and syncs chapters for the selected target before invoking migration.

Do not silently migrate and do not duplicate migration business logic.

## Local Quality Signal

Add a local-only persistence path for future source-quality learning.

Suggested table:

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

- Write a signal after the user chooses a best candidate and confirms migration/copy or explicitly saves the choice.
- Do not feed this into Source Evaluation or For You ranking yet.
- Backup/restore can be deferred unless simple and low-risk.

## Error Handling

Handle these as UI state, not app crashes:

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

Cancellation should remain cancellation. Non-cancellation errors should be isolated to the affected candidate/source where possible.

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
- changes to normal global search limits,
- changes to Browse > For You ranking,
- automatic migration without confirmation.

## Tests

Add unit tests for:

- result cap preference defaults and clamping,
- cap values `1`, `2`, `5`, `10`,
- origin filtered before cap,
- auto-select true selects non-origin candidates,
- auto-select false starts unselected,
- manual deselection preserved while results update,
- chapter default selection from latest read/in-progress,
- chapter fallback to latest origin chapter,
- candidate chapter matching by chapter number,
- page sample helper avoids first pages when possible,
- page sample helper handles tiny chapters,
- sample sizes `2`, `5`, `10`,
- local quality signal insert/query if DB added,
- selected migration target preparation success/failure.

Run at minimum:

```text
:app:testDebugUnitTest --tests "*CrossExtensionMatch*"
:app:testDebugUnitTest --tests "*LovedManga*"
:app:testDebugUnitTest --tests "*Migration*"
:app:testDebugUnitTest
:app:assembleDebug
```

If any full run is skipped, document exactly why.

## Manual QA

Verify:

1. Normal global search remains uncapped.
2. Love/Like/Dislike/Seen/Favorite other versions respect cap `1`, `2`, `5`, `10`.
3. Auto-select setting works both on and off.
4. Find best version opens from manga detail.
5. Candidate search respects recommendation languages/source priority.
6. Wrong candidates can be deselected.
7. Latest read chapter is chosen by default when available.
8. Chapter can be changed before preview.
9. Chapter can be changed after preview.
10. Preview samples avoid first pages when enabled.
11. Sample sizes `2`, `5`, `10` work.
12. A failing source does not crash the screen.
13. A candidate without matching chapter shows a safe unavailable state.
14. Migration/copy uses existing behavior and requires confirmation.

## Documentation After Implementation

Claude must create:

```text
docs/recommendations/KMK_RECS_V0_7_6_BEST_VERSION_VISUAL_QUALITY_MIGRATION_IMPLEMENTATION.md
```

Claude must update:

```text
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/README.md
RECOMMENDATION_VERSIONING.md
```

Implementation report must include:

- summary,
- files changed,
- preferences added,
- DB migrations if any,
- tests added,
- tests run,
- APK filename if built,
- deferred items,
- known risks.

## What's New

If an APK is produced, update local KMK-Recs What's New with only user-facing changes:

- Find best version
- configurable same-manga result cap
- selectable default behavior for same-manga matches
- visual page sample comparison

Do not include markdown/doc updates in What's New.
