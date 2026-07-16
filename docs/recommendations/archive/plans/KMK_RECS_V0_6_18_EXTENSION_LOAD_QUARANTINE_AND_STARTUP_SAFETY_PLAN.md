# KMK-Recs v0.6.18: Extension Load Quarantine and Startup Safety Plan

Date: 2026-06-20

Status: implementation plan, awaiting user approval before coding

Feature version: KMK-Recs v0.6.18

Expected APK name: `Komikku-v1.13.6-kmk.6.18-debug.apk`

## Summary

The repeated startup crash is still caused by `DigitalComicMuseum` extension code executing inside Komikku. v0.6.17 correctly added Source Evaluation probe recovery and known-unsafe seeding, but its quarantine only affects the Source Evaluation candidate list. It does not stop an already installed/private extension from being loaded by Komikku on app startup.

v0.6.18 should add a package-level extension-load quarantine that prevents known-dangerous extension packages from being loaded into the app process at all. This must happen inside the extension loading path before `Class.forName(...)`, source construction, interceptors, or source network code can run.

## Evidence

User crash extract: `C:\Users\USER\Downloads\komikku_0644_crash_extract.txt`

Crash details:

- App ID: `app.komikku.dev`
- App version: `1.13.6-1 (582ea3e, 79, 2026-06-20T11:34:09Z)`
- Crash time: around 2026-06-20 06:44
- Native fatal: `SIGSEGV`, `SEGV_ACCERR`
- Thread: `DefaultDispatch`
- Cause: `stack pointer is not in a rw map; likely due to stack overflow`
- Backtrace repeats:
  - `okhttp3.logging.HttpLoggingInterceptor.intercept`
  - `eu.kanade.tachiyomi.network.interceptor.IgnoreGzipInterceptor.intercept`
  - `eu.kanade.tachiyomi.extension.en.digitalcomicmuseum.DigitalComicMuseum.b`
  - `eu.kanade.tachiyomi.extension.en.digitalcomicmuseum.DigitalComicMuseum.a`
  - anonymous extension interceptor frames

This is the same native/process-level failure pattern as the earlier crash reports. It is not a normal Kotlin exception and cannot be solved by adding `try/catch` around UI code.

## Current Implementation Findings

### v0.6.17 Behavior

Implemented files:

- `app/src/main/java/eu/kanade/tachiyomi/App.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationStartupRecovery.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationKnownUnsafeSeeds.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt`

`App.kt` calls:

```kotlin
SourceEvaluationStartupRecovery().runAsync(scope)
```

This runs asynchronously after `MangaCoverMetadata.load()` and before widget/sync initialization.

`SourceEvaluationKnownUnsafeSeeds` contains:

```kotlin
pkgName = "eu.kanade.tachiyomi.extension.en.digitalcomicmuseum"
```

The seed writes into the existing `source_evaluation_unsafe_source` table if the extension can be found in installed or available metadata.

### Important Limitation

The v0.6.17 implementation notes explicitly say:

> Seeds and quarantine only affect Source Evaluation candidate filtering. Extensions screen and installed list are unaffected.

That explains why the crash can continue after v0.6.17:

- `source_evaluation_unsafe_source` hides unsafe extensions from Source Evaluation candidates.
- It does not prevent `ExtensionLoader.loadExtensions(context)` from loading installed/private extension APKs.
- It does not prevent `ExtensionLoader.loadExtension(...)` from constructing source classes.
- It does not prevent an installed source/interceptor from being used elsewhere in the app.

### Current Candidate Filter Boundary

`SourceEvaluationCandidateFilter.buildPool(...)` uses unsafe extension keys only here:

```kotlin
if (extKey in unsafeExtensionKeys) {
    unsafeCount++
    unsafeKeys.add(extKey)
    continue
}
```

This only filters available non-installed Source Evaluation candidates. It does not protect app startup.

### Extension Loading Risk Point

`ExtensionManager.initExtensions()` calls:

```kotlin
val extensions = ExtensionLoader.loadExtensions(context)
```

`ExtensionLoader.loadExtensions(context)`:

1. Finds installed shared extension packages.
2. Finds private extension files in `filesDir/exts`.
3. Selects extension packages.
4. Loads each extension concurrently.
5. Calls `loadExtension(...)`.

`ExtensionLoader.loadExtension(...)` then:

1. Reads package metadata.
2. Validates version/signature/trust.
3. Creates a `ChildFirstPathClassLoader`.
4. Instantiates source classes via reflection:

```kotlin
Class.forName(it, false, classLoader).getDeclaredConstructor().newInstance()
```

This is the correct level for an app-process safety block. Once an unsafe extension has been instantiated, Komikku has already crossed the dangerous boundary.

## Root Cause

The app still loads or uses the installed/private `DigitalComicMuseum` extension after startup. Since the extension code appears to recursively re-enter the network interceptor chain and overflows the native stack, the process dies. Because this is a native crash, Kotlin exception handling and Compose error surfaces cannot catch it.

## Goals

1. Prevent known-dangerous extension packages from being loaded into Komikku at startup.
2. Preserve v0.6.16/v0.6.17 Source Evaluation quarantine behavior.
3. Keep the fix narrow and reversible.
4. Avoid auto-uninstalling extensions without user action.
5. Make the user-facing state clear: the source is blocked for safety, not merely hidden from recommendations.
6. Update documentation and version metadata consistently under the v0.6.x extension/source-evaluation line.

## Non-Goals

- Do not redesign Source Evaluation.
- Do not auto-delete user data.
- Do not automatically uninstall installed extension APKs.
- Do not globally disable all extensions.
- Do not rely on try/catch to handle native `SIGSEGV`.
- Do not make broad source-specific hacks beyond the known repeated crash culprit unless the implementation creates a general mechanism.

## Required Implementation

### 1. Add Package-Level Unsafe Extension Model

The existing `source_evaluation_unsafe_source` table is keyed by extension signature/package and belongs to Source Evaluation. It should remain as-is.

Add a separate app-safety concept for packages that must not be loaded:

Suggested name:

- `SourceEvaluationUnsafeExtensionPackage`
- or `UnsafeExtensionPackage`

Required fields:

- `pkgName: String`
- `extensionName: String?`
- `reason: String`
- `createdAt: Long`
- `updatedAt: Long`
- `source: String`
  - examples: `known_seed`, `probe_recovery`, `manual`
- `removable: Boolean`
  - known seeds should be removable from UI, but removal should warn that the app may crash again

Recommended table:

```sql
CREATE TABLE unsafe_extension_package(
    pkg_name TEXT NOT NULL PRIMARY KEY,
    extension_name TEXT,
    reason TEXT NOT NULL,
    source TEXT NOT NULL,
    removable INTEGER AS Boolean NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
```

Use a new migration after the current latest migration. Do not reuse `source_evaluation_unsafe_source` for this because that table requires signature-based extension keys and does not express app-level load blocking.

Add SQLDelight queries:

- `selectAll`
- `selectPackage(pkgName)`
- `upsert`
- `deletePackage(pkgName)`
- `deleteAll`

Add domain/data layers following current patterns:

- data model in domain taste package or an appropriate extension safety package
- repository interface
- repository implementation
- interactors:
  - `GetUnsafeExtensionPackages`
  - `UpsertUnsafeExtensionPackage`
  - `DeleteUnsafeExtensionPackage`
  - optionally `ClearUnsafeExtensionPackages`

Register interactors/repository in `KMKDomainModule` or the existing module that registers source evaluation repositories.

### 2. Seed DigitalComicMuseum Into Package-Level Quarantine

Update `SourceEvaluationStartupRecovery` so known unsafe seeds write both:

1. Existing Source Evaluation unsafe source record when signature metadata exists.
2. New package-level unsafe package record by package name.

DigitalComicMuseum package:

```text
eu.kanade.tachiyomi.extension.en.digitalcomicmuseum
```

Reason:

```text
Known native crash suspect from user logs: DigitalComicMuseum repeatedly caused SIGSEGV/stack overflow during network interception.
```

Unlike the old v0.6.17 seed, package-level quarantine must not require signature hash. Package name alone is enough to skip loading the extension package before source classes are instantiated.

### 3. Block Unsafe Packages in ExtensionLoader Before Loading

Add a way for `ExtensionLoader` to know which package names are blocked before it calls `loadExtension(...)`.

Preferred approach:

- Add an injectable/queryable package quarantine provider, but keep `ExtensionLoader` free from direct UI dependencies.
- Since `ExtensionLoader` already uses `injectLazy()` for `TrustExtension`, `SourcePreferences`, and `GetExtensionRepo`, it may also use an interactor/repository for unsafe packages if DI is available at load time.

Required behavior in `ExtensionLoader.loadExtensions(context)`:

1. Read unsafe package names.
2. When building `extPkgs`, filter any `ExtensionInfo` whose `packageInfo.packageName` is unsafe.
3. Log a warning:

```text
KMK extension safety: skipped unsafe extension package eu.kanade.tachiyomi.extension.en.digitalcomicmuseum
```

4. Return a non-success load result if the app needs to display blocked extensions somewhere.

Decision point:

- If `LoadResult` can be extended safely, add `LoadResult.Blocked` with minimal metadata.
- If extending `LoadResult` creates too much UI churn, silently skip loading and expose blocked packages through the new safety repository/UI.

Important: skipping must happen before:

- `ChildFirstPathClassLoader(...)`
- `Class.forName(...)`
- source constructor calls
- source factory calls

Also update `ExtensionLoader.loadExtensionFromPkgName(context, pkgName)`:

- If `pkgName` is unsafe, return `LoadResult.Error` or `LoadResult.Blocked`.
- This protects trust/install receiver paths from manually reloading the unsafe package.

### 4. Ensure Startup Recovery Runs Early Enough

v0.6.17 currently runs:

```kotlin
SourceEvaluationStartupRecovery().runAsync(scope)
```

Because this is async, extension loading may already be finished before package-level quarantine is seeded. Also, `ExtensionManager` may initialize during DI/module creation because it is an injected singleton.

v0.6.18 should not rely only on async startup recovery.

Required behavior:

- Package-level known unsafe seeds must be available before `ExtensionLoader.loadExtensions(context)` performs load filtering.

Recommended options:

Option A: Static known-unsafe package guard in `ExtensionLoader`

- Keep a small static list/object such as `KnownUnsafeExtensionPackages`.
- `ExtensionLoader` checks this list directly before loading.
- The DB/UI quarantine can mirror this list, but the critical safety block does not depend on database readiness.
- This is the safest immediate fix for the repeated startup crash.

Option B: Synchronous seed before ExtensionManager init

- Rework app/DI initialization so package safety records are inserted before `ExtensionManager` initializes.
- This is more invasive and riskier.

Recommended choice: Option A for v0.6.18.

Rationale:

- The crash is a startup native crash.
- The package name is known and repeated.
- A direct pre-load package guard is the only reliable way to stop the code from entering the app process before DB recovery finishes.
- The guard can be narrow, documented, and removable later.

### 5. Add a Central Known Unsafe Package Object

Create a central object that does not depend on Source Evaluation DB:

Suggested file:

`app/src/main/java/exh/recs/evaluation/KnownUnsafeExtensionPackages.kt`

or, if better architecturally:

`app/src/main/java/eu/kanade/tachiyomi/extension/util/KnownUnsafeExtensionPackages.kt`

Suggested API:

```kotlin
object KnownUnsafeExtensionPackages {
    data class Entry(
        val pkgName: String,
        val extensionName: String,
        val reason: String,
    )

    val DIGITAL_COMIC_MUSEUM = Entry(
        pkgName = "eu.kanade.tachiyomi.extension.en.digitalcomicmuseum",
        extensionName = "Digital Comic Museum",
        reason = "Known native crash suspect from user logs: repeated SIGSEGV/stack overflow during network interception.",
    )

    val all = listOf(DIGITAL_COMIC_MUSEUM)

    fun isKnownUnsafe(pkgName: String): Boolean =
        all.any { it.pkgName == pkgName }
}
```

`ExtensionLoader` can depend on this without database access.

`SourceEvaluationKnownUnsafeSeeds` can reuse this object to avoid duplicate hardcoded strings.

### 6. User-Facing Safety UI

Add a small section in Source Evaluation settings or diagnostics:

Title idea:

- `Blocked unsafe extensions`

Content:

- Show package-level blocked extensions.
- Explain that these are blocked from loading because they caused native app crashes.
- Show installed/private status if detectable.
- Provide actions:
  - `Uninstall extension` if installed/shared extension can be sent through normal uninstall flow.
  - `Remove private extension file` only if it is a private extension and Komikku already has a safe path to delete it.
  - `Allow again` only if the block is removable, with a strong warning.

Do not auto-uninstall by default.

For DigitalComicMuseum specifically:

- If it is still installed as a shared Android extension, blocking load should stop Komikku from crashing, but the extension APK remains on the device.
- The UI should tell the user they may uninstall it from Browse > Extensions or Android settings.

### 7. Preserve Source Evaluation Quarantine

Keep v0.6.16/v0.6.17 behavior:

- probe marker before risky operations
- recovery marks `source_evaluation_unsafe_source`
- candidates skip unsafe extension keys
- unsafe sources card remains in Source Evaluation

But clarify UI copy:

- Source Evaluation quarantine = avoids evaluating sources that crashed during evaluation.
- Package-level blocked extension = avoids loading known dangerous extension packages into Komikku at all.

These are related but separate safety layers.

### 8. Improve Diagnostics

Update `SourceEvaluationDiagnosticsBuilder` to include:

- number of Source Evaluation unsafe records
- number of package-level blocked unsafe extensions
- whether DigitalComicMuseum is blocked by static guard
- whether DigitalComicMuseum appears installed/private, if safely detectable without loading it

Diagnostics should be readable from the Source Evaluation screen without triggering source loading.

### 9. Versioning and Documentation

Update:

- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`
  - `VERSION_CODE = 618`
  - `VERSION_NAME = "KMK-Recs v0.6.18"`
  - user-facing What's New:
    - mention startup crash protection for unsafe extension packages
    - mention Source Evaluation safety diagnostics
    - do not mention internal markdown/doc work

Add implementation report after coding:

- `docs/recommendations/KMK_RECS_V0_6_18_EXTENSION_LOAD_QUARANTINE_AND_STARTUP_SAFETY_IMPLEMENTATION.md`

Update:

- `docs/recommendations/README.md`
- `docs/recommendations/NEXT_WORK.md`
- `RECOMMENDATION_VERSIONING.md`

Keep this under major version `0.6.x` because it is still extension/source-evaluation work.

## Testing Plan

### Unit Tests

Add tests for:

1. `KnownUnsafeExtensionPackages`
   - contains DigitalComicMuseum
   - package name exact match
   - unknown package not blocked

2. Package-level unsafe repository/interactors
   - insert/upsert
   - delete
   - subscribe/get all if patterns allow

3. Extension loader safety policy
   - package marked known unsafe is skipped before load
   - non-unsafe package is allowed

If `ExtensionLoader` is difficult to unit-test directly, extract a pure helper:

```kotlin
ExtensionLoadSafetyPolicy.shouldBlockPackage(pkgName, userBlockedPackages)
```

Test the helper thoroughly.

### Manual Tests

1. Install v0.6.18 over the crashing build.
2. Open Komikku and wait at least two minutes.
3. Confirm app no longer closes around 30-50 seconds.
4. Open Browse > For You Settings > Source Evaluation.
5. Confirm unsafe/block diagnostics display DigitalComicMuseum as blocked or known unsafe.
6. Start a small evaluation batch.
7. Confirm DigitalComicMuseum is not evaluated.
8. Confirm other sources still evaluate.
9. If DigitalComicMuseum extension is installed, uninstall it manually from the extension list/settings and confirm Komikku remains stable.

### Regression Tests

1. Normal installed extensions still load.
2. Available extension list still loads.
3. Sources To Try still works.
4. Source Evaluation candidates still show eligible extensions.
5. Source Evaluation unsafe source quarantine still filters candidates.
6. Removing a Source Evaluation unsafe source does not accidentally remove package-level blocked safety unless explicitly chosen.

## Risks

### Risk: Blocking By Package Name Is Broad

Blocking by package name can hide the extension even if a future fixed version exists.

Mitigation:

- make the block visible and removable;
- keep the known seed list very small;
- document why the block exists;
- later add version-aware unblock if needed.

### Risk: ExtensionLoader Cannot Safely Access DB

Extension loading happens early during DI initialization, so DB-backed quarantine may not be ready.

Mitigation:

- use a static known-unsafe guard for the immediate crash culprit;
- mirror the state into DB/UI later for visibility.

### Risk: Installed Extension Still Exists

The extension APK may remain installed on Android.

Mitigation:

- do not load it;
- show uninstall guidance/action;
- avoid auto-uninstall.

## Recommendation

Proceed with v0.6.18 as a safety hotfix.

The newest crash proves v0.6.17 was not sufficient because it only protected Source Evaluation candidate selection. The next fix needs to move the protection to the extension loading boundary, before dangerous extension code is instantiated. The narrowest reliable fix is a static package-level guard for DigitalComicMuseum plus a visible package-level quarantine system for diagnostics and user control.

