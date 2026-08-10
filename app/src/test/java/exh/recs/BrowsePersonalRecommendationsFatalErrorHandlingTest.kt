package exh.recs

import eu.kanade.tachiyomi.source.RecoverableSourceRuntimeException
import eu.kanade.tachiyomi.source.rethrowIfFatal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK v0.8.12-fix1 -->
/**
 * `BrowsePersonalRecommendationsScreenModel` cannot be constructed in this project's pure-JVM unit
 * test environment (no Robolectric -- same constraint documented in
 * [RecommendationSourceFailureIsolationTest]). What changed in this fix is the body of two
 * `catch (e: Error) { ... }` blocks in `searchSource()` -- the main tag-search attempt loop and the
 * v0.8.12 catalogue fallback probe -- both of which previously swallowed *every* `Error` subtype
 * unconditionally (including `OutOfMemoryError`/`StackOverflowError`/`ThreadDeath`) instead of only
 * recoverable per-source failures. This test drives that *exact* corrected catch-block shape --
 * `catch (e: Error) { rethrowIfFatal(e); lastError = e }` -- against real thrown `Error`s to prove the
 * fix: recoverable failures are still isolated, fatal ones are not.
 */
class BrowsePersonalRecommendationsFatalErrorHandlingTest {

    /** Mirrors the real catch(Error) block's now-corrected body at both call sites. */
    private fun runAttempt(action: () -> Unit): Throwable? {
        return try {
            action()
            null
        } catch (e: Exception) {
            e
        } catch (e: Error) {
            rethrowIfFatal(e)
            e
        }
    }

    @Test
    fun `a recoverable LinkageError is isolated as a per-source failure, not rethrown`() {
        val result = runAttempt { throw NoClassDefFoundError("okhttp3.zstd.Zstd") }
        assertTrue(result is NoClassDefFoundError)
    }

    @Test
    fun `a RecoverableSourceRuntimeException is isolated as a per-source failure, not rethrown`() {
        val result = runAttempt { throw RecoverableSourceRuntimeException(IllegalStateException("boom")) }
        assertTrue(result is RecoverableSourceRuntimeException)
    }

    @Test
    fun `an ordinary Exception is isolated as a per-source failure, not rethrown`() {
        val result = runAttempt { throw IllegalStateException("network hiccup") }
        assertTrue(result is IllegalStateException)
    }

    @Test
    fun `a fatal OutOfMemoryError is rethrown, never recorded as a per-source failure`() {
        assertThrows(OutOfMemoryError::class.java) {
            runAttempt { throw OutOfMemoryError("heap exhausted") }
        }
    }

    @Test
    fun `a fatal StackOverflowError is rethrown, never recorded as a per-source failure`() {
        assertThrows(StackOverflowError::class.java) {
            runAttempt { throw StackOverflowError("stack exhausted") }
        }
    }

    @Test
    fun `a fatal ThreadDeath is rethrown, never recorded as a per-source failure`() {
        assertThrows(ThreadDeath::class.java) {
            runAttempt { throw ThreadDeath() }
        }
    }

    @Test
    fun `an unrelated non-linkage Error such as AssertionError is rethrown, not treated as recoverable`() {
        assertThrows(AssertionError::class.java) {
            runAttempt { throw AssertionError("invariant violated") }
        }
    }

    // KMK v0.8.12 catalogue fallback: mirrors the fallback's own catch(Error) block, which swallows
    // the recoverable case entirely (no lastError assignment -- a failed probe silently falls through
    // to the normal NoMatches/FilteredOut status) but must still rethrow anything fatal.
    private fun runFallbackAttempt(action: () -> Unit): Boolean {
        var probeFailed = false
        try {
            action()
        } catch (e: Exception) {
            probeFailed = true
        } catch (e: Error) {
            rethrowIfFatal(e)
            probeFailed = true
        }
        return probeFailed
    }

    @Test
    fun `the catalogue fallback swallows a recoverable LinkageError and falls through, never marking success`() {
        val probeFailed = runFallbackAttempt { throw NoClassDefFoundError("okhttp3.zstd.Zstd") }
        assertTrue(probeFailed)
    }

    @Test
    fun `the catalogue fallback does not swallow a fatal OutOfMemoryError`() {
        assertThrows(OutOfMemoryError::class.java) {
            runFallbackAttempt { throw OutOfMemoryError("heap exhausted") }
        }
    }

    @Test
    fun `the catalogue fallback does not swallow a fatal StackOverflowError`() {
        assertThrows(StackOverflowError::class.java) {
            runFallbackAttempt { throw StackOverflowError("stack exhausted") }
        }
    }

    @Test
    fun `the catalogue fallback runs at most one bounded probe attempt, never a retry loop`() {
        // RecommendationCatalogueFallbackPolicy.shouldAttempt() is a single boolean gate evaluated
        // once per searchSource() call -- there is no loop or retry construct around the fallback
        // probe in production code, unlike the tag-search attempt chain (capped at
        // RecommendationQueryPlanner.MAX_STRATEGIES_PER_SOURCE). This asserts the gate itself is not
        // re-entrant: a source that already produced raw results (hadRawResults = true, as it would
        // be immediately after one fallback attempt runs) is never offered a second attempt.
        assertTrue(RecommendationCatalogueFallbackPolicy.shouldAttempt(hadRawResults = false, hadError = false))
        assertEquals(
            false,
            RecommendationCatalogueFallbackPolicy.shouldAttempt(hadRawResults = true, hadError = false),
        )
    }
}
// KMK <--
