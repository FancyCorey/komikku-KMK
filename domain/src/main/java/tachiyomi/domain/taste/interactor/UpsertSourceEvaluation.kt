package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.repository.SourceEvaluationRepository

// KMK -->
class UpsertSourceEvaluation(
    private val repository: SourceEvaluationRepository,
) {
    suspend fun await(evaluation: SourceEvaluation) = repository.upsert(evaluation)
}
// KMK <--
