package exh.recs.evaluation

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for [SourceEvaluationCatalogueEnricher].
 *
 * See docs/recommendations/KMK_SOURCE_EVALUATION_TAG_ENRICHMENT_AND_SCORING_FIX_PLAN.md
 * ("Catalogue Detail Enrichment", required test list).
 *
 * Run with: ./gradlew :app:testDebugUnitTest --tests "*.SourceEvaluationCatalogueEnricherTest"
 */
class SourceEvaluationCatalogueEnricherTest {

    private fun sManga(url: String, title: String = url, genres: List<String>? = null): SManga =
        SManga.create().apply {
            this.url = url
            this.title = title
            this.genre = genres?.joinToString(", ")
        }

    /** Fake source whose getMangaDetails() behavior is fully controlled per test. */
    private class FakeCatalogueSource(
        private val detailsByUrl: Map<String, () -> SManga>,
    ) : CatalogueSource {
        override val id: Long = 1L
        override val lang: String = "en"
        override val name: String = "Fake Source"
        override val supportsLatest: Boolean = true
        var detailCallCount = 0
            private set

        override suspend fun getPopularManga(page: Int): MangasPage = throw UnsupportedOperationException()
        override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
            throw UnsupportedOperationException()
        override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()
        override fun getFilterList(): FilterList = FilterList()
        override suspend fun getChapterList(manga: SManga) = throw UnsupportedOperationException()
        override suspend fun getPageList(chapter: eu.kanade.tachiyomi.source.model.SChapter) = throw UnsupportedOperationException()
        override suspend fun getRelatedMangaList(
            manga: SManga,
            exceptionHandler: (Throwable) -> Unit,
            pushResults: suspend (relatedManga: Pair<String, List<SManga>>, completed: Boolean) -> Unit,
        ) = throw UnsupportedOperationException()

        override suspend fun getMangaDetails(manga: SManga): SManga {
            detailCallCount++
            return detailsByUrl[manga.url]?.invoke() ?: manga
        }
    }

    @Test
    fun `list entries without genres are enriched through getMangaDetails`() = runBlocking {
        val raw = listOf(sManga("/m/1", genres = null))
        val source = FakeCatalogueSource(
            detailsByUrl = mapOf("/m/1" to { sManga("/m/1", genres = listOf("Action")) }),
        )

        val result = SourceEvaluationCatalogueEnricher.enrich(source, raw, sourceId = 1L)

        assertEquals(1, result.detailAttempts)
        assertEquals(1, result.detailSuccesses)
        assertEquals(listOf("Action"), result.samples.single().genre)
        assertEquals(1, source.detailCallCount)
    }

    @Test
    fun `list entries with genres do not spend enrichment budget`() = runBlocking {
        val raw = listOf(sManga("/m/1", genres = listOf("Drama")))
        val source = FakeCatalogueSource(detailsByUrl = emptyMap())

        val result = SourceEvaluationCatalogueEnricher.enrich(source, raw, sourceId = 1L)

        assertEquals(0, result.detailAttempts)
        assertEquals(0, result.detailSuccesses)
        assertEquals(0, source.detailCallCount)
        assertEquals(listOf("Drama"), result.samples.single().genre)
    }

    @Test
    fun `enrichment cap is respected`() = runBlocking {
        val raw = (1..20).map { sManga("/m/$it", genres = null) }
        val source = FakeCatalogueSource(
            detailsByUrl = raw.associate { it.url to { sManga(it.url, genres = listOf("Action")) } },
        )

        val result = SourceEvaluationCatalogueEnricher.enrich(source, raw, sourceId = 1L, cap = 12)

        assertEquals(12, result.detailAttempts)
        assertEquals(12, result.detailSuccesses)
        assertEquals(20, result.samples.size)
        // Items beyond the cap keep their (empty) list-entry genre.
        assertTrue(result.samples.count { it.genre.isNullOrEmpty() } == 8)
    }

    @Test
    fun `detail failure keeps original candidate and does not abort evaluation`() = runBlocking {
        val raw = listOf(sManga("/m/1", genres = null), sManga("/m/2", genres = null))
        val source = FakeCatalogueSource(
            detailsByUrl = mapOf(
                "/m/1" to { throw RuntimeException("boom") },
                "/m/2" to { sManga("/m/2", genres = listOf("Comedy")) },
            ),
        )

        val result = SourceEvaluationCatalogueEnricher.enrich(source, raw, sourceId = 1L)

        assertEquals(2, result.detailAttempts)
        assertEquals(1, result.detailSuccesses)
        assertTrue(result.samples[0].genre.isNullOrEmpty())
        assertEquals(listOf("Comedy"), result.samples[1].genre)
    }

    @Test
    fun `timeout result keeps original candidate and increments attempt but not success`() = runBlocking {
        val raw = listOf(sManga("/m/1", genres = null))
        val source = FakeCatalogueSource(
            detailsByUrl = mapOf(
                "/m/1" to {
                    // This lambda body isn't actually suspending, so we simulate a hang via a
                    // synchronous long sleep is not possible here; instead we assert timeout
                    // behavior using a 0ms timeoutMs so withTimeoutOrNull always returns null.
                    sManga("/m/1", genres = listOf("Action"))
                },
            ),
        )

        val result = SourceEvaluationCatalogueEnricher.enrich(source, raw, sourceId = 1L, timeoutMs = 0L)

        assertEquals(1, result.detailAttempts)
        assertEquals(0, result.detailSuccesses)
        assertTrue(result.samples.single().genre.isNullOrEmpty())
    }

    @Test
    fun `cancellation propagates`() {
        val raw = listOf(sManga("/m/1", genres = null))
        class CancellingSource : CatalogueSource {
            override val id: Long = 1L
            override val lang: String = "en"
            override val name: String = "Cancelling Source"
            override val supportsLatest: Boolean = true
            override suspend fun getPopularManga(page: Int): MangasPage = throw UnsupportedOperationException()
            override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
                throw UnsupportedOperationException()
            override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()
            override fun getFilterList(): FilterList = FilterList()
            override suspend fun getChapterList(manga: SManga) = throw UnsupportedOperationException()
            override suspend fun getPageList(chapter: eu.kanade.tachiyomi.source.model.SChapter) = throw UnsupportedOperationException()
            override suspend fun getRelatedMangaList(
                manga: SManga,
                exceptionHandler: (Throwable) -> Unit,
                pushResults: suspend (relatedManga: Pair<String, List<SManga>>, completed: Boolean) -> Unit,
            ) = throw UnsupportedOperationException()

            override suspend fun getMangaDetails(manga: SManga): SManga {
                delay(1)
                throw CancellationException("cancelled")
            }
        }

        assertThrows(CancellationException::class.java) {
            runBlocking {
                SourceEvaluationCatalogueEnricher.enrich(CancellingSource(), raw, sourceId = 1L)
            }
        }
    }

    @Test
    fun `deduplicates raw items by url before enriching`() = runBlocking {
        val raw = listOf(sManga("/m/1", genres = null), sManga("/m/1", genres = null))
        val source = FakeCatalogueSource(
            detailsByUrl = mapOf("/m/1" to { sManga("/m/1", genres = listOf("Action")) }),
        )

        val result = SourceEvaluationCatalogueEnricher.enrich(source, raw, sourceId = 1L)

        assertEquals(1, result.samples.size)
        assertFalse(result.detailAttempts > 1)
    }
}
