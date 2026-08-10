package eu.kanade.tachiyomi.ui.manga.track

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.track.Tracker
import exh.util.FakePreferenceStore
import exh.util.NonUndoableEventJournal
import exh.util.NonUndoableEventType
import exh.util.TrackWriteField
import exh.util.TrackWriteReceiptJournal
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.track.model.Track

// KMK -->
// KMK_CLAUDE_REMAINING_FIXTURE_BLOCKER_IMPLEMENTATION_PLAN_2026-08-03 Phase 6: direct coverage for
// withTrackedWrite (made `internal` for this test), the shared boundary every user-initiated
// tracker write (status/score/chapter-progress/dates) in this screen goes through. Uses a mocked
// [Tracker] (an interface) and a plain fake `write` lambda -- never a real tracker account or
// network call -- to prove success/ordinary-failure/cancellation classification and that a receipt
// is committed only after the write lambda actually completes (record-after-verified-success),
// matching TrackWriteReceipt's own "not a database rollback, a fresh forward write" contract.
// [sourcePreferences] is passed explicitly rather than relying on withTrackedWrite's Injekt
// default, since Injekt's singleton caching is process-global across the whole test JVM.
class WithTrackedWriteTest {

    private fun sourcePreferences(evaluationModeEnabled: Boolean = true) =
        SourcePreferences(FakePreferenceStore()).apply { evaluationMode().set(evaluationModeEnabled) }

    @AfterEach
    fun tearDown() {
        NonUndoableEventJournal.clear()
        TrackWriteReceiptJournal.clear()
    }

    private fun track(mangaId: Long = 1L) = Track(
        id = 1L,
        mangaId = mangaId,
        trackerId = 20L,
        remoteId = 30L,
        libraryId = null,
        title = "Test",
        lastChapterRead = 0.0,
        totalChapters = 0L,
        status = 1L,
        score = 0.0,
        remoteUrl = "",
        startDate = 0L,
        finishDate = 0L,
        private = false,
    )

    @Test
    fun `a successful write records exactly one TRACKER_WRITE_COMPLETED event and receipt`() = runTest {
        val tracker = mockk<Tracker> { every { id } returns 20L }
        var writeCalled = false

        withTrackedWrite(
            tracker = tracker,
            track = track(),
            field = TrackWriteField.STATUS,
            previousStatus = 1L,
            sourcePreferences = sourcePreferences(),
            write = { writeCalled = true },
        )

        assertTrue(writeCalled, "the real write lambda must actually run")
        val event = NonUndoableEventJournal.snapshot().single()
        assertEquals(NonUndoableEventType.TRACKER_WRITE_COMPLETED, event.eventType)
        assertEquals(event.id, TrackWriteReceiptJournal.snapshot().single().id)
    }

    @Test
    fun `evaluation mode disabled records nothing even on success`() = runTest {
        val tracker = mockk<Tracker> { every { id } returns 20L }

        withTrackedWrite(
            tracker = tracker,
            track = track(),
            field = TrackWriteField.STATUS,
            sourcePreferences = sourcePreferences(evaluationModeEnabled = false),
            write = {},
        )

        assertTrue(NonUndoableEventJournal.isEmpty())
        assertTrue(TrackWriteReceiptJournal.isEmpty())
    }

    @Test
    fun `an ordinary remote failure records nothing and does not escape withTrackedWrite`() = runTest {
        val tracker = mockk<Tracker> { every { id } returns 20L }

        // BaseTracker already logs/surfaces the remote failure elsewhere; withTrackedWrite must
        // swallow it here rather than let it propagate a second time, and must never record a
        // completion receipt for a write that did not actually succeed.
        withTrackedWrite(
            tracker = tracker,
            track = track(),
            field = TrackWriteField.STATUS,
            sourcePreferences = sourcePreferences(),
            write = { throw IllegalStateException("remote tracker rejected the update") },
        )

        assertTrue(NonUndoableEventJournal.isEmpty(), "an ordinary failure must never record a completion receipt")
        assertTrue(TrackWriteReceiptJournal.isEmpty())
    }

    @Test
    fun `a cancelled write rethrows CancellationException and records nothing`() = runTest {
        val tracker = mockk<Tracker> { every { id } returns 20L }

        var thrown: CancellationException? = null
        try {
            withTrackedWrite(
                tracker = tracker,
                track = track(),
                field = TrackWriteField.STATUS,
                sourcePreferences = sourcePreferences(),
                write = { throw CancellationException("scope cancelled") },
            )
        } catch (e: CancellationException) {
            thrown = e
        }

        assertTrue(thrown != null, "cancellation must propagate out of withTrackedWrite, not be swallowed like an ordinary failure")
        assertTrue(NonUndoableEventJournal.isEmpty(), "a cancelled write must never record a completion receipt")
    }

    @Test
    fun `the write lambda is invoked before any receipt is recorded (remote-write-attempted precedes remote-write-completed)`() = runTest {
        val tracker = mockk<Tracker> { every { id } returns 20L }
        var journalWasEmptyDuringWrite = false

        withTrackedWrite(
            tracker = tracker,
            track = track(),
            field = TrackWriteField.SCORE,
            sourcePreferences = sourcePreferences(),
            write = {
                journalWasEmptyDuringWrite = NonUndoableEventJournal.isEmpty()
            },
        )

        assertTrue(journalWasEmptyDuringWrite, "no receipt may exist while the remote write is still in flight")
        assertEquals(1, NonUndoableEventJournal.snapshot().size, "exactly one receipt must exist once the write has actually completed")
    }
}
// KMK <--
