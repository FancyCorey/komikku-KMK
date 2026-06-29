package exh.recs

// KMK -->
import tachiyomi.domain.manga.model.Manga

/**
 * Accumulates [PersonalRecommendation] results from multiple sources and ranks them for
 * the synthetic "Top Picks" For You row.
 *
 * Primary key: (manga.source, manga.url) — same stable identity used by the taste system.
 *
 * Conservative work-key merging: two candidates from different sources may be treated as the
 * same work if and only if their normalized title matches exactly AND at least one of normalized
 * author or normalized artist also matches exactly and is non-blank. Title-only matches are never
 * merged. When metadata is incomplete or ambiguous, entries stay separate.
 *
 * Ranking signal:
 *   combinedScore = bestScore
 *       + (occurrenceCount - 1) * OCCURRENCE_BONUS
 *       + boostedBonus
 * where bestScore comes from [PersonalRecommendationScorer] and is the primary driver.
 * OCCURRENCE_BONUS and boostedBonus are secondary signals that should not overpower a much
 * stronger personal preference score.
 *
 * This class is not thread-safe; callers must synchronize externally.
 */
internal class CombinedPicksAccumulator {

    internal data class Bucket(
        var manga: Manga, // best representative; updated on work-key merge
        var bestScore: Double,
        var occurrenceCount: Int,
        val matchedGroups: MutableSet<String>,
        val sourceIds: MutableSet<Long>,
    )

    /** Primary buckets keyed by "source:url". */
    private val buckets = LinkedHashMap<String, Bucket>()

    /**
     * Maps a conservative work-key (exact title + exact author or exact title + exact artist)
     * to the primary bucket key first associated with it. Enables cross-source dedup of
     * confirmed duplicates with shared author OR shared artist.
     */
    private val workKeyToPrimaryKey = HashMap<String, String>()

    fun add(
        recommendations: List<PersonalRecommendation>,
        sourceId: Long,
    ) {
        for (rec in recommendations) {
            val rawKey = "${rec.manga.source}:${rec.manga.url}"
            val workKeys = runCatching { conservativeWorkKeys(rec.manga) }.getOrElse { emptySet() }

            // Find canonical primary key via any matching work key (first match wins)
            val effectiveKey = workKeys.firstNotNullOfOrNull { wk -> workKeyToPrimaryKey[wk] } ?: rawKey

            // Register all work keys to the canonical key (putIfAbsent so first registration wins)
            for (wk in workKeys) {
                workKeyToPrimaryKey.putIfAbsent(wk, effectiveKey)
            }

            val existing = buckets[effectiveKey]
            if (existing == null) {
                buckets[effectiveKey] = Bucket(
                    manga = rec.manga,
                    bestScore = rec.score,
                    occurrenceCount = 1,
                    matchedGroups = rec.matchedGroups.toMutableSet(),
                    sourceIds = mutableSetOf(sourceId),
                )
            } else {
                if (rec.score > existing.bestScore) {
                    existing.bestScore = rec.score
                    // Prefer the entry with the higher score as display representative
                    existing.manga = rec.manga
                } else if (rec.score == existing.bestScore && isBetterRepresentative(rec.manga, existing.manga)) {
                    existing.manga = rec.manga
                }
                existing.occurrenceCount++
                existing.matchedGroups.addAll(rec.matchedGroups)
                existing.sourceIds.add(sourceId)
            }
        }
    }

    /**
     * Returns up to [cap] recommendations ranked by combined score.
     * [boostedSourceIds] provides a bonus to candidates that appeared in boosted sources.
     */
    fun rank(boostedSourceIds: Set<Long>, cap: Int): List<PersonalRecommendation> {
        return buckets.values
            .sortedWith(
                compareByDescending<Bucket> { b ->
                    b.bestScore +
                        (b.occurrenceCount - 1) * OCCURRENCE_BONUS +
                        if (b.sourceIds.any { it in boostedSourceIds }) BOOSTED_BONUS else 0.0
                }.thenByDescending { it.occurrenceCount }
                    .thenByDescending { it.matchedGroups.size }
                    .thenByDescending { it.bestScore }
                    .thenBy { it.manga.id },
            )
            .take(cap)
            .map { b -> PersonalRecommendation(b.manga, b.bestScore, b.matchedGroups.toList()) }
    }

    fun clear() {
        buckets.clear()
        workKeyToPrimaryKey.clear()
    }

    fun isEmpty(): Boolean = buckets.isEmpty()

    companion object {
        /** Score bonus added per additional source occurrence beyond the first. */
        const val OCCURRENCE_BONUS = 0.3

        /** Score bonus added when at least one contributing source is in the boosted set. */
        const val BOOSTED_BONUS = 0.2

        /**
         * Returns a set of conservative work-keys for [manga] for cross-source duplicate
         * detection. Each key represents an exact (title, contributor) pair. Returns an empty
         * set when metadata is too weak to safely identify duplicates.
         *
         * Two candidates merge if they share at least one key:
         * - exact normalized title + exact normalized author (when author is non-blank), OR
         * - exact normalized title + exact normalized artist (when artist is non-blank).
         *
         * Title-only matches never produce a key.
         */
        internal fun conservativeWorkKeys(manga: Manga): Set<String> {
            val title = normalizeForDedup(manga.title).ifBlank { return emptySet() }
            val author = normalizeForDedup(manga.author ?: "")
            val artist = normalizeForDedup(manga.artist ?: "")
            val keys = mutableSetOf<String>()
            if (author.isNotBlank()) keys.add("title:$title|author:$author")
            if (artist.isNotBlank()) keys.add("title:$title|artist:$artist")
            return keys
        }

        /**
         * Returns true if [candidate] is a better display representative than [current]
         * when scores are equal. Prefers richer metadata.
         */
        private fun isBetterRepresentative(candidate: Manga, current: Manga): Boolean {
            val candGenres = candidate.genre?.size ?: 0
            val currGenres = current.genre?.size ?: 0
            if (candGenres != currGenres) return candGenres > currGenres
            val candHasAuthor = !candidate.author.isNullOrBlank()
            val currHasAuthor = !current.author.isNullOrBlank()
            if (candHasAuthor != currHasAuthor) return candHasAuthor
            return candidate.id < current.id
        }

        private fun normalizeForDedup(s: String): String =
            s.lowercase()
                .replace(Regex("[^a-z0-9]"), " ")
                .trim()
                .replace(Regex("\\s+"), " ")
    }
}
// KMK <--
