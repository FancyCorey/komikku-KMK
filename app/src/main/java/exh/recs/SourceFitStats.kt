package exh.recs

// KMK -->

/**
 * Rolling per-source stats accumulated across For You runs.
 * Stats are merged after each run via [SourceFitStatsStore.mergeRun].
 */
data class SourceFitStats(
    val sourceId: Long,
    val runCount: Int,
    val shownCount: Int,
    val noMatchCount: Int,
    val filteredOutCount: Int,
    val errorCount: Int,
    val hiddenByDuplicateCount: Int,
    val totalVisibleCandidates: Int,
    val lastSuccessAt: Long,
    val lastErrorAt: Long,
    val updatedAt: Long,
) {
    val fitLabel: SourceFitLabel
        get() {
            if (runCount < MIN_RUNS_FOR_LABEL) return SourceFitLabel.TooLittleData
            val errorRate = errorCount.toDouble() / runCount
            val shownRate = shownCount.toDouble() / runCount
            val noMatchRate = noMatchCount.toDouble() / runCount
            val filteredRate = filteredOutCount.toDouble() / runCount
            val avgVisible = totalVisibleCandidates.toDouble() / maxOf(1, shownCount)
            return when {
                errorRate > 0.6 -> SourceFitLabel.OftenErrors
                shownRate > 0.5 && avgVisible >= 3.0 -> SourceFitLabel.GreatFit
                shownRate > 0.3 -> SourceFitLabel.GoodFit
                noMatchRate > 0.5 -> SourceFitLabel.NoMatchesRecently
                filteredRate > 0.4 -> SourceFitLabel.OftenFiltered
                else -> SourceFitLabel.Mixed
            }
        }

    internal fun merge(status: RecommendationSourceRunStatus, now: Long): SourceFitStats = copy(
        runCount = runCount + 1,
        shownCount = shownCount + if (status.status == RecommendationSourceStatus.Shown) 1 else 0,
        noMatchCount = noMatchCount + if (status.status == RecommendationSourceStatus.NoMatches) 1 else 0,
        filteredOutCount = filteredOutCount + if (status.status == RecommendationSourceStatus.FilteredOut) 1 else 0,
        errorCount = errorCount + if (status.status == RecommendationSourceStatus.Error) 1 else 0,
        hiddenByDuplicateCount = hiddenByDuplicateCount + if (status.status == RecommendationSourceStatus.HiddenByDuplicateHandling) 1 else 0,
        totalVisibleCandidates = totalVisibleCandidates + status.visibleCount,
        lastSuccessAt = if (status.status == RecommendationSourceStatus.Shown) now else lastSuccessAt,
        lastErrorAt = if (status.status == RecommendationSourceStatus.Error) now else lastErrorAt,
        updatedAt = now,
    )

    companion object {
        const val MIN_RUNS_FOR_LABEL = 3
    }
}

enum class SourceFitLabel {
    GreatFit,
    GoodFit,
    Mixed,
    NoMatchesRecently,
    OftenFiltered,
    OftenErrors,
    TooLittleData,
    ;

    /** Higher score = better fit; used for suggested ordering. */
    val fitScore: Int
        get() = when (this) {
            GreatFit -> 6
            GoodFit -> 5
            Mixed -> 3
            NoMatchesRecently -> 2
            OftenFiltered -> 1
            OftenErrors -> 0
            TooLittleData -> -1
        }
}

object SourceFitStatsStore {
    private const val FIELD_SEP = "|"
    private const val ROW_SEP = ";"

    fun serialize(stats: Collection<SourceFitStats>): String =
        stats.joinToString(ROW_SEP) { s ->
            "${s.sourceId}$FIELD_SEP${s.runCount}$FIELD_SEP${s.shownCount}$FIELD_SEP" +
                "${s.noMatchCount}$FIELD_SEP${s.filteredOutCount}$FIELD_SEP${s.errorCount}$FIELD_SEP" +
                "${s.hiddenByDuplicateCount}$FIELD_SEP${s.totalVisibleCandidates}$FIELD_SEP" +
                "${s.lastSuccessAt}$FIELD_SEP${s.lastErrorAt}$FIELD_SEP${s.updatedAt}"
        }

    fun parse(value: String): Map<Long, SourceFitStats> {
        if (value.isBlank()) return emptyMap()
        val result = mutableMapOf<Long, SourceFitStats>()
        for (row in value.split(ROW_SEP)) {
            runCatching {
                val parts = row.split(FIELD_SEP)
                if (parts.size < 11) return@runCatching
                val sourceId = parts[0].toLong()
                result[sourceId] = SourceFitStats(
                    sourceId = sourceId,
                    runCount = parts[1].toInt(),
                    shownCount = parts[2].toInt(),
                    noMatchCount = parts[3].toInt(),
                    filteredOutCount = parts[4].toInt(),
                    errorCount = parts[5].toInt(),
                    hiddenByDuplicateCount = parts[6].toInt(),
                    totalVisibleCandidates = parts[7].toInt(),
                    lastSuccessAt = parts[8].toLong(),
                    lastErrorAt = parts[9].toLong(),
                    updatedAt = parts[10].toLong(),
                )
            }
        }
        return result
    }

    /**
     * Merges [runStatuses] from a completed For You run into [existing] rolling stats.
     * Only terminal statuses (Shown, NoMatches, FilteredOut, Error, HiddenByDuplicateHandling)
     * are recorded; Disabled/OutsideAttemptLimit are skipped as they indicate the source was
     * not actually searched this run.
     */
    fun mergeRun(
        existing: Map<Long, SourceFitStats>,
        runStatuses: Collection<RecommendationSourceRunStatus>,
    ): Map<Long, SourceFitStats> {
        val result = existing.toMutableMap()
        val now = System.currentTimeMillis()
        for (status in runStatuses) {
            if (status.status == RecommendationSourceStatus.Disabled ||
                status.status == RecommendationSourceStatus.OutsideAttemptLimit
            ) continue
            val current = result[status.sourceId] ?: SourceFitStats(
                sourceId = status.sourceId,
                runCount = 0,
                shownCount = 0,
                noMatchCount = 0,
                filteredOutCount = 0,
                errorCount = 0,
                hiddenByDuplicateCount = 0,
                totalVisibleCandidates = 0,
                lastSuccessAt = 0L,
                lastErrorAt = 0L,
                updatedAt = now,
            )
            result[status.sourceId] = current.merge(status, now)
        }
        return result
    }
}
// KMK <--
