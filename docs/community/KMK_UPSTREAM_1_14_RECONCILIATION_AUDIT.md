# KMK / Official Komikku 1.14.0 Reconciliation Audit

**Date:** 2026-07-15  
**Scope:** Read-only audit of the `kmk-recs` worktree against official Komikku `v1.13.6` and `v1.14.0`  
**Current KMK feature line observed:** `KMK-Recs v0.8.8`  
**Status:** Audit complete enough to block an implementation prompt; code changes are intentionally not included.

## Executive Verdict

The current worktree is **not ready to be called Komikku 1.14.0-compatible**. The APK and Gradle configuration still identify the upstream base as `1.13.6`, while official `v1.14.0` contains a broad set of source, database, extension-installation, synchronization, reader, migration, and UI changes.

The most serious finding is the database migration numbering collision:

- Official `v1.13.6` ends at migration `44`.
- Official `v1.14.0` adds migration `45` for performance indexes and migration `46` for the extension-store conversion plus manga/chapter memo columns.
- The KMK worktree uses `45.sqm` for KMK performance indexes, `46.sqm` for the taste/recommendation schema, and continues through `62.sqm` for KMK features.
- Therefore, copying official `45.sqm` and `46.sqm` into the current tree or changing the version number alone would be unsafe. Existing KMK databases would need a new, append-only migration strategy that preserves their already-applied KMK migrations and ports the official schema changes exactly once.

This is a **release blocker** until it is resolved and tested with an upgrade path from a real KMK database.

## Evidence Sources

- Official upstream tag `v1.13.6`: `git show v1.13.6`
- Official upstream tag `v1.14.0`: `git show v1.14.0`
- Official release notes: [Komikku changelog](https://komikku-app.github.io/changelogs/)
- Official comparison: [v1.13.6...v1.14.0](https://github.com/komikku-app/komikku/compare/v1.13.6...v1.14.0)
- Current build configuration: `app/build.gradle.kts`
- Current custom release notes: `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`
- Current database migrations: `data/src/main/sqldelight/tachiyomi/migrations/`
- Current reconciliation status: `docs/community/KMK_RECONCILIATION_AND_PUBLISHING_READINESS_IMPLEMENTATION.md`
- Current feature status: `docs/recommendations/CURRENT_STATE.md` and `docs/recommendations/NEXT_WORK.md`

## Findings By Severity

### Blocker B-01: The upstream 1.14.0 base is not integrated

Current `app/build.gradle.kts` still contains:

- `versionName = "1.13.6"`
- `versionCode = 88`

Official `v1.14.0` contains `versionName = "1.14.0"` and `versionCode = 80` in the upstream branch. The KMK Android package/build version policy must be decided before implementation, but the current build is demonstrably still based on the 1.13.6 source line.

The correct task is not a cosmetic version bump. The upstream source changes must be reconciled into the KMK branch while preserving the KMK feature code and build-channel policy.

### Blocker B-02: SQLDelight migration numbers collide with upstream

Official migration history:

| Migration | Official 1.14.0 meaning |
|---|---|
| `45.sqm` | Performance indexes, including history and manga-sync indexes |
| `46.sqm` | Converts `extension_repos` to `extension_store`, then adds `mangas.memo` and `chapters.memo` |

Current KMK migration history:

| Migration | Current KMK meaning |
|---|---|
| `45.sqm` | KMK performance indexes; it does not include the official history and manga-sync indexes |
| `46.sqm` | Taste, tag alias, recommendation cache, and disabled-source tables |
| `47.sqm` onward | Source evaluation, quarantine, cross-source links, OCR, discovery, source quality, and later KMK features |

The current migration README says that `46-55` are reserved for KMK, but that statement is no longer compatible with official 1.14.0. This is an outdated fork policy document, not a safe migration solution.

Required future implementation work:

1. Preserve every migration already applied by existing KMK installations.
2. Choose a new append-only migration number after `62` for the official 1.14.0 schema work, or another explicitly proven SQLDelight-compatible strategy.
3. Port the official `45` index set that is missing from current `45.sqm` without replaying already-applied KMK indexes incorrectly.
4. Port the official `46` extension-repository conversion and memo columns as a new migration against the actual KMK schema.
5. Reconcile the current extension-repository code, backup/restore, DI, preferences, loader, and settings screens with the official extension-store model.
6. Test fresh install, official 1.13.6 database upgrade, existing KMK database upgrade, repeated open, backup/restore, and sync.

No implementation prompt should tell Claude to rename or overwrite migration files in place.

### Blocker B-03: Extension repository/store architecture diverges

Official 1.14.0 replaces the old repository model with extension stores in multiple layers:

- `ExtensionReposScreen` / `ExtensionReposScreenModel`
- `ExtensionRepoRepository` and related interactors
- `extension_repos.sq`
- `ExtensionRepoBackupCreator` and `ExtensionRepoRestorer`
- `ExtensionStore`/repository code in the official tag as part of the transition
- `ExtensionLoader`, `TrustExtension`, extension API/model, migrations, and settings routes

The current worktree still contains KMK code and schema centered on `extension_repos`, while it also contains some store-named files from the prior fork state. This must be reconciled as one coherent model. A build passing is not enough: a user must be able to retain configured repositories, load extensions, trust signatures, install/update extensions, back up and restore repository configuration, and migrate old data without losing sources.

### High H-01: Official extension install/update fixes overlap directly with KMK evaluation

Official 1.14.0 changes the extension API, loader, installer, install service, package-installer path, Shizuku path, and extension manager. It specifically fixes:

- installs stuck in pending;
- installs stuck in installing;
- package-manager mass install/uninstall crashes;
- extension install/update ANRs.

KMK source evaluation installs and uninstalls many extensions, uses installer overrides, private/Shizuku modes, quarantine markers, and batch cancellation. The overlapping files include:

- `app/src/main/java/eu/kanade/tachiyomi/extension/ExtensionManager.kt`
- `app/src/main/java/eu/kanade/tachiyomi/extension/api/ExtensionApi.kt`
- `app/src/main/java/eu/kanade/tachiyomi/extension/installer/Installer.kt`
- `PackageInstallerInstaller.kt`
- `ShizukuInstaller.kt`
- `ExtensionInstallActivity.kt`
- `ExtensionInstallService.kt`
- `ExtensionInstaller.kt`
- `ExtensionLoader.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationRunner.kt`
- `SourceEvaluationCleanupPolicy.kt`
- `SourceEvaluationJobState.kt`

The upstream installer lifecycle must be merged first conceptually, then KMK evaluation must be checked against the new lifecycle. Particular risks are duplicate installation requests, uninstalling the wrong package after a retry, continuing evaluation after an install failure, and treating a pending package as a loaded extension.

### High H-02: Official source API and source-manager changes overlap recommendation evaluation

Official 1.14.0 updates `CatalogueSource`, `Source`, `MangasPage`, `SManga`, `SChapter`, HTTP/metadata source contracts, `AndroidSourceManager`, `MergedSource`, MangaDex, E-Hentai-related sources, and source API models. It also adds source-level related-manga support detection and removes the default Brotli interceptor.

KMK relies on these contracts in:

- `exh/recs/sources/RecommendationPagingSource.kt`
- `exh/recs/sources/CrossExtensionGenreSearchSource.kt`
- `exh/recs/evaluation/SourceEvaluationRunner.kt`
- `exh/recs/evaluation/SourceRecommendationFitProbe.kt`
- `exh/recs/evaluation/SourceEvaluationCatalogueEnricher.kt`
- `exh/recs/evaluation/SourceEvaluationScorer.kt`
- `exh/recs/RecommendationQueryPlanner.kt`
- `exh/recs/RecommendationPagingSource.kt`
- `app/src/main/java/eu/kanade/tachiyomi/source/AndroidSourceManager.kt`

The source API must be updated without breaking extension ABI compatibility. The evaluation code must then explicitly handle absent related-manga support, changed metadata availability, changed filter behavior, and changed network compression/interceptor behavior.

### High H-03: Official backup/sync changes overlap KMK proto extensions

Official 1.14.0 changes backup and sync models, options, creators/restorers, `SyncManager`, `SyncService`, and `SyncYomiSyncService`. The current KMK backup adds fork-specific proto fields in the `620-627` range and current documentation reserves further KMK numbers.

Required checks before integration:

- Confirm no official field now occupies a KMK-reserved proto number.
- Keep all existing KMK fields wire-compatible.
- Reconcile extension repo/store backup fields rather than serializing both models accidentally.
- Preserve manga taste, tag taste, aliases, disabled sources, cross-source groups, quality signals, seen keys, and group primaries.
- Verify sync merge behavior separately from local backup restore.
- Add round-trip tests for old and current backups, including missing optional fields and unknown future fields.

### High H-04: Official manga/chapter/reader/migration behavior overlaps KMK reader features

Official 1.14.0 changes manga/chapter models and repositories, migration behavior, adjacent chapter loading, pager/webtoon adapters, manga screen state, and the migration flow. It specifically improves migration transfer of last-page-read and adjacent-chapter loading.

KMK has active custom code in:

- `ReaderActivity.kt`
- `ReaderViewModel.kt`
- reader timer and schedule packages
- `MangaScreen.kt`
- `MangaScreenModel.kt`
- best-version and cross-extension migration flows
- OCR downloaded-page indexing

The merge must verify that:

- the reading timer/schedule state machine still receives the correct natural/manual chapter transitions;
- the chapter-completion rating prompt still triggers only at the true latest chapter;
- migration preserves last-page-read and does not bypass schedule restrictions;
- best-version migration and grouped-version migration preserve the correct manga/chapter relationship;
- adjacent chapter loading does not create duplicate requests or break the timer grace rules;
- process backgrounding and activity recreation do not serialize KMK screen/state objects.

### High H-05: Official UI selection and lifecycle fixes overlap rated-manga UI and settings

Official 1.14.0 changes selection toolbars, manga/library screens, notes, browse screens, shortcuts, haptic feedback, and background crash handling. KMK also changed:

- rated manga selection mode;
- group actions and group recommendations;
- source evaluation settings and index navigation;
- reading schedule/timer dialogs;
- What's New and release-note navigation.

These must be reconciled by behavior, not just compilation. Selection state must survive recomposition without stale indices, destructive actions must be confirmed, and navigation must not carry non-serializable match modes through Android saved state.

### High H-06: Official tracker/network error handling overlaps KMK recommendation jobs

Official 1.14.0 changes Anilist GraphQL error parsing, MAL informative errors, network exception types/helpers, logging initialization, and debug logging behavior. KMK runs long-lived source-evaluation, For You, recommendation, OCR, and background processes.

The integration must ensure that cancellation remains cancellation, transient network errors remain retryable, authentication/token expiry is user-actionable, and a single source or tracker failure cannot cancel the whole batch or crash the app.

## Official 1.14.0 Change Inventory

The official changelog groups the changes as follows. Every row needs an explicit `integrated`, `adapted`, `not applicable`, or `blocked` result in the implementation plan.

### New

- TachiyomiX 1.6 extension support.
- Extension repository changed to the extension store and a newer extension index.
- Duplicate detection for library tracking.
- Download bookmarked chapters option.
- Removal of the notes text limit.
- EHentai EHTags update.
- Informative MAL errors for unapproved titles.
- Anilist GraphQL error parsing for downtime and token expiry.
- Debug verbose-log disable behavior.

### Improvements

- Optimize the library tracked filter.
- Allow automatic library updates while using a VPN.
- Improve shortcut-helper automation.
- Split sync-on-add into metadata and chapter preferences.
- Mask WebDAV passwords.
- Clean up database indexes/schema.
- Refactor UI selection for race/performance issues.
- Load all adjacent chapter pages in the reader UI rather than only a small fixed window.
- Add SyncYomi sync events.
- Copy last-page-read during migration.
- Add haptic feedback.
- Sync trackers only after chapter updates.
- Rename the extension state term `Obsolete` to `Orphaned`.
- Disable download URL hashing by default.
- Add notification plural forms.
- Add source API related-manga support detection.
- Remove the Brotli interceptor from the default networking path.

### Fixes

- MangaDex delegated-source behavior.
- Merged chapter sorting by source order.
- Anilist GraphQL HTTP errors.
- MangaDex tracker nullability.
- Extension install/update stuck in pending.
- Extension install/update stuck in installing.
- PackageManager mass install/uninstall crash.
- Extension install/update ANR.
- Notes text-selection crash.
- Crash when the app is backgrounded.
- Logging before XLog initialization.

## KMK v0.8.8 State Checked

The latest KMK implementation report describes v0.8.8 as code-complete but explicitly leaves manual QA open. Its own limitations are material to this reconciliation:

- no real-device verification of schedule blocking;
- no real-device verification of chapter-completion rating prompts;
- no real-device verification of settings navigation on phone/tablet;
- no real-device verification of multi-batch source-evaluation continuation;
- no full Compose/Robolectric integration coverage;
- aggregate, rather than per-extension, explanation for unreachable stale evaluations;
- no direct `RecommendsScreenModel` integration harness;
- no genuine production refresh path exercising the new generation guard.

Those gaps must remain visible after the upstream merge. They cannot be marked fixed merely because the upstream build compiles.

## Repository and Process Risks

### Dirty worktree

The branch contains a very large set of modified and untracked source, test, documentation, migration, and generated/build artifacts. There is no clean commit boundary separating the current v0.8.8 state from the future 1.14 reconciliation. Before implementation, create a non-destructive checkpoint commit or archive/branch that captures the current state. Do not reset or discard the current worktree.

### Version metadata inconsistency

The current worktree has multiple independent version concepts:

- Android `versionName` remains `1.13.6`.
- KMK feature release notes report `KMK-Recs v0.8.8`.
- Some current documents contain dates after the audit date or stale dates.
- APK naming is generated/handed off separately from Android version metadata.

The future implementation plan must define one deterministic APK naming rule and update the release notes, What's New entries, documentation, output verification, and handoff path together. Internal build-channel language must not be shown as app-facing feature text.

### Verification environment gap

The current shell has Java `1.8.0_461`; Gradle requires JVM 17 or later. Therefore the claimed Claude verification results cannot be independently reproduced in this environment yet. This is an audit limitation, not evidence that the code fails. A JDK 17+ environment is required before signing off.

## Required Reconciliation Order

The next implementation plan should require Claude to work in this order, without producing a final APK until all phases are complete:

1. Create a source-control checkpoint and document the exact baseline commit/worktree state.
2. Reconcile Gradle/version catalogs and official build configuration.
3. Design and test the append-only database migration bridge before changing repository code.
4. Reconcile extension store/repository, loader, trust, installer, and backup/sync code.
5. Reconcile source API, networking, tracker error handling, and logging.
6. Reconcile library, manga, migration, reader, selection, notes, and shortcut behavior.
7. Reconcile KMK recommendation/evaluation/OCR/timer/schedule behavior against each changed upstream contract.
8. Add or update unit, migration, backup, sync, and targeted integration tests.
9. Run the official verification sequence in a JDK 17+ environment: `spotlessApply`, `spotlessCheck`, SQLDelight generation where needed, unit tests, and the intended APK builds.
10. Perform real-device QA for upgrade, fresh install, extension install/update/uninstall, source evaluation, recommendations, reader schedule/timer, backup/restore, sync, and backgrounding.
11. Only then update the authoritative current-state, versioning, What's New, and community-readiness documents.

## Recommended Decision

Proceed with the 1.14.0 upgrade, but treat it as a dedicated reconciliation project rather than a normal feature fix. Do not ask Claude to “merge upstream” generically. The implementation plan must contain an explicit file inventory, the migration bridge design, proto compatibility checks, upstream/KMK conflict decisions, test fixtures, and device QA cases.

Until B-01 and B-02 are resolved and verified, the correct status is **not ready for a 1.14-based release**. The current 0.8.8 APK may remain a development handoff artifact, but it should not be described as an official-1.14-compatible build.
