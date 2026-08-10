package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

// KMK -->
/**
 * Source-level integration guard for the transient per-source error path.
 *
 * The JVM test suite does not provide a Compose device harness here. This assertion keeps the
 * existing queue-state field and the user-visible summary branch coupled, while the device fixture
 * verifies the actual rendered route separately.
 */
class SourceEvaluationErrorSummaryContractTest {
    @Test
    fun `summary renders a bounded localized count for transient source errors`() {
        val screen = File("src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt").readText()
        val strings = File("../i18n-kmk/src/commonMain/moko-resources/base/strings.xml").readText()

        assertTrue(screen.contains("if (queueState.errorCount > 0)"))
        assertTrue(screen.contains("KMR.strings.source_evaluation_error_count"))
        assertTrue(strings.contains("name=\"source_evaluation_error_count\""))
    }
}
// KMK <--
