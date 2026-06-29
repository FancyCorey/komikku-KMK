package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.model.SourceEvaluationProbeMarker
import tachiyomi.domain.taste.repository.SourceEvaluationSafetyRepository

// KMK -->
class GetSourceEvaluationProbeMarker(
    private val repository: SourceEvaluationSafetyRepository,
) {
    suspend fun await(): SourceEvaluationProbeMarker? = repository.getProbeMarker()
}
// KMK <--
