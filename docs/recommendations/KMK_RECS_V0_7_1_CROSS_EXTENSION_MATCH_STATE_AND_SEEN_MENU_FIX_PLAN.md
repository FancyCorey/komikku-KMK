# KMK-Recs v0.7.1: Cross-Extension Match State And Seen Menu Fix Plan

Date: 2026-06-20

Status: implementation plan, awaiting user approval before coding

Feature version: `KMK-Recs v0.7.1`

Expected APK name: `Komikku-v1.13.6-kmk.7.1-debug.apk`

## Summary

This plan fixes a crash triggered from the manga detail page when selecting `Mark other as seen` / `Seen other versions`, and fixes related action-menu visibility behavior.

The crash is caused by Android/Voyager trying to serialize `CrossExtensionMatchScreen`, which currently stores a `CrossExtensionMatchMode` instance directly. `CrossExtensionMatchMode.MarkSeen` is a Kotlin `data object` and does not implement `Serializable`, so Android fails during state save with:

```text
android.os.BadParcelableException: Parcelable encountered IOException writing serializable object
Caused by: java.io.NotSerializableException: exh.recs.matching.CrossExtensionMatchMode$MarkSeen
```

This bug is not limited to `MarkSeen`. The current implementation also has `CrossExtensionMatchMode.Favorite` as a `data object`, so the same state-save risk likely exists for `Favorite other versions`. Rating mode is a `data class` containing `MangaRating`; Claude must verify whether that path is safely serializable or should be covered by the same route-safe fix.

## User-Reported Behavior

Steps:

1. Open a manga detail page.
2. Open the KMK rating/action dropdown.
3. Select `Mark other as seen` / `Seen other versions`.
4. Komikku shows the `Whoops!` unexpected error screen.

Visible crash:

```text
java.lang.Throwable: android.os.BadParcelableException: Parcelable encountered IOException writing serializable object (name = exh.recs.matching.CrossExtensionMatchScreen)
...
Caused by: java.io.NotSerializableException: exh.recs.matching.CrossExtensionMatchMode$MarkSeen
```

Additional UI issue:

- After selecting `Mark as seen`, the `Seen other versions` option disappears.
- This is incorrect because marking the current manga as seen does not mean the user has marked other source versions as seen.

Expected behavior:

- Selecting `Seen other versions` from manga detail should open the cross-extension matching workflow without crashing.
- `Seen other versions` should remain available even if the current manga is already marked seen.
- The matching workflow should still let the user select/deselect other matching entries.
- Existing Love/Like/Dislike/Favorite other-version flows should remain unchanged except for the same serialization safety fix.

## Current Code Findings

### Crash Source

Relevant file:

```text
app/src/main/java/exh/recs/matching/CrossExtensionMatchScreen.kt
```

Current screen constructor:

```kotlin
class CrossExtensionMatchScreen(
    private val originMangaId: Long,
    private val mode: CrossExtensionMatchMode,
) : Screen()
```

Problem:

- `Screen` instances can be saved/restored by Voyager/Android.
- The screen stores `mode` directly.
- `CrossExtensionMatchMode.MarkSeen` is not serializable.
- Android tries to write the screen to a parcel/bundle and fails.

### Mode Definition

Relevant file:

```text
app/src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt
```

Current mode definition:

```kotlin
sealed interface CrossExtensionMatchMode {
    data class Rating(val rating: MangaRating) : CrossExtensionMatchMode
    data object MarkSeen : CrossExtensionMatchMode
    data object Favorite : CrossExtensionMatchMode
}
```

Risk:

- `MarkSeen` is confirmed crashing.
- `Favorite` likely has the same issue.
- `Rating` should be evaluated because it is also stored inside `CrossExtensionMatchScreen`.

### Navigation Entry Points

Relevant file:

```text
app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreen.kt
```

Current navigation examples:

```kotlin
navigator.push(
    CrossExtensionMatchScreen(
        originMangaId = successState.manga.id,
        mode = CrossExtensionMatchMode.MarkSeen,
    ),
)
```

Similar calls exist for:

- `CrossExtensionMatchMode.Rating(rating)`
- `CrossExtensionMatchMode.Favorite`

### Seen Other Versions Visibility

Relevant file:

```text
app/src/main/java/eu/kanade/presentation/manga/components/MangaInfoHeader.kt
```

Current condition:

```kotlin
if (onSeenOtherVersionsClicked != null && !isSeen) {
    DropdownMenuItem(...)
}
```

Problem:

- When the current manga is marked seen, `isSeen == true`.
- The menu hides `Seen other versions`.
- This incorrectly prevents the user from marking other source versions as seen.

Correct behavior:

```kotlin
if (onSeenOtherVersionsClicked != null) {
    DropdownMenuItem(...)
}
```

The single-entry seen action can still switch between `Mark as seen` and `Clear seen`.

## Goals

1. Fix the `NotSerializableException` / `BadParcelableException` for `MarkSeen`.
2. Ensure `Favorite` and `Rating` modes are also safe across Android/Voyager state save.
3. Keep normal global search unchanged.
4. Keep cross-extension matching behavior unchanged:
   - per-source cap remains 2;
   - origin filtering remains before cap;
   - candidates selected by default;
   - manual deselection preserved;
   - selected candidates only are applied.
5. Keep `Seen other versions` visible even when the current manga is already seen.
6. Review the immediate matching/navigation code for similar state-save exceptions.
7. Add defensive handling where appropriate so a bad mode or missing manga ID shows a safe error state instead of crashing.
8. Document the fix and tests.

## Non-Goals

- Do not redesign the manga action menu.
- Do not change For You scoring.
- Do not change Loved Manga view behavior.
- Do not change source evaluation.
- Do not implement new deferred roadmap phases.
- Do not change normal global search caps or behavior.
- Do not add broad app-wide exception swallowing.

## Recommended Fix

### Preferred Fix: Route-Safe Primitive Arguments

Do not store `CrossExtensionMatchMode` directly inside `CrossExtensionMatchScreen`.

Instead, store only primitive/serializable route arguments:

```kotlin
class CrossExtensionMatchScreen(
    private val originMangaId: Long,
    private val modeKey: String,
    private val ratingValue: Int? = null,
) : Screen()
```

Or use a small serializable enum:

```kotlin
enum class CrossExtensionMatchModeKey {
    RATING,
    MARK_SEEN,
    FAVORITE,
}
```

If using an enum, verify it is safely serializable in the Voyager screen context. The most conservative route is string/int primitives.

Add conversion helpers:

```kotlin
internal object CrossExtensionMatchRouteMode {
    const val RATING = "rating"
    const val MARK_SEEN = "mark_seen"
    const val FAVORITE = "favorite"

    fun fromMode(mode: CrossExtensionMatchMode): RouteArgs
    fun toMode(modeKey: String, ratingValue: Int?): CrossExtensionMatchMode?
}
```

The screen should reconstruct the mode inside `Content()`:

```kotlin
val mode = remember(modeKey, ratingValue) {
    CrossExtensionMatchRouteMode.toMode(modeKey, ratingValue)
}
```

If mode reconstruction fails:

- show a small error state,
- allow back navigation,
- do not crash.

### Alternative Minimal Fix

Make `CrossExtensionMatchMode` extend `java.io.Serializable`:

```kotlin
sealed interface CrossExtensionMatchMode : Serializable
```

This is smaller, but less robust because it keeps a richer object in the screen route. The preferred fix is primitive route arguments.

Claude may choose the minimal fix only if Voyager patterns in this codebase already use serializable route objects safely and a primitive route conversion would create unnecessary churn. If choosing the minimal fix, document the reason in the implementation report.

## Required Code Changes

### 1. Make CrossExtensionMatchScreen State-Safe

File:

```text
app/src/main/java/exh/recs/matching/CrossExtensionMatchScreen.kt
```

Required:

- stop storing raw `CrossExtensionMatchMode` directly in the screen;
- store route-safe mode data;
- reconstruct `CrossExtensionMatchMode` in `Content()`;
- pass reconstructed mode to `CrossExtensionMatchScreenModel`;
- keep screen title/confirm-label logic based on reconstructed mode;
- show safe error UI if mode is invalid.

Potential safe error UI:

```text
Unable to open matching action.
```

Use existing app error/empty-state components if a simple pattern exists.

### 2. Add Mode Route Helper

Preferred new file:

```text
app/src/main/java/exh/recs/matching/CrossExtensionMatchRouteMode.kt
```

Responsibilities:

- convert `CrossExtensionMatchMode` to primitive route args;
- convert primitive route args back to `CrossExtensionMatchMode`;
- handle unknown mode safely;
- handle invalid/unknown rating safely.

Suggested test file:

```text
app/src/test/java/exh/recs/matching/CrossExtensionMatchRouteModeTest.kt
```

Test cases:

- `Rating(LOVE)` round-trips;
- `Rating(LIKE)` round-trips;
- `Rating(DISLIKE)` round-trips;
- `MarkSeen` round-trips;
- `Favorite` round-trips;
- unknown mode returns null or safe error result;
- rating mode with missing/invalid rating returns null or a safe default only if explicitly intended.

### 3. Update Navigation Calls

File:

```text
app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreen.kt
```

Update calls such as:

```kotlin
CrossExtensionMatchScreen(
    originMangaId = successState.manga.id,
    mode = CrossExtensionMatchMode.MarkSeen,
)
```

to either:

```kotlin
CrossExtensionMatchScreen.fromMode(
    originMangaId = successState.manga.id,
    mode = CrossExtensionMatchMode.MarkSeen,
)
```

or:

```kotlin
CrossExtensionMatchScreen(
    originMangaId = successState.manga.id,
    modeKey = CrossExtensionMatchRouteMode.MARK_SEEN,
)
```

A `fromMode` factory is cleaner and reduces mistakes at call sites.

Apply the same fix to:

- Love/Like/Dislike other versions;
- Seen other versions;
- Favorite other versions.

Then search the repo for every `CrossExtensionMatchScreen(` call and update all of them.

### 4. Keep Seen Other Versions Visible

File:

```text
app/src/main/java/eu/kanade/presentation/manga/components/MangaInfoHeader.kt
```

Change:

```kotlin
if (onSeenOtherVersionsClicked != null && !isSeen) {
```

to:

```kotlin
if (onSeenOtherVersionsClicked != null) {
```

Expected behavior:

- `Mark as seen` changes to `Clear seen` when current manga is seen.
- `Seen other versions` remains visible.
- The user can mark other versions even if the current manga is already seen.

### 5. Check For Similar Exceptions

Claude should search for Voyager screens in the KMK recommendation feature that store non-primitive/non-serializable constructor state.

Minimum search:

```text
rg -n "class .*Screen\\(" app/src/main/java/exh/recs app/src/main/java/eu/kanade/tachiyomi/ui/manga app/src/main/java/eu/kanade/presentation/manga
rg -n "Screen\\(" app/src/main/java/exh/recs/matching app/src/main/java/exh/recs/loved app/src/main/java/exh/recs
```

Specific screens to inspect:

```text
exh/recs/matching/CrossExtensionMatchScreen.kt
exh/recs/loved/LovedMangaScreen.kt
exh/recs/TopPicksScreen.kt
exh/recs/evaluation/SourceEvaluationScreen.kt
```

Do not rewrite unrelated screens unless the same concrete serialization risk is present.

If another screen stores a complex non-serializable object, either:

- fix it in this pass if small and related, or
- document it as a follow-up if larger.

### 6. Exception Handling Scope

This fix should not add broad try/catch blocks that hide programming errors. The correct fix is to avoid placing non-serializable mode objects in route state.

Add narrow defensive handling only around:

- invalid route mode reconstruction;
- missing origin manga ID in `CrossExtensionMatchScreenModel` init;
- applying an action with no selected targets.

Current `applyRating()` already handles empty targets by calling `onComplete()`.

If origin manga is missing:

- screen model should expose an error state or screen should show an error;
- do not spin forever with `total == 0` and a loading indicator.

If adding this error state is too much for the serialization bugfix, document it as a follow-up, but Claude should at least inspect whether missing manga currently causes an infinite loader.

## Testing Plan

### Unit Tests

Add:

```text
app/src/test/java/exh/recs/matching/CrossExtensionMatchRouteModeTest.kt
```

Required tests:

- `Rating LOVE route args round trip`
- `Rating LIKE route args round trip`
- `Rating DISLIKE route args round trip`
- `MarkSeen route args round trip`
- `Favorite route args round trip`
- `unknown mode is rejected safely`
- `rating mode without rating is rejected safely`
- `invalid rating value is rejected safely`

If the implementation instead uses `Serializable`, add tests proving `ObjectOutputStream` can serialize and deserialize:

- `CrossExtensionMatchMode.MarkSeen`
- `CrossExtensionMatchMode.Favorite`
- `CrossExtensionMatchMode.Rating(MangaRating.LOVE)`

However, route-primitive tests are preferred.

### UI / Manual Tests

1. Open a manga detail page.
2. Select `Seen other versions`.
3. Confirm the matching screen opens without `Whoops`.
4. Press Home / switch app / rotate if possible / let Android background the app.
5. Return to Komikku and confirm no `BadParcelableException`.
6. Mark current manga as seen.
7. Reopen the rating/action dropdown.
8. Confirm `Seen other versions` is still visible.
9. Confirm `Clear seen` is visible for the current manga.
10. Open `Love other versions`, `Like other versions`, `Dislike other versions`, and `Favorite other versions` if available.
11. Confirm none crash on open/background/return.
12. Confirm normal global search remains unchanged.

### Commands

Run focused tests first:

```text
./gradlew :app:testDebugUnitTest --tests "*CrossExtensionMatch*"
```

Then run broader unit tests if practical:

```text
./gradlew :app:testDebugUnitTest
```

Build APK:

```text
./gradlew :app:assembleDebug
```

## Documentation Requirements

After implementation, create:

```text
docs/recommendations/KMK_RECS_V0_7_1_CROSS_EXTENSION_MATCH_STATE_AND_SEEN_MENU_FIX_IMPLEMENTATION.md
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

- exact crash fixed;
- route/state serialization approach chosen;
- files changed;
- whether `MarkSeen`, `Favorite`, and `Rating` are all covered;
- menu visibility change for `Seen other versions`;
- any adjacent screen-state exceptions found;
- tests run and results;
- APK path if built;
- known limitations or follow-ups.

User-facing What's New should say something like:

```text
- Fixed a crash when opening Seen other versions from manga details.
- Seen other versions now remains available after marking the current manga as seen.
```

Do not mention internal serialization, Parcelable, or documentation work in What's New.

## Recommendation

Proceed with this fix before further v0.7.x feature work. It is small, targeted, and addresses a confirmed crash in a user-facing manga-detail action. The preferred implementation is route-safe primitive screen arguments because it prevents the same Android state-save crash for all matching modes, not only `MarkSeen`.


