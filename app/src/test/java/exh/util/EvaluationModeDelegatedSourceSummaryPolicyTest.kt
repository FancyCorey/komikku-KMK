package exh.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EvaluationModeDelegatedSourceSummaryPolicyTest {

    private val rawNames = listOf("Pururin", "MangaDex", "8Muses", "NHentai", "LANraragi")

    @Test
    fun `evaluation mode replaces every delegated source name`() {
        val summary = EvaluationModeDelegatedSourceSummaryPolicy.displayNames(rawNames, true)

        assertEquals(5, summary.split(", ").size)
        assertTrue(summary.split(", ").all { it.matches(Regex("Source [A-Z]+")) })
        assertTrue(rawNames.none(summary::contains))
    }

    @Test
    fun `evaluation mode keeps names unchanged for normal settings`() {
        val summary = EvaluationModeDelegatedSourceSummaryPolicy.displayNames(rawNames, false)

        assertEquals(rawNames.joinToString(), summary)
    }

    @Test
    fun `evaluation mode deduplicates after relabeling`() {
        val summary = EvaluationModeDelegatedSourceSummaryPolicy.displayNames(
            listOf("Pururin", "Pururin", "MangaDex"),
            true,
        )

        assertEquals(2, summary.split(", ").size)
        assertFalse(summary.contains("Pururin"))
        assertFalse(summary.contains("MangaDex"))
    }
}
