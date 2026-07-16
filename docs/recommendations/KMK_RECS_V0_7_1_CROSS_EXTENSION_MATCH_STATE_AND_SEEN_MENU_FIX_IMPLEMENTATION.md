# KMK-Recs v0.7.1: Cross-Extension Match State And Seen Menu Fix â€” Implementation Report

Date: 2026-06-20

Status: implemented

Feature version: `KMK-Recs v0.7.1`

APK: `Komikku-v1.13.6-kmk.7.1-debug.apk` (VERSION_CODE 710)

## Crash Fixed

`android.os.BadParcelableException: Parcelable encountered IOException writing serializable object (name = exh.recs.matching.CrossExtensionMatchScreen)`
Caused by: `java.io.NotSerializableException: exh.recs.matching.CrossExtensionMatchMode$MarkSeen`

Triggered when Android/Voyager tried to save `CrossExtensionMatchScreen` state during app background or rotation. The screen stored `CrossExtensionMatchMode` (a sealed interface) directly in its constructor. `CrossExtensionMatchMode.MarkSeen` and `CrossExtensionMatchMode.Favorite` are Kotlin `data object` types that do not implement `Serializable`, so the Android parcel write failed.

`CrossExtensionMatchMode.Rating` stores a `MangaRating` enum which also had the same theoretical risk and was fixed under the same approach.

## Serialization Approach

**Route-safe primitive arguments** (preferred approach from the plan).

`CrossExtensionMatchScreen` constructor changed from:

```kotlin
class CrossExtensionMatchScreen(
    private val originMangaId: Long,
    private val mode: CrossExtensionMatchMode,
) : Screen()
```

to:

```kotlin
class CrossExtensionMatchScreen(
    private val originMangaId: Long,
    private val modeKey: String,
    private val ratingValue: Int? = null,
) : Screen()
```

`modeKey` is one of the constants `"rating"`, `"mark_seen"`, `"favorite"`. `ratingValue` holds the `MangaRating.value` Int only for rating mode; `null` otherwise.

A new `CrossExtensionMatchRouteMode` internal object handles bidirectional conversion. Mode is reconstructed inside `Content()` via `remember(modeKey, ratingValue)`. If reconstruction returns `null` (unknown key or missing rating), the screen shows `"Unable to open matching action."` instead of crashing.

A `companion object { fun fromMode(...) }` factory was added to `CrossExtensionMatchScreen` for clean call sites.

## Seen Menu Visibility Fix

`MangaInfoHeader.kt` line 482: condition changed from

```kotlin
if (onSeenOtherVersionsClicked != null && !isSeen) {
```

to

```kotlin
if (onSeenOtherVersionsClicked != null) {
```

The `&& !isSeen` guard incorrectly hid "Seen other versions" after the current manga was marked seen. The action is independently useful: the user may want to mark other source versions as seen even if the current entry is already seen.

## Adjacent Screens Checked

All other KMK recs screens were verified safe:

- `LovedMangaScreen : Screen()` â€” no constructor args
- `SourceEvaluationScreen : Screen()` â€” no constructor args
- `TopPicksScreen(mangaIds: ArrayList<Long>, isPartial: Boolean)` â€” both are serializable types

No changes needed to adjacent screens.

## Files Changed

### New Files

- `app/src/main/java/exh/recs/matching/CrossExtensionMatchRouteMode.kt` â€” internal object with `RATING`, `MARK_SEEN`, `FAVORITE` key constants; `fromMode()` and `toMode()` conversion functions
- `app/src/test/java/exh/recs/matching/CrossExtensionMatchRouteModeTest.kt` â€” 8 unit tests for route helper

### Modified Files

- `app/src/main/java/exh/recs/matching/CrossExtensionMatchScreen.kt` â€” primitive constructor, `fromMode()` companion factory, safe error state on invalid mode, `remember(modeKey, ratingValue)` mode reconstruction
- `app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreen.kt` â€” 3 call sites updated to `CrossExtensionMatchScreen.fromMode(originMangaId, mode)` (Rating, Favorite, MarkSeen)
- `app/src/main/java/eu/kanade/presentation/manga/components/MangaInfoHeader.kt` â€” removed `&& !isSeen` from Seen other versions guard
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` â€” VERSION_CODE 700 â†’ 710, VERSION_NAME â†’ "KMK-Recs v0.7.1", added v0.7.1 What's New entries
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` â€” added `rec_match_mode_invalid` string
- `docs/recommendations/CURRENT_STATE.md` â€” updated version, CrossExtension state-save note, isSeen visibility note, test count
- `docs/recommendations/NEXT_WORK.md` â€” marked v0.7.1 bug section as resolved
- `docs/recommendations/README.md` â€” plan row updated; implementation report added
- `RECOMMENDATION_VERSIONING.md` â€” v0.7.1 entry added

## Tests Run

```
CrossExtensionMatchRouteModeTest > Rating LOVE route args round trip()         PASSED
CrossExtensionMatchRouteModeTest > Rating LIKE route args round trip()         PASSED
CrossExtensionMatchRouteModeTest > Rating DISLIKE route args round trip()      PASSED
CrossExtensionMatchRouteModeTest > MarkSeen route args round trip()            PASSED
CrossExtensionMatchRouteModeTest > Favorite route args round trip()            PASSED
CrossExtensionMatchRouteModeTest > unknown mode key is rejected safely()       PASSED
CrossExtensionMatchRouteModeTest > rating mode without rating value is rejected safely()  PASSED
CrossExtensionMatchRouteModeTest > invalid rating value is rejected safely()   PASSED

CrossExtensionMatchSelectionTest (10 tests)                                    all PASSED

:app:testDebugUnitTest --tests "*CrossExtensionMatch*"   BUILD SUCCESSFUL
:app:assembleDebug                                        BUILD SUCCESSFUL
```

## What's New (user-facing)

```
- Fixed a crash when opening Seen other versions from manga details.
- Seen other versions now remains available after marking the current manga as seen.
```

## Known Limitations and Follow-Ups

- If `originMangaId` is invalid (manga was deleted), the screen model `init` block calls `getMangaInteractor.await(originMangaId)` which returns `null`, and the search is never started. The screen will show an infinite loading spinner (`state.total == 0` â†’ `CircularProgressIndicator`). This pre-existed v0.7.1 and is not introduced by this fix. Flagged for a future pass if it becomes user-visible.
- Backup/restore for seen entries remains deferred.
- Cross-source link groups remain deferred.

