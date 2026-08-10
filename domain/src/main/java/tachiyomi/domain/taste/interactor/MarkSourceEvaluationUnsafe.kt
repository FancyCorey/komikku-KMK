package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.model.SourceEvaluationProbeMarker
import tachiyomi.domain.taste.repository.SourceEvaluationSafetyRepository

// KMK -->
class MarkSourceEvaluationUnsafe(
    private val repository: SourceEvaluationSafetyRepository,
) {
    suspend fun await(marker: SourceEvaluationProbeMarker, reason: String, now: Long) =
        repository.markUnsafeFromProbe(marker, reason, now)
}
// KMK <--
