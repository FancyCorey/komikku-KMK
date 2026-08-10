package tachiyomi.data.taste

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.taste.model.SourceEvaluationProbeMarker
import tachiyomi.domain.taste.model.SourceEvaluationUnsafeKeys
import tachiyomi.domain.taste.model.SourceEvaluationUnsafeSource
import tachiyomi.domain.taste.repository.SourceEvaluationSafetyRepository

// KMK -->
class SourceEvaluationSafetyRepositoryImpl(
    private val handler: DatabaseHandler,
) : SourceEvaluationSafetyRepository {

    override fun getUnsafeAsFlow(): Flow<List<SourceEvaluationUnsafeSource>> {
        return handler.subscribeToList {
            source_evaluation_unsafe_sourceQueries.getAllAsFlow(unsafeSourceMapper)
        }
    }

    override suspend fun getUnsafe(): List<SourceEvaluationUnsafeSource> {
        return handler.awaitList {
            source_evaluation_unsafe_sourceQueries.getAll(unsafeSourceMapper)
        }
    }

    override suspend fun getProbeMarker(): SourceEvaluationProbeMarker? {
        return handler.awaitOneOrNull {
            source_evaluation_probe_markerQueries.get(probeMarkerMapper)
        }
    }

    override suspend fun upsertProbeMarker(marker: SourceEvaluationProbeMarker) {
        handler.await(inTransaction = false) {
            source_evaluation_probe_markerQueries.upsert(
                evaluationKey = marker.evaluationKey,
                extensionPkgName = marker.extensionPkgName,
                signatureHash = marker.signatureHash,
                extensionName = marker.extensionName,
                sourceId = marker.sourceId,
                sourceName = marker.sourceName,
                lang = marker.lang,
                phase = marker.phase,
                startedAt = marker.startedAt,
                updatedAt = marker.updatedAt,
                batchId = marker.batchId,
            )
        }
    }

    override suspend fun clearProbeMarker() {
        handler.await { source_evaluation_probe_markerQueries.clear() }
    }

    override suspend fun markUnsafeFromProbe(
        marker: SourceEvaluationProbeMarker,
        reason: String,
        now: Long,
    ) {
        val unsafeKey = SourceEvaluationUnsafeKeys.build(
            signatureHash = marker.signatureHash,
            pkgName = marker.extensionPkgName,
            sourceId = marker.sourceId,
        )
        handler.await(inTransaction = true) {
            source_evaluation_unsafe_sourceQueries.upsertCrash(
                unsafeKey = unsafeKey,
                evaluationKey = marker.evaluationKey,
                extensionPkgName = marker.extensionPkgName,
                signatureHash = marker.signatureHash,
                extensionName = marker.extensionName,
                sourceId = marker.sourceId,
                sourceName = marker.sourceName,
                lang = marker.lang,
                phase = marker.phase,
                reason = reason,
                now = now,
                batchId = marker.batchId,
            )
        }
    }

    override suspend fun deleteUnsafe(key: String) {
        handler.await { source_evaluation_unsafe_sourceQueries.deleteByKey(key) }
    }

    override suspend fun clearUnsafe() {
        handler.await { source_evaluation_unsafe_sourceQueries.deleteAll() }
    }
}

private val probeMarkerMapper = {
        _: Long,
        evaluationKey: String?,
        extensionPkgName: String,
        signatureHash: String,
        extensionName: String,
        sourceId: Long?,
        sourceName: String?,
        lang: String?,
        phase: String,
        startedAt: Long,
        updatedAt: Long,
        batchId: String?,
    ->
    SourceEvaluationProbeMarker(
        evaluationKey = evaluationKey,
        extensionPkgName = extensionPkgName,
        signatureHash = signatureHash,
        extensionName = extensionName,
        sourceId = sourceId,
        sourceName = sourceName,
        lang = lang,
        phase = phase,
        startedAt = startedAt,
        updatedAt = updatedAt,
        batchId = batchId,
    )
}

private val unsafeSourceMapper = {
        unsafeKey: String,
        evaluationKey: String?,
        extensionPkgName: String,
        signatureHash: String,
        extensionName: String,
        sourceId: Long?,
        sourceName: String?,
        lang: String?,
        phase: String,
        reason: String,
        crashCount: Long,
        firstSeenAt: Long,
        lastSeenAt: Long,
        lastBatchId: String?,
    ->
    SourceEvaluationUnsafeSource(
        unsafeKey = unsafeKey,
        evaluationKey = evaluationKey,
        extensionPkgName = extensionPkgName,
        signatureHash = signatureHash,
        extensionName = extensionName,
        sourceId = sourceId,
        sourceName = sourceName,
        lang = lang,
        phase = phase,
        reason = reason,
        crashCount = crashCount.toInt(),
        firstSeenAt = firstSeenAt,
        lastSeenAt = lastSeenAt,
        lastBatchId = lastBatchId,
    )
}
// KMK <--
