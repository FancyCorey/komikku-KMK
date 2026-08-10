package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.repository.SourceEvaluationSafetyRepository

// KMK -->
class ClearSourceEvaluationUnsafe(
    private val repository: SourceEvaluationSafetyRepository,
) {
    suspend fun await() = repository.clearUnsafe()
}
// KMK <--
