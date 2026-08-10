package eu.kanade.presentation.more.settings.screen

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class SettingsDataScreenPrivacyPresentationTest {

    @Test
    fun `storage location presentation never exposes a path or URI`() {
        val source = File("src/main/java/eu/kanade/presentation/more/settings/screen/SettingsDataScreen.kt").readText()
        val storageText = source.substringAfter("fun storageLocationText(").substringBefore("private fun getStorageLocationPref")

        assertFalse(storageText.contains("displayablePath"))
        assertFalse(storageText.contains("invalid_location"))
        assertTrue(storageText.contains("storage_location_configured"))
    }

    @Test
    fun `backup status and sensitive-data warning remain separate information items`() {
        val source = File("src/main/java/eu/kanade/presentation/more/settings/screen/SettingsDataScreen.kt").readText()
        val backupGroup = source.substringAfter("private fun getBackupAndRestoreGroup").substringBefore("private fun getDataGroup")

        assertTrue(backupGroup.contains("last_auto_backup_info"))
        assertTrue(backupGroup.contains("backup_info"))
        assertTrue(
            backupGroup.indexOf("last_auto_backup_info") < backupGroup.indexOf("backup_info"),
            "operational status should be presented before the separate warning",
        )
        assertFalse(backupGroup.contains("last_auto_backup_info") && backupGroup.contains("+ \"\\n\\n\""))
    }
}
