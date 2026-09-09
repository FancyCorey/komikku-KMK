package exh.validation.route

import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.reader.ChapterCompletionPromptReducer
import eu.kanade.tachiyomi.ui.reader.ChapterCompletionPromptState
import eu.kanade.tachiyomi.ui.reader.LatestChapterCompletionPolicy
import eu.kanade.tachiyomi.ui.reader.loader.ChapterLoader
import eu.kanade.tachiyomi.ui.reader.loader.PageLoader
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.schedule.ReaderScheduleEntitlement
import eu.kanade.tachiyomi.ui.reader.schedule.ReaderScheduleResult
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import exh.util.FakePreferenceStore
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import java.io.ByteArrayInputStream

class ReaderHostRouteFixtureTest {

    @Test
    fun `ordered in-memory pages travel from wait through loading to loaded`() {
        HostRouteTestEnvironment().use { environment ->
            val chapter = chapter(101L)
            val pages = listOf(page(0, "page-zero"), page(1, "page-one"), page(2, "page-two"))
            val pagesGate = CompletableDeferred<List<ReaderPage>>()
            val pageLoader = ScriptedPageLoader { pagesGate.await() }
            val route = chapterLoader(environment, factory = { pageLoader })
            var result: Result<Unit>? = null

            environment.scope.launch { result = runCatching { route.loadChapter(chapter) } }
            environment.runCurrent()

            assertSame(ReaderChapter.State.Loading, chapter.state)
            assertSame(pageLoader, chapter.pageLoader)

            pagesGate.complete(pages)
            environment.advanceUntilIdle()

            assertTrue(checkNotNull(result).isSuccess)
            val loaded = assertInstanceOf(ReaderChapter.State.Loaded::class.java, chapter.state)
            assertEquals(listOf(0, 1, 2), loaded.pages.map { it.index })
            assertTrue(loaded.pages.all { it.chapter === chapter })
            assertEquals("page-one", loaded.pages[1].stream?.invoke()?.readBytes()?.decodeToString())
            assertEquals(0, pageLoader.recycleCount)

            chapter.ref()
            chapter.unref()
            assertEquals(1, pageLoader.recycleCount)
            assertSame(ReaderChapter.State.Wait, chapter.state)
        }
    }

    @Test
    fun `ordinary typed failure is terminal and a public retry can recover`() {
        HostRouteTestEnvironment().use { environment ->
            val chapter = chapter(102L)
            val failure = SyntheticReaderFailure("synthetic-reader-failure")
            val failedLoader = ScriptedPageLoader { throw failure }
            val recoveredLoader = ScriptedPageLoader { listOf(page(0, "recovered")) }
            val loaders = ArrayDeque(listOf(failedLoader, recoveredLoader))
            val route = chapterLoader(environment, factory = { loaders.removeFirst() })

            val thrown = assertThrows(SyntheticReaderFailure::class.java) {
                environment.execute { route.loadChapter(chapter) }
            }

            assertSame(failure, thrown)
            assertSame(failure, assertInstanceOf(ReaderChapter.State.Error::class.java, chapter.state).error)
            assertNull(chapter.pageLoader)
            assertEquals(1, failedLoader.recycleCount)

            environment.execute { route.loadChapter(chapter) }

            assertInstanceOf(ReaderChapter.State.Loaded::class.java, chapter.state)
            assertSame(recoveredLoader, chapter.pageLoader)
        }
    }

    @Test
    fun `cancellation returns the owned chapter to wait and never becomes an error`() {
        HostRouteTestEnvironment().use { environment ->
            val chapter = chapter(103L)
            val pageLoader = ScriptedPageLoader { awaitCancellation() }
            val route = chapterLoader(environment, factory = { pageLoader })
            var result: Result<Unit>? = null

            val loadJob = environment.scope.launch {
                result = runCatching { route.loadChapter(chapter) }
            }
            environment.runCurrent()
            assertSame(ReaderChapter.State.Loading, chapter.state)

            loadJob.cancel()
            environment.advanceUntilIdle()

            assertTrue(checkNotNull(result).exceptionOrNull() is CancellationException)
            assertSame(ReaderChapter.State.Wait, chapter.state)
            assertNull(chapter.pageLoader)
            assertEquals(1, pageLoader.recycleCount)
        }
    }

    @Test
    fun `stale cancelled load recycles itself without replacing a newer loaded state`() {
        HostRouteTestEnvironment().use { environment ->
            val chapter = chapter(104L)
            val staleLoader = ScriptedPageLoader { awaitCancellation() }
            val currentLoader = ScriptedPageLoader { listOf(page(0, "current")) }
            val loaders = ArrayDeque(listOf(staleLoader, currentLoader))
            val route = chapterLoader(environment, factory = { loaders.removeFirst() })
            var staleResult: Result<Unit>? = null

            val staleJob = environment.scope.launch {
                staleResult = runCatching { route.loadChapter(chapter) }
            }
            environment.runCurrent()
            assertSame(staleLoader, chapter.pageLoader)

            environment.execute { route.loadChapter(chapter) }
            assertSame(currentLoader, chapter.pageLoader)
            assertInstanceOf(ReaderChapter.State.Loaded::class.java, chapter.state)

            staleJob.cancel()
            environment.advanceUntilIdle()

            assertTrue(checkNotNull(staleResult).exceptionOrNull() is CancellationException)
            assertSame(currentLoader, chapter.pageLoader)
            assertInstanceOf(ReaderChapter.State.Loaded::class.java, chapter.state)
            assertEquals(1, staleLoader.recycleCount)
            assertEquals(0, currentLoader.recycleCount)
        }
    }

    @Test
    fun `empty page list is a typed terminal error and leaves no loader attached`() {
        HostRouteTestEnvironment().use { environment ->
            val chapter = chapter(105L)
            val pageLoader = ScriptedPageLoader { emptyList() }
            val route = chapterLoader(environment, factory = { pageLoader })

            val thrown = assertThrows(EmptySyntheticPageList::class.java) {
                environment.execute { route.loadChapter(chapter) }
            }

            assertSame(thrown, assertInstanceOf(ReaderChapter.State.Error::class.java, chapter.state).error)
            assertNull(chapter.pageLoader)
            assertEquals(1, pageLoader.recycleCount)
        }
    }

    @Test
    fun `viewer window switch retains shared chapter and reader leave releases the remaining window`() {
        val first = loadedChapter(106L)
        val shared = loadedChapter(107L)
        val last = loadedChapter(108L)
        val firstWindow = ViewerChapters(first.chapter, null, shared.chapter)
        val secondWindow = ViewerChapters(shared.chapter, first.chapter, last.chapter)

        firstWindow.ref()
        secondWindow.ref()
        firstWindow.unref()

        assertEquals(0, first.loader.recycleCount)
        assertEquals(0, shared.loader.recycleCount)
        assertEquals(0, last.loader.recycleCount)

        secondWindow.unref()

        assertEquals(1, first.loader.recycleCount)
        assertEquals(1, shared.loader.recycleCount)
        assertEquals(1, last.loader.recycleCount)
        assertSame(ReaderChapter.State.Wait, first.chapter.state)
        assertSame(ReaderChapter.State.Wait, shared.chapter.state)
        assertSame(ReaderChapter.State.Wait, last.chapter.state)
    }

    @Test
    fun `already loaded chapter ignores duplicate load without constructing another loader`() {
        HostRouteTestEnvironment().use { environment ->
            val chapter = chapter(109L)
            val pageLoader = ScriptedPageLoader { listOf(page(0, "only")) }
            var factoryCalls = 0
            val route = chapterLoader(environment) {
                factoryCalls += 1
                pageLoader
            }

            environment.execute { route.loadChapter(chapter) }
            environment.execute { route.loadChapter(chapter) }

            assertEquals(1, factoryCalls)
            assertSame(pageLoader, chapter.pageLoader)
            assertInstanceOf(ReaderChapter.State.Loaded::class.java, chapter.state)
        }
    }

    @Test
    fun `fatal loader failure propagates and remains an explicit terminal error`() {
        HostRouteTestEnvironment().use { environment ->
            val chapter = chapter(110L)
            val fatal = SyntheticFatalReaderError()
            val pageLoader = ScriptedPageLoader { throw fatal }
            val route = chapterLoader(environment, factory = { pageLoader })

            val thrown = assertThrows(SyntheticFatalReaderError::class.java) {
                environment.execute { route.loadChapter(chapter) }
            }

            assertSame(fatal, thrown)
            assertSame(fatal, assertInstanceOf(ReaderChapter.State.Error::class.java, chapter.state).error)
            assertEquals(1, pageLoader.recycleCount)
        }
    }

    @Test
    fun `completion and schedule decisions derive from the same current chapter`() {
        val current = loadedChapter(111L, mangaId = 401L, pageCount = 3).chapter
        val window = ViewerChapters(current, prevChapter = null, nextChapter = null)
        val reachedPage = checkNotNull(window.currChapter.pages).last()

        val completed = LatestChapterCompletionPolicy.isGenuineLatestChapterCompletion(
            pageIndex = reachedPage.index,
            lastPageIndex = window.currChapter.pages?.lastIndex,
            hasExtraPage = false,
            hasNextChapter = window.nextChapter != null,
            isErrorPage = false,
        )
        val prompt = if (completed) {
            ChapterCompletionPromptReducer.onGenuineCompletion(
                ChapterCompletionPromptState.None,
                checkNotNull(window.currChapter.chapter.manga_id),
            )
        } else {
            ChapterCompletionPromptState.None
        }
        val scheduleChapterKey = checkNotNull(window.currChapter.chapter.id).toString()
        val entitlement = ReaderScheduleEntitlement.next(
            ReaderScheduleEntitlement.NotStarted,
            ReaderScheduleResult.ALLOWED,
            graceConsumed = false,
        )

        assertTrue(completed)
        assertEquals(ChapterCompletionPromptState.PendingOnExit(401L), prompt)
        assertEquals("111", scheduleChapterKey)
        assertFalse(entitlement.blocksReading)
    }

    private fun chapterLoader(
        environment: HostRouteTestEnvironment,
        factory: (ReaderChapter) -> PageLoader,
    ) = ChapterLoader(
        context = mockk(relaxed = true),
        downloadManager = mockk<DownloadManager>(relaxed = true),
        downloadProvider = mockk<DownloadProvider>(relaxed = true),
        manga = Manga.create().copy(id = 401L, source = 501L, ogTitle = "Synthetic Reader Manga"),
        source = mockk<Source>(relaxed = true),
        sourceManager = mockk<SourceManager>(relaxed = true),
        readerPrefs = ReaderPreferences(FakePreferenceStore()),
        mergedReferences = emptyList(),
        mergedManga = null,
        loadDispatcher = environment.dispatcher,
        pageLoaderFactory = factory,
        emptyPageExceptionFactory = { EmptySyntheticPageList() },
    )

    private fun chapter(id: Long, mangaId: Long = 401L) = ReaderChapter(
        ChapterImpl().apply {
            this.id = id
            manga_id = mangaId
            url = "/synthetic/chapter/$id"
            name = "Synthetic Chapter $id"
            chapter_number = id.toFloat()
        },
    )

    private fun page(index: Int, payload: String) = ReaderPage(
        index = index,
        url = "/synthetic/page/$index",
        stream = { ByteArrayInputStream(payload.encodeToByteArray()) },
    )

    private fun loadedChapter(
        id: Long,
        mangaId: Long = 401L,
        pageCount: Int = 1,
    ): LoadedChapter {
        val chapter = chapter(id, mangaId)
        val loader = ScriptedPageLoader { error("already-loaded") }
        val pages = List(pageCount) { index -> page(index, "payload-$index").also { it.chapter = chapter } }
        chapter.pageLoader = loader
        chapter.state = ReaderChapter.State.Loaded(pages)
        return LoadedChapter(chapter, loader)
    }

    private fun <T> HostRouteTestEnvironment.execute(block: suspend () -> T): T {
        var result: Result<T>? = null
        scope.launch { result = runCatching { block() } }
        advanceUntilIdle()
        return checkNotNull(result).getOrThrow()
    }

    private data class LoadedChapter(
        val chapter: ReaderChapter,
        val loader: ScriptedPageLoader,
    )

    private class ScriptedPageLoader(
        private val pages: suspend () -> List<ReaderPage>,
    ) : PageLoader() {
        override var isLocal = false
        var recycleCount = 0
            private set

        override suspend fun getPages() = pages()

        override fun recycle() {
            recycleCount += 1
            super.recycle()
        }
    }

    private class SyntheticReaderFailure(message: String) : Exception(message)

    private class EmptySyntheticPageList : Exception("synthetic-empty-page-list")

    private class SyntheticFatalReaderError : AssertionError("synthetic-fatal-reader-error")
}
