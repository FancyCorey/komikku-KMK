package exh.taste

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.normalizeTag

class TagNormalizationTest {

    @Test
    fun `lowercase and trim`() {
        assertEquals("action", "Action".normalizeTag())
    }

    @Test
    fun `hyphen becomes space`() {
        assertEquals("sci fi", "Sci-Fi".normalizeTag())
    }

    @Test
    fun `multiple spaces collapsed`() {
        assertEquals("girls love", "Girls  Love".normalizeTag())
    }

    @Test
    fun `mixed punctuation stripped`() {
        assertEquals("boys love", "Boys' Love".normalizeTag())
    }

    @Test
    fun `already normalized string unchanged`() {
        assertEquals("action", "action".normalizeTag())
    }

    @Test
    fun `empty string stays empty`() {
        assertEquals("", "".normalizeTag())
    }

    @Test
    fun `unicode letters preserved`() {
        assertEquals("isekai", "Isekai".normalizeTag())
    }
}
