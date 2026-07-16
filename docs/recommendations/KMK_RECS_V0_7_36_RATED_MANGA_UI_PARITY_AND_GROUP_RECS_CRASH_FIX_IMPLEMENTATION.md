# KMK-Recs v0.7.36 â€” Rated Manga UI Parity And Group-Seeded Recommendation Crash Fix â€” Implementation Report

Date: 2026-07-08

## Summary

Four fixes shipped in v0.7.36:

1. **Rated Manga UI parity** â€” Liked and Disliked manga screens now share the full Loved Manga feature set via a refactored `RatedMangaCollectionContent` composable. All rating tiers have sort chips, group-duplicates toggle, version badges, cross-source link management, and export/share.
2. **Library toolbar shortcuts** â€” Loved Manga, Liked Manga, and Disliked Manga are accessible from the Library overflow menu, not only from Browse > For You.
3. **Group-seeded recommendation crash fix** â€” All recommendation results are now localized via `NetworkToLocalManga` before being shown. `MangaScreen` no longer receives non-local manga ids from this screen.
4. **Discoverability** â€” A small Explore icon overlay on each Loved/Liked card opens "Recommendations from this" without requiring a long-press. Long-press still works. Disliked cards omit the overlay.

APK: `Komikku-v1.13.6-kmk.7.36-debug.apk`

---

## 1. Rated Manga UI Parity (Goals 1 + 2)

### Problem

`RatedMangaScreen` (introduced in v0.7.35 for LIKE/DISLIKE) was a stripped-down grid. It had no export, no sort chips, no group-duplicates toggle, no version badges, no link management, and no group-seeded recommendation entry. Users of Liked and Disliked manga had significantly fewer tools than Loved Manga.

### Refactoring approach

**Extracted shared content composable:**

```
RatedMangaCollectionContent(rating: MangaRating, screenModel: LovedMangaScreenModel)
```

This composable implements the full UI â€” all features previously only in `LovedMangaScreen` â€” and is parameterized by `MangaRating` to vary title strings, empty/error strings, export filename, and action label.

**`LovedMangaScreen`** (compatibility route) now delegates to this shared composable:

```kotlin
class LovedMangaScreen : Screen() {
    @Composable override fun Content() {
        val screenModel = rememberScreenModel { LovedMangaScreenModel() }
        RatedMangaCollectionContent(rating = MangaRating.LOVE, screenModel = screenModel)
    }
}
```

**`RatedMangaScreen(ratingValue: Int)`** calls the same content with LIKE/DISLIKE:

```kotlin
data class RatedMangaScreen(val ratingValue: Int) : Screen() {
    @Composable override fun Content() {
        val rating = MangaRating.fromValue(ratingValue) ?: MangaRating.LIKE
        val screenModel = rememberScreenModel(tag = "rated_$ratingValue") {
            LovedMangaScreenModel(filterRating = rating)
        }
        RatedMangaCollectionContent(rating = rating, screenModel = screenModel)
    }
}
```

The private helper composables `GroupDuplicatesToggleRow` and `LoveSortRow` moved from `LovedMangaScreen.kt` into `RatedMangaScreen.kt` (renamed `RatedGroupDuplicatesToggleRow` / `RatedSortRow`) as private composables called by `RatedMangaCollectionContent`.

### Export per rating tier

| Rating  | Action string                     | Filename                   |
|---------|-----------------------------------|----------------------------|
| LOVE    | `rec_bundle_export_loved_manga`   | `kmk_loved_manga.json`     |
| LIKE    | `rec_bundle_export_liked_manga`   | `kmk_liked_manga.json`     |
| DISLIKE | `rec_bundle_export_disliked_manga`| `kmk_disliked_manga.json`  |

New i18n strings added to `i18n-kmk/.../base/strings.xml`:
- `rec_bundle_export_liked_manga` â€” "Export Liked Manga"
- `rec_bundle_export_disliked_manga` â€” "Export Disliked Manga"

The existing `RecommendationBundleExporter.buildLovedMangaBundle` method is reused as-is â€” the bundle serialization is rating-agnostic. The filename and action label carry the rating identity.

**Files changed:**
- `app/src/main/java/exh/recs/loved/RatedMangaScreen.kt` â€” full rewrite with `RatedMangaCollectionContent`
- `app/src/main/java/exh/recs/loved/LovedMangaScreen.kt` â€” simplified to thin delegation
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` â€” 2 new export strings

---

## 2. Library Toolbar Shortcuts (Goal 3)

### Problem

Loved/Liked/Disliked manga were only reachable from Browse > For You. Library had no direct entry points.

### Implementation

Added three optional callbacks to `LibraryToolbar` and `LibraryRegularToolbar`:

```kotlin
onClickLovedManga: (() -> Unit)? = null,
onClickLikedManga: (() -> Unit)? = null,
onClickDislikedManga: (() -> Unit)? = null,
```

When non-null, each adds an overflow menu item to `LibraryRegularToolbar`. Selection mode (`LibrarySelectionToolbar`) is unchanged â€” the KMK callbacks are only present in the regular toolbar branch.

Wired in `LibraryTab.kt`:

```kotlin
onClickLovedManga = { navigator.push(LovedMangaScreen()) },
onClickLikedManga = { navigator.push(RatedMangaScreen(MangaRating.LIKE.value)) },
onClickDislikedManga = { navigator.push(RatedMangaScreen(MangaRating.DISLIKE.value)) },
```

**Design choice:** overflow menu items rather than visible icon buttons. The Library toolbar already has one visible icon (Filter) and overflow for Update Library, Update Category, Random Manga, and Invalidate Cache. Adding three more visible icons would crowd the toolbar on smaller screens. The overflow menu is an appropriate home for infrequent navigation shortcuts.

**Files changed:**
- `app/src/main/java/eu/kanade/presentation/library/components/LibraryToolbar.kt` â€” 3 optional callbacks + `KMR` import
- `app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryTab.kt` â€” wire callbacks, add imports for `LovedMangaScreen`, `RatedMangaScreen`, `MangaRating`

---

## 3. Group-Seeded Recommendation Crash Fix (Goal 4)

### Problem

`GroupSeededRecommendationsScreenModel` emitted `PersonalRecommendation` objects whose `manga` field was a transient in-memory `Manga` created by `smanga.toDomainManga(source.id)`. This manga did not exist in the local database. When the user tapped a result, `GroupSeededRecommendationsScreen` called `navigator.push(MangaScreen(rec.manga.id, true))`, which caused `MangaScreenModel.getMangaById` to return null â†’ `NullPointerException` crash.

### Fix

Injected `NetworkToLocalManga` into `GroupSeededRecommendationsScreenModel`:

```kotlin
private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
```

After building the transient `raw` manga from search results, localize it before scoring:

```kotlin
val raw = smanga.toDomainManga(source.id)
// KMK --> v0.7.36: localize before scoring so MangaScreen can open safely
val local = withContext(Dispatchers.IO) {
    networkToLocalManga(listOf(raw)).firstOrNull()
} ?: continue
// KMK <--
val scored = PersonalRecommendationScorer.score(local, boostedProfile, aliasMap)
```

If localization returns null for a candidate (rare; should not happen in practice), that candidate is skipped via `?: continue`. Only fully localized manga with valid local DB ids are emitted in `State.Success`.

**Side effect:** `NetworkToLocalManga` inserts or updates local manga rows as a natural part of its contract. This matches the behavior of For You, global search, and feed screens. The localized manga are not favorited, rated, or added to library.

**Files changed:**
- `app/src/main/java/exh/recs/group/GroupSeededRecommendationsScreenModel.kt` â€” inject `NetworkToLocalManga`, localize per candidate

---

## 4. Discoverability (Goal 5)

### Problem

Group-seeded recommendations were only accessible via long-press on Loved/Liked Manga cards. Long-press is not discoverable without prior knowledge.

### Implementation

Each LOVE/LIKE card in the grid now has a small icon badge overlay at `Alignment.TopStart`. It uses the same `Surface + Box(clickable)` pattern as the version count badge at `Alignment.TopEnd`:

```kotlin
if (rating != MangaRating.DISLIKE) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
        shape = MaterialTheme.shapes.extraSmall,
        modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .clickable { navigator.push(GroupSeededRecommendationsScreen(...)) }
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Explore,
                contentDescription = stringResource(KMR.strings.rated_manga_recommendations_from_this),
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
```

- `Icons.Outlined.Explore` communicates "explore similar" visually.
- The icon is 14.dp inside a `secondaryContainer` badge for visual distinction.
- Long-press on the card body still opens the same screen (for LOVE/LIKE).
- DISLIKE cards: no overlay. Long-press falls back to opening MangaScreen. Rationale: "find recommendations similar to something you dislike" is semantically confusing; restricting to LOVE/LIKE avoids the UX problem.

**Files changed:**
- `app/src/main/java/exh/recs/loved/RatedMangaScreen.kt` (via `RatedMangaCollectionContent`)

---

## Verification

- `spotlessApply`: BUILD SUCCESSFUL
- `testDebugUnitTest` (full suite, 267 tasks): BUILD SUCCESSFUL
  - All prior tests pass (SourceRecommendationFitProbeTest 17/17, LovedMangaSourceFilterTest 16/16, LovedMangaSortTest 7/7, LovedMangaDuplicateGrouperTest, etc.)
- `assembleDebug`: BUILD SUCCESSFUL
- APK: `Komikku-v1.13.6-kmk.7.36-debug.apk` (170.1 MB, at `private/`)

---

## Deferred / Not Changed in v0.7.36

- No new unit tests added for `GroupSeededRecommendationsScreenModel` (network-bound, fake `NetworkToLocalManga` injection would require additional test infrastructure). The localization fix is straightforward and matches the proven pattern from `BrowsePersonalRecommendationsScreenModel`.
- No DB caching added for group-seeded results (documented non-goal per plan).
- `GroupSeededRecommendationsScreen` click handler does not have an additional null-guard on `rec.manga.id` â€” if `networkToLocalManga` returns a localized row, its id is always a valid positive DB id. The `?: continue` in the model is the safety boundary.

