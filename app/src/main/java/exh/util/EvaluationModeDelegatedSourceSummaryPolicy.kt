package exh.util

/**
 * Builds the user-facing delegated-source summary without allowing Evaluation Mode to expose
 * source identities. This is intentionally separate from the Composable so the privacy boundary
 * can be regression-tested without constructing the full settings screen.
 */
object EvaluationModeDelegatedSourceSummaryPolicy {

    fun displayNames(sourceNames: Iterable<String>, evaluationModeEnabled: Boolean): String {
        return sourceNames
            .map { sourceName ->
                if (evaluationModeEnabled) {
                    EvaluationModeFormatter.sourceLabel(sourceName)
                } else {
                    sourceName
                }
            }
            .distinct()
            .joinToString()
    }
}
