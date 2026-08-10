package eu.kanade.tachiyomi.data.backup

// KMK --> R-008 v0.7.16: structural guard — OCR data must never appear in backup/sync payloads
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * Verifies that `ocr_indexed_page` data is not backed up, exported, or synced.
 *
 * OCR text is local-only private data (R-006/R-008). These tests guard against
 * accidental future addition of OCR fields to backup models.
 */
class KmkOcrExclusionTest {

    // KMK --> R-008 v0.7.16
    @Test
    fun `BackupManga has no OCR fields`() {
        val fieldNames = BackupManga::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse(
            fieldNames.any { it.contains("ocr") },
            "BackupManga must not contain OCR fields — OCR text is local-only. Found: $fieldNames",
        )
    }

    @Test
    fun `BackupChapter has no OCR fields`() {
        val fieldNames = BackupChapter::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse(
            fieldNames.any { it.contains("ocr") },
            "BackupChapter must not contain OCR fields — OCR text is local-only. Found: $fieldNames",
        )
    }

    @Test
    fun `backup models package has no OCR model class`() {
        val backupModelsPackage = "eu.kanade.tachiyomi.data.backup.models"
        val ocrModelClassName = "$backupModelsPackage.BackupOcrIndexedPage"
        val classFound = try {
            Class.forName(ocrModelClassName)
            true
        } catch (_: ClassNotFoundException) {
            false
        }
        assertFalse(
            classFound,
            "BackupOcrIndexedPage must not exist — OCR text is local-only and must not be backed up.",
        )
    }
    // KMK <--
}
