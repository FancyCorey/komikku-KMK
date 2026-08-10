package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK -->
class SourceEvaluationErrorDisplayPolicyTest {

    @Test
    fun `evaluation mode off preserves crash recovery extension identity and phase`() {
        val result = SourceEvaluationErrorDisplayPolicy.crashRecoveryDisplay(
            extensionName = "Real Extension",
            phase = "Probing",
            evaluationModeEnabled = false,
        )

        assertEquals("Real Extension", result.extensionLabel)
        assertEquals("Probing", result.phase)
    }

    @Test
    fun `evaluation mode on relabels crash recovery extension identity`() {
        val result = SourceEvaluationErrorDisplayPolicy.crashRecoveryDisplay(
            extensionName = "com.example.private-extension",
            phase = "Installing",
            evaluationModeEnabled = true,
        )

        assertNotEquals("com.example.private-extension", result.extensionLabel)
        assertTrue(result.extensionLabel.startsWith("Source "))
        assertEquals("Installing", result.phase)
    }
}
// KMK <--
