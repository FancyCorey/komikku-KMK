package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * R1 correction (fix6): proves `ManagementAction.CLEAR_SEEN_MANGA` no longer touches the legacy
 * `seenRecommendationMangaKeys` preference at all -- a prior "one-way hygiene" write there was
 * itself a live cleanup write of the legacy store, which the frozen R1 exit gate prohibits outside
 * the one-time migration and old-backup-restore boundaries. `SourceEvaluationScreenModel` has a
 * large, multi-dependency constructor (25+ collaborators); constructing it directly for this one
 * assertion is disproportionate to a single-line deletion, so this is a source-text guard, matching
 * this codebase's own established convention for such cases (see `RecommendsScreenModelSeenKeysSourceTest`
 * for the same reasoning applied to a sibling screen model).
 */
class SourceEvaluationScreenModelClearSeenMangaSourceTest {

    private fun source(): String = File("src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt").also {
        assertTrue(it.isFile, "expected source file at ${it.path}")
    }.readText()

    @Test
    fun `CLEAR_SEEN_MANGA never writes the legacy seen-manga preference`() {
        val text = source()
        val actionStart = text.indexOf("ManagementAction.CLEAR_SEEN_MANGA ->")
        assertTrue(actionStart >= 0, "expected to find the CLEAR_SEEN_MANGA branch")
        val nextBranchStart = text.indexOf("ManagementAction.CLEAR_DISMISSED_SUGGESTIONS", actionStart)
        assertTrue(nextBranchStart > actionStart, "expected a following branch to bound the CLEAR_SEEN_MANGA block")
        val branchBody = text.substring(actionStart, nextBranchStart)

        assertFalse(
            branchBody.contains("seenRecommendationMangaKeys"),
            "CLEAR_SEEN_MANGA must not touch the legacy preference -- MangaTaste is the sole rating-family authority",
        )
        assertTrue(
            branchBody.contains("clearMangaTaste.await("),
            "CLEAR_SEEN_MANGA must still clear the authoritative MangaTaste rows",
        )
    }
}
