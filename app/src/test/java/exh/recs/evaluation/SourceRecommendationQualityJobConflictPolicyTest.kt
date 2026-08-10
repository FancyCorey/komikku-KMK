package exh.recs.evaluation

// KMK --> v0.7.44: tests for the pure job-conflict guard decision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SourceRecommendationQualityJobConflictPolicyTest {

    @Test
    fun `starting Source Evaluation is allowed when nothing else is running`() {
        val result = SourceRecommendationQualityJobConflictPolicy.conflictFor(
            starting = ScreenErrorKey.ActiveJobKind.SOURCE_EVALUATION,
            sourceEvaluationRunning = false,
            recommendationQualityRunning = false,
        )
        assertNull(result)
    }

    @Test
    fun `starting Source Evaluation is blocked when the quality job is running`() {
        val result = SourceRecommendationQualityJobConflictPolicy.conflictFor(
            starting = ScreenErrorKey.ActiveJobKind.SOURCE_EVALUATION,
            sourceEvaluationRunning = false,
            recommendationQualityRunning = true,
        )
        assertEquals(ScreenErrorKey.ActiveJobKind.RECOMMENDATION_QUALITY, result)
    }

    @Test
    fun `starting the quality job is allowed when nothing else is running`() {
        val result = SourceRecommendationQualityJobConflictPolicy.conflictFor(
            starting = ScreenErrorKey.ActiveJobKind.RECOMMENDATION_QUALITY,
            sourceEvaluationRunning = false,
            recommendationQualityRunning = false,
        )
        assertNull(result)
    }

    @Test
    fun `starting the quality job is blocked when Source Evaluation is running`() {
        val result = SourceRecommendationQualityJobConflictPolicy.conflictFor(
            starting = ScreenErrorKey.ActiveJobKind.RECOMMENDATION_QUALITY,
            sourceEvaluationRunning = true,
            recommendationQualityRunning = false,
        )
        assertEquals(ScreenErrorKey.ActiveJobKind.SOURCE_EVALUATION, result)
    }

    @Test
    fun `starting Source Evaluation is not blocked by itself being reported as running`() {
        // The "starting" job's own running flag is irrelevant to its own conflict check —
        // only the *other* job's running state can block it.
        val result = SourceRecommendationQualityJobConflictPolicy.conflictFor(
            starting = ScreenErrorKey.ActiveJobKind.SOURCE_EVALUATION,
            sourceEvaluationRunning = true,
            recommendationQualityRunning = false,
        )
        assertNull(result)
    }
}
// KMK <--
