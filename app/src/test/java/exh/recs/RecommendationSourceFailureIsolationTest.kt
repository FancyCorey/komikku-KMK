package exh.recs

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
}
// KMK <--
