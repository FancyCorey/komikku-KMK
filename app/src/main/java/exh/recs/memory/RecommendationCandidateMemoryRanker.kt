package exh.recs.memory

// KMK --> v0.7.38: For You candidate discovery memory
import exh.recs.CandidateVisibility
import exh.recs.MangaTasteKey
import exh.recs.PersonalRecommendation
import exh.recs.PersonalRecommendationScorer
import exh.recs.RecommendationCandidateVisibilityPolicy
import exh.recs.SeenMangaKey
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.model.MangaTaste
import tachiyomi.domain.taste.model.RatedMangaVisibility
import tachiyomi.domain.taste.model.TasteProfile

/**
 * Pure merge-and-rank helper for For You candidate discovery memory.
 *
 * Takes remembered [Manga] candidates (with their stored entries) plus newly discovered
 * [PersonalRecommendation] from a live source search, applies the current profile + filters
 * to all, and returns the best candidates up to [limit].
 *
 * Remembered candidates that no longer pass filters are excluded but NOT deleted from memory —
 * the store may keep them for future refreshes where conditions change.
 */
object RecommendationCandidateMemoryRanker {

    /**
     * Merge remembered local manga + new search results, re-score all with [profile],
     * apply filters, and return the best [limit] results.
     *
     * [remembered]: pairs of (resolvedManga, memoryEntry) — manga already resolved from DB.
     * [newResults]: freshly scored candidates from a live source search.
     * The merged set is deduped by local manga id before scoring.
     */
    internal fun merge(
        remembered: List<Pair<Manga, RecommendationCandidateMemoryEntry>>,
        newResults: List<PersonalRecommendation>,
        profile: TasteProfile,
        aliasMap: Map<String, String>,
        tasteByKey: Map<MangaTasteKey, MangaTaste>,
        visibility: RatedMangaVisibility,
        seenKeys: Set<SeenMangaKey>,
        knownIds: Set<Long>,
        limit: Int,
        // KMK --> v0.7.41: min-chapter context so memory candidates obey the same policy as live/cache
        minChapterCount: Int = 0,
        chapterCounts: Map<Long, Long> = emptyMap(),
        // KMK <--
    ): List<PersonalRecommendation> {
        // Build deduped candidate set: local id → (manga, source)
        val candidateById = mutableMapOf<Long, Pair<Manga, Long>>()

        for ((manga, entry) in remembered) {
            if (manga.id == 0L) continue
            candidateById[manga.id] = manga to entry.sourceId
        }
        for (rec in newResults) {
            if (rec.manga.id == 0L) continue
            candidateById[rec.manga.id] = rec.manga to rec.manga.source
        }

        // Score and filter all unique candidates. Visibility is decided by the single shared policy
        // (v0.7.41) so memory-ranked results cannot reappear past favorite/rated/seen/known/min-chapter.
        val scored = candidateById.values.mapNotNull { (manga, _) ->
            val visible = RecommendationCandidateVisibilityPolicy.evaluate(
                manga = manga,
                tasteByKey = tasteByKey,
                visibility = visibility,
                seenKeys = seenKeys,
                knownIds = knownIds,
                minChapterCount = minChapterCount,
                chapterCounts = chapterCounts,
            ) == CandidateVisibility.VISIBLE
            if (!visible) return@mapNotNull null

            val result = PersonalRecommendationScorer.score(manga, profile, aliasMap)
            // KMK v0.8.13: require positive taste evidence, the same gate
            // PersonalRecommendationScorer.rankCandidates() applies by default -- confirmed on a live
            // device: memory rows existed with a positive score and empty/null matchedGroups, purely
            // from source affinity. Without this, a stale memory row created under the old looser
            // rule (or a manga whose only positive signal is source affinity) could keep competing
            // for display forever, since memory rows are never deleted, only re-filtered here on
            // every merge.
            if (result.blocked || result.score <= 0.0 || result.matchedGroups.isEmpty()) return@mapNotNull null

            PersonalRecommendation(
                manga = manga,
                score = result.score,
                matchedGroups = result.matchedGroups,
            )
        }

        return scored.sortedByDescending { it.score }.take(limit)
    }
}
// KMK <--
