package tachiyomi.domain.taste.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.taste.model.SourceEvaluationProbeMarker
import tachiyomi.domain.taste.model.SourceEvaluationUnsafeSource

// KMK -->
interface SourceEvaluationSafetyRepository {

    fun getUnsafeAsFlow(): Flow<List<SourceEvaluationUnsafeSource>>

    suspend fun getUnsafe(): List<SourceEvaluationUnsafeSource>

    suspend fun getProbeMarker(): SourceEvaluationProbeMarker?

    suspend fun upsertProbeMarker(marker: SourceEvaluationProbeMarker)

    suspend fun clearProbeMarker()

    suspend fun markUnsafeFromProbe(marker: SourceEvaluationProbeMarker, reason: String, now: Long)

    suspend fun deleteUnsafe(key: String)

    suspend fun clearUnsafe()
}
// KMK <--
