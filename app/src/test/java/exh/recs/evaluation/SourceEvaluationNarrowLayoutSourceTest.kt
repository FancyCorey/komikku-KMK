package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** Guards wrapping for Source Evaluation controls that can exceed a phone-width row. */
class SourceEvaluationNarrowLayoutSourceTest {

    @Test
    fun `batch chips and runtime recovery actions use the shared wrapping layout`() {
        val source = File("src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt").readText()

        val batchStart = source.indexOf("private fun BatchSizeSelector(")
        val runtimeStart = source.indexOf("private fun RuntimeHealthIssueRow(")
        val runtimeEnd = source.indexOf("private fun SafetyDiagnosticsRow(", runtimeStart)
        assertTrue(batchStart >= 0, "expected the batch-size control owner")
        assertTrue(runtimeStart >= 0, "expected the runtime-health action owner")
        assertTrue(runtimeEnd > runtimeStart, "expected the runtime-health owner boundary")

        val batch = source.substring(batchStart, runtimeStart)
        val runtime = source.substring(runtimeStart, runtimeEnd)
        assertTrue(batch.contains("FlowRow("), "batch-size chips must wrap on narrow layouts")
        assertTrue(runtime.contains("FlowRow("), "runtime recovery actions must wrap on narrow layouts")
        assertTrue(
            batch.contains("verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall)"),
            "wrapped batch chips must keep a readable vertical rhythm",
        )
        assertTrue(
            runtime.contains("verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall)"),
            "wrapped runtime actions must keep a readable vertical rhythm",
        )
    }

    @Test
    fun `installer mode chips use the shared wrapping layout`() {
        val source = File("src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt").readText()
        val start = source.indexOf("private fun InstallerModeSelector(")
        val end = source.indexOf("private fun OptionToggleRow(", start)
        assertTrue(start >= 0, "expected the installer-mode control owner")
        assertTrue(end > start, "expected the installer-mode owner boundary")
        val owner = source.substring(start, end)
        assertTrue(owner.contains("FlowRow("), "installer-mode chips must wrap on narrow layouts")
        assertTrue(
            owner.contains("verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall)"),
            "wrapped installer chips must keep a readable vertical rhythm",
        )
    }
}
