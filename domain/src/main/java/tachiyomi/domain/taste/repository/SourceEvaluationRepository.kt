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

    suspend fun deleteAll()
}
// KMK <--
