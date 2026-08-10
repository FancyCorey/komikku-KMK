package exh.recs

// KMK -->

data class RecommendationSourceRunStatus(
    val sourceId: Long,
    val status: RecommendationSourceStatus,
    val visibleCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

enum class RecommendationSourceStatus {
    Shown,
    NoMatches,
    FilteredOut,
    Error,
    Disabled,
    OutsideAttemptLimit,
    HiddenByDuplicateHandling,
}

object RecommendationSourceRunStatusStore {
    private const val FIELD_SEP = "|"
    private const val ROW_SEP = ";"

    fun serialize(statuses: Collection<RecommendationSourceRunStatus>): String =
        statuses.joinToString(ROW_SEP) { s ->
            "${s.sourceId}$FIELD_SEP${s.status.name}$FIELD_SEP${s.visibleCount}$FIELD_SEP${s.updatedAt}"
        }

    fun parse(value: String): Map<Long, RecommendationSourceRunStatus> {
        if (value.isBlank()) return emptyMap()
        val result = mutableMapOf<Long, RecommendationSourceRunStatus>()
        for (row in value.split(ROW_SEP)) {
            runCatching {
                val parts = row.split(FIELD_SEP)
                if (parts.size < 4) return@runCatching
                val sourceId = parts[0].toLong()
                val status = RecommendationSourceStatus.valueOf(parts[1])
                val visibleCount = parts[2].toInt()
                val updatedAt = parts[3].toLong()
                result[sourceId] = RecommendationSourceRunStatus(sourceId, status, visibleCount, updatedAt)
            }
        }
        return result
    }
}
// KMK <--
