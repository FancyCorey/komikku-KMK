package exh.recs.settings

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class RecommendationPreferenceMutationAtomicitySourceTest {
    @Test
    fun `multi-key preference writers restore their prior values before rethrowing`() {
        val source = File("src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt").readText()
        val recommendationWriter = source.substringAfter("private fun writeRecommendationSourcePreferenceState")
            .substringBefore("fun setInstalledSourcePreference")
        val qualityWriter = source.substringAfter("private fun writeSourceQualityState")
            .substringBefore("private fun journalSourceQualityChange")

        listOf(recommendationWriter, qualityWriter).forEach { writer ->
            assertTrue(writer.contains("try {"))
            assertTrue(writer.contains("catch (e: Throwable)"))
            assertTrue(writer.contains("runCatching"))
            assertTrue(writer.contains("throw e"))
        }
    }
}
