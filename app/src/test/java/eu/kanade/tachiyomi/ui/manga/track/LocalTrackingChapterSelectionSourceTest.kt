package eu.kanade.tachiyomi.ui.manga.track

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** Guards source-specific local progress as the primary chapter-opening contract. */
class LocalTrackingChapterSelectionSourceTest {

    @Test
    fun `local chapter action resolves source progress before generic history fallback`() {
        val source = File(
            "src/main/java/eu/kanade/tachiyomi/ui/manga/track/TrackInfoDialog.kt",
        ).readText()
        val sourceProgressLookup = source.indexOf("getSourceProgress(it, manga.source, manga.url)")
        val sourceChapterLookup = source.indexOf("getChapterByUrlAndMangaId.await(it, manga.id)")
        val historyFallback = source.indexOf("LocalTrackingHistoryProgressPolicy.resolve(getHistory.await(mangaId))")

        assertTrue(sourceProgressLookup >= 0, "local chapter opening must read source-specific progress")
        assertTrue(sourceChapterLookup > sourceProgressLookup, "source progress must resolve its chapter URL")
        assertTrue(historyFallback > sourceChapterLookup, "generic history must remain only as fallback")
    }
}
