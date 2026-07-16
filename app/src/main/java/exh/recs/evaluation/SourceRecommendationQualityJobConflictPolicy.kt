package exh.recs.evaluation

// KMK --> v0.7.44: pure, testable extraction of the job-conflict guard decision used by
// SourceEvaluationScreenModel. Source Evaluation and the background For You search compatibility
// job (SourceRecommendationQualityJob) both may temporarily install/uninstall extensions and must
// never run at the same time. This was previously two inline `if (...Job.isRunning(context))`
// checks with no dedicated test coverage (Android WorkManager's isRunning() itself is not
// unit-testable, but the decision of "which job should block starting the other" is pure).
internal object SourceRecommendationQualityJobConflictPolicy {

    /**
     * Returns the job kind that should block starting [starting], or null if starting is allowed.
     */
    fun conflictFor(
        starting: ScreenErrorKey.ActiveJobKind,
        sourceEvaluationRunning: Boolean,
        recommendationQualityRunning: Boolean,
    ): ScreenErrorKey.ActiveJobKind? = when (starting) {
        ScreenErrorKey.ActiveJobKind.SOURCE_EVALUATION ->
            ScreenErrorKey.ActiveJobKind.RECOMMENDATION_QUALITY.takeIf { recommendationQualityRunning }
        ScreenErrorKey.ActiveJobKind.RECOMMENDATION_QUALITY ->
            ScreenErrorKey.ActiveJobKind.SOURCE_EVALUATION.takeIf { sourceEvaluationRunning }
    }
}
// KMK <--
