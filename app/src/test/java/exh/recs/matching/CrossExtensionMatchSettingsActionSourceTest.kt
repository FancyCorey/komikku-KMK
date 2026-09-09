package exh.recs.matching

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class CrossExtensionMatchSettingsActionSourceTest {
    @Test
    fun `other versions exposes matching settings without restoring rejection action`() {
        val source = File("src/main/java/exh/recs/matching/CrossExtensionMatchScreen.kt").readText()

        assertTrue(source.contains("AppBarActions"))
        assertTrue(source.contains("RecommendationDiagnosticsSettingsScreen"))
        assertTrue(source.contains("anchor = \"same_manga_preselect\""))
        assertTrue(source.contains("Icons.Outlined.Settings"))
        assertTrue(
            File("../i18n-kmk/src/commonMain/moko-resources/base/strings.xml").readText()
                .contains("<string name=\"rec_match_search_action\">Search installed sources</string>"),
        )
        assertTrue(!source.contains("onReject ="))
        assertTrue(!source.contains("requestRejectCandidate"))
    }
}
