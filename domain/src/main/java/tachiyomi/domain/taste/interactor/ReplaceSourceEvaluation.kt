package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.repository.SourceEvaluationRepository

// KMK -->
class ReplaceSourceEvaluation(
    private val repository: SourceEvaluationRepository,
) {
    suspend fun await(
        pkgName: String,
        signatureHash: String,
        evaluation: SourceEvaluation,
    ) = repository.replaceByPackage(pkgName, signatureHash, evaluation)
}
// KMK <--
