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
    // KMK --> v0.7.32: D1 — rolling 30-day recent window for more accurate fit labels
    val recentRunCount: Int = 0,
    val recentShownCount: Int = 0,
    val recentErrorCount: Int = 0,
    val windowStartAt: Long = 0L,
    // KMK --> v0.7.32: D2 — number of times this source contributed to the Top Picks row
    val topPicksContributionCount: Int = 0,
    // KMK <--
) {
    val fitLabel: SourceFitLabel
        get() {
            if (runCount < MIN_RUNS_FOR_LABEL) return SourceFitLabel.TooLittleData
            // KMK --> v0.7.32: D1 — prefer recent window rates when sufficient recent data exists
            val useRecent = recentRunCount >= MIN_RUNS_FOR_LABEL
            val effectiveRunCount = if (useRecent) recentRunCount else runCount
            val effectiveShownCount = if (useRecent) recentShownCount else shownCount
            val effectiveErrorCount = if (useRecent) recentErrorCount else errorCount
            // KMK <--
            val errorRate = effectiveErrorCount.toDouble() / effectiveRunCount
            val shownRate = effectiveShownCount.toDouble() / effectiveRunCount
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

    // KMK --> v0.7.32: D1/D2 — accepts optional top-picks contributor flag
    internal fun merge(
        status: RecommendationSourceRunStatus,
        now: Long,
        isTopPicksContributor: Boolean = false,
    ): SourceFitStats {
        // D1: reset recent window counts when the 30-day window has expired
        val windowExpired = windowStartAt > 0L && (now - windowStartAt) > RECENT_WINDOW_MS
        val newWindowStart = if (windowStartAt == 0L || windowExpired) now else windowStartAt
        val wasShown = status.status == RecommendationSourceStatus.Shown
        val wasError = status.status == RecommendationSourceStatus.Error
        val newRecentRunCount = if (windowExpired) 1 else recentRunCount + 1
        val newRecentShownCount = if (windowExpired) {
            if (wasShown) 1 else 0
        } else {
            recentShownCount + if (wasShown) 1 else 0
        }
        val newRecentErrorCount = if (windowExpired) {
            if (wasError) 1 else 0
        } else {
            recentErrorCount + if (wasError) 1 else 0
        }
        return copy(
            runCount = runCount + 1,
            shownCount = shownCount + if (wasShown) 1 else 0,
            noMatchCount = noMatchCount + if (status.status == RecommendationSourceStatus.NoMatches) 1 else 0,
            filteredOutCount = filteredOutCount + if (status.status == RecommendationSourceStatus.FilteredOut) 1 else 0,
            errorCount = errorCount + if (wasError) 1 else 0,
            hiddenByDuplicateCount = hiddenByDuplicateCount + if (status.status == RecommendationSourceStatus.HiddenByDuplicateHandling) 1 else 0,
            totalVisibleCandidates = totalVisibleCandidates + status.visibleCount,
            lastSuccessAt = if (wasShown) now else lastSuccessAt,
            lastErrorAt = if (wasError) now else lastErrorAt,
            updatedAt = now,
            recentRunCount = newRecentRunCount,
            recentShownCount = newRecentShownCount,
            recentErrorCount = newRecentErrorCount,
            windowStartAt = newWindowStart,
            topPicksContributionCount = topPicksContributionCount + if (isTopPicksContributor) 1 else 0,
        )
    }
    // KMK <--

    companion object {
        const val MIN_RUNS_FOR_LABEL = 3
        // KMK --> v0.7.32: D1 — 30-day sliding window
        private const val RECENT_WINDOW_MS = 30L * 24 * 60 * 60 * 1000
        // KMK <--
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
                "${s.lastSuccessAt}$FIELD_SEP${s.lastErrorAt}$FIELD_SEP${s.updatedAt}$FIELD_SEP" +
                // KMK --> v0.7.32: D1 recent window fields (positions 11-14)
                "${s.recentRunCount}$FIELD_SEP${s.recentShownCount}$FIELD_SEP" +
                "${s.recentErrorCount}$FIELD_SEP${s.windowStartAt}$FIELD_SEP" +
                // KMK --> v0.7.32: D2 top picks contribution count (position 15)
                "${s.topPicksContributionCount}"
            // KMK <--
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
                    // KMK --> v0.7.32: D1 recent window (default 0 for old data without these fields)
                    recentRunCount = if (parts.size > 11) parts[11].toInt() else 0,
                    recentShownCount = if (parts.size > 12) parts[12].toInt() else 0,
                    recentErrorCount = if (parts.size > 13) parts[13].toInt() else 0,
                    windowStartAt = if (parts.size > 14) parts[14].toLong() else 0L,
                    // KMK --> v0.7.32: D2 top picks contribution (default 0 for old data)
                    topPicksContributionCount = if (parts.size > 15) parts[15].toInt() else 0,
                    // KMK <--
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
     * KMK --> v0.7.32: D2 — [topPicksContributors] is the set of source IDs that contributed
     * to the final Top Picks row; those sources get their [SourceFitStats.topPicksContributionCount] incremented.
     */
    fun mergeRun(
        existing: Map<Long, SourceFitStats>,
        runStatuses: Collection<RecommendationSourceRunStatus>,
        // KMK --> v0.7.32: D2
        topPicksContributors: Set<Long> = emptySet(),
        // KMK <--
    ): Map<Long, SourceFitStats> {
        val result = existing.toMutableMap()
        val now = System.currentTimeMillis()
        for (status in runStatuses) {
            if (status.status == RecommendationSourceStatus.Disabled ||
                status.status == RecommendationSourceStatus.OutsideAttemptLimit
            ) {
                continue
            }
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
            result[status.sourceId] = current.merge(
                status,
                now,
                // KMK --> v0.7.32: D2
                isTopPicksContributor = status.sourceId in topPicksContributors,
                // KMK <--
            )
        }
        return result
    }
}
// KMK <--
