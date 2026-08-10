package exh.recs

// KMK -->

/**
 * Corrects pre-dedupe source statuses by checking whether each [RecommendationSourceStatus.Shown]
 * source's recommendations are still visible after cross-source display dedupe.
 *
 * [sourceHasVisibleResults] maps sourceId → true if the source has at least one card surviving
 * display dedupe, false if all cards were hidden. Sources not in the map are left unchanged.
 *
 * Sources with [RecommendationSourceStatus.Shown] that have no visible post-dedupe cards are
 * reclassified as [RecommendationSourceStatus.HiddenByDuplicateHandling] with visibleCount=0.
 * All other statuses are unchanged.
 */
internal fun adjustStatusesForDedupe(
    statuses: Map<Long, RecommendationSourceRunStatus>,
    sourceHasVisibleResults: Map<Long, Boolean>,
): Map<Long, RecommendationSourceRunStatus> =
    statuses.mapValues { (sourceId, status) ->
        if (status.status != RecommendationSourceStatus.Shown) return@mapValues status
        if (sourceHasVisibleResults[sourceId] == false) {
            status.copy(status = RecommendationSourceStatus.HiddenByDuplicateHandling, visibleCount = 0)
        } else {
            status
        }
    }

// KMK <--
