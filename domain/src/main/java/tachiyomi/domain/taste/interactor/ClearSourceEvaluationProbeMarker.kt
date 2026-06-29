package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.repository.SourceEvaluationSafetyRepository

// KMK -->
class ClearSourceEvaluationProbeMarker(
    private val repository: SourceEvaluationSafetyRepository,
) {
    suspend fun await() = repository.clearProbeMarker()
}
// KMK <--
