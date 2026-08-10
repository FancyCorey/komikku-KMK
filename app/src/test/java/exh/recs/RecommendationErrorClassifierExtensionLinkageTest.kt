package exh.recs

import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK v0.8.10-fix2 -->
/**
 * Covers [RecommendationErrorClassifier.isRecoverableSourceFailure] and the extended
 * [RecommendationErrorKind] classification -- the pure decision behind the v0.8.10 fix2 crash fix.
 *
 * Confirmed root cause: a broken/incompletely-packaged extension (the installed Asura Scans
 * extension) throws `NoClassDefFoundError: okhttp3.zstd.Zstd` while constructing its HTTP client
 * during a per-source recommendation request. `NoClassDefFoundError` is a [LinkageError], which is a
 * [java.lang.Error], not a [Exception]. Before this fix: `RecommendsScreenModel`'s shared GROUP_PREVIEW
 * boundary rethrew every [Error] unconditionally (`if (e is Error) throw e`), and
 * `BrowsePersonalRecommendationsScreenModel`'s per-source search loop (the literal "For You" tab)
 * caught only `Exception` and never saw an `Error` at all -- both let the failure escape and crash the
 * whole load/process instead of becoming one failed source row.
 */
class RecommendationErrorClassifierExtensionLinkageTest {

    // --- Linkage-failure classification: recoverable ---

    @Test
    fun `NoClassDefFoundError -- the confirmed real-world crash cause -- is recoverable`() {
        val e = NoClassDefFoundError("Failed resolution of: Lokhttp3/zstd/Zstd;")
        assertTrue(RecommendationErrorClassifier.isRecoverableSourceFailure(e))
        assertEquals(RecommendationErrorKind.ExtensionIncompatible, RecommendationErrorClassifier.classify(e))
    }

    @Test
    fun `NoSuchMethodError is recoverable`() {
        val e = NoSuchMethodError("missing method")
        assertTrue(RecommendationErrorClassifier.isRecoverableSourceFailure(e))
        assertEquals(RecommendationErrorKind.ExtensionIncompatible, RecommendationErrorClassifier.classify(e))
    }

    @Test
    fun `NoSuchFieldError is recoverable`() {
        assertTrue(RecommendationErrorClassifier.isRecoverableSourceFailure(NoSuchFieldError("missing field")))
    }

    @Test
    fun `IncompatibleClassChangeError is recoverable`() {
        assertTrue(RecommendationErrorClassifier.isRecoverableSourceFailure(IncompatibleClassChangeError("incompatible")))
    }

    @Test
    fun `UnsatisfiedLinkError -- a LinkageError subtype -- is recoverable`() {
        assertTrue(RecommendationErrorClassifier.isRecoverableSourceFailure(UnsatisfiedLinkError("missing native lib")))
    }

    @Test
    fun `a generic LinkageError not covered by a more specific subtype is still recoverable`() {
        assertTrue(RecommendationErrorClassifier.isRecoverableSourceFailure(object : LinkageError("generic linkage failure") {}))
    }

    // --- Fatal VM errors: must still propagate, never downgraded ---

    @Test
    fun `OutOfMemoryError is never recoverable -- must always propagate`() {
        assertFalse(RecommendationErrorClassifier.isRecoverableSourceFailure(OutOfMemoryError("heap exhausted")))
    }

    @Test
    fun `StackOverflowError is never recoverable -- must always propagate`() {
        assertFalse(RecommendationErrorClassifier.isRecoverableSourceFailure(StackOverflowError()))
    }

    @Test
    fun `an unrelated non-linkage Error (e_g_ AssertionError) is never recoverable`() {
        assertFalse(RecommendationErrorClassifier.isRecoverableSourceFailure(AssertionError("invariant violated")))
    }

    // --- Ordinary exceptions: unaffected, still recoverable exactly as before ---

    @Test
    fun `an ordinary Exception remains recoverable, unaffected by this fix`() {
        assertTrue(RecommendationErrorClassifier.isRecoverableSourceFailure(RuntimeException("normal failure")))
    }

    @Test
    fun `a network IOException remains classified and recoverable exactly as before`() {
        val e = java.io.IOException("network down")
        assertTrue(RecommendationErrorClassifier.isRecoverableSourceFailure(e))
        assertEquals(RecommendationErrorKind.Network, RecommendationErrorClassifier.classify(e))
    }

    @Test
    fun `CancellationException classification is unaffected by the LinkageError addition`() {
        val e = CancellationException("cancelled")
        assertTrue(RecommendationErrorClassifier.isRecoverableSourceFailure(e))
        assertEquals(RecommendationErrorKind.Cancelled, RecommendationErrorClassifier.classify(e))
    }

    // --- Sanitized diagnostic category: no raw stack trace / class-resolution detail exposed ---

    @Test
    fun `the storage key round-trips through fromStorageKey and resolves to the real KMR string resource`() {
        val kind = RecommendationErrorKind.fromStorageKey(RecommendationErrorKind.ExtensionIncompatible.storageKey)
        assertEquals(RecommendationErrorKind.ExtensionIncompatible, kind)
        // Resolving a message resource for the sanitized category must not throw and must be the
        // real, registered KMR string -- never a raw exception message assembled ad hoc.
        val res = recommendationErrorMessageRes(RecommendationErrorKind.ExtensionIncompatible)
        assertEquals(tachiyomi.i18n.kmk.KMR.strings.rec_error_extension_incompatible, res)
    }

    @Test
    fun `classifyToStorageKey never returns the raw exception message for a linkage failure`() {
        val e = NoClassDefFoundError("Failed resolution of: Lokhttp3/zstd/Zstd;")
        val key = RecommendationErrorClassifier.classifyToStorageKey(e)
        assertEquals("REC_ERROR_EXTENSION_INCOMPATIBLE", key)
        assertFalse(key.contains("zstd", ignoreCase = true))
        assertFalse(key.contains("Failed resolution"))
    }
}
// KMK <--
