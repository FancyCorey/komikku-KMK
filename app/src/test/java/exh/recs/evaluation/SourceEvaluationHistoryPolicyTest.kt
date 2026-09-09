package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class SourceEvaluationHistoryPolicyTest {
    @Test
    fun `successful management action in Evaluation Mode records visibility event`() {
        assertTrue(SourceEvaluationHistoryPolicy.shouldRecordDataClearedEvent(true))
    }

    @Test
    fun `ordinary failure does not record visibility event`() {
        assertFalse(SourceEvaluationHistoryPolicy.shouldRecordDataClearedEvent(false))
    }

    @Test
    fun `Evaluation Mode disabled still records a successful visibility event`() {
        assertTrue(SourceEvaluationHistoryPolicy.shouldRecordDataClearedEvent(true))
    }

    @Test
    fun `Source Evaluation screen model logs do not carry throwable or raw identifier payloads`() {
        val source = File("src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt").readText()

        assertFalse(source.contains("logcat(LogPriority.ERROR, e)"))
        assertFalse(source.contains("logcat(LogPriority.WARN, e)"))
        assertFalse(source.contains("\$pkgName"))
        assertFalse(source.contains("\$key"))
    }

    @Test
    fun `Source Evaluation runner logs do not carry throwable or raw source identifiers`() {
        val source = File("src/main/java/exh/recs/evaluation/SourceEvaluationRunner.kt").readText()

        assertFalse(source.contains("logcat(LogPriority.ERROR, e)"))
        assertFalse(source.contains("logcat(LogPriority.WARN, e)"))
        assertFalse(source.contains("logcat(LogPriority.WARN, unwrapped)"))
        assertFalse(source.contains("logcat(LogPriority.ERROR, unwrapped)"))
        assertFalse(source.contains("KMK SourceEvaluation install: ext=\${ext.name}"))
        assertFalse(source.contains("post-install \${ext.name}"))
        assertFalse(source.contains("Probe failed for source \${source.name}"))
        assertFalse(source.contains("Extension evaluation failed: \${ext.name}"))
        assertFalse(source.contains("\${ext.name} / \${source.name}"))
        assertFalse(source.contains("Cleanup failed for \${ext.name}"))
        assertFalse(source.contains("pkg=\${ext.pkgName}"))
    }

    @Test
    fun `Source Evaluation startup recovery logs do not carry throwable or raw identifiers`() {
        val source = File("src/main/java/exh/recs/evaluation/SourceEvaluationStartupRecovery.kt").readText()

        assertFalse(source.contains("logcat(LogPriority.ERROR, e)"))
        assertFalse(source.contains("quarantined \${decision.marker.extensionName}"))
        assertFalse(source.contains("pkgName=\${decision.marker.extensionPkgName}"))
        assertFalse(source.contains("for \${decision.marker.extensionName}"))
        assertFalse(source.contains("for \${seed.pkgName}"))
        assertFalse(source.contains("sig=\$signatureHash"))
    }
}
