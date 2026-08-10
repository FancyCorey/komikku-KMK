package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ExecutionException

// KMK v0.8.10-fix3 -->
/**
 * Covers the new [SourceEvaluationProbeErrorKind.EXTENSION_INCOMPATIBLE] classification, added so
 * a broken/incompletely-packaged extension's [LinkageError] during Source Evaluation probing is
 * recorded as a technical incompatibility -- never as "weak taste fit" or a generic "internal
 * error" -- per the behavior contract's explicit requirement.
 */
class SourceEvaluationProbeErrorClassifierTest {

    @Test
    fun `a NoClassDefFoundError classifies as EXTENSION_INCOMPATIBLE, not INTERNAL`() {
        val kind = SourceEvaluationProbeErrorClassifier.classify(NoClassDefFoundError("okhttp3.zstd.Zstd"))
        assertEquals(SourceEvaluationProbeErrorKind.EXTENSION_INCOMPATIBLE, kind)
        assertNotEquals(SourceEvaluationProbeErrorKind.INTERNAL, kind)
    }

    @Test
    fun `a wrapped LinkageError unwraps and still classifies as EXTENSION_INCOMPATIBLE`() {
        val wrapped = ExecutionException(NoSuchMethodError("m"))
        assertEquals(SourceEvaluationProbeErrorKind.EXTENSION_INCOMPATIBLE, SourceEvaluationProbeErrorClassifier.classify(wrapped))
    }

    @Test
    fun `the storage key round-trips through fromStorageKey`() {
        val key = SourceEvaluationProbeErrorClassifier.classifyToStorageKey(NoClassDefFoundError("x"))
        assertEquals("EXTENSION_INCOMPATIBLE", key)
        assertEquals(SourceEvaluationProbeErrorKind.EXTENSION_INCOMPATIBLE, SourceEvaluationProbeErrorKind.fromStorageKey(key))
    }

    @Test
    fun `network and timeout classification is unaffected by the new kind`() {
        assertEquals(SourceEvaluationProbeErrorKind.NETWORK_UNAVAILABLE, SourceEvaluationProbeErrorClassifier.classify(UnknownHostException()))
        assertEquals(SourceEvaluationProbeErrorKind.TIMEOUT, SourceEvaluationProbeErrorClassifier.classify(SocketTimeoutException()))
        assertEquals(SourceEvaluationProbeErrorKind.NETWORK_UNAVAILABLE, SourceEvaluationProbeErrorClassifier.classify(IOException()))
    }

    @Test
    fun `an ordinary internal exception still classifies as INTERNAL`() {
        assertEquals(SourceEvaluationProbeErrorKind.INTERNAL, SourceEvaluationProbeErrorClassifier.classify(RuntimeException("oops")))
    }
}
// KMK <--
