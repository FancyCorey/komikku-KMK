package eu.kanade.tachiyomi.ui.reader

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ReaderRouteOwnershipAdoptionSourceTest {

    private val source = String(
        Files.readAllBytes(Path.of("src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt")),
        Charsets.UTF_8,
    )

    @Test
    fun `manga-bound reader resources are owned by one serialized route`() {
        assertTrue(source.contains("private var activeRoute: ReaderRouteState?"))
        assertTrue(source.contains("routeOwner.replace("))
        assertTrue(source.contains("buildReaderRoute("))
        assertTrue(source.contains("commitReaderRoute("))
        assertFalse(source.contains("unfilteredChapterList by lazy"))
        assertFalse(source.contains("chapterList by lazy"))
        assertFalse(source.contains("private var loader: ChapterLoader?"))
    }

    @Test
    fun `schedule gate precedes initial page preparation and clear closes route owner`() {
        val scheduleGate = source.indexOf("if (!isChapterNavigationBlockedBySchedule())")
        val pagePreparation = source.indexOf("prepareViewerChapters(route, selectedReaderChapter, page)")
        assertTrue(scheduleGate in 0..<pagePreparation)
        assertTrue(source.contains("routeOwner.close()"))
        assertTrue(source.contains("route?.release()"))
    }

    @Test
    fun `page side effects use one captured route and reject stale callbacks`() {
        assertTrue(source.contains("if (selectedChapter !in route.chapterList || route.isReleased()) return"))
        assertTrue(source.contains("updateChapterProgress(route, selectedChapter"))
        assertTrue(source.contains("downloadNextChapters(route)"))
        assertTrue(source.contains("updateHistory(route)"))
        assertTrue(source.contains("if (activeRoute !== route || route.isReleased()) return"))
    }

    @Test
    fun `alternate source navigation reuses the serialized route owner and exact merged owner`() {
        assertTrue(source.contains("routeSwitcher = AlternateSourceReaderRouteSwitcher(::switchReaderRoute)"))
        assertTrue(source.contains("replaceReaderRoute(route.mangaId, route.chapterId, route.pageIndex)"))
        assertTrue(source.contains("route.mergedManga?.get(chapter.chapter.manga_id)"))
        assertTrue(source.contains("manga.source != record.source || manga.url != record.url"))
        assertTrue(source.contains("alternateSourceReaderCoordinator.automaticReturn(completedChapterUrl)"))
        assertFalse(source.contains("alternateSourceBridgeRepository"))
    }
}
