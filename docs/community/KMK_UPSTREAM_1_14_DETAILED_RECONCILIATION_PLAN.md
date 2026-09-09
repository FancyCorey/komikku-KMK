# KMK / Komikku 1.14.0 Detailed Reconciliation Implementation Plan

**Date:** 2026-07-15  
**Status:** Planning only; no implementation has been approved or performed  
**Target:** KMK private development build based on official Komikku 1.14.0  
**Source audit:** `KMK_UPSTREAM_1_14_RECONCILIATION_AUDIT.md`  
**Authoritative upstream inputs:** git tags `v1.13.6` and `v1.14.0`, official changelog, and the current KMK worktree

## 1. Objective

Reconcile the current KMK worktree with official Komikku 1.14.0 while preserving every intentional KMK feature, database record, backup field, source-evaluation behavior, OCR feature, reader timer/schedule behavior, and build variant.

This is an integration and hardening task, not a version-number edit. Claude must not produce the final APK until every phase below is implemented, tested, documented, and reviewed against the actual working tree.

The final APK must retain the current Android package/build-channel policy. Internal development terms such as “private build” or “public test build” must remain implementation/documentation language only and must never appear as user-facing feature text.

## 2. Mandatory Preflight

Before editing code, Claude must:

1. Read `AGENTS.md`, `docs/community/KMK_UPSTREAM_1_14_RECONCILIATION_AUDIT.md`, this plan, `docs/recommendations/CURRENT_STATE.md`, `docs/recommendations/NEXT_WORK.md`, `docs/recommendations/DOCUMENTATION_RULES.md`, `RECOMMENDATION_VERSIONING.md`, and the encyclopedia/index documentation.
2. Record the current branch, HEAD, dirty files, existing APK metadata, current Android version, current KMK version, and current SQLDelight migration ceiling.
3. Create a non-destructive source-control checkpoint for the current KMK v0.8.8 state before merging any upstream code. Do not reset, clean, or discard existing user/Claude changes.
4. Compare each target file against both `v1.13.6` and `v1.14.0`; do not infer the upstream change from the changelog alone.
5. Produce a preflight table listing every upstream file that has KMK, SY, EXH, OCR, recommendation, timer, schedule, or other custom modifications.
6. Stop and report if a file’s current ownership or intended behavior cannot be determined from code and documentation. Do not guess at merge semantics.

## 3. Phase A: Build and Version Baseline

### Files to inspect and reconcile

- `app/build.gradle.kts`
- `build.gradle.kts`
- `data/build.gradle.kts`
- `gradle/libs.versions.toml`
- `gradle/androidx.versions.toml`
- `gradle/compose.versions.toml`
- `gradle/kotlinx.versions.toml`
- `gradle/sy.versions.toml`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`
- `RECOMMENDATION_VERSIONING.md`
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`

### Required work

1. Port official 1.14.0 Gradle/plugin/dependency changes while preserving KMK build types and application-ID policy.
2. Decide the Android version-code/version-name relationship explicitly. Android `versionCode`, Android `versionName`, KMK feature version, and generated APK filename must not contradict each other.
3. Keep the existing separate KMK feature version line, but update the documented upstream base to 1.14.0 only after the source and migration reconciliation is complete.
4. Preserve `debug`, `release`, `releaseTest`, `foss`, `preview`, `benchmark`, and `kmkPublicTest` behavior unless a direct upstream change requires adaptation.
5. Verify that the `kmkPublicTest` variant does not leak internal build-channel wording into in-app strings.
6. Update the release-notes registry so every KMK version created in the final 0.8 line remains visible in What's New, then add the 1.14-based entry separately. Do not collapse historical version entries.
7. Use the established APK naming scheme and verify the actual output filename instead of documenting a guessed filename.

### Acceptance checks

- The generated APK metadata reports the intended Android base version and KMK feature version.
- All intended variants resolve the correct manifest, resources, application ID, and signing configuration.
- `KmkRecsReleaseNotes.VERSION_CODE` is monotonic and does not pretend that an upstream Android version is a KMK feature version.
- No app-visible strings say “private”, “public”, “community build”, or similar internal workflow language.

## 4. Phase B: Database and Migration Bridge

This phase must be completed before repository code is ported.

### Current collision

The current KMK tree uses `45.sqm` through `62.sqm` for KMK changes. Official 1.14.0 uses `45.sqm` and `46.sqm` for upstream schema changes. Migration files must never be overwritten or renumbered after release.

### Files to inspect

- `data/src/main/sqldelight/tachiyomi/migrations/*.sqm`
- `data/src/main/sqldelight/tachiyomi/migrations/README.md`
- `data/src/main/sqldelight/tachiyomi/data/*.sq`
- `data/src/main/java/tachiyomi/data/DatabaseAdapter.kt`
- `data/src/main/java/tachiyomi/data/TransactionContext.kt`
- `app/src/test/java/eu/kanade/tachiyomi/data/database/KmkMigrationTest.kt`
- official `v1.14.0` migrations `45.sqm` and `46.sqm`

### Required migration design

1. Determine the highest schema version used by current KMK installations, not merely the highest file currently present.
2. Preserve current KMK migrations as immutable history.
3. Add a new append-only KMK migration after the current ceiling to port the official 1.14.0 schema changes.
4. Include the two official performance indexes absent from current KMK `45.sqm`:
   - `idx_history_last_read`
   - `idx_manga_sync_sync_id_remote_id`
5. Port the official extension repository/store conversion against the actual current schema. The migration must safely handle:
   - a database containing `extension_repos`;
   - a database already containing `extension_store`;
   - no repository rows;
   - duplicate signing fingerprints;
   - old legacy repository fields;
   - rerun/idempotency behavior where SQLDelight permits it.
6. Add the official `mangas.memo` and `chapters.memo` columns with the same type, default, and JSON adapter semantics as upstream.
7. Preserve all KMK tables and fields: taste, tag aliases, recommendation cache, disabled sources, source evaluations, probe markers, unsafe extensions, cross-source groups, source quality signals, OCR index, discovery memory/progress, and group primaries.
8. Reconcile any current `54.sqm` comments that describe it as OCR if official migration numbering or current schema ownership requires a different document label. Do not change the historical filename; document the actual applied meaning.
9. Update the migration README to describe the real append-only history and the new upstream-ported migration number.

### Required migration tests

Create fixtures or test resources for:

- fresh database at the current schema;
- official 1.13.6 database at version 44;
- KMK database after v0.7/v0.8 migrations;
- a database with configured extension repositories;
- a database with taste/ratings/groups/OCR data;
- a database with empty optional tables.

For each fixture, verify schema creation, data preservation, indexes, extension-store conversion, memo defaults, foreign keys, and reopening after migration. Run the migration twice where supported to prove no destructive duplicate operation occurs.

## 5. Phase C: Extension Repository, Store, Loader, and Installer

### Files to reconcile

- `app/src/main/java/eu/kanade/tachiyomi/extension/ExtensionManager.kt`
- `app/src/main/java/eu/kanade/tachiyomi/extension/api/ExtensionApi.kt`
- `app/src/main/java/eu/kanade/tachiyomi/extension/installer/Installer.kt`
- `PackageInstallerInstaller.kt`
- `ShizukuInstaller.kt`
- `ExtensionInstallActivity.kt`
- `ExtensionInstallService.kt`
- `ExtensionInstaller.kt`
- `ExtensionLoader.kt`
- `app/src/main/java/eu/kanade/domain/extension/interactor/*`
- `app/src/main/java/mihon/domain/extensionrepo/*`
- `data/src/main/java/mihon/data/extension/*`
- `domain/src/main/java/mihon/domain/extensionrepo/*`
- `data/src/main/sqldelight/tachiyomi/data/extension_repos.sq`
- `data/src/main/sqldelight/tachiyomi/data/extension_store.sq`
- `app/src/main/java/eu/kanade/presentation/more/settings/screen/browse/*`
- `app/src/main/java/eu/kanade/tachiyomi/data/backup/*Extension*`
- `app/src/main/java/eu/kanade/tachiyomi/data/sync/*`
- `mihon/core/migration/migrations/*Repo*`

### Required behavior sequence

#### Repository/store loading

1. Load persisted repositories/stores through one repository abstraction.
2. Migrate old records before `ExtensionLoader` or `ExtensionApi` requests the list.
3. Fetch indexes using the official 1.14 model and preserve source/repo identity needed by KMK evaluation.
4. Deduplicate by the official identity fields, not by display name.
5. Keep language, NSFW, signature, package, and source metadata intact.
6. Report one repository failure without preventing other repositories from loading.

#### Extension discovery

1. Reconcile official `ExtensionApi.findExtensions()` and update checks with KMK's “not installed source suggestions” pipeline.
2. Ensure source-evaluation candidates include all eligible uninstalled extensions from every configured store, not only the first visible page.
3. Preserve explicit source-language, NSFW, blocked-source, quarantined-source, and disliked-source filters.
4. Ensure installed extensions are removed from the not-installed evaluation list immediately after installation and after process restart.

#### Install/update/uninstall

1. Port official pending/installing/ANR/package-manager fixes first.
2. Keep KMK's installer override behavior for current/private/Shizuku modes.
3. Ensure `SourceEvaluationRunner` waits for a definitive install/load result before probing.
4. Ensure cleanup only uninstalls the package that this evaluation session installed; never uninstall a pre-existing user installation.
5. Make repeated install requests idempotent by package and session.
6. Preserve cancellation semantics: cancellation stops new work, cancels the current request when possible, cleans temporary files, and leaves a truthful resumable state.
7. Keep Shizuku prompts conditional on Shizuku mode; current/private modes must not demand Shizuku.
8. Verify mass install and mass uninstall behavior against the official fix, including Android 16 and the current tablet device.

### Tests

- Repository migration and store loading.
- Multiple repository success/failure isolation.
- Duplicate signing fingerprint handling.
- Install success, pending, installing, timeout, cancellation, update, uninstall, and retry.
- Private installer and Shizuku installer separately.
- Evaluation install/evaluate/cleanup for fresh and already-installed extensions.
- Concurrent batch install requests and package-manager callback ordering.

## 6. Phase D: Source API, Networking, and Extension Compatibility

### Files

- `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/CatalogueSource.kt`
- `Source.kt`
- `source/model/MangasPage.kt`
- `SManga.kt`, `SMangaImpl.kt`, `SChapter.kt`, `SChapterImpl.kt`
- `source/online/HttpSource.kt`
- `ParsedHttpSource.kt`
- `MetadataSource.kt`
- `FollowsSource.kt`
- `source-local/*`
- `app/src/main/java/eu/kanade/tachiyomi/source/AndroidSourceManager.kt`
- `exh/source/DelegatedHttpSource.kt`
- `exh/source/EnhancedHttpSource.kt`
- `exh/recs/sources/*`
- `core/common/src/main/kotlin/eu/kanade/tachiyomi/network/*`
- `core/common/src/main/kotlin/exh/log/*`

### Required work

1. Reconcile source interface changes without breaking installed extension ABI loading.
2. Update delegated/enhanced sources to implement new defaults and support checks.
3. Port the source-related-manga capability flag and make recommendation code check it before invoking related-manga requests.
4. Preserve KMK catalogue sampling through `popularMangaRequest`, `latestUpdatesRequest`, `fetchSearchManga`, and detail enrichment, but adapt to changed nullability/model fields.
5. Port the Brotli interceptor change deliberately. Confirm which extensions need explicit Brotli support and ensure evaluation and ordinary browsing use the same network policy.
6. Preserve cancellation and timeout behavior in `SourceEvaluationRunner`, `SourceRecommendationFitProbe`, `RecommendationPagingSource`, OCR, and background jobs.
7. Update network error classification so HTTP errors, offline errors, timeouts, unsupported operations, authentication failures, and cancellation remain distinct.
8. Ensure logs are initialized before any source evaluation or extension load can emit a log. Preserve KMK diagnostic keys without logging credentials, URLs containing private tokens, or manga content.

### Tests

- Source API compile/ABI checks against representative extension artifacts.
- Related-manga capability true/false paths.
- Popular/latest/search/detail enrichment with missing metadata.
- Brotli and non-Brotli responses.
- Offline, timeout, HTTP, unsupported-operation, and cancellation classification.
- Log initialization before app/source startup.

## 7. Phase E: Backup, Restore, Sync, and Proto Compatibility

### Files

- `app/src/main/java/eu/kanade/tachiyomi/data/backup/models/Backup.kt`
- all `Backup*` model files
- `BackupCreator.kt`, `BackupOptions.kt`
- all creators/restorers
- `BackupRestorer.kt`, `RestoreOptions.kt`
- `SyncManager.kt`
- `SyncService.kt`
- `SyncYomiSyncService.kt`
- `WebDavSyncService.kt`
- `app/src/main/java/eu/kanade/domain/sync/*`
- `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md`

### Required work

1. Compare every upstream backup field and proto number against KMK's reserved `620-629` range.
2. Preserve existing KMK proto numbers exactly; never renumber released fields.
3. Reconcile extension repository/store backup models so old backups restore into the new store model and new backups remain readable by the intended KMK versions.
4. Preserve all KMK taste/group/OCR/source-quality/discovery fields.
5. Ensure optional missing fields decode as empty/default without aborting unrelated restore sections.
6. Keep per-section and per-row restore error isolation.
7. Reconcile sync merge behavior for backup feeds, trackers, chapters, stores, and KMK data.
8. Preserve timestamps and conflict semantics for ratings, group primaries, source quality, and seen/not-interested state.
9. Add tests for backup round trips, unknown fields, old backups, partial failures, sync merge, and sync cancellation.

## 8. Phase F: Library, Manga, Migration, Reader, and Notes

### Files

- `app/src/main/java/eu/kanade/tachiyomi/data/library/LibraryUpdateJob.kt`
- `MetadataUpdateJob.kt`
- `LibraryUpdateNotifier.kt`
- `ui/library/LibraryScreenModel.kt`
- `ui/library/LibraryTab.kt`
- `ui/manga/MangaScreen.kt`
- `ui/manga/MangaScreenModel.kt`
- `presentation/manga/*`
- `ui/browse/migration/manga/MigrateMangaScreenModel.kt`
- `MigrateMangaScreen.kt`
- `ui/browse/migration/search/*`
- `ReaderActivity.kt`
- `ReaderViewModel.kt`
- `reader/viewer/pager/PagerViewerAdapter.kt`
- `reader/viewer/webtoon/WebtoonAdapter.kt`
- KMK timer/schedule/prompt files
- `MangaNotesTextArea.kt`

### Required checks

1. Port tracked-library filter optimization without changing KMK rated/known/not-interested visibility policy.
2. Port migration last-page-read copying and verify Best Version migration retains the correct chapter progress.
3. Port adjacent-chapter page loading while preserving timer and schedule natural/manual transition flags.
4. Ensure chapter-completion rating prompts still trigger only for genuine latest-chapter completion and never duplicate after rotation, backgrounding, or process recreation.
5. Remove the official notes text limit while preserving KMK note serialization and text-selection crash fixes.
6. Reconcile merged-chapter source-order sorting with KMK grouped-version and source-quality behavior.
7. Verify backgrounding does not serialize non-serializable KMK objects or retain stale screen models.
8. Port haptic feedback and shortcut-helper changes without adding inappropriate feedback to long-running evaluation/OCR jobs.

### Tests and device cases

- Latest chapter, next chapter, previous chapter, manual chapter selection.
- Reader timer and schedule restriction before/after chapter transitions.
- Activity rotation, lock screen, process backgrounding, and restoration.
- Migration with unread/read/bookmarked chapters and nonzero last-page-read.
- Notes long text, selection, save, restore, and rotation.
- Merged chapter ordering.

## 9. Phase G: Tracker, Library Update, and Notification Behavior

### Files

- `data/track/anilist/AnilistApi.kt`
- `data/track/anilist/dto/ALError.kt`
- `data/track/myanimelist/MyAnimeListApi.kt`
- `MyAnimeListInterceptor.kt`
- `data/library/LibraryUpdateJob.kt`
- `data/library/LibraryUpdateNotifier.kt`
- `data/sync/service/SyncYomiSyncService.kt`
- notification string/plural resources

### Required behavior

- Anilist GraphQL downtime and token-expiry errors become actionable UI states, not generic failures.
- MAL unapproved-title errors are distinguishable from network failures.
- Trackers sync only after chapter updates complete.
- VPN library updates continue according to official behavior without duplicating KMK background jobs.
- Notification plural forms use the correct resource module and do not leak internal version wording.
- SyncYomi events cannot cause duplicate chapter updates or duplicate taste/group writes.

## 10. Phase H: Recommendation and Source Evaluation Compatibility

### Files

- `exh/recs/BrowsePersonalRecommendationsScreenModel.kt`
- `BrowsePersonalRecommendationsTab.kt`
- `RecommendsScreenModel.kt`
- `RecommendationQueryPlanner.kt`
- `RecommendationPagingSource.kt`
- `CrossExtensionGenreSearchSource.kt`
- `SourceEvaluationRunner.kt`
- `SourceEvaluationScreenModel.kt`
- `SourceEvaluationCandidateFilter.kt`
- `SourceEvaluationCandidateQueuePolicy.kt`
- `SourceEvaluationContinuationPolicy.kt`
- `SourceRecommendationFitProbe.kt`
- `SourceRecommendationQualityQueue.kt`
- `SourceEvaluationScorer.kt`
- `SourceEvaluationDisplayFilter.kt`
- `data/src/main/java/tachiyomi/data/taste/*`
- all corresponding KMK tests

### Required checks

1. Re-run the source-evaluation candidate pipeline using the post-1.14 extension list and store model.
2. Confirm language, explicit-content, installed-source, blocked-source, disliked-source, quarantined-source, and stale-evaluation policies still produce the intended candidate pool.
3. Confirm continuation cursors remain stable across batches and do not restart at the first candidate.
4. Confirm source evaluation uses Popular/Latest catalogue evidence, bounded detail enrichment, aliases, and metadata confidence exactly as documented.
5. Confirm For You retrieval remains separate from source catalogue quality and recommendation compatibility.
6. Confirm group recommendations use the confirmed group metadata and remain bounded by the v0.8.6 preview budget/concurrency/cache rules.
7. Ensure source API capability changes cannot turn unsupported recommendation calls into batch-wide errors.
8. Preserve error classification and show actionable per-source diagnostics without storing raw sensitive exception text.
9. Re-check cache keys after source ordering, store configuration, visibility, taste, and extension-version changes.

### Tests

- Candidate continuation across 10/25/50/100 batches and more than 100 sources.
- Installed/uninstalled and language-filter changes between batches.
- Updated extension targeted reassessment.
- Source API unsupported-operation path.
- Group recommendation source-row timeout, cancellation, cache hit, cache invalidation, and source expansion.
- For You refresh generation and background cancellation.
- Exact-title/alias/metadata-confidence scoring regression cases.

## 11. Phase I: OCR Compatibility

### Files

- `exh/ocr/OcrIndexService.kt`
- `OcrSearchScreen.kt`
- `OcrSearchScreenModel.kt`
- OCR database schema and migration files
- download/page model changes from upstream

### Required checks

1. Verify upstream chapter/page/download model changes do not invalidate OCR page identity keys.
2. Preserve incremental indexing, recognized-text storage, status/error fields, storage accounting, and deletion.
3. Ensure OCR does not index pages from the wrong source after migration or grouped-version changes.
4. Ensure OCR jobs cancel correctly when the app is backgrounded and do not block ordinary reader/network work.
5. Add tests for page identity, duplicate indexing, failed/empty OCR rows, deletion, and version upgrades.

## 12. Phase J: UI, Localization, and Accessibility

### Files

- official changed presentation/browse/library/manga/settings files
- KMK rated manga screens
- `RecommendationSettingsIndexScreen.kt`
- source evaluation screens
- reader schedule/timer dialogs
- rated bulk-selection toolbar/actions
- `i18n-kmk`, `i18n`, and `i18n-sy` base resources only

### Required work

1. Preserve official selection race/performance fixes.
2. Keep KMK-only strings in `i18n-kmk`/`KMR`; use upstream `MR` only for upstream behavior.
3. Do not edit translated locale files.
4. Verify phone-width layouts for source evaluation, settings index, rated collections, group recommendations, reader dialogs, and OCR search.
5. Verify TalkBack labels, content descriptions, focus order, haptic behavior, and dismiss/back semantics.
6. Verify all dialogs survive recomposition, rotation, and backgrounding without serializing complex objects.

## 13. Verification Gate

Claude must not provide the final APK until all of these are complete:

1. `spotlessApply`
2. `spotlessCheck`
3. SQLDelight generation/schema verification
4. full unit tests for all modules
5. migration tests against fresh, official, and KMK databases
6. backup/restore and sync tests
7. intended debug/private build and test-build outputs
8. static checks for forbidden locale edits, raw logging, stale application/version metadata, and untracked generated artifacts
9. real-device verification on the Samsung tablet and a narrow phone layout if available
10. update all implementation reports, current-state documents, encyclopedia/index references, and What's New entries

## 14. Completion Definition

The reconciliation is complete only when:

- official 1.14.0 behavior is present in the correct architecture;
- all KMK behavior remains functional;
- the migration bridge is append-only and upgrade-tested;
- extension stores/installers/evaluation agree on package/source identity;
- backup/sync preserve all KMK and upstream data;
- reader, migration, schedule, timer, OCR, and recommendation flows pass regression checks;
- no known release-blocking crash or data-loss path remains;
- the current and next-work documents describe the actual code, not the intended plan;
- the APK name and embedded version metadata agree;
- the final APK is placed in the established private handoff location;
- no final build is claimed as 1.14-compatible before the above evidence exists.
