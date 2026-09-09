package exh.recs

/**
 * Pure gate for [BrowsePersonalRecommendationsScreenModel.recordVisibleExposure], extracted so the
 * "only a loaded, non-empty, fully-settled, not-already-recorded generation" contract is directly
 * testable without constructing the full ScreenModel (which has ~20 injected dependencies and no
 * existing direct-construction test harness).
 */
object RecommendationExposureCapturePolicy {

    /**
     * @param isLoading the refresh is still in its initial synchronous setup.
     * @param hasItems at least one source row exists in state (loading or otherwise).
     * @param total sources this refresh will attempt (`0` before the batch loop starts).
     * @param progress sources that have finished (success or failure) so far.
     * @param generation the current [BrowsePersonalRecommendationsScreenModel.State.resultGeneration].
     * @param lastRecordedGeneration the generation last passed to a successful recording call, or a
     * sentinel (e.g. `-1`) if none yet.
     */
    fun shouldRecord(
        isLoading: Boolean,
        hasItems: Boolean,
        total: Int,
        progress: Int,
        generation: Long,
        lastRecordedGeneration: Long,
    ): Boolean {
        if (isLoading || !hasItems) return false
        if (total <= 0 || progress < total) return false
        if (generation == lastRecordedGeneration) return false
        return true
    }
}
// KMK <--
