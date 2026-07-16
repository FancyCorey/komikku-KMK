# KMK-Recs v0.6.6 Extension Selective Uninstall Plan

Status: planning. Do not implement until the user explicitly approves or provides this plan to Claude for implementation.

Target version: `KMK-Recs v0.6.6`

## Purpose

Add multi-select uninstall support to the normal `Browse > Extensions` page.

The `KMK-Recs v0.6.5` Sources To Try feature added selection mode for installing selected suggested sources. The user now wants a similar quality-of-life workflow for deleting/uninstalling multiple installed extensions from the Extensions page.

This should make extension cleanup easier without changing normal extension install/update behavior.

## Versioning

Use:

`KMK-Recs v0.6.6`

Reason:

- This continues the v0.6 extension/source management track.
- It is related to extension install/select UX, but applies to the normal Extensions page instead of Sources To Try.
- Do not jump to a new major topic number.

Expected debug APK naming pattern:

`Komikku-v1.13.6-kmk.6.6-debug.apk`

Only use this exact name if source metadata confirms it is the next correct version.

## Current Code Context

Primary files:

- `app/src/main/java/eu/kanade/tachiyomi/ui/browse/extension/ExtensionsScreenModel.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/browse/extension/ExtensionsTab.kt`
- `app/src/main/java/eu/kanade/presentation/browse/ExtensionsScreen.kt`
- `app/src/main/java/eu/kanade/tachiyomi/extension/ExtensionManager.kt`
- `app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionInstaller.kt`

Current uninstall entry points:

- `ExtensionsScreenModel.uninstallExtension(extension)`
- `ExtensionManager.uninstallExtension(extension)`
- `ExtensionInstaller.uninstallApk(pkgName)`

Current Extensions page behavior:

- Long-press on an installed extension currently uninstalls it if Android reports the package is installed.
- Long-press on a private/non-package extension opens the existing private extension confirmation path.
- Installed extension rows currently do not have a selection mode.
- The Extensions page already has `Update all` for pending updates.

## Important Technical Constraint

Bulk uninstall is not the same as bulk install/update.

Installing/updating extensions can use Komikku's extension install flow and installer queue. Uninstalling normal extension APKs usually starts Android's uninstall UI through an `ACTION_UNINSTALL_PACKAGE` intent.

If the app fires multiple uninstall intents at once, Android may:

- stack prompts badly,
- ignore later prompts,
- interrupt the current prompt,
- behave differently across devices/ROMs.

Therefore, this feature must not blindly call uninstall on every selected extension in a tight loop.

## Required User Experience

Add a clear manual selection mode to `Browse > Extensions`.

Recommended UX:

1. Add a `Select` action to the Extensions tab overflow menu.
2. Tapping `Select` enters extension selection mode.
3. In selection mode:
   - installed/untrusted extension rows show a checkbox,
   - available/non-installed extension rows are not selectable,
   - user can select/deselect multiple installed/untrusted extensions,
   - app bar or list controls show `Uninstall selected (N)`,
   - `Cancel` exits selection mode and clears selection.
4. Tapping `Uninstall selected (N)` shows one confirmation dialog.
5. Confirmation dialog should clearly say how many extensions will be uninstalled.
6. After confirmation:
   - selected extensions are uninstalled in a controlled/sequential way,
   - selection mode exits or clears,
   - normal Extensions page behavior remains intact.

Do not rely only on long-press. The user should have an explicit visible way to enter selection mode.

## Scope Rules

Selectable:

- `Extension.Installed`
- `Extension.Untrusted`

Not selectable:

- `Extension.Available`
- extensions currently downloading/installing/updating/uninstalling if such state exists
- items with active non-idle install/update state

Rationale:

- Available extensions are not installed and cannot be uninstalled.
- Active installs/updates should not be interrupted by a batch uninstall feature.

## State Model Changes

In:

`app/src/main/java/eu/kanade/tachiyomi/ui/browse/extension/ExtensionsScreenModel.kt`

Extend `State` with:

```kotlin
val isExtensionSelectionMode: Boolean = false
val selectedExtensionKeys: Set<String> = emptySet()
val isBulkUninstallingExtensions: Boolean = false
```

Use the same identity style already used for extension install/update tracking:

```kotlin
extension.pkgName + "_${extension.signatureHash}"
```

Add helper:

```kotlin
private fun Extension.selectionKey(): String =
    pkgName + "_${signatureHash}"
```

If `signatureHash` is unavailable on some extension subtype, use the exact properties available in the model and match the existing package/signature identity style used by this fork.

## Screen Model Actions

Add:

```kotlin
fun enterExtensionSelectionMode()
fun exitExtensionSelectionMode()
fun toggleExtensionSelected(extension: Extension)
fun uninstallSelectedExtensions(extensions: List<Extension>)
```

Behavior:

- `enterExtensionSelectionMode()` sets `isExtensionSelectionMode = true`.
- `exitExtensionSelectionMode()` clears selection and sets `isExtensionSelectionMode = false`.
- `toggleExtensionSelected(extension)` only works for `Extension.Installed` and `Extension.Untrusted`.
- `uninstallSelectedExtensions(extensions)` filters to currently visible selected installed/untrusted extensions, then starts controlled uninstall flow.

Guard behavior:

- If `isBulkUninstallingExtensions` is true, ignore new uninstall batch requests.
- Ignore selected keys that are not currently visible.
- Ignore available/non-installed extensions.
- Do not uninstall active install/update rows where `installStep.isCompleted()` is false.

## Uninstall Execution Strategy

Use a conservative sequential strategy.

Recommended implementation:

```kotlin
fun uninstallSelectedExtensions(extensions: List<Extension>) {
    val toUninstall = extensions
        .filter { it is Extension.Installed || it is Extension.Untrusted }
        .filter { it.selectionKey() in state.value.selectedExtensionKeys }

    if (toUninstall.isEmpty()) return

    mutableState.update { it.copy(isBulkUninstallingExtensions = true) }
    screenModelScope.launchIO {
        try {
            for (extension in toUninstall) {
                uninstallExtension(extension)
                delay(SAFE_UNINSTALL_PROMPT_DELAY)
            }
        } finally {
            mutableState.update {
                it.copy(
                    isBulkUninstallingExtensions = false,
                    isExtensionSelectionMode = false,
                    selectedExtensionKeys = emptySet(),
                )
            }
        }
    }
}
```

However, Claude must inspect current Android uninstall behavior before finalizing this.

Important:

- If `uninstallExtension()` launches Android package uninstall UI for normal APK extensions, a simple delay may not be enough to guarantee prompt-by-prompt completion.
- If completion callbacks are unavailable, document this limitation clearly.
- Avoid trying to implement a complex uninstall result receiver unless the existing code already supports it.
- The first implementation can reasonably be "fire sequential uninstall intents with a small delay" if that matches Android behavior well enough, but Claude should choose the safest approach supported by current code.

Alternative safer approach:

- For normal installed APK extensions, show the confirmation dialog and then start uninstall prompts one at a time with a delay.
- For private/internal extensions, uninstall directly.
- If Android prompt queuing proves unreliable, fall back to selected batch confirmation followed by sequential prompts, clearly documenting that the user may need to confirm each Android uninstall prompt.

## UI Changes

### ExtensionsTab

In:

`app/src/main/java/eu/kanade/tachiyomi/ui/browse/extension/ExtensionsTab.kt`

Add an overflow action:

- `Select extensions`

Only show or enable it when not already in selection mode and there are installed/untrusted extensions visible.

When in selection mode:

- Back press should exit selection mode before closing search/navigation.
- Existing search back behavior should remain sensible.

Potential order:

1. If selection mode active, back exits selection mode.
2. Else if search query active, back clears search.
3. Else normal behavior.

### ExtensionsScreen

In:

`app/src/main/java/eu/kanade/presentation/browse/ExtensionsScreen.kt`

Extend `ExtensionScreen` and `ExtensionContent` parameters to receive:

```kotlin
isSelectionMode: Boolean
selectedExtensionKeys: Set<String>
isBulkUninstallingExtensions: Boolean
onEnterSelectionMode: () -> Unit
onExitSelectionMode: () -> Unit
onToggleExtensionSelected: (Extension) -> Unit
onUninstallSelectedExtensions: (List<Extension>) -> Unit
```

Claude may simplify this interface if state/actions can be passed more cleanly from `ExtensionsTab`.

### Selection Controls

Add selection controls at a natural location:

- header action for installed section, or
- top of list as a small action row, or
- app bar action passed from `ExtensionsTab`.

Recommended list control row in selection mode:

- `Uninstall selected (N)` primary button
- `Cancel` text/outlined button

The selected count should be computed from visible installed/untrusted extensions matching selected keys.

### Row UI

In:

`ExtensionItem`

Add optional selection params:

```kotlin
selectionMode: Boolean = false
selected: Boolean = false
selectable: Boolean = false
onToggleSelected: () -> Unit = {}
```

When selection mode is active and row is selectable:

- show a checkbox,
- tapping checkbox toggles selection,
- tapping row may toggle selection instead of opening extension details,
- hide or disable normal row action buttons if they conflict with selection.

When selection mode is active and row is not selectable:

- do not show checkbox,
- keep row visually normal or slightly disabled,
- do not allow selecting available extensions.

Recommended:

- installed/untrusted rows: checkbox visible.
- available rows: no checkbox and normal install button may be hidden during selection mode to avoid mixed actions.
- active install/update rows: not selectable.

## Confirmation Dialog

Add a dialog before uninstalling selected extensions.

Suggested state can be local Compose state in `ExtensionsTab` or screen model state.

Dialog content:

- Title: `Uninstall selected extensions?`
- Body: `This will uninstall N extensions. Android may ask you to confirm each uninstall.`
- Optional list first 3-5 extension names if not too crowded.
- Confirm: `Uninstall`
- Cancel: existing cancel string.

Use existing strings where available:

- `MR.strings.ext_uninstall`
- `MR.strings.action_cancel`

Add KMK strings for new messages.

## String Resources

Add strings in the KMK resource file used by previous recommendation/extension additions:

Suggested keys:

```xml
<string name="extension_select">Select</string>
<string name="extension_cancel_selection">Cancel</string>
<string name="extension_uninstall_selected">Uninstall selected (%1$d)</string>
<string name="extension_select_extensions">Select extensions</string>
<string name="extension_uninstall_selected_title">Uninstall selected extensions?</string>
<string name="extension_uninstall_selected_message">This will uninstall %1$d extensions. Android may ask you to confirm each uninstall.</string>
<string name="extension_select_extension">Select extension</string>
```

Use existing project naming conventions if similar strings already exist.

## Edge Cases

Handle:

- selected extension disappears because it was uninstalled externally,
- user changes search while selection mode is active,
- user refreshes extension list while selected keys exist,
- an extension is updating/installing and should not be selectable,
- user selects extensions across multiple headers/languages,
- untrusted extensions selected with installed extensions,
- user presses back during selection mode,
- user taps uninstall selected with zero selected items,
- user starts selection mode while update/install is active.

Recommended behavior:

- Selected keys are stale-tolerant and filtered against currently visible items at uninstall time.
- Cancel clears selection.
- Search should not necessarily clear selection, but uninstall should only apply to currently visible selected items unless Claude chooses a clearer "selected across current list" behavior.
- Keep implementation simple: selection applies to visible items in the current filtered list.

## Tests

Add tests where practical.

Preferred tests:

1. Selection mode enter/exit updates state.
2. Exit clears selected keys.
3. Toggle selected only works for installed/untrusted extensions.
4. Toggle selected ignores available extensions.
5. Uninstall selected filters only visible selected uninstallable extensions.
6. Uninstall selected ignores active install/update rows if that state is available to the filtering function.
7. Duplicate uninstall batch guard prevents overlapping calls.

If UI/screen model tests are impractical due to DI and Compose structure:

- extract pure filtering helper where possible,
- test that helper,
- document any untested UI-only behavior in the implementation markdown.

Run existing tests and document results.

## Manual QA

After building APK:

1. Open Browse > Extensions.
2. Confirm normal install/update/open behavior still works outside selection mode.
3. Open overflow menu and tap `Select extensions`.
4. Confirm installed/untrusted rows show checkboxes.
5. Confirm available extension rows are not selectable.
6. Select one installed extension and confirm button says `Uninstall selected (1)`.
7. Select multiple installed extensions and confirm count updates.
8. Deselect an extension and confirm count decreases.
9. Tap Cancel and confirm selection clears.
10. Enter selection mode again, select multiple extensions, tap Uninstall selected.
11. Confirm dialog appears before uninstall begins.
12. Confirm Android uninstall prompts appear in a controlled order.
13. Confirm cancelled Android uninstall does not crash the app.
14. Confirm selected state clears after batch starts/completes.
15. Confirm update-all still works.
16. Confirm pull-to-refresh still works.

## Documentation Updates

Claude must update documentation after implementation.

Required:

- Create implementation report:
  - `docs/recommendations/KMK_RECS_V0_6_6_EXTENSION_SELECTIVE_UNINSTALL_IMPLEMENTATION.md`
- Update:
  - `docs/recommendations/CURRENT_STATE.md`
  - `docs/recommendations/NEXT_WORK.md`
  - `docs/recommendations/README.md`
  - `RECOMMENDATION_VERSIONING.md`
- Update release notes:
  - `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`

Documentation should explicitly mention:

- v0.6.6 adds selected uninstall for the normal Extensions page,
- v0.6.5 added selected install for Sources To Try,
- normal install/update behavior is unchanged,
- Android may still show one uninstall confirmation per extension,
- tests run,
- APK produced.

## Acceptance Criteria

This implementation is complete only when:

- Extensions page has an explicit selection mode.
- Installed/untrusted extensions can be selected.
- Available/non-installed extensions are not selected for uninstall.
- User can uninstall selected extensions after a confirmation dialog.
- Selection mode can be cancelled cleanly.
- Selected state clears after cancel or uninstall start/completion.
- Normal extension install/update/open/trust behavior still works.
- Update all still works.
- Existing tests pass or failures are clearly documented.
- Documentation and release notes are updated to `KMK-Recs v0.6.6`.
- A debug APK is produced using current versioning rules.

