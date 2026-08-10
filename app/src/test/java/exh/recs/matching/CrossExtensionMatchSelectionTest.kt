package exh.recs.matching

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK -->
class CrossExtensionMatchSelectionTest {

    private fun key(source: Long, url: String) = MangaIdentityKey(source, url)

    // --- Selection state helpers (pure logic extracted for testing) ---

    private fun autoSelectCandidates(
        incoming: List<MangaIdentityKey>,
        originSource: Long,
        originUrl: String,
        manuallyDeselectedKeys: Set<MangaIdentityKey>,
        existingSelected: Set<MangaIdentityKey> = emptySet(),
    ): Set<MangaIdentityKey> {
        val newKeys = incoming.mapNotNull { k ->
            if (k.source == originSource && k.url == originUrl) return@mapNotNull null
            if (k in manuallyDeselectedKeys) return@mapNotNull null
            k
        }.toSet()
        return existingSelected + newKeys
    }

    private fun toggleSelection(
        key: MangaIdentityKey,
        selected: Set<MangaIdentityKey>,
        manuallyDeselected: Set<MangaIdentityKey>,
    ): Pair<Set<MangaIdentityKey>, Set<MangaIdentityKey>> {
        return if (key in selected) {
            Pair(selected - key, manuallyDeselected + key)
        } else {
            Pair(selected + key, manuallyDeselected - key)
        }
    }

    @Test
    fun `per-source result limit is 2`() {
        assertEquals(2, CrossExtensionMatchScreenModel.PER_SOURCE_RESULT_LIMIT)
    }

    @Test
    fun `incoming candidates are auto-selected by default`() {
        val keys = listOf(key(1L, "/a"), key(2L, "/b"), key(3L, "/c"))
        val result = autoSelectCandidates(keys, originSource = 99L, originUrl = "/origin", manuallyDeselectedKeys = emptySet())
        assertEquals(3, result.size)
        assertTrue(key(1L, "/a") in result)
        assertTrue(key(2L, "/b") in result)
        assertTrue(key(3L, "/c") in result)
    }

    @Test
    fun `origin manga is excluded from auto-selection`() {
        val originSource = 1L
        val originUrl = "/origin"
        val keys = listOf(key(1L, "/origin"), key(2L, "/other"))
        val result = autoSelectCandidates(keys, originSource, originUrl, emptySet())
        assertFalse(key(1L, "/origin") in result)
        assertTrue(key(2L, "/other") in result)
    }

    @Test
    fun `manually deselected key is not re-selected when results update`() {
        val k = key(1L, "/a")
        val manuallyDeselected = setOf(k)
        val incoming = listOf(k, key(2L, "/b"))
        val result = autoSelectCandidates(incoming, 99L, "/origin", manuallyDeselected)
        assertFalse(k in result)
        assertTrue(key(2L, "/b") in result)
    }

    @Test
    fun `toggling selected key moves it to manuallyDeselected`() {
        val k = key(1L, "/a")
        val (selected, manual) = toggleSelection(k, selected = setOf(k), manuallyDeselected = emptySet())
        assertFalse(k in selected)
        assertTrue(k in manual)
    }

    @Test
    fun `toggling deselected key removes it from manuallyDeselected and adds to selected`() {
        val k = key(1L, "/a")
        val (selected, manual) = toggleSelection(k, selected = emptySet(), manuallyDeselected = setOf(k))
        assertTrue(k in selected)
        assertFalse(k in manual)
    }

    @Test
    fun `new results from a second source do not lose previous selections`() {
        val first = key(1L, "/a")
        val existing = setOf(first)
        val newKeys = listOf(key(2L, "/b"))
        val result = autoSelectCandidates(newKeys, 99L, "/origin", emptySet(), existingSelected = existing)
        assertTrue(first in result)
        assertTrue(key(2L, "/b") in result)
    }

    @Test
    fun `origin manga is filtered before cap - source returns origin plus two others`() {
        // [origin, a, b, c] with cap=2 should produce [a, b], not [a]
        val originSource = 1L
        val originUrl = "/origin"
        val originKey = key(originSource, originUrl)
        val aKey = key(originSource, "/a")
        val bKey = key(originSource, "/b")
        val cKey = key(originSource, "/c")
        val all = listOf(originKey, aKey, bKey, cKey)
        val filtered = all.filterNot { it.source == originSource && it.url == originUrl }
            .take(CrossExtensionMatchScreenModel.PER_SOURCE_RESULT_LIMIT)
        assertEquals(2, filtered.size)
        assertFalse(originKey in filtered)
        assertTrue(aKey in filtered)
        assertTrue(bKey in filtered)
    }

    @Test
    fun `toggleSelection defensively ignores origin key`() {
        val originSource = 1L
        val originUrl = "/origin"
        val originKey = key(originSource, originUrl)
        // Simulate the guard: if key matches origin, do not toggle
        fun toggleWithGuard(
            k: MangaIdentityKey,
            selected: Set<MangaIdentityKey>,
            manuallyDeselected: Set<MangaIdentityKey>,
        ): Pair<Set<MangaIdentityKey>, Set<MangaIdentityKey>> {
            if (k.source == originSource && k.url == originUrl) {
                return Pair(selected, manuallyDeselected)
            }
            return toggleSelection(k, selected, manuallyDeselected)
        }
        val initial: Set<MangaIdentityKey> = emptySet()
        val (selected, _) = toggleWithGuard(originKey, initial, emptySet())
        assertFalse(originKey in selected)
    }

    @Test
    fun `identity key equality uses source and url`() {
        val k1 = MangaIdentityKey(source = 1L, url = "/a")
        val k2 = MangaIdentityKey(source = 1L, url = "/a")
        val k3 = MangaIdentityKey(source = 2L, url = "/a")
        assertEquals(k1, k2)
        assertTrue(k1 != k3)
    }
}
// KMK <--
