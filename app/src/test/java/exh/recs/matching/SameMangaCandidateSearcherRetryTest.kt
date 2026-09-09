package exh.recs.matching

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.network.HttpException
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

// KMK v0.8.21-fix4 -->
/**
 * Regression coverage for R2/AUG-05's bounded same-source discovery retry (see
 * [SameMangaCandidateSearcher.searchOneSource]): the live-reproduced failure was a transient
 * HTTP 502 with no retry of any kind, permanently failing a source's candidate search for one bad
 * moment. These tests prove the retry is real, bounded to exactly one extra attempt, scoped only
 * to classifications where a second attempt could plausibly help, and safe under cancellation.
 */
class SameMangaCandidateSearcherRetryTest {

    @Test
    fun `a retryable failure on the first attempt succeeds on the retry`() = runTest {
        val source = CountingFakeSource(9_920_001) { call ->
            if (call == 1) throw HttpException(502)
            page(manga("/found", "Origin"))
        }
        val results = mutableListOf<SameMangaSourceResult>()

        searcher(UnconfinedTestDispatcher(testScheduler), identityNetworkToLocalManga()).search(
            queries = listOf("query"),
            settings = settings(),
            originManga = origin(),
            sources = listOf(source),
            onResult = results::add,
        )

        assertEquals(2, source.callCount)
        val success = results.single().result as SameMangaCandidateResult.Success
        assertEquals(listOf("/found"), success.results.map(Manga::url))
    }

    @Test
    fun `a retryable failure that fails again is bounded to exactly one retry`() = runTest {
        val source = CountingFakeSource(9_920_002) { throw HttpException(502) }
        val results = mutableListOf<SameMangaSourceResult>()

        searcher(UnconfinedTestDispatcher(testScheduler)).search(
            queries = listOf("query"),
            settings = settings(),
            originManga = origin(),
            sources = listOf(source),
            onResult = results::add,
        )

        assertEquals(2, source.callCount)
        val failure = results.single().result as SameMangaCandidateResult.Error
        assertTrue(failure.throwable is HttpException)
        assertEquals(502, (failure.throwable as HttpException).code)
    }

    @Test
    fun `a non-retryable failure is never retried`() = runTest {
        val authSource = CountingFakeSource(9_920_003) { throw HttpException(401) }
        val illegalArgSource = CountingFakeSource(9_920_004) { throw IllegalArgumentException("not retryable") }
        val results = mutableListOf<SameMangaSourceResult>()

        searcher(UnconfinedTestDispatcher(testScheduler)).search(
            queries = listOf("query"),
            settings = settings(),
            originManga = origin(),
            sources = listOf(authSource, illegalArgSource),
            onResult = results::add,
        )

        assertEquals(1, authSource.callCount)
        assertEquals(1, illegalArgSource.callCount)
        assertEquals(2, results.count { it.result is SameMangaCandidateResult.Error })
    }

    @Test
    fun `multiple queries only retry the query that actually failed`() = runTest {
        val source = CountingFakeSource(9_920_005) { call ->
            // First query's single call fails once; second query always succeeds first try.
            if (call == 1) throw HttpException(503)
            page(manga("/q$call", "Origin"))
        }
        val results = mutableListOf<SameMangaSourceResult>()

        searcher(UnconfinedTestDispatcher(testScheduler), identityNetworkToLocalManga()).search(
            queries = listOf("first", "second"),
            settings = settings(resultCap = 5),
            originManga = origin(),
            sources = listOf(source),
            onResult = results::add,
        )

        // call 1 (first query, fails) + call 2 (first query retry, succeeds) + call 3 (second query, succeeds)
        assertEquals(3, source.callCount)
        val success = results.single().result as SameMangaCandidateResult.Success
        assertEquals(setOf("/q2", "/q3"), success.results.map(Manga::url).toSet())
    }

    @Test
    fun `cancellation during the retry attempt propagates without a late result callback`() = runTest {
        val enteredRetry = CompletableDeferred<Unit>()
        val source = CountingFakeSource(9_920_006) { call ->
            if (call == 1) throw HttpException(502)
            enteredRetry.complete(Unit)
            awaitCancellation()
        }
        val results = mutableListOf<SameMangaSourceResult>()

        val job = launch {
            searcher(UnconfinedTestDispatcher(testScheduler)).search(
                queries = listOf("query"),
                settings = settings(),
                originManga = origin(),
                sources = listOf(source),
                onResult = results::add,
            )
        }
        enteredRetry.await()
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertTrue(results.isEmpty())
        assertEquals(2, source.callCount)
    }

    private fun searcher(
        dispatcher: CoroutineDispatcher,
        networkToLocalManga: NetworkToLocalManga = mockk(relaxed = true),
    ) = SameMangaCandidateSearcher(
        sourcePreferences = mockk<SourcePreferences>(relaxed = true),
        sourceManager = mockk<SourceManager>(relaxed = true),
        networkToLocalManga = networkToLocalManga,
        coroutineDispatcher = dispatcher,
        getIdentityDecisions = mockk<tachiyomi.domain.taste.interactor.GetCrossSourceIdentityDecisions>().also {
            coEvery { it.await(any()) } returns null
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

    /** A [Source] fixture whose search behavior is a function of the call count (1-indexed). */
    private inner class CountingFakeSource(
        override val id: Long,
        private val search: suspend (call: Int) -> MangasPage,
    ) : Source {
        var callCount = 0
            private set
        override val name = "Fixture $id"
        override val lang = "en"
        override val supportsLatest = false

        override suspend fun getPopularManga(page: Int) = throw UnsupportedOperationException()
        override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
            callCount++
            return search(callCount)
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
// KMK <--
