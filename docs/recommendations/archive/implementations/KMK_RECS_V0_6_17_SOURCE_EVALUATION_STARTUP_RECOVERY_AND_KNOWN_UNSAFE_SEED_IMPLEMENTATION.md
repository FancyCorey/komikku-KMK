# KMK-Recs v0.6.17 Implementation Notes

Date: 2026-06-20
APK: `Komikku-v1.13.6-kmk.6.17-debug.apk`
VERSION_CODE: 617

## Problem

v0.6.16 crash recovery ran only when the user opened Source Evaluation (`SourceEvaluationScreenModel.init`). A fourth crash report showed Komikku closing ~35 seconds after startup (Samsung SM-X520, Android 16, process uptime 35s) before the user reached Source Evaluation. Stack: `DigitalComicMuseum.b / IgnoreGzipInterceptor / HttpLoggingInterceptor / OkHttp` â€” SIGSEGV / stack overflow, unchatchable with try/catch.

## Solution

1. **Move crash recovery to app startup** â€” `SourceEvaluationStartupRecovery.runAsync(scope)` is called in `App.onCreate` immediately after `MangaCoverMetadata.load()` and before `WidgetManager`/sync. It launches on `Dispatchers.IO`, catches all exceptions, and logs with prefix `KMK SourceEvaluation startup recovery:`.

2. **Shared helper** â€” `SourceEvaluationStartupRecovery` is used by both App.kt (full startup path with extension manager for seeds) and `SourceEvaluationScreenModel` (screen-open fallback, no seeding if seed already applied). Both paths share the same policy, marker handling, and reason strings.

3. **DCM known-unsafe seed** â€” If Digital Comic Museum (`eu.kanade.tachiyomi.extension.en.digitalcomicmuseum`) is found in installed/available extension metadata and has no existing unsafe record, it is pre-seeded into the quarantine table on startup. The seed is visible in the Quarantined Extensions UI and removable by the user. Seeds are skipped if signature hash cannot be resolved.

4. **Installed-unsafe guidance** â€” The Quarantined Extensions dialog description now explains that quarantine only prevents Source Evaluation from evaluating an extension, and that if a quarantined installed extension still crashes Komikku outside Source Evaluation the user should uninstall/disable it from the Extensions screen.

## Files Changed

### New files

- `app/.../exh/recs/evaluation/SourceEvaluationKnownUnsafeSeeds.kt` â€” `KnownUnsafeSeed` data class + `ALL_SEEDS` list with DCM entry
- `app/.../exh/recs/evaluation/SourceEvaluationStartupRecovery.kt` â€” shared helper with `run()` suspend function and `runAsync(scope)` launcher
- `app/src/test/.../SourceEvaluationKnownUnsafeSeedsTest.kt` â€” 6 seed tests
- `app/src/test/.../SourceEvaluationStartupRecoveryTest.kt` â€” 7 policy/decision tests

### Modified files

- `App.kt` â€” import + `SourceEvaluationStartupRecovery().runAsync(scope)` after `MangaCoverMetadata.load()`
- `SourceEvaluationScreenModel.kt` â€” refactored init crash-recovery block to call `SourceEvaluationStartupRecovery(...).run()` using existing constructor deps; uses `Result.markerRecovered` to update `screenErrorMessage`
- `i18n-kmk/strings.xml` â€” updated `source_evaluation_unsafe_sources_desc` with installed-unsafe guidance; added `source_evaluation_unsafe_installed_warning` string
- `KmkRecsReleaseNotes.kt` â€” VERSION_CODE=617, VERSION_NAME="KMK-Recs v0.6.17", what's new entry

## Key Design Decisions

- **No new DB tables** â€” uses existing v0.6.16 `source_evaluation_probe_marker` and `source_evaluation_unsafe_source` tables.
- **Seed idempotent** â€” `getUnsafeSources.awaitAll()` is checked before each seed; already-present extension keys are skipped. `markUnsafeFromProbe` uses ON CONFLICT upsert so concurrent calls are safe.
- **Screen recovery as fallback** â€” If startup recovery cleared the marker before the user opens Source Evaluation, the screen-open call finds DoNothing and is a no-op. No duplicate marking.
- **No auto-uninstall** â€” seeds and quarantine only affect Source Evaluation candidate filtering. Extensions screen and installed list are unaffected.

## Build Status

- `:app:testDebugUnitTest` â€” BUILD SUCCESSFUL (all 267 tasks; 26 executed, new tests included)
- `:app:assembleDebug` â€” BUILD SUCCESSFUL (127 MB)

