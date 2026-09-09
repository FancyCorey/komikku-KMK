# KMK-Recs v0.5.1 Matching Cap And Origin Filter Implementation

Date: 2026-06-16

Status: implemented.

## Summary

Small correction patch for the cross-extension rating matching workflow introduced in v0.5.0.

The matching cap was already set to 2 in `CrossExtensionMatchScreenModel.PER_SOURCE_RESULT_LIMIT`, but origin filtering happened after the cap. This meant a source returning `[origin, A, B, C]` would produce `[A]` instead of `[A, B]`, wasting one result slot on the origin manga. The patch moves origin filtering to before the cap and adds a defensive guard in `toggleSelection()`.

## Changes

### `CrossExtensionMatchScreenModel.kt`

Added `isOrigin()` private helper:

```kotlin
private fun isOrigin(manga: Manga): Boolean {
    val origin = originManga ?: return false
    return manga.source == origin.source && manga.url == origin.url
}
```

In `search()`, added `.filterNot(::isOrigin)` before `.take(PER_SOURCE_RESULT_LIMIT)`:

```kotlin
.let { networkToLocalManga(it) }
.filterNot(::isOrigin)
.take(PER_SOURCE_RESULT_LIMIT)
```

In `toggleSelection()`, added a defensive early return:

```kotlin
val origin = originManga
if (origin != null && key.source == origin.source && key.url == origin.url) return
```

`updateItem()` retains its own origin exclusion from auto-selection for completeness, but the primary fix is at the search/cap level.

### `KmkRecsReleaseNotes.kt`

Added a second bullet to the v0.5.1 What's New entry:

```text
- The manga you are rating no longer appears as a candidate in the matching list.
```

### `CrossExtensionMatchSelectionTest.kt`

Added two new tests (10 total, up from 8):

- `origin manga is filtered before cap - source returns origin plus two others` â€” verifies `[origin, A, B, C]` with cap=2 produces `[A, B]`.
- `toggleSelection defensively ignores origin key` â€” verifies the guard prevents origin from being added to the selected set.

## Acceptance Criteria Check

| Criterion | Status |
| --- | --- |
| Origin manga never appears as a displayed candidate | Fixed â€” filtered before `MatchItemResult.Success` is stored |
| Origin manga never counts toward "Selected N of M" | Fixed â€” not in result list, not auto-selected |
| Origin manga cannot be selected manually | Fixed â€” defensive guard in `toggleSelection()` |
| Source returning `[origin, A, B]` shows both A and B | Fixed â€” filter before cap |
| Normal global search uncapped | Unchanged â€” `SearchScreenModel.perSourceResultLimit` still defaults to `null` |

## Files Changed

- `app/src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt`
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`
- `app/src/test/java/exh/recs/matching/CrossExtensionMatchSelectionTest.kt`
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/README.md`
- `RECOMMENDATION_VERSIONING.md`

## Tests Run

- `exh.recs.matching.CrossExtensionMatchSelectionTest` â€” 10 tests, all PASSED
- `:app:testDebugUnitTest` â€” BUILD SUCCESSFUL
- `:app:assembleDebug` â€” BUILD SUCCESSFUL

## APK

`Komikku-v1.13.6-kmk.5.1-debug.apk`

## Deferred

Favorite mode, cross-source link groups, backup/restore for link groups â€” all remain deferred to a future version.

