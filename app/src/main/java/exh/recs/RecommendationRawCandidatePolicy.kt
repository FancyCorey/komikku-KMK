package exh.recs

/** Shared page shaping for source results: discard duplicate records before applying the work cap. */
internal object RecommendationRawCandidatePolicy {
    fun <T> distinctWithinLimit(items: List<T>, limit: Int, key: (T) -> String): List<T> {
        if (limit <= 0) return emptyList()
        return items.distinctBy(key).take(limit)
    }
}
