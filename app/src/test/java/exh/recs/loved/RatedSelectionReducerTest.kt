package exh.recs.loved

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for [RatedSelectionReducer].
 *
 * Run with: ./gradlew :app:testDebugUnitTest --tests "*.RatedSelectionReducerTest"
 */
class RatedSelectionReducerTest {

    private val idle = RatedSelectionReducer.Selection(selectionMode = false, selectedKeys = emptySet())
    private val keyA = RatedMangaKey(1, "/a")
    private val keyB = RatedMangaKey(2, "/b")

    @Test
    fun `long-press enters selection mode and selects the item`() {
        val result = RatedSelectionReducer.enter(idle, keyA)
        assertTrue(result.selectionMode)
        assertEquals(setOf(keyA), result.selectedKeys)
    }

    @Test
    fun `entering selection again while already in it adds another key`() {
        val afterFirst = RatedSelectionReducer.enter(idle, keyA)
        val afterSecond = RatedSelectionReducer.enter(afterFirst, keyB)
        assertEquals(setOf(keyA, keyB), afterSecond.selectedKeys)
    }

    @Test
    fun `tap toggle is a no-op outside selection mode`() {
        val result = RatedSelectionReducer.toggle(idle, keyA)
        assertEquals(idle, result)
    }

    @Test
    fun `tap toggle adds an unselected key while in selection mode`() {
        val selecting = RatedSelectionReducer.enter(idle, keyA)
        val result = RatedSelectionReducer.toggle(selecting, keyB)
        assertEquals(setOf(keyA, keyB), result.selectedKeys)
    }

    @Test
    fun `tap toggle removes an already-selected key`() {
        val selecting = RatedSelectionReducer.enter(idle, keyA)
        val result = RatedSelectionReducer.toggle(selecting, keyA)
        assertTrue(result.selectedKeys.isEmpty())
        // Toggling the last key off does not itself exit selection mode — clear() does that
        // explicitly, matching an "empty selection but still browsing to select more" UX.
        assertTrue(result.selectionMode)
    }

    @Test
    fun `clear exits selection mode and empties selection`() {
        val selecting = RatedSelectionReducer.enter(idle, keyA)
        val result = RatedSelectionReducer.clear()
        assertFalse(result.selectionMode)
        assertTrue(result.selectedKeys.isEmpty())
        assertEquals(idle, result)
        // selecting is unused beyond documenting the pre-clear state for readability
        assertTrue(selecting.selectionMode)
    }

    @Test
    fun `selectAll adds every member key and enters selection mode`() {
        val result = RatedSelectionReducer.selectAll(idle, listOf(keyA, keyB))
        assertTrue(result.selectionMode)
        assertEquals(setOf(keyA, keyB), result.selectedKeys)
    }

    @Test
    fun `selectAll on top of an existing selection is additive`() {
        val selecting = RatedSelectionReducer.enter(idle, keyA)
        val keyC = RatedMangaKey(3, "/c")
        val result = RatedSelectionReducer.selectAll(selecting, listOf(keyB, keyC))
        assertEquals(setOf(keyA, keyB, keyC), result.selectedKeys)
    }

    // KMK --> v0.8.1-fix1: app-bar "Select" action must not silently select anything
    @Test
    fun `enterEmpty enters selection mode without selecting anything`() {
        val result = RatedSelectionReducer.enterEmpty(idle)
        assertTrue(result.selectionMode)
        assertTrue(result.selectedKeys.isEmpty())
    }

    @Test
    fun `enterEmpty preserves an existing selection rather than clearing it`() {
        val selecting = RatedSelectionReducer.enter(idle, keyA)
        val result = RatedSelectionReducer.enterEmpty(selecting)
        assertTrue(result.selectionMode)
        assertEquals(setOf(keyA), result.selectedKeys)
    }

    @Test
    fun `enterEmpty then a subsequent tap toggle can still select an item`() {
        val entered = RatedSelectionReducer.enterEmpty(idle)
        val result = RatedSelectionReducer.toggle(entered, keyA)
        assertEquals(setOf(keyA), result.selectedKeys)
    }
    // KMK <--
}
