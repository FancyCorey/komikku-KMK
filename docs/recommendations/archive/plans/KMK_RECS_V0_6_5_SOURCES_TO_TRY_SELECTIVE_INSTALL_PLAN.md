# KMK-Recs v0.6.5 Sources To Try Selective Install Plan

Status: planning. Do not implement until the user explicitly approves or provides this plan to Claude for implementation.

Target version: `KMK-Recs v0.6.5`

## Purpose

Add manual multi-select install support to `Recommendation Settings > Sources To Try`.

The existing `KMK-Recs v0.6.3` feature added `Install visible suggestions`, and `KMK-Recs v0.6.4` fixes that bulk install flow so it installs all visible suggestions reliably. However, the user also wants a more controlled workflow:

- choose only specific suggested sources,
- press one button,
- install only the selected suggestions.

This feature should preserve `Install visible suggestions` as the quick action while adding a selective install mode for cases where the visible suggestions contain a mixture of wanted and unwanted extensions.

## Versioning

Use:

`KMK-Recs v0.6.5`

Reason:

- This revisits the same v0.6 extension/source recommendation system.
- It builds directly on Sources To Try, source preferences, and bulk install behavior.
- It should not jump to a new major topic number.

Expected debug APK naming pattern:

`Komikku-v1.13.6-kmk.6.5-debug.apk`

Only use this exact APK name if source metadata confirms it is the next correct version.

## Current Behavior

In:

`app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt`

The Sources To Try section currently computes:

```kotlin
val visibleSuggestions = if (state.suggestionsExpanded) state.nonInstalledSuggestions
else state.nonInstalledSuggestions.take(5)
```

Rows are rendered with `SourceSuggestionItem`.

The bulk action calls:

```kotlin
screenModel.installSuggestions(visibleSuggestions)
```

In:

`app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt`

The screen model currently tracks:

- `nonInstalledSuggestions`
- `suggestionsExpanded`
- `likedSourceKeys`
- `dislikedSourceKeys`
- `installingSuggestionKeys`
- `isBulkInstallingSuggestions`

After `v0.6.4`, `installSuggestions(...)` should be fixed to use a safe install lifecycle.

## Required User Experience

Add a clear manual selection mode for Sources To Try.

Recommended UX:

1. Keep the existing `Install visible suggestions (N)` button.
2. Add a `Select` action near the Sources To Try bulk install controls.
3. Tapping `Select` enters selection mode.
4. In selection mode:
   - each visible suggestion row shows a checkbox,
   - selected rows are visually clear,
   - the user can select/deselect individual suggestions,
   - an `Install selected (N)` button installs only selected rows,
   - a `Cancel` button exits selection mode and clears selection.
5. After `Install selected (N)` is tapped:
   - selected suggestions begin installing through the same fixed `installSuggestions(...)` path,
   - selection mode exits or clears once the install starts,
   - rows being installed remain disabled via `installingSuggestionKeys`.

Important:

- Do not rely only on long-press. Long-press is hidden and easy to miss.
- A long-press shortcut is optional, but an explicit `Select` button should exist.
- Do not remove `Install visible suggestions`.

## Non-Goals

Do not change:

- Sources To Try scoring,
- non-installed source suggestion generation,
- source like/dislike semantics,
- source dismissal behavior,
- source priority ordering,
- For You recommendation generation,
- normal Extensions tab install/update behavior,
- normal global search behavior.

Do not add:

- a full source management screen,
- drag/reorder for Sources To Try,
- selection persistence across app restarts,
- automatic install of hidden/collapsed suggestions unless they are visible and selected.

## State Model Changes

In:

`app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt`

Extend `State` with:

```kotlin
val isSuggestionSelectionMode: Boolean = false
val selectedSuggestionKeys: ImmutableSet<String> = persistentSetOf()
```

Use `dismissalKey` as the stable selection key, because it is already used for suggestion identity and install tracking.

Add screen model actions:

```kotlin
fun enterSuggestionSelectionMode()
fun exitSuggestionSelectionMode()
fun toggleSuggestionSelected(suggestion: NonInstalledSourceSuggestion)
fun clearSelectedSuggestions()
fun installSelectedSuggestions(suggestions: List<NonInstalledSourceSuggestion>)
```

Recommended behavior:

- `enterSuggestionSelectionMode()` sets `isSuggestionSelectionMode = true`.
- `exitSuggestionSelectionMode()` sets `isSuggestionSelectionMode = false` and clears `selectedSuggestionKeys`.
- `toggleSuggestionSelected(...)` toggles the suggestion's `dismissalKey`.
- `clearSelectedSuggestions()` clears `selectedSuggestionKeys`.
- `installSelectedSuggestions(visibleSuggestions)` filters visible suggestions to selected keys, calls `installSuggestions(selected)`, then clears/exits selection mode.

Guard behavior:

- If `isBulkInstallingSuggestions` is true, selection controls should be disabled.
- If selected count is zero, `Install selected` should be disabled.
- If a selected suggestion is already installing, it should either be ignored or filtered out before calling `installSuggestions`.

## UI Changes

In:

`app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt`

### Sources To Try Controls

Keep the existing bulk install button:

- normal mode: `Install visible suggestions (N)`
- installing mode: existing installing label

Add next to it:

- `Select`, when not in selection mode,
- `Cancel`, when in selection mode.

In selection mode, add:

- `Install selected (N)` button,
- disabled when `N == 0` or bulk install is active.

Recommended layout:

- Use a compact `Row` or `FlowRow` under the suggestion list.
- Avoid crowded buttons if screen width is narrow.
- If there are three buttons in selection mode, use:
  - `Install selected (N)` as primary,
  - `Cancel` as text/outlined action.

### SourceSuggestionItem

Extend `SourceSuggestionItem` parameters:

```kotlin
selectionMode: Boolean = false
selected: Boolean = false
onToggleSelected: () -> Unit = {}
```

When `selectionMode` is true:

- show a checkbox at the start or end of the row,
- clicking the checkbox toggles selection,
- optionally clicking the card row toggles selection, but do not accidentally trigger install/dismiss/like/dislike actions.

Recommended:

- Use Material checkbox if already available.
- Keep Install, Dismiss, Like, and Dislike controls visible unless the row becomes too crowded.
- If crowded, selection mode may hide per-row Install/Dismiss buttons and keep Like/Dislike only if layout remains clean.

Preferred selection-mode row behavior:

- show checkbox,
- show source title/details/reasons,
- keep like/dislike optional but not required,
- hide individual `Install` button during selection mode to avoid two competing install paths,
- keep `Dismiss` hidden or secondary during selection mode to avoid accidental removal.

Claude should choose the cleanest Compose layout consistent with the existing UI.

## String Resources

Add localized strings in the same string files used for v0.6.3/v0.6.4 recommendation strings.

Suggested keys:

```xml
<string name="rec_suggestion_select">Select</string>
<string name="rec_suggestion_cancel_selection">Cancel</string>
<string name="rec_suggestion_install_selected">Install selected (%1$d)</string>
<string name="rec_suggestion_selected_count">%1$d selected</string>
<string name="rec_suggestion_select_source">Select source</string>
```

Use existing localization conventions and generated resources if this project uses moko resources.

## Interaction With v0.6.4 Bulk Install Fix

This feature depends on the corrected `installSuggestions(...)` implementation from v0.6.4.

Claude must verify the current implementation before coding:

- If `v0.6.4` has not been implemented yet, implement/fix that first or stop and report that the dependency is missing.
- `installSelectedSuggestions(...)` must call the same safe `installSuggestions(...)` path.
- Do not create a second install queue or duplicate installer logic.

The install lifecycle must continue to respect:

- `installingSuggestionKeys`,
- `isBulkInstallingSuggestions`,
- terminal install states,
- cancellation/error cleanup,
- Android installer modes.

## Edge Cases

Handle:

- suggestion list changes while selection mode is active,
- selected suggestion disappears after install/dismiss/recompute,
- collapsed vs expanded list,
- user expands/collapses while selection mode is active,
- user taps Select with no visible suggestions,
- user taps Install selected with zero selected items,
- user starts individual install while selection mode is active,
- bulk install already running.

Recommended behavior:

- Selection applies only to currently visible suggestions.
- When suggestions recompute, stale selected keys should be ignored.
- `Install selected` should only install selected suggestions that are still in `visibleSuggestions`.
- Exiting selection mode clears selection.

## Tests

Add tests if practical.

Preferred focused tests:

1. Enter selection mode sets `isSuggestionSelectionMode = true`.
2. Exit selection mode clears `selectedSuggestionKeys`.
3. Toggle suggestion selection adds/removes `dismissalKey`.
4. Install selected filters only selected visible suggestions.
5. Install selected ignores selected keys not present in the visible list.
6. Install selected does nothing with zero selected suggestions.
7. Selection controls do not alter like/dislike/dismiss preferences.

If screen model tests are hard because of dependencies, document why and test any extracted pure helper.

Run the existing test suite and document results.

## Manual QA

After building the APK:

1. Open Browse.
2. Open Recommendation Settings.
3. Scroll to Sources To Try.
4. Confirm `Install visible suggestions` still exists.
5. Tap `Select`.
6. Confirm rows show selectable controls.
7. Select one suggestion and confirm `Install selected (1)`.
8. Select multiple suggestions and confirm the count updates.
9. Deselect a suggestion and confirm the count decreases.
10. Tap `Cancel` and confirm selection mode exits and clears.
11. Enter selection mode again, select multiple suggestions, tap `Install selected`.
12. Confirm only selected suggestions begin installing.
13. Confirm unselected visible suggestions do not install.
14. Confirm all installing selected rows are disabled.
15. Confirm `Install visible suggestions` still installs all visible suggestions when not in selection mode.
16. Confirm normal single-row install still works outside selection mode.

## Documentation Updates

Claude must update documentation after implementation.

Required:

- Create implementation report:
  - `docs/recommendations/KMK_RECS_V0_6_5_SOURCES_TO_TRY_SELECTIVE_INSTALL_IMPLEMENTATION.md`
- Update:
  - `docs/recommendations/CURRENT_STATE.md`
  - `docs/recommendations/NEXT_WORK.md`
  - `docs/recommendations/README.md`
  - `RECOMMENDATION_VERSIONING.md`
- Update release notes:
  - `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`

Documentation should explicitly mention:

- v0.6.5 adds manual selected install for Sources To Try,
- v0.6.4 fixed visible bulk install reliability,
- `Install visible suggestions` remains available,
- selection mode is temporary UI state and not persisted,
- tests run,
- APK produced.

## Acceptance Criteria

This implementation is complete only when:

- Sources To Try has a visible/manual way to enter selection mode.
- User can select multiple visible suggestions.
- User can install exactly selected suggestions in one action.
- `Install visible suggestions` remains available and unchanged.
- Selection mode can be cancelled cleanly.
- Selection state clears after cancel or install.
- Bulk install state and per-row installing state remain correct.
- No duplicate install batches can be started.
- Existing source like/dislike/dismiss behavior still works.
- Existing tests pass or failures are documented clearly.
- Documentation and release notes are updated to `KMK-Recs v0.6.5`.
- A debug APK is produced using the current project versioning rules.

