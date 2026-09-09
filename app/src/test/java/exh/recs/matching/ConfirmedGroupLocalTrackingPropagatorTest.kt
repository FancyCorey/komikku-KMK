package exh.recs.matching

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.tracker.model.LocalTrackedWork
import tachiyomi.domain.tracker.model.LocalTrackedWorkSource
import tachiyomi.domain.tracker.model.LocalTrackedWorkSourceConfirmation
import tachiyomi.domain.tracker.model.LocalTrackedWorkSourceProgress
import tachiyomi.domain.tracker.repository.LocalTrackerRepository

class ConfirmedGroupLocalTrackingPropagatorTest {
    private val repository = mockk<LocalTrackerRepository>(relaxed = true)
    private val propagator = ConfirmedGroupLocalTrackingPropagator(repository)

    @Test
    fun `no existing local work leaves group unchanged`() = runTest {
        val origin = manga(1, "/origin")
        val target = manga(2, "/target")
        coEvery { repository.getWorkIdBySourceUrl(any(), any()) } returns null

        assertFalse(propagator.propagateIfAnyTracked(listOf(origin, target)))
        coVerify(exactly = 0) { repository.upsertWork(any()) }
        coVerify(exactly = 0) { repository.upsertSource(any()) }
    }

    @Test
    fun `untracked confirmed sibling is attached to existing work`() = runTest {
        val origin = manga(1, "/origin")
        val target = manga(2, "/target")
        val work = LocalTrackedWork(
            id = "work-1",
            title = "Origin",
            normalizedTitle = "origin",
            status = tachiyomi.domain.tracker.model.LocalTrackedWorkStatus.READING,
            lastChapterSource = 1,
            lastChapterNumber = 4.0,
            lastChapterUrl = "/chapter-4",
            lastChapterLabel = "Chapter 4",
            lastProgressAt = 10L,
            createdAt = 1L,
            updatedAt = 10L,
        )
        coEvery { repository.getWorkIdBySourceUrl(1, "/origin") } returns "work-1"
        coEvery { repository.getWorkIdBySourceUrl(2, "/target") } returns null
        coEvery { repository.getWork("work-1") } returns work

        assertTrue(propagator.propagateIfAnyTracked(listOf(origin, target)))
        coVerify { repository.upsertSource(match { it.workId == "work-1" && it.source == 2L && it.url == "/target" }) }
        coVerify(exactly = 0) { repository.upsertWork(any()) }
    }

    @Test
    fun `separate existing works are consolidated before propagation`() = runTest {
        val origin = manga(1, "/origin")
        val target = manga(2, "/target")
        val canonical = work("work-1", "Origin", updatedAt = 20L).copy(createdAt = 1L)
        val duplicate = work("work-2", "Target", updatedAt = 30L).copy(createdAt = 2L, score = 8.5)
        val targetSource = source("work-2", 2, "/target")
        val targetProgress = LocalTrackedWorkSourceProgress(
            workId = "work-2",
            source = 2,
            url = "/target",
            chapterNumber = 12.0,
            chapterUrl = "/target/ch-12",
            chapterLabel = "Chapter 12",
            progressAt = 30L,
            updatedAt = 30L,
        )
        coEvery { repository.getWorkIdBySourceUrl(1, "/origin") } returns "work-1"
        coEvery { repository.getWorkIdBySourceUrl(2, "/target") } returns "work-2"
        coEvery { repository.getWork("work-1") } returns canonical
        coEvery { repository.getWork("work-2") } returns duplicate
        coEvery { repository.getSources("work-1") } returns emptyList()
        coEvery { repository.getSources("work-2") } returns listOf(targetSource)
        coEvery { repository.getSourceProgress("work-2", 2, "/target") } returns targetProgress
        coEvery { repository.getLists(any()) } returns emptyList()

        assertTrue(propagator.propagateIfAnyTracked(listOf(origin, target)))

        coVerify(exactly = 1) { repository.consolidateWork("work-1", "work-2") }
        coVerify(exactly = 0) { repository.upsertSource(any()) }
    }

    @Test
    fun `target order does not change oldest canonical identity`() = runTest {
        val origin = manga(1, "/origin")
        val target = manga(2, "/target")
        val older = work("work-older", "Origin", updatedAt = 20L).copy(createdAt = 1L)
        val newer = work("work-newer", "Target", updatedAt = 30L).copy(createdAt = 2L)

        suspend fun consolidateInOrder(targets: List<Manga>) {
            val orderRepository = mockk<LocalTrackerRepository>(relaxed = true)
            val orderPropagator = ConfirmedGroupLocalTrackingPropagator(orderRepository)
            coEvery { orderRepository.getWorkIdBySourceUrl(1, "/origin") } returns "work-older"
            coEvery { orderRepository.getWorkIdBySourceUrl(2, "/target") } returns "work-newer"
            coEvery { orderRepository.getWork("work-older") } returns older
            coEvery { orderRepository.getWork("work-newer") } returns newer

            assertTrue(orderPropagator.propagateIfAnyTracked(targets))
            coVerify(exactly = 1) { orderRepository.consolidateWork("work-older", "work-newer") }
        }

        consolidateInOrder(listOf(origin, target))
        consolidateInOrder(listOf(target, origin))
    }

    private fun manga(source: Long, url: String) = Manga.create().copy(source = source, url = url, ogTitle = url)

    private fun source(workId: String, source: Long, url: String) = LocalTrackedWorkSource(
        workId = workId,
        source = source,
        url = url,
        title = url,
        confidence = 100,
        confirmation = LocalTrackedWorkSourceConfirmation.USER_CONFIRMED,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun work(id: String, title: String, updatedAt: Long) = LocalTrackedWork(
        id = id,
        title = title,
        normalizedTitle = title.lowercase(),
        status = tachiyomi.domain.tracker.model.LocalTrackedWorkStatus.READING,
        lastChapterSource = null,
        lastChapterNumber = null,
        lastChapterUrl = null,
        lastChapterLabel = null,
        lastProgressAt = null,
        createdAt = 1L,
        updatedAt = updatedAt,
    )
}
