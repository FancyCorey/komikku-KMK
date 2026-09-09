# KMK-Recs v0.6.10: Source Evaluation DI Crash Fix Implementation

Date: 2026-06-19

Feature version: KMK-Recs v0.6.10

## Summary

Fixed a crash when opening Source Evaluation from Recommendation Settings. The crash was a missing Injekt DI registration for `GetNonInstalledSourceSuggestions`.

## Root Cause

`SourceEvaluationScreenModel` requested `GetNonInstalledSourceSuggestions` via `Injekt.get()` in its constructor:

```kotlin
private val getNonInstalled: GetNonInstalledSourceSuggestions = Injekt.get()
```

But the class was never registered in `KMKDomainModule`. Injekt throws `InjektionException` at construction time â€” before the screen renders, before any flow subscription. No `.catch` on a Flow can catch this because the crash happens outside a coroutine context.

`RecommendationsSettingsScreenModel` avoided the crash by manually constructing the helper (`GetNonInstalledSourceSuggestions()` â€” valid because the class has `= Injekt.get()` constructor defaults), but this created an inconsistent pattern.

## Why v0.6.9 Did Not Fix This

v0.6.9 added `.catch` fallbacks on `getSourceEvaluations.subscribeAll()` flows â€” protecting against the `source_evaluation` table being absent. The v0.6.10 crash is in `SourceEvaluationScreenModel.<init>` during Injekt graph resolution, which happens synchronously before any flow is subscribed. These are independent failure modes.

## Files Changed

### `app/src/main/java/eu/kanade/domain/KMKDomainModule.kt`

Added import:

```kotlin
import exh.recs.discovery.GetNonInstalledSourceSuggestions
```

Added factory registration inside the `// KMK -->` block, after `ClearSourceEvaluations`:

```kotlin
addFactory { GetNonInstalledSourceSuggestions(get(), get(), get()) }
```

### `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt`

Changed manual construction to DI injection:

```kotlin
// before
private val getNonInstalledSourceSuggestions: GetNonInstalledSourceSuggestions = GetNonInstalledSourceSuggestions()

// after
private val getNonInstalledSourceSuggestions: GetNonInstalledSourceSuggestions = Injekt.get()
```

`Injekt` was already imported in this file.

### `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt`

Removed redundant outer `screenModelScope.launch {}` wrapper from candidate loading. `launchIn(screenModelScope)` already launches collection on its own.

Added `.catch` before `.onEach` so candidate loading failures produce a safe empty state instead of crashing:

```kotlin
// KMK --> v0.6.10: remove redundant outer launch; add catch so candidate failure doesn't crash the screen
getNonInstalled.subscribe()
    .catch { e ->
        logcat(LogPriority.ERROR, e) { "Failed to load source evaluation candidates" }
        mutableState.update { it.copy(candidates = emptyList(), isLoadingCandidates = false) }
        emit(emptyList())
    }
    .onEach { suggestions -> ... }
    .launchIn(screenModelScope)
// KMK <--
```

### `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`

- `VERSION_CODE` bumped from 609 to 610.
- `VERSION_NAME` changed to `"KMK-Recs v0.6.10"`.
- Added user-facing What's New entry: "Fixed a crash when opening Source Evaluation from Recommendation Settings."

## DI Registration Pattern

`GetNonInstalledSourceSuggestions` takes three constructor parameters (`ExtensionManager`, `SourcePreferences`, `GetSourceEvaluations`), all of which are already registered in the Injekt graph. The registration uses positional `get()` calls:

```kotlin
addFactory { GetNonInstalledSourceSuggestions(get(), get(), get()) }
```

This matches the pattern used for other KMK interactors in this module.

## Tests Run

- `:app:testDebugUnitTest` â€” BUILD SUCCESSFUL, all tests PASSED
- `:app:assembleDebug` â€” BUILD SUCCESSFUL

## APK

Generated: `app/build/outputs/apk/debug/app-universal-debug.apk`

Copied to: `Komikku-v1.13.6-kmk.6.10-debug.apk`

## Manual Verification

Not performed by this session. Required manual test: install v0.6.10 APK over v0.6.9, open Recommendation Settings > Source Evaluation, confirm no crash, confirm candidate list loads or shows safe empty state.

