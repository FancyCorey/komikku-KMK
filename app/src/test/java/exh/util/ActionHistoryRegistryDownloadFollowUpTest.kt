package exh.util

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.Source
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/**
 * Direct tests for [ActionHistoryRegistry]'s `downloadFollowUpFor` -- the download compensating
 * action ("Re-download"). Mirrors
 * [ActionHistoryRegistryMigrationFollowUpTest]'s own seam-swap pattern.
 */
class ActionHistoryRegistryDownloadFollowUpTest {

    private val getManga = mockk<GetManga>()
    private val getChapter = mockk<GetChapter>()
    private val sourceManager = mockk<SourceManager>()
    private val sourcePreferences = SourcePreferences(FakePreferenceStore())
    private val downloadManager = mockk<DownloadManager>(relaxed = true)

    @AfterEach
    fun tearDown() {
        NonUndoableEventJournal.clear()
        DownloadReceiptJournal.clear()
        actionHistoryFollowUpGetMangaProvider = { Injekt.get() }
        actionHistoryFollowUpGetChapterProvider = { Injekt.get() }
        actionHistoryFollowUpSourceManagerProvider = { Injekt.get() }
        actionHistoryFollowUpSourcePreferencesProvider = { Injekt.get() }
        actionHistoryFollowUpDownloadManagerProvider = { Injekt.get() }
    }

    private fun bindFakes() {
        sourcePreferences.evaluationMode().set(true)
        actionHistoryFollowUpGetMangaProvider = { getManga }
        actionHistoryFollowUpGetChapterProvider = { getChapter }
        actionHistoryFollowUpSourceManagerProvider = { sourceManager }
        actionHistoryFollowUpSourcePreferencesProvider = { sourcePreferences }
        actionHistoryFollowUpDownloadManagerProvider = { downloadManager }
    }

    private fun manga(id: Long = 1L, sourceId: Long = 10L) =
        Manga.create().copy(id = id, source = sourceId, url = "/m", ogTitle = "Manga $id", favorite = false)

    private fun chapter(id: Long, mangaId: Long = 1L) = Chapter.create().copy(id = id, mangaId = mangaId)

    private fun seedReceipt(
        mangaId: Long = 1L,
        sourceId: Long = 10L,
        chapterIds: List<Long> = listOf(100L, 101L),
    ): String {
        val id = NonUndoableEvent.newId()
        NonUndoableEventJournal.record(
            NonUndoableEvent(id = id, timestamp = System.currentTimeMillis(), eventType = NonUndoableEventType.DOWNLOAD_DELETED),
        )
        DownloadReceiptJournal.record(
            DownloadReceipt(id = id, timestamp = System.currentTimeMillis(), mangaId = mangaId, sourceId = sourceId, chapterIds = chapterIds),
        )
        return id
    }

    private fun stubEligible(m: Manga, chapters: List<Chapter>) {
        coEvery { getManga.await(m.id) } returns m
        every { sourceManager.get(m.source) } returns mockk<Source>(relaxed = true)
        chapters.forEach { c -> coEvery { getChapter.await(c.id) } returns c }
    }

    @Test
    fun `an event with no matching receipt gets no follow-up`() {
        bindFakes()
        NonUndoableEventJournal.record(
            NonUndoableEvent(id = NonUndoableEvent.newId(), timestamp = System.currentTimeMillis(), eventType = NonUndoableEventType.DOWNLOAD_DELETED),
        )

        val row = ActionHistoryRegistry.snapshot().first()

        assertNull(row.followUp, "no DownloadReceipt correlates with this event id -- no follow-up should be offered")
    }

    @Test
    fun `a non-download event type never gets a download follow-up`() {
        bindFakes()
        NonUndoableEventJournal.record(
            NonUndoableEvent(id = NonUndoableEvent.newId(), timestamp = System.currentTimeMillis(), eventType = NonUndoableEventType.EXTENSION_INSTALLED),
        )

        val row = ActionHistoryRegistry.snapshot().first()

        assertNull(row.followUp)
    }

    @Test
    fun `a download-deleted receipt always offers a Re-download follow-up -- eligibility is checked at trigger time`() {
        bindFakes()
        seedReceipt()

        val row = ActionHistoryRegistry.snapshot().first()

        assertTrue(row.followUp != null, "a receipt must always offer a follow-up -- see downloadFollowUpFor's own doc comment for why")
    }

    @Test
    fun `trigger enqueues exactly the resolved chapters and returns Started when eligible`() = runTest {
        bindFakes()
        val m = manga()
        val chapters = listOf(chapter(100L), chapter(101L))
        stubEligible(m, chapters)
        seedReceipt(mangaId = m.id, sourceId = m.source, chapterIds = chapters.map { it.id })
        val followUp = ActionHistoryRegistry.snapshot().first().followUp ?: error("expected a follow-up to be offered")

        val result = followUp.trigger()

        assertEquals(ActionHistoryFollowUpResult.Started, result)
        verify(exactly = 1) { downloadManager.downloadChapters(m, chapters) }
    }

    @Test
    fun `trigger refuses and does not enqueue when the manga is no longer found`() = runTest {
        bindFakes()
        val m = manga()
        coEvery { getManga.await(m.id) } returns null
        every { sourceManager.get(m.source) } returns mockk<Source>(relaxed = true)
        coEvery { getChapter.await(any()) } returns chapter(100L)
        seedReceipt(mangaId = m.id, sourceId = m.source, chapterIds = listOf(100L))
        val followUp = ActionHistoryRegistry.snapshot().first().followUp ?: error("expected a follow-up to be offered")

        val result = followUp.trigger()

        assertEquals(ActionHistoryFollowUpResult.Failed, result)
        verify(exactly = 0) { downloadManager.downloadChapters(any(), any()) }
    }

    @Test
    fun `trigger refuses and does not enqueue when the source is not installed`() = runTest {
        bindFakes()
        val m = manga()
        coEvery { getManga.await(m.id) } returns m
        every { sourceManager.get(m.source) } returns null
        coEvery { getChapter.await(any()) } returns chapter(100L)
        seedReceipt(mangaId = m.id, sourceId = m.source, chapterIds = listOf(100L))
        val followUp = ActionHistoryRegistry.snapshot().first().followUp ?: error("expected a follow-up to be offered")

        val result = followUp.trigger()

        assertEquals(ActionHistoryFollowUpResult.Failed, result)
        verify(exactly = 0) { downloadManager.downloadChapters(any(), any()) }
    }

    @Test
    fun `trigger refuses and does not enqueue when none of the deleted chapters still resolve`() = runTest {
        bindFakes()
        val m = manga()
        coEvery { getManga.await(m.id) } returns m
        every { sourceManager.get(m.source) } returns mockk<Source>(relaxed = true)
        coEvery { getChapter.await(any()) } returns null
        seedReceipt(mangaId = m.id, sourceId = m.source, chapterIds = listOf(100L, 101L))
        val followUp = ActionHistoryRegistry.snapshot().first().followUp ?: error("expected a follow-up to be offered")

        val result = followUp.trigger()

        assertEquals(ActionHistoryFollowUpResult.Failed, result)
        verify(exactly = 0) { downloadManager.downloadChapters(any(), any()) }
    }

    @Test
    fun `trigger enqueues only the chapters that still resolve when the deleted set is partially gone`() = runTest {
        bindFakes()
        val m = manga()
        val stillThere = chapter(100L)
        coEvery { getManga.await(m.id) } returns m
        every { sourceManager.get(m.source) } returns mockk<Source>(relaxed = true)
        coEvery { getChapter.await(100L) } returns stillThere
        coEvery { getChapter.await(101L) } returns null
        seedReceipt(mangaId = m.id, sourceId = m.source, chapterIds = listOf(100L, 101L))
        val followUp = ActionHistoryRegistry.snapshot().first().followUp ?: error("expected a follow-up to be offered")

        val result = followUp.trigger()

        assertEquals(ActionHistoryFollowUpResult.Started, result)
        verify(exactly = 1) { downloadManager.downloadChapters(m, listOf(stillThere)) }
    }

    @Test
    fun `trigger re-resolves eligibility fresh -- a conflict that appeared since rendering is still caught`() = runTest {
        bindFakes()
        val m = manga()
        val chapters = listOf(chapter(100L))
        stubEligible(m, chapters)
        seedReceipt(mangaId = m.id, sourceId = m.source, chapterIds = chapters.map { it.id })
        val followUp = ActionHistoryRegistry.snapshot().first().followUp ?: error("expected a follow-up to be offered")
        // Simulates the manga being removed from the library entirely between render and trigger.
        coEvery { getManga.await(m.id) } returns null

        val result = followUp.trigger()

        assertEquals(ActionHistoryFollowUpResult.Failed, result)
        verify(exactly = 0) { downloadManager.downloadChapters(any(), any()) }
    }

    @Test
    fun `clearing Action History clears both the visible event journal and the private receipt journal`() {
        bindFakes()
        seedReceipt()
        assertTrue(DownloadReceiptJournal.isEmpty().not())

        ActionHistoryRegistry.clearAll()

        assertTrue(NonUndoableEventJournal.isEmpty())
        assertTrue(DownloadReceiptJournal.isEmpty())
    }

    @Test
    fun `CancellationException from GetManga during trigger propagates instead of being swallowed as Failed`() {
        bindFakes()
        val m = manga()
        val chapters = listOf(chapter(100L))
        stubEligible(m, chapters)
        seedReceipt(mangaId = m.id, sourceId = m.source, chapterIds = chapters.map { it.id })
        val followUp = ActionHistoryRegistry.snapshot().first().followUp ?: error("expected a follow-up to be offered")
        coEvery { getManga.await(m.id) } throws CancellationException("cancelled")

        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking { followUp.trigger() }
        }
    }
}
// KMK <--
