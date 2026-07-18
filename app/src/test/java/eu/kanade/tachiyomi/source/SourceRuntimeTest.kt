package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException

// KMK v0.8.10-fix3 -->
/**
 * Covers the pure classification/unwrap functions behind [SourceRuntime] -- the shared, app-wide
 * runtime boundary superseding v0.8.10-fix2's narrower, recommendation-only
 * [exh.recs.RecommendationErrorClassifier] containment. See
 * `docs/community/KMK_RECS_V0_8_10_FIX3_STRUCTURAL_SOURCE_RUNTIME_ISOLATION_IMPLEMENTATION.md` for
 * the full call-site inventory this boundary is meant to unify.
 */
class SourceRuntimeTest {

    private class FakeSource(override val id: Long, override val name: String, override val lang: String = "en") : Source {
        override val supportsLatest: Boolean = true
        override fun getFilterList(): FilterList = FilterList()
        override suspend fun getPopularManga(page: Int): MangasPage = throw UnsupportedOperationException()
        override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()
        override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage = throw UnsupportedOperationException()
        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): SMangaUpdate = throw UnsupportedOperationException()
        override suspend fun getPageList(chapter: SChapter): List<Page> = throw UnsupportedOperationException()
    }

    // --- Recoverable: LinkageError family ---

    @Test
    fun `NoClassDefFoundError is recoverable and classified as extension incompatible`() {
        val e = NoClassDefFoundError("Failed resolution of: Lokhttp3/zstd/Zstd;")
        assertTrue(e.isRecoverableSourceRuntimeFailure())
        assertEquals(SourceRuntimeFailureKind.ExtensionIncompatible, e.toSourceRuntimeFailureKind())
    }

    @Test
    fun `NoSuchMethodError, NoSuchFieldError, IncompatibleClassChangeError, and ExceptionInInitializerError are all recoverable`() {
        assertTrue(NoSuchMethodError("m").isRecoverableSourceRuntimeFailure())
        assertTrue(NoSuchFieldError("f").isRecoverableSourceRuntimeFailure())
        assertTrue(IncompatibleClassChangeError("c").isRecoverableSourceRuntimeFailure())
        assertTrue(ExceptionInInitializerError(RuntimeException("init failed")).isRecoverableSourceRuntimeFailure())
    }

    @Test
    fun `a generic LinkageError subtype not explicitly enumerated is still recoverable`() {
        assertTrue(object : LinkageError("generic") {}.isRecoverableSourceRuntimeFailure())
    }

    // --- Fatal: must remain fatal ---

    @Test
    fun `OutOfMemoryError, StackOverflowError, ThreadDeath, and AssertionError are all non-recoverable`() {
        assertFalse(OutOfMemoryError().isRecoverableSourceRuntimeFailure())
        assertFalse(StackOverflowError().isRecoverableSourceRuntimeFailure())
        assertFalse(ThreadDeath().isRecoverableSourceRuntimeFailure())
        assertFalse(AssertionError("invariant violated").isRecoverableSourceRuntimeFailure())
    }

    // --- Unwrap behavior ---

    @Test
    fun `a LinkageError wrapped in ExecutionException unwraps correctly before classification`() {
        val wrapped = ExecutionException(NoClassDefFoundError("okhttp3.zstd.Zstd"))
        val unwrapped = wrapped.unwrapSourceRuntimeCause()
        assertTrue(unwrapped is NoClassDefFoundError)
        assertTrue(unwrapped.isRecoverableSourceRuntimeFailure())
    }

    @Test
    fun `a LinkageError wrapped in CompletionException unwraps correctly`() {
        val wrapped = CompletionException(NoSuchMethodError("m"))
        assertTrue(wrapped.unwrapSourceRuntimeCause() is NoSuchMethodError)
    }

    @Test
    fun `a LinkageError wrapped in InvocationTargetException unwraps correctly`() {
        val wrapped = InvocationTargetException(IncompatibleClassChangeError("c"))
        assertTrue(wrapped.unwrapSourceRuntimeCause() is IncompatibleClassChangeError)
    }

    @Test
    fun `nested wrapper layers all unwrap down to the real cause`() {
        val inner = NoClassDefFoundError("okhttp3.zstd.Zstd")
        val wrapped = ExecutionException(CompletionException(inner))
        assertEquals(inner, wrapped.unwrapSourceRuntimeCause())
    }

    @Test
    fun `an un-wrapped exception with no cause is returned unchanged`() {
        val e = RuntimeException("plain failure")
        assertEquals(e, e.unwrapSourceRuntimeCause())
    }

    // --- Ordinary exceptions unaffected ---

    @Test
    fun `an ordinary Exception is recoverable and classified as internal by default`() {
        val e = RuntimeException("normal failure")
        assertTrue(e.isRecoverableSourceRuntimeFailure())
        assertEquals(SourceRuntimeFailureKind.Internal, e.toSourceRuntimeFailureKind())
    }

    @Test
    fun `network exceptions classify as Network or Timeout`() {
        assertEquals(SourceRuntimeFailureKind.Network, java.net.UnknownHostException().toSourceRuntimeFailureKind())
        assertEquals(SourceRuntimeFailureKind.Timeout, java.net.SocketTimeoutException().toSourceRuntimeFailureKind())
    }

    // --- SourceRuntime.run: end-to-end behavior + source identity + registry ---

    @Test
    fun `SourceRuntime run returns Result_success on a successful call`() = runTest {
        val source = FakeSource(1L, "Test Source")
        val result = SourceRuntime.run(source, SourceRuntimeOperation.FilterList) { getFilterList() }
        assertTrue(result.isSuccess)
    }

    @Test
    fun `SourceRuntime run converts a recoverable LinkageError into Result_failure and records source identity`() = runTest {
        val source = FakeSource(42L, "Asura Scans", "en")
        SourceRuntimeFailureRegistry.clear(42L)
        val result = SourceRuntime.run<Unit>(source, SourceRuntimeOperation.Popular) {
            throw NoClassDefFoundError("okhttp3.zstd.Zstd")
        }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoClassDefFoundError)

        val recorded = SourceRuntimeFailureRegistry.get(42L)
        assertTrue(recorded != null)
        assertEquals("Asura Scans", recorded!!.sourceName)
        assertEquals(SourceRuntimeOperation.Popular, recorded.operation)
        assertEquals(SourceRuntimeFailureKind.ExtensionIncompatible, recorded.kind)
    }

    @Test
    fun `SourceRuntime run rethrows a genuinely fatal error rather than returning a Result`() {
        val source = FakeSource(2L, "Test Source")
        assertThrows(OutOfMemoryError::class.java) {
            kotlinx.coroutines.runBlocking {
                SourceRuntime.run<Unit>(source, SourceRuntimeOperation.Search) {
                    throw OutOfMemoryError("heap exhausted")
                }
            }
        }
    }

    @Test
    fun `SourceRuntime run rethrows CancellationException rather than converting it`() {
        val source = FakeSource(3L, "Test Source")
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                SourceRuntime.run<Unit>(source, SourceRuntimeOperation.Search) {
                    throw CancellationException("cancelled")
                }
            }
        }
    }

    @Test
    fun `SourceRuntimeFailureRegistry isTemporarilyUnavailable is true immediately after a recorded failure`() = runTest {
        val source = FakeSource(99L, "Broken Source")
        SourceRuntimeFailureRegistry.clear(99L)
        SourceRuntime.run<Unit>(source, SourceRuntimeOperation.Latest) { throw NoClassDefFoundError("x") }
        assertTrue(SourceRuntimeFailureRegistry.isTemporarilyUnavailable(99L))
        SourceRuntimeFailureRegistry.clear(99L)
        assertFalse(SourceRuntimeFailureRegistry.isTemporarilyUnavailable(99L))
    }

    // KMK v0.8.10-fix4 -->
    // AsuraScans' real crash: getPopularManga()/getSearchManga()/etc. don't throw directly -- the
    // extension's own `client` property is a `by lazy { ... OkHttpClient with a Zstd interceptor ... }`
    // that only resolves `okhttp3.zstd.Zstd` the first time something reads `client`, which happens
    // from *inside* the overridden Source method body, not at construction time. This fake mirrors
    // that shape exactly (a lazy property read inside the method) instead of a `throw` as the first
    // statement, to prove SourceRuntime.run() classifies/records it the same way either way.
    private class LazyClientFakeSource(override val id: Long, override val name: String) : Source {
        override val lang: String = "en"
        override val supportsLatest: Boolean = true

        // Simulates AsuraScans' `private val client by lazy { ... }` -- NoClassDefFoundError is
        // thrown the first time this property is actually read, not when the object is constructed.
        private val client: Any by lazy { throw NoClassDefFoundError("Failed resolution of: Lokhttp3/zstd/Zstd;") }

        override fun getFilterList(): FilterList = FilterList()
        override suspend fun getPopularManga(page: Int): MangasPage {
            client.toString() // forces the lazy to resolve, mirroring the real crash site
            return MangasPage(emptyList(), false)
        }
        override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()
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
    fun `a NoClassDefFoundError from a lazy client property read inside the method body is classified and recorded, not just a top-level throw`() = runTest {
        val source = LazyClientFakeSource(777L, "AsuraScans")
        SourceRuntimeFailureRegistry.clear(777L)

        val result = SourceRuntime.run(source, SourceRuntimeOperation.Popular) { getPopularManga(1) }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoClassDefFoundError)

        val recorded = SourceRuntimeFailureRegistry.get(777L)
        assertTrue(recorded != null)
        assertEquals("AsuraScans", recorded!!.sourceName)
        assertEquals(SourceRuntimeFailureKind.ExtensionIncompatible, recorded.kind)

        // Reading the lazy a second time from a sibling call must still classify the same way --
        // the failure isn't a one-shot fluke of Kotlin's `by lazy` caching a null/sentinel.
        val second = SourceRuntime.run(source, SourceRuntimeOperation.Popular) { getPopularManga(1) }
        assertTrue(second.isFailure)
        assertTrue(second.exceptionOrNull() is NoClassDefFoundError)
    }
    // KMK <--
}
// KMK <--
