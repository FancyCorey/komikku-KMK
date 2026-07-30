package exh.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

// KMK Undo Expansion Phase 1 -->
/**
 * Covers [PreferenceUndoService.undo] generically -- since [PreferenceUndoEntry] captures its own
 * typed read/restore functions, these tests exercise the full restore contract (conflict, restore,
 * failure, cancellation) without needing any real preference store or interactor, and the same
 * coverage applies to every concrete preference family (numeric settings, tag preferences, source
 * quality marks, reading schedule) that goes through this one service.
 */
class PreferenceUndoServiceTest {

    private val service = PreferenceUndoService()

    @AfterEach
    fun tearDown() {
        PreferenceUndoJournal.clear()
    }

    @Test
    fun `undo restores the previous value when current state matches the expected post-action value`() = runTest {
        var stored = 5
        val entry = PreferenceUndoEntry(
            id = PreferenceUndoEntry.newId(),
            timestamp = 0L,
            actionType = PreferenceJournalActionType.MIN_CHAPTER_COUNT,
            identityKey = "minChapterCount",
            previousValue = 1,
            expectedPostValue = 5,
            readCurrent = { stored },
            restore = { stored = it },
        )
        PreferenceUndoJournal.record(entry)

        val outcome = service.undo(entry.id)

        assertEquals(GroupUndoResult.RESTORED, outcome)
        assertEquals(1, stored)
        assertEquals(0, PreferenceUndoJournal.snapshot().size)
    }

    @Test
    fun `undo refuses and leaves the value untouched when it changed after journaling`() = runTest {
        var stored = 99 // diverged from expectedPostValue
        val entry = PreferenceUndoEntry(
            id = PreferenceUndoEntry.newId(),
            timestamp = 0L,
            actionType = PreferenceJournalActionType.MIN_CHAPTER_COUNT,
            identityKey = "minChapterCount",
            previousValue = 1,
            expectedPostValue = 5,
            readCurrent = { stored },
            restore = { stored = it },
        )
        PreferenceUndoJournal.record(entry)

        val outcome = service.undo(entry.id)

        assertEquals(GroupUndoResult.CONFLICT, outcome)
        assertEquals(99, stored)
        assertEquals(1, PreferenceUndoJournal.snapshot().size)
    }

    @Test
    fun `undo of an unknown entry id reports failed`() = runTest {
        assertEquals(GroupUndoResult.FAILED, service.undo("missing"))
    }

    @Test
    fun `a restore that throws is reported as failed and keeps the entry for retry`() = runTest {
        val entry = PreferenceUndoEntry(
            id = PreferenceUndoEntry.newId(),
            timestamp = 0L,
            actionType = PreferenceJournalActionType.MIN_CHAPTER_COUNT,
            identityKey = "minChapterCount",
            previousValue = 1,
            expectedPostValue = 5,
            readCurrent = { 5 },
            restore = { throw RuntimeException("boom") },
        )
        PreferenceUndoJournal.record(entry)

        assertEquals(GroupUndoResult.FAILED, service.undo(entry.id))
        assertEquals(1, PreferenceUndoJournal.snapshot().size)
    }

    @Test
    fun `cancellation is propagated, never swallowed as a failure`() = runTest {
        val entry = PreferenceUndoEntry(
            id = PreferenceUndoEntry.newId(),
            timestamp = 0L,
            actionType = PreferenceJournalActionType.MIN_CHAPTER_COUNT,
            identityKey = "minChapterCount",
            previousValue = 1,
            expectedPostValue = 5,
            readCurrent = { 5 },
            restore = { throw CancellationException("cancelled") },
        )
        PreferenceUndoJournal.record(entry)

        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking { service.undo(entry.id) }
        }
    }

    @Test
    fun `a non-reversible entry is always a conflict`() = runTest {
        val entry = PreferenceUndoEntry(
            id = PreferenceUndoEntry.newId(),
            timestamp = 0L,
            actionType = PreferenceJournalActionType.MIN_CHAPTER_COUNT,
            identityKey = "minChapterCount",
            previousValue = 1,
            expectedPostValue = 5,
            readCurrent = { 5 },
            restore = {},
            reversible = false,
        )
        PreferenceUndoJournal.record(entry)

        assertEquals(GroupUndoResult.CONFLICT, service.undo(entry.id))
    }
}
// KMK <--
