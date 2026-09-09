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

class ConfirmedMangaGroupTargetsTest {
    private val links = mockk<GetCrossSourceMangaLinks>()
    private val manga = mockk<GetManga>()
    private val resolver = mockk<CrossSourceIdentityAuthorizationResolver>()
    private val target = ConfirmedMangaGroupTargets(links, manga, resolver)

    @Test
    fun `missing group keeps the origin only`() = runTest {
        val origin = manga(1, "/origin")
        coEvery { links.awaitBySourceUrl(1, "/origin") } returns null

        assertEquals(listOf(origin), target.await(origin))
    }

    @Test
    fun `confirmed members are included once`() = runTest {
        val origin = manga(1, "/origin")
        val confirmed = manga(2, "/confirmed")
        val link = link(1, "/origin")
        val members = listOf(link, link(2, "/confirmed"), link(2, "/confirmed"))
        coEvery { links.awaitBySourceUrl(1, "/origin") } returns link
        coEvery { links.awaitByGroupId("group") } returns members
        coEvery { resolver.confirmedGroupMembers(1, "/origin", members) } returns members
        coEvery { manga.await("/origin", 1) } returns origin
        coEvery { manga.await("/confirmed", 2) } returns confirmed

        assertEquals(listOf(origin, confirmed), target.await(origin))
    }

    private fun manga(source: Long, url: String) = Manga.create().copy(source = source, url = url, ogTitle = url)

    private fun link(source: Long, url: String) = CrossSourceMangaLink(source, url, "group", url, 1, 1)
}
