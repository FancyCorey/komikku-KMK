package tachiyomi.data.taste

import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.taste.model.MangaSourceQualitySignal
import tachiyomi.domain.taste.repository.MangaSourceQualitySignalRepository

// KMK --> v0.7.8
class MangaSourceQualitySignalRepositoryImpl(
    private val handler: DatabaseHandler,
) : MangaSourceQualitySignalRepository {

    override suspend fun getAll(): List<MangaSourceQualitySignal> {
        return handler.awaitList {
            manga_source_quality_signalQueries.getAll(signalMapper)
        }
    }

    override suspend fun getByOrigin(originSourceId: Long, originUrl: String): List<MangaSourceQualitySignal> {
        return handler.awaitList {
            manga_source_quality_signalQueries.getByOrigin(originSourceId, originUrl, signalMapper)
        }
    }

    override suspend fun getBySelectedSource(selectedSourceId: Long): List<MangaSourceQualitySignal> {
        return handler.awaitList {
            manga_source_quality_signalQueries.getBySelectedSource(selectedSourceId, signalMapper)
        }
    }

    override suspend fun insert(signal: MangaSourceQualitySignal) {
        handler.await(inTransaction = true) {
            manga_source_quality_signalQueries.insert(
                originSourceId = signal.originSourceId,
                originUrl = signal.originUrl,
                originTitle = signal.originTitle,
                selectedSourceId = signal.selectedSourceId,
                selectedUrl = signal.selectedUrl,
                selectedTitle = signal.selectedTitle,
                selectedSourceName = signal.selectedSourceName,
                comparedCandidatesJson = signal.comparedCandidatesJson,
                chapterNumber = signal.chapterNumber,
                chapterName = signal.chapterName,
                sampleSize = signal.sampleSize.toLong(),
                sampledPagesJson = signal.sampledPagesJson,
                selectedAt = signal.selectedAt,
                qualitySignalVersion = signal.qualitySignalVersion.toLong(),
            )
        }
    }

    override suspend fun deleteById(id: Long) {
        handler.await { manga_source_quality_signalQueries.deleteById(id) }
    }

    override suspend fun deleteAll() {
        handler.await { manga_source_quality_signalQueries.deleteAll() }
    }
}

private val signalMapper = {
        id: Long,
        originSourceId: Long,
        originUrl: String,
        originTitle: String,
        selectedSourceId: Long,
        selectedUrl: String,
        selectedTitle: String,
        selectedSourceName: String,
        comparedCandidatesJson: String,
        chapterNumber: Double?,
        chapterName: String,
        sampleSize: Long,
        sampledPagesJson: String,
        selectedAt: Long,
        qualitySignalVersion: Long,
    ->
    MangaSourceQualitySignal(
        id = id,
        originSourceId = originSourceId,
        originUrl = originUrl,
        originTitle = originTitle,
        selectedSourceId = selectedSourceId,
        selectedUrl = selectedUrl,
        selectedTitle = selectedTitle,
        selectedSourceName = selectedSourceName,
        comparedCandidatesJson = comparedCandidatesJson,
        chapterNumber = chapterNumber,
        chapterName = chapterName,
        sampleSize = sampleSize.toInt(),
        sampledPagesJson = sampledPagesJson,
        selectedAt = selectedAt,
        qualitySignalVersion = qualitySignalVersion.toInt(),
    )
}
// KMK <--
