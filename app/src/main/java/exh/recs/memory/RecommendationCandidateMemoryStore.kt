package exh.recs.memory

// KMK --> v0.7.38: For You candidate discovery memory
import exh.recs.PersonalRecommendation
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.interactor.GetRecommendationCandidateMemory
import tachiyomi.domain.taste.interactor.PruneRecommendationCandidateMemory
import tachiyomi.domain.taste.interactor.UpsertRecommendationCandidateMemory
import tachiyomi.domain.taste.model.RecommendationCandidateMemory

/**
 * Wraps the recommendation candidate memory repository with JSON parse safety and
 * upsert/prune helpers. Used by [BrowsePersonalRecommendationsScreenModel].
 */
class RecommendationCandidateMemoryStore(
    private val getMemory: GetRecommendationCandidateMemory,
    private val upsertMemory: UpsertRecommendationCandidateMemory,
    private val pruneMemory: PruneRecommendationCandidateMemory,
) {
    /** Load all known candidates for [sourceId] and [querySignature], parsed safely. */
    suspend fun loadForSourceQuery(
        sourceId: Long,
        querySignature: String,
    ): List<RecommendationCandidateMemoryEntry> =
        getMemory.awaitBySourceQuery(sourceId, querySignature).map { it.toDomainEntry() }

    /** Load all known candidates for [sourceId] (all queries), parsed safely. */
    suspend fun loadForSource(sourceId: Long): List<RecommendationCandidateMemoryEntry> =
        getMemory.awaitBySource(sourceId).map { it.toDomainEntry() }

    /** Return distinct pages already discovered for this source + query. */
    suspend fun knownPages(sourceId: Long, querySignature: String): Set<Int> =
        getMemory.awaitDistinctPagesBySourceQuery(sourceId, querySignature)

    /**
     * Upsert a batch of scored recommendations into memory.
     * Each entry is keyed on (sourceId, url). Scores and reasons are updated on conflict.
     */
    suspend fun upsertBatch(
        sourceId: Long,
        querySignature: String,
        queryTags: List<String>,
        queryStrategy: String?,
        page: Int,
        profileFingerprint: String,
        recommendations: List<PersonalRecommendation>,
    ) {
        val now = System.currentTimeMillis()
        for (rec in recommendations) {
            val entry = RecommendationCandidateMemory(
                sourceId = sourceId,
                url = rec.manga.url,
                mangaId = if (rec.manga.id != 0L) rec.manga.id else null,
                title = rec.manga.title,
                thumbnailUrl = rec.manga.thumbnailUrl,
                normalizedTitle = normalizeTitle(rec.manga.title),
                lastScore = rec.score,
                matchedGroupsJson = rec.matchedGroups.joinToString(",").ifBlank { null },
                resultReasonsJson = null,
                querySignature = querySignature,
                queryTagsJson = queryTags.joinToString(","),
                queryStrategy = queryStrategy,
                page = page,
                discoveredAt = now,
                lastScoredAt = now,
                lastSeenAt = now,
                profileFingerprint = profileFingerprint,
                filteredReason = null,
            )
            runCatching { upsertMemory.await(entry) }
        }
    }

    /** Upsert a single resolved manga candidate that was filtered out. */
    suspend fun upsertFiltered(
        sourceId: Long,
        manga: Manga,
        querySignature: String,
        queryTags: List<String>,
        queryStrategy: String?,
        page: Int,
        profileFingerprint: String,
        filteredReason: String,
    ) {
        val now = System.currentTimeMillis()
        val entry = RecommendationCandidateMemory(
            sourceId = sourceId,
            url = manga.url,
            mangaId = if (manga.id != 0L) manga.id else null,
            title = manga.title,
            thumbnailUrl = manga.thumbnailUrl,
            normalizedTitle = normalizeTitle(manga.title),
            lastScore = 0.0,
            matchedGroupsJson = null,
            resultReasonsJson = null,
            querySignature = querySignature,
            queryTagsJson = queryTags.joinToString(","),
            queryStrategy = queryStrategy,
            page = page,
            discoveredAt = now,
            lastScoredAt = now,
            lastSeenAt = now,
            profileFingerprint = profileFingerprint,
            filteredReason = filteredReason,
        )
        runCatching { upsertMemory.await(entry) }
    }

    /** Prune stored candidates for [sourceId] if over the cap. */
    suspend fun pruneIfNeeded(sourceId: Long) {
        runCatching { pruneMemory.awaitIfNeeded(sourceId) }
    }

    companion object {
        /** Normalize title for cross-source dedup keying. Matches logic in BrowsePersonalRecommendationsScreenModel. */
        fun normalizeTitle(title: String): String =
            title.lowercase()
                .replace(Regex("""\([^)]*\)|\[[^\]]*\]"""), "")
                .replace(Regex("[^a-z0-9]"), " ")
                .trim()
                .replace(Regex("\\s+"), " ")

        /** Safely parse a comma-separated JSON-lite list (used for matchedGroups and queryTags). */
        fun parseCommaList(json: String?): List<String> =
            if (json.isNullOrBlank()) {
                emptyList()
            } else {
                json.split(",").map { it.trim() }.filter { it.isNotBlank() }
            }
    }
}

private fun RecommendationCandidateMemory.toDomainEntry(): RecommendationCandidateMemoryEntry =
    RecommendationCandidateMemoryEntry(
        sourceId = sourceId,
        url = url,
        mangaId = mangaId,
        title = title,
        normalizedTitle = normalizedTitle,
        lastScore = lastScore,
        matchedGroups = RecommendationCandidateMemoryStore.parseCommaList(matchedGroupsJson),
        resultReasons = RecommendationCandidateMemoryStore.parseCommaList(resultReasonsJson),
        querySignature = querySignature,
        queryTags = RecommendationCandidateMemoryStore.parseCommaList(queryTagsJson),
        queryStrategy = queryStrategy,
        page = page,
        discoveredAt = discoveredAt,
        lastScoredAt = lastScoredAt,
        lastSeenAt = lastSeenAt,
    )
// KMK <--
