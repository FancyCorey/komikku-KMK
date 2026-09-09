package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** Guards the shared row rhythm used by every Source Evaluation result. */
class SourceEvaluationRowRhythmSourceTest {

    @Test
    fun `result row uses shared vertical spacing for text and wrapped actions`() {
        val source = File("src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt").readText()
        val rowStart = source.indexOf("private fun EvaluationResultRow(")
        assertTrue(rowStart >= 0, "expected the shared EvaluationResultRow owner")
        val row = source.substring(rowStart)

        assertTrue(
            row.contains("verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall)"),
            "the result row must keep text and actions on a consistent vertical rhythm",
        )
        assertTrue(
            row.contains(
                "modifier = Modifier.weight(1f),\n            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall)",
            ),
            "the primary source/status text stack must use the same shared vertical rhythm",
        )
        assertTrue(
            !row.contains("verticalArrangement = Arrangement.spacedBy(0.dp)"),
            "wrapped result actions must not collapse into zero vertical spacing",
        )
    }
}
