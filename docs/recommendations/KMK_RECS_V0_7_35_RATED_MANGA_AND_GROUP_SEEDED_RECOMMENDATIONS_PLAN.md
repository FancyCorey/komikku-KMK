# KMK-Recs v0.7.35 Rated Manga And Group-Seeded Recommendations Plan

Date: 2026-07-08

Status: implementation plan. Do not implement until explicitly approved by the user.

## User-Approved Direction

The user wants to keep the current distinction between ratings and neutral exclusions:

- `Dislike` means "I do not want manga like this" and should continue to affect recommendation scoring.
- `Seen` / `Mark as seen` means "do not show this specific manga again" and should remain neutral.
- `Seen other versions` should remain the way to neutrally hide confirmed alternate versions across sources.

Because the existing `Seen` flow already covers the proposed title-specific dislike use case, this plan must not add a new title-specific dislike state, table, preference, backup field, or UI action.

The approved new work is:

1. Generalize the current Loved Manga view into a broader rated-manga framework that can show Loved, Liked, and Disliked manga.
2. Preserve the existing Loved Manga behavior and heart entry point.
3. Add separate quick entry points for Liked and Disliked manga using clear icons, instead of hiding all three categories behind the heart icon.
4. Add a way to generate recommendations from an already-approved/grouped manga, especially Loved or Liked manga.
5. Reuse the existing cross-source grouping/link infrastructure instead of creating another grouping system.

## Current Code Facts To Preserve

### Taste Profile Semantics

`domain/src/main/java/tachiyomi/domain/taste/interactor/GetTasteProfile.kt`

Current behavior:

- `MangaRating.LOVE` contributes `+2.0`.
- `MangaRating.LIKE` contributes `+1.0`.
- `MangaRating.DISLIKE` contributes `-2.0`.
- Source affinity is also influenced by positive vs negative ratings.

This means `DISLIKE` is already a broad recommendation signal. Do not weaken or repurpose it.

### Seen Semantics

Relevant files:

- `app/src/main/java/exh/recs/SeenRecommendationMangaStore.kt`
- `app/src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt`
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt`
- `app/src/test/java/exh/recs/SeenRecommendationMangaStoreTest.kt`

Current behavior:

- Seen manga are stored as `SeenMangaKey(sourceId, url)` in `SourcePreferences.seenRecommendationMangaKeys()`.
- `CrossExtensionMatchMode.MarkSeen` writes seen keys only.
- MarkSeen does not write `manga_taste` rows.
- MarkSeen does not change learned tag weights.
- For You always filters seen manga from fresh and cached results.
- Seen keys are backed up/restored through proto 626 as of v0.7.28.

Preserve this behavior. If the implementation touches this area, add regression tests proving seen manga do not affect `GetTasteProfile`.

### Loved Manga Existing Architecture

Relevant files:

- `app/src/main/java/exh/recs/loved/LovedMangaScreen.kt`
- `app/src/main/java/exh/recs/loved/LovedMangaScreenModel.kt`
- `app/src/main/java/exh/recs/loved/LovedMangaDuplicateGrouper.kt`
- `app/src/main/java/exh/recs/loved/LovedMangaSourceFilter.kt`
- `app/src/test/java/exh/recs/loved/*`

Current behavior:

- Loved Manga subscribes to all manga taste rows reactively.
- It filters to `MangaRating.LOVE`.
- It filters out entries from sources that are not currently installed.
- It loads local manga rows by `mangaId`, falling back to `(url, source)`.
- It groups duplicates display-only.
- Confirmed `manga_cross_source_link` group IDs are the highest-confidence grouping signal.
- Metadata fallback grouping is conservative and display-only.
- No taste rows are deleted or merged by duplicate grouping.
- The screen supports sorting, grouping toggle, link group management, and JSON export.

The new Rated Manga work should reuse this architecture rather than introducing a parallel implementation.

### Cross-Source Link Infrastructure

Relevant files:

- `domain/src/main/java/tachiyomi/domain/taste/model/CrossSourceMangaLink.kt`
- `domain/src/main/java/tachiyomi/domain/taste/interactor/GetCrossSourceMangaLinks.kt`
- `domain/src/main/java/tachiyomi/domain/taste/interactor/UpsertCrossSourceMangaLinks.kt`
- `domain/src/main/java/tachiyomi/domain/taste/interactor/DeleteCrossSourceMangaLink.kt`
- `data/src/main/sqldelight/tachiyomi/data/manga_cross_source_link.sq`
- migration 50

Current behavior:

- Confirmed cross-extension match actions create link groups.
- Loved Manga uses these link groups as tier 1 grouping evidence.
- Link groups are backup/sync aware per current docs.

The group-seeded recommendation feature must reuse this data.

## Non-Goals

Do not implement:

- A new title-specific dislike state.
- A new duplicate grouping table.
- A new same-manga identity engine.
- A tracker/AniList/MAL-based recommendation system.
- Automatic grouping without user-confirmed evidence beyond the existing conservative Loved Manga grouping helper.
- Any migration or backup proto field unless absolutely necessary. This plan should not require one.
- Any change to normal global search behavior.
- Any change to existing `Dislike`, `Seen`, or `Seen other versions` semantics.
## Prerequisite Follow-Up: Fix Recommendation Quality Probe Threading

The user is repeatedly seeing Source Evaluation recommendation-quality rows fail with:

```text
android.os.NetworkOnMainThreadException
Plan TOP_TAGS_FILTER: error
Plan TAG_PAIR: error
```

This appears in the Source Evaluation screen under `Recommendations: Error` for many otherwise promising sources.

### Likely Cause

`app/src/main/java/exh/recs/evaluation/SourceRecommendationFitProbe.kt` performs extension/source work directly:

- `source.getFilterList()`
- `source.getSearchManga(...)`
- `source.getMangaDetails(...)`

The on-demand recommendation-quality path in `SourceEvaluationScreenModel.evaluateRecommendationQualityForPromising()` calls `probe.probe(...)` from the screen model coroutine path. Unlike the main For You search path, the probe currently does not force extension/network calls onto `Dispatchers.IO` or an injected IO dispatcher.

Android therefore reports `NetworkOnMainThreadException`. This should be treated as an internal probe-threading bug, not as proof that every listed source has bad recommendation quality.

### Required Fix Before Final v0.7.35 Build

Claude must fix this before producing the final v0.7.35 APK/build handoff.

Required changes:

1. Update `SourceRecommendationFitProbe` so every source/extension call that can touch network or disk runs on an IO dispatcher:
   - `source.getFilterList()`
   - `source.getSearchManga(...)`
   - `source.getMangaDetails(...)`
2. Keep existing per-plan and enrichment timeouts.
3. Prefer an injectable dispatcher or small internal `Dispatchers.IO.limitedParallelism(...)` pattern matching nearby Komikku/KMK code.
4. Preserve cancellation behavior: rethrow `CancellationException`.
5. Do not broaden the probe scope; keep page 1, raw cap, enrichment cap, and bounded timeouts unchanged.
6. Ensure both installed-source and temporarily-installed-source recommendation-quality checks use the fixed probe automatically.
7. If the new group-seeded recommendation feature introduces any direct extension/source calls, those calls must follow the same IO-safe pattern.

### UI/Error Handling Requirement

If `NetworkOnMainThreadException` somehow still occurs, classify it as an internal probe execution error in diagnostics rather than presenting it as an ordinary source search failure.

Do not mark a source as genuinely poor quality purely because this exception occurred.

### Tests Required For This Fix

Add or update tests near `SourceRecommendationFitProbeTest`:

- A fake `CatalogueSource` that records/checks the thread or coroutine dispatcher used by `getSearchManga` and `getMangaDetails`.
- A regression test proving probe source calls are not executed on the caller/main test dispatcher.
- A test that a thrown `NetworkOnMainThreadException` is captured into probe outcome/error diagnostics without crashing the screen model.

If thread identity is hard to test directly in the existing unit test environment, add a fake source that throws unless called under the expected injected IO dispatcher and document the approach in the implementation report.

### Acceptance Criteria For This Follow-Up

- Existing recommendation-quality source rows no longer mass-fail with `NetworkOnMainThreadException`.
- Errors that remain are real source/network/timeout/no-result outcomes, not UI-thread network bugs.
- The implementation report explicitly states this fix was completed before the final v0.7.35 build.

## Part 1: Generalize Loved Manga Into Rated Manga

### Goal

Create a generalized rated-manga framework that can show Loved manga, Liked manga, and Disliked manga through clear rating-specific entry points. The existing Loved Manga feature should remain available and should behave the same for users who only open Loved Manga.

### Preferred Architecture

Refactor the current Loved Manga files into rating-aware equivalents while minimizing churn.

Recommended approach:

1. Keep `LovedMangaScreen` as a compatibility route/entry point.
2. Add a new screen concept, either `RatedMangaScreen(initialRating = MangaRating.LOVE)` with `LovedMangaScreen` delegating to it, or keep the physical filename `LovedMangaScreen.kt` but introduce a generalized internal `RatedMangaContent`.
3. Prefer clear new names for reusable models/helpers: `RatedMangaScreenModel`, `RatedMangaEntry`, `RatedMangaDisplayItem`, `RatedMangaSortMode`, `RatedMangaSourceFilter`.
4. Keep wrappers/aliases for old Loved names if this reduces route or test churn.

Do not break existing navigation to Loved Manga.

### UI Behavior

Add rating-specific quick entry points plus a broader Rated Manga view where useful:

- Keep the current heart icon behavior: tapping the heart opens Loved Manga only.
- Add a thumbs-up icon/action that opens the same style of screen filtered to Liked manga.
- Add a thumbs-down icon/action that opens the same style of screen filtered to Disliked manga.
- If a general Rated Manga screen is also added, it may use tabs or filter chips for `Loved`, `Liked`, and `Disliked`, but the rating-specific icons must still open the expected category directly.
- Default category should match the entry point: heart -> Loved, thumbs-up -> Liked, thumbs-down -> Disliked.
- Preserve group duplicates toggle, sort controls, version count badge, link group management action, manga cover grid behavior, and installed-source filtering.

Disliked manga should be viewable, but this does not mean they should appear in For You. Existing For You visibility settings still control recommendation visibility.

### Filtering Behavior

Generalize `filterLovedTastesByInstalledSources(...)` into something like:

```kotlin
filterRatedTastesByInstalledSources(
    tastes: List<MangaTaste>,
    installedSourceIds: Set<Long>,
    allowedRatings: Set<MangaRating>,
): List<MangaTaste>
```

Keep the old Loved wrapper if tests or existing call sites depend on it.

### Grouping Behavior

Grouping should work within the currently selected rating category by default. Do not merge a Loved entry and a Disliked entry into one display item just because they are linked.

For example:

- In the Loved tab, group Loved versions together.
- In the Liked tab, group Liked versions together.
- In the Disliked tab, group Disliked versions together.

If a future `All rated` tab is added, it must clearly display mixed rating state and should be treated as out of scope for v0.7.35 unless trivial.

### Export Behavior

Existing Loved Manga JSON export should continue to export Loved Manga when opened from the Loved tab. Do not silently change `kmk_loved_manga.json` to include liked/disliked items.

If a broader export is added, use a separate filename such as `kmk_rated_manga.json`, include rating type in the exported schema, and keep backward compatibility. Broad export is optional for this implementation.

## Part 2: Recommendations From Rated/Grouped Manga

### Goal

Allow a user to generate recommendations from a rated manga/group, especially from Loved manga. If the manga is part of a confirmed cross-source group, the recommendation seed should use the broader grouped representation rather than only one source entry.

### Entry Points

Recommended entry points:

1. Loved/Liked/Disliked Manga grid item overflow or long-press action: `Recommendations from this`.
2. Manga detail rating menu or recommendations menu: optional, only if it fits existing UI patterns without clutter.

Start with the Loved/Liked/Disliked rated-manga screens if there is any doubt.

### Seed Resolution Rules

Given a selected displayed item:

1. Identify its key as `(source, url)`.
2. Check whether that key belongs to a `manga_cross_source_link` group.
3. If yes, load all entries in that link group, resolve local manga rows by `(source, url)`, include only installed-source entries, and use the selected item as the primary display manga.
4. If no link group exists, use the selected manga only. Optionally include conservative display-group members already computed by the Loved/Rated grouper, but do not create/persist new links automatically.

Do not perform a fresh global same-manga search as part of this action. The point is to reuse already-confirmed grouping.

### Seed Metadata

Create a small pure model for the seed, for example:

```kotlin
data class GroupRecommendationSeed(
    val primaryTitle: String,
    val titles: List<String>,
    val tags: List<String>,
    val sourceIds: Set<Long>,
    val memberKeys: Set<MangaTasteKey>,
    val groupId: String?,
)
```

Collect primary title, alternate titles from linked versions, genres/tags from all linked local manga rows, source IDs, `(source, url)` keys for exclusion, and optional descriptions/authors/artists if useful for scoring or display.

Deduplicate metadata with existing normalization helpers where possible: `normalizeTag()` for tags/genres, existing alias maps from `GetTagAliases`, and Loved Manga title normalization only if already appropriate.

### Recommendation Query Strategy

Reuse the existing For You recommendation pipeline where possible:

- `GetTasteProfile`
- `GetTagAliases`
- `RecommendationQueryPlanner`
- `GenreFilterMapper`
- `RecommendationCandidateEnricher`
- `PersonalRecommendationScorer`
- `RecommendationSourceOrdering`
- `RecommendationSourceFilter`
- source priority/language/disabled-source preferences

Do not create a completely separate recommendation engine.

The seed should influence query generation by adding/boosting the selected group's positive tags and title/alias information, but should not overwrite the user's global taste profile entirely.

Recommended scoring model:

1. Start with the global `TasteProfile`.
2. Create a temporary positive seed profile contribution: tags from the group get a modest positive boost, repeated tags across multiple linked versions can receive slightly higher confidence, and blocked tags still remain blocked.
3. Exclude all seed member keys from results so the selected manga/group does not recommend itself.
4. Apply existing rated/seen/favorite/known filters.

### Display Behavior

Use existing recommendation row/card components if possible. A new screen titled `Recommendations from <title>` is acceptable. A Top Picks-style row plus source rows is preferred if reuse is practical; a simple ranked list is acceptable if it is cleaner.

Minimum acceptable behavior:

- show loading state,
- show source errors without crashing,
- show empty state when no results are found,
- allow tapping a manga to open its manga page,
- hide seed manga versions from results,
- apply seen/rated/known filters consistently with For You.

### Cache Behavior

Do not reuse the normal For You cache key directly, because the seed changes the query. If caching is added, use a separate prefix such as `group_seed_v1:<groupId-or-source-url-hash>:<sourceId>:<queryKey>`.

Fingerprint should include global taste profile fingerprint elements, seed member keys or group ID, seed tag list, blocked tags, selected recommendation languages, source order/disabled-source preferences, and hide-known/seen count/min-chapter settings if the same filters are applied.

If caching adds too much complexity, it is acceptable to skip caching for the first implementation and document that decision.

## Part 3: Do Not Duplicate Seen With Title-Specific Dislike

Add or update documentation to explicitly explain:

- `Dislike` is a negative taste signal.
- `Seen` is neutral title exclusion.
- `Seen other versions` is neutral exclusion for selected alternate versions.
- No new title-only dislike feature was added because it would duplicate `Seen`.

Potential documentation targets: `CURRENT_STATE.md`, `NEXT_WORK.md` if applicable, `README.md`, and the implementation report created by Claude after coding.

## Part 4: Strings And Komikku Alignment

Follow the existing KMK string discipline:

- User-visible KMK strings must be in `i18n-kmk`.
- Do not hardcode English strings in Compose screens.
- Follow existing Komikku screen/component style.
- Avoid adding large explanatory text inside the main UI.

Likely string additions: Liked Manga title, Disliked Manga title, optional Rated Manga title, Loved/Liked/Disliked tab labels if a combined screen exists, Recommendations action label, Recommendations-from screen title, and empty/error labels for group-seeded recommendations.

## Part 5: Tests Required

Add focused tests before broad build verification.

### Rated Manga Filtering Tests

Extend or add tests near `app/src/test/java/exh/recs/loved/LovedMangaSourceFilterTest.kt`.

Required cases:

- LOVE entries pass when selected rating is LOVE.
- LIKE entries pass when selected rating is LIKE.
- DISLIKE entries pass when selected rating is DISLIKE.
- entries from uninstalled sources are filtered out for every rating.
- existing `filterLovedTastesByInstalledSources` wrapper still returns only LOVE entries.

### Rated Manga Grouping Tests

Required cases:

- link-group grouping still wins over metadata fallback.
- grouping is display-only.
- grouping within one rating category does not accidentally include a different rating category.

### Seen/Dislike Regression Tests

Add or confirm tests proving:

- `MangaRating.DISLIKE` still creates negative learned tag weights in `GetTasteProfile`.
- `SeenRecommendationMangaStore` entries are not read by `GetTasteProfile`.
- MarkSeen path does not write taste rows, if this can be tested with existing fake repositories.
- For You still filters seen keys.

### Group-Seeded Recommendation Tests

Create pure helper tests for seed building:

- single manga seed includes its title/tags/source key.
- linked group seed includes all installed linked members.
- linked group seed ignores uninstalled/unresolvable members.
- seed metadata deduplicates tags/titles.
- seed member keys are exposed for result exclusion.

If a new screen model is added, add tests for seed member exclusion, seen/favorite/rated filters, and empty/error source result handling.

## Part 6: Documentation Required After Implementation

Claude must create an implementation report after coding, following `DOCUMENTATION_RULES.md`.

Suggested file:

```text
docs/recommendations/KMK_RECS_V0_7_35_RATED_MANGA_AND_GROUP_SEEDED_RECOMMENDATIONS_IMPLEMENTATION.md
```

Also update:

- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md` if any related item is added or closed
- `docs/recommendations/README.md`
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`
- `RECOMMENDATION_VERSIONING.md` if an APK is handed off

## Verification Commands

Run at minimum, after implementing both the recommendation-quality probe threading fix and the rated/group-seeded recommendation work:

```text
./gradlew spotlessCheck
./gradlew :app:testDebugUnitTest --tests "*LovedManga*"
./gradlew :app:testDebugUnitTest --tests "*Rated*"
./gradlew :app:testDebugUnitTest --tests "*GetTasteProfile*"
./gradlew :app:testDebugUnitTest --tests "*ForYouVisibility*"
./gradlew :app:testDebugUnitTest --tests "*SourceRecommendationFitProbe*"
./gradlew :app:testDebugUnitTest
./gradlew assembleDebug
```

If no `Rated*` tests exist because naming stayed under `Loved*`, explain that in the implementation report and list the equivalent tests that were run.

## Acceptance Criteria

Implementation is acceptable when:

1. Loved Manga still works exactly as before when opened normally from the heart icon.
2. Separate quick entry points exist for Loved, Liked, and Disliked manga, and each opens the expected rating category directly.
3. If a broader Rated Manga view is added, it can show Loved, Liked, and Disliked manga separately.
4. Installed-source filtering applies to all rating categories.
5. Duplicate grouping still uses confirmed cross-source links first.
6. Grouping does not merge entries across rating categories by accident.
7. A user can request recommendations from a rated/loved/liked manga or group.
8. Group-seeded recommendations use linked/group metadata when available.
9. Seed manga versions are excluded from the results.
10. Existing For You filters for seen, known, favorite, rated visibility, blocked tags, and source preferences are preserved where applicable.
11. `Dislike` remains a negative recommendation signal.
12. `Seen` remains the neutral "do not show this specific manga again" mechanism.
13. No new title-specific dislike state is added.
14. KMK strings are localized through `i18n-kmk`.
15. The recommendation-quality probe no longer performs source/network calls on the main thread.
16. Tests pass and the implementation report is written.

## Known Limitations To Document

- Group-seeded recommendations depend on local metadata already available from linked versions. If linked entries lack tags/genres, recommendation quality may be limited.
- This feature should not perform broad automatic same-manga discovery. Users still use existing cross-extension matching to confirm versions.
- Conservative grouping may miss valid alternate-title versions unless they were previously linked.
- If caching is skipped in the first implementation, group-seeded recommendations may be slower than cached For You results.




