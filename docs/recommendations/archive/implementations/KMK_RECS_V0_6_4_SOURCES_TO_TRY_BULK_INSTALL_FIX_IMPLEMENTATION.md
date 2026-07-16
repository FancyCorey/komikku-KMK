# KMK-Recs v0.6.4 Sources To Try Bulk Install Fix Implementation

Date: 2026-06-19

Status: implemented as KMK-Recs v0.6.4.

## Problem

In v0.6.3, "Install visible suggestions" stopped after the first one or two extensions. The same bug affected individual Install buttons: the install-in-progress indicator would sometimes not clear.

Root cause: both `installSuggestion()` and `installSuggestions()` called `extensionManager.installExtension(suggestion.extension).collect {}`. The `installExtension()` flow emits `InstallStep` values (Pending â†’ Downloading â†’ Installing â†’ Installed) but does NOT terminate after `Installed` â€” it continues emitting (or stays open). Raw `.collect {}` blocks indefinitely, so in the bulk loop the first extension's coroutine never returned to the `for` loop body, and later extensions never started.

## Fix

Adapted the proven pattern from `ExtensionsScreenModel.collectToInstallUpdate()`:

```kotlin
// ExtensionsScreenModel (upstream reference)
private suspend fun Flow<InstallStep>.collectToInstallUpdate(extension: Extension) =
    this
        .onEach { installStep -> addDownloadState(extension, installStep) }
        .takeWhile { installStep -> installStep != InstallStep.Installed }
        .onCompletion { removeDownloadState(extension) }
        .collect()
```

The key: `.takeWhile { ... }` terminates collection when a terminal step is reached. Without it, `.collect()` never returns.

For our case we use `InstallStep.isCompleted()` (returns true for `Installed`, `Error`, or `Idle`) instead of checking only `Installed`, so any terminal state â€” including errors â€” ends collection and lets the loop advance:

```kotlin
extensionManager.installExtension(suggestion.extension)
    .takeWhile { !it.isCompleted() }
    .collect()
```

### `installSuggestion()` change

Before:
```kotlin
extensionManager.installExtension(suggestion.extension).collect {}
```

After:
```kotlin
extensionManager.installExtension(suggestion.extension)
    .takeWhile { !it.isCompleted() }
    .collect()
```

The `finally` block that clears `installingSuggestionKeys` was already correct.

### `installSuggestions()` changes

1. Guard against overlapping bulk batches: `if (state.value.isBulkInstallingSuggestions) return` at the top.
2. Replaced `.collect {}` with `.takeWhile { !it.isCompleted() }.collect()` inside the per-suggestion `try`.
3. Added explicit `CancellationException` rethrow so bulk coroutine cancellation propagates correctly.
4. Moved per-suggestion `installingSuggestionKeys` cleanup into a `finally` block (was missing; cleanup relied on normal exit only).
5. Moved `isBulkInstallingSuggestions = false` into an outer `finally` block (was missing; would not clear on coroutine cancellation).

Before:
```kotlin
try {
    extensionManager.installExtension(suggestion.extension).collect {}
} catch (_: Exception) {}
mutableState.update { it.copy(installingSuggestionKeys = ...) }
// ...
mutableState.update { it.copy(isBulkInstallingSuggestions = false) }
```

After:
```kotlin
try {
    extensionManager.installExtension(suggestion.extension)
        .takeWhile { !it.isCompleted() }
        .collect()
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    // per-extension failure isolated
} finally {
    mutableState.update { it.copy(installingSuggestionKeys = ...) }
}
// ...
} finally {
    mutableState.update { it.copy(isBulkInstallingSuggestions = false) }
}
```

## Files Changed

- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt`
  - Added imports: `InstallStep`, `CancellationException`, `kotlinx.coroutines.flow.collect`, `kotlinx.coroutines.flow.takeWhile`
  - Fixed `installSuggestion()`: `takeWhile { !it.isCompleted() }.collect()`
  - Fixed `installSuggestions()`: duplicate-batch guard, `takeWhile`, `CancellationException` rethrow, `finally` for both cleanup paths
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` â€” VERSION_CODE=604

## What Was Not Changed

- Sources To Try scoring, like/dislike semantics, source ordering â€” unchanged.
- Normal Extensions tab install/update behavior â€” unchanged.
- No new UI elements added.

## Commands Run

```text
./gradlew :app:compileDebugKotlin --offline â†’ BUILD SUCCESSFUL
./gradlew :app:testDebugUnitTest --offline â†’ BUILD SUCCESSFUL, all tests PASSED
./gradlew :app:assembleDebug --offline â†’ BUILD SUCCESSFUL
```

## APK

`Komikku-v1.13.6-kmk.6.4-debug.apk`

