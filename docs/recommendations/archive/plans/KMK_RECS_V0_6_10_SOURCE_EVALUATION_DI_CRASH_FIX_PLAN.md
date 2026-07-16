# KMK-Recs v0.6.10: Source Evaluation DI Crash Fix Plan

Date: 2026-06-19

Feature version target: KMK-Recs v0.6.10

Status: implementation plan only. Do not modify application code until the user explicitly approves implementation.

## Goal

Fix the crash that happens when opening the Source Evaluation screen from For You / Recommendation Settings in the latest v0.6.9 APK.

The fix must make Source Evaluation safe to open even if candidate loading fails, and it must prevent this class of dependency-injection crash from recurring when recommendation helpers are reused by multiple screens.

## Current Crash

The crash log from `C:\Users\USER\Downloads\extracted_komikku_crash_logs.txt` shows the current failure is:

```text
uy.kohesive.injekt.api.InjektionException:
No registered instance or factory for type class exh.recs.discovery.GetNonInstalledSourceSuggestions
```

The active stack trace points to:

```text
exh.recs.evaluation.SourceEvaluationScreenModel.<init>(SourceEvaluationScreenModel.kt:171)
exh.recs.evaluation.SourceEvaluationScreen.Content(SourceEvaluationScreen.kt:59)
```

This is different from the previous v0.6.9 database crash:

```text
android.database.sqlite.SQLiteException: no such table: source_evaluation
```

v0.6.9 addressed the missing-table path by adding SQLDelight migration `47.sqm` and catch fallbacks around direct `source_evaluation` reads. The latest crash is now happening earlier, during construction of `SourceEvaluationScreenModel`, before the screen can render.

## Root Cause

`SourceEvaluationScreenModel` requests `GetNonInstalledSourceSuggestions` from Injekt:

```kotlin
private val getNonInstalled: GetNonInstalledSourceSuggestions = Injekt.get()
```

However, `GetNonInstalledSourceSuggestions` is not registered in the Injekt graph.

The class currently exists at:

```text
app/src/main/java/exh/recs/discovery/GetNonInstalledSourceSuggestions.kt
```

It has constructor defaults that pull its own dependencies from Injekt:

```kotlin
class GetNonInstalledSourceSuggestions(
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val getSourceEvaluations: GetSourceEvaluations = Injekt.get(),
)
```

That means it can be manually constructed as `GetNonInstalledSourceSuggestions()`. This is why `RecommendationsSettingsScreenModel` currently works: it manually constructs the helper instead of requesting it from Injekt.

But `SourceEvaluationScreenModel` requests it through `Injekt.get()`, which fails because no factory exists.

## Relevant Code State

### Consumer 1: Recommendation Settings

File:

```text
app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt
```

Current pattern:

```kotlin
private val getNonInstalledSourceSuggestions: GetNonInstalledSourceSuggestions = GetNonInstalledSourceSuggestions()
```

This bypasses the missing registration.

### Consumer 2: Source Evaluation

File:

```text
app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt
```

Current pattern:

```kotlin
private val getNonInstalled: GetNonInstalledSourceSuggestions = Injekt.get()
```

This crashes on screen creation.

### Current DI Module

File:

```text
app/src/main/java/eu/kanade/domain/KMKDomainModule.kt
```

Source Evaluation interactors are already registered:

```kotlin
addSingletonFactory<SourceEvaluationRepository> { SourceEvaluationRepositoryImpl(get()) }
addFactory { GetSourceEvaluations(get()) }
addFactory { GetSourceEvaluation(get()) }
addFactory { UpsertSourceEvaluation(get()) }
addFactory { DeleteSourceEvaluation(get()) }
addFactory { ClearSourceEvaluations(get()) }
```

But `GetNonInstalledSourceSuggestions` is not registered.

## Recommended Fix Strategy

Use one consistent dependency pattern for `GetNonInstalledSourceSuggestions`.

The recommended approach is to register it in `KMKDomainModule` and then inject it through Injekt everywhere it is used.

This is better than adding another one-off manual constructor because:

- the helper is now shared by Recommendation Settings and Source Evaluation,
- it depends on app-level services and interactors,
- future screens are likely to reuse it,
- missing DI registrations fail at runtime and are easy to miss during feature work,
- one consistent pattern reduces future confusion for Claude, Codex, and human maintainers.

## Implementation Steps

### 1. Register `GetNonInstalledSourceSuggestions`

Update:

```text
app/src/main/java/eu/kanade/domain/KMKDomainModule.kt
```

Add the import:

```kotlin
import exh.recs.discovery.GetNonInstalledSourceSuggestions
```

Add a factory near the other KMK recommendation/source-evaluation registrations:

```kotlin
addFactory {
    GetNonInstalledSourceSuggestions(
        extensionManager = get(),
        sourcePreferences = get(),
        getSourceEvaluations = get(),
    )
}
```

If named arguments do not compile because of import or Kotlin style constraints in this module, use positional arguments:

```kotlin
addFactory { GetNonInstalledSourceSuggestions(get(), get(), get()) }
```

The registration must happen after `GetSourceEvaluations` is available, or at least in the same module load where `GetSourceEvaluations` can be resolved.

### 2. Normalize `RecommendationsSettingsScreenModel`

Update:

```text
app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt
```

Replace the manual construction:

```kotlin
private val getNonInstalledSourceSuggestions: GetNonInstalledSourceSuggestions = GetNonInstalledSourceSuggestions()
```

With:

```kotlin
private val getNonInstalledSourceSuggestions: GetNonInstalledSourceSuggestions = Injekt.get()
```

If this change causes a compilation issue because `Injekt` is not imported in the file, add:

```kotlin
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
```

Only add imports that are actually needed.

### 3. Keep `SourceEvaluationScreenModel` Injection

Update:

```text
app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt
```

Keep this constructor dependency:

```kotlin
private val getNonInstalled: GetNonInstalledSourceSuggestions = Injekt.get()
```

Once the DI factory exists, this should no longer crash.

Do not replace it with manual construction unless the DI registration approach proves incompatible with the existing module system.

### 4. Add Defensive Candidate Loading

Still in:

```text
app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt
```

The v0.6.9 fix added a catch around `getSourceEvaluations.subscribeAll()`, but candidate loading through `getNonInstalled.subscribe()` should also fail open.

Current pattern to audit:

```kotlin
screenModelScope.launch {
    getNonInstalled.subscribe()
        .onEach { suggestions ->
            mutableState.update {
                it.copy(
                    candidates = suggestions.map { suggestion -> suggestion.extension },
                    isLoadingCandidates = false,
                )
            }
        }
        .launchIn(screenModelScope)
}
```

Replace with a direct flow subscription that includes `.catch`:

```kotlin
getNonInstalled.subscribe()
    .catch { e ->
        logcat(LogPriority.ERROR, e) { "Failed to load source evaluation candidates" }
        mutableState.update {
            it.copy(
                candidates = emptyList(),
                isLoadingCandidates = false,
            )
        }
        emit(emptyList())
    }
    .onEach { suggestions ->
        mutableState.update {
            it.copy(
                candidates = suggestions.map { suggestion -> suggestion.extension },
                isLoadingCandidates = false,
            )
        }
    }
    .launchIn(screenModelScope)
```

If the existing code already imports `catch`, `onEach`, and `launchIn`, reuse those imports. Otherwise add only the missing imports from `kotlinx.coroutines.flow`.

Do not keep an unnecessary outer `screenModelScope.launch { ... launchIn(screenModelScope) }`, because `launchIn(screenModelScope)` already launches the collection.

Expected behavior after this step:

- Source Evaluation opens even if Sources To Try candidate loading fails.
- The screen shows no candidates instead of crashing.
- The error is logged for diagnosis.
- Past source evaluations still load independently through the existing v0.6.9 fallback.

### 5. Audit Other Source Evaluation Dependencies

Before building, inspect these files for any additional `Injekt.get()` calls to newly added KMK classes that may not be registered:

```text
app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt
app/src/main/java/exh/recs/evaluation/SourceEvaluationRunner.kt
app/src/main/java/exh/recs/discovery/GetNonInstalledSourceSuggestions.kt
app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt
app/src/main/java/eu/kanade/domain/KMKDomainModule.kt
```

Known expected dependencies:

- `ExtensionManager`
- `BasePreferences`
- `SourcePreferences`
- `GetTasteProfile`
- `GetSourceEvaluations`
- `GetSourceEvaluation`
- `UpsertSourceEvaluation`
- `DeleteSourceEvaluation`
- `ClearSourceEvaluations`
- `SourceEvaluationRepository`

If any app-specific KMK helper is requested through `Injekt.get()` and not registered, register it or convert it to explicit construction consistently. Do not make unrelated feature changes.

### 6. Preserve v0.6.9 Migration Fix

Do not remove or alter:

```text
data/src/main/sqldelight/tachiyomi/migrations/47.sqm
data/src/main/sqldelight/tachiyomi/data/source_evaluation.sq
```

The current crash is not evidence that migration 47 failed. It is a separate dependency-injection issue.

### 7. Update Versioning And Release Notes

Update the local KMK recommendation version to v0.6.10.

Expected files to inspect and update:

```text
app/src/main/java/exh/recs/whatsnew/KmkRecsReleaseNotes.kt
RECOMMENDATION_VERSIONING.md
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/README.md
```

Recommended release note wording:

```text
Fixed a crash when opening Source Evaluation from Recommendation Settings.
```

Do not add developer-only documentation changes to the user-facing What's New page.

Expected APK naming pattern:

```text
Komikku-v1.13.6-kmk.6.10-debug.apk
```

If the project's actual Gradle version fields differ, follow the project's existing naming logic and document the exact generated APK name.

### 8. Add Implementation Notes

After implementation, create:

```text
docs/recommendations/KMK_RECS_V0_6_10_SOURCE_EVALUATION_DI_CRASH_FIX_IMPLEMENTATION.md
```

That file must document:

- exact root cause,
- exact files changed,
- why v0.6.9 did not fix this crash,
- whether `GetNonInstalledSourceSuggestions` was registered or manually constructed,
- test commands run,
- generated APK path/name,
- whether device/manual verification was performed.

## Testing Plan

Run:

```text
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

If Gradle is unavailable or fails for environmental reasons, document the exact failure and do not claim the APK is verified.

Manual APK verification should include:

1. Install the v0.6.10 debug APK over the latest crashing v0.6.9 APK.
2. Open the app.
3. Go to Browse > For You / Recommendation Settings.
4. Open Source Evaluation.
5. Confirm the screen does not crash.
6. Confirm candidate list either loads or shows a safe empty state.
7. Confirm previous evaluations, if any, show without crashing.
8. Leave and reopen the Source Evaluation screen.
9. Confirm crash logs no longer contain:

```text
No registered instance or factory for type class exh.recs.discovery.GetNonInstalledSourceSuggestions
```

Optional deeper manual test:

1. Start a very small evaluation batch, such as 10.
2. Confirm the runner starts without an immediate dependency crash.
3. Confirm cancellation/cleanup still works.
4. Confirm installed extension state is not left dirty after cancellation.

## Acceptance Criteria

This fix is complete only if:

- Source Evaluation opens from Recommendation Settings without crashing.
- `GetNonInstalledSourceSuggestions` has one consistent dependency construction pattern.
- Candidate loading failures are caught and displayed as an empty safe state.
- The previous `source_evaluation` migration fix remains intact.
- Unit tests pass, or failures are clearly documented as unrelated/environmental.
- A debug APK is built successfully.
- Documentation and versioning are updated to v0.6.10.

## Non-Goals

Do not implement these in this hotfix:

- new source evaluation scoring logic,
- Shizuku behavior changes,
- batch size changes,
- explicit-source classifier changes,
- Sources To Try ranking changes,
- cross-extension matching changes,
- UI redesign.

This is a focused crash fix and hardening pass.

## Recommendation

Move forward with this fix before adding more Source Evaluation features.

The feature is currently blocked by a startup crash in the Source Evaluation screen. The lowest-risk path is to register the missing helper in the app's dependency graph, normalize both consumers to use that registration, and add a local catch around candidate loading so that any future metadata/evaluation failure degrades safely instead of taking down the screen.

