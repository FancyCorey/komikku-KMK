package exh.recs.matching

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager

class SameMangaCandidateSearcherLifecycleTest {

    @Test
    fun `cancellation propagates without a late result callback`() = runTest {
        val enteredSearch = CompletableDeferred<Unit>()
        val source = FakeSource(9_910_007) {
            enteredSearch.complete(Unit)
            awaitCancellation()
        }
        val results = mutableListOf<SameMangaSourceResult>()
        val searcher = searcher(UnconfinedTestDispatcher(testScheduler))

        val job = launch {
            searcher.search(
                queries = listOf("query"),
                settings = settings(),
                originManga = origin(),
                sources = listOf(source),
                onResult = results::add,
            )
        }
        enteredSearch.await()
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `ordinary failure is isolated per source`() = runTest {
        val networkToLocalManga = identityNetworkToLocalManga()
        val first = FakeSource(9_910_008) { page(manga("/success", "Shared")) }
        val second = FakeSource(9_910_009) { throw IllegalArgumentException("second") }
        val results = mutableListOf<SameMangaSourceResult>()

        searcher(UnconfinedTestDispatcher(testScheduler), networkToLocalManga).search(
            queries = listOf("query"),
            settings = settings(),
            originManga = origin(),
            sources = listOf(first, second),
            onResult = results::add,
        )

        assertEquals(setOf(9_910_008L, 9_910_009L), results.map { it.source.id }.toSet())
        assertEquals(1, results.count { it.result is SameMangaCandidateResult.Success })
        assertEquals(1, results.count { it.result is SameMangaCandidateResult.Error })
    }

    @Test
    fun `recoverable linkage failure is isolated from a successful source`() = runTest {
        val networkToLocalManga = identityNetworkToLocalManga()
        val first = FakeSource(9_910_010) { page(manga("/success", "Shared")) }
        val second = FakeSource(9_910_011) { throw NoClassDefFoundError("fixture dependency") }
        val results = mutableListOf<SameMangaSourceResult>()

        searcher(UnconfinedTestDispatcher(testScheduler), networkToLocalManga).search(
            queries = listOf("query"),
            settings = settings(),
            originManga = origin(),
            sources = listOf(first, second),
            onResult = results::add,
        )

        assertEquals(1, results.count { it.result is SameMangaCandidateResult.Success })
        val failure = results.single { it.result is SameMangaCandidateResult.Error }
            .result as SameMangaCandidateResult.Error
        assertTrue(failure.throwable is NoClassDefFoundError)
    }

    @Test
    fun `fatal error propagates instead of becoming a source result`() = runTest {
        val source = FakeSource(9_910_012) { throw OutOfMemoryError("fatal") }
        var propagated = false

        try {
            searcher(UnconfinedTestDispatcher(testScheduler)).search(
                queries = listOf("query"),
                settings = settings(),
                originManga = origin(),
                sources = listOf(source),
                onResult = {},
            )
        } catch (_: OutOfMemoryError) {
            propagated = true
        }

        assertTrue(propagated)
    }

    @Test
    fun `origin exclusion happens before the result cap`() = runTest {
        val source = FakeSource(99) {
            page(
                manga("/origin", "Origin"),
                manga("/candidate", "Origin"),
            )
        }
        val results = mutableListOf<SameMangaSourceResult>()

        searcher(UnconfinedTestDispatcher(testScheduler), identityNetworkToLocalManga()).search(
            queries = listOf("query"),
            settings = settings(resultCap = 1),
            originManga = origin(),
            sources = listOf(source),
            onResult = results::add,
        )

        val success = results.single().result as SameMangaCandidateResult.Success
        assertEquals(listOf("/candidate"), success.results.map(Manga::url))
    }

    @Test
    fun `query and URL duplicates are deduplicated in first-seen order`() = runTest {
        val source = FakeSource(9_910_002) { query ->
            when (query) {
                "first" -> page(manga("/a", "Origin"), manga("/b", "Origin"))
                else -> page(manga("/a", "Origin"), manga("/c", "Origin"))
            }
        }
        val results = mutableListOf<SameMangaSourceResult>()

        searcher(UnconfinedTestDispatcher(testScheduler), identityNetworkToLocalManga()).search(
            queries = listOf("first", "second"),
            settings = settings(resultCap = 5),
            originManga = origin(),
            sources = listOf(source),
            onResult = results::add,
        )

        val success = results.single().result as SameMangaCandidateResult.Success
        assertEquals(listOf("/a", "/b", "/c"), success.results.map(Manga::url))
    }

    @Test
    fun `identical relative URLs from different sources remain distinct candidates`() = runTest {
        val first = FakeSource(9_910_003) { page(manga("/shared", "Origin")) }
        val second = FakeSource(9_910_004) { page(manga("/shared", "Origin")) }
        val results = mutableListOf<SameMangaSourceResult>()

        searcher(UnconfinedTestDispatcher(testScheduler), identityNetworkToLocalManga()).search(
            queries = listOf("query"),
            settings = settings(resultCap = 5),
            originManga = origin(),
            sources = listOf(first, second),
            onResult = results::add,
        )

        val candidates = results.flatMap { result ->
            (result.result as SameMangaCandidateResult.Success).results
        }
        assertEquals(setOf(9_910_003L, 9_910_004L), candidates.map(Manga::source).toSet())
        assertEquals(listOf("/shared", "/shared"), candidates.map(Manga::url))
    }

    @Test
    fun `result cap preserves source result order when evidence ties`() = runTest {
        val source = FakeSource(9_910_005) {
            page(
                manga("/c", "Same"),
                manga("/a", "Same"),
                manga("/b", "Same"),
            )
        }
        val results = mutableListOf<SameMangaSourceResult>()

        searcher(UnconfinedTestDispatcher(testScheduler), identityNetworkToLocalManga()).search(
            queries = listOf("query"),
            settings = settings(resultCap = 2),
            originManga = origin(title = "Same"),
            sources = listOf(source),
            onResult = results::add,
        )

        val success = results.single().result as SameMangaCandidateResult.Success
        assertEquals(listOf("/c", "/a"), success.results.map(Manga::url))
    }

    @Test
    fun `current user rejection is suppressed before candidate delivery`() = runTest {
        val source = FakeSource(6) { page(manga("/rejected", "Same"), manga("/unknown", "Same")) }
        val rejected = tachiyomi.domain.taste.model.CrossSourceIdentityDecisionPolicy.userDecision(
            pair = tachiyomi.domain.taste.model.CrossSourceIdentityDecisionPolicy.canonicalPair(
                tachiyomi.domain.taste.model.CrossSourceRecordKey(99, "/origin"),
                tachiyomi.domain.taste.model.CrossSourceRecordKey(6, "/rejected"),
            ),
            value = tachiyomi.domain.taste.model.CrossSourceIdentityDecisionValue.USER_REJECTED,
            previous = null,
            timestamp = 10_000,
        )
        val results = mutableListOf<SameMangaSourceResult>()

        searcher(
            UnconfinedTestDispatcher(testScheduler),
            identityNetworkToLocalManga(),
            rejected,
        ).search(
            queries = listOf("query"),
            settings = settings(resultCap = 2),
            originManga = origin(title = "Same"),
            sources = listOf(source),
            onResult = results::add,
        )

        val success = results.single().result as SameMangaCandidateResult.Success
        assertEquals(listOf("/unknown"), success.results.map(Manga::url))
    }

    private fun searcher(
        dispatcher: CoroutineDispatcher,
        networkToLocalManga: NetworkToLocalManga = mockk(relaxed = true),
        identityDecision: tachiyomi.domain.taste.model.CrossSourceIdentityDecision? = null,
    ) = SameMangaCandidateSearcher(
        sourcePreferences = mockk<SourcePreferences>(relaxed = true),
        sourceManager = mockk<SourceManager>(relaxed = true),
        networkToLocalManga = networkToLocalManga,
        coroutineDispatcher = dispatcher,
        getIdentityDecisions = mockk<tachiyomi.domain.taste.interactor.GetCrossSourceIdentityDecisions>().also {
            coEvery { it.await(any()) } answers {
                identityDecision?.takeIf { decision -> decision.pair == firstArg() }
            }
        },
    )

    private fun settings(resultCap: Int = 2) = SameMangaMatchSettings(
        resultsPerSource = resultCap,
        preselectResults = false,
        previewSampleSize = 5,
        avoidFirstPages = true,
    )

    private fun origin(title: String = "Origin") = Manga.create().copy(
        source = 99,
        url = "/origin",
        ogTitle = title,
    )

    private fun identityNetworkToLocalManga(): NetworkToLocalManga {
        return NetworkToLocalManga(
            mockk<MangaRepository>().also { repository ->
                coEvery { repository.insertNetworkManga(any(), true) } coAnswers { arg<List<Manga>>(0) }
            },
        )
    }

    private fun manga(url: String, title: String) = SManga.create().apply {
        this.url = url
        this.title = title
    }

    private fun page(vararg mangas: SManga) = MangasPage(mangas.toList(), false)

    private inner class FakeSource(
        override val id: Long,
        private val search: suspend (String) -> MangasPage,
    ) : Source {
        override val name = "Fixture $id"
        override val lang = "en"
        override val supportsLatest = false

        override suspend fun getPopularManga(page: Int) = throw UnsupportedOperationException()
        override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
            val value = search(query)
            return value
        }
        override suspend fun getLatestUpdates(page: Int) = throw UnsupportedOperationException()
        override fun getFilterList() = FilterList()
        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ) = throw UnsupportedOperationException()
        override suspend fun getPageList(chapter: SChapter) = throw UnsupportedOperationException()
    }
}
