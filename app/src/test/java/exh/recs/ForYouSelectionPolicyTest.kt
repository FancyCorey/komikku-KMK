package exh.recs

import exh.recs.matching.MangaIdentityKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK v0.8.16 -->
class ForYouSelectionPolicyTest {

    private val keyA = MangaIdentityKey(1L, "/a")
    private val keyB = MangaIdentityKey(1L, "/b")

    @Test
    fun `long-press on an unselected card enters selection with one item`() {
        val result = ForYouSelectionPolicy.longPress(emptyMap<MangaIdentityKey, String>(), keyA, "manga-a")
        assertEquals(mapOf(keyA to "manga-a"), result)
        assertTrue(ForYouSelectionPolicy.isSelectionMode(result))
    }

    @Test
    fun `long-press on an already-selected card is a no-op`() {
        val selected = mapOf(keyA to "manga-a")
        val result = ForYouSelectionPolicy.longPress(selected, keyA, "manga-a")
        assertEquals(selected, result)
    }

    @Test
    fun `tap in selection mode toggles additional items into selection`() {
        val selected = mapOf(keyA to "manga-a")
        val result = ForYouSelectionPolicy.toggle(selected, keyB, "manga-b")
        assertEquals(mapOf(keyA to "manga-a", keyB to "manga-b"), result)
    }

    @Test
    fun `tap on an already-selected item deselects it`() {
        val selected = mapOf(keyA to "manga-a", keyB to "manga-b")
        val result = ForYouSelectionPolicy.toggle(selected, keyB, "manga-b")
        assertEquals(mapOf(keyA to "manga-a"), result)
    }

    @Test
    fun `deselecting the last item exits selection mode`() {
        val selected = mapOf(keyA to "manga-a")
        val result = ForYouSelectionPolicy.toggle(selected, keyA, "manga-a")
        assertTrue(result.isEmpty())
        assertFalse(ForYouSelectionPolicy.isSelectionMode(result))
    }

    @Test
    fun `close clears the whole selection`() {
        val result = ForYouSelectionPolicy.clear<String>()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `single-only actions are available only with exactly one item selected`() {
        assertFalse(ForYouSelectionPolicy.isSingleSelection(emptyMap<MangaIdentityKey, String>()))
        assertTrue(ForYouSelectionPolicy.isSingleSelection(mapOf(keyA to "manga-a")))
        assertFalse(ForYouSelectionPolicy.isSingleSelection(mapOf(keyA to "manga-a", keyB to "manga-b")))
    }
}
// KMK <--
