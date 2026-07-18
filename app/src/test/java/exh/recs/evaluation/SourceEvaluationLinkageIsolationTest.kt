package exh.recs.evaluation

import eu.kanade.tachiyomi.source.isRecoverableSourceRuntimeFailure
import eu.kanade.tachiyomi.source.unwrapSourceRuntimeCause
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
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
}
// KMK <--
