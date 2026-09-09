package tachiyomi.domain.taste.repository

import tachiyomi.domain.taste.model.RecommendationExposure

interface RecommendationExposureRepository {

    suspend fun getBySourceUrls(keys: List<Pair<Long, String>>): List<RecommendationExposure>

    suspend fun getBySource(sourceId: Long): List<RecommendationExposure>

    /** Atomically increments the exposure count (or creates a first-sighting row) for each key. */
    suspend fun recordExposureBatch(keys: List<Pair<Long, String>>, mangaIds: Map<Pair<Long, String>, Long?>, timestamp: Long)

    /** Deletes exposure rows whose `last_exposed_at` is older than [cutoff]. Ordering history only -- never touches ratings/library/manga. */
    suspend fun pruneOlderThan(cutoff: Long)

    suspend fun deleteBySource(sourceId: Long)

    suspend fun deleteAll()
}
// KMK <--
