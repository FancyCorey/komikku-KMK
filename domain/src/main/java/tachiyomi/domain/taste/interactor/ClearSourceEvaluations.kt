package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.repository.SourceEvaluationRepository

// KMK -->
class ClearSourceEvaluations(
    private val repository: SourceEvaluationRepository,
) {
    suspend fun await() = repository.deleteAll()
}
// KMK <--
