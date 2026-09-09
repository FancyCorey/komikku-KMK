package eu.kanade.tachiyomi.ui.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ReaderInitialChapterPolicyTest {
    @Test
    fun `requested chapter remains preferred`() {
        assertEquals(1, ReaderInitialChapterPolicy.resolveIndex(listOf(10L, 20L, 30L), 20L))
    }

    @Test
    fun `stale requested chapter falls back to first ordered chapter`() {
        assertEquals(0, ReaderInitialChapterPolicy.resolveIndex(listOf(10L, 20L, 30L), 99L))
    }

    @Test
    fun `empty chapter list remains unavailable`() {
        assertNull(ReaderInitialChapterPolicy.resolveIndex(emptyList(), 99L))
    }

    @Test
    fun `null chapter ids do not prevent deterministic fallback`() {
        assertEquals(0, ReaderInitialChapterPolicy.resolveIndex(listOf(null, 20L), 99L))
    }
}
