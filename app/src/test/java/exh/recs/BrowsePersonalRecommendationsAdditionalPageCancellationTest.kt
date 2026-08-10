package exh.recs

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * KMK security-hardening pass: `BrowsePersonalRecommendationsScreenModel.discoverAdditionalPage()`
 * previously wrapped its whole extra-page probe in
 * `runCatching { ... }.onFailure { if (e is CancellationException) throw e; ... }.getOrDefault(...)`.
 * That outer guard looked safe, but the body contained two NESTED `runCatching` calls (chapter-count
 * and known-manga-id lookups) that each caught `Throwable` -- including `CancellationException` --
 * internally and returned a fallback value before the exception could ever reach the outer rethrow.
 * A cancelled coroutine would therefore silently continue running and report a normal probe result
 * instead of being torn down.
 *
 * The full screen model can't be constructed in this pure-JVM test module (no Robolectric --
 * `BrowsePersonalRecommendationsScreenModel` needs a real Android `Context`), so this test mirrors the
 * exact corrected try/catch shape now used at every suspend boundary inside `discoverAdditionalPage()`,
 * proving cancellation propagates through a nested nested-then-outer boundary instead of being
 * absorbed by an inner catch.
 */
class BrowsePersonalRecommendationsAdditionalPageCancellationTest {

    /** Mirrors the corrected inner (chapter-count/known-id lookup) + outer (probe) boundary shape. */
    private suspend fun runProbe(
        innerLookup: suspend () -> Map<Long, Int>,
        outerSearch: suspend () -> List<String>,
    ): Pair<List<String>, String> {
        return try {
            val chapterCounts = try {
                innerLookup()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyMap()
            }
            val results = outerSearch()
            (results + chapterCounts.keys.map { it.toString() }) to "SUCCESS"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList<String>() to "ERROR"
        }
    }

    @Test
    fun `a successful probe returns results and SUCCESS status`() = runTest {
        val (results, status) = runProbe(
            innerLookup = { mapOf(1L to 5) },
            outerSearch = { listOf("manga-a") },
        )
        assertEquals(listOf("manga-a", "1"), results)
        assertEquals("SUCCESS", status)
    }

    @Test
    fun `an ordinary exception from the inner chapter-count lookup falls back to empty, not a crash`() = runTest {
        val (results, status) = runProbe(
            innerLookup = { throw IllegalStateException("db hiccup") },
            outerSearch = { listOf("manga-a") },
        )
        assertEquals(listOf("manga-a"), results)
        assertEquals("SUCCESS", status)
    }

    @Test
    fun `an ordinary exception from the outer search is recorded as ERROR, not a crash`() = runTest {
        val (results, status) = runProbe(
            innerLookup = { emptyMap() },
            outerSearch = { throw IllegalStateException("source failed") },
        )
        assertEquals(emptyList<String>(), results)
        assertEquals("ERROR", status)
    }

    @Test
    fun `cancellation inside the nested inner lookup propagates through the outer boundary`() = runTest {
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                runProbe(
                    innerLookup = { throw CancellationException("scope cancelled") },
                    outerSearch = { listOf("manga-a") },
                )
            }
        }
    }

    @Test
    fun `cancellation inside the outer search propagates instead of being recorded as ERROR`() = runTest {
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                runProbe(
                    innerLookup = { emptyMap() },
                    outerSearch = { throw CancellationException("scope cancelled") },
                )
            }
        }
    }
}
