# KMK-Recs v0.7.36 Rated Manga UI Parity And Group-Seeded Recommendation Crash Fix Plan

Date: 2026-07-08

Status: implementation plan. Do not treat this as implemented until a matching implementation report, tests, and APK/build output exist.

## User-Approved Scope

This follow-up corrects issues found after KMK-Recs v0.7.35:

1. Loved/Liked/Disliked manga should share one reusable rated-manga collection UI instead of three separate feature paths.
2. Liked and Disliked manga screens should have the same practical controls as Loved Manga, including grouping, sorting, export/share, cross-source link management, duplicate controls, and version badges.
3. Loved/Liked/Disliked quick access should be available from the Library top app bar area, not only Browse > For You.
4. Group-seeded recommendations launched from Loved Manga currently crash when opening a recommended manga because the result may not exist in the local manga database. This must be fixed.
5. "Recommendations from this" should be more discoverable than long-press only.

## Current Verified Behavior

The following is verified from code and v0.7.35 implementation docs:

- `BrowsePersonalRecommendationsTab.kt` adds Loved, Liked, and Disliked shortcuts only to Browse > For You.
- `LibraryToolbar.kt` and `LibraryTab.kt` do not expose Loved/Liked/Disliked shortcuts.
- `LovedMangaScreen.kt` contains the full feature set:
  - export/share,
  - cross-source link management,
  - group duplicate toggle,
  - sort chips,
  - version badge,
  - no-duplicates feedback,
  - installed-source filtering through `LovedMangaScreenModel`,
  - long-press to `GroupSeededRecommendationsScreen`.
- `RatedMangaScreen.kt` is a simplified LIKE/DISLIKE-only grid:
  - no export/share,
  - no link management,
  - no sort chips,
  - no duplicate grouping toggle,
  - no version badge,
  - no group-seeded recommendation entry.
- `GroupSeededRecommendationsScreenModel.kt` builds recommendation result manga with `smanga.toDomainManga(source.id)`.
- `GroupSeededRecommendationsScreen.kt` opens results with `MangaScreen(rec.manga.id, true)`.
- Because the group-seeded result may not be localized/inserted into the local DB, `MangaScreenModel` can crash with:

```text
java.lang.NullPointerException: ResultSet returned null for mangas.sq:getMangaById
```

This is the crash seen when selecting a recommendation from the group-seeded recommendations screen.

## Goals

### Goal 1: Refactor Loved Manga Into A Reusable Rated Manga Collection UI

Do not keep `LovedMangaScreen` and `RatedMangaScreen` as separate UI systems.

Create a reusable implementation that supports all rating tiers:

- `MangaRating.LOVE`
- `MangaRating.LIKE`
- `MangaRating.DISLIKE`

Preferred approach:

1. Keep `LovedMangaScreen` as a compatibility route for existing navigation.
2. Make `LovedMangaScreen` delegate to the generalized rated manga collection screen/content with `MangaRating.LOVE`.
3. Make `RatedMangaScreen(ratingValue)` use the same shared content for LIKE/DISLIKE.
4. Avoid duplicating grid, toolbar, sort, export, grouping, and card rendering logic.

Possible naming:

- `RatedMangaCollectionScreen`
- `RatedMangaCollectionContent`
- `RatedMangaScreenModel`
- `RatedMangaDisplayItem`
- `RatedMangaSortMode`

However, do not rename aggressively if it creates unnecessary churn. It is acceptable to keep existing `LovedMangaScreenModel` temporarily if it is generalized cleanly and documented.

### Goal 2: Feature Parity For Liked And Disliked Manga Screens

Liked and Disliked Manga should support the same useful controls currently available in Loved Manga:

- group clear duplicates checkbox,
- sort chips:
  - most recent,
  - oldest first,
  - title A-Z,
  - source,
- version count badge,
- no clear duplicates feedback,
- installed-source-only display,
- cross-source link management action,
- export/share action,
- normal manga open on tap,
- a discoverable "Recommendations from this" action.

The titles, empty states, error states, and export filenames should match the selected rating:

- Loved Manga -> `kmk_loved_manga.json`
- Liked Manga -> `kmk_liked_manga.json`
- Disliked Manga -> `kmk_disliked_manga.json`

If the existing bundle exporter method is named `buildLovedMangaBundle`, either:

- safely generalize it to a rated manga bundle builder, or
- keep the method and document that it exports a list of rated manga display items, not only LOVE, if renaming is too risky.

User-facing strings must use `i18n-kmk` / KMR strings.

### Goal 3: Add Library Top App Bar Quick Access

Add quick access icons in the Library top app bar area:

- Heart -> Loved Manga.
- Thumbs up -> Liked Manga.
- Thumbs down -> Disliked Manga.

Implementation constraints:

- Do not break current Library toolbar behavior.
- Do not interfere with selection mode.
- Do not interfere with search behavior.
- Do not remove existing filter/update/random/sync actions.
- Prefer adding optional callbacks to `LibraryToolbar`:
  - `onClickLovedManga: (() -> Unit)?`
  - `onClickLikedManga: (() -> Unit)?`
  - `onClickDislikedManga: (() -> Unit)?`
- Wire those callbacks from `LibraryTab`, where `navigator` is available.
- Keep presentation components navigator-free.
- If space is tight, it is acceptable for one or more of these actions to go into overflow, but the preferred behavior is visible quick icons if the app bar can accommodate them cleanly.

Suggested files:

- `app/src/main/java/eu/kanade/presentation/library/components/LibraryToolbar.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryTab.kt`
- `app/src/main/java/exh/recs/loved/LovedMangaScreen.kt`
- `app/src/main/java/exh/recs/loved/RatedMangaScreen.kt`

### Goal 4: Fix Group-Seeded Recommendation Result Crash

Group-seeded recommendations must never navigate to `MangaScreen` with a manga id that does not exist in the local DB.

Current crash path:

```text
GroupSeededRecommendationsScreenModel
-> smanga.toDomainManga(source.id)
-> PersonalRecommendation(manga = transient domain manga)
-> GroupSeededRecommendationsScreen
-> MangaScreen(rec.manga.id, true)
-> MangaScreenModel getMangaById
-> null result crash
```

Required fix:

1. Inject or otherwise use `NetworkToLocalManga` in `GroupSeededRecommendationsScreenModel`.
2. Convert source search results into local manga rows before emitting them to UI, matching the safe pattern used by For You/global search/feed.
3. Do this on `Dispatchers.IO`.
4. If localization fails for a candidate, skip that candidate or show a safe per-screen error; never emit a result that can crash on click.
5. Preserve seed-member filtering by `(sourceId, url)` before or after localization as appropriate, but ensure final emitted results are local DB manga.
6. Keep result scoring based on genre/tag data. If details enrichment is needed before localization, follow the existing safe enrichment pattern and keep it bounded.

Likely implementation pattern:

```kotlin
val raw = smanga.toDomainManga(source.id)
val local = withContext(Dispatchers.IO) {
    networkToLocalManga(listOf(raw)).firstOrNull()
}
if (local != null) {
    val scored = PersonalRecommendationScorer.score(local, boostedProfile, aliasMap)
    ...
}
```

Adjust exact call style to match the actual `NetworkToLocalManga` API in this codebase.

Important: Do not use raw `toDomainManga()` results directly for any UI item that opens `MangaScreen`.

Suggested files:

- `app/src/main/java/exh/recs/group/GroupSeededRecommendationsScreenModel.kt`
- `app/src/main/java/exh/recs/group/GroupSeededRecommendationsScreen.kt`
- Tests under `app/src/test/java/exh/recs/group/` if practical.

### Goal 5: Make "Recommendations From This" Discoverable

Long-press-only discovery is not good enough.

Add a visible or semi-visible route to "Recommendations from this" on rated manga collection cards.

Acceptable approaches:

- Add an overflow/menu action on each item.
- Add a small icon button overlay on the card.
- Add a click/long-click help affordance if that matches existing Komikku card conventions.

Preferred behavior:

- Tap card -> open manga detail.
- Explicit action or long-press -> recommendations from this.
- Keep long-press as a shortcut if already implemented, but do not make it the only path.

For Disliked Manga:

- Be careful with wording.
- A disliked manga normally means "avoid manga like this", so "Recommendations from this" may be semantically confusing.
- If the action is available for Disliked entries, label it neutrally, such as "Find similar" or "Use as seed", and do not imply the user wants more of the same.
- If UX becomes confusing, restrict group-seeded recommendations to LOVE and LIKE for this version and document the choice.

## Non-Goals

- Do not redesign the whole Library screen.
- Do not add a new main tab.
- Do not change how ratings affect the taste profile.
- Do not create a second cross-source grouping system.
- Do not change the semantics of Seen:
  - Seen remains a neutral title-specific exclusion.
  - Dislike remains a negative recommendation signal.
- Do not add DB caching for group-seeded recommendation results in this pass unless absolutely necessary.

## Detailed Implementation Plan

### Part 1: Generalize The Rated Manga Route

1. Inspect current `LovedMangaScreen.kt`, `RatedMangaScreen.kt`, and `LovedMangaScreenModel.kt`.
2. Create one reusable content path for all rating tiers.
3. Ensure LOVE uses the same route as before:
   - existing `LovedMangaScreen()` calls must still work.
4. Ensure LIKE/DISLIKE use the same toolbar, grid, grouping, sort, and card rendering as LOVE.
5. Preserve `LovedMangaScreenModel(filterRating = rating)` behavior or rename/generalize it with minimal churn.
6. Remove or replace the simplified `RatedMangaScreen` grid implementation.

### Part 2: Generalize Labels, Empty States, Errors, Export Labels, And Filenames

1. Add helper functions or simple mappings for rating-specific:
   - title string,
   - empty string,
   - error string,
   - export action title,
   - export filename,
   - optional recommendation action title.
2. Add missing KMR strings in `i18n-kmk`.
3. Do not hardcode user-facing English in Compose files.

Potential new strings:

- `rec_bundle_export_liked_manga`
- `rec_bundle_export_disliked_manga`
- `rated_manga_find_similar`
- `rated_manga_recommendations_from_this`

Reuse existing strings where already present.

### Part 3: Library Toolbar Shortcuts

1. Add optional callbacks to `LibraryToolbar`.
2. Pass them through to `LibraryRegularToolbar`.
3. Add the icons to the normal, non-selection app bar actions:
   - `Icons.Outlined.Favorite`
   - `Icons.Outlined.ThumbUp`
   - `Icons.Outlined.ThumbDown`
4. Wire the callbacks from `LibraryTab`:
   - Loved -> `navigator.push(LovedMangaScreen())`
   - Liked -> `navigator.push(RatedMangaScreen(MangaRating.LIKE.value))`
   - Disliked -> `navigator.push(RatedMangaScreen(MangaRating.DISLIKE.value))`
5. Confirm selection mode app bar is unchanged.
6. Confirm search query mode still behaves normally.

### Part 4: Fix Group-Seeded Recommendations Crash

1. Inject `NetworkToLocalManga` into `GroupSeededRecommendationsScreenModel`.
2. Localize all emitted recommendation result manga before storing in `State.Success`.
3. Keep localization bounded and on IO dispatcher.
4. Do not emit transient `toDomainManga()` results.
5. Consider dedupe by stable key:
   - source id + url,
   - then local manga id after localization.
6. On click, only navigate if `rec.manga.id` is a valid local DB id.
7. If an unexpected invalid id remains, fail safe with a toast or screen-level error instead of opening `MangaScreen`.

### Part 5: Make Recommendations From This Discoverable

1. Keep long-press behavior if desired.
2. Add a visible explicit action for each rated manga card or row.
3. Prefer a familiar icon with tooltip/content description if the existing component supports it.
4. Avoid crowding card UI; if overlay is too busy, use overflow/menu.
5. Document the chosen interaction in the implementation report and What's New if user-visible.

### Part 6: Documentation Updates

Update:

- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/README.md`
- `docs/recommendations/KMK_RECS_V0_7_36_RATED_MANGA_UI_PARITY_AND_GROUP_RECS_CRASH_FIX_IMPLEMENTATION.md` (new implementation report)
- `RECOMMENDATION_VERSIONING.md` if an APK is handed off
- `app/src/main/java/exh/recs/release/KmkRecsReleaseNotes.kt` or the current release-note location, if user-visible What's New is updated

Also update `docs/KMK_MARKDOWN_ENCYCLOPEDIA.md` only if the new v0.7.36 report becomes a primary reference document rather than a routine follow-up.

## Testing Requirements

Run or add focused tests where practical:

### Required Manual QA

1. Library toolbar:
   - Loved icon opens Loved Manga.
   - Liked icon opens Liked Manga.
   - Disliked icon opens Disliked Manga.
   - Selection mode still shows selection toolbar.
   - Search still works.
   - Existing filter/update/random/sync actions still work.

2. Rated manga parity:
   - Loved/Liked/Disliked screens all show:
     - group duplicate toggle,
     - sort chips,
     - version badges,
     - no-duplicates feedback,
     - link management action,
     - export/share action.
   - Sorting works in all three screens.
   - Grouping works in all three screens.
   - Export files are rating-appropriate.

3. Group-seeded recommendations:
   - Long-press or explicit action opens recommendations from a rated manga.
   - Tapping a result opens manga detail without crash.
   - Results from unlocalized transient search objects do not crash.
   - Seed manga itself is filtered from results.

### Automated Tests To Prefer

- Existing `LovedMangaSourceFilterTest`.
- Existing `LovedMangaDuplicateGrouperTest`.
- Existing `LovedMangaSortTest`.
- Add a test for any new rating-label/export-filename helper if created.
- Add a focused test for group-seeded result localization if feasible with fake `NetworkToLocalManga`.

At minimum run:

```text
./gradlew spotlessCheck
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

If full tests are too slow, document exactly which focused tests were run and why full verification was skipped.

## Risks

### UI Crowding In Library Toolbar

Adding three icons may crowd the Library toolbar on smaller screens.

Mitigation:

- Use AppBar action overflow behavior if available.
- Keep icons out of selection mode.
- Do not remove existing Library actions.

### Export Semantics

Existing bundle export may be named Loved Manga even though it can technically export any rated list.

Mitigation:

- Either rename/generalize carefully or add rating-specific wrappers around the existing exporter.
- Ensure exported bundle title/metadata does not falsely claim all entries are loved when exporting liked/disliked lists.

### Disliked Recommendations From This

Offering recommendations from a disliked manga can confuse users.

Mitigation:

- Prefer neutral wording.
- Or restrict the action to LOVE/LIKE and document the decision.

### DB Localization Side Effects

`NetworkToLocalManga` writes/resolves local DB rows. This is expected for screens that can open manga details, but it means group-seeded recommendation results may now create local manga rows.

Mitigation:

- This matches For You/global search behavior.
- Do not mark them favorite.
- Do not rate them.
- Do not add them to library.

## Acceptance Criteria

The implementation is complete only when:

1. Loved, Liked, and Disliked screens share the same rated manga collection behavior.
2. Liked and Disliked screens are no longer stripped-down grids.
3. Library top app bar can open Loved, Liked, and Disliked manga.
4. Group-seeded recommendation result taps no longer crash.
5. Group-seeded recommendation results are localized before navigation.
6. "Recommendations from this" has a discoverable action beyond long-press, or the implementation report clearly documents why the chosen UI is different.
7. Documentation is updated.
8. Tests/build are run and recorded.
9. A new implementation report exists.

## Expected Version

```text
KMK-Recs v0.7.36
```

Expected debug APK name if built:

```text
Komikku-v1.13.6-kmk.7.36-debug.apk
```

