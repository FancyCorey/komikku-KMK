package exh.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EvaluationModeMangaDescriptionPolicyTest {
    @Test
    fun `evaluation mode replaces source annotation and known source names`() {
        val description = "Synopsis\n\n(Source: Tappytoon)\nOfficial Tappytoon release"

        val result = EvaluationModeMangaDescriptionPolicy.displayDescription(
            description = description,
            evaluationModeEnabled = true,
            sourceLabel = "Source I",
            rawSourceNames = listOf("Tappytoon"),
        )

        assertEquals("Synopsis\n\n(Source: Source I)\nOfficial Source I release", result)
    }

    @Test
    fun `evaluation mode replaces all known merged source names longest first`() {
        val description = "Available on Source Alpha and Alpha"

        val result = EvaluationModeMangaDescriptionPolicy.displayDescription(
            description = description,
            evaluationModeEnabled = true,
            sourceLabel = "Source B",
            rawSourceNames = listOf("Alpha", "Source Alpha"),
        )

        assertEquals("Available on Source B and Source B", result)
    }

    @Test
    fun `evaluation mode replaces a bare source annotation`() {
        val result = EvaluationModeMangaDescriptionPolicy.displayDescription(
            description = "Source: HiddenSource",
            evaluationModeEnabled = true,
            sourceLabel = "Source C",
            rawSourceNames = emptyList(),
        )

        assertEquals("Source: Source C", result)
    }

    @Test
    fun `evaluation mode off preserves the original description exactly`() {
        val description = "Synopsis\n\n(Source: Tappytoon)"

        val result = EvaluationModeMangaDescriptionPolicy.displayDescription(
            description = description,
            evaluationModeEnabled = false,
            sourceLabel = "Source I",
            rawSourceNames = listOf("Tappytoon"),
        )

        assertEquals(description, result)
    }

    @Test
    fun `blank and null descriptions are preserved`() {
        assertEquals(
            "",
            EvaluationModeMangaDescriptionPolicy.displayDescription(
                description = "",
                evaluationModeEnabled = true,
                sourceLabel = "Source A",
                rawSourceNames = listOf("HiddenSource"),
            ),
        )
        assertEquals(
            null,
            EvaluationModeMangaDescriptionPolicy.displayDescription(
                description = null,
                evaluationModeEnabled = true,
                sourceLabel = "Source A",
                rawSourceNames = listOf("HiddenSource"),
            ),
        )
    }
}
