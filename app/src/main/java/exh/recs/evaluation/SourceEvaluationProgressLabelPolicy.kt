package exh.recs.evaluation

import exh.util.EvaluationModeFormatter

object SourceEvaluationProgressLabelPolicy {
    data class Labels(
        val extension: String?,
        val source: String?,
    )

    fun labels(
        queueState: SourceEvaluationQueueState,
        evaluationModeEnabled: Boolean,
    ): Labels = Labels(
        extension = queueState.currentExtensionName?.let { extensionLabel(it, evaluationModeEnabled) },
        source = queueState.currentSourceName?.let { sourceLabel(it, evaluationModeEnabled) },
    )

    fun extensionLabel(name: String, evaluationModeEnabled: Boolean): String =
        if (evaluationModeEnabled) EvaluationModeFormatter.sourceLabel("ext:$name") else name

    fun sourceLabel(name: String, evaluationModeEnabled: Boolean): String =
        if (evaluationModeEnabled) EvaluationModeFormatter.sourceLabel(name) else name
}
