package exh.util

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
class CustomCoverUndoJournalTest {

    @AfterEach
    fun tearDown() {
        CustomCoverUndoJournal.clear()
    }

    private fun entry(id: String) = CustomCoverUndoEntry(
        id = id,
        timestamp = id.removePrefix("e").toLong(),
        mangaId = 1L,
        previousDigest = null,
        expectedPostDigest = "post",
    )

    @Test
    fun `journal is newest first and bounded`() {
        repeat(CustomCoverUndoJournal.MAX_ENTRIES + 2) { CustomCoverUndoJournal.record(entry("e$it")) }

        val rows = CustomCoverUndoJournal.snapshot()
        assertEquals(CustomCoverUndoJournal.MAX_ENTRIES, rows.size)
        assertEquals("e${CustomCoverUndoJournal.MAX_ENTRIES + 1}", rows.first().id)
        assertFalse(rows.any { it.id == "e0" })
    }

    @Test
    fun `remove and clear only affect the journal`() {
        CustomCoverUndoJournal.record(entry("e1"))
        CustomCoverUndoJournal.record(entry("e2"))
        CustomCoverUndoJournal.removeById("e1")

        assertEquals(listOf("e2"), CustomCoverUndoJournal.snapshot().map { it.id })
        assertFalse(CustomCoverUndoJournal.isEmpty())
        CustomCoverUndoJournal.clear()
        assertTrue(CustomCoverUndoJournal.isEmpty())
    }

    @Test
    fun `sha256 is stable for file contents`() {
        val file = File.createTempFile("kmk-cover", ".bin")
        try {
            file.writeText("cover-content")
            assertEquals(CustomCoverUndoRecorder.sha256(file), CustomCoverUndoRecorder.sha256(file))
        } finally {
            file.delete()
        }
    }
}
