package exh.recs.evaluation

import tachiyomi.domain.taste.model.SourceEvaluation

// KMK -->
/**
 * Pure display-only filter for Source Evaluation past results.
 *
 * Installed extensions crowd the past-results list when the user is mainly trying to
 * decide which non-installed sources are worth trying. This filter hides them by default.
 * No DB deletion is performed — the rows are always preserved.
 */
object SourceEvaluationDisplayFilter {

    data class FilterResult(
        val visible: List<SourceEvaluation>,
        val hiddenInstalledCount: Int,
    )

    /**
     * Filter [evaluations] by installed-extension visibility.
     *
     * @param installedExtensionKeys Set of "${signatureHash}|${pkgName}" for installed extensions.
     * @param showInstalled When false (default), evaluations whose extensionKey matches an
     *   installed extension are hidden from the list.
     */
    fun filter(
        evaluations: List<SourceEvaluation>,
        installedExtensionKeys: Set<String>,
        showInstalled: Boolean,
    ): FilterResult {
        if (showInstalled || installedExtensionKeys.isEmpty()) {
            return FilterResult(visible = evaluations, hiddenInstalledCount = 0)
        }

        var hiddenCount = 0
        val visible = evaluations.filter { eval ->
            val key = eval.extensionKey
            val isInstalled = key.isNotBlank() && key in installedExtensionKeys
            if (isInstalled) {
                hiddenCount++
                false
            } else {
                true
            }
        }

        return FilterResult(visible = visible, hiddenInstalledCount = hiddenCount)
    }
}
// KMK <--
