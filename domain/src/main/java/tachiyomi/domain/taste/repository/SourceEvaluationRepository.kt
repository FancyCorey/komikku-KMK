package tachiyomi.domain.taste.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.taste.model.SourceEvaluation

// KMK -->
interface SourceEvaluationRepository {

    suspend fun getAll(): List<SourceEvaluation>

    fun getAllAsFlow(): Flow<List<SourceEvaluation>>

    suspend fun getByKey(key: String): SourceEvaluation?

    suspend fun getBySourceId(sourceId: Long): SourceEvaluation?

    suspend fun getByPackage(pkgName: String, signatureHash: String): List<SourceEvaluation>

    suspend fun upsert(evaluation: SourceEvaluation)

    suspend fun deleteByKey(key: String)

    suspend fun deleteByPackage(pkgName: String, signatureHash: String)

    // KMK v0.8.19: atomic replacement for extension-level error reconciliation -- deletes existing
    // package/signature rows and inserts the replacement evaluation in one transaction, so a failure
    // partway through can never leave stale rows deleted without their replacement written (or vice
    // versa). See SourceEvaluationRunner.recordExtensionError().
    suspend fun replaceByPackage(
        pkgName: String,
        signatureHash: String,
        evaluation: SourceEvaluation,
    )

    suspend fun deleteAll()
}
// KMK <--
