package exh.util

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.model.Track
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK Universal Action History Recovery Plan 2026-08-01 -->
class ActionHistoryRegistryTrackFollowUpTest {

    private val getManga = mockk<GetManga>()
    private val getTracks = mockk<GetTracks>()
    private val trackerManager = mockk<TrackerManager>()
    private val tracker = mockk<BaseTracker>(relaxed = true)
    private val sourcePreferences = SourcePreferences(FakePreferenceStore())
    private val manga = Manga.create()
    private val track = Track(
        id = 1L,
        mangaId = manga.id,
        trackerId = 2L,
        remoteId = 3L,
        libraryId = null,
        title = "Manga",
        lastChapterRead = 4.0,
        totalChapters = 10L,
        status = 1L,
        score = 5.0,
        remoteUrl = "https://example.invalid/manga",
        startDate = 0L,
        finishDate = 0L,
        private = false,
    )

    @AfterEach
    fun tearDown() {
        NonUndoableEventJournal.clear()
        TrackWriteReceiptJournal.clear()
        actionHistoryFollowUpGetMangaProvider = { Injekt.get() }
        actionHistoryFollowUpGetTracksProvider = { Injekt.get() }
        actionHistoryFollowUpTrackerManagerProvider = { Injekt.get() }
        actionHistoryFollowUpSourcePreferencesProvider = { Injekt.get() }
    }

    private fun bindFakes() {
        sourcePreferences.evaluationMode().set(true)
        actionHistoryFollowUpGetMangaProvider = { getManga }
        actionHistoryFollowUpGetTracksProvider = { getTracks }
        actionHistoryFollowUpTrackerManagerProvider = { trackerManager }
        actionHistoryFollowUpSourcePreferencesProvider = { sourcePreferences }
        every { tracker.id } returns track.trackerId
        every { tracker.displayScore(any()) } returns "5"
        every { trackerManager.get(track.trackerId) } returns tracker
        every { trackerManager.loggedInTrackersFlow() } returns flowOf(listOf(tracker))
    }

    private fun seed(field: TrackWriteField = TrackWriteField.STATUS): String {
        val id = NonUndoableEvent.newId()
        NonUndoableEventJournal.record(NonUndoableEvent(id, 1L, NonUndoableEventType.TRACKER_WRITE_COMPLETED))
        TrackWriteReceiptJournal.record(
            TrackWriteReceipt(
                id = id,
                timestamp = 1L,
                mangaId = manga.id,
                trackerId = track.trackerId,
                field = field,
                previousStatus = if (field == TrackWriteField.STATUS) 0L else null,
                previousScore = if (field == TrackWriteField.SCORE) "3" else null,
                previousChapterProgress = if (field == TrackWriteField.CHAPTER_PROGRESS) 2 else null,
                previousStartDate = if (field == TrackWriteField.START_DATE) 10L else null,
                previousFinishDate = if (field == TrackWriteField.FINISH_DATE) 20L else null,
                previousPrivate = if (field == TrackWriteField.PRIVATE) true else null,
            ),
        )
        return id
    }

    private fun eligible() {
        coEvery { getManga.await(manga.id) } returns manga
        coEvery { getTracks.await(manga.id) } returns listOf(track)
    }

    @Test
    fun `unmatched event has no follow-up`() {
        bindFakes()
        NonUndoableEventJournal.record(NonUndoableEvent("missing", 1L, NonUndoableEventType.TRACKER_WRITE_COMPLETED))
        assertNull(ActionHistoryRegistry.snapshot().first().followUp)
    }

    @Test
    fun `eligible status receipt offers a restore label`() {
        bindFakes()
        seed()
        assertTrue(ActionHistoryRegistry.snapshot().first().followUp != null)
    }

    @Test
    fun `trigger restores the previous status and records the new write`() = runBlocking {
        bindFakes()
        eligible()
        seed()
        val followUp = ActionHistoryRegistry.snapshot().first().followUp!!

        assertEquals(ActionHistoryFollowUpResult.Started, followUp.trigger())
        coVerify(exactly = 1) { tracker.setRemoteStatus(any(), 0L) }
        assertEquals(2, NonUndoableEventJournal.snapshot().size)
    }

    @Test
    fun `trigger refuses when the tracker is no longer logged in`() = runBlocking {
        bindFakes()
        eligible()
        every { trackerManager.loggedInTrackersFlow() } returns flowOf(emptyList())
        seed()

        assertEquals(ActionHistoryFollowUpResult.Failed, ActionHistoryRegistry.snapshot().first().followUp!!.trigger())
        coVerify(exactly = 0) { tracker.setRemoteStatus(any(), any()) }
    }

    @Test
    fun `trigger returns failed and records nothing when the remote write fails`() = runBlocking {
        bindFakes()
        eligible()
        coEvery { tracker.setRemoteStatus(any(), any()) } throws IllegalStateException("remote failure")
        seed()

        assertEquals(ActionHistoryFollowUpResult.Failed, ActionHistoryRegistry.snapshot().first().followUp!!.trigger())
        assertEquals(1, NonUndoableEventJournal.snapshot().size)
    }

    @Test
    fun `trigger restores tracker dates through the remote API`() = runBlocking {
        bindFakes()
        eligible()
        seed(TrackWriteField.START_DATE)

        assertEquals(ActionHistoryFollowUpResult.Started, ActionHistoryRegistry.snapshot().first().followUp!!.trigger())
        coVerify(exactly = 1) { tracker.setRemoteStartDate(any(), 10L) }

        NonUndoableEventJournal.clear()
        TrackWriteReceiptJournal.clear()
        seed(TrackWriteField.FINISH_DATE)
        assertEquals(ActionHistoryFollowUpResult.Started, ActionHistoryRegistry.snapshot().first().followUp!!.trigger())
        coVerify(exactly = 1) { tracker.setRemoteFinishDate(any(), 20L) }
    }

    @Test
    fun `trigger restores tracker privacy through the remote API`() = runBlocking {
        bindFakes()
        eligible()
        seed(TrackWriteField.PRIVATE)

        assertEquals(ActionHistoryFollowUpResult.Started, ActionHistoryRegistry.snapshot().first().followUp!!.trigger())
        coVerify(exactly = 1) { tracker.setRemotePrivate(any(), true) }
    }

    @Test
    fun `cancellation propagates from the remote write`() {
        bindFakes()
        eligible()
        coEvery { tracker.setRemoteStatus(any(), any()) } throws CancellationException("cancelled")
        seed()

        assertThrows(CancellationException::class.java) {
            runBlocking { ActionHistoryRegistry.snapshot().first().followUp!!.trigger() }
        }
    }

    @Test
    fun `clear removes tracker receipts with the visible event`() {
        bindFakes()
        seed()
        ActionHistoryRegistry.clearAll()
        assertTrue(TrackWriteReceiptJournal.isEmpty())
        assertTrue(NonUndoableEventJournal.isEmpty())
    }
}
// KMK <--
