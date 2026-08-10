package tachiyomi.domain.taste.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.taste.model.SourceEvaluationUnsafeSource
import tachiyomi.domain.taste.repository.SourceEvaluationSafetyRepository

// KMK -->
class GetSourceEvaluationUnsafeSources(
    private val repository: SourceEvaluationSafetyRepository,
) {
    fun subscribeAll(): Flow<List<SourceEvaluationUnsafeSource>> = repository.getUnsafeAsFlow()

    suspend fun awaitAll(): List<SourceEvaluationUnsafeSource> = repository.getUnsafe()
}
// KMK <--
