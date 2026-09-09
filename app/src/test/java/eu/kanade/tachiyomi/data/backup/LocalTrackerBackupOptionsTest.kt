package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalTrackerBackupOptionsTest {
    @Test
    fun oldBackupCreateOptionsKeepLocalTrackingEnabled() {
        assertTrue(BackupOptions.fromBooleanArray(BooleanArray(13)).localTracker)
    }

    @Test
    fun newBackupCreateOptionsRoundTripLocalTracking() {
        assertFalse(BackupOptions.fromBooleanArray(BooleanArray(14)).localTracker)
    }

    @Test
    fun oldRestoreOptionsKeepLocalTrackingEnabled() {
        assertTrue(RestoreOptions.fromBooleanArray(BooleanArray(7)).localTracker)
    }

    @Test
    fun newRestoreOptionsRoundTripLocalTracking() {
        assertFalse(RestoreOptions.fromBooleanArray(BooleanArray(8)).localTracker)
    }
}
