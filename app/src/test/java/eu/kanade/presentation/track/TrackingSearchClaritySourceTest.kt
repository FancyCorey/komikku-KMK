package eu.kanade.presentation.track

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class TrackingSearchClaritySourceTest {

    @Test
    fun `tracked and untracked entry actions remain visibly distinct`() {
        val source = read("src/main/java/eu/kanade/presentation/track/TrackInfoDialogHome.kt")

        assertTrue(source.contains("MR.strings.track_find_different_match"))
        assertTrue(source.contains("MR.strings.add_tracking"))
        assertTrue(source.contains("onClickLabel = changeMatchLabel"))
        assertTrue(source.contains("onLongClickLabel = copyTitleLabel"))
        assertTrue(source.contains("role = Role.Button"))
        assertTrue(source.contains(".heightIn(min = 48.dp)"))
    }

    @Test
    fun `tracker result search names its scope and actionable icons`() {
        val source = read("src/main/java/eu/kanade/presentation/track/TrackerSearch.kt")

        assertTrue(source.contains("MR.strings.track_search_hint, trackerName"))
        assertTrue(source.contains("MR.strings.action_bar_up_description"))
        assertTrue(source.contains("MR.strings.action_clear_search"))
        assertTrue(source.contains("tracker-search-${'$'}{it.tracker_id}-${'$'}{it.remote_id}-${'$'}{it.tracking_url}"))
        assertTrue(!source.contains("tracker-search-${'$'}{it.hashCode()}"))
    }

    @Test
    fun `screen passes the selected tracker name to the renderer`() {
        val source = read("src/main/java/eu/kanade/tachiyomi/ui/manga/track/TrackInfoDialog.kt")

        assertTrue(source.contains("trackerName = screenModel.trackerName"))
        assertTrue(source.contains("val trackerName = tracker.name"))
    }

    @Test
    fun `tracking confirmation closes only after registration succeeds`() {
        val source = read("src/main/java/eu/kanade/tachiyomi/ui/manga/track/TrackInfoDialog.kt")

        assertTrue(source.contains("if (screenModel.registerTracking(selected))"))
        assertTrue(source.contains("navigator.pop()"))
        assertTrue(source.contains("context.toast(MR.strings.unknown_error)"))
        assertTrue(source.contains("suspend fun registerTracking(item: TrackSearch): Boolean"))
    }

    @Test
    fun `clarity copy is resource backed`() {
        val resources = read("../i18n/src/commonMain/moko-resources/base/strings.xml")

        assertTrue(resources.contains("name=\"track_find_different_match\""))
        assertTrue(resources.contains("name=\"track_search_hint\""))
        assertTrue(resources.contains("name=\"action_clear_search\""))
        assertTrue(resources.contains("name=\"action_copy_tracking_title\""))
    }

    private fun read(relativePath: String): String = File(relativePath).also {
        assertTrue(it.isFile, "expected source file at ${it.path}")
    }.readText()
}
