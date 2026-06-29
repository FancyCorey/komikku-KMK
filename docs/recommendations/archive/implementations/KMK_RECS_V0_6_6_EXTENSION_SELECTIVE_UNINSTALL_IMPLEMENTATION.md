# KMK-Recs v0.6.6 Extension Selective Uninstall Implementation

Date: 2026-06-19

Status: implemented as KMK-Recs v0.6.6.

## What Was Implemented

Selective uninstall mode for Browse > Extensions. Users can enter a selection mode, check multiple installed/untrusted extensions, and uninstall all selected extensions after a single confirmation dialog.

## Entry Point

A new "Select extensions" overflow action was added to the Extensions tab app bar. Tapping it calls `enterExtensionSelectionMode()` in the screen model. If selection mode is already active, the tap is ignored (no double-enter).

## Screen Model Changes (`ExtensionsScreenModel.kt`)

### State additions

```kotlin
val isExtensionSelectionMode: Boolean = false,
val selectedExtensionKeys: Set<String> = emptySet(),
val isBulkUninstallingExtensions: Boolean = false,
```

### Helper

```kotlin
private fun Extension.selectionKey(): String = pkgName + "_${signatureHash}"
```

Same identity style already used for download/install tracking in `addDownloadState` / `removeDownloadState`.

### Actions

**`enterExtensionSelectionMode()`** — sets `isExtensionSelectionMode = true`.

**`exitExtensionSelectionMode()`** — clears `isExtensionSelectionMode` and `selectedExtensionKeys` atomically.

**`toggleExtensionSelected(extension)`** — ignores `Extension.Available`; toggles `selectionKey()` in `selectedExtensionKeys` for installed/untrusted extensions.

**`uninstallSelectedExtensions()`** — guards against overlapping bulk batches (`isBulkUninstallingExtensions`). Filters `state.value.items` to selected, installed/untrusted, idle (not actively installing) extensions. Fires `uninstallExtension()` for each with 300ms delay between calls to avoid rapid-fire Android intent stacking. Uses `try/finally` to always exit selection mode and clear state.

### Uninstall execution strategy

`ExtensionManager.uninstallExtension()` fires an `ACTION_UNINSTALL_PACKAGE` Android intent. There is no completion callback. The 300ms delay between calls reduces rapid-fire prompt stacking without requiring any callback tracking. Android will show one uninstall confirmation dialog per extension. The feature documents this clearly in the confirmation dialog copy.

The `finally` block clears all selection state after the loop exits, whether all uninstalls succeed, some fail, or the coroutine is cancelled.

## UI Changes

### `ExtensionsTab.kt`

- Added `var showBulkUninstallConfirmDialog by remember { mutableStateOf(false) }` local state.
- Added "Select extensions" overflow action (calls `enterExtensionSelectionMode()` if not already in selection mode).
- Replaced the existing `BackHandler(enabled = state.searchQuery != null)` with a combined handler: back in selection mode exits selection mode; back with search query clears search. Priority: selection mode > search query.
- Wired `onToggleExtensionSelected`, `onRequestUninstallSelected`, `onExitSelectionMode` into `ExtensionScreen`.
- Added `ExtensionBulkUninstallConfirmation` dialog shown when `showBulkUninstallConfirmDialog` is true.
- Added `ExtensionBulkUninstallConfirmation` composable: title = "Uninstall selected extensions?", body shows count and informs user Android may ask per-extension, confirm = "Uninstall", dismiss = "Cancel".

### `ExtensionsScreen.kt`

New params on `ExtensionScreen` (all with defaults so existing call sites not broken):
- `onToggleExtensionSelected: (Extension) -> Unit`
- `onRequestUninstallSelected: () -> Unit`
- `onExitSelectionMode: () -> Unit`

Same params added to `ExtensionContent` (private).

**Selection controls item** — injected into the `LazyColumn` immediately after the "Installed" header when `state.isExtensionSelectionMode` is true:
- "Uninstall selected (N)" `Button` — disabled when N = 0 or bulk uninstall running.
- "Cancel" `OutlinedButton` — disabled when bulk uninstall running.

**`ExtensionItem` changes** — 4 new params: `selectionMode`, `selectable`, `selected`, `onToggleSelected` (all default-valued).

- `selectable = true` when `selectionMode && (Installed || Untrusted) && installStep.isCompleted()`.
- When `selectable`: clicking the row calls `onToggleSelected` instead of the normal open/install/trust action.
- When `selectable`: icon slot shows a `Checkbox` (40dp box) instead of the extension icon + progress indicator.
- When `selectionMode` (regardless of selectable): action buttons (Settings, Update, Install, Trust, Cancel) are hidden to prevent conflicts. Long-click remains wired as before (does not conflict since long-click is the existing per-extension uninstall shortcut).
- Available extensions (`selectable = false`): show normally with no checkbox.

### String resources added

```xml
<string name="extension_select_extensions">Select extensions</string>
<string name="extension_uninstall_selected">Uninstall selected (%1$d)</string>
<string name="extension_cancel_selection">Cancel</string>
<string name="extension_uninstall_selected_title">Uninstall selected extensions?</string>
<string name="extension_uninstall_selected_message">This will uninstall %1$d extensions. Android may ask you to confirm each uninstall.</string>
```

## Edge Cases Handled

- **Available extension rows**: not selectable, no checkbox shown, normal display.
- **Active install/update rows**: `installStep.isCompleted() == false` → excluded from selectable, existing CircularProgressIndicator shown normally.
- **Stale selected keys**: `uninstallSelectedExtensions()` filters against current `state.items` — stale keys for hidden/uninstalled extensions are automatically ignored.
- **Back press in selection mode**: exits selection mode before clearing search query.
- **Cancel in dialog**: only closes dialog, does not clear selection (user may want to try again).
- **Zero selected on confirm**: guarded by disabled state on the "Uninstall selected (0)" button.
- **Duplicate bulk batch**: `isBulkUninstallingExtensions` guard prevents overlapping calls.
- **Coroutine cancellation**: `finally` block in `uninstallSelectedExtensions()` always clears selection state.
- **Android uninstall prompt**: one dialog per extension; user must confirm each separately. Document says "Android may ask you to confirm each uninstall."

## What Was Not Changed

- Normal long-press uninstall behavior — unchanged.
- Single-extension uninstall from `onUninstallExtension` — unchanged.
- Trust extension flow (dialog) — unchanged.
- Install / update / update-all — unchanged.
- For You, Recommendation Settings, Sources To Try — unchanged.

## Tests

No new unit tests added. `ExtensionsScreenModel` depends on `Injekt` DI (Android `Application`, `ExtensionManager`, `GetExtensionsByType`), making unit testing of the new actions impractical without a test harness. The filtering logic in `uninstallSelectedExtensions()` is straightforward (type check + key set membership + `installStep.isCompleted()`).

Existing test suite:
- `:app:testDebugUnitTest --offline` → BUILD SUCCESSFUL, all tests PASSED.

## Files Changed

- `app/src/main/java/eu/kanade/tachiyomi/ui/browse/extension/ExtensionsScreenModel.kt` — 3 new state fields, helper, 4 new actions
- `app/src/main/java/eu/kanade/tachiyomi/ui/browse/extension/ExtensionsTab.kt` — overflow action, BackHandler update, confirmation dialog, wiring
- `app/src/main/java/eu/kanade/presentation/browse/ExtensionsScreen.kt` — new params, selection controls item, checkbox in ExtensionItem, hide actions in selection mode
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` — 5 new strings
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` — VERSION_CODE=606

## Commands Run

```text
./gradlew :app:compileDebugKotlin --offline → BUILD SUCCESSFUL
./gradlew :app:testDebugUnitTest --offline → BUILD SUCCESSFUL, all tests PASSED
./gradlew :app:assembleDebug --offline → BUILD SUCCESSFUL
```

## APK

`Komikku-v1.13.6-kmk.6.6-debug.apk` (universal, 133 MB)
