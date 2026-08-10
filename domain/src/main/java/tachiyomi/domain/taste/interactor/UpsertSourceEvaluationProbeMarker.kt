package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.model.SourceEvaluationProbeMarker
import tachiyomi.domain.taste.repository.SourceEvaluationSafetyRepository

// KMK -->
class UpsertSourceEvaluationProbeMarker(
    private val repository: SourceEvaluationSafetyRepository,
) {
    suspend fun await(marker: SourceEvaluationProbeMarker) = repository.upsertProbeMarker(marker)
}
// KMK <--
