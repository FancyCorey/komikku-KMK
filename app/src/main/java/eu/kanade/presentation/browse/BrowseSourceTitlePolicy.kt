package eu.kanade.presentation.browse

import exh.util.EvaluationModeFormatter

/** Keeps source-specific Browse titles privacy-safe during Evaluation Mode evidence capture. */
object BrowseSourceTitlePolicy {
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
