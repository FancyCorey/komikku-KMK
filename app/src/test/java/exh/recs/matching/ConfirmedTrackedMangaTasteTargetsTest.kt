package exh.recs.matching

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.interactor.GetCrossSourceMangaLinks
import tachiyomi.domain.taste.model.CrossSourceMangaLink
import tachiyomi.domain.tracker.repository.LocalTrackerRepository

class ConfirmedTrackedMangaTasteTargetsTest {
    private val links = mockk<GetCrossSourceMangaLinks>()
    private val manga = mockk<GetManga>()
    private val resolver = mockk<CrossSourceIdentityAuthorizationResolver>()
    private val localTracker = mockk<LocalTrackerRepository>()
    private val target = ConfirmedTrackedMangaTasteTargets(links, manga, resolver, localTracker)

    @Test
    fun `missing link group keeps the origin only`() = runTest {
        val origin = manga(1, "/origin")
        coEvery { links.awaitBySourceUrl(1, "/origin") } returns null

        assertEquals(listOf(origin), target.await(origin))
    }

    @Test
    fun `only confirmed locally tracked members are returned`() = runTest {
        val origin = manga(1, "/origin")
        val confirmedTracked = manga(2, "/confirmed")
        val untracked = manga(3, "/untracked")
        val link = link(1, "/origin")
        val members = listOf(link, link(2, "/confirmed"), link(3, "/untracked"), link(2, "/confirmed"))
        coEvery { links.awaitBySourceUrl(1, "/origin") } returns link
        coEvery { links.awaitByGroupId("group") } returns members
        coEvery { resolver.confirmedGroupMembers(1, "/origin", members) } returns members
        coEvery { manga.await("/origin", 1) } returns origin
        coEvery { manga.await("/confirmed", 2) } returns confirmedTracked
        coEvery { manga.await("/untracked", 3) } returns untracked
        coEvery { localTracker.getWorkIdBySourceUrl(2, "/confirmed") } returns "work-2"
        coEvery { localTracker.getWorkIdBySourceUrl(3, "/untracked") } returns null

        assertEquals(listOf(origin, confirmedTracked), target.await(origin))
    }

    private fun manga(source: Long, url: String) = Manga.create().copy(source = source, url = url, ogTitle = url)

    private fun link(source: Long, url: String) = CrossSourceMangaLink(source, url, "group", url, 1, 1)
}
