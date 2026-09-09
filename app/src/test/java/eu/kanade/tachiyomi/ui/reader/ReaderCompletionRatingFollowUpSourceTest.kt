package eu.kanade.tachiyomi.ui.reader

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** Keeps completion-rating navigation alive long enough to launch the other-version follow-up. */
class ReaderCompletionRatingFollowUpSourceTest {

    @Test
    fun `other-version activity launches before completion dialog dismissal`() {
        val source = File("src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt").readText()
        val handler = source.substringAfter("                is ReaderViewModel.Dialog.ChapterCompletionRatingGroupOffer ->")
            .substringBefore("// KMK <--")
        val launch = handler.indexOf("startActivity(")
        val dismiss = handler.indexOf("onDismissRequest()")

        assertTrue(launch >= 0, "completion rating follow-up must launch the existing MainActivity route")
        assertTrue(dismiss > launch, "dialog dismissal must happen after the follow-up launch")
        assertTrue(handler.contains("OPEN_CROSS_EXTENSION_MATCH_FOR_RATING"))
    }

    @Test
    fun `reader uses one configurable follow-up path for all rating values`() {
        val source = File("src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt").readText()

        assertTrue(source.contains("chapterCompletionRatingOtherVersionsPromptEnabled"))
        assertTrue(source.contains("MangaRating.LOVE"))
        assertTrue(source.contains("MangaRating.LIKE"))
        assertTrue(source.contains("MangaRating.DISLIKE"))
        assertTrue(source.contains("MangaRating.NOT_INTERESTED"))
        assertTrue(source.contains("ChapterCompletionRatingGroupOffer"))
    }
}
