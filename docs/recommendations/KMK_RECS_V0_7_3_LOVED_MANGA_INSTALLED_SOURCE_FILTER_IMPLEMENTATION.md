# KMK-Recs v0.7.3: Loved Manga Installed Source Filter â€” Implementation Report

Date: 2026-06-21

Status: implemented

Feature version: `KMK-Recs v0.7.3`

APK: `Komikku-v1.13.6-kmk.7.3-debug.apk` (VERSION_CODE 730)

## Problem Fixed

The Loved Manga view showed every `MangaRating.LOVE` taste row regardless of whether the source/extension was still installed. A stale loved entry from an uninstalled extension would appear with its stored title and a placeholder cover, and the user could not open it. The issue also affected grouped duplicate counts: if one of three versions came from an uninstalled source, the count showed 3 instead of 2.

## Required Behavior (per plan)

- Loved Manga shows only entries whose `taste.source` is in the currently installed/visible catalogue sources.
- This is display-only filtering. Taste rows and cross-source link rows are not modified or deleted.
- Reinstalling a source can make previously hidden loved entries visible again.
- `versionCount` in grouped entries counts only visible installed-source members.
- If `SourceManager.getVisibleCatalogueSources()` fails, prefer empty state over showing all uninstalled entries (fail closed).
- Filtering happens before grouping.

## Implementation Details

### New file: `LovedMangaSourceFilter.kt`

Pure package-level helper in `exh.recs.loved`.

```kotlin
internal fun filterLovedTastesByInstalledSources(
    tastes: List<MangaTaste>,
    installedSourceIds: Set<Long>,
): List<MangaTaste>
```

Behavior:
- Keeps entries where `rating == MangaRating.LOVE.value` AND `source in installedSourceIds`.
- LIKE and DISLIKE entries are excluded even if their source is installed (LOVE filter was previously inline in the screen model; it is now consolidated into this helper).
- Does not mutate the input list.
- Preserves input order.

### Updated: `LovedMangaScreenModel.kt`

- Added `SourceManager` injection via `Injekt.get()`.
- In `load()`, source IDs are built with a fail-safe `runCatching` wrapper. If `getVisibleCatalogueSources()` throws, an empty set is used, which produces an empty state rather than showing uninstalled entries.
- `filterLovedTastesByInstalledSources(allTastes, installedSourceIds)` is called before `.sortedByDescending { it.updatedAt }`.
- Removed now-redundant `MangaRating` import from screen model (the `LOVE.value` comparison is inside the helper).
- Cross-source link loading (v0.7.2) is unchanged and still loads all links regardless of installed state.
- Grouping (v0.7.2) is unchanged; because entries are filtered before grouping, only installed-source entries reach the grouper.

### Updated: `KmkRecsReleaseNotes.kt`

- VERSION_CODE bumped 720 â†’ 730.
- VERSION_NAME â†’ "KMK-Recs v0.7.3".
- Added What's New entry: "Loved Manga now hides entries from sources that are no longer installed."

## Files Changed

| File | Change |
|---|---|
| `exh/recs/loved/LovedMangaSourceFilter.kt` | New â€” pure filter helper |
| `exh/recs/loved/LovedMangaScreenModel.kt` | Inject `SourceManager`; apply filter before sort; remove unused `MangaRating` import |
| `app/src/test/.../LovedMangaSourceFilterTest.kt` | New â€” 9 unit tests |
| `KmkRecsReleaseNotes.kt` | VERSION_CODE 730, VERSION_NAME v0.7.3, new What's New entry |
| `docs/recommendations/CURRENT_STATE.md` | Version, Loved Manga section updated |
| `docs/recommendations/NEXT_WORK.md` | v0.7.3 entry removed from planning queue |
| `docs/recommendations/README.md` | Plan status updated, implementation report added |
| `RECOMMENDATION_VERSIONING.md` | v0.7.3 entry added |

## Fail-Safe Behavior

If `sourceManager.getVisibleCatalogueSources()` throws (e.g., during a cold-start race), `getOrDefault(emptySet())` returns an empty set. `filterLovedTastesByInstalledSources` with an empty set produces an empty list. The screen model then transitions to `State.Empty` (existing empty state behavior) rather than crashing or showing all entries.

This matches the plan requirement: "prefer showing an empty state rather than crashing; do not fail open by showing all sources."

## Local Source

Local Source (`id == 0L`) is excluded by `getVisibleCatalogueSources()` in this codebase (same exclusion used by For You). Any loved entries from Local Source are therefore hidden in the Loved Manga view under the same rule. This matches the plan decision: no special-casing for Local Source in this pass.

## Tests Run

```
LovedMangaSourceFilterTest â€” 9 tests, all PASSED

  keeps LOVE entries whose source is installed                                     PASSED
  removes LOVE entries whose source is not installed                               PASSED
  removes LIKE entries even if source is installed                                 PASSED
  removes DISLIKE entries even if source is installed                              PASSED
  preserves entries when multiple installed source IDs exist                       PASSED
  returns empty list when installed source set is empty                            PASSED
  does not mutate input list                                                       PASSED
  returned list is a different object from input                                   PASSED
  filtering before grouping means versionCount counts only installed entries       PASSED

LovedMangaDuplicateGrouperTest â€” 38 tests, all PASSED

:app:testDebugUnitTest --tests "*LovedManga*"  BUILD SUCCESSFUL
:app:testDebugUnitTest                          BUILD SUCCESSFUL
:app:assembleDebug                              BUILD SUCCESSFUL
```

## What's New (user-facing)

```
- Loved Manga now hides entries from sources that are no longer installed.
```

## Known Limitations and Follow-Ups

- **No user-visible explanation for hidden entries**: If all loved manga are hidden because all sources are uninstalled, the normal empty state appears with no additional explanation. The plan noted an optional "No loved manga from installed sources" string but it was not added because it requires extra i18n work for a rare edge case. The existing empty state is acceptable.
- **Local Source loved manga**: Loved entries from Local Source are hidden because `getVisibleCatalogueSources()` excludes Local Source by id. If a future pass wants to show Local Source loved manga, that requires a separate product decision.
- **versionCount accuracy depends on filter**: Because filtering happens before grouping, `versionCount` correctly reflects only installed-source group members. If a group previously had 3 members but 2 sources are uninstalled, the single remaining member shows `versionCount = 1` and no version badge.

## Deviations from the Plan

None. All plan sections were implemented as specified.

