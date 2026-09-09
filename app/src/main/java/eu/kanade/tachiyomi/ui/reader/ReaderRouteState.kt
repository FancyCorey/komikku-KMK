package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.reader.loader.ArchiveReaderDegradation
import eu.kanade.tachiyomi.ui.reader.loader.ChapterLoader
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import exh.metadata.metadata.RaisedSearchMetadata
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

/** All mutable resources that must move together when the reader changes manga routes. */
internal class ReaderRouteState(
    val generation: Long,
    val manga: Manga,
    val metadata: RaisedSearchMetadata?,
    val mergedManga: Map<Long, Manga>?,
    val unfilteredChapterList: List<Chapter>,
    val chapterList: List<ReaderChapter>,
    val loader: ChapterLoader,
    val incognitoMode: Boolean,
    val selectedChapterId: Long,
    val dateRelativeTime: Boolean,
    val autoScrollFrequency: Float,
    val archiveDegradations: List<ArchiveReaderDegradation>,
    private val restoreQueuedDownload: (Download) -> Unit,
) {
    private val releaseLock = Any()

    @Volatile
    private var released = false

    @Volatile
    var viewerChapters: ViewerChapters? = null

    @Volatile
    var pendingDownload: Download? = null

    fun isReleased(): Boolean = released

    fun release() {
        val viewer: ViewerChapters?
        val download: Download?
        synchronized(releaseLock) {
            if (released) return
            released = true
            viewer = viewerChapters
            viewerChapters = null
            download = pendingDownload
            pendingDownload = null
        }

        viewer?.unref()
        chapterList.forEach { chapter ->
            chapter.pageLoader?.recycle()
            chapter.pageLoader = null
            chapter.state = ReaderChapter.State.Wait
        }
        download?.let(restoreQueuedDownload)
    }
}
