package exh.util

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK Undo Expansion Phase 1 -->
class PreferenceUndoJournalTest {

    @AfterEach
    fun tearDown() {
        PreferenceUndoJournal.clear()
    }

    private fun entry(id: String, previous: Int = 0, expected: Int = 1) = PreferenceUndoEntry(
        id = id,
        timestamp = System.currentTimeMillis(),
        actionType = PreferenceJournalActionType.MIN_CHAPTER_COUNT,
        identityKey = "minChapterCount",
        previousValue = previous,
        expectedPostValue = expected,
        readCurrent = { expected },
        restore = {},
    )

    @Test
    fun `record then snapshot returns newest first`() = runTest {
        PreferenceUndoJournal.record(entry("e1"))
        PreferenceUndoJournal.record(entry("e2"))
        assertEquals(listOf("e2", "e1"), PreferenceUndoJournal.snapshot().map { it.id })
    }

    @Test
    fun `eviction bound is enforced without splitting entries (each entry is one whole preference change)`() {
        repeat(PreferenceUndoJournal.MAX_ENTRIES + 5) { i -> PreferenceUndoJournal.record(entry("e$i")) }
        assertEquals(PreferenceUndoJournal.MAX_ENTRIES, PreferenceUndoJournal.snapshot().size)
    }

    @Test
    fun `isEmpty and clear behave as expected`() {
        assertTrue(PreferenceUndoJournal.isEmpty())
        PreferenceUndoJournal.record(entry("e1"))
        assertFalse(PreferenceUndoJournal.isEmpty())
        PreferenceUndoJournal.clear()
        assertTrue(PreferenceUndoJournal.isEmpty())
    }

    @Test
    fun `removeById removes exactly one entry`() {
        PreferenceUndoJournal.record(entry("e1"))
        PreferenceUndoJournal.record(entry("e2"))
        PreferenceUndoJournal.removeById("e1")
        assertEquals(listOf("e2"), PreferenceUndoJournal.snapshot().map { it.id })
    }
}
// KMK <--
