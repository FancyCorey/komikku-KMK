package exh.recs.evaluation

import exh.util.EvaluationModeFormatter

// KMK -->
/** Presentation policy for typed Source Evaluation screen errors. */
object SourceEvaluationErrorDisplayPolicy {

    data class CrashRecoveryDisplay(
        val extensionLabel: String,
        val phase: String,
    )

    /**
     * Keep the original user-facing identity outside Evaluation Mode, but apply the same stable
     * relabeling used by active progress and result rows when the mode is enabled.
     */
    fun crashRecoveryDisplay(
        extensionName: String,
        phase: String,
        evaluationModeEnabled: Boolean,
    ): CrashRecoveryDisplay = CrashRecoveryDisplay(
        extensionLabel = if (evaluationModeEnabled) {
            EvaluationModeFormatter.sourceLabel("ext:$extensionName")
        } else {
            extensionName
        },
        phase = phase,
    )
}
// KMK <--
