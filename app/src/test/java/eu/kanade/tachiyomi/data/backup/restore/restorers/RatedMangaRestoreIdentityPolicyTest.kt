package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupMangaTaste
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RatedMangaRestoreIdentityPolicyTest {

    @Test
    fun `accepts a bounded rated non-library identity for minimal restore`() {
        assertTrue(
            RatedMangaRestoreIdentityPolicy.isValid(
                BackupMangaTaste(
                    mangaId = 91,
                    source = 12,
                    url = "/series/alternate",
                    title = "Alternate Version",
                    rating = -2,
                    updatedAt = 100,
                ),
            ),
        )
    }

    @Test
    fun `rejects identities that cannot safely recreate a manga row`() {
        val valid = BackupMangaTaste(source = 12, url = "/series/alternate", title = "Alternate Version")

        assertFalse(RatedMangaRestoreIdentityPolicy.isValid(valid.copy(source = 0)))
        assertFalse(RatedMangaRestoreIdentityPolicy.isValid(valid.copy(url = " ")))
        assertFalse(RatedMangaRestoreIdentityPolicy.isValid(valid.copy(title = " ")))
        assertFalse(RatedMangaRestoreIdentityPolicy.isValid(valid.copy(url = "x".repeat(8_193))))
        assertFalse(RatedMangaRestoreIdentityPolicy.isValid(valid.copy(title = "x".repeat(2_049))))
    }
}
