package eu.kanade.tachiyomi.data.backup.restore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * KMK Code-Only Completion Plan 2026-07-31: direct tests for [BackupRestoreOutcome] and the pure
 * [shouldRecordBackupRestoreEvent] decision extracted from [BackupRestoreJob.doWork] -- the actual
 * gate deciding whether a manual, fully successful restore gets a non-undoable Action History event.
 * `BackupRestorer` itself needs a real `Context`/`BackupNotifier`/several restorer sub-objects, so
 * full-pipeline testing is out of scope here (per the plan's "avoid an unreasonable Android test
 * harness" guidance); this covers the truthful-outcome type and the recording decision it feeds,
 * which is where the actual Action History correctness lives.
 */
class BackupRestoreOutcomeTest {

    @Test
    fun `a manual restore with zero errors records an event when Evaluation Mode is on`() {
        assertTrue(
            shouldRecordBackupRestoreEvent(
                isSync = false,
                outcome = BackupRestoreOutcome.Success(restoredCount = 10),
                evaluationModeEnabled = true,
            ),
        )
    }

    @Test
    fun `a manual restore with zero errors records nothing when Evaluation Mode is off`() {
        assertFalse(
            shouldRecordBackupRestoreEvent(
                isSync = false,
                outcome = BackupRestoreOutcome.Success(restoredCount = 10),
                evaluationModeEnabled = false,
            ),
        )
    }

    @Test
    fun `a sync-triggered restore never records an event, even on full success with Evaluation Mode on`() {
        assertFalse(
            shouldRecordBackupRestoreEvent(
                isSync = true,
                outcome = BackupRestoreOutcome.Success(restoredCount = 10),
                evaluationModeEnabled = true,
            ),
        )
    }

    @Test
    fun `a partial success never records an event, even for a manual restore with Evaluation Mode on`() {
        assertFalse(
            shouldRecordBackupRestoreEvent(
                isSync = false,
                outcome = BackupRestoreOutcome.PartialSuccess(restoredCount = 8, errorCount = 2),
                evaluationModeEnabled = true,
            ),
        )
    }

    @Test
    fun `a partial success and a sync restore together still never record an event`() {
        assertFalse(
            shouldRecordBackupRestoreEvent(
                isSync = true,
                outcome = BackupRestoreOutcome.PartialSuccess(restoredCount = 8, errorCount = 2),
                evaluationModeEnabled = true,
            ),
        )
    }

    @Test
    fun `Success and PartialSuccess carry their own counts and are distinct types`() {
        val success = BackupRestoreOutcome.Success(restoredCount = 5)
        val partial = BackupRestoreOutcome.PartialSuccess(restoredCount = 5, errorCount = 1)

        assertEquals(5, success.restoredCount)
        assertEquals(5, partial.restoredCount)
        assertEquals(1, partial.errorCount)
        assertTrue(success is BackupRestoreOutcome)
        assertTrue(partial is BackupRestoreOutcome)
        assertFalse(success == partial)
    }
}
