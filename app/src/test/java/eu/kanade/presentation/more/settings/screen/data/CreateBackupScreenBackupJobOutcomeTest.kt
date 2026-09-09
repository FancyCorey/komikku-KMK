package eu.kanade.presentation.more.settings.screen.data

import androidx.work.WorkInfo
import eu.kanade.tachiyomi.util.export.SafArtifactOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// KMK -->
// Direct coverage
// for backupJobOutcomeFor, the WorkInfo.State -> SafArtifactOutcome mapping CreateBackupScreenModel
// uses to decide whether the SAF document the picker created for a manual backup gets a cleanup
// offer. The system document picker creates the destination document as soon as a location is
// chosen, before BackupCreateJob ever runs -- so every non-SUCCEEDED terminal (or unresolved) state
// must leave a real cleanup offer behind, never silently report success or pretend an unknown
// WorkManager state proves the destination is empty.
class CreateBackupScreenBackupJobOutcomeTest {

    @Test
    fun `SUCCEEDED maps to SUCCESS`() {
        assertEquals(SafArtifactOutcome.SUCCESS, backupJobOutcomeFor(WorkInfo.State.SUCCEEDED))
    }

    @Test
    fun `FAILED maps to FAILED`() {
        assertEquals(SafArtifactOutcome.FAILED, backupJobOutcomeFor(WorkInfo.State.FAILED))
    }

    @Test
    fun `CANCELLED maps to CANCELLED`() {
        assertEquals(SafArtifactOutcome.CANCELLED, backupJobOutcomeFor(WorkInfo.State.CANCELLED))
    }

    @Test
    fun `a null state (no WorkManager record at all) maps to unresolved, not partial-or-empty`() {
        // e.g. a pruned or KEEP-dropped enqueue where the returned request id never corresponds
        // to a durable running job. That absence cannot prove what happened to the document.
        assertEquals(SafArtifactOutcome.UNRESOLVED, backupJobOutcomeFor(null))
    }

    @Test
    fun `a non-terminal state observed at this point maps to unresolved, not partial-or-empty`() {
        // awaitManualJobTerminalState only returns once WorkInfo.State.isFinished is true (or the
        // record vanished), so RUNNING/ENQUEUED/BLOCKED should never actually reach this mapping --
        // if one somehow does, it must not be treated as proof of an empty destination.
        assertEquals(SafArtifactOutcome.UNRESOLVED, backupJobOutcomeFor(WorkInfo.State.RUNNING))
        assertEquals(SafArtifactOutcome.UNRESOLVED, backupJobOutcomeFor(WorkInfo.State.ENQUEUED))
        assertEquals(SafArtifactOutcome.UNRESOLVED, backupJobOutcomeFor(WorkInfo.State.BLOCKED))
    }
}
// KMK <--
