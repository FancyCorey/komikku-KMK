package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.data.database.models.toDomainChapter
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.reader.loader.ChapterLoader
import eu.kanade.tachiyomi.ui.reader.loader.PageLoader
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

class ReaderRouteStateTest {

    @Test
    fun `release is idempotent and recycles referenced and preloaded chapter resources`() {
        val chapters = (1L..4L).map { id ->
            ReaderChapter(Chapter.create().copy(id = id, mangaId = 10L)).also {
                it.pageLoader = RecordingPageLoader()
                it.state = ReaderChapter.State.Loading
            }
        }
        val viewer = ViewerChapters(chapters[1], chapters[0], chapters[2]).also(ViewerChapters::ref)
        val restored = mutableListOf<Download>()
        val pendingDownload = mockk<Download>()
        val route = route(chapters, restored).also {
            it.viewerChapters = viewer
            it.pendingDownload = pendingDownload
        }

        route.release()
        route.release()

        assertTrue(route.isReleased())
        assertNull(route.viewerChapters)
        assertNull(route.pendingDownload)
        assertEquals(listOf(pendingDownload), restored)
        chapters.forEach {
            assertNull(it.pageLoader)
            assertEquals(ReaderChapter.State.Wait, it.state)
        }
    }

    private fun route(
        chapters: List<ReaderChapter>,
        restored: MutableList<Download>,
    ) = ReaderRouteState(
        generation = 1L,
        manga = Manga.create().copy(id = 10L, source = 20L),
        metadata = null,
        mergedManga = null,
        unfilteredChapterList = chapters.map { it.chapter.toDomainChapter()!! },
        chapterList = chapters,
        loader = mockk<ChapterLoader>(),
        incognitoMode = false,
        selectedChapterId = chapters[1].chapter.id!!,
        dateRelativeTime = true,
        autoScrollFrequency = -1f,
        archiveDegradations = emptyList(),
        restoreQueuedDownload = restored::add,
    )

    private class RecordingPageLoader : PageLoader() {
        override var isLocal = false

        override suspend fun getPages(): List<ReaderPage> = emptyList()
    }
}
