# KMK-Recs v0.8.1-fix1 Rated Manga And Source Evaluation Polish Plan

Date: 2026-07-12

Status: implementation plan only. Do not code until explicitly approved.

Scope type: private KMK-Recs v0.8.1-fix1 corrective follow-up.

## Goal

Fix the issues found after Claude's v0.7.47 Source Evaluation tag-enrichment/database work and v0.8.0 Rated Manga bulk-selection/group-action work.

This is not a new feature expansion. It is a polish/safety/data-lifecycle follow-up so the implemented systems behave consistently with the approved plans:

1. add confirmation before removing a version from a linked group inside the focused linked-version list;
2. include user-selected group primary versions in backup/restore/sync;
3. make the rated-manga top-right Select action enter selection mode without silently selecting the first item;
4. document/adjust the "Set Primary Version" menu behavior so it is clearly available through View Linked Versions;
5. surface Source Evaluation's new v0.7.47 enrichment/evidence counters in a compact explainability UI;
6. update documentation, tests, and versioning.

## Current Findings To Fix

### Finding 1: Linked Version List Removes Links Without Confirmation

Current code:

- `app/src/main/java/exh/recs/links/LinkedVersionListScreen.kt`
  - Row wiring:
    - `onRemove = { screenModel.removeFromGroup(row.key) }`
  - Delete icon:
    - `IconButton(onClick = onRemove)`

Problem:

- Removing a row from a cross-source group is destructive group editing.
- The v0.8.0 plan required confirmation for "remove from group."
- The main rated manga screen has confirmation for remove-from-group, but the focused linked-version list bypasses it.

Required fix:

- Add a confirmation dialog before `screenModel.removeFromGroup(row.key)` is invoked from `LinkedVersionListScreen`.
- Dialog should identify the version/title being removed where possible.
- Removing a version must not clear rating, favorite, history, or manga data.

### Finding 2: Group Primary Version Is Durable User Data But Not Backed Up/Synced

Current code:

- `data/src/main/sqldelight/tachiyomi/data/manga_cross_source_group_primary.sq`
- `data/src/main/sqldelight/tachiyomi/migrations/62.sqm`
- `domain/src/main/java/tachiyomi/domain/taste/model/CrossSourceGroupPrimary.kt`
- `TasteRepository` / `TasteRepositoryImpl`
- `GetCrossSourceGroupPrimary`, `SetCrossSourceGroupPrimary`, `ClearCrossSourceGroupPrimary`

Problem:

- `manga_cross_source_group_primary` stores a user decision: which linked version controls the rated-list cover/title.
- `manga_cross_source_link` itself is already backed up/synced at proto 624.
- Losing the primary version during restore/sync creates inconsistent UX: the group survives but the user's chosen representative does not.

Required fix:

- Add backup/restore/sync support for `CrossSourceGroupPrimary`.
- Use proto number 627 unless Claude discovers a collision. Proto numbers 620-629 are reserved for KMK taste system fields; current known usage:
  - 620 manga tastes;
  - 621 tag tastes;
  - 622 tag aliases;
  - 623 disabled recommendation sources;
  - 624 cross-source manga links;
  - 625 best-version quality signals;
  - 626 seen manga keys.

### Finding 3: Rated Manga Select Action Auto-Selects First Item

Current code:

- `app/src/main/java/exh/recs/loved/RatedMangaScreen.kt`
  - Top app-bar Select action uses:
    - `successState?.displayItems?.firstOrNull()`
    - `screenModel.enterSelection(first.key)`

Problem:

- Pressing "Select" should enter selection mode for discoverability.
- It should not silently select the first visible manga.
- Auto-selecting the first item can cause accidental bulk actions on an unintended manga.

Required fix:

- Add a screen-model action that enters selection mode with no selected items.
- The app-bar Select action should call that.
- Existing long-press behavior should still enter selection mode and select the long-pressed item.
- Tapping items in selection mode should toggle selection as currently implemented.

### Finding 4: "Set Primary Version" Is Not Directly In Item Menu

Current behavior:

- The user can set primary from `LinkedVersionListScreen`.
- The rated item menu exposes `View Linked Versions`.
- It does not directly expose `Set Primary Version`.

Decision:

- This is acceptable if documented and worded clearly.
- Do not add a separate direct "Set Primary Version" menu action unless it can be done without clutter.
- The item menu may use wording such as `View linked versions` and the version-list screen should clearly show primary actions.

Required fix:

- Update implementation docs/current-state docs to say "Set Primary Version is available inside View Linked Versions."
- If UI copy can be improved without clutter, use a subtitle/tooltip/string in the version-list screen.

### Finding 5: Source Evaluation New Evidence Counters Are Persisted But Barely Visible

Current code:

- v0.7.47 added:
  - `detailEnrichmentAttemptCount`;
  - `detailEnrichmentSuccessCount`;
  - `metadataCandidateCount`;
  - `positiveCandidateCount`;
  - `negativeCandidateCount`;
  - `explicitPreferredGroupHitCount`;
  - `learnedPositiveGroupHitCount`;
  - `blockedCandidateCount`;
  - `adultSignalCandidateCount`.
- KMR strings exist:
  - `source_evaluation_metadata_sparse`;
  - `source_evaluation_detail_enriched_count`;
  - `source_evaluation_detail_enrichment_failed_count`;
  - `source_evaluation_positive_negative_summary`;
  - `source_evaluation_verdict_review_explanation`.

Problem:

- These values are computed and stored, but most are not surfaced in the Source Evaluation row UI.
- The user still cannot easily see why a source was marked Strong/Worth Trying/Weak/Needs Review.

Required fix:

- Add compact explainability to Source Evaluation rows without making the phone UI crowded.
- Prefer an expand/collapse detail area per row rather than always adding multiple extra lines.
- Show:
  - enrichment success/attempt count;
  - metadata samples count vs sample count;
  - positive/negative/blocked/adult-risk candidate counts;
  - a short "Needs manual review" explanation when applicable.

## Detailed Implementation Plan

## Part A: Linked Version Remove Confirmation

Files:

- `app/src/main/java/exh/recs/links/LinkedVersionListScreen.kt`
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`
- tests if any UI logic is extracted.

Implementation:

1. Add saveable state:

```kotlin
var pendingRemove by rememberSaveable { mutableStateOf<RatedMangaKey?>(null) }
```

If `RatedMangaKey` is not saveable by default, store primitive values instead:

```kotlin
var pendingRemoveSource by rememberSaveable { mutableStateOf<Long?>(null) }
var pendingRemoveUrl by rememberSaveable { mutableStateOf<String?>(null) }
```

2. Change row wiring:

```kotlin
onRemove = { pendingRemove = row.key }
```

3. Add `AlertDialog`:

- title: `rated_manga_action_remove_from_group`;
- body: new KMR string, e.g. `linked_version_list_confirm_remove`;
- confirm: calls `screenModel.removeFromGroup(key)`;
- dismiss: clears pending state.

4. Confirm button should dismiss first, then call remove.

5. Ensure `screenModel.removeFromGroup()` itself remains safe:

- no rating deletion;
- no favorite/history deletion;
- no crash if link was already removed.

Tests:

- If a pure event reducer is not practical, add at least a screen-model test for `removeFromGroup()` preserving primary/group behavior where possible.
- Existing `LinkedVersionListBuilderTest` should continue to pass.

## Part B: Backup/Restore/Sync For Group Primary Versions

### B1. Backup Model

Add file:

- `app/src/main/java/eu/kanade/tachiyomi/data/backup/models/BackupCrossSourceGroupPrimary.kt`

Suggested model:

```kotlin
@Serializable
data class BackupCrossSourceGroupPrimary(
    @ProtoNumber(1) val groupId: String = "",
    @ProtoNumber(2) val source: Long = 0,
    @ProtoNumber(3) val url: String = "",
    @ProtoNumber(4) val updatedAt: Long = 0,
)
```

Update:

- `app/src/main/java/eu/kanade/tachiyomi/data/backup/models/Backup.kt`

Add:

```kotlin
@ProtoNumber(627) var backupCrossSourceGroupPrimaries: List<BackupCrossSourceGroupPrimary> = emptyList(),
```

If proto 627 is already taken in the current branch, use the next unused 628/629 and document the reason.

### B2. Backup Creation

Files:

- `app/src/main/java/eu/kanade/tachiyomi/data/backup/create/creators/TasteBackupCreator.kt`
- `app/src/main/java/eu/kanade/tachiyomi/data/backup/create/BackupCreator.kt`

Implementation:

1. Inject/reuse:

```kotlin
GetCrossSourceGroupPrimary
```

2. Add:

```kotlin
suspend fun backupCrossSourceGroupPrimaries(): List<BackupCrossSourceGroupPrimary> =
    getCrossSourceGroupPrimary.awaitAll().map { ... }
```

3. Add wrapper in `BackupCreator`:

```kotlin
suspend fun backupCrossSourceGroupPrimaries(options: BackupOptions): List<BackupCrossSourceGroupPrimary> {
    if (!options.tasteProfile) return emptyList()
    return tasteBackupCreator.backupCrossSourceGroupPrimaries()
}
```

4. Add this field where `Backup(...)` is constructed alongside other taste-profile backup fields.

### B3. Restore

Files:

- `app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/restorers/TasteRestorer.kt`
- `app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/BackupRestorer.kt`

Implementation:

1. Add import/model usage for `BackupCrossSourceGroupPrimary`.

2. Inject/reuse:

```kotlin
GetCrossSourceGroupPrimary
SetCrossSourceGroupPrimary
```

or call repository methods directly if that matches the local style.

3. Add:

```kotlin
suspend fun restoreCrossSourceGroupPrimaries(
    primaries: List<BackupCrossSourceGroupPrimary>,
): List<String>
```

Rules:

- Empty list returns empty errors.
- Skip invalid rows:
  - blank `groupId`;
  - `source == 0L`;
  - blank `url`.
- Merge by `groupId`.
- If existing primary is missing or existing `updatedAt < backup.updatedAt`, restore backup primary.
- If existing is newer, keep existing.
- Errors should be collected per group, not abort all restore.

4. Call restore after `restoreCrossSourceMangaLinks(...)` so the group links exist first.

### B4. Sync Merge

Files:

- `app/src/main/java/eu/kanade/tachiyomi/data/sync/SyncManager.kt`
- `app/src/main/java/eu/kanade/tachiyomi/data/sync/service/SyncService.kt`

Implementation:

1. Add `backupCrossSourceGroupPrimaries` to sync payload creation if `SyncManager` manually constructs `Backup`.

2. Add merge helper near `mergeCrossSourceMangaLinks(...)`:

```kotlin
private fun mergeCrossSourceGroupPrimaries(
    localPrimaries: List<BackupCrossSourceGroupPrimary>?,
    remotePrimaries: List<BackupCrossSourceGroupPrimary>?,
): List<BackupCrossSourceGroupPrimary>
```

Rules:

- Key by `groupId`.
- If only local/remote exists, keep it.
- If both exist, keep newer `updatedAt`.
- Ignore blank `groupId` if helper already filters invalid records elsewhere, or preserve and let restore skip. Prefer filtering invalid rows here.

3. Add merged primaries to merged `Backup(...)`.

Tests:

- Backup round-trip test:
  - one primary serializes/deserializes;
  - multiple primaries preserve `groupId/source/url/updatedAt`;
  - empty old backup restores cleanly;
  - newer existing primary wins over older backup;
  - newer backup wins over older existing.
- Sync merge test:
  - local-only;
  - remote-only;
  - same group chooses newer `updatedAt`.

Documentation:

- Update `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md`:
  - add `manga_cross_source_group_primary`;
  - mark durable-user-data;
  - mark backed up/synced yes after fix;
  - proto 627.
- Update `CURRENT_STATE.md`.
- Update v0.8.0 implementation report or add v0.8.1-fix1 implementation report with correction.

## Part C: Select Action Should Enter Empty Selection Mode

Files:

- `app/src/main/java/exh/recs/loved/RatedSelectionReducer.kt`
- `app/src/main/java/exh/recs/loved/LovedMangaScreenModel.kt`
- `app/src/main/java/exh/recs/loved/RatedMangaScreen.kt`
- `app/src/test/java/exh/recs/loved/RatedSelectionReducerTest.kt`

Implementation:

1. Add reducer method:

```kotlin
fun enterEmpty(current: Selection): Selection =
    current.copy(selectionMode = true)
```

or add screen-model method directly:

```kotlin
fun enterSelectionMode() {
    val current = mutableState.value as? State.Success ?: return
    mutableState.value = current.copy(selectionMode = true)
}
```

Prefer reducer for testability.

2. Add `LovedMangaScreenModel.enterSelectionMode()` that preserves selected keys if already present or starts empty if none.

3. Change app-bar Select action:

Current:

```kotlin
val first = successState?.displayItems?.firstOrNull()
if (first != null) screenModel.enterSelection(first.key)
```

Replace with:

```kotlin
screenModel.enterSelectionMode()
```

4. Keep long-press unchanged:

```kotlin
screenModel.enterSelection(item.key)
```

Tests:

- Pressing select/`enterSelectionMode()` sets `selectionMode = true` and leaves `selectedKeys` empty.
- Long-press/`enterSelection(key)` still selects the key.
- Tap toggle outside selection remains no-op.
- Tap toggle inside selection still works.

## Part D: Clarify Set Primary Version Access

Files:

- `app/src/main/java/exh/recs/loved/RatedMangaScreen.kt`
- `app/src/main/java/exh/recs/links/LinkedVersionListScreen.kt`
- KMR strings.
- Docs.

Implementation options:

Preferred minimal path:

1. Keep `Set Primary Version` inside `LinkedVersionListScreen`.
2. Rename or supplement item-menu action from `View Linked Versions` to wording that implies management, for example:
   - `View linked versions`;
   - optional subtitle not possible in `DropdownMenuItem`, so keep concise.
3. In `LinkedVersionListScreen`, ensure the primary star icon has a clear content description.
4. Add a small top or row hint only if it does not clutter:
   - "Use the star to choose the primary version."
   - Must be KMR string.

Alternative:

- Add direct item-menu action `Set Primary Version` only if it opens the same linked-version screen or a compact picker. Do not build a second primary picker.

Acceptance:

- The user can discover that primary version is managed from the version list.
- Docs no longer imply the action is directly in the rated item menu if it is not.

## Part E: Source Evaluation Explainability UI

Files:

- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationDisplayPolicy.kt` if helper expansion needed.
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`
- `app/src/test/java/exh/recs/evaluation/...` for any pure helper.

Current row function:

- `EvaluationResultRow(...)` around `SourceEvaluationScreen.kt`.
- It already checks `SourceEvaluationDisplayPolicy.state(...)`.
- It currently shows:
  - outdated message for outdated/expired;
  - error text for errors;
  - catalogue fit and metadata confidence for normal rows;
  - separate recommendation-quality line.

Required UI:

- Add compact expandable evidence details per row, not always-visible clutter.
- Keep phone layout readable.

Suggested implementation:

1. In `EvaluationResultRow`, add local saved expanded state keyed by evaluation key if possible:

```kotlin
var expanded by rememberSaveable(evaluation.evaluationKey) { mutableStateOf(false) }
```

If this composable cannot safely use rememberSaveable due list recycling, use `remember`.

2. Add a small `TextButton` / icon button / clickable "Details" line for current rows and metadata-sparse rows.

3. Expanded details should show no raw exception traces. It should show:

```text
Enriched X of Y samples
N liked â€¢ M disliked â€¢ B blocked â€¢ A adult-risk
Metadata: C / S samples
```

4. For `NEEDS_MANUAL_REVIEW`, show:

```text
Catalogue metadata was too sparse to confidently score this source. Review it manually before deciding.
```

5. For outdated rows, keep the current "Outdated - reassess needed" primary subtitle. Optional details can remain hidden or disabled because counters are stale.

6. Use existing KMR strings where possible:

- `source_evaluation_detail_enriched_count`;
- `source_evaluation_positive_negative_summary`;
- `source_evaluation_verdict_review_explanation`;
- `source_evaluation_metadata_sparse`.

Add one missing KMR string if needed:

- `source_evaluation_metadata_sample_count`: `%1$d of %2$d samples had metadata`
- `source_evaluation_details_toggle`: `Details`
- `source_evaluation_details_hide`: `Hide details`

7. If extracting a pure helper, create:

```kotlin
internal data class SourceEvaluationEvidenceSummary(...)
internal object SourceEvaluationEvidenceSummaryFormatterPolicy
```

But do not over-engineer. A small composable helper is acceptable if logic remains simple.

Tests:

- Prefer pure tests for any helper that decides:
  - details visible for current/metadata-sparse rows;
  - stale rows considered stale;
  - positive/negative/blocked/adult counts map correctly.
- If no pure helper is extracted, rely on existing scorer tests plus manual UI verification documented in the implementation report.

## Part F: Documentation And Versioning

Files:

- `docs/recommendations/KMK_RECS_V0_8_1_FIX1_RATED_MANGA_AND_SOURCE_EVALUATION_POLISH_IMPLEMENTATION.md`
- `docs/recommendations/README.md`
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md`
- `RECOMMENDATION_VERSIONING.md`
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`

Requirements:

1. Version label:

```text
KMK-Recs v0.8.1-fix1
```

If the codebase only supports numeric `VERSION_CODE`, bump from the current value and set `VERSION_NAME` to `KMK-Recs v0.8.1-fix1` or the closest existing naming convention.

2. Implementation report must include:

- date;
- version/build label;
- user-approved scope;
- files changed;
- behavior changed;
- migrations/proto fields added;
- backup/sync changes;
- tests run;
- build/APK output if produced;
- known limitations;
- deviations from this plan.

3. `NEXT_WORK.md` should remove or update:

- primary-version backup/sync limitation if fixed;
- linked-version remove confirmation issue if fixed;
- Source Evaluation explainability if fixed.

4. `CURRENT_STATE.md` should accurately state:

- primary versions are backed up/synced at proto 627;
- Select action enters empty selection mode;
- linked version removals are confirmed;
- Source Evaluation rows have expandable evidence details.

## Tests And Verification

Run at minimum:

```text
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew :app:testDebugUnitTest
./gradlew assembleDebug
```

If public-test flavor was recently affected by backup/proto/versioning code, also run:

```text
./gradlew :app:assembleKmkPublicTest
```

Specific tests to add/update:

- Backup/proto round-trip for `BackupCrossSourceGroupPrimary`.
- Restore merge precedence for group primaries.
- Sync merge helper test for group primaries.
- `RatedSelectionReducerTest` / screen-model test for empty selection mode.
- `LinkedVersionListBuilderTest` or new test ensuring missing source still renders after primary changes.
- Source Evaluation explainability helper tests if helper is extracted.
- Existing:
  - `KmkMigrationTest`;
  - `TasteBackupRoundTripTest`;
  - `RatedSelectionReducerTest`;
  - `RatedGroupPrimaryResolverTest`;
  - `LinkedVersionListBuilderTest`;
  - `SourceEvaluationScorerTest`;
  - `SourceEvaluationResultListTest`;
  - `SourceRecommendationFitEligibilityTest`.

## Non-Goals

- Do not redesign Rated Manga screens beyond the listed fixes.
- Do not change group recommendation scoring.
- Do not change Source Evaluation scoring formulas in this pass.
- Do not add source-specific hacks for Elf Toon, KaliScan, or any extension.
- Do not add new migrations unless necessary for backup/sync; proto-only backup field does not require a DB migration.
- Do not make a public release unless explicitly requested.

## Acceptance Criteria

- Removing a linked version from `LinkedVersionListScreen` requires confirmation.
- `manga_cross_source_group_primary` is included in backup, restore, and sync.
- Backup proto number is documented and tested.
- Pressing Select in Loved/Liked/Disliked enters selection mode without selecting the first item.
- Long-press still selects the pressed item.
- `Set Primary Version` access is clear and documented through View Linked Versions.
- Source Evaluation rows can show compact details explaining enrichment and evidence counts.
- No raw exception traces are added to Source Evaluation normal rows.
- Full unit tests and debug build pass, or failures are documented with exact cause.

