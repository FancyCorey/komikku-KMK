# KMK-Recs v0.7.0 Implementation Notes

Date: 2026-06-20
APK: `Komikku-v1.13.6-kmk.7.0-debug.apk`
VERSION_CODE: 700

## Scope

Implementation of Phase 5 from `KMK_RECS_DEFERRED_FEATURE_MASTER_IMPLEMENTATION_PLAN.md`.

---

## Goal

Add a user-facing Loved Manga view showing all manga rated `MangaRating.LOVE`, with a conservative display-only duplicate grouping option.

---

## Entry Point

A heart icon button ("Loved Manga") was added to the For You tab action bar, between the Refresh and Settings actions. Tapping it pushes `LovedMangaScreen` via the Voyager navigator. No new Browse tab was added — Browse navigation was not restructured.

---

## Changes Implemented

### New Files

**`app/src/main/java/exh/recs/loved/LovedMangaDuplicateGrouper.kt`**

Pure stateless helper for conservative duplicate grouping. Operates on `List<GroupInput>` (key, title, description strings) — no Android dependencies, fully unit-testable.

Grouping rules:
- Group only when `normalizeTitle(a) == normalizeTitle(b)` AND `normalizeDescription(a) == normalizeDescription(b)` AND `description.length >= 50`.
- Do not group by title alone.
- Entries with blank or short (< 50 chars) descriptions are always standalone.
- Normalization: lowercase, trim, collapse whitespace. No fuzzy/Levenshtein matching — strict equality after normalization only.

Output: `List<GroupResult>` in first-occurrence order. The first entry to establish a group key becomes the representative (`primaryKey`).

**`app/src/main/java/exh/recs/loved/LovedMangaScreenModel.kt`**

Voyager `StateScreenModel` with sealed `State` (Loading, Empty, Error, Success).

Init flow:
1. `getMangaTaste.awaitAll()` — gets all taste rows.
2. Filter to `rating == MangaRating.LOVE.value`.
3. Sort by `updatedAt` descending (most recently loved first).
4. For each, resolve manga: `getManga.await(taste.mangaId)` first, fallback to `getManga.await(taste.url, taste.source)`. Either may return null without error.
5. Emit `Empty` or `Success`.

`toggleGroupDuplicates()` flips `State.Success.groupDuplicates`. The `displayItems` computed property builds the display list via `LovedMangaDuplicateGrouper` when grouping is on, or flat `LovedDisplayItem(versionCount=1)` items when off. No data is written or deleted.

Data classes:
- `LovedMangaEntry(taste: MangaTaste, manga: Manga?)` — raw resolved entry.
- `LovedDisplayItem(taste: MangaTaste, manga: Manga?, versionCount: Int)` — one UI row.

**`app/src/main/java/exh/recs/loved/LovedMangaScreen.kt`**

Voyager `Screen` following `TopPicksScreen` pattern. Uses `LazyVerticalGrid` with `GridCells.Adaptive(96.dp)`.

States:
- Loading → `CircularProgressIndicator`.
- Empty → centered message.
- Error → centered error text.
- Success → full-width toggle row + manga grid.

Toggle row: full-width `Checkbox` + "Group clear duplicates" label above the grid items (`GridItemSpan(maxLineSpan)`).

Each grid item:
- Uses `MangaItem(title, cover, isFavorite, onClick, onLongClick)` from `eu.kanade.presentation.browse.components`.
- Tapping navigates to `MangaScreen(taste.mangaId, true)`.
- If `manga == null` (unresolved), falls back to `taste.title` for display and a `MangaCover(ogUrl=null)` placeholder for the cover image.
- If `versionCount > 1` (grouped item), a `Surface` badge showing "%1$d versions" is overlaid at `Alignment.TopEnd` of the item `Box`.

**`app/src/test/java/exh/recs/loved/LovedMangaDuplicateGrouperTest.kt`**

12 unit tests covering all grouping rules (see Tests section below).

---

### Modified Files

**`app/src/main/java/exh/recs/BrowsePersonalRecommendationsTab.kt`**
- Added `import androidx.compose.material.icons.outlined.Favorite`
- Added `import exh.recs.loved.LovedMangaScreen`
- Added `AppBar.Action(title = "Loved Manga", icon = Icons.Outlined.Favorite, onClick = { navigator.push(LovedMangaScreen()) })` between Refresh and Settings actions, inside `// KMK --> v0.7.0` block.

**`i18n-kmk/src/commonMain/moko-resources/base/strings.xml`**
- Added 5 new strings (see Strings section).

**`app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`**
- `VERSION_CODE = 700`
- `VERSION_NAME = "KMK-Recs v0.7.0"`
- Added v0.7.0 MARKDOWN section (3 bullet points, user-facing only).

---

## New Strings

| Key | Value |
|---|---|
| `loved_manga_title` | `Loved Manga` |
| `loved_manga_empty` | `No loved manga yet. Rate manga you love from the manga detail page.` |
| `loved_manga_error` | `Failed to load loved manga.` |
| `loved_manga_group_toggle` | `Group clear duplicates` |
| `loved_manga_versions` | `%1$d versions` |

---

## Tests

**`LovedMangaDuplicateGrouperTest`** — 12 tests, all PASSED:

1. `love entries are included` — single input → one group result
2. `each entry produces at least one group` — two different inputs → two groups
3. `same title and same non-blank description groups` — k1+k2 with matching title+desc → 1 group, versionCount=2
4. `same title but different description does not group` — different long descriptions → 2 separate groups
5. `blank description does not group` — both entries have `""` → 2 standalone groups
6. `short description below threshold does not group` — description < 50 chars → 2 standalone groups
7. `title case differences do not prevent grouping` — "MY MANGA" vs "my manga" → 1 group
8. `grouping disabled — all entries returned as size-1 groups` — each entry tested solo
9. `empty input returns empty list` — `computeGroups(emptyList())` → empty
10. `first entry in group becomes primary key` — k1 is primary for a 3-entry group
11. `output order follows first-occurrence order` — mixed standalone/grouped preserves encounter order
12. `three-way group with mixed standalone entries` — 3 "Same Title" + 1 "Other Title" → 2 groups

**Full test suite:** `:app:testDebugUnitTest` — BUILD SUCCESSFUL (all prior tests still pass)

**APK:** `:app:assembleDebug` — BUILD SUCCESSFUL

---

## Constraints Preserved

- No taste rows were deleted, merged, or altered. Grouping is display-only.
- For You, Top Picks, ratings, cross-extension matching, Seen, and Source Evaluation behavior unchanged.
- Browse navigation was not restructured. No new tab was added.
- Like/Dislike entries excluded — only LOVE entries appear.
- Unresolved manga (not in local DB) is handled gracefully: title from `MangaTaste.title`, placeholder cover.

---

## Known Limitations

- **Cover for unresolved manga**: If `getManga` returns null (extreme edge case: DB cleared after rating), the cover image shows a placeholder. The manga title from `MangaTaste` is still displayed.
- **Duplicate grouping is strict**: Near-exact matching is exact equality after normalization. Very similar but not identical descriptions (e.g. one has a trailing newline) do not group. This is intentional for the "conservative" requirement.
- **No live updates**: The screen loads once at open time. New ratings are not reflected until the screen is reopened.
- **No sorting options**: Entries are always sorted by `updatedAt` descending. A sort dropdown (by title, by source, etc.) is deferred.
- **No pagination**: All loved entries are loaded into memory at once. This is acceptable for the expected dataset size (loved manga lists tend to be small).
- **No backup/restore for the grouping toggle state**: The toggle is session-local and defaults to off.

---

## Deferred

- Sorting options (title, source, oldest first).
- Live updates when ratings change while the screen is open.
- Longer description near-exact threshold (Levenshtein or word-overlap) for looser grouping.
- Backup/restore for grouping preference.
