package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.repository.SourceEvaluationRepository

// KMK -->
class DeleteSourceEvaluation(
    private val repository: SourceEvaluationRepository,
) {
    suspend fun awaitByKey(key: String) = repository.deleteByKey(key)

    suspend fun awaitByPackage(pkgName: String, signatureHash: String) =
        repository.deleteByPackage(pkgName, signatureHash)
}
// KMK <--
