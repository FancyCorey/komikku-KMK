package exh.i18n

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class KmkRtlScalingInternationalSourceTest {

    private val readerSchedule = File("src/main/java/eu/kanade/presentation/reader/ReaderScheduleDialog.kt")
    private val sourceEvaluation = File("src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt")
    private val settingsShared = File("src/main/java/exh/recs/settings/RecommendationSettingsSharedComponents.kt")
    private val bestVersion = File("src/main/java/exh/recs/bestversion/BestVersionCompareScreen.kt")
    private val actionHistory = File("src/main/java/exh/util/EvaluationModeActionHistoryScreen.kt")
    private val resources = File("../i18n-kmk/src/commonMain/moko-resources/base/strings.xml")

    @Test
    fun `reader weekdays wrap and use locale display names`() {
        val source = readerSchedule.readText()

        assertTrue(source.contains("FlowRow("))
        assertTrue(source.contains("getDisplayName(TextStyle.SHORT, locale)"))
        assertTrue(source.contains("LocalConfiguration.current.locales[0]"))
        assertFalse(source.contains("resources.configuration.locales"))
        assertFalse(source.contains("day.name.take(1)"))
        assertFalse(source.contains("(whole day)"))
    }

    @Test
    fun `selected route geometry does not encode fixed left or right ownership`() {
        val forbidden = Regex("padding\\s*\\([^)]*\\b(left|right)\\s*=|absoluteOffset\\s*\\(|TextAlign\\.(Left|Right)")
        val selected = listOf(readerSchedule, sourceEvaluation, settingsShared, bestVersion, actionHistory)

        selected.forEach { file ->
            assertFalse(forbidden.containsMatchIn(file.readText()), "Fixed directional geometry in ${file.path}")
        }
    }

    @Test
    fun `recommendation casing boundaries are locale invariant`() {
        val roots = listOf(File("src/main/java/exh/recs"), File("src/main/java/exh/util"))
        val bareCaseCall = Regex("\\.(lowercase|uppercase)\\(\\)")
        val offenders = roots
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .filter { file -> bareCaseCall.containsMatchIn(file.readText()) }

        assertTrue(offenders.isEmpty(), "Locale-sensitive casing remains in: ${offenders.joinToString { it.path }}")
    }

    @Test
    fun `installer mode labels are exhaustive resource-backed branches`() {
        val source = sourceEvaluation.readText()
        val strings = resources.readText()

        assertTrue(source.contains("source_evaluation_installer_current_label"))
        assertTrue(source.contains("source_evaluation_installer_private_label"))
        assertTrue(source.contains("source_evaluation_installer_shizuku_label"))
        assertFalse(source.contains("mode.name.lowercase"))
        assertTrue(strings.contains("name=\"source_evaluation_installer_current_label\""))
        assertTrue(strings.contains("name=\"source_evaluation_installer_shizuku_label\""))
    }

    @Test
    fun `intentional horizontal collections are not used for prose`() {
        val settings = settingsShared.readText()
        val preview = bestVersion.readText()

        assertTrue(settings.contains("RecommendationSettingsQuickAccessRow"))
        assertTrue(settings.contains(".horizontalScroll(scrollState)"))
        assertTrue(preview.contains("items(previewState.pages"))
        assertFalse(readerSchedule.readText().contains("horizontalScroll"))
    }
}
