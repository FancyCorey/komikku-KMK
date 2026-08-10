package eu.kanade.presentation.browse

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ExtensionDetailsSourceTitlePolicyTest {

    @Test
    fun `Evaluation Mode off preserves the original multi-source name`() {
        assertEquals(
            "Private Source",
            extensionSourceTitle(
                evaluationModeEnabled = false,
                sourceId = 501L,
                labelAsName = true,
                rawName = { "Private Source" },
                languageLabel = { "English" },
            ),
        )
    }

    @Test
    fun `Evaluation Mode off preserves the original language fallback`() {
        assertEquals(
            "English",
            extensionSourceTitle(
                evaluationModeEnabled = false,
                sourceId = 502L,
                labelAsName = false,
                rawName = { "Private Source" },
                languageLabel = { "English" },
            ),
        )
    }

    @Test
    fun `Evaluation Mode on uses a generic source id label`() {
        val result = extensionSourceTitle(
            evaluationModeEnabled = true,
            sourceId = 503L,
            labelAsName = true,
            rawName = { "Private Source" },
            languageLabel = { "English" },
        )

        assertEquals(exh.util.EvaluationModeFormatter.sourceLabel(503L), result)
        assertFalse(result == "Private Source")
    }

    @Test
    fun `Evaluation Mode on does not evaluate the raw source name`() {
        var rawNameEvaluated = false

        extensionSourceTitle(
            evaluationModeEnabled = true,
            sourceId = 504L,
            labelAsName = true,
            rawName = {
                rawNameEvaluated = true
                "Private Source"
            },
            languageLabel = { "English" },
        )

        assertFalse(rawNameEvaluated)
    }

    @Test
    fun `the same source id keeps a stable generic title`() {
        val first = extensionSourceTitle(
            evaluationModeEnabled = true,
            sourceId = 505L,
            labelAsName = true,
            rawName = { "First Private Source" },
            languageLabel = { "English" },
        )
        val second = extensionSourceTitle(
            evaluationModeEnabled = true,
            sourceId = 505L,
            labelAsName = true,
            rawName = { "Second Private Source" },
            languageLabel = { "English" },
        )

        assertEquals(first, second)
    }
}
