package exh.recs.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

// KMK v0.8.10 -->
class RecommendationSettingsAnchorScrollTest {

    private val keys = listOf("header", "content_a", "content_b", "footer")

    @Test
    fun `a null anchor resolves to null - no scroll`() {
        assertNull(resolveAnchorIndex(keys, null))
    }

    @Test
    fun `a known anchor resolves to its index`() {
        assertEquals(2, resolveAnchorIndex(keys, "content_b"))
    }

    @Test
    fun `the first item resolves to index zero`() {
        assertEquals(0, resolveAnchorIndex(keys, "header"))
    }

    @Test
    fun `an unknown anchor (row not currently composed for this state) resolves to null - safe no-op, not a crash`() {
        assertNull(resolveAnchorIndex(keys, "does_not_exist"))
    }

    @Test
    fun `an empty item list never resolves any anchor`() {
        assertNull(resolveAnchorIndex(emptyList(), "header"))
    }

    @Test
    fun `a blank anchor string is treated as unknown, not the first item`() {
        assertNull(resolveAnchorIndex(keys, ""))
    }
}
// KMK <--
