# KMK-Recs v0.7.8 Best Version / Chapter Quality Implementation Plan

Date: 2026-06-22

Status: active implementation plan. Do not implement until the user explicitly approves and asks Claude Code to proceed.

## Version Context

`KMK-Recs v0.7.7` was expected to include Best Version / chapter quality work, but the implemented v0.7.7 pass only completed Source Evaluation follow-up fixes:

- reversible `Show installed` / `Hide installed`;
- visible `Recommendation Quality` section;
- on-demand recommendation-quality checks for promising sources;
- clearer recommendation-quality labels.

Therefore, the Best Version / chapter visual quality workflow moves to:

```text
KMK-Recs v0.7.8
```

If an APK is built:

```text
Komikku-v1.13.6-kmk.7.8-debug.apk
```

## Purpose

Add a bounded, user-controlled workflow that helps the user find the best available version of the same manga across installed extensions by visually sampling chapter pages, then migrate or copy to the selected better version using existing Komikku migration behavior.

This is not automatic global source-quality ranking. It is a manual comparison workflow:

```text
Open manga
-> Find best version
-> Search likely same manga across installed sources
-> User confirms which results are actually the same manga
-> User chooses chapter/sample pages
-> App previews sampled pages from selected sources
-> User chooses best version
-> App offers migrate/copy
-> App stores local quality signal for future use
```

## Required Reading Before Coding

Claude must read:

```text
docs/recommendations/README.md
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/DOCUMENTATION_RULES.md
docs/recommendations/CHAPTER_IMAGE_QUALITY_GLOBAL_SEARCH_FEASIBILITY_RESEARCH.md
docs/recommendations/KMK_RECS_V0_7_7_BEST_VERSION_VISUAL_QUALITY_MIGRATION_PLAN.md
docs/recommendations/KMK_RECS_V0_7_8_BEST_VERSION_CHAPTER_QUALITY_IMPLEMENTATION_PLAN.md
RECOMMENDATION_VERSIONING.md
```

Claude must inspect current code before editing:

```text
app/src/main/java/exh/recs/matching/CrossExtensionMatchScreen.kt
app/src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt
app/src/main/java/exh/recs/matching/CrossExtensionMatchQueryPlanner.kt
app/src/main/java/exh/recs/matching/CrossExtensionMatchRouteMode.kt
app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreen.kt
app/src/main/java/eu/kanade/presentation/manga/MangaScreen.kt
app/src/main/java/eu/kanade/presentation/manga/components/MangaInfoHeader.kt
app/src/main/java/mihon/feature/migration/list/MigrationListScreenModel.kt
app/src/main/java/mihon/feature/migration/list/search/SmartSourceSearchEngine.kt
app/src/main/java/mihon/feature/migration/list/search/SourceMatchScorer.kt
app/src/main/java/mihon/domain/migration/usecases/MigrateMangaUseCase.kt
app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt
source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/Source.kt
source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/online/HttpSource.kt
source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/model/Page.kt
app/src/main/java/eu/kanade/tachiyomi/data/download/Downloader.kt
app/src/main/java/eu/kanade/domain/chapter/interactor/SyncChaptersWithSource.kt
app/src/main/java/eu/kanade/domain/manga/interactor/UpdateManga.kt
app/src/main/java/eu/kanade/domain/manga/model/toSManga.kt
app/src/main/java/eu/kanade/domain/manga/interactor/GetPagePreviews.kt
app/src/main/java/eu/kanade/tachiyomi/data/cache/PagePreviewCache.kt
app/src/main/java/eu/kanade/tachiyomi/data/coil/PagePreviewFetcher.kt
```

## Non-Goals

Do not implement:

- automatic global image-quality ranking;
- comparing every installed source without user confirmation;
- non-installed extension quality probing;
- full chapter downloads for comparison;
- OCR;
- translation-quality scoring;
- watermark detection;
- sharpness/compression scoring;
- source-specific scraping rules;
- changes to normal global search result limits;
- changes to For You ranking;
- automatic migration without confirmation;
- background mass comparison.

## Current Foundations

### Cross-Extension Matching

Existing matching already supports:

- searching installed sources;
- recommendation-language filtering;
- source priority ordering;
- alternate title query planning;
- origin filtering;
- user selection/deselection;
- cross-source link group persistence after confirmed match actions.

But currently:

- per-source same-manga result cap is hardcoded at `2`;
- results are always selected by default;
- there is no dedicated Best Version screen.

### Migration

Existing migration already supports:

- searching candidate sources;
- fetching details;
- fetching/syncing chapters;
- scoring candidates by chapter count/latest chapter;
- `MigrateMangaUseCase` for actual migrate/copy behavior.

This feature should reuse migration behavior instead of creating duplicate migration logic.

### Page / Image Access

The source API exposes:

```text
Source.getChapterList(manga)
Source.getPageList(chapter)
HttpSource.getImageUrl(page)
HttpSource.getImage(page)
```

This makes bounded visual sampling feasible, but it must be user-triggered, capped, cancellable, and safe when a source fails.

## Part 1: Same-Manga Matching Settings

Add new preferences to `SourcePreferences`.

### Result Cap

```kotlin
fun sameMangaMatchResultsPerSource() =
    preferenceStore.getInt("same_manga_match_results_per_source", 2)
```

Allowed values:

```text
1, 2, 5, 10
```

Default:

```text
2
```

Behavior:

- Applies only to bounded same-manga workflows.
- Does not affect normal global search.
- Does not affect For You.
- Does not affect normal migration search unless explicitly using same-manga bounded matching.

Apply to:

- Love other versions
- Like other versions
- Dislike other versions
- Seen other versions
- Favorite other versions
- Find best version

Clamp invalid values to `2`.

### Auto-Select Matches

```kotlin
fun sameMangaMatchPreselectResults() =
    preferenceStore.getBoolean("same_manga_match_preselect_results", true)
```

Default:

```text
true
```

Behavior:

- When true, same-manga results start selected.
- When false, same-manga results start unselected.
- Manual deselection/selection must still be preserved while results load.
- Origin manga must never be selected.

### Preview Sample Size

```kotlin
fun bestVersionPreviewSampleSize() =
    preferenceStore.getInt("best_version_preview_sample_size", 5)
```

Allowed values:

```text
2, 5, 10
```

Default:

```text
5
```

Clamp invalid values to `5`.

### Avoid First Pages

```kotlin
fun bestVersionAvoidFirstPages() =
    preferenceStore.getBoolean("best_version_avoid_first_pages", true)
```

Default:

```text
true
```

Reason:

Some sources prepend ads, credits, covers, or promotional pages. Automatic samples should avoid the first pages when the chapter has enough pages.

## Part 2: Settings UI

Add a clear section in Recommendation Settings:

```text
Same manga matching
```

Controls:

- `Same manga results per source`: `1`, `2`, `5`, `10`
- `Select matches by default`: switch
- `Best version preview pages`: `2`, `5`, `10`
- `Avoid first pages in preview`: switch

UX note:

Make clear that result cap applies only to bounded same-manga workflows, not normal global search.

## Part 3: Extract Shared Same-Manga Candidate Search

Do not duplicate search logic separately for every workflow.

Create a reusable helper/service such as:

```text
app/src/main/java/exh/recs/matching/SameMangaCandidateSearcher.kt
app/src/main/java/exh/recs/matching/SameMangaMatchSettings.kt
app/src/main/java/exh/recs/matching/SameMangaCandidateResult.kt
```

Responsibilities:

- load matching installed sources;
- respect recommendation languages;
- respect source priority order;
- use `CrossExtensionMatchQueryPlanner.buildQueries(originManga)`;
- apply configured per-source cap;
- filter origin before cap;
- merge multi-query results by `(source, url)`;
- return per-source loading/success/error results;
- never affect normal global search.

Then update existing `CrossExtensionMatchScreenModel` to use this helper where practical.

## Part 4: Best Version Entry Point

Add a manga-detail action:

```text
Find best version
```

Possible placement:

- manga detail overflow/action menu;
- near existing `Love other versions`, `Seen other versions`, `Favorite other versions`;
- do not clutter primary reading actions.

On click:

```kotlin
BestVersionCompareScreen(originMangaId)
```

Use primitive Voyager route args only:

```kotlin
class BestVersionCompareScreen(
    private val originMangaId: Long,
) : Screen
```

Do not store:

- source objects;
- manga objects;
- chapter objects;
- page objects;
- sealed mode objects;
- large lists;
- lambdas

in the route constructor.

## Part 5: Best Version Screen Flow

Create:

```text
app/src/main/java/exh/recs/bestversion/BestVersionCompareScreen.kt
app/src/main/java/exh/recs/bestversion/BestVersionCompareScreenModel.kt
```

Suggested states:

```kotlin
sealed interface BestVersionStep {
    data object LoadingOrigin : BestVersionStep
    data object SearchingCandidates : BestVersionStep
    data object ConfirmCandidates : BestVersionStep
    data object LoadingChapters : BestVersionStep
    data object SelectChapter : BestVersionStep
    data object LoadingPreview : BestVersionStep
    data object ComparePreview : BestVersionStep
    data object PreparingMigration : BestVersionStep
    data object Error : BestVersionStep
}
```

Flow:

1. Load origin manga.
2. Search same-manga candidates.
3. User confirms/deselects candidates.
4. Fetch chapter lists for origin and candidates.
5. Select default chapter.
6. User may change chapter.
7. Fetch page lists for matching candidate chapters.
8. Choose sample pages.
9. Load visual previews.
10. User chooses best candidate.
11. Show migrate/copy confirmation.
12. Reuse migration behavior.

## Part 6: Candidate Confirmation

Candidate search must be user-confirmed before page/chapter probing.

Requirements:

- Results selected by default only if `sameMangaMatchPreselectResults()` is true.
- User can deselect wrong matches.
- Candidate list should show source name, title, thumbnail if already available, and enough metadata to identify obvious wrong matches.
- Do not run chapter/page preview for unselected candidates.
- Candidate cap uses `sameMangaMatchResultsPerSource()`.

## Part 7: Chapter Selection

After candidates are confirmed:

1. Fetch origin chapter list.
2. Fetch selected candidate chapter lists.
3. Determine default chapter.

Default priority:

1. Latest read/in-progress chapter of origin manga if available.
2. Latest downloaded/read origin chapter if available.
3. Latest origin chapter.

Candidate chapter matching:

- Prefer `chapterNumber`.
- Fallback to normalized chapter title/number extraction.
- Never match only by list index.

If a candidate lacks the selected chapter:

- show `Chapter unavailable`;
- do not crash;
- do not remove candidate automatically.

User must be able to:

- change chapter before preview;
- change chapter after preview;
- rerun preview after changing chapter.

## Part 8: Page Sample Selection

After chapter matching:

1. Fetch `getPageList()` for selected candidate chapters.
2. Choose sample pages from the page list.
3. Load preview images for only those pages.

Automatic sample algorithm:

```text
sampleSize = bestVersionPreviewSampleSize()
avoidFirstPages = bestVersionAvoidFirstPages()
```

Suggested behavior:

- If page count <= sample size, use all pages.
- If avoid-first-pages is true and enough pages exist, skip first 1-2 pages.
- Avoid final page when practical.
- Sample roughly from 30% to 75% of the chapter.
- Preserve actual sampled indexes per candidate because page counts differ.

Example:

```text
30 pages, sample size 5 -> sample around pages 9, 13, 17, 21, 24
```

User controls:

- change sample size;
- move sample earlier/later;
- change chapter;
- retry failed previews.

## Part 9: Visual Preview UI

Preview cards should show:

- source name;
- candidate manga title;
- matched chapter;
- page count;
- sampled page previews;
- loading/error state;
- simple evidence such as `Loaded 5/5 sample pages`.

Limits:

- selected candidates only;
- limited concurrency;
- per-source/per-page timeout;
- cancellable;
- do not download full chapters;
- do not automatically run in background;
- store metadata only, not full images, beyond normal image cache behavior.

Errors:

- page list failed;
- image URL failed;
- image load failed;
- timeout;
- chapter unavailable;
- source uninstalled while comparing;
- network disconnected.

Each error should appear per candidate/source where possible.

## Part 10: Migrate / Copy

After user selects a best candidate:

1. Show confirmation.
2. Offer migrate/copy consistent with existing migration behavior.
3. Prepare selected target:
   - ensure local manga exists;
   - fetch details if needed;
   - fetch/sync chapters.
4. Call existing migration behavior, preferably `MigrateMangaUseCase`.

Do not:

- silently migrate;
- duplicate migration business logic;
- skip existing migration flags/categories/tracking behavior if existing migration handles them.

If direct use of existing migration screen is easier:

- route to migration with chosen target preselected;
- or use `MigrationListScreenModel.useMangaForMigration(...)` behavior as reference.

## Part 11: Local Quality Signal

Add local-only persistence for user-confirmed source quality choices.

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

- write after user selects best candidate and confirms migrate/copy, or explicitly saves choice;
- do not feed this into Source Evaluation scoring yet;
- do not feed it into For You ranking yet;
- backup/restore can be deferred unless simple and low-risk.

Create domain/data layers if adding SQL:

```text
tachiyomi.domain.taste.model.MangaSourceQualitySignal
tachiyomi.domain.taste.repository.MangaSourceQualitySignalRepository
tachiyomi.domain.taste.interactor.UpsertMangaSourceQualitySignal
tachiyomi.domain.taste.interactor.GetMangaSourceQualitySignals
tachiyomi.data.taste.MangaSourceQualitySignalRepositoryImpl
```

Register in `KMKDomainModule`.

## Part 12: Exception Handling

Every network-heavy stage must fail safely:

- origin manga missing;
- source search failure;
- details fetch failure;
- chapter list failure;
- no matching chapter;
- page list failure;
- image URL/image load failure;
- timeout;
- network loss;
- candidate source uninstalled mid-flow;
- selected target missing;
- selected target has no chapters;
- migration preparation failure.

Rules:

- cancellation remains cancellation;
- non-cancellation errors become UI state;
- one bad candidate should not kill the whole flow;
- all-candidate failure should show retry/back state;
- no app crash.

## Part 13: Tests

Add pure/unit tests where possible.

### Same-Manga Settings / Search

- default cap is 2;
- valid caps 1/2/5/10 are respected;
- invalid cap clamps to 2;
- auto-select true selects non-origin candidates;
- auto-select false starts unselected;
- origin is filtered before cap;
- normal global search remains uncapped.

### Chapter Matching

- defaults to latest read/in-progress chapter;
- falls back to latest origin chapter;
- matches candidate chapter by `chapterNumber`;
- falls back to normalized chapter name;
- returns unavailable if no match.

### Page Sample Helper

- sample size 2/5/10 respected;
- tiny chapter returns all pages;
- avoid-first-pages skips first pages when possible;
- avoid-first-pages does not break tiny chapters;
- final page avoided when possible;
- indexes are stable/in range.

### Quality Signal

If DB added:

- insert signal;
- query by origin;
- query by selected source;
- handles compared candidates JSON;
- migration applies cleanly.

### Screen Model / Flow

Where practical:

- candidate confirmation gates preview;
- unselected candidates are not previewed;
- source error becomes candidate error state;
- no chapter match shows unavailable;
- selected target with chapters can prepare migration;
- selected target with no chapters returns safe error.

Run:

```text
./gradlew :app:testDebugUnitTest --tests "*CrossExtensionMatch*"
./gradlew :app:testDebugUnitTest --tests "*BestVersion*"
./gradlew :app:testDebugUnitTest --tests "*Migration*"
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

If any command cannot be run, document why.

## Part 14: Manual QA

Verify:

1. Normal global search remains uncapped.
2. Love/Like/Dislike/Seen/Favorite other versions respect cap setting.
3. Auto-select setting works on/off.
4. `Find best version` appears on manga detail.
5. Candidate search respects languages/source priority.
6. Wrong candidates can be deselected.
7. Preview does not start until candidates are confirmed.
8. Latest read chapter is selected by default when available.
9. Chapter can be changed before preview.
10. Chapter can be changed after preview.
11. Samples avoid first pages when enabled.
12. Sample sizes 2/5/10 work.
13. Candidate with no matching chapter shows safe unavailable state.
14. Failed image/page load does not crash.
15. User can choose best candidate.
16. Migrate/copy requires confirmation.
17. Library state after migration/copy matches normal Komikku migration expectations.

## Documentation After Implementation

Claude must create:

```text
docs/recommendations/KMK_RECS_V0_7_8_BEST_VERSION_CHAPTER_QUALITY_IMPLEMENTATION.md
```

Update:

```text
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/README.md
RECOMMENDATION_VERSIONING.md
app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt
```

Implementation report must include:

- what was implemented;
- what was deferred;
- files changed;
- preferences added;
- migrations added, if any;
- tests added;
- tests run;
- APK filename/path if built;
- known risks.

## What's New

If an APK is produced, include only user-facing changes:

- added `Find best version`;
- compare sampled pages across same-manga sources;
- choose chapter/sample pages before preview;
- migrate or copy to the selected better version;
- configurable same-manga result caps;
- configurable default match selection.

Do not include documentation/test/internal migration details in What's New.

## Summary

v0.7.8 should finally implement the Best Version / chapter quality workflow that was planned but not included in v0.7.7.

The implementation should be:

- installed-source only;
- user-confirmed;
- bounded;
- preview-based;
- migration-safe;
- resilient to bad source behavior.
