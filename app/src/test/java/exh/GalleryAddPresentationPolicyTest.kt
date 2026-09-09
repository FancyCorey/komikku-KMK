package exh

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GalleryAddPresentationPolicyTest {
    @Test
    fun `evaluation mode never returns a title`() {
        assertNull(GalleryAddPresentationPolicy.safeTitle("Private manga title", evaluationModeEnabled = true))
    }

    @Test
    fun `ordinary title strips controls and collapses whitespace`() {
        assertEquals(
            "Private title next line",
            GalleryAddPresentationPolicy.safeTitle("  Private\u0000 title\n next\tline  ", evaluationModeEnabled = false),
        )
    }

    @Test
    fun `ordinary title is bounded`() {
        val title = GalleryAddPresentationPolicy.safeTitle("x".repeat(500), evaluationModeEnabled = false)
        assertEquals(80, title?.length)
        assertTrue(title!!.endsWith("..."))
    }

    @Test
    fun `blank title is omitted`() {
        assertNull(GalleryAddPresentationPolicy.safeTitle("\n\t", evaluationModeEnabled = false))
    }

    @Test
    fun `gallery events cannot carry URLs or display messages`() {
        val propertyNames = listOf(GalleryAddEvent.Success::class.java, GalleryAddEvent.Fail::class.java)
            .flatMap { it.declaredFields.toList() }
            .map { it.name }
            .toSet()

        assertFalse("galleryUrl" in propertyNames)
        assertFalse("logMessage" in propertyNames)
        assertEquals(
            setOf("UNKNOWN_TYPE", "UNKNOWN_SOURCE", "NOT_FOUND", "CHAPTER_NOT_FOUND", "IMPORT_FAILED"),
            GalleryAddFailureKind.entries.map { it.name }.toSet(),
        )
    }
}
