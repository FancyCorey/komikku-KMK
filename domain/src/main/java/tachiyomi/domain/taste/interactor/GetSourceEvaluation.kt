package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.repository.SourceEvaluationRepository

// KMK -->
class GetSourceEvaluation(
    private val repository: SourceEvaluationRepository,
) {
    suspend fun await(key: String): SourceEvaluation? = repository.getByKey(key)

    suspend fun awaitBySourceId(sourceId: Long): SourceEvaluation? = repository.getBySourceId(sourceId)
}
// KMK <--
