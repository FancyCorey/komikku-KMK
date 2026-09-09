# KMK-Recs v0.7.9 Best Version Dialog And Preview Polish Plan

Date: 2026-06-22

Status: planning; pending user approval before implementation.

## Goal

Fix and polish the v0.7.8 Best Version / Chapter Quality workflow without changing its core search, chapter matching, migration, or recommendation behavior.

This pass is intentionally narrow:

1. Fix the Best Version migration confirmation Cancel bug.
2. Add fullscreen page preview with pinch-to-zoom and pan.
3. Verify the same-manga preselect setting remains configurable and defaults to selected.
4. Add defensive state handling around invalid selected-best targets so the workflow does not get stuck.
5. Update documentation, release notes, and tests for the follow-up build.

## User-Confirmed Requirements

### Cancel Behavior

When the user taps "Select as best version" from the preview screen, the migration confirmation dialog appears.

If the user presses Cancel, taps outside the dialog, or dismisses via back:

- close only the confirmation dialog;
- return to the same Best Version preview screen;
- preserve loaded previews, selected candidates, selected chapter, and scrollable comparison state as much as Compose naturally allows;
- do not exit the Best Version workflow;
- do not navigate back to the manga page;
- do not start migration or copy.

### Fullscreen Preview Behavior

In the Best Version preview screen:

- tapping a sampled page thumbnail should open that page in fullscreen;
- fullscreen preview should show the same page image at a larger size;
- tapping/back/close should return to the exact comparison screen;
- fullscreen should support pinch-to-zoom and pan;
- closing fullscreen must not clear selected candidates, selected chapter, loaded previews, or selected-best state;
- thumbnail layout should remain compact for side-by-side source comparison.

### Same-Manga Match Selection Default

The existing same-manga setting should remain:

- default: selected by default;
- configurable in Recommendation Settings;
- applies to bounded same-manga workflows only, not normal global search.

Do not flip the default to unselected. The user only wants the ability to choose unselected behavior in settings when needed.

## Current Code Findings

### v0.7.8 Best Version Is Implemented

Current implementation report:

```text
docs/recommendations/KMK_RECS_V0_7_8_BEST_VERSION_CHAPTER_QUALITY_IMPLEMENTATION.md
```

Current active state:

```text
docs/recommendations/CURRENT_STATE.md
```

Key files:

```text
app/src/main/java/exh/recs/bestversion/BestVersionCompareScreen.kt
app/src/main/java/exh/recs/bestversion/BestVersionCompareScreenModel.kt
app/src/main/java/exh/recs/bestversion/BestVersionPageSampler.kt
app/src/main/java/exh/recs/bestversion/BestVersionChapterMatcher.kt
app/src/main/java/exh/recs/matching/SameMangaCandidateSearcher.kt
app/src/main/java/exh/recs/matching/SameMangaMatchSettings.kt
app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt
app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt
app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt
i18n-kmk/src/commonMain/moko-resources/base/strings.xml
app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt
```

### Cancel Bug Root Cause

In `BestVersionCompareScreen.kt`, the dialog is shown when:

```kotlin
if (state.selectedBestKey != null && state.step == BestVersionStep.ComparePreview) {
    ...
}
```

The dialog currently dismisses with:

```kotlin
onDismiss = { screenModel.selectBestVersion(MangaIdentityKey(-1, "")) }
```

This is the core bug.

`MangaIdentityKey(-1, "")` is still non-null, so the dialog condition remains true. The UI can end up with a fake selected best key, no real target, an empty target title, and no clean way to dismiss the dialog.

The fix should remove the fake-key sentinel entirely.

### Model State Supports A Clean Fix

`BestVersionCompareScreenModel.State` already has:

```kotlin
val selectedBestKey: MangaIdentityKey? = null
```

Therefore the correct dismiss state is simply:

```kotlin
selectedBestKey = null
```

Add a dedicated action to the model, for example:

```kotlin
fun dismissMigrationDialog() {
    mutableState.update {
        it.copy(
            selectedBestKey = null,
            isMigrating = false,
        )
    }
}
```

If `isMigrating` is only meaningful during `PreparingMigration`, resetting it here is harmless but should be considered carefully. Do not reset unrelated fields.

### Confirm Migration Should Be Defensive

Current `confirmMigration(replace: Boolean)` does this:

```kotlin
val origin = originManga ?: return
val key = state.value.selectedBestKey ?: return
val target = state.value.selectedCandidates.find { it.source == key.source && it.url == key.url } ?: return
mutableState.update { it.copy(step = BestVersionStep.PreparingMigration, isMigrating = true) }
```

If the selected key is invalid, the function silently returns. That is acceptable for some stale UI races, but it can leave the UI in an unclear state if `selectedBestKey` remains non-null.

For v0.7.9, make this defensive:

- if `origin` is missing, transition to a safe `Error` or clear dialog and show an existing origin-missing error;
- if `selectedBestKey` is null, do nothing;
- if `target` cannot be found, clear `selectedBestKey` and remain on `ComparePreview`, or show a user-facing error state if a suitable string exists.

Do not silently keep an invalid selected-best key.

### Preview Thumbnails Currently Cannot Be Opened

In `ComparePreviewContent`, loaded sampled pages are rendered in a `LazyRow`:

```kotlin
AsyncImage(
    model = page.imageUrl,
    contentDescription = null,
    modifier = Modifier
        .height(180.dp)
        .aspectRatio(0.7f)
        .clip(MaterialTheme.shapes.small),
    contentScale = ContentScale.Crop,
)
```

There is no `clickable`, no fullscreen state, and no zoom/pan UI.

## Implementation Scope

## Part 1: Fix Migration Dialog Dismissal

### Files

```text
app/src/main/java/exh/recs/bestversion/BestVersionCompareScreenModel.kt
app/src/main/java/exh/recs/bestversion/BestVersionCompareScreen.kt
```

### Required Changes

1. Add a dedicated model function:

```kotlin
fun dismissMigrationDialog()
```

Behavior:

- set `selectedBestKey = null`;
- keep `step = BestVersionStep.ComparePreview` when already in preview;
- do not clear candidates, chapters, previews, selected chapter, sample settings, or loaded image URLs.

2. Replace the current fake-key dismiss call:

```kotlin
screenModel.selectBestVersion(MangaIdentityKey(-1, ""))
```

with:

```kotlin
screenModel.dismissMigrationDialog()
```

3. Ensure `AlertDialog.onDismissRequest`, Cancel button, and any outside/back dismissal use the same dismiss action.

4. Guard dialog rendering so it only renders when the selected key resolves to a real target:

```kotlin
val selectedBestKey = state.selectedBestKey
val target = selectedBestKey?.let { key ->
    state.selectedCandidates.find { it.source == key.source && it.url == key.url }
}
if (target != null && state.step == BestVersionStep.ComparePreview) {
    MigrationConfirmDialog(...)
}
```

If `selectedBestKey != null` but `target == null`, the screen should clear it via model action rather than rendering a broken dialog. Avoid calling model mutation directly inside composition without using a safe side effect such as `LaunchedEffect(selectedBestKey)`.

Suggested pattern:

```kotlin
if (state.selectedBestKey != null && target == null) {
    LaunchedEffect(state.selectedBestKey) {
        screenModel.dismissMigrationDialog()
    }
}
```

5. Make `confirmMigration(replace)` clear invalid selection if the target is missing.

Recommended logic:

```kotlin
val origin = originManga ?: run {
    mutableState.update { it.copy(step = BestVersionStep.Error("..."), selectedBestKey = null) }
    return
}
val key = state.value.selectedBestKey ?: return
val target = state.value.selectedCandidates.find { it.source == key.source && it.url == key.url }
if (target == null) {
    dismissMigrationDialog()
    return
}
```

Use existing string resources where practical. Avoid hardcoded user-facing text unless the surrounding file already uses hardcoded fallback errors for internal failures.

## Part 2: Add Fullscreen Page Preview With Zoom/Pan

### Files

```text
app/src/main/java/exh/recs/bestversion/BestVersionCompareScreen.kt
i18n-kmk/src/commonMain/moko-resources/base/strings.xml
```

### UX Requirements

In `ComparePreviewContent`:

- each sampled thumbnail is tappable;
- tapping opens fullscreen preview;
- fullscreen preview shows:
  - the image;
  - optional page number/source title in a minimal top/bottom overlay if it does not clutter the view;
  - a close affordance, or at minimum back/tap-to-close;
- pinch-to-zoom and pan work;
- the preview can be dismissed with back, close, or tap when not zoomed;
- closing returns to `ComparePreviewContent` without refetching or changing state.

### Recommended Compose Approach

Use a local UI state in `BestVersionCompareScreen.Content()` or inside `ComparePreviewContent`:

```kotlin
var fullscreenPage by remember { mutableStateOf<FullscreenPreviewPage?>(null) }
```

Where `FullscreenPreviewPage` is a small private data class in the screen file:

```kotlin
private data class FullscreenPreviewPage(
    val imageUrl: String,
    val pageIndex: Int,
    val mangaTitle: String,
)
```

Pass a callback into `ComparePreviewContent`:

```kotlin
onOpenPagePreview: (FullscreenPreviewPage) -> Unit
```

Then render a fullscreen dialog when non-null:

```kotlin
fullscreenPage?.let { page ->
    FullscreenPagePreviewDialog(
        page = page,
        onDismiss = { fullscreenPage = null },
    )
}
```

This is UI-only state. It does not need to be stored in `BestVersionCompareScreenModel` unless rotation/state restoration is required. Keeping it local avoids overcomplicating the state machine.

### Zoom/Pan Implementation Notes

Prefer a simple Compose implementation using `Modifier.pointerInput` with `detectTransformGestures`.

State:

```kotlin
var scale by remember { mutableFloatStateOf(1f) }
var offset by remember { mutableStateOf(Offset.Zero) }
```

Behavior:

- minimum scale: `1f`;
- maximum scale: `4f` or `5f`;
- when scale returns to `1f`, reset offset to `Offset.Zero`;
- pan should only meaningfully apply while scale > 1f;
- use `graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }`;
- image should use `ContentScale.Fit`, not `Crop`, in fullscreen.

Be careful that single-tap dismissal does not fight with pinch gestures. If tap-to-close becomes unreliable with transform gestures, use an explicit close icon and Android back dismissal. The user asked tap/back/close-like behavior, but stability matters more than clever gesture stacking.

### Thumbnail Content Scale

Keep thumbnails compact. Do not replace the thumbnail row with huge images.

Thumbnail `ContentScale.Crop` is acceptable for compact comparison, but consider `ContentScale.Fit` if cropping makes quality comparison misleading. If changing it, document why. The fullscreen view must use `ContentScale.Fit`.

### Accessibility / Content Description

Add a content description string if the project style expects one for interactive images, for example:

```xml
<string name="best_version_preview_page_content_description">Preview page %1$d from %2$s</string>
<string name="best_version_close_preview">Close preview</string>
```

If existing KMK UI often uses `contentDescription = null` for decorative images, at least ensure the close icon has a content description.

## Part 3: Verify Same-Manga Preselect Setting

### Files

```text
app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt
app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt
app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt
app/src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt
app/src/main/java/exh/recs/bestversion/BestVersionCompareScreenModel.kt
i18n-kmk/src/commonMain/moko-resources/base/strings.xml
```

### Current Behavior To Preserve

Current preference:

```kotlin
fun sameMangaMatchPreselectResults() =
    preferenceStore.getBoolean("same_manga_match_preselect_results", true)
```

The default is `true`. Keep it true.

`CrossExtensionMatchScreenModel` already reads the preference in `updateItem()`.

`BestVersionCompareScreenModel` already reads it through `resolveSettings()` and passes it to `updateCandidate(...)`.

### Required Verification

Claude should verify:

- the setting is visible in Recommendation Settings;
- the title and summary clearly communicate the behavior;
- turning it off causes new same-manga matching searches to start unselected;
- turning it on causes new same-manga matching searches to start selected;
- origin manga remains unselected in both cases;
- normal global search remains unchanged and uncapped.

### Optional Wording Polish

If the current text is clear enough, do not churn strings.

Current strings:

```xml
same_manga_match_preselect_title="Select matches by default"
same_manga_match_preselect_summary="When enabled, same-manga candidates start selected. Origin manga is never selected."
```

These already match the requirement. Only change if needed for clarity.

## Part 4: Defensive Workflow Audit

While touching `BestVersionCompareScreen.kt` and `BestVersionCompareScreenModel.kt`, audit related state paths for small stuck-state bugs.

### Required Checks

1. `selectBestVersion(key)` should only be called from real candidate rows.
2. If a stale selected key no longer resolves, clear it rather than rendering an empty dialog.
3. `confirmMigration(replace = true)` and `confirmMigration(replace = false)` should not leave `isMigrating = true` if the operation fails before launching.
4. Error handling should always transition to either:
   - `BestVersionStep.Error(message)`, or
   - `ComparePreview` with dialog dismissed.
5. Back navigation from fullscreen preview should dismiss fullscreen first, not leave the screen.
6. Back navigation from the migration confirmation dialog should dismiss the dialog first.
7. Back navigation from the main Best Version screen should keep existing behavior.

### Do Not Add In This Pass

Do not add:

- new source search strategies;
- new database tables;
- backup/restore for quality signals;
- automatic best-version re-search;
- source quality ranking UI;
- new migration behavior beyond making Cancel/dismiss safe;
- changes to normal global search;
- changes to the current results-per-source cap.

## Part 5: Tests

### Unit Tests

Add or update tests where the logic can be tested without Compose instrumentation.

Recommended tests:

```text
app/src/test/java/exh/recs/bestversion/BestVersionCompareScreenModelTest.kt
```

If constructing the full `BestVersionCompareScreenModel` is too heavy due Injekt/domain dependencies, extract tiny pure helpers instead of forcing brittle tests.

Potential pure helper:

```kotlin
object BestVersionSelectionPolicy {
    fun shouldShowMigrationDialog(
        selectedBestKey: MangaIdentityKey?,
        selectedCandidates: List<Manga>,
        step: BestVersionStep,
    ): Boolean
}
```

But only add this abstraction if it genuinely simplifies tests. Do not over-abstract just to test a one-line Compose condition.

Useful test cases:

- null selected key -> no dialog;
- real selected key + ComparePreview -> dialog can render;
- invalid selected key + ComparePreview -> should not render, should be clearable;
- `dismissMigrationDialog()` sets selected key to null and leaves preview state intact;
- same-manga preselect default remains true through `SameMangaMatchSettings` tests if applicable.

### Existing Tests To Run

At minimum:

```text
./gradlew :app:testDebugUnitTest --tests "*BestVersion*"
./gradlew :app:testDebugUnitTest --tests "*SameManga*"
```

Preferred:

```text
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

If full unit tests are too slow, document exactly which targeted tests were run and why the full suite was skipped.

### Manual QA Checklist

Test on device/tablet APK:

1. Open a manga.
2. Start "Find best version."
3. Confirm candidates.
4. Select or keep default chapter.
5. Load preview.
6. Tap a thumbnail.
7. Confirm fullscreen opens.
8. Pinch zoom and pan.
9. Close fullscreen and verify preview list remains intact.
10. Tap "Select as best version."
11. Press Cancel.
12. Verify dialog closes and the comparison screen remains usable.
13. Reopen the dialog for the same or another candidate.
14. Press Copy, verify existing copy behavior still works.
15. Repeat and press Migrate, verify existing migration behavior still works.
16. In Recommendation Settings, turn "Select matches by default" off.
17. Start a same-manga workflow and verify candidates start unselected.
18. Turn it back on and verify candidates start selected.

## Versioning

Recommended version:

```text
KMK-Recs v0.7.9
VERSION_CODE = 790
APK: Komikku-v1.13.6-kmk.7.9-debug.apk
```

Rationale:

- v0.7.8 introduced Best Version.
- This pass is a direct Best Version follow-up.
- It should install over the v0.7.8 APK because `VERSION_CODE` increases from 780 to 790.

If Claude decides this should be a patch build instead, use:

```text
KMK-Recs v0.7.8.1
VERSION_CODE = 781
APK: Komikku-v1.13.6-kmk.7.8.1-debug.apk
```

But the preferred label for simplicity is v0.7.9.

## Documentation Updates Required After Implementation

Claude must update or create:

```text
docs/recommendations/KMK_RECS_V0_7_9_BEST_VERSION_DIALOG_AND_PREVIEW_POLISH_IMPLEMENTATION.md
docs/recommendations/CURRENT_STATE.md
docs/recommendations/README.md
docs/recommendations/NEXT_WORK.md
RECOMMENDATION_VERSIONING.md
```

Implementation report must include:

- date;
- final version/build label;
- files changed;
- behavior changed;
- tests run;
- APK output path;
- known limitations;
- deviations from this plan.

## Expected Files Changed By Implementation

Likely files:

```text
app/src/main/java/exh/recs/bestversion/BestVersionCompareScreen.kt
app/src/main/java/exh/recs/bestversion/BestVersionCompareScreenModel.kt
i18n-kmk/src/commonMain/moko-resources/base/strings.xml
app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt
docs/recommendations/CURRENT_STATE.md
docs/recommendations/README.md
docs/recommendations/NEXT_WORK.md
RECOMMENDATION_VERSIONING.md
```

Possible test files:

```text
app/src/test/java/exh/recs/bestversion/BestVersionCompareScreenModelTest.kt
app/src/test/java/exh/recs/bestversion/BestVersionSelectionPolicyTest.kt
app/src/test/java/exh/recs/matching/SameMangaMatchSettingsTest.kt
```

Do not modify unrelated source evaluation, recommendation fit, Loved Manga, JSON export/import, or extension installer code for this pass.

## Success Criteria

The implementation is successful when:

- Cancel on the Best Version migration confirmation closes the dialog reliably.
- No fake `MangaIdentityKey(-1, "")` sentinel remains in the Best Version dialog path.
- Invalid selected-best keys cannot trap the UI in a broken dialog.
- Sampled page thumbnails can open fullscreen.
- Fullscreen page preview supports pinch-to-zoom and pan.
- Closing fullscreen returns to the comparison screen with previews still loaded.
- Same-manga match results still default to selected.
- The existing setting can still switch same-manga workflows to manual selection.
- Normal global search remains unchanged.
- Targeted Best Version / Same Manga tests pass.
- Debug APK builds and can update over v0.7.8.

