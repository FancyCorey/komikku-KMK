package exh.recs.evaluation

import exh.util.EvaluationModeFormatter
import tachiyomi.domain.taste.model.SourceEvaluationProbeMarker

// KMK -->
/**
 * Pure sanitization policy for [SourceEvaluationDiagnosticsBuilder]. Separated out so every
 * privacy-sensitive field in the copy-to-clipboard diagnostics export has one unit-testable
 * decision point, mirroring [exh.recs.settings.RecommendationSettingsSectionSummaries.sourcePrioritySummary].
 *
 * Evaluation Mode ([exh.util.EvaluationModeFormatter]) is a display-only relabeling layer, not a
 * security boundary -- this policy exists so the clipboard export independently stays privacy-safe
 * rather than assuming whatever the on-screen UI happens to already redact. Regardless of Evaluation
 * Mode state, this policy never emits extension package names, signature hashes, or raw error/exception
 * text -- those are never "intentionally safe diagnostics" for a value that can be pasted into a public
 * bug report.
 */
object SourceEvaluationDiagnosticsPolicy {

    data class SanitizedProbeMarker(
        val extensionLabel: String,
        val sourceLabel: String?,
        val phase: String,
        val updatedAt: Long,
    )

    data class Sanitized(
        val currentExtensionLabel: String?,
        val currentSourceLabel: String?,
        val errorCategory: String?,
        val probeMarker: SanitizedProbeMarker?,
    )

    fun sanitize(
        evaluationModeEnabled: Boolean,
        currentExtensionName: String?,
        currentSourceName: String?,
        lastError: String?,
        lastProbeMarker: SourceEvaluationProbeMarker?,
    ): Sanitized {
        return Sanitized(
            currentExtensionLabel = currentExtensionName?.let { extensionLabel(it, evaluationModeEnabled) },
            currentSourceLabel = currentSourceName?.let { sourceLabel(it, evaluationModeEnabled) },
            errorCategory = lastError?.let { errorCategoryLabel(it) },
            probeMarker = lastProbeMarker?.let { marker ->
                SanitizedProbeMarker(
                    extensionLabel = extensionLabel(marker.extensionName, evaluationModeEnabled, keyOverride = marker.extensionPkgName),
                    sourceLabel = marker.sourceName?.let { sourceLabel(it, evaluationModeEnabled, idOverride = marker.sourceId) },
                    phase = marker.phase,
                    updatedAt = marker.updatedAt,
                )
            },
        )
    }

    private fun extensionLabel(name: String, evaluationModeEnabled: Boolean, keyOverride: String? = null): String =
        if (evaluationModeEnabled) EvaluationModeFormatter.sourceLabel("ext:${keyOverride ?: name}") else name

    private fun sourceLabel(name: String, evaluationModeEnabled: Boolean, idOverride: Long? = null): String =
        if (evaluationModeEnabled) {
            if (idOverride != null) EvaluationModeFormatter.sourceLabel(idOverride) else EvaluationModeFormatter.sourceLabel(name)
        } else {
            name
        }

    /**
     * Finite, stable category for [rawError] -- never the raw exception/error text itself, in either
     * Evaluation Mode state. Reuses [SourceRecommendationFitFailureClassifier] rather than inventing a
     * parallel classification system.
     */
    private fun errorCategoryLabel(rawError: String): String {
        val kind = SourceRecommendationFitFailureClassifier.classify(rawError)
        return kind.name.split("_").joinToString(" ") { it.lowercase().replaceFirstChar(Char::uppercase) }
    }
}
// KMK <--
