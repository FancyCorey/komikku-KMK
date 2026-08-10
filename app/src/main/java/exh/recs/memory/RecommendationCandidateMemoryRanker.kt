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
        // KMK: optional bounded soft
        // reordering by local exposure history, applied AFTER scoring/filtering but BEFORE the
        // `take(limit)` cap below -- this is what lets a less-exposed candidate be promoted into a
        // visible slot; reranking after the cap could never do that, since a candidate cut by the cap
        // is already gone. Defaults to a no-op (empty map -> the pre-existing plain score sort),
        // so every caller that does not pass exposure data keeps its exact original behavior.
        // KMK: keyed by the full (sourceId, url) identity.
        // A url-only map was unsafe here specifically because this function deliberately mixes
        // remembered candidates (which carry their own originating `entry.sourceId`) with freshly
        // searched ones, so two sources sharing a relative url could cross-penalise each other.
        exposureByKey: Map<
            exh.recs.RecommendationDisplayReranker.ExposureKey,
            exh.recs.RecommendationDisplayReranker.ExposureSummary,
            > = emptyMap(),
        interactions: exh.recs.RecommendationDisplayReranker.InteractionSignals =
            exh.recs.RecommendationDisplayReranker.InteractionSignals.NONE,
        exposureNow: Long = 0L,
        exposureWindowDays: Int = exh.recs.RecommendationExposurePolicy.DEFAULT_WINDOW_DAYS,
    ): List<PersonalRecommendation> {
        // Build deduped candidate set: local id → (manga, source, lane)
        val candidateById = mutableMapOf<Long, Triple<Manga, Long, exh.recs.RecommendationDiscoveryLane>>()

        for ((manga, entry) in remembered) {
            if (manga.id == 0L) continue
            // A remembered row's lane is recovered from the free-form queryStrategy column it was
            // written with; an unknown/legacy/missing value is PERSONALIZED, never Latest.
            val lane = exh.recs.RecommendationDiscoveryLane.fromStorageKey(entry.queryStrategy)
                ?: exh.recs.RecommendationDiscoveryLane.PERSONALIZED
            candidateById[manga.id] = Triple(manga, entry.sourceId, lane)
        }
        for (rec in newResults) {
            if (rec.manga.id == 0L) continue
            // A fresh result supersedes a remembered row for the same manga, including its lane.
            candidateById[rec.manga.id] = Triple(rec.manga, rec.manga.source, rec.lane)
        }

        // Score and filter all unique candidates. Visibility is decided by the single shared policy
        // (v0.7.41) so memory-ranked results cannot reappear past favorite/rated/seen/known/min-chapter.
        // Retains each accepted candidate's originating source id so exposure identity stays
        // (sourceId, url) rather than collapsing to url.
        val sourceIdByMangaId = HashMap<Long, Long>(candidateById.size)
        val scored = candidateById.values.mapNotNull { (manga, candidateSourceId, lane) ->
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

            sourceIdByMangaId[manga.id] = candidateSourceId
            PersonalRecommendation(
                manga = manga,
                score = result.score,
                matchedGroups = result.matchedGroups,
                lane = lane,
            )
        }

        val ordered = scored.sortedByDescending { it.score }
        // KMK: the reranker only permutes
        // -- it never mutates PersonalRecommendation.score and never drops a candidate -- so applying
        // it before the cap is safe even when exposureByKey is empty (identity permutation), and it
        // is the only placement that can promote a less-exposed candidate into a visible slot.
        val reranked = if (exposureByKey.isEmpty()) {
            ordered
        } else {
            exh.recs.RecommendationDisplayReranker.rerank(
                candidates = ordered,
                exposureByKey = exposureByKey,
                interactions = interactions,
                now = exposureNow,
                windowDays = exposureWindowDays,
                keyOf = { rec ->
                    exh.recs.RecommendationDisplayReranker.ExposureKey(
                        sourceIdByMangaId[rec.manga.id] ?: rec.manga.source,
                        rec.manga.url,
                    )
                },
            )
        }
        // KMK: the cap itself now enforces the
        // personalized-majority invariant on the realised per-lane counts, instead of relying on the
        // input quota arithmetic (which does not hold when the personalized lane is sparse).
        return exh.recs.RecommendationLatestBudgetPolicy.enforcePersonalizedMajority(reranked, limit)
    }
}
// KMK <--
