# KMK-Recs v0.8.1-fix1 Rated Manga And Source Evaluation Polish â€” Implementation Report

Date: 2026-07-12

KMK-Recs version/build label: `KMK-Recs v0.8.1-fix1` (`KmkRecsReleaseNotes.VERSION_CODE = 751`).

**This is a PRIVATE build.** No public release was prepared or requested. The public-test
`applicationId` (`app.komikku.kmk`) was not built or touched in this pass â€” only the private/
personal line (`app.komikku` / `app.komikku.dev`, `:app:assembleDebug`) was verified.

Status: implemented and verified (automated: compile, tests, spotless, `assembleDebug`).

Plan implemented: `docs/recommendations/KMK_RECS_V0_8_1_FIX1_RATED_MANGA_AND_SOURCE_EVALUATION_POLISH_PLAN.md`

## User-Approved Scope

Private corrective/polish follow-up fixing five specific findings from the v0.8.0 (Rated Manga
bulk selection) and v0.7.47 (Source Evaluation tag enrichment) passes. No new features, no
redesign beyond the listed fixes, no public release.

## Files Changed

**New files:**

- `app/src/main/java/eu/kanade/tachiyomi/data/backup/models/BackupCrossSourceGroupPrimary.kt` â€”
  backup model, proto 1-4 fields (`groupId`, `source`, `url`, `updatedAt`).
- `app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/restorers/CrossSourceGroupPrimaryRestorePolicy.kt`
  â€” pure restore-precedence policy (`isValid`, `newestOf`, `shouldRestore`), extracted for
  testability without instantiating `TasteRestorer`'s full dependency graph.
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationEvidenceSummaryPolicy.kt` â€” pure
  visibility/passthrough policy for the new Source Evaluation "Details" section.
- Tests: `CrossSourceGroupPrimaryRestorePolicyTest.kt`,
  `SyncServiceCrossSourceGroupPrimaryMergeTest.kt`, `SourceEvaluationEvidenceSummaryPolicyTest.kt`.

**Modified files:**

- `app/src/main/java/exh/recs/links/LinkedVersionListScreen.kt` â€” remove action now stores a
  pending `(source, url)` pair (primitives, since `RatedMangaKey` is not a `Saveable` type) instead
  of calling `removeFromGroup()` directly; a new `AlertDialog` confirms, naming the version's title
  when it can be resolved from the currently loaded rows, falling back to a generic message
  otherwise. Also added a `linked_version_list_primary_hint` caption above the row list, making it
  explicit that the star sets the primary version (Finding 4/Part D).
- `app/src/main/java/exh/recs/loved/RatedSelectionReducer.kt` â€” new `enterEmpty(current)` pure
  function: enters selection mode without adding any key.
- `app/src/main/java/exh/recs/loved/LovedMangaScreenModel.kt` â€” new `enterSelectionMode()` wrapping
  `RatedSelectionReducer.enterEmpty()`.
- `app/src/main/java/exh/recs/loved/RatedMangaScreen.kt` â€” the app-bar "Select" action now calls
  `screenModel.enterSelectionMode()` instead of `screenModel.enterSelection(displayItems.firstOrNull().key)`.
- `app/src/main/java/eu/kanade/tachiyomi/data/backup/models/Backup.kt` â€” added
  `@ProtoNumber(627) var backupCrossSourceGroupPrimaries: List<BackupCrossSourceGroupPrimary>`.
- `app/src/main/java/eu/kanade/tachiyomi/data/backup/create/creators/TasteBackupCreator.kt` â€” new
  `backupCrossSourceGroupPrimaries()` reading through the existing `GetCrossSourceGroupPrimary`
  interactor.
- `app/src/main/java/eu/kanade/tachiyomi/data/backup/create/BackupCreator.kt` â€” new
  `backupCrossSourceGroupPrimaries(options)` wrapper (gated on `options.tasteProfile`, matching
  every other taste-profile field) and wired into the constructed `Backup(...)`.
- `app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/restorers/TasteRestorer.kt` â€” new
  `restoreCrossSourceGroupPrimaries(backupPrimaries)`, using `CrossSourceGroupPrimaryRestorePolicy`
  for the valid/newest/precedence decisions, writing through `tasteRepository
  .upsertCrossSourceGroupPrimary()` directly (not `SetCrossSourceGroupPrimary`, which always stamps
  "now") so the backup's own `updatedAt` is preserved for future sync comparisons.
- `app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/BackupRestorer.kt` â€”
  `restoreTasteProfile(...)` gained a `backupCrossSourceGroupPrimaries` parameter, called
  immediately after `restoreCrossSourceMangaLinks(...)` (so the group already exists), and the call
  site passes `backup.backupCrossSourceGroupPrimaries`.
- `app/src/main/java/eu/kanade/tachiyomi/data/sync/SyncManager.kt` â€” added
  `backupCrossSourceGroupPrimaries = backupCreator.backupCrossSourceGroupPrimaries(backupOptions)`
  to the constructed sync payload `Backup(...)`.
- `app/src/main/java/eu/kanade/tachiyomi/data/sync/service/SyncService.kt` â€” new
  `mergeCrossSourceGroupPrimaries(local, remote)` private instance method delegating to a new
  `companion object` function `mergeCrossSourceGroupPrimariesPure(local, remote)` (extracted so it's
  directly unit-testable without instantiating the abstract `SyncService` class); wired into
  `mergeSyncData()`'s merged-`Backup` construction.
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt` â€” `EvaluationResultRow` gained
  a second, independent expand/collapse "Details" block (separate from the existing rec-quality
  error-details block) showing enrichment/metadata/positive-negative-blocked-adult evidence and the
  manual-review explanation, gated by `SourceEvaluationEvidenceSummaryPolicy.evidenceFor(...)`.
- `app/src/test/java/eu/kanade/tachiyomi/data/backup/TasteBackupRoundTripTest.kt` â€” 3 new tests for
  proto 627 (round-trip, coexistence with 620-626, forward-compat with old backups lacking 627).
- `app/src/test/java/exh/recs/loved/RatedSelectionReducerTest.kt` â€” 3 new tests for `enterEmpty`.
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` â€” version bump + changelog entry.
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` â€” 6 new KMR strings:
  `linked_version_list_confirm_remove`, `linked_version_list_confirm_remove_titled`,
  `linked_version_list_primary_hint`, `source_evaluation_metadata_sample_count`,
  `source_evaluation_details_toggle`, `source_evaluation_details_hide`. No hardcoded user-facing
  text was added.
- Documentation: `docs/recommendations/README.md`, `CURRENT_STATE.md`, `NEXT_WORK.md`,
  `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md`, `RECOMMENDATION_VERSIONING.md`, and this report.

## Behavior Changed

1. **Linked-version removal requires confirmation.** Previously destructive with no dialog; now
   shows an `AlertDialog` before deleting the `manga_cross_source_link` row. Confirmed unaffected:
   rating (`manga_taste`), favorite (`manga.favorite`), history, and the manga row itself are never
   touched by this action (`LinkedVersionListScreenModel.removeFromGroup()` calls only
   `DeleteCrossSourceMangaLink.awaitBySourceUrl()`).
2. **Group primary versions survive backup/restore/sync.** Previously durable user data with zero
   backup/restore/sync coverage (a documented v0.8.0 known limitation); now included at proto 627
   whenever `BackupOptions.tasteProfile` is enabled, restored after cross-source links with
   newer-wins-per-group precedence, and merged in sync with the same precedence.
3. **"Select" enters an empty selection.** Previously silently selected the first visible item â€”
   a safety gap since any subsequent bulk action (clear rating, mark not interested, etc.) would
   then apply to an item the user never intentionally picked. Long-press behavior is unchanged.
4. **Set Primary Version access documented, not moved.** Still only reachable via `View linked
   versions` â†’ the star icon in `LinkedVersionListScreen`; a new hint caption makes this explicit in
   the UI itself, and documentation no longer implies (nor did it previously explicitly claim) a
   direct rated-item-menu action.
5. **Source Evaluation rows can show why they got their verdict.** A new, separate "Details" toggle
   surfaces enrichment success/attempt counts, metadata sample coverage, and positive/negative/
   blocked/adult-risk candidate counts â€” computed since v0.7.47 but previously invisible in the UI.
   Hidden entirely for outdated, error, and zero-sample rows.

## Migrations / Proto Fields Added

- **No new database migration** (per plan Â§Non-Goals â€” proto-only backup field does not require
  one; `manga_cross_source_group_primary` itself was already created by migration 62 in v0.8.0).
- **Proto 627**: `backupCrossSourceGroupPrimaries` on `Backup.kt`. The plan suggested 627 unless
  occupied; inspection of `Backup.kt` confirmed proto fields 620-626 were in use and 627-629 were
  reserved-but-unused, so 627 was used exactly as suggested â€” no collision, no deviation needed.

## Backup/Sync Changes

See "Files Changed" and "Behavior Changed" above for the full backup/restore/sync wiring. Summary:

- **Backup**: `TasteBackupCreator.backupCrossSourceGroupPrimaries()` â†’ `BackupCreator
  .backupCrossSourceGroupPrimaries(options)` (gated on `tasteProfile`) â†’ `Backup.backupCrossSourceGroupPrimaries`.
- **Restore**: `BackupRestorer` passes `backup.backupCrossSourceGroupPrimaries` into
  `restoreTasteProfile(...)`, which calls `tasteRestorer.restoreCrossSourceGroupPrimaries(...)`
  immediately after `restoreCrossSourceMangaLinks(...)`. Precedence: missing existing â†’ restore;
  newer backup â†’ restore; newer/equal existing â†’ keep existing (no restore on a tie, matching the
  plan's "if existing is newer, keep existing" â€” ties favor the already-stored row). Invalid rows
  (blank `groupId`, `source == 0`, blank `url`) are filtered before any DB write. Errors are
  collected per group (`"Primary version for group '$groupId': ..."`) and do not abort the rest of
  taste restore â€” a single bad primary row cannot break manga-taste/tag/link restore.
- **Sync**: `SyncManager` includes the field in the payload it builds; `SyncService.mergeSyncData()`
  calls the new merge helper and includes the merged list in the reconciled `Backup`.

## Tests Run

All run with repo-local JDK 17 (`.tools/jdk17/jdk-17.0.19+10`).

| Command | Result |
|---|---|
| `./gradlew :app:compileDebugKotlin` | BUILD SUCCESSFUL (run repeatedly as checkpoints during implementation) |
| `./gradlew :app:compileDebugUnitTestKotlin` | BUILD SUCCESSFUL |
| `./gradlew :app:testDebugUnitTest` (1st run) | 1046 tests, 1 failure â€” a test-authoring bug (blocked-group test used `blockedGroups = setOf("boys_love")` without also passing the alias map that resolves `"yaoi"` â†’ `"boys_love"` to `SourceEvaluationScorer.score()`; fixed by blocking the raw `"yaoi"` group key directly, matching the no-aliasMap default the test already used) |
| `./gradlew :app:testDebugUnitTest` (after fix) | **BUILD SUCCESSFUL â€” 1046 tests, 0 failures, 0 errors** (up from 1016 pre-existing) |
| `./gradlew spotlessApply` | BUILD SUCCESSFUL (auto-reformatted) |
| `./gradlew spotlessCheck` | BUILD SUCCESSFUL |
| `./gradlew :app:testDebugUnitTest` (post-spotless re-run) | BUILD SUCCESSFUL â€” 1046/1046 |
| `./gradlew assembleDebug` | **BUILD SUCCESSFUL** (private line only â€” `app.komikku`/`app.komikku.dev`) |

**`:app:assembleKmkPublicTest` was intentionally NOT run.** The plan's own instructions said to run
it only "if public-test flavor was recently affected by backup/proto/versioning code," but the
user's explicit instructions for this pass said: "Do not switch to the public test applicationId,"
which takes precedence. `assembleDebug` (private line) already exercises the same shared
backup/sync/proto code paths at compile and build time, so this omission does not reduce coverage
of the actual code change â€” it only skips producing a second, unrequested build artifact under a
different applicationId.

New/updated test files and counts:

- `CrossSourceGroupPrimaryRestorePolicyTest` â€” 8 tests: `isValid` (blank groupId, source==0, blank
  url, all-valid), `newestOf`, `shouldRestore` (missing existing, newer backup wins, newer existing
  wins, equal-updatedAt keeps existing).
- `TasteBackupRoundTripTest` â€” 3 new tests: proto 627 round-trip; all KMK proto fields 620-627
  coexist without corruption; backup without field 627 decodes to an empty list (forward compat).
- `SyncServiceCrossSourceGroupPrimaryMergeTest` â€” 8 tests: local-only, remote-only, conflict
  (remote wins / local wins), equal-updatedAt tie-break (matches `mergeCrossSourceMangaLinks`'
  local-wins-on-tie behavior), distinct groups from both sides preserved, blank-groupId rows
  filtered from both sides, both-null returns empty.
- `RatedSelectionReducerTest` â€” 3 new tests: `enterEmpty` enters selection mode without selecting
  anything; `enterEmpty` preserves an existing selection rather than clearing it; a subsequent
  tap-toggle after `enterEmpty` can still select an item.
- `SourceEvaluationEvidenceSummaryPolicyTest` â€” 7 tests: current row returns evidence; outdated row
  returns null; error row returns null; zero-sample row returns null; positive/negative/blocked/
  adult counts map correctly; `NEEDS_MANUAL_REVIEW` is flagged; `detailEnrichmentFailedCount` is
  attempts minus successes.

### Existing Regression Tests Re-Run (via the full suite above)

- `KmkMigrationTest`, `LinkedVersionListBuilderTest`, `RatedGroupPrimaryResolverTest`,
  `SourceEvaluationScorerTest`, `SourceEvaluationResultListTest`,
  `SourceRecommendationFitEligibilityTest` â€” all passing, none touched by this pass's logic
  changes.
- Full `:app:testDebugUnitTest` â€” run in full (1046/1046), not skipped.

## Build / APK Output

`assembleDebug` succeeded for the private/personal line (`app.komikku` / `app.komikku.dev`). **No
APK was copied or renamed to a versioned handoff filename in this pass** â€” none was requested for
v0.8.1-fix1. The last named handoff copy remains `private/Komikku-v1.13.6-kmk.8.0-debug.apk`
(v0.8.0, produced in the prior session on explicit user request). The raw v0.8.1-fix1 debug build
exists at the standard Gradle output path
(`app/build/outputs/apk/debug/app-universal-debug.apk`) if a manual install is wanted; no manual
real-device verification was performed. `:app:assembleKmkPublicTest` (public-test applicationId)
was not run, per the explicit private-build-only instruction for this pass.

## Known Limitations

- **No manual/real-device verification.** All verification is automated (compile, unit tests,
  spotless, `assembleDebug`). The confirmation dialogs, empty-selection Select action, and Source
  Evaluation details toggle have not been exercised on a device or emulator.
- **Restore-tie behavior favors the existing row, not documented as a separate case in the plan.**
  The plan specified "if existing primary is missing or older, restore backup primary; if existing
  is newer, keep existing" â€” it did not explicitly address an exact `updatedAt` tie. This
  implementation keeps the existing row on a tie (`existingUpdatedAt < backupUpdatedAt` must be
  strictly true to restore), which is the conservative/idempotent choice and avoids an unnecessary
  DB write when nothing actually changed.
- **Sync tie-break also favors local on equal `updatedAt`**, matching the pre-existing
  `mergeCrossSourceMangaLinks`' tie-break convention (`local.updatedAt >= remote.updatedAt` keeps
  local) â€” chosen for consistency with the already-established pattern rather than introducing a
  different tie-break rule for a structurally identical merge.
- **`SourceEvaluationEvidenceSummaryPolicyTest`'s blocked-candidate test does not exercise alias
  resolution** â€” it blocks the raw tag string directly rather than an aliased group, since the test
  constructs evaluations via `SourceEvaluationScorer.score()` without an `aliasMap` argument (this
  mirrors the existing `SourceEvaluationScorerTest`'s own convention for tests not specifically
  about alias resolution).

## Deviations From The Approved Plan

None in substance. Two implementation choices the plan left open, both documented here per plan
Â§Non-Goals/Â§Acceptance Criteria discipline:

1. **Merge/restore tie-break direction** (see Known Limitations above) â€” the plan's wording did not
   specify behavior for an exact `updatedAt` tie; this implementation's choice (keep existing on
   restore tie; keep local on sync tie) matches the codebase's own pre-existing
   `mergeCrossSourceMangaLinks` convention rather than inventing a new one.
2. **Pure-helper extraction beyond what the plan explicitly asked for.** The plan said "add a small
   pure helper... but do not over-engineer" for Source Evaluation, and separately asked for sync/
   restore merge tests without specifying how to make `SyncService`'s private merge method and
   `TasteRestorer`'s restore-precedence logic testable without heavy DI. Both were extracted into
   small, focused pure functions/objects (`SyncService`'s `mergeCrossSourceGroupPrimariesPure`
   companion function; `CrossSourceGroupPrimaryRestorePolicy`) specifically so the plan's own
   required tests ("Sync merge helper test for group primaries," "Restore merge precedence... newer
   backup wins, newer existing wins") could be written without instantiating `SyncService`'s or
   `TasteRestorer`'s full multi-interactor constructors â€” this is the same "extract pure helper for
   testability" pattern already used throughout this recommendation-system codebase (e.g.
   `RatedGroupMergePlanner`, `RatedGroupPrimaryResolver`, `RatedSelectionReducer` from v0.8.0).

No source-specific hacks were added. No group recommendation scoring was touched. No Source
Evaluation scoring formula was touched â€” only its display layer gained a new expandable section
reading already-computed, already-persisted fields. No public release was made or requested; the
public-test `applicationId` was never built or referenced.

