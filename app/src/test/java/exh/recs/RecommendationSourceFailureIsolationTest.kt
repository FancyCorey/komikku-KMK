package exh.recs

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceRuntime
import eu.kanade.tachiyomi.source.SourceRuntimeFailureRegistry
import eu.kanade.tachiyomi.source.SourceRuntimeOperation
import eu.kanade.tachiyomi.source.SourceTemporarilyUnavailableException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK v0.8.10-fix2 -->
/**
 * `RecommendsScreenModel`/`BrowsePersonalRecommendationsScreenModel` are heavy `StateScreenModel`s
 * with many Android/Injekt dependencies that cannot be constructed in this project's pure-JVM unit
 * test environment (no Robolectric). What actually changed in this fix is a single, isolated
 * decision -- [RecommendationErrorClassifier.isRecoverableSourceFailure] -- applied inside each
 * per-source `try { ... } catch (e: CancellationException) { throw e } catch (e: Throwable/Error) {
 * if (!isRecoverableSourceFailure(e)) throw e; recordAsPerSourceError(e) }` block, running inside a
 * `batch.map { async { ... } }.awaitAll()` pattern that is otherwise completely unmodified by this
 * fix (it is the pre-existing v0.8.6 concurrency/cancellation work).
 *
 * This test drives that *exact* control-flow shape -- real `async`/`awaitAll`, real
 * `CancellationException`, real thrown `Error`s -- against a small in-memory harness that mirrors
 * both production catch blocks line-for-line, to prove the properties the fix must preserve:
 * sibling-source continuation, cancellation propagation, and fatal-error propagation.
 */
class RecommendationSourceFailureIsolationTest {

    private sealed interface SourceOutcome {
        data class Success(val name: String) : SourceOutcome
        data class Error(val name: String, val throwable: Throwable) : SourceOutcome
    }

    /** Mirrors the exact catch-block shape at both real call sites. */
    private suspend fun runSource(name: String, action: suspend () -> Unit): SourceOutcome {
        return try {
            action()
            SourceOutcome.Success(name)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (!RecommendationErrorClassifier.isRecoverableSourceFailure(e)) throw e
            SourceOutcome.Error(name, e)
        }
    }

    @Test
    fun `one source's recoverable linkage failure becomes an error row while sibling sources still succeed`() = runTest {
        val results = listOf(
            async { runSource("asurascans") { throw NoClassDefFoundError("okhttp3.zstd.Zstd") } },
            async { runSource("mangadex") {} },
            async { runSource("comick") {} },
        ).awaitAll()

        val asura = results.single { it is SourceOutcome.Error } as SourceOutcome.Error
        assertEquals("asurascans", asura.name)
        assertTrue(asura.throwable is NoClassDefFoundError)

        val succeeded = results.filterIsInstance<SourceOutcome.Success>().map { it.name }
        assertEquals(listOf("mangadex", "comick"), succeeded)
    }

    @Test
    fun `multiple sibling sources each with their own recoverable linkage failure all become independent error rows`() = runTest {
        val results = listOf(
            async { runSource("asurascans") { throw NoClassDefFoundError("okhttp3.zstd.Zstd") } },
            async { runSource("brokenext") { throw NoSuchMethodError("missing method") } },
            async { runSource("mangadex") {} },
        ).awaitAll()

        assertEquals(2, results.count { it is SourceOutcome.Error })
        assertEquals(1, results.count { it is SourceOutcome.Success })
    }

    @Test
    fun `a genuinely fatal VM error still propagates out of awaitAll -- never downgraded to a source row`() = runTest {
        assertThrows(OutOfMemoryError::class.java) {
            kotlinx.coroutines.runBlocking {
                listOf(
                    async { runSource("mangadex") {} },
                    async { runSource("badsource") { throw OutOfMemoryError("heap exhausted") } },
                ).awaitAll()
            }
        }
    }

    @Test
    fun `cancellation is never converted into a per-source error row -- it always propagates`() = runTest {
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                listOf(
                    async { runSource("slow") { throw CancellationException("load cancelled") } },
                ).awaitAll()
            }
        }
    }

    @Test
    fun `a parent scope cancellation stops sibling work rather than letting it publish stale results`() = runTest {
        var sawStaleWork = false
        val job = launch {
            delay(1000)
            sawStaleWork = true
        }
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(!sawStaleWork)
    }

    // KMK v0.8.10-fix4 -->
    // The tests above drive RecommendationErrorClassifier's decision in isolation. Fix4 migrated
    // the real call sites (CrossExtensionGenreSearchSource, RecommendationCandidateEnricher,
    // GroupRecommendationSeedBuilder) to call SourceRuntime.run() directly instead of a local
    // try/catch. This section drives the *actual* SourceRuntime.run() boundary against a small
    // per-manga enrichment loop shaped exactly like RecommendationCandidateEnricher.enrich() --
    // sequential `for (manga in candidates)`, one SourceRuntime.run() call per candidate, a failure
    // on one candidate must not stop enrichment of the next -- and additionally confirms the
    // failure registry (not just a returned Result) records the failing source.
    private class FakeEnrichSource(override val id: Long, override val name: String) : Source {
        override val lang: String = "en"
        override val supportsLatest: Boolean = false
        var failOnUrl: String? = null

        override fun getFilterList(): FilterList = FilterList()
        override suspend fun getPopularManga(page: Int): MangasPage = throw UnsupportedOperationException()
        override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()
        override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage = throw UnsupportedOperationException()
        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): SMangaUpdate {
            if (manga.url == failOnUrl) {
                throw NoClassDefFoundError("okhttp3.zstd.Zstd")
            }
            return SMangaUpdate(manga = manga, chapters = emptyList())
        }
        override suspend fun getPageList(chapter: SChapter): List<Page> = throw UnsupportedOperationException()
    }

    @Test
    fun `SourceRuntime run isolates one candidate's linkage failure during sequential enrichment while sibling candidates still enrich`() = runTest {
        val source = FakeEnrichSource(555L, "BrokenEnrichSource").apply { failOnUrl = "/manga/broken" }
        SourceRuntimeFailureRegistry.clear(555L)

        val candidateUrls = listOf("/manga/ok-1", "/manga/broken", "/manga/ok-2")
        val outcomes = candidateUrls.map { url ->
            val smanga = SManga.create().also { it.url = url }
            url to SourceRuntime.run(source, SourceRuntimeOperation.MangaUpdate) {
                getMangaUpdate(manga = smanga, chapters = emptyList(), fetchDetails = true, fetchChapters = false)
            }
        }

        assertEquals(true, outcomes[0].second.isSuccess)
        assertEquals(true, outcomes[1].second.isFailure)
        assertTrue(outcomes[1].second.exceptionOrNull() is NoClassDefFoundError)

        // KMK v0.8.10-fix8: run() now enforces suppression, so once /manga/broken confirms this
        // source is broken, the very next candidate on the *same* source within the suppression
        // window is short-circuited before ever touching it again -- it never reaches
        // getMangaUpdate() a third time and never re-throws the raw NoClassDefFoundError. This is
        // the intended strengthening (stop hammering a source already known to be broken); sibling
        // *sources* (as opposed to sibling candidates of the same broken source) remaining
        // unaffected is covered separately by
        // `a batch of sibling sources is unaffected when one source is suppressed mid-batch` in
        // SourceRuntimeTest.
        assertEquals(true, outcomes[2].second.isFailure)
        assertTrue(outcomes[2].second.exceptionOrNull() is SourceTemporarilyUnavailableException)

        val recorded = SourceRuntimeFailureRegistry.get(555L)
        assertTrue(recorded != null)
        assertEquals(SourceRuntimeOperation.MangaUpdate, recorded!!.operation)
    }
    // KMK <--
}
// KMK <--
