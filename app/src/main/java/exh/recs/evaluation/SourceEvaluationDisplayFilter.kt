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
        // KMK v0.8.1-fix4: source/library-quality-disliked rows hidden by default, recoverable via toggle
        val hiddenSourceQualityCount: Int = 0,
    )

    /**
     * Filter [evaluations] by installed-extension visibility and source/library-quality dislike.
     *
     * @param installedExtensionKeys Set of "${signatureHash}|${pkgName}" for installed extensions.
     * @param showInstalled When false (default), evaluations whose extensionKey matches an
     *   installed extension are hidden from the list.
     * @param qualityDislikedExtensionKeys Set of "a|${signatureHash}|${pkgName}" marked poor/explicit
     *   as a source/library, independent of the recommendation-dislike axis.
     * @param showSourceQualityDisliked When false (default), source-quality-disliked rows are hidden
     *   from the list (recoverable via a compact toggle) rather than deleted — history is preserved.
     */
    fun filter(
        evaluations: List<SourceEvaluation>,
        installedExtensionKeys: Set<String>,
        showInstalled: Boolean,
        qualityDislikedExtensionKeys: Set<String> = emptySet(),
        showSourceQualityDisliked: Boolean = false,
    ): FilterResult {
        var hiddenInstalledCount = 0
        var hiddenQualityCount = 0

        val visible = evaluations.filter { eval ->
            val key = eval.extensionKey
            val isInstalled = key.isNotBlank() && key in installedExtensionKeys
            if (!showInstalled && isInstalled) {
                hiddenInstalledCount++
                return@filter false
            }
            val qualityKey = "a|$key"
            val isQualityDisliked = qualityKey in qualityDislikedExtensionKeys
            if (!showSourceQualityDisliked && isQualityDisliked) {
                hiddenQualityCount++
                return@filter false
            }
            true
        }

        return FilterResult(
            visible = visible,
            hiddenInstalledCount = hiddenInstalledCount,
            hiddenSourceQualityCount = hiddenQualityCount,
        )
    }
}
// KMK <--
