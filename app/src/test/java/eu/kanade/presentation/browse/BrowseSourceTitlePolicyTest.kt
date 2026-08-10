package eu.kanade.presentation.browse

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class BrowseSourceTitlePolicyTest {
    @Test
    fun `Evaluation Mode off returns the raw source name`() {
        assertEquals(
            "Manhwatop",
            BrowseSourceTitlePolicy.resolve(false, 42L) { "Manhwatop" },
        )
    }

    @Test
    fun `Evaluation Mode on returns a generic source label`() {
        val result = BrowseSourceTitlePolicy.resolve(true, 43L) { "Manhwatop" }

        assertFalse(result == "Manhwatop")
        assertEquals(exh.util.EvaluationModeFormatter.sourceLabel(43L), result)
    }

    @Test
    fun `Evaluation Mode on does not invoke the raw source name supplier`() {
        var invoked = false

        BrowseSourceTitlePolicy.resolve(true, 44L) {
            invoked = true
            "Private source"
        }

        assertFalse(invoked)
    }

    @Test
    fun `the same source id keeps the same generic title`() {
        val first = BrowseSourceTitlePolicy.resolve(true, 45L) { "First" }
        val second = BrowseSourceTitlePolicy.resolve(true, 45L) { "Second" }

        assertEquals(first, second)
    }
}
