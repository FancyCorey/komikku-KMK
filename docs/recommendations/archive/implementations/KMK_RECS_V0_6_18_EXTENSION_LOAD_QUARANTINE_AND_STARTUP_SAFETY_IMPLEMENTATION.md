# KMK-Recs v0.6.18 Implementation Notes

Date: 2026-06-20
APK: `Komikku-v1.13.6-kmk.6.18-debug.apk`
VERSION_CODE: 618

## Problem

v0.6.17 moved Source Evaluation crash recovery to app startup and added a DCM seed into `source_evaluation_unsafe_source`. But that table only hides DCM from Source Evaluation candidate selection — it does not prevent the installed/private DCM extension package from being loaded by `ExtensionLoader` at startup. The fatal SIGSEGV (stack overflow) still occurred because DCM's network interceptor code was still executing inside the app process.

## Root Fix

Block the unsafe extension package before `ChildFirstPathClassLoader` and `Class.forName` are called. Once an extension class is instantiated, its interceptors can be invoked by any part of the app.

## Two-Layer Defense

### Layer 1 — Static compile-time guard (immediate, no DB needed)

`KnownUnsafeExtensionPackages` lists confirmed crash culprits. `ExtensionLoadSafetyPolicy.shouldBlock(pkgName)` is checked in `ExtensionLoader` before any extension loading starts. This is the only layer that matters for preventing the startup crash — it runs synchronously before any async DB read.

### Layer 2 — DB-backed package quarantine (visible/manageable in UI)

`unsafe_extension_package` table (migration 49.sqm) stores package-level blocks with name, reason, source, removable flag, and timestamps. `SourceEvaluationStartupRecovery` seeds DCM into this table on every startup (idempotent). The Source Evaluation screen shows blocked packages in a new card with per-package "Allow again" and "Allow all" actions.

## Files Changed

### New files

- `app/.../extension/util/KnownUnsafeExtensionPackages.kt` — static guard with `Entry` data class, `DIGITAL_COMIC_MUSEUM` entry, `ALL` list, `isKnownUnsafe(pkgName)` function
- `app/.../extension/util/ExtensionLoadSafetyPolicy.kt` — pure testable helper: `shouldBlock(pkgName, userBlockedPackages)` checks static guard + caller-supplied set
- `data/migrations/49.sqm` — creates `unsafe_extension_package` table
- `data/...unsafe_extension_package.sq` — SQLDelight queries (getAll, getAllAsFlow, getByPkg, upsert, deleteByPkg, deleteAll)
- `domain/.../taste/model/UnsafeExtensionPackage.kt` — data class
- `domain/.../taste/repository/UnsafeExtensionPackageRepository.kt` — interface
- `data/.../taste/UnsafeExtensionPackageRepositoryImpl.kt` — impl
- `domain/.../taste/interactor/GetUnsafeExtensionPackages.kt`
- `domain/.../taste/interactor/UpsertUnsafeExtensionPackage.kt`
- `domain/.../taste/interactor/DeleteUnsafeExtensionPackage.kt`
- `domain/.../taste/interactor/ClearUnsafeExtensionPackages.kt`
- Test: `KnownUnsafeExtensionPackagesTest.kt` — 7 tests
- Test: `ExtensionLoadSafetyPolicyTest.kt` — 8 tests

### Modified files

- `extension/model/LoadResult.kt` — added `Blocked(pkgName, reason)` variant (silently ignored by ExtensionManager since it doesn't match Success/Untrusted filters)
- `extension/util/ExtensionLoader.kt` — in `loadExtensions()`: filter `extPkgs` through `ExtensionLoadSafetyPolicy.shouldBlock()` before concurrent loading, return blocked results as `LoadResult.Blocked`; in `loadExtensionFromPkgName()`: same guard on manual reload path
- `exh/recs/evaluation/SourceEvaluationKnownUnsafeSeeds.kt` — delegates to `KnownUnsafeExtensionPackages` to avoid duplicate hardcoded strings; `ALL_SEEDS` is now derived from `KnownUnsafeExtensionPackages.ALL`
- `exh/recs/evaluation/SourceEvaluationStartupRecovery.kt` — added `GetUnsafeExtensionPackages` and `UpsertUnsafeExtensionPackage` deps; `applyKnownUnsafeSeeds()` now writes package-level block first (no sig hash needed), then optionally writes SE unsafe record if sig hash found
- `eu/kanade/domain/KMKDomainModule.kt` — registered `UnsafeExtensionPackageRepository` + 4 interactors
- `exh/recs/evaluation/SourceEvaluationDiagnosticsBuilder.kt` — added `blockedPackageCount` and `dcmIsStaticallyBlocked` params; included in diagnostics output
- `exh/recs/evaluation/SourceEvaluationScreenModel.kt` — added 3 new constructor params; added `blockedPackages`, `showBlockedPackagesDialog`, `showClearBlockedPackagesDialog` state; observes `getUnsafeExtensionPackages.subscribeAll()`; added 6 action methods; `copyDiagnosticsToClipboard()` passes new diagnostics fields
- `exh/recs/evaluation/SourceEvaluationScreen.kt` — added blocked packages card to LazyColumn; added 2 dialogs (detail + clear confirm); added `BlockedPackagesCard` and `BlockedPackageRow` composables; imported `UnsafeExtensionPackage`
- `i18n-kmk/strings.xml` — 10 new v0.6.18 strings for blocked packages UI
- `exh/recs/KmkRecsReleaseNotes.kt` — VERSION_CODE=618, VERSION_NAME="KMK-Recs v0.6.18"

## Key Design Decisions

- **Static guard is the crash fix** — `KnownUnsafeExtensionPackages` runs synchronously with no DB dependency. Even if the DB is unavailable or the startup recovery coroutine hasn't finished, DCM will never be loaded.
- **`LoadResult.Blocked` ignored by ExtensionManager** — `filterIsInstance<LoadResult.Success>()` and `filterIsInstance<LoadResult.Untrusted>()` in ExtensionManager already ignore `Blocked`. Zero UI impact on the extensions screen.
- **No schema changes to existing tables** — new `unsafe_extension_package` table is separate from `source_evaluation_unsafe_source`. Package-level blocks don't need signature hash.
- **Seed is idempotent** — package-level block only written if `pkgName` not already in DB. SE unsafe record only written if extension key not already present.
- **No auto-uninstall** — the extension APK remains on device. UI explains the user should uninstall from Browse > Extensions or Android Settings.

## Build Status

- `:app:testDebugUnitTest` — BUILD SUCCESSFUL
- `:app:assembleDebug` — BUILD SUCCESSFUL (127 MB)
