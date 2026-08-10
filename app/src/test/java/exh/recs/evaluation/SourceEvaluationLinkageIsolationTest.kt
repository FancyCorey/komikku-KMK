package exh.recs.evaluation

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceRuntime
import eu.kanade.tachiyomi.source.SourceRuntimeFailureRegistry
import eu.kanade.tachiyomi.source.SourceRuntimeOperation
import eu.kanade.tachiyomi.source.isRecoverableSourceRuntimeFailure
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.unwrapSourceRuntimeCause
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK v0.8.10-fix3 -->
/**
 * `SourceEvaluationRunner` batches many extensions/sources with side effects (install/probe/
 * cleanup, DB writes) that this pure-JVM test environment cannot easily construct end-to-end. This
 * test instead drives the exact per-source probe catch-block shape added to
 * `SourceEvaluationRunner.kt` -- "record this one source as an error and continue to the next" --
 * against a small harness, proving a broken source's LinkageError does not abort the batch, and
 * that a genuinely fatal error still does.
 */
class SourceEvaluationLinkageIsolationTest {

    /** Mirrors SourceEvaluationRunner's per-source probe catch shape. */
    private fun probeSource(name: String, action: () -> Unit): Pair<String, SourceEvaluationProbeErrorKind?> {
        return try {
            action()
            name to null
        } catch (e: Throwable) {
            val unwrapped = e.unwrapSourceRuntimeCause()
            if (!unwrapped.isRecoverableSourceRuntimeFailure()) throw e
            name to SourceEvaluationProbeErrorClassifier.classify(unwrapped)
        }
    }

    @Test
    fun `one source's linkage failure is recorded as EXTENSION_INCOMPATIBLE and the batch continues to the next source`() {
        val results = listOf("asurascans", "mangadex", "comick").map { name ->
            if (name == "asurascans") {
                probeSource(name) { throw NoClassDefFoundError("okhttp3.zstd.Zstd") }
            } else {
                probeSource(name) { }
            }
        }

        val asura = results.single { it.first == "asurascans" }
        assertEquals(SourceEvaluationProbeErrorKind.EXTENSION_INCOMPATIBLE, asura.second)

        val healthy = results.filter { it.first != "asurascans" }
        assertEquals(listOf("mangadex" to null, "comick" to null), healthy)
    }

    @Test
    fun `a genuinely fatal error during a probe still propagates rather than being recorded`() {
        assertThrows(OutOfMemoryError::class.java) {
            probeSource("badsource") { throw OutOfMemoryError("heap exhausted") }
        }
    }

    // KMK v0.8.10-fix4 -->
    // Fix4 migrated SourceEvaluationRunner's Popular/Latest probe calls, and
    // SourceEvaluationCatalogueEnricher's per-item getMangaUpdate() enrichment call, to
    // SourceRuntime.run() instead of the catch(Exception)/catch(Error) pair the harness above
    // mirrors. This drives the real SourceRuntime.run() boundary across several sources' Popular
    // probes (one per source, like SourceEvaluationRunner's `for (source in catalogueSources)`
    // loop) to prove sibling *sources* still complete, and confirms the failure is recorded in
    // SourceRuntimeFailureRegistry -- not just swallowed into a local Result.
    private class FakeProbeSource(
        override val id: Long,
        override val name: String,
        private val failPopular: Boolean,
    ) : CatalogueSource {
        override val lang: String = "en"
        override val supportsLatest: Boolean = false
        override fun getFilterList(): FilterList = FilterList()
        override suspend fun getPopularManga(page: Int): MangasPage {
            if (failPopular) throw NoClassDefFoundError("okhttp3.zstd.Zstd")
            return MangasPage(emptyList(), false)
        }
        override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(emptyList(), false)
        override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage = throw UnsupportedOperationException()
        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): SMangaUpdate = throw UnsupportedOperationException()
        override suspend fun getPageList(chapter: SChapter): List<Page> = throw UnsupportedOperationException()
    }

    @Test
    fun `SourceRuntime run isolates one source's Popular-probe linkage failure while sibling sources in the same batch still probe successfully`() = runTest {
        val sources = listOf(
            FakeProbeSource(1L, "asurascans", failPopular = true),
            FakeProbeSource(2L, "mangadex", failPopular = false),
            FakeProbeSource(3L, "comick", failPopular = false),
        )
        sources.forEach { SourceRuntimeFailureRegistry.clear(it.id) }

        val outcomes = sources.map { source ->
            source.name to SourceRuntime.run(source, SourceRuntimeOperation.Popular) { getPopularManga(1) }
        }

        val asura = outcomes.single { it.first == "asurascans" }
        assertTrue(asura.second.isFailure)
        assertTrue(asura.second.exceptionOrNull() is NoClassDefFoundError)

        val siblings = outcomes.filter { it.first != "asurascans" }
        assertTrue(siblings.all { it.second.isSuccess })

        val recorded = SourceRuntimeFailureRegistry.get(1L)
        assertTrue(recorded != null)
        assertEquals(SourceRuntimeOperation.Popular, recorded!!.operation)
        // Sibling sources must never be recorded as failed just because one source in the batch was.
        assertEquals(null, SourceRuntimeFailureRegistry.get(2L))
        assertEquals(null, SourceRuntimeFailureRegistry.get(3L))
    }
    // KMK <--
}
// KMK <--
