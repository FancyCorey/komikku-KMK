package exh.recs.bestversion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

// KMK -->
/**
 * Tests for [BestVersionSourceLabelPolicy] -- the behavior contract's required "Evaluation Mode source-label
 * privacy" coverage for Find Best Version, extracted from
 * [BestVersionCompareScreenModel.sourceName] since that method requires a full screen-model
 * instantiation (SourceManager, GetManga, GetChaptersByMangaId, NetworkToLocalManga,
 * MigrateMangaUseCase, UpsertMangaSourceQualitySignal) no existing test in this codebase performs.
 */
class BestVersionSourceLabelPolicyTest {

    @Test
    fun `Evaluation Mode off returns the raw name`() {
        val result = BestVersionSourceLabelPolicy.resolve(evaluationModeEnabled = false, sourceId = 1L, rawName = { "MangaDex" })
        assertEquals("MangaDex", result)
    }

    @Test
    fun `Evaluation Mode on never returns the raw name`() {
        val result = BestVersionSourceLabelPolicy.resolve(evaluationModeEnabled = true, sourceId = 1L, rawName = { "MangaDex" })
        assertFalse(result == "MangaDex", "Evaluation Mode must never surface the raw source name")
        assertEquals(exh.util.EvaluationModeFormatter.sourceLabel(1L), result)
    }

    @Test
    fun `Evaluation Mode on never invokes the raw-name lookup at all`() {
        var invoked = false
        BestVersionSourceLabelPolicy.resolve(evaluationModeEnabled = true, sourceId = 2L, rawName = {
            invoked = true
            "Should Not Be Read"
        })
        assertFalse(invoked, "the private source lookup must not run when Evaluation Mode is enabled -- not just discarded after running")
    }

    @Test
    fun `the same sourceId always resolves to the same obfuscated label`() {
        val first = BestVersionSourceLabelPolicy.resolve(evaluationModeEnabled = true, sourceId = 99L, rawName = { "X" })
        val second = BestVersionSourceLabelPolicy.resolve(evaluationModeEnabled = true, sourceId = 99L, rawName = { "X" })
        assertEquals(first, second)
    }
}
// KMK <--
