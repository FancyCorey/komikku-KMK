package tachiyomi.domain.taste.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.repository.SourceEvaluationRepository

// KMK -->
class GetSourceEvaluations(
    private val repository: SourceEvaluationRepository,
) {
    suspend fun awaitAll(): List<SourceEvaluation> = repository.getAll()

    fun subscribeAll(): Flow<List<SourceEvaluation>> = repository.getAllAsFlow()

    suspend fun awaitByPackage(pkgName: String, signatureHash: String): List<SourceEvaluation> =
        repository.getByPackage(pkgName, signatureHash)
}
// KMK <--
