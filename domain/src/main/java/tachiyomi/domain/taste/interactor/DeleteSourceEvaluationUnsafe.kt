package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.repository.SourceEvaluationSafetyRepository

// KMK -->
class DeleteSourceEvaluationUnsafe(
    private val repository: SourceEvaluationSafetyRepository,
) {
    suspend fun await(key: String) = repository.deleteUnsafe(key)
}
// KMK <--
