# KMK Upstream 1.14.0 Reconciliation — Implementation Report

Date: 2026-07-17

Status: All 9 phases complete and verified. App version bumped to reflect the reconciliation
(`versionName` 1.13.6 → 1.14.0, `versionCode` 88 → 89). KMK-Recs feature label unchanged at v0.8.9
(no new recommendation feature was added by this work — see "Versioning decision" below).

This report supersedes the planning documents for status purposes; the plan and audit remain as
historical record of how the work was scoped and estimated:

- `docs/community/KMK_UPSTREAM_1_14_DETAILED_RECONCILIATION_PLAN.md`
- `docs/community/KMK_UPSTREAM_1_14_RECONCILIATION_AUDIT.md`

## Summary

Reconciled the KMK fork against the official Komikku v1.14.0 tag (up from the fork's v1.13.6
baseline), following the plan's 9-phase structure. Every file that differed between the official
v1.13.6 and v1.14.0 tags was diffed directly against the real tag content (never against
changelog summaries), and every touched file was checked for genuine KMK/SY customization before
editing: zero-diff files were ported wholesale from v1.14.0; files with real KMK divergence were
hand-merged preserving every existing behavior. 12 commits landed across the 9 phases (git range
`9c25317aa..3987cb204` on branch `kmk-recs`), touching 228 files (30 added, 19 deleted, 175
modified, 4 renamed).

## Phase-by-phase outcome

**Phase 1 — Baseline/inventory.** Completed in an earlier session (predates this report's git
range); established the file-by-file diff methodology used throughout.

**Phase 2 — DB migration bridge.** Completed in an earlier session. Migration 63 (`63.sqm`)
appends the official 1.14.0 schema changes (`extension_repos` → `extension_store` table
conversion, `mangas.memo`/`chapters.memo` columns) after KMK's own migrations 45-62, without
renumbering or overwriting any of them.

**Phase 3 — Extension stores/loaders/installers.** Ported the extension-store domain/data layer
wholesale (`mihon.domain.extension.*`, `mihon.data.extension.*`), replacing the old
`mihon.domain.extensionrepo.*` package. Renamed `BackupExtensionRepos` → `BackupExtensionStore`,
`ExtensionRepoBackupCreator`/`ExtensionRepoRestorer` → `ExtensionStoresBackupCreator`/
`ExtensionStoreRestorer`. Added TachiyomiX 1.6 support (`SUPPORTED_LIB_VERSIONS`,
`tachiyomix.name`/`tachiyomix.extensionLib`/`tachiyomix.contentWarning` metadata keys). Found the
extension installer stack (Shizuku/private/root install paths) was already reconciled from earlier
KMK work — no changes needed there.

**Phase 4 — Source API and networking.** Folded `CatalogueSource`'s Observable-based fetch methods
into the base `Source` interface (`getPopularManga`/`getLatestUpdates`/`getSearchManga`/
`getMangaUpdate`), replacing the old `getMangaDetails`+`updateManga.awaitUpdateFromSource`+
`getChapterList`+`syncChaptersWithSource.await` dance with the new
`mihon.domain.source.interactor.UpdateMangaFromRemote` interactor across ~15 call sites. Widened
consumer code from `CatalogueSource` to `Source` wherever it didn't depend on deprecated
CatalogueSource-only APIs. Ported `NetworkHelper`/`HttpException`/logging-init-safety helpers
(`EHLogLevel`, `ResettableLogger`, `safeXLogTag`) wholesale, preserving KMK's own recommendation
diagnostics untouched. Added the official related-manga capability API
(`supportsRelatedMangas`/`getRelatedMangaList`) — confirmed byte-for-byte identical to the v1.14.0
tag. Bumped `kotlinx-coroutines-bom` to 1.11.0 (required for the 3-arg `resume` overload used by
ported `OkHttpExtensions.kt`) and fixed a missing BOM-alignment gap in `core/common`'s build config
that was causing it to resolve a stale transitive coroutines version.

**Phase 5 — Backup/restore/sync/proto compatibility.** The highest-risk phase, given the same care
as Phase 2:
- Confirmed the KMK proto range 620-629 (taste system) has zero collision with anything upstream
  changed — upstream never touches fields past 106 in `Backup.kt`.
- Found and closed a real gap: Phase 2's `mangas.memo`/`chapters.memo` columns were wired for
  direct DB reads/updates but explicitly left "vacant" for backup/restore and for new-row inserts.
  Wired `BackupManga`/`BackupChapter` proto fields (112/13), the `insert` queries in
  `mangas.sq`/`chapters.sq` (which were missing `memo` entirely — new rows silently fell back to
  the SQL default), and `ChapterUpdate`'s missing `memo` field.
- Found a second, deeper memo gap while auditing for other issues: `SManga`/`SChapter`
  (source-facing models) already carried `memo` from the Phase 4 wholesale port, but the
  conversion glue (`Chapter.toSChapter()`/`copyFromSChapter()`/`toDbChapter()`,
  `Manga.toSManga()`/`copyFrom()`, `SManga.toDomainManga()`, `ShouldUpdateDbChapter`) never
  propagated it — any memo value an extension source actually returned would have been silently
  dropped before reaching the DB.
- Found and fixed a real **data-loss risk**: upstream renames the underlying SharedPreferences
  keys backing sync settings (`"library_entries"` → `"sync_library_entries"`, `"webdav_url"` →
  `"connection_webdav_url"`, etc.) to avoid a key-namespace collision. A naive Kotlin-string-literal
  rename (my first instinct) would have silently reset every user's sync configuration and dropped
  stored WebDAV/Google Drive credentials on upgrade. Ported upstream's own `SyncPrefKeyMigration`
  (mihon.core.migration, version `80f`) instead, extended with KMK's own WebDAV key renames.
- Finished the `extensionRepoSettings` → `extensionStores` rename Phase 3 left half-done in
  `BackupOptions`/`RestoreOptions`/`SyncSettings`.
- Added real partial-restore/malformed-backup tests against a genuine in-memory SQLDelight
  database (not mocks): `MangaChapterMemoRestorePathTest` exercises the real generated
  `mangasQueries.insert`/`update` and `chaptersQueries.insert` calls (including a transaction
  rollback proving per-manga atomicity), and `Kmk114MemoBackupRoundTripTest` covers old-backup
  compatibility, forward unknown-field tolerance, and malformed/truncated-backup decode behavior.
- Found (but did not fix, since it predates this reconciliation) a real bug in
  `BackupDecoder.decode()`: a truncated-but-well-formed backup throws `IndexOutOfBoundsException`
  from kotlinx.serialization's protobuf reader, which is not a `SerializationException` and so
  isn't caught by the existing `catch (_: SerializationException)` — the app would crash instead
  of showing the "invalid backup file" message. Confirmed via direct tag diff that
  `BackupDecoder.kt` is byte-identical between v1.13.6 and v1.14.0, so this is a latent,
  version-independent bug, not a reconciliation regression. Documented in a test and flagged as a
  separate follow-up task rather than patched as a side effect of this reconciliation.

**Phase 6 — Library/manga/migration/reader.** Merged `MangaScreenModel`'s
`fetchMangaFromSource`/`fetchChaptersFromSource` into the single `fetchAllFromSource(manualFetch,
fetchDetails, fetchChapters)` upstream now uses, and reordered tracker sync to run after chapter
update instead of racing it concurrently (upstream's own "Only sync trackers after update
chapters" fix). Fixed a repeated `source.baseUrl` → `source.getHomeUrl()` gap across migration,
extension-details, and source-browse UI screens (`getHomeUrl()` correctly resolves the true home
page for delegated/enhanced sources, where `baseUrl` alone can point at the wrong domain) — this
affected "open in browser" URLs, extension-cookie clearing, and installed-source search matching.
Triaged `MigrationListScreenModel.kt`'s apparent 104-line divergence and found it was a false
alarm: legitimate pre-existing KMK `SourceMatchScorer` smart-migration-matching logic coexisting
correctly with the already-ported upstream changes, not a missing port.

**Phase 7 — Trackers/updates/notifications/UI.** Fixed AniList's GraphQL error handling (the old
code threw a generic HTTP-status exception before AniList's own in-body GraphQL error message —
e.g. token expiry — ever got parsed) by porting upstream's `awaitALSuccess()` pattern across all 8
AniList API call sites, preserving KMK's own error-message extraction. Added the MAL
unapproved-title error (`MALTitleNotApproved`) so adding a MAL "pending approval" title shows an
informative message instead of a bare "HTTP 400" toast. Switched the update-error notification to
proper plural forms and applied the "Obsolete" → "Orphaned" extension terminology change across
strings, debug-info text, and crash logs. Fixed a real crash-on-backgrounding bug in `Manga.kt`
(Android's process-state-saving path can attempt plain Java reflection-based serialization of held
objects; `Manga` now proxies through `kotlinx.serialization` via `writeReplace`/`readResolve`
instead). Confirmed the MangaDex-tracker-covers fix was already applied (0 diff). Explicitly
searched for and found **no matching commits** in the real v1.13.6..v1.14.0 range for several items
that had been named as things to look for (VPN library updates, SyncYomi events, haptic feedback,
shortcut-helper behavior, "selection race" fixes) — these do not correspond to any actual upstream
change in this release; documented as "searched, not found" rather than invented.

**Phase 8 — Preserve/validate v0.8.9.** Verified directly (not assumed): the What's New renderer
and all 76 historical entries plus the v0.8.9 entry are untouched (zero commits this reconciliation
touched `KmkRecsReleaseNotes.kt` or the What's New screens/dialogs); `KmkRecsReleaseNotesTest`'s 9
tests pass. The Recommendation Settings search index's 7 category destinations (For You, Source
Priority, Taste/Tags, Evaluation, Discovery, Installer, Diagnostics) are all present and unchanged;
`RecommendationSettingsSearchIndexTest`'s 17 tests confirm ranked matching (title-prefix >
title-contains > synonym > summary/category), punctuation/whitespace normalization, and
case-insensitivity all still work. The category-level-not-per-control search limitation is still
accurately documented in `NEXT_WORK.md`. Confirmed via `git diff --stat` that zero commits this
reconciliation touched `exh/recs/**` beyond the single Phase 3+4 `CatalogueSource`→`Source`
widening pass (already verified safe), any OCR file, or any reader-UI file.

**Phase 9 — Tests, validation, final APK.** This report, plus the full verification sequence and
version/APK finalization described below.

## Files changed

228 files across the reconciliation (30 added, 19 deleted, 175 modified, 4 renamed). Full list:
`git diff --stat 9c25317aa..3987cb204` (or `git diff --name-status` for the categorized list) in
the `komikku-source` repo. Grouped by area:

- **Extension store domain/data** (new): `mihon.domain.extension.*` (7 files),
  `mihon.data.extension.*` (6 files); (deleted): `mihon.domain.extensionrepo.*` (10 files),
  `mihon.data.repository.ExtensionRepoRepositoryImpl.kt`.
- **Backup models/creators/restorers**: `Backup.kt`, `BackupManga.kt`, `BackupChapter.kt`,
  `BackupExtensionStore.kt` (renamed from `BackupExtensionRepos.kt`), `BackupOptions.kt`,
  `RestoreOptions.kt`, `MangaBackupCreator.kt`, `MangaRestorer.kt`, `BackupCreator.kt`,
  `BackupRestorer.kt`, `PreferenceBackupCreator.kt`, `ExtensionStoresBackupCreator.kt`/
  `ExtensionStoreRestorer.kt` (renamed).
- **Source API** (`source-api/`): `Source.kt`, `CatalogueSource.kt`, `HttpSource.kt`,
  `ParsedHttpSource.kt`, `MetadataSource.kt`, `FollowsSource.kt`, `SManga.kt`/`SMangaImpl.kt`,
  `SChapter.kt`/`SChapterImpl.kt`, `MangasPage.kt`, `SMangaUpdate.kt` (new),
  `DelegatedHttpSource.kt`, `EnhancedHttpSource.kt`.
- **Networking/logging** (`core/common/`): `NetworkHelper.kt`, `NetworkPreferences.kt`,
  `OkHttpExtensions.kt`, `HttpException.kt` (new), `EHLogLevel.kt`, `EHNetworkLogging.kt`,
  `Logging.kt`, `ResettableLogger.kt` (new), `JsonObject.kt` (new); deleted
  `IgnoreGzipInterceptor.kt`.
- **Migration**: `Migrations.kt`, `SyncPrefKeyMigration.kt` (new), `TrustExtensionRepositoryMigration.kt`,
  `MigrateMangaUseCase.kt`, `63.sqm` (from an earlier session).
- **Memo pipeline**: `Manga.kt`/`MangaUpdate.kt`/`Chapter.kt`/`ChapterUpdate.kt` (domain),
  `MangaMapper.kt`/`ChapterMapper.kt`/`MangaRepositoryImpl.kt`/`ChapterRepositoryImpl.kt`/
  `DatabaseAdapter.kt` (data), `mangas.sq`/`chapters.sq`, `SManga.kt` (mihon.domain.manga.model),
  `ShouldUpdateDbChapter.kt`, `SyncChaptersWithSource.kt`.
- **Sync**: `SyncManager.kt`, `SyncService.kt`, `SyncPreferences.kt`, `SyncSettings.kt`,
  `SyncSettingsSelector.kt`.
- **UI**: `MangaScreenModel.kt`, `ExtensionDetailsScreen.kt`, `ExtensionsScreen.kt`,
  `ExtensionsScreenModel.kt`, `ExtensionsTab.kt`, `ExtensionDetailsScreenModel.kt`,
  `BrowseSourceScreen.kt`, `BrowseSourceScreenModel.kt`, `MigrateSourceSearchScreen.kt`,
  `MigrateSearchScreenModel.kt`, `FeedScreen.kt`/`FeedScreenModel.kt`, `GlobalSearchScreen.kt`/
  `GlobalSearchScreenModel.kt`, `SearchScreenModel.kt`, `DeepLinkScreenModel.kt`,
  `MainActivity.kt`, `SettingsAdvancedScreen.kt`, `SettingsBrowseScreen.kt`,
  `SettingsTrackingScreen.kt`, `MigrationConfigScreen.kt`, `MigrationListScreenModel.kt`,
  `SmartSourceSearchEngine.kt`, `CrashLogUtil.kt`.
- **Trackers**: `AnilistApi.kt`, `MyAnimeListApi.kt`, `MyAnimeListInterceptor.kt`.
- **Library/EXH**: `LibraryUpdateJob.kt`, `LibraryUpdateNotifier.kt`, `MetadataUpdateJob.kt`,
  `EHentaiUpdateWorker.kt`, `EHentaiUpdateNotifier.kt`, `EHentaiUpdateHelper.kt`,
  `EHentai.kt`, `Lanraragi.kt`, `MangaDex.kt`, `MergedSource.kt`, `NHentai.kt`, `EightMuses.kt`,
  `Pururin.kt`, `GalleryAdder.kt`, `DebugFunctions.kt`, `FavoritesSyncHelper.kt`, `PageHandler.kt`,
  `MangaDexLoginHelper.kt`, `MangaDexService.kt`, `MangaDexSimilarPagingSource.kt`.
- **exh.recs (recommendation pipeline)**: 27 files, all touched only in the single Phase 3+4
  `CatalogueSource`→`Source` widening pass — see Phase 8 verification above.
- **Extension manager/API**: `ExtensionManager.kt`, `ExtensionApi.kt`, `ExtensionLoader.kt`,
  `AndroidSourceManager.kt`, `Extension.kt` (moved from `app` to `domain`), `TrustExtension.kt`.
- **Build config**: `app/build.gradle.kts` (version bump), `core/common/build.gradle.kts`,
  `data/build.gradle.kts`, `gradle/kotlinx.versions.toml`.
- **i18n**: `strings.xml`, `plurals.xml`.
- **New tests**: `Kmk114ReconciliationMigrationTest.kt`, `Kmk114MemoBackupRoundTripTest.kt`,
  `MangaChapterMemoRestorePathTest.kt`; updated: 15 existing `exh.recs.*`/`exh.source.*` test files
  (mechanical `CatalogueSource`→`Source` fake-class updates from the Phase 4 widening).

## Database / preferences / proto changes

- **Migration 63** (`data/src/main/sqldelight/tachiyomi/migrations/63.sqm`): appends the
  `extension_repos`→`extension_store` conversion and `mangas.memo`/`chapters.memo` column
  additions after KMK's migrations 45-62. Never renumbers or overwrites any KMK migration.
- **Proto (`Backup.kt`)**: field 106 renamed `backupExtensionRepo`→`backupExtensionStores`
  (matches upstream's own rename exactly). KMK's 620-627 taste-system range is untouched and
  confirmed conflict-free (upstream never reaches past field 106). New fields: `BackupManga` proto
  112 (`memo`), `BackupChapter` proto 13 (`memo`) — both `ByteArray`, matching upstream's
  `MemoColumnAdapter`-encoded `JsonObject` scheme exactly, including the `data class`→`class`
  change (`ByteArray` breaks structural equals/hashCode) upstream itself made.
- **SharedPreferences key renames** (via the new `SyncPrefKeyMigration`, version `80f`): sync
  toggles (`library_entries`→`sync_library_entries`, etc.), sync connection keys
  (`sync_client_host`→`connection_sync_client_host`, etc.), and KMK's own WebDAV keys
  (`webdav_url`→`connection_webdav_url`, etc.) — all migrated forward automatically on upgrade,
  preserving existing values.

## Tests run and results (final verification pass, Phase 9)

All commands run synchronously to completion in this session; no backgrounding, no polling.

| Check | Result |
|---|---|
| `spotlessApply` | BUILD SUCCESSFUL, no changes needed |
| `spotlessCheck` | BUILD SUCCESSFUL |
| `:app:testDebugUnitTest` (full suite) | BUILD SUCCESSFUL — **1306 tests, 0 failures, 0 errors** (aggregated directly from the JUnit XML reports) |
| Targeted: `Kmk114ReconciliationMigrationTest`, `KmkMigrationTest` | PASSED |
| Targeted: `Kmk114MemoBackupRoundTripTest`, `MangaChapterMemoRestorePathTest`, `TasteBackupRoundTripTest` | PASSED |
| Targeted: `SyncServiceCrossSourceGroupPrimaryMergeTest`, `CrossSourceGroupPrimaryRestorePolicyTest`, `SeenMangaKeyBackupTest` | PASSED |
| Targeted: `KmkOcrExclusionTest`, all 5 `exh.ocr.*` suites | PASSED |
| Targeted: full `exh.recs.*` package, `ExplicitSourceClassifierTest`, `NonInstalledSourceSuggestionScorerTest` | PASSED |
| `:app:assembleDebug` | BUILD SUCCESSFUL (both before and after the version bump) |

No Robolectric/Compose-UI instrumented test infrastructure exists in this project (pre-existing
gap, not introduced by this reconciliation) — UI-level behavior for touched screens was verified
by direct code inspection and the pure-logic unit tests that back their view models, consistent
with how every prior KMK-Recs release in this project has been verified.

## Known limitations / not verified in this environment

No physical device or emulator was available. The following require manual QA before wide
distribution, exactly as has been true for every prior KMK-Recs release:

- Fresh install and upgrade-from-current-KMK (pre-1.14.0-reconciliation) install paths, including
  the DB migration 63 and the `SyncPrefKeyMigration` preference migration actually running against
  a real installed database/preference store.
- Extension install/update/uninstall through the real installer paths (private, Shizuku, root),
  including the TachiyomiX 1.6 support and the legacy-extension-store-index auto-migration.
- Source Evaluation end-to-end (temporary install → probe → verdict → uninstall) against real
  extensions.
- For You / group recommendations against live sources.
- What's New dialog/screen rendering (phone/tablet, TalkBack, large font, rotation).
- Recommendation Settings search UI (phone/tablet, dark/light, TalkBack, large font, rotation).
- Reader timer/schedule enforcement and the chapter-completion rating prompt flow end-to-end.
- Backup create/restore and sync (WebDAV, Google Drive, SyncYomi-compatible server) against real
  backends, including old-backup compatibility on a real device.
- Rotation, lock screen, and app-backgrounding behavior (the `Manga.kt` Java-serialization crash
  fix in particular should be exercised this way).

## Deviations from the original plan (with reasoning)

1. **Extension installer stack** (Phase 3): the plan anticipated needing to reconcile the
   Shizuku/private/root installer paths; direct diffing found they were already fully reconciled
   from earlier KMK work. No changes made — confirmed via diff rather than assumed.
2. **`MangaScreenModel.fetchAllFromSource` merge** (Phase 4→6): the plan's Phase 4 scope
   implicitly included this, but given the file's size (2300+ lines) and how many call sites
   depended on the two separate functions, the initial Phase 4 pass deliberately kept
   `fetchMangaFromSource`/`fetchChaptersFromSource` split (functionally equivalent, lower risk)
   rather than doing the full structural merge in the same pass as the `UpdateMangaFromRemote`
   interactor rollout. The merge was completed properly in Phase 6 once the rest of the file's
   surrounding changes were being reconciled anyway.
3. **`MigrationListScreenModel.kt`'s 104-line diff** (Phase 6): initially flagged as a possible
   remaining gap based on diff-line-count triage; direct investigation found it was a false
   alarm — the diff noise came entirely from KMK's own pre-existing `SourceMatchScorer`
   smart-migration-matching feature (not present upstream) correctly coexisting with the
   already-ported upstream behavior. No further changes were needed; documented as "investigated,
   not a gap" rather than silently skipped.
4. **Several Phase 7 items not found in the real range**: "VPN library updates," "SyncYomi
   events," "haptic feedback," "shortcut-helper behavior," and "selection race/performance fixes"
   were named as things to check for, but a direct search of the actual v1.13.6..v1.14.0 commit
   log and CHANGELOG.md found no matching commits. Rather than inventing speculative fixes,
   these are documented here as genuinely not applicable to this release.
5. **`BackupDecoder.kt`'s truncated-backup crash** (Phase 5): a real bug was found via a new test,
   but it predates this reconciliation (confirmed byte-identical between v1.13.6 and v1.14.0) and
   is unrelated to anything this reconciliation's diff touches. Rather than folding an unplanned
   product fix into a reconciliation commit, it was documented in the test and spun off as a
   separate flagged follow-up task.
6. **Versioning**: see below — the plan didn't specify how to version the reconciliation itself;
   resolved by reading `RECOMMENDATION_VERSIONING.md`'s own stated boundary between app-level and
   KMK-Recs-feature-level versioning rather than guessing.

## Versioning decision

`RECOMMENDATION_VERSIONING.md` states that upstream Android package versioning
(`app/build.gradle.kts`'s `versionCode`/`versionName`) is tracked separately from the KMK-Recs
feature label, and that the feature label "does not need to change Android install/update behavior
by itself." This reconciliation is fundamentally an upstream app-version sync — it added no new
user-facing recommendation capability, so it does not fit any category in the canonical
recommendation-versioning table (new feature / corrective follow-up to a feature). Accordingly:

- `app/build.gradle.kts`: `versionName` "1.13.6" → "1.14.0", `versionCode` 88 → 89 (next available
  integer after the prior KMK-local value; note upstream's own versionCode for this tag is 80,
  lower than KMK's already-incremented 88, so it cannot be copied directly without breaking
  Android's monotonic-versionCode requirement).
- `KmkRecsReleaseNotes.VERSION_CODE`/`VERSION_NAME` (the KMK-Recs feature trail): **left
  unchanged** at 759 / "KMK-Recs v0.8.9" — no new recommendation feature was added, so no new
  entry belongs in that trail or in the in-app What's New dialog.
- APK filename follows the established `Komikku-v{app version}-kmk.{recs version}-debug.apk`
  pattern with both halves reflecting their respective, independently-tracked values:
  **`Komikku-v1.14.0-kmk.8.9-debug.apk`**.

## Final APK

- Build: `:app:assembleDebug`, universal variant (`app-universal-debug.apk`), matching every prior
  KMK-Recs handoff APK's build command and variant choice.
- Embedded metadata (from `output-metadata.json`): `versionCode = 89`, `versionName =
  "1.14.0-<commit-count>"` (the commit-count suffix is the pre-existing, expected debug-build
  behavior — every prior debug APK in this project's history carries the same suffix pattern).
- Handoff path: `C:\Users\USER\Downloads\Komikku\private\Komikku-v1.14.0-kmk.8.9-debug.apk`.
- Filename and embedded `versionName`/`versionCode` confirmed consistent with each other and with
  this report.

## Follow-up work (not part of this reconciliation, flagged separately)

- Fix `BackupDecoder.decode()`'s truncated-backup crash (pre-existing, unrelated to this
  reconciliation's diff — see "Known limitations" above and the flagged background task).
- All manual-QA items listed above.
