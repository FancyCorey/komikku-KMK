package exh.recs.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class QualitySignalSourceLabelPolicyTest {

    @Test
    fun `Evaluation Mode off returns the persisted source name`() {
        assertEquals(
            "MangaFire",
            QualitySignalSourceLabelPolicy.resolve(false, 42L) { "MangaFire" },
        )
    }

    @Test
    fun `Evaluation Mode on returns a generic source label`() {
        val result = QualitySignalSourceLabelPolicy.resolve(true, 42L) { "MangaFire" }

        assertFalse(result == "MangaFire")
        assertEquals(exh.util.EvaluationModeFormatter.sourceLabel(42L), result)
    }

    @Test
    fun `Evaluation Mode on does not read the persisted source name`() {
        var invoked = false

        QualitySignalSourceLabelPolicy.resolve(true, 42L) {
            invoked = true
            "MangaFire"
        }

        assertFalse(invoked)
    }

    @Test
    fun `the same source id keeps the same generic label`() {
        val first = QualitySignalSourceLabelPolicy.resolve(true, 99L) { "First" }
        val second = QualitySignalSourceLabelPolicy.resolve(true, 99L) { "Second" }

        assertEquals(first, second)
    }
}
