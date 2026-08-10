package exh.recs.settings

import exh.util.EvaluationModeFormatter

/** Keeps Best Version history source labels private during Evaluation Mode evidence capture. */
object QualitySignalSourceLabelPolicy {
    fun resolve(
        evaluationModeEnabled: Boolean,
        sourceId: Long,
        rawName: () -> String,
    ): String = if (evaluationModeEnabled) {
        EvaluationModeFormatter.sourceLabel(sourceId)
    } else {
        rawName()
    }
}
