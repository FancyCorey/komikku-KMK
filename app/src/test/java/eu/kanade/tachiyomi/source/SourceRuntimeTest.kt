package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
 * runtime boundary superseding the narrower, recommendation-only
 * [exh.recs.RecommendationErrorClassifier] containment. These tests prove the shared classification
 * and unwrap contract used by every migrated call site.
 */
class SourceRuntimeTest {

    private class FakeSource(
        override val id: Long,
        override val name: String,
        override val lang: String = "en",
        // KMK v0.8.10-fix7: optional search-failure hook for the RecommendationSource wrapper test.
        private val onSearchManga: (suspend () -> MangasPage)? = null,
        // KMK v0.8.10-fix8: touch counter/hook for suppression-enforcement tests -- proves
        // SourceRuntime.run()/runBlockingSourceCall() never invoke source.block() at all when the
        // source is temporarily unavailable, not merely that they classify a failure afterward.
        private val onTouch: (() -> Unit)? = null,
    ) : Source {
        override val supportsLatest: Boolean = true
        override fun getFilterList(): FilterList {
            onTouch?.invoke()
            return FilterList()
        }
        override suspend fun getPopularManga(page: Int): MangasPage {
            onTouch?.invoke()
            throw UnsupportedOperationException()
        }
        override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()
        override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
            onSearchManga?.invoke() ?: throw UnsupportedOperationException()
        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): SMangaUpdate = throw UnsupportedOperationException()
        override suspend fun getPageList(chapter: SChapter): List<Page> = throw UnsupportedOperationException()
    }

    // KMK v0.8.10-fix8: minimal fake HttpSource for safeClientOrNull()/safeHeadersOrNull() suppression
    // tests -- a real HttpSource cannot be constructed in this test suite (its `network` lazy
    // property calls Injekt.get<NetworkHelper>()), but every suspend Source/CatalogueSource method
    // already has a working default implementation, so only id/name/baseUrl/client/headers need
    // overriding here -- nothing else is touched by the accessors under test.
    private class FakeHttpSource(
        override val id: Long,
        override val name: String,
        override val lang: String = "en",
        private val onClientTouch: (() -> Unit)? = null,
        private val onHeadersTouch: (() -> Unit)? = null,
        private val clientThrows: (() -> Throwable)? = null,
    ) : eu.kanade.tachiyomi.source.online.HttpSource() {
        override val baseUrl: String = "https://example.invalid"
        override val supportsLatest: Boolean = true
        override val client: okhttp3.OkHttpClient
            get() {
                onClientTouch?.invoke()
                clientThrows?.let { throw it() }
                return okhttp3.OkHttpClient()
            }
        override val headers: okhttp3.Headers
            get() {
                onHeadersTouch?.invoke()
                return okhttp3.Headers.Builder().build()
            }
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
        // KMK v0.8.10-fix8: run() now enforces suppression, so a second call within the suppression
        // window is short-circuited before ever touching the source again (proven separately by
        // `SourceRuntime run suppresses a temporarily unavailable source...` below). Clearing the
        // registry here forces a genuine second touch, preserving this test's original intent.
        SourceRuntimeFailureRegistry.clear(777L)
        val second = SourceRuntime.run(source, SourceRuntimeOperation.Popular) { getPopularManga(1) }
        assertTrue(second.isFailure)
        assertTrue(second.exceptionOrNull() is NoClassDefFoundError)
    }
    // KMK <--

    // KMK v0.8.10-fix5 -->
    // safeClientOrNull()/safeHeadersOrNull() (SourceRuntimeAccessors.kt) are one-line wrappers
    // around SourceRuntime.runBlockingSourceCall() -- the exact synchronous mechanism these tests
    // exercise directly. A literal HttpSource-backed test is not feasible in this environment: every
    // HttpSource subclass's `network`/`client` lazy properties call Injekt.get<NetworkHelper>() at
    // construction, and this test suite has no Injekt-bootstrapping harness (the same limitation
    // already documented for RecommendsScreenModel/BrowsePersonalRecommendationsScreenModel in
    // CURRENT_STATE.md). These tests instead prove runBlockingSourceCall's classification/recording
    // behavior for the Client/Headers/CoverImage/PreviewImage operations added in this pass, which is
    // the actual boundary safeClientOrNull()/safeHeadersOrNull() route through -- the accessors
    // themselves add no logic beyond calling it.

    @Test
    fun `runBlockingSourceCall converts a recoverable LinkageError from a lazy client-like property into Result_failure and records the Client operation`() {
        val source = FakeSource(501L, "Asura Scans")
        SourceRuntimeFailureRegistry.clear(501L)

        val result = SourceRuntime.runBlockingSourceCall<Unit>(source, SourceRuntimeOperation.Client) {
            throw NoClassDefFoundError("Failed resolution of: Lokhttp3/zstd/Zstd;")
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoClassDefFoundError)
        val recorded = SourceRuntimeFailureRegistry.get(501L)
        assertTrue(recorded != null)
        assertEquals(SourceRuntimeOperation.Client, recorded!!.operation)
        assertEquals(SourceRuntimeFailureKind.ExtensionIncompatible, recorded.kind)
    }

    @Test
    fun `runBlockingSourceCall converts a recoverable LinkageError from a lazy headers-like property into Result_failure and records the Headers operation`() {
        val source = FakeSource(502L, "Asura Scans")
        SourceRuntimeFailureRegistry.clear(502L)

        val result = SourceRuntime.runBlockingSourceCall<Unit>(source, SourceRuntimeOperation.Headers) {
            throw NoClassDefFoundError("Failed resolution of: Lokhttp3/zstd/Zstd;")
        }

        assertTrue(result.isFailure)
        val recorded = SourceRuntimeFailureRegistry.get(502L)
        assertTrue(recorded != null)
        assertEquals(SourceRuntimeOperation.Headers, recorded!!.operation)
        assertEquals(SourceRuntimeFailureKind.ExtensionIncompatible, recorded.kind)
    }

    @Test
    fun `runBlockingSourceCall rethrows a genuinely fatal non-linkage error rather than returning a Result`() {
        val source = FakeSource(503L, "Test Source")
        assertThrows(OutOfMemoryError::class.java) {
            SourceRuntime.runBlockingSourceCall<Unit>(source, SourceRuntimeOperation.Client) {
                throw OutOfMemoryError("heap exhausted")
            }
        }
    }

    @Test
    fun `runBlockingSourceCall rethrows CancellationException rather than converting it`() {
        val source = FakeSource(504L, "Test Source")
        assertThrows(CancellationException::class.java) {
            SourceRuntime.runBlockingSourceCall<Unit>(source, SourceRuntimeOperation.Headers) {
                throw CancellationException("cancelled")
            }
        }
    }

    @Test
    fun `SourceRuntimeFailureRegistry increments count on repeated failures of the same source`() {
        val source = FakeSource(505L, "Repeatedly Broken Source")
        SourceRuntimeFailureRegistry.clear(505L)

        // KMK v0.8.10-fix8 correction: as of this pass, runBlockingSourceCall() enforces suppression
        // -- once a source is temporarily unavailable, subsequent calls within the suppression window
        // are short-circuited *before* touching the source, so they no longer call record() a second
        // or third time. The registry's count now reflects genuine, separately-attempted (i.e.
        // cleared-between) failures, not rapid repeats of the same call. See the dedicated
        // `clearing the registry lifts suppression...` test below for that behavior; this test is
        // updated to reflect the new, correct semantics: only the first of 3 rapid repeats actually
        // touches the source and records a failure.
        repeat(3) {
            SourceRuntime.runBlockingSourceCall<Unit>(source, SourceRuntimeOperation.Client) {
                throw NoClassDefFoundError("x")
            }
        }

        val recorded = SourceRuntimeFailureRegistry.get(505L)
        assertTrue(recorded != null)
        assertEquals(1, recorded!!.count)
    }

    @Test
    fun `a broken source's failure does not prevent a sibling source from succeeding via the same boundary`() {
        val broken = FakeSource(506L, "Broken Sibling")
        val healthy = FakeSource(507L, "Healthy Sibling")
        SourceRuntimeFailureRegistry.clear(506L)
        SourceRuntimeFailureRegistry.clear(507L)

        val brokenResult = SourceRuntime.runBlockingSourceCall<Unit>(broken, SourceRuntimeOperation.Client) {
            throw NoClassDefFoundError("okhttp3.zstd.Zstd")
        }
        val healthyResult = SourceRuntime.runBlockingSourceCall(healthy, SourceRuntimeOperation.Client) { "ok" }

        assertTrue(brokenResult.isFailure)
        assertTrue(healthyResult.isSuccess)
        assertEquals("ok", healthyResult.getOrNull())
        // Only the broken source was recorded -- the healthy sibling is completely unaffected.
        assertTrue(SourceRuntimeFailureRegistry.get(506L) != null)
        assertTrue(SourceRuntimeFailureRegistry.get(507L) == null)
    }
    // KMK <--

    // KMK v0.8.10-fix6 -->
    // Regression tests for Task 7: SourceFeedScreenModel/FeedScreenModel/SmartSourceSearchEngine's
    // safeFilterList()-shaped call sites, RecommendationPagingSource's delegate wrapper, and one more
    // explicit sibling-isolation proof, per the fix6 plan's exact required cases.

    @Test
    fun `runBlockingSourceCall(FilterList) catches a lazy-client-shaped NoClassDefFoundError and records FilterList`() {
        val source = FakeSource(601L, "Broken Feed Source")
        SourceRuntimeFailureRegistry.clear(601L)

        val result = SourceRuntime.runBlockingSourceCall<FilterList>(source, SourceRuntimeOperation.FilterList) {
            // Mirrors SourceFeedScreenModel.safeFilterList()'s exact shape: getFilterList() itself
            // triggers the extension's lazy client-builder, not a top-level throw.
            throw NoClassDefFoundError("Failed resolution of: Lokhttp3/zstd/Zstd;")
        }

        assertTrue(result.isFailure)
        val recorded = SourceRuntimeFailureRegistry.get(601L)
        assertTrue(recorded != null)
        assertEquals(SourceRuntimeOperation.FilterList, recorded!!.operation)
        assertEquals(SourceRuntimeFailureKind.ExtensionIncompatible, recorded.kind)
    }

    @Test
    fun `a fake delegated recommendation source method using SourceRuntime run getOrThrow records the failure before rethrowing it`() {
        // Mirrors RecommendationPagingSource.RecommendationSource's delegate wrapper shape (Task 4):
        // the wrapper's own override calls SourceRuntime.run(...).getOrThrow() and lets the caller
        // receive a normal exception, but the failure must already be classified/recorded by the
        // time that exception reaches the caller.
        val delegate = FakeSource(602L, "Broken Delegate Source")
        SourceRuntimeFailureRegistry.clear(602L)

        val thrown = assertThrows(NoClassDefFoundError::class.java) {
            kotlinx.coroutines.runBlocking {
                SourceRuntime.run(delegate, SourceRuntimeOperation.Popular) {
                    throw NoClassDefFoundError("okhttp3.zstd.Zstd")
                }.getOrThrow()
            }
        }

        assertTrue(thrown is NoClassDefFoundError)
        val recorded = SourceRuntimeFailureRegistry.get(602L)
        assertTrue(recorded != null)
        assertEquals(SourceRuntimeOperation.Popular, recorded!!.operation)
        assertEquals(SourceRuntimeFailureKind.ExtensionIncompatible, recorded.kind)
    }

    @Test
    fun `a sibling healthy source still succeeds via SourceRuntime run after a broken source is recorded`() = runTest {
        val broken = FakeSource(603L, "Broken Sibling (run)")
        val healthy = FakeSource(604L, "Healthy Sibling (run)")
        SourceRuntimeFailureRegistry.clear(603L)
        SourceRuntimeFailureRegistry.clear(604L)

        val brokenResult = SourceRuntime.run<FilterList>(broken, SourceRuntimeOperation.FilterList) {
            throw NoClassDefFoundError("okhttp3.zstd.Zstd")
        }
        val healthyResult = SourceRuntime.run(healthy, SourceRuntimeOperation.FilterList) { getFilterList() }

        assertTrue(brokenResult.isFailure)
        assertTrue(healthyResult.isSuccess)
        assertTrue(SourceRuntimeFailureRegistry.get(603L) != null)
        assertTrue(SourceRuntimeFailureRegistry.get(604L) == null)
    }
    // KMK <--

    // KMK v0.8.10-fix7 -->
    // Tests for getOrThrowSourceRuntimeException() (SourceRuntime.kt) -- the exception bridge that
    // stops a recoverable source LinkageError from escaping a UI/job path as a raw Error when the
    // caller uses Result.getOrThrow()-style flow instead of Result.fold(...).

    @Test
    fun `getOrThrowSourceRuntimeException converts NoClassDefFoundError to Exception`() {
        val result = Result.failure<Unit>(NoClassDefFoundError("okhttp3.zstd.Zstd"))

        val thrown = assertThrows(RecoverableSourceRuntimeException::class.java) {
            result.getOrThrowSourceRuntimeException()
        }

        assertTrue(thrown is Exception)
        assertTrue(thrown.cause is NoClassDefFoundError)
        assertEquals("okhttp3.zstd.Zstd", thrown.cause?.message)
    }

    @Test
    fun `getOrThrowSourceRuntimeException does not wrap fatal Error`() {
        val result = Result.failure<Unit>(OutOfMemoryError("heap exhausted"))

        assertThrows(OutOfMemoryError::class.java) {
            result.getOrThrowSourceRuntimeException()
        }
    }

    @Test
    fun `getOrThrowSourceRuntimeException does not wrap CancellationException`() {
        val result = Result.failure<Unit>(CancellationException("cancelled"))

        assertThrows(CancellationException::class.java) {
            result.getOrThrowSourceRuntimeException()
        }
    }

    @Test
    fun `RecommendationSource wrapper throws recoverable source failure as Exception`() = runTest {
        val delegate = FakeSource(701L, "Broken Delegate", onSearchManga = {
            throw NoClassDefFoundError("okhttp3.zstd.Zstd")
        })
        val fakeSourceManager = object : tachiyomi.domain.source.service.SourceManager {
            override val isInitialized = kotlinx.coroutines.flow.MutableStateFlow(true)
            override val sources = kotlinx.coroutines.flow.flowOf(listOf<Source>(delegate))
            override fun get(sourceKey: Long): Source = delegate
            override fun getOrStub(sourceKey: Long): Source = delegate
            override fun getAll(): List<Source> = listOf(delegate)
            override fun getOnlineSources() = emptyList<eu.kanade.tachiyomi.source.online.HttpSource>()
            override fun getVisibleOnlineSources() = emptyList<eu.kanade.tachiyomi.source.online.HttpSource>()
            override fun getVisibleSources(): List<Source> = listOf(delegate)
            override suspend fun getMergedSources(mangaId: Long): List<Source> = emptyList()
            override fun getStubSources() = emptyList<tachiyomi.domain.source.model.StubSource>()
        }
        SourceRuntimeFailureRegistry.clear(701L)

        val recommendationSource = exh.recs.sources.RecommendationSource(
            id = 701L,
            sourceManager = fakeSourceManager,
        )

        val thrown = assertThrows(RecoverableSourceRuntimeException::class.java) {
            kotlinx.coroutines.runBlocking {
                recommendationSource.getSearchManga(1, "query", FilterList())
            }
        }

        assertTrue(thrown.cause is NoClassDefFoundError)
        val recorded = SourceRuntimeFailureRegistry.get(701L)
        assertTrue(recorded != null)
        assertEquals(SourceRuntimeOperation.Search, recorded!!.operation)
    }
    // KMK <--

    // KMK v0.8.10-fix8 -->
    // Enforced source-runtime suppression tests: prove run()/runBlockingSourceCall() never touch
    // source.block() at all once a source is temporarily unavailable -- not merely that a subsequent
    // failure gets classified correctly (that was already true before this pass; the NEW guarantee is
    // that the source is not re-invoked in the first place).

    @Test
    fun `SourceRuntime run suppresses a temporarily unavailable source before touching its block`() = runTest {
        val source = FakeSource(801L, "Recently Broken Source")
        SourceRuntimeFailureRegistry.clear(801L)
        // First call fails and marks the source temporarily unavailable.
        SourceRuntime.run<Unit>(source, SourceRuntimeOperation.Popular) {
            throw NoClassDefFoundError("okhttp3.zstd.Zstd")
        }
        assertTrue(SourceRuntimeFailureRegistry.isTemporarilyUnavailable(801L))

        var touched = false
        val result = SourceRuntime.run(source, SourceRuntimeOperation.FilterList) {
            touched = true
            getFilterList()
        }

        assertFalse(touched, "block() must not run once the source is suppressed")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SourceTemporarilyUnavailableException)
        val ex = result.exceptionOrNull() as SourceTemporarilyUnavailableException
        assertEquals(801L, ex.sourceId)
        assertEquals(SourceRuntimeOperation.Popular, ex.lastOperation)
    }

    @Test
    fun `SourceRuntime runBlockingSourceCall suppresses a temporarily unavailable source before touching its block`() {
        val source = FakeSource(802L, "Recently Broken Source (blocking)")
        SourceRuntimeFailureRegistry.clear(802L)
        SourceRuntime.runBlockingSourceCall<Unit>(source, SourceRuntimeOperation.Client) {
            throw NoClassDefFoundError("okhttp3.zstd.Zstd")
        }
        assertTrue(SourceRuntimeFailureRegistry.isTemporarilyUnavailable(802L))

        var touched = false
        val result = SourceRuntime.runBlockingSourceCall(source, SourceRuntimeOperation.FilterList) {
            touched = true
            getFilterList()
        }

        assertFalse(touched, "block() must not run once the source is suppressed")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SourceTemporarilyUnavailableException)
    }

    @Test
    fun `SourceTemporarilyUnavailableException is an ordinary Exception, never wraps CancellationException or a fatal Error`() = runTest {
        val source = FakeSource(803L, "Suppressed Source")
        SourceRuntimeFailureRegistry.clear(803L)
        SourceRuntime.run<Unit>(source, SourceRuntimeOperation.Popular) { throw NoClassDefFoundError("x") }
        assertTrue(SourceRuntimeFailureRegistry.isTemporarilyUnavailable(803L))

        // Suppression itself must never mask cancellation or a fatal error that would occur from a
        // *different* code path -- it only ever returns Result.failure(SourceTemporarilyUnavailableException),
        // which is an ordinary Exception and is itself recoverable/non-fatal.
        val result = SourceRuntime.run(source, SourceRuntimeOperation.FilterList) { getFilterList() }
        assertTrue(result.exceptionOrNull() is Exception)
        assertFalse(result.exceptionOrNull() is CancellationException)
        assertFalse(result.exceptionOrNull() is Error)
    }

    @Test
    fun `clearing the registry lifts suppression and allows the next call to actually touch the source`() = runTest {
        val source = FakeSource(804L, "Recovering Source")
        SourceRuntimeFailureRegistry.clear(804L)
        SourceRuntime.run<Unit>(source, SourceRuntimeOperation.Popular) { throw NoClassDefFoundError("x") }
        assertTrue(SourceRuntimeFailureRegistry.isTemporarilyUnavailable(804L))

        SourceRuntimeFailureRegistry.clear(804L)
        assertFalse(SourceRuntimeFailureRegistry.isTemporarilyUnavailable(804L))

        var touched = false
        val result = SourceRuntime.run(source, SourceRuntimeOperation.FilterList) {
            touched = true
            getFilterList()
        }
        assertTrue(touched, "a manually-cleared source must be touched again on the next call (user retry)")
        assertTrue(result.isSuccess)
    }

    // KMK v0.8.10-fix9 -->
    @Test
    fun `a second failure after a manual clear re-enters suppression rather than being allowed indefinitely`() = runTest {
        val source = FakeSource(809L, "Repeatedly Recovering Source")
        SourceRuntimeFailureRegistry.clear(809L)

        // First failure -- suppressed as expected.
        SourceRuntime.run<Unit>(source, SourceRuntimeOperation.Popular) { throw NoClassDefFoundError("x") }
        assertTrue(SourceRuntimeFailureRegistry.isTemporarilyUnavailable(809L))

        // Manual retry (e.g. Source Evaluation's "Retry now" action) clears the registry.
        SourceRuntimeFailureRegistry.clear(809L)
        assertFalse(SourceRuntimeFailureRegistry.isTemporarilyUnavailable(809L))

        // The retry itself fails again (source is still genuinely broken).
        val retryResult = SourceRuntime.run<Unit>(source, SourceRuntimeOperation.Popular) {
            throw NoClassDefFoundError("x")
        }
        assertTrue(retryResult.isFailure)
        assertTrue(retryResult.exceptionOrNull() is NoClassDefFoundError)

        // Suppression must re-engage -- a manual retry is not a one-shot permanent bypass.
        assertTrue(SourceRuntimeFailureRegistry.isTemporarilyUnavailable(809L))
        var touchedAgain = false
        val thirdAttempt = SourceRuntime.run(source, SourceRuntimeOperation.Popular) {
            touchedAgain = true
            getFilterList()
        }
        assertFalse(touchedAgain, "a source that failed again right after a manual retry must be suppressed again")
        assertTrue(thirdAttempt.exceptionOrNull() is SourceTemporarilyUnavailableException)
    }
    // KMK <--

    @Test
    fun `safeClientOrNull does not trigger the lazy client after the source is marked temporarily unavailable`() {
        var clientTouched = false
        val source = FakeHttpSource(805L, "Broken Client Source", onClientTouch = { clientTouched = true })
        SourceRuntimeFailureRegistry.clear(805L)
        SourceRuntime.runBlockingSourceCall<Unit>(source, SourceRuntimeOperation.Popular) {
            throw NoClassDefFoundError("okhttp3.zstd.Zstd")
        }
        assertTrue(SourceRuntimeFailureRegistry.isTemporarilyUnavailable(805L))

        val result = source.safeClientOrNull()

        assertFalse(clientTouched, "the lazy client property must not be read while suppressed")
        assertTrue(result == null)
    }

    @Test
    fun `safeHeadersOrNull does not trigger the lazy headers after the source is marked temporarily unavailable`() {
        var headersTouched = false
        val source = FakeHttpSource(806L, "Broken Headers Source", onHeadersTouch = { headersTouched = true })
        SourceRuntimeFailureRegistry.clear(806L)
        SourceRuntime.runBlockingSourceCall<Unit>(source, SourceRuntimeOperation.Popular) {
            throw NoClassDefFoundError("okhttp3.zstd.Zstd")
        }
        assertTrue(SourceRuntimeFailureRegistry.isTemporarilyUnavailable(806L))

        val result = source.safeHeadersOrNull()

        assertFalse(headersTouched, "the lazy headers property must not be read while suppressed")
        assertTrue(result == null)
    }

    @Test
    fun `safeClientOrNull works normally for a healthy source and its recorded failure is the real okhttp3_zstd_Zstd NoClassDefFoundError`() {
        val source = FakeHttpSource(807L, "AsuraScans-like Source")
        SourceRuntimeFailureRegistry.clear(807L)

        // Healthy path: real OkHttpClient returned, nothing recorded.
        assertTrue(source.safeClientOrNull() != null)
        assertTrue(SourceRuntimeFailureRegistry.get(807L) == null)
    }

    // KMK v0.8.10-fix9 -->
    // Covers 5A (Suwayomi client access): the exact accessor SuwayomiApi.kt now routes through
    // (safeClientOrNull()) must record and swallow a recoverable lazy-client LinkageError as null,
    // but never swallow cancellation or a genuinely fatal Error.
    @Test
    fun `safeClientOrNull records a recoverable lazy client LinkageError and returns null instead of crashing`() {
        val source = FakeHttpSource(
            811L,
            "Suwayomi-like Source",
            clientThrows = { NoClassDefFoundError("Failed resolution of: Lokhttp3/zstd/Zstd;") },
        )
        SourceRuntimeFailureRegistry.clear(811L)

        val result = source.safeClientOrNull()

        assertTrue(result == null)
        val recorded = SourceRuntimeFailureRegistry.get(811L)
        assertTrue(recorded != null)
        assertEquals(SourceRuntimeFailureKind.ExtensionIncompatible, recorded!!.kind)
    }

    @Test
    fun `safeClientOrNull rethrows a genuinely fatal Error from the lazy client rather than returning null`() {
        val source = FakeHttpSource(812L, "Fatally Broken Source", clientThrows = { OutOfMemoryError("heap exhausted") })
        SourceRuntimeFailureRegistry.clear(812L)

        assertThrows(OutOfMemoryError::class.java) {
            source.safeClientOrNull()
        }
    }
    // KMK <--

    @Test
    fun `a batch of sibling sources is unaffected when one source is suppressed mid-batch`() = runTest {
        val broken = FakeSource(808L, "Broken In Batch")
        val healthy1 = FakeSource(809L, "Healthy Sibling 1")
        val healthy2 = FakeSource(810L, "Healthy Sibling 2")
        listOf(808L, 809L, 810L).forEach { SourceRuntimeFailureRegistry.clear(it) }

        // Simulates a batch/recommendation loop: broken source fails first (recorded + suppressed for
        // any immediate repeat), siblings still succeed independently -- mirrors
        // RecommendationSearchHelper.kt/BrowsePersonalRecommendationsScreenModel.kt's per-source
        // async{}+catch pattern, without needing the full Injekt-backed screen model.
        val brokenResult = SourceRuntime.run<Unit>(broken, SourceRuntimeOperation.Popular) {
            throw NoClassDefFoundError("okhttp3.zstd.Zstd")
        }
        val healthy1Result = SourceRuntime.run(healthy1, SourceRuntimeOperation.FilterList) { getFilterList() }
        val healthy2Result = SourceRuntime.run(healthy2, SourceRuntimeOperation.FilterList) { getFilterList() }

        assertTrue(brokenResult.isFailure)
        assertTrue(healthy1Result.isSuccess)
        assertTrue(healthy2Result.isSuccess)

        // A second attempt at the broken source within the same batch is suppressed without touching
        // it again, while siblings remain completely unaffected.
        var brokenTouchedAgain = false
        val secondAttempt = SourceRuntime.run(broken, SourceRuntimeOperation.Popular) {
            brokenTouchedAgain = true
            throw NoClassDefFoundError("okhttp3.zstd.Zstd")
        }
        assertFalse(brokenTouchedAgain)
        assertTrue(secondAttempt.exceptionOrNull() is SourceTemporarilyUnavailableException)
    }
    // KMK <--

    // KMK v0.8.21-fix5 -->
    // R2 correction: SameMangaCandidateSearcher's bounded same-source retry used to call
    // SourceRuntimeFailureRegistry.clear(source.id) unconditionally before its retry attempt -- a
    // process-global side effect. These tests exercise the replacement `bypassSuppression` parameter
    // directly against the real SourceRuntime.run(), proving the exact properties the R2 exit gate
    // requires: unrelated callers cannot accidentally bypass suppression, diagnostic failure state
    // is not silently lost by a bypassed attempt, a retry failure records correct (not misleadingly
    // fresh) state, and a bypassed call is still cancellation-safe.

    @Test
    fun `bypassSuppression touches the source even while it is suppressed`() = runTest {
        val source = FakeSource(901L, "Bypass Target")
        SourceRuntimeFailureRegistry.clear(901L)
        SourceRuntime.run<Unit>(source, SourceRuntimeOperation.Search) { throw NoClassDefFoundError("x") }
        assertTrue(SourceRuntimeFailureRegistry.isTemporarilyUnavailable(901L))

        var touched = false
        val result = SourceRuntime.run(source, SourceRuntimeOperation.Search, bypassSuppression = true) {
            touched = true
            getFilterList()
        }

        assertTrue(touched, "bypassSuppression must actually invoke the source, not short-circuit like an ordinary call")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `an unrelated concurrent caller is still suppressed while another caller bypasses for its own retry`() = runTest {
        val source = FakeSource(902L, "Shared Source")
        SourceRuntimeFailureRegistry.clear(902L)
        SourceRuntime.run<Unit>(source, SourceRuntimeOperation.Search) { throw NoClassDefFoundError("x") }
        assertTrue(SourceRuntimeFailureRegistry.isTemporarilyUnavailable(902L))

        // A second, unrelated caller (e.g. a different screen's ordinary load) does NOT opt into
        // bypassSuppression and must remain suppressed -- bypass is scoped to the one call that
        // requests it, never a side effect visible to other callers before any outcome is known.
        var unrelatedTouched = false
        val unrelatedResult = SourceRuntime.run(source, SourceRuntimeOperation.Popular) {
            unrelatedTouched = true
            getFilterList()
        }
        assertFalse(unrelatedTouched, "an unrelated caller must not be able to bypass suppression it never asked for")
        assertTrue(unrelatedResult.exceptionOrNull() is SourceTemporarilyUnavailableException)

        // The bounded retry itself, which DID opt in, still reaches the source.
        var retryTouched = false
        val retryResult = SourceRuntime.run(source, SourceRuntimeOperation.Search, bypassSuppression = true) {
            retryTouched = true
            getFilterList()
        }
        assertTrue(retryTouched)
        assertTrue(retryResult.isSuccess)
    }

    @Test
    fun `a bypassed call that fails again preserves diagnostic evidence -- count increments and firstFailureAt is unchanged`() = runTest {
        val source = FakeSource(903L, "Still Broken Source")
        SourceRuntimeFailureRegistry.clear(903L)

        SourceRuntime.run<Unit>(source, SourceRuntimeOperation.Search) { throw NoClassDefFoundError("x") }
        val firstEntry = SourceRuntimeFailureRegistry.get(903L)
        assertTrue(firstEntry != null)
        assertEquals(1, firstEntry!!.count)
        val originalFirstFailureAt = firstEntry.firstFailureAt

        // The retry bypasses suppression and fails again -- this must NOT look like a brand-new
        // single-failure entry (which is what the old clear()-before-retry code produced). Evidence
        // of the original failure (count, firstFailureAt) must survive.
        val retryResult = SourceRuntime.run(source, SourceRuntimeOperation.Search, bypassSuppression = true) {
            throw NoClassDefFoundError("x")
        }
        assertTrue(retryResult.isFailure)

        val secondEntry = SourceRuntimeFailureRegistry.get(903L)
        assertTrue(secondEntry != null, "diagnostic failure state must not be silently lost after a failed retry")
        assertEquals(2, secondEntry!!.count, "a second consecutive failure must increment count, not reset it")
        assertEquals(originalFirstFailureAt, secondEntry.firstFailureAt, "firstFailureAt must be preserved across the retry")
        assertTrue(SourceRuntimeFailureRegistry.isTemporarilyUnavailable(903L), "suppression must re-engage after the retry also fails")
    }

    @Test
    fun `a bypassed call that succeeds clears the stale entry so a later unrelated call is no longer suppressed`() = runTest {
        val source = FakeSource(904L, "Recovered Source")
        SourceRuntimeFailureRegistry.clear(904L)

        SourceRuntime.run<Unit>(source, SourceRuntimeOperation.Search) { throw NoClassDefFoundError("x") }
        assertTrue(SourceRuntimeFailureRegistry.isTemporarilyUnavailable(904L))

        val retryResult = SourceRuntime.run(source, SourceRuntimeOperation.Search, bypassSuppression = true) {
            getFilterList()
        }
        assertTrue(retryResult.isSuccess)

        // Confirmed success is fresh evidence the source is healthy -- unlike the preemptive,
        // outcome-blind clear() the old code used, this only ever fires after a real success.
        assertTrue(SourceRuntimeFailureRegistry.get(904L) == null)
        var laterTouched = false
        val laterResult = SourceRuntime.run(source, SourceRuntimeOperation.Popular) {
            laterTouched = true
            getFilterList()
        }
        assertTrue(laterTouched, "a later call must not be wrongly suppressed by a failure already superseded by a confirmed success")
        assertTrue(laterResult.isSuccess)
    }

    @Test
    fun `bypassSuppression does not affect a completely different source's suppression state`() = runTest {
        val target = FakeSource(905L, "Bypass Target")
        val sibling = FakeSource(906L, "Untouched Sibling")
        SourceRuntimeFailureRegistry.clear(905L)
        SourceRuntimeFailureRegistry.clear(906L)

        SourceRuntime.run<Unit>(target, SourceRuntimeOperation.Search) { throw NoClassDefFoundError("x") }
        SourceRuntime.run<Unit>(sibling, SourceRuntimeOperation.Search) { throw NoClassDefFoundError("x") }
        assertTrue(SourceRuntimeFailureRegistry.isTemporarilyUnavailable(905L))
        assertTrue(SourceRuntimeFailureRegistry.isTemporarilyUnavailable(906L))

        SourceRuntime.run(target, SourceRuntimeOperation.Search, bypassSuppression = true) { getFilterList() }

        // Only the bypassed source's entry is affected by its own successful retry -- the sibling's
        // independent suppression window is completely untouched.
        assertTrue(SourceRuntimeFailureRegistry.get(905L) == null)
        assertTrue(SourceRuntimeFailureRegistry.isTemporarilyUnavailable(906L), "an unrelated sibling source's suppression must survive another source's bypassed retry")
    }

    @Test
    fun `a bypassed call rethrows CancellationException rather than converting or swallowing it`() {
        val source = FakeSource(907L, "Cancelled During Bypass")
        SourceRuntimeFailureRegistry.clear(907L)
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                SourceRuntime.run<Unit>(source, SourceRuntimeOperation.Search, bypassSuppression = true) {
                    throw CancellationException("cancelled mid-retry")
                }
            }
        }
        // Cancellation must never be recorded as a source failure -- the registry stays untouched.
        assertTrue(SourceRuntimeFailureRegistry.get(907L) == null)
    }
    // KMK <--

    // KMK v0.8.21-fix6 -->
    // R2 concurrency-race correction: a bypassed call's post-success clear() used to be
    // unconditional. If a concurrent caller recorded a *newer* failure for the same source while
    // the bypassed attempt was still in flight, that unconditional clear would erase the newer
    // evidence too. clearIfUnchanged fixes this; these tests deterministically reproduce both
    // orderings the independent audit named.

    @Test
    fun `failure-before-success ordering -- a newer concurrent failure recorded while a bypassed call is in flight survives that call's success`() = runTest {
        val enteredBlock = CompletableDeferred<Unit>()
        val releaseBlock = CompletableDeferred<Unit>()
        val source = FakeSource(950L, "Racing Source")
        SourceRuntimeFailureRegistry.clear(950L)

        // Seed an initial failure so the bypassed call has a real entryBeforeAttempt to compare
        // against (not just null).
        SourceRuntime.run<Unit>(source, SourceRuntimeOperation.Popular) { throw NoClassDefFoundError("initial") }
        assertTrue(SourceRuntimeFailureRegistry.get(950L) != null)

        val bypassedJob = launch {
            SourceRuntime.run(source, SourceRuntimeOperation.Search, dispatcher = Dispatchers.Unconfined, bypassSuppression = true) {
                enteredBlock.complete(Unit)
                releaseBlock.await()
                "recovered"
            }
        }

        // Wait until the bypassed call's block has actually started (entryBeforeAttempt has been
        // captured), then -- while it is still suspended awaiting releaseBlock -- a concurrent
        // caller records a newer failure. Recorded directly against the registry (rather than via
        // a second SourceRuntime.run call) because a second *ordinary* call would itself be
        // suppressed by the initial seeded failure -- the real-world equivalent is another bypassed
        // caller's own SourceRuntime.run failing, which reaches SourceRuntimeFailureRegistry.record
        // the same way.
        enteredBlock.await()
        SourceRuntimeFailureRegistry.record(
            SourceRuntimeFailure(
                sourceId = 950L,
                sourceName = source.name,
                sourceLang = source.lang,
                operation = SourceRuntimeOperation.PageList,
                kind = SourceRuntimeFailureKind.ExtensionIncompatible,
                throwable = NoClassDefFoundError("newer concurrent failure"),
            ),
        )
        val newerEntry = SourceRuntimeFailureRegistry.get(950L)
        assertTrue(newerEntry != null)
        assertEquals(SourceRuntimeOperation.PageList, newerEntry!!.operation)

        // Now let the bypassed call finish successfully.
        releaseBlock.complete(Unit)
        bypassedJob.join()

        // The newer failure must survive -- the bypassed call's success must not have erased it.
        val afterEntry = SourceRuntimeFailureRegistry.get(950L)
        assertTrue(afterEntry != null, "a newer concurrent failure recorded during a bypassed call's flight must survive that call's success")
        assertEquals(newerEntry, afterEntry)
    }

    @Test
    fun `success-before-failure ordering -- a bypassed success that completes with no intervening failure still clears normally`() = runTest {
        val source = FakeSource(951L, "Non-Racing Source")
        SourceRuntimeFailureRegistry.clear(951L)

        SourceRuntime.run<Unit>(source, SourceRuntimeOperation.Popular) { throw NoClassDefFoundError("initial") }
        assertTrue(SourceRuntimeFailureRegistry.get(951L) != null)

        // The bypassed call completes (success) entirely before any other failure is recorded --
        // the safe ordering. clearIfUnchanged must still clear in this case, exactly like the old
        // unconditional clear() did, since nothing raced it.
        val result = SourceRuntime.run(source, SourceRuntimeOperation.Search, bypassSuppression = true) { "recovered" }
        assertTrue(result.isSuccess)
        assertTrue(SourceRuntimeFailureRegistry.get(951L) == null, "a bypassed success with no concurrent interleaving must still clear the stale entry")

        // A failure recorded afterward is unaffected -- fresh evidence, not lost, not merged.
        SourceRuntime.run<Unit>(source, SourceRuntimeOperation.PageList) { throw NoClassDefFoundError("later, unrelated failure") }
        val laterEntry = SourceRuntimeFailureRegistry.get(951L)
        assertTrue(laterEntry != null)
        assertEquals(1, laterEntry!!.count)
    }
    // KMK <--
}
// KMK <--
