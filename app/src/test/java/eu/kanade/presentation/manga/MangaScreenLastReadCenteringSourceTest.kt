package eu.kanade.presentation.manga

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class MangaScreenLastReadCenteringSourceTest {

    private val source by lazy {
        File("src/main/java/eu/kanade/presentation/manga/MangaScreen.kt").readText()
    }

    @Test
    fun `shared helper waits for a measured frame before centering`() {
        val helper = source.substringAfter("private suspend fun LazyListState.animateToLastReadChapter(")
            .substringBefore("\n@Composable")

        assertTrue(helper.contains("withFrameNanos { }"))
        assertTrue(helper.indexOf("withFrameNanos { }") < helper.indexOf("val layoutInfo = layoutInfo"))
        assertTrue(helper.contains("LastReadChapterTargetPolicy.centeringScrollDelta("))
        assertTrue(helper.contains("?: return false"))
        assertTrue(helper.contains("return true"))
    }

    @Test
    fun `portrait and tablet callers surface positioning failure`() {
        assertEquals(2, Regex("val positioned = chapterListState\\.animateToLastReadChapter\\(").findAll(source).count())
        assertEquals(
            2,
            Regex("if \\(!positioned\\) snackbarHostState\\.showSnackbar\\(jumpUnavailableMessage\\)")
                .findAll(source).count(),
        )
    }
}
