package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.source.SourceRuntimeFailure
import eu.kanade.tachiyomi.source.SourceRuntimeFailureKind
import eu.kanade.tachiyomi.source.SourceRuntimeFailureRegistry
import eu.kanade.tachiyomi.source.SourceRuntimeOperation
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import exh.util.FakePreferenceStore
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

class HttpPageLoaderRetryLifecycleTest {

    @Test
    fun `explicit retry clears only the affected source suppression`() {
        val source = mockk<HttpSource>(relaxed = true)
        val sibling = mockk<HttpSource>(relaxed = true)
        every { source.id } returns 901L
        every { sibling.id } returns 902L
        SourceRuntimeFailureRegistry.clearAll()
        SourceRuntimeFailureRegistry.record(failureFor(source.id))
        SourceRuntimeFailureRegistry.record(failureFor(sibling.id))

        val chapter = ReaderChapter(Chapter.create().copy(id = 903L, mangaId = 904L))
        val preferenceStore = FakePreferenceStore()
        preferenceStore.getBoolean("eh_reader_instant_retry", true).set(false)
        val loader = HttpPageLoader(
            chapter = chapter,
            source = source,
            chapterCache = mockk<ChapterCache>(relaxed = true),
            readerPreferences = ReaderPreferences(preferenceStore),
            sourcePreferences = SourcePreferences(FakePreferenceStore()),
        )
        val page = ReaderPage(index = 0).apply {
            status = Page.State.Error(IllegalStateException("synthetic-page-failure"))
        }
        page.chapter = chapter
        chapter.state = ReaderChapter.State.Loaded(listOf(page))

        loader.retryPage(page)

        assertFalse(SourceRuntimeFailureRegistry.isTemporarilyUnavailable(source.id))
        assertTrue(SourceRuntimeFailureRegistry.isTemporarilyUnavailable(sibling.id))
        assertFalse(page.status is Page.State.Error)

        loader.recycle()
        SourceRuntimeFailureRegistry.clearAll()
    }

    @Test
    fun `individual retry fans out to failed siblings but preserves nonfailed and unrelated pages`() {
        val source = mockk<HttpSource>(relaxed = true)
        every { source.id } returns 905L
        val chapter = ReaderChapter(Chapter.create().copy(id = 906L, mangaId = 907L))
        val tappedFailure = ReaderPage(index = 0).apply {
            status = Page.State.Error(IllegalStateException("tapped-failure"))
            this.chapter = chapter
        }
        val siblingFailure = ReaderPage(index = 1).apply {
            status = Page.State.Error(IllegalStateException("sibling-failure"))
            this.chapter = chapter
        }
        val ready = ReaderPage(index = 2).apply {
            status = Page.State.Ready
            this.chapter = chapter
        }
        val loading = ReaderPage(index = 3).apply {
            status = Page.State.LoadPage
            this.chapter = chapter
        }
        val queued = ReaderPage(index = 4).apply {
            status = Page.State.Queue
            this.chapter = chapter
        }
        chapter.state = ReaderChapter.State.Loaded(listOf(tappedFailure, siblingFailure, ready, loading, queued))
        val otherChapter = ReaderChapter(Chapter.create().copy(id = 908L, mangaId = 909L))
        val unrelatedFailure = ReaderPage(index = 0).apply {
            status = Page.State.Error(IllegalStateException("unrelated-failure"))
            this.chapter = otherChapter
        }
        otherChapter.state = ReaderChapter.State.Loaded(listOf(unrelatedFailure))

        val loader = HttpPageLoader(
            chapter = chapter,
            source = source,
            chapterCache = mockk<ChapterCache>(relaxed = true),
            readerPreferences = ReaderPreferences(FakePreferenceStore()),
            sourcePreferences = SourcePreferences(FakePreferenceStore()),
        )

        loader.retryPage(tappedFailure)

        assertTrue(tappedFailure.status == Page.State.Queue)
        assertTrue(siblingFailure.status == Page.State.Queue)
        assertTrue(ready.status == Page.State.Ready)
        assertTrue(loading.status == Page.State.LoadPage)
        assertTrue(queued.status == Page.State.Queue)
        assertTrue(unrelatedFailure.status is Page.State.Error)

        loader.recycle()
        SourceRuntimeFailureRegistry.clearAll()
    }

    @Test
    fun `retry after recycle cannot mutate page state or image identity`() {
        val loader = HttpPageLoader(
            chapter = ReaderChapter(Chapter.create().copy(id = 901L, mangaId = 902L)),
            source = mockk<HttpSource>(relaxed = true),
            chapterCache = mockk<ChapterCache>(relaxed = true),
            readerPreferences = ReaderPreferences(FakePreferenceStore()),
            sourcePreferences = SourcePreferences(FakePreferenceStore()),
        )
        val failure = IllegalStateException("synthetic-page-failure")
        val errorState = Page.State.Error(failure)
        val page = ReaderPage(index = 0, imageUrl = "https://invalid.example/synthetic-page").apply {
            status = errorState
        }

        loader.recycle()
        loader.retryPage(page)

        assertTrue(loader.isRecycled)
        assertSame(errorState, page.status)
        assertSame(failure, (page.status as Page.State.Error).error)
        assertTrue(page.imageUrl == "https://invalid.example/synthetic-page")
    }

    private fun failureFor(sourceId: Long) = SourceRuntimeFailure(
        sourceId = sourceId,
        sourceName = "Synthetic source $sourceId",
        sourceLang = "en",
        operation = SourceRuntimeOperation.Image,
        kind = SourceRuntimeFailureKind.Network,
        throwable = IllegalStateException("synthetic failure"),
    )
}
