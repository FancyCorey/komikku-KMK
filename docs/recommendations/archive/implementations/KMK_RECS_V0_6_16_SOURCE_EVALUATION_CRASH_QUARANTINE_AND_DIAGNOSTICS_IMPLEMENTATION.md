# KMK-Recs v0.6.16 Implementation Notes

Date: 2026-06-20
APK: `Komikku-v1.13.6-kmk.6.16-debug.apk`
VERSION_CODE: 616

## Problem

SIGSEGV (fatal signal 11, stack overflow) during Source Evaluation on `DefaultDispatch` â€” caused by OkHttp / HttpLoggingInterceptor / extension code (observed with DigitalComicMuseum extension). Not catchable with try/catch â€” it's a process-level native crash.

## Solution: Probe Marker Quarantine

1. Write a single-row SQLite record (id=1) before each risky network call.
2. If the process dies, the record persists.
3. On the next screen open, read the record. If recent (< 24h), mark the extension unsafe, then clear the record.
4. Unsafe extensions are skipped in candidate pool building.

## Files Changed

### New SQLDelight migrations

- `data/src/main/sqldelight/tachiyomi/migrations/48.sqm` â€” creates `source_evaluation_probe_marker` and `source_evaluation_unsafe_source` tables

### New SQLDelight query files

- `data/src/main/sqldelight/tachiyomi/data/source_evaluation_probe_marker.sq`
- `data/src/main/sqldelight/tachiyomi/data/source_evaluation_unsafe_source.sq`

### New domain models

- `domain/.../taste/model/SourceEvaluationProbeMarker.kt`
- `domain/.../taste/model/SourceEvaluationUnsafeSource.kt` (includes `SourceEvaluationUnsafeKeys` object)

### New repository interface and implementation

- `domain/.../taste/repository/SourceEvaluationSafetyRepository.kt`
- `data/.../taste/SourceEvaluationSafetyRepositoryImpl.kt`

### New interactors (7)

- `GetSourceEvaluationUnsafeSources.kt`
- `GetSourceEvaluationProbeMarker.kt`
- `UpsertSourceEvaluationProbeMarker.kt`
- `ClearSourceEvaluationProbeMarker.kt`
- `MarkSourceEvaluationUnsafe.kt`
- `DeleteSourceEvaluationUnsafe.kt`
- `ClearSourceEvaluationUnsafe.kt`

### New pure helpers

- `SourceEvaluationCrashRecoveryPolicy.kt` â€” decides MarkUnsafe / ClearStale / DoNothing
- `SourceEvaluationDiagnosticsBuilder.kt` â€” builds diagnostics text

### Modified files

- `KMKDomainModule.kt` â€” registers `SourceEvaluationSafetyRepository` + 7 interactors; updated `GetSourceEvaluationCandidates` factory call to 4 params
- `SourceEvaluationCandidateFilter.kt` â€” added `unsafeExtensionKeys` param to `buildPool()`, `unsafeExtensionKeys` and `unsafeHiddenCount` to `CandidatePoolResult`
- `GetSourceEvaluationCandidates.kt` â€” added `GetSourceEvaluationUnsafeSources` dep; combine includes unsafe sources flow; extracts `unsafeExtensionKeys` set
- `SourceEvaluationRunner.kt` â€” added `UpsertSourceEvaluationProbeMarker` + `ClearSourceEvaluationProbeMarker` deps; writes marker before each risky phase; clears in `finally`; generates `batchId` (UUID) per `start()` call; clears stale marker before batch begins
- `SourceEvaluationScreenModel.kt` â€” crash recovery init block; observes unsafe sources flow; new state fields; new action methods; `applyOptionsAndUpdateState` includes `unsafeHiddenCount`
- `SourceEvaluationScreen.kt` â€” new dialogs (clear unsafe, unsafe sources list); new cards (error card, unsafe sources card); copy diagnostics button; updated `CandidateDiagnosticsRow`; updated `InfoCard` with optional dismiss
- `i18n-kmk/strings.xml` â€” 14 new v0.6.16 strings
- `KmkRecsReleaseNotes.kt` â€” VERSION_CODE=616, VERSION_NAME="KMK-Recs v0.6.16"

### New tests

- `SourceEvaluationCrashRecoveryPolicyTest.kt` â€” 6 tests
- `SourceEvaluationUnsafeKeysTest.kt` â€” 5 tests
- `SourceEvaluationCandidateFilterTest.kt` â€” 4 new unsafe filtering tests added (22 total)

## Key Design Decisions

- **Extension-level quarantine** â€” marker tracks `signatureHash|pkgName`; unsafe key does the same so the entire extension is skipped (not per-source). Source-level keys are recorded when sourceId is known but filtering is at extension level.
- **24h stale threshold** â€” markers older than 24h are likely from abandoned sessions, not crashes; they are cleared without quarantining.
- **No try/catch around network calls** â€” the probe marker approach handles SIGSEGV which is unchatchable; existing try/catch remains for normal exceptions.
- **DB errors in marker writes are swallowed** â€” `writeProbeMarker` catches all exceptions to prevent marker-write failures from aborting evaluation runs.
- **Pure helpers** â€” `SourceEvaluationCrashRecoveryPolicy` and `SourceEvaluationDiagnosticsBuilder` are pure objects for testability.

## Build Status

- `:app:testDebugUnitTest` â€” BUILD SUCCESSFUL (all tests pass)
- `:app:assembleDebug` â€” BUILD SUCCESSFUL

