package exh.recs

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Verifies the outer boundary used by RecommendationSearchHelper.beginSearch.
 * The Android/Injekt-backed helper is not constructible in this pure-JVM module, so the test keeps
 * the production catch ordering explicit: cancellation propagates, ordinary failure is classified,
 * and cleanup still runs for both outcomes.
 */
class RecommendationSearchCancellationTest {

    private suspend fun runSearchBoundary(
        search: suspend () -> Unit,
        onFailure: (Exception) -> Unit,
        onCleanup: () -> Unit,
    ) {
        try {
            search()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onFailure(e)
        } finally {
            onCleanup()
        }
    }

    @Test
    fun `cancellation propagates and cleanup runs`() = runTest {
        var cleaned = false
        assertThrows(CancellationException::class.java) {
            runBlocking {
                runSearchBoundary(
                    search = { throw CancellationException("cancelled") },
                    onFailure = { throw AssertionError("cancellation must not be classified") },
                    onCleanup = { cleaned = true },
                )
            }
        }
        assertTrue(cleaned)
    }

    @Test
    fun `ordinary failure is classified and cleanup runs`() = runTest {
        var classified: Exception? = null
        var cleaned = false
        runSearchBoundary(
            search = { throw IllegalStateException("source failed") },
            onFailure = { classified = it },
            onCleanup = { cleaned = true },
        )
        assertTrue(classified is IllegalStateException)
        assertTrue(cleaned)
    }
}
