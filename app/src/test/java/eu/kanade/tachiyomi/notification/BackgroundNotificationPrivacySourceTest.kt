package eu.kanade.tachiyomi.notification

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class BackgroundNotificationPrivacySourceTest {
    private fun source(path: String): String = Files.readAllBytes(Path.of(path)).toString(Charsets.UTF_8)

    @Test
    fun `save-image notifications never receive formatted exception text`() {
        val reader = source("src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt")
        val saveImageRegion = reader.substring(reader.indexOf("fun saveImage("), reader.indexOf("private fun saveImages("))

        assertFalse("notifier.onError(with(context) { e.formattedMessage })" in saveImageRegion)
        assertTrue("notifier.onError(context.stringResource(MR.strings.unknown_error))" in saveImageRegion)
        assertTrue(Regex(Regex.escape("rethrowIfFatal(e)")).findAll(saveImageRegion).count() >= 2)
    }

    @Test
    fun `source evaluation notifications apply the evaluation-mode label policy`() {
        val evaluation = source("src/main/java/exh/recs/evaluation/SourceEvaluationNotifier.kt")
        val quality = source("src/main/java/exh/recs/evaluation/SourceRecommendationQualityNotifier.kt")

        assertTrue("SourceEvaluationProgressLabelPolicy.extensionLabel" in evaluation)
        assertTrue("sourcePreferences.evaluationMode().get()" in evaluation)
        assertTrue("SourceEvaluationProgressLabelPolicy.sourceLabel" in quality)
        assertTrue("sourcePreferences.evaluationMode().get()" in quality)
    }
}
