# KMK-Recs v0.5.5 Best Version / Visual Quality Migration Plan

Date: 2026-06-22

Status: implementation plan. Do not implement until the user explicitly approves and asks Claude Code to proceed.

## Version Placement

Recommended feature version:

```text
KMK-Recs v0.5.5
```

Reason: this work extends the cross-extension same-manga workflow that began in the v0.5 line. It should stay in the v0.5.x family even though current overall documentation is at v0.7.5.

Recommended implementation order:

1. Finish `KMK_RECS_V0_6_22_SOURCE_EVALUATION_CONTINUATION_AND_RECOMMENDATION_QUALITY_PLAN.md`.
2. Then implement this v0.5.5 best-version workflow.

Reason: v0.6.22 changes source evaluation and recommendation-quality probing. This v0.5.5 plan should not depend on partially implemented source-evaluation changes, and it should not mix source-evaluation bugs into the new migration/quality workflow.

## Purpose

Add a user-controlled workflow that helps the user find the best available version of the same manga across installed extensions, visually compare sampled pages, and optionally migrate/copy from the current manga to the selected better-quality version.

The goal is not automatic global image-quality ranking. The goal is a bounded, manual, evidence-based flow:

```text
Open current manga
-> Find best version
-> Search likely same manga across sources
-> User confirms candidate matches
-> User selects chapter/sample pages
-> App shows visual samples from candidates
-> User chooses the best version
-> App opens/uses existing migration behavior
-> App stores a local source-quality signal for future use
```

## User Requirements Captured

- The feature should help identify which source has better chapter/page quality for the same manga.
- It must not blindly trust search results because some extensions return many wrong matches.
- Candidate result count per source should be configurable: `1`, `2`, `5`, or `10`.
- The same per-source match cap setting should apply to existing "other versions" flows: Love, Like, Dislike, Seen, Favorite, and Best Version.
- Results are currently selected by default in cross-extension matching. This should become a reversible setting.
- Default behavior should preserve the current UX: results selected by default, user can deselect wrong matches.
- Users should be able to change the setting so results are not selected by default.
- The user should select the chapter before visual preview.
- The system should default to the latest read chapter of the current manga when possible.
- The user must be able to change the chapter/page sample before previewing.
- The user must also be able to change the chapter/page sample after previewing, then re-run the preview.
- Do not use only the first pages by default because many sources prepend ads/promotional pages.
- The preview should support a small sample size, such as 2, 5, or 10 pages.
- After selecting the best version, the app should prompt with the normal migration/copy behavior instead of silently changing the library.
- Store a latent local signal about which source tended to have better visual quality for future possible use.

## Current Code Foundations

### Cross-Extension Matching

Relevant files:

```text
app/src/main/java/exh/recs/matching/CrossExtensionMatchScreen.kt
app/src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt
app/src/main/java/exh/recs/matching/CrossExtensionMatchQueryPlanner.kt
app/src/main/java/exh/recs/matching/CrossExtensionMatchRouteMode.kt
```

Current behavior:

- `CrossExtensionMatchScreenModel.PER_SOURCE_RESULT_LIMIT = 2` is hardcoded.
- Normal global search remains uncapped.
- Matching searches recommendation-language-filtered catalogue sources.
- Matching uses source priority order.
- Origin manga is filtered out.
- Candidates are selected by default unless manually deselected.
- Confirming match actions writes ratings/seen/favorite state and cross-source link groups.

Required change:

- Replace the hardcoded `PER_SOURCE_RESULT_LIMIT` with a preference-backed setting.
- Replace hardcoded auto-selection behavior with a preference-backed setting.
- Keep normal global search unchanged and uncapped.

### Migration

Relevant files:

```text
app/src/main/java/mihon/feature/migration/list/MigrationListScreenModel.kt
app/src/main/java/mihon/feature/migration/list/search/SmartSourceSearchEngine.kt
app/src/main/java/mihon/feature/migration/list/search/SourceMatchScorer.kt
app/src/main/java/mihon/domain/migration/usecases/MigrateMangaUseCase.kt
```

Current behavior:

- Migration can search candidate sources.
- It can fetch manga details.
- It can fetch and sync chapter lists.
- It already compares chapter counts/latest chapter through `SourceMatchScorer`.
- `MigrationListScreenModel.useMangaForMigration(...)` can set a manually selected target.
- `migrateMangas()`, `copyMangas()`, and `migrateNow(...)` reuse `MigrateMangaUseCase`.

Required change:

- Reuse migration behavior for the final migrate/copy step.
- Do not create a parallel migration implementation if the existing one can be reused.
- If direct reuse of `MigrationListScreenModel` is awkward, extract a small helper around target validation/chapter sync and still call `MigrateMangaUseCase`.

### Source API / Page Fetching

Relevant source API methods:

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
app/src/main/java/eu/kanade/tachiyomi/data/download/Downloader.kt
app/src/main/java/eu/kanade/domain/manga/interactor/GetPagePreviews.kt
app/src/main/java/eu/kanade/tachiyomi/data/cache/PagePreviewCache.kt
app/src/main/java/eu/kanade/tachiyomi/data/coil/PagePreviewFetcher.kt
```

Meaning:

- Chapter list comparison is feasible.
- Page-list sampling is feasible if bounded.
- Image preview is feasible only if user-initiated and tightly capped.
- The app should not fetch full chapters or mass-sample all sources.

Reference research:

```text
docs/recommendations/CHAPTER_IMAGE_QUALITY_GLOBAL_SEARCH_FEASIBILITY_RESEARCH.md
```

## New Preferences

Add the following preferences to `SourcePreferences`.

### Same Manga Match Result Cap

Preference:

```kotlin
fun sameMangaMatchResultsPerSource() = preferenceStore.getInt("same_manga_match_results_per_source", 2)
```

Allowed values:

```text
1, 2, 5, 10
```

Default:

```text
2
```

Apply to:

- Love other versions
- Like other versions
- Dislike other versions
- Seen other versions
- Favorite other versions
- Best version / quality compare candidate search

Do not apply to:

- normal global search
- normal migration search
- Browse > For You source rows
- Top Picks

Validation:

- Clamp unknown values back to `2`.
- Unit-test clamping and behavior.

### Same Manga Auto-Select Results

Preference:

```kotlin
fun sameMangaMatchPreselectResults() = preferenceStore.getBoolean("same_manga_match_preselect_results", true)
```

Default:

```text
true
```

Behavior:

- When true, same-manga match results are selected by default, matching current behavior.
- When false, results are shown unselected and the user must manually select correct candidates.
- Manual selection/deselection must still be preserved while results update.
- Origin manga must never be selected, regardless of this setting.

Apply to:

- Love/Like/Dislike other versions
- Seen other versions
- Favorite other versions
- Best version candidate confirmation

### Quality Preview Sample Size

Preference:

```kotlin
fun bestVersionPreviewSampleSize() = preferenceStore.getInt("best_version_preview_sample_size", 5)
```

Allowed values:

```text
2, 5, 10
```

Default:

```text
5
```

Behavior:

- Determines how many pages are sampled per candidate for visual preview.
- The preview should not fetch more than this number of pages per candidate.
- If a chapter has fewer usable pages, show fewer pages and explain that only N pages were available.

### Avoid First Pages By Default

Preference:

```kotlin
fun bestVersionAvoidFirstPages() = preferenceStore.getBoolean("best_version_avoid_first_pages", true)
```

Default:

```text
true
```

Behavior:

- If enabled, the automatic sample window should avoid the first 1-2 pages when the chapter has enough pages.
- Also avoid the final page where practical.
- User can still manually choose a different sample window.

## Settings UI

Add settings in Recommendation Settings or a nested "Same manga matching" section.

Suggested placement:

- Recommendation Settings
- Near existing rating/matching/source controls, not inside Source Evaluation.

Controls:

1. `Same manga results per source`
   - Segmented/list preference: `1`, `2`, `5`, `10`
   - Summary: used only for bounded same-manga workflows, not normal global search.
2. `Select matches by default`
   - Switch
   - Summary: when enabled, possible same-manga matches start selected so the user can deselect wrong entries.
3. `Best version preview pages`
   - Segmented/list preference: `2`, `5`, `10`
4. `Avoid first pages in preview`
   - Switch
   - Summary: skips likely ad/promo pages when choosing automatic samples.

Do not crowd the Source Evaluation screen with these unless there is already a matching-related settings group there.

## Candidate Search Flow

Entry point:

- Manga detail page action: `Find best version` or `Compare versions`.

Recommended label:

```text
Find best version
```

Avoid labels that overpromise:

- `Find perfect source`
- `Automatically find best quality`

Flow:

1. User opens a manga detail page.
2. User taps `Find best version`.
3. App opens a new flow using the current manga as origin.
4. App runs same-manga search across installed recommendation-language-filtered sources.
5. Search uses `CrossExtensionMatchQueryPlanner.buildQueries(originManga)`.
6. Per-source result limit comes from `sameMangaMatchResultsPerSource()`.
7. Origin manga is filtered out before applying the cap.
8. Results are selected by default only if `sameMangaMatchPreselectResults()` is true.
9. User confirms the likely same-manga candidates.

Implementation options:

### Option A: Extend CrossExtensionMatchScreen

Add a new mode:

```kotlin
CrossExtensionMatchMode.FindBestVersion
```

Pros:

- Reuses current search UI.
- Reuses source ordering, query planning, selection model.

Cons:

- Best-version flow is multi-step and more complex than "apply one action".
- Could make `CrossExtensionMatchScreenModel.applyRating()` more overloaded.

### Option B: Extract Search Helper And Create New Screen

Extract reusable search logic from `CrossExtensionMatchScreenModel` into a helper/service:

```text
SameMangaCandidateSearcher
SameMangaMatchSettings
SameMangaCandidateResult
```

Then create a dedicated flow:

```text
BestVersionCompareScreen
BestVersionCompareScreenModel
```

Pros:

- Cleaner architecture.
- Easier to add chapter/page preview state.
- Avoids making cross-extension rating screen too broad.

Cons:

- More files.

Recommendation:

```text
Use Option B if implementation time allows.
```

The current matching screen is simple and action-focused. Best-version comparison needs candidate search, chapter list loading, chapter selection, page selection, preview loading, quality signal storage, and migration/copy. A dedicated screen model is safer.

## Chapter Selection

After candidates are confirmed, fetch chapter lists for:

- origin manga,
- each selected candidate.

Default chapter selection priority:

1. Latest read/in-progress chapter from the origin manga.
2. If no read progress exists, latest downloaded/read chapter from local state if available.
3. If no reading state exists, latest chapter from the origin manga.
4. If origin chapter list cannot load, show a chapter-selection error and allow retry.

Matching candidates to the selected chapter:

- Prefer matching by `chapterNumber`.
- Then fallback to normalized chapter name/number extraction.
- Do not match only by list index because different sources can add ad chapters, specials, or reorder chapters.

UI:

- Show selected chapter near the top before preview.
- Provide `Change chapter`.
- After preview, keep `Change chapter` available and allow re-running preview.

Failure handling:

- If a candidate does not have the selected chapter, show `Chapter unavailable` for that candidate.
- Do not remove the candidate automatically.
- Let the user choose another chapter/sample.

## Page Sample Selection

After a chapter is selected, fetch page lists for the selected chapter from each candidate.

Default sample strategy:

```text
sampleSize = preference bestVersionPreviewSampleSize()
avoidFirstPages = preference bestVersionAvoidFirstPages()
usable range = middle-ish portion of chapter
```

Suggested algorithm:

1. Let page count be `N`.
2. If `N <= sampleSize`, use all pages.
3. If `avoidFirstPages == true` and `N >= sampleSize + 3`, exclude first 2 pages and last 1 page from automatic sampling.
4. Choose evenly spaced page indexes across roughly 30% to 75% of the remaining chapter.
5. Preserve actual page indexes per candidate because page counts can differ.

Example:

```text
N = 30, sampleSize = 5
sample indexes around pages 9, 13, 17, 21, 24
```

User controls:

- `Change sample`
- choose sample size from setting or inline menu
- move sample earlier/later
- optionally choose exact pages if simple enough

Do not use first pages as the default sample when avoid-first-pages is on.

## Visual Preview

Preview UI should show candidate source cards/sections with the sampled pages.

Each candidate should show:

- source name,
- manga title,
- chapter matched,
- page count for selected chapter,
- sampled page previews,
- loading/error per sampled page,
- quick evidence summary.

Evidence examples:

```text
Loaded 5/5 sample pages
Chapter 42 matched by chapter number
Pages: 34
Image sample loaded successfully
```

Error examples:

```text
Could not load page list
Image host rejected sample
Selected chapter not found
Timed out loading sample
```

Performance limits:

- Only load previews for selected candidates.
- Maximum candidates should naturally be bounded by the match cap and source selection, but add a hard guard if needed.
- Use limited concurrency, e.g. 2-3 sources at a time for image previews.
- Use per-source/per-page timeout.
- Make preview cancellable.
- Do not fetch full chapters.
- Do not run in background automatically.

Image loading:

- Prefer existing Coil/image loading patterns where possible.
- Avoid manually downloading full image bytes unless needed.
- If image metadata is collected, store metadata only, not image files.

## Migration / Copy

After preview, user chooses one candidate as the best version.

Then:

1. Show confirmation: migrate or copy.
2. Reuse existing migration flags/behavior where possible.
3. Sync/fetch chapters for target before migration.
4. Call existing migration code/use case rather than duplicating data-moving logic.

Preferred implementation:

- Use `MigrateMangaUseCase(current, target, replace = true/false)`.
- If needed, add a small interactor:

```text
PrepareBestVersionMigrationTarget
```

Responsibilities:

- ensure target manga exists locally,
- fetch details,
- fetch/sync chapters,
- return success/error.

Do not:

- silently migrate without confirmation,
- change tracking/categories manually if existing migration handles it,
- create separate one-off migration logic that diverges from normal migration.

## Local Source Quality Signal

Add a local-only table to store user-confirmed quality choices for future use.

Suggested table:

```sql
manga_source_quality_signal
```

Suggested fields:

```text
id INTEGER PRIMARY KEY
origin_source_id INTEGER NOT NULL
origin_url TEXT NOT NULL
origin_title TEXT NOT NULL
selected_source_id INTEGER NOT NULL
selected_url TEXT NOT NULL
selected_title TEXT NOT NULL
selected_source_name TEXT
compared_candidates_json TEXT NOT NULL
chapter_number REAL
chapter_name TEXT
sample_size INTEGER NOT NULL
sampled_pages_json TEXT NOT NULL
selected_at INTEGER NOT NULL
quality_signal_version INTEGER NOT NULL
```

Purpose:

- preserve evidence that the user picked source X over source Y for the same manga,
- support future source-quality learning,
- support future UI showing "you previously preferred this source for quality."

Initial usage:

- Write the signal after the user confirms migration/copy or explicitly chooses the best candidate.
- Do not automatically change source evaluation scores in this first implementation.
- Do not show source ranking based on this signal yet unless trivial.

Backup/restore:

- Optional. If implementation becomes too large, defer backup/restore for this table.
- If implemented, reserve a proto number and test round-trip serialization.

## Relationship To Source Evaluation

This feature is separate from Source Evaluation.

Source Evaluation asks:

```text
Is this source generally useful for recommendations?
```

Best Version asks:

```text
For this exact manga/chapter, which installed source looks best to the user?
```

Do not mix the two in the first implementation.

Future bridge:

- After enough `manga_source_quality_signal` rows exist, a future source-quality learner could use them as weak local evidence.

## Route / Voyager State Safety

Follow the v0.7.1 route-safe pattern.

Do not store these directly in a Voyager screen constructor:

- sealed mode objects,
- source objects,
- manga objects,
- page objects,
- chapter objects,
- lambdas,
- large candidate lists.

Screen constructor should store only primitive route args, for example:

```kotlin
BestVersionCompareScreen(originMangaId: Long)
```

All heavier state should live in the screen model and be loaded by ID.

This avoids the `BadParcelableException` / `NotSerializableException` class of bugs already fixed in cross-extension matching.

## Exception Handling

Every network-heavy stage must fail per candidate/source, not crash the app:

- source search failure,
- manga details failure,
- chapter list failure,
- chapter matching failure,
- page list failure,
- image URL failure,
- image decode/load failure,
- network disconnect,
- timeout,
- candidate source uninstalled during flow,
- origin manga missing,
- selected target missing,
- migration target has no chapters.

Rules:

- Cancellation should still cancel normally.
- Non-cancellation exceptions should become UI state.
- A bad candidate should not kill the whole flow.
- A bad source should show an error row/card.
- If all candidates fail, show a clear retry/back state.

## Non-Goals

Do not implement in this pass:

- automatic global source/image-quality ranking,
- mass sampling of many manga,
- sampling non-installed extensions,
- OCR or translation quality detection,
- watermark detection,
- sharpness/compression analysis,
- source-specific scraping rules,
- changing normal global search limits,
- changing Browse > For You ranking,
- automatic migration without confirmation,
- using first pages as default sample when avoid-first-pages is enabled.

## User-Facing Strings

Add strings in `i18n-kmk`.

Suggested strings:

```text
Find best version
Compare versions
Select matching versions
Selected %1$d of %2$d
Change chapter
Preview sample
Change sample
Sample pages
Avoid first pages
Same manga results per source
Select matches by default
Best version preview pages
Chapter unavailable
Could not load page list
Could not load sample
Use this version
Migrate to this version
Copy to this version
Quality signal saved
```

Keep language user-facing, not developer-facing.

## Tests To Add

### Matching Settings Tests

Add/update tests near existing cross-extension matching tests:

- default per-source cap is 2,
- allowed values 1/2/5/10 are respected,
- invalid values clamp to 2,
- origin manga is filtered before cap,
- auto-select enabled selects non-origin candidates,
- auto-select disabled leaves candidates unselected,
- manual deselection remains preserved while results update.

### Page Sample Tests

Add pure helper tests:

- low page count returns all usable pages,
- sample size 2/5/10 respected,
- avoid-first-pages skips first pages when possible,
- avoid-first-pages does not break tiny chapters,
- sample indexes are stable and in range,
- last page avoided when possible.

### Chapter Matching Tests

Add pure helper tests:

- defaults to latest read chapter when available,
- falls back to latest origin chapter,
- matches candidate chapter by chapter number,
- falls back to normalized chapter name,
- reports unavailable when no match.

### Quality Signal Tests

If a DB table/interactor is added:

- insert signal,
- query by origin,
- query by selected source,
- handles compared-candidate JSON,
- migration applies cleanly on existing DB.

### Migration Preparation Tests

Where practical:

- selected candidate with chapters can proceed,
- selected candidate with no chapters returns a safe error,
- source failure returns safe error,
- no silent migration occurs before confirmation.

### Regression Tests

Run:

```text
:app:testDebugUnitTest --tests "*CrossExtensionMatch*"
:app:testDebugUnitTest --tests "*LovedManga*"
:app:testDebugUnitTest --tests "*Migration*"
:app:testDebugUnitTest
:app:assembleDebug
```

If the full test suite is too slow, still run all new tests plus cross-extension tests and document what was skipped.

## Manual Testing Checklist

1. Open a manga and run Love other versions with cap 1, 2, 5, and 10.
2. Confirm normal global search remains uncapped.
3. Toggle `Select matches by default` off; open Love other versions; verify candidates are unselected.
4. Toggle it on; verify current default selected behavior returns.
5. Open `Find best version` from a library manga.
6. Confirm same-manga candidates are bounded by the configured cap.
7. Deselect wrong candidates and continue.
8. Confirm default chapter is latest read/in-progress when available.
9. Change chapter before preview.
10. Preview 2, 5, and 10 page samples.
11. Verify automatic sample does not use first pages when avoid-first-pages is enabled and chapter has enough pages.
12. Change sample after preview and refresh.
13. Pick a candidate and migrate/copy.
14. Verify library data behaves like normal migration/copy.
15. Verify app does not crash when one source fails chapter list or page list.
16. Verify app does not crash when internet disconnects mid-preview.

## Documentation Updates After Implementation

Claude must update:

```text
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/README.md
RECOMMENDATION_VERSIONING.md
```

Claude must also create an implementation report:

```text
docs/recommendations/KMK_RECS_V0_5_5_BEST_VERSION_VISUAL_QUALITY_MIGRATION_IMPLEMENTATION.md
```

The implementation report must include:

- what was implemented,
- what was intentionally deferred,
- files changed,
- database migrations if any,
- preferences added,
- tests added,
- tests run,
- APK name if built,
- known risks.

## What's New

If an APK is produced, update local KMK-Recs What's New with user-facing changes only.

Do include:

- `Find best version`
- configurable same-manga result cap
- selectable default behavior for same-manga matches
- visual page sample comparison

Do not include:

- internal markdown/doc updates,
- test names,
- database table names,
- implementation details.

## Summary Recommendation

This feature is feasible if it stays manual, bounded, and user-confirmed.

Recommended first implementation:

1. Add same-manga match settings.
2. Apply those settings to existing other-version workflows.
3. Add a dedicated `Find best version` flow.
4. Reuse cross-extension candidate search.
5. User confirms candidates.
6. Fetch chapter lists and default to latest read chapter.
7. Fetch bounded page samples away from first pages.
8. Show visual comparison.
9. Reuse existing migration/copy behavior.
10. Store a local quality signal for future use.

Do not attempt fully automatic quality scoring across all sources in this pass.
