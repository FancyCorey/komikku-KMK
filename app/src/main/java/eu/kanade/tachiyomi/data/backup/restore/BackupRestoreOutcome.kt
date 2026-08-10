package eu.kanade.tachiyomi.data.backup.restore

// KMK Code-Only Completion Plan 2026-07-31 -->
/**
 * Truthful result of [BackupRestorer.restore] once it returns normally (without throwing).
 *
 * `BackupRestorer.restoreFromFile()` launches one coroutine per restore section
 * (categories/manga/preferences/extension repos/taste profile/...) inside a `coroutineScope { }`,
 * so `restore()` only returns once every section has actually finished. Per-item failures inside
 * manga restore and extension-repo restore are already isolated (caught, appended to `errors`, not
 * rethrown) so one bad manga/repo doesn't abort the whole restore -- but a failure in a section with
 * no such isolation (categories, app/source preferences) still propagates out of `restore()` as a
 * thrown exception exactly as before this type was added; that existing propagation contract is
 * deliberately unchanged; this type only classifies the *no-exception* completion path.
 *
 * This is deliberately not a reversal/undo contract, mirroring [mihon.domain.migration.usecases.MigrationOutcome]'s
 * own doc: restoring a backup writes over existing library/category/preference/taste state with no
 * staged snapshot to reverse it from, so [BackupRestoreOutcome] only makes the already-happened
 * result truthfully reportable -- it does not make the operation undoable, and no Action History
 * entry recorded from this type is ever offered an Undo action (see
 * [exh.util.NonUndoableEventJournal]'s doc).
 */
sealed interface BackupRestoreOutcome {
    /** Every requested restore section completed with zero item-level errors recorded. */
    data class Success(val restoredCount: Int) : BackupRestoreOutcome

    /**
     * The restore pipeline ran to completion (no thrown exception), but [errorCount] individual
     * manga and/or extension-repo entries failed and were logged to the restore error file instead
     * of aborting the whole restore. Deliberately not recorded as a non-undoable Action History
     * event -- there is no existing product pattern for a "restore partially failed" event, and the
     * plan this type was introduced under explicitly requires not inventing new UX beyond what the
     * codebase already supports for a partial-failure case.
     */
    data class PartialSuccess(val restoredCount: Int, val errorCount: Int) : BackupRestoreOutcome
}

/**
 * Pure decision extracted from [BackupRestoreJob.doWork] so it is directly unit-testable without a
 * `CoroutineWorker`/`Context`/`WorkerParameters` harness -- an Action History event is recorded only
 * for a manual (non-sync) restore that completed with zero item-level errors, and only while
 * Evaluation Mode is enabled (matching every other non-undoable event in the app).
 */
fun shouldRecordBackupRestoreEvent(
    isSync: Boolean,
    outcome: BackupRestoreOutcome,
    evaluationModeEnabled: Boolean,
): Boolean = !isSync && outcome is BackupRestoreOutcome.Success && evaluationModeEnabled
// KMK <--
