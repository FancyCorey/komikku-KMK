package exh.recs.matching

import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.tracker.model.LocalTrackedWorkSource
import tachiyomi.domain.tracker.model.LocalTrackedWorkSourceConfirmation
import tachiyomi.domain.tracker.repository.LocalTrackerRepository
import java.util.Locale

/**
 * Attaches confirmed versions to an existing local-tracking work without changing an existing
 * target work. A rating or other group action may use this only after the user enabled local
 * propagation; unconfirmed candidates must stay outside this owner.
 */
class ConfirmedGroupLocalTrackingPropagator(
    private val repository: LocalTrackerRepository,
) {
    suspend fun propagateIfAnyTracked(targets: List<Manga>): Boolean {
        val resolved = targets.distinctBy { it.source to it.url }
        val existingWorkIds = resolved.mapNotNull { manga ->
            repository.getWorkIdBySourceUrl(manga.source, manga.url)
        }.distinct()
        // The oldest work owns the canonical identity; target ordering must not affect it.
        val existing = existingWorkIds.mapNotNull { repository.getWork(it) }
            .minWithOrNull(compareBy<tachiyomi.domain.tracker.model.LocalTrackedWork> { it.createdAt }.thenBy { it.id })
            ?: return false

        val now = System.currentTimeMillis()
        val sharedWorkId = existing.id
        val duplicateWorkIds = existingWorkIds.filter { it != sharedWorkId }
        duplicateWorkIds.forEach { duplicateWorkId ->
            repository.consolidateWork(sharedWorkId, duplicateWorkId)
        }
        for (manga in resolved) {
            val existingId = repository.getWorkIdBySourceUrl(manga.source, manga.url)
            if (existingId != null) continue
            if (repository.getWork(sharedWorkId) == null) {
                repository.upsertWork(
                    existing.copy(
                        id = sharedWorkId,
                        title = manga.title,
                        normalizedTitle = manga.title.trim().lowercase(Locale.ROOT),
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
            val work = repository.getWork(sharedWorkId) ?: continue
            repository.upsertSource(
                LocalTrackedWorkSource(
                    workId = work.id,
                    source = manga.source,
                    url = manga.url,
                    title = manga.title,
                    confidence = 100,
                    confirmation = LocalTrackedWorkSourceConfirmation.USER_CONFIRMED,
                    createdAt = work.createdAt,
                    updatedAt = now,
                ),
            )
        }
        return true
    }
}
