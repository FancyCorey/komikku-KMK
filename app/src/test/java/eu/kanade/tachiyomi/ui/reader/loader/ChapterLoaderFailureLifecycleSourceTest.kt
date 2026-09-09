package eu.kanade.tachiyomi.ui.reader.loader

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ChapterLoaderFailureLifecycleSourceTest {

    private val source = File(
        "src/main/java/eu/kanade/tachiyomi/ui/reader/loader/ChapterLoader.kt",
    ).readText()

    @Test
    fun `failure cleanup recycles stale loaders without replacing a newer chapter owner`() {
        val loadMethod = source
            .substringAfter("suspend fun loadChapter")
            .substringBefore("private fun chapterIsReady")
        val cancellationCatch = loadMethod.indexOf("catch (e: CancellationException)")
        val ordinaryCatch = loadMethod.indexOf("catch (e: Throwable)")
        val firstRecycle = loadMethod.indexOf("recycleFailedPageLoader(chapter, loader)", cancellationCatch)
        val secondRecycle = loadMethod.indexOf("recycleFailedPageLoader(chapter, loader)", ordinaryCatch)
        val errorAssignment = loadMethod.indexOf("chapter.state = ReaderChapter.State.Error(e)", ordinaryCatch)

        assertTrue(cancellationCatch >= 0)
        assertTrue(ordinaryCatch > cancellationCatch)
        assertTrue(firstRecycle in (cancellationCatch + 1)..<ordinaryCatch)
        assertTrue(secondRecycle > ordinaryCatch)
        assertTrue(errorAssignment > secondRecycle)
        assertTrue(loadMethod.contains("chapter.state = ReaderChapter.State.Wait"))
        assertTrue(source.contains("val ownsChapter = chapter.pageLoader === failedLoader"))
        assertTrue(source.contains("failedLoader.recycle()"))
        assertTrue(source.contains("if (ownsChapter)"))
        assertTrue(source.contains("return ownsChapter"))
    }
}
