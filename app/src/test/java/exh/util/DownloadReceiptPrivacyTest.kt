package exh.util

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK -->
class DownloadReceiptPrivacyTest {

    @Test
    fun `receipt fields stay limited to opaque recovery data`() {
        val allowed = setOf("id", "timestamp", "mangaId", "sourceId", "chapterIds")
        val fields = DownloadReceipt::class.java.declaredFields
            .filterNot { it.isSynthetic || it.name == "Companion" || it.name == "\$stable" }

        assertTrue(fields.all { it.name in allowed }, "Unexpected receipt fields: ${fields.map { it.name }}")
        assertTrue(fields.map { it.name }.toSet() == allowed)
    }
}
// KMK <--
