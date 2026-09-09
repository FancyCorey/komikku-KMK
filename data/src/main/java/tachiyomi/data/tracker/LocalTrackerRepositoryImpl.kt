package tachiyomi.data.tracker

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.tracker.model.LocalTrackedWork
import tachiyomi.domain.tracker.model.LocalTrackedWorkList
import tachiyomi.domain.tracker.model.LocalTrackedWorkSource
import tachiyomi.domain.tracker.model.LocalTrackedWorkSourceConfirmation
import tachiyomi.domain.tracker.model.LocalTrackedWorkSourceProgress
import tachiyomi.domain.tracker.model.LocalTrackedWorkStatus
import tachiyomi.domain.tracker.repository.LocalTrackerRepository

class LocalTrackerRepositoryImpl(
    private val handler: DatabaseHandler,
) : LocalTrackerRepository {
    override fun getAllWorksAsFlow(): Flow<List<LocalTrackedWork>> = handler.subscribeToList {
        local_trackerQueries.getAllWorks(localTrackedWorkMapper)
    }

    override suspend fun getWork(id: String): LocalTrackedWork? = handler.awaitOneOrNull {
        local_trackerQueries.getWork(id, localTrackedWorkMapper)
    }

    override suspend fun upsertWork(work: LocalTrackedWork) {
        require(work.id.isNotBlank())
        require(work.title.isNotBlank())
        require(work.normalizedTitle.isNotBlank())
        require(work.createdAt > 0L && work.updatedAt >= work.createdAt)
        handler.await {
            local_trackerQueries.upsertWork(
                id = work.id,
                title = work.title,
                normalizedTitle = work.normalizedTitle,
                status = work.status.name,
                lastChapterSource = work.lastChapterSource,
                lastChapterNumber = work.lastChapterNumber,
                lastChapterUrl = work.lastChapterUrl,
                lastChapterLabel = work.lastChapterLabel,
                lastProgressAt = work.lastProgressAt,
                score = work.score,
                startDate = work.startDate,
                finishDate = work.finishDate,
                createdAt = work.createdAt,
                updatedAt = work.updatedAt,
            )
        }
    }

    override suspend fun deleteWork(id: String) {
        handler.await(inTransaction = true) {
            // Keep deletion deterministic even when a test/restore SQLite driver does not enable
            // foreign-key enforcement; the local tracker owns all three tables together.
            local_trackerQueries.deleteSources(id)
            local_trackerQueries.deleteWorkSourceProgress(id)
            local_trackerQueries.deleteLists(id)
            local_trackerQueries.deleteWork(id)
        }
    }

    override suspend fun consolidateWork(canonicalId: String, duplicateId: String) {
        require(canonicalId.isNotBlank() && duplicateId.isNotBlank() && canonicalId != duplicateId)
        handler.await(inTransaction = true) {
            val canonical = local_trackerQueries.getWork(canonicalId, localTrackedWorkMapper).executeAsOneOrNull()
                ?: return@await
            val duplicate = local_trackerQueries.getWork(duplicateId, localTrackedWorkMapper).executeAsOneOrNull()
                ?: return@await
            val duplicateSources = local_trackerQueries.getSources(duplicateId, localTrackedWorkSourceMapper).executeAsList()

            duplicateSources.forEach { source ->
                val canonicalWorkId = local_trackerQueries
                    .getWorkIdBySourceUrl(source.source, source.url)
                    .executeAsOneOrNull()
                val progress = local_trackerQueries
                    .getSourceProgress(duplicateId, source.source, source.url, localTrackedWorkSourceProgressMapper)
                    .executeAsOneOrNull()
                val canonicalProgress = if (canonicalWorkId == canonicalId) {
                    local_trackerQueries
                        .getSourceProgress(canonicalId, source.source, source.url, localTrackedWorkSourceProgressMapper)
                        .executeAsOneOrNull()
                } else {
                    null
                }

                // The global identity index prevents attaching the copied row while the
                // duplicate still owns it. Snapshot dependents, move the source, then restore
                // the best progress row; the surrounding transaction provides rollback.
                local_trackerQueries.deleteSourceProgress(duplicateId, source.source, source.url)
                local_trackerQueries.deleteSource(duplicateId, source.source, source.url)
                if (canonicalWorkId != canonicalId) {
                    local_trackerQueries.upsertSource(
                        workId = canonicalId,
                        source = source.source,
                        url = source.url,
                        title = source.title,
                        confidence = source.confidence.toLong(),
                        confirmation = source.confirmation.name,
                        inheritanceOptedOut = if (source.inheritanceOptedOut) 1L else 0L,
                        createdAt = source.createdAt,
                        updatedAt = source.updatedAt,
                    )
                } else if (canonicalWorkId == canonicalId && source.inheritanceOptedOut) {
                    // An opt-out is user intent; never lose it while folding duplicate rows.
                    local_trackerQueries.setInheritanceOptedOut(
                        optedOut = 1L,
                        updatedAt = maxOf(source.updatedAt, canonical.updatedAt),
                        workId = canonicalId,
                        source = source.source,
                        url = source.url,
                    )
                }

                val progressShouldWin = progress != null && (
                    canonicalProgress == null ||
                        compareProgress(progress, canonicalProgress) > 0
                    )
                if (progressShouldWin) {
                    local_trackerQueries.upsertSourceProgress(
                        workId = canonicalId,
                        source = progress.source,
                        url = progress.url,
                        chapterNumber = progress.chapterNumber,
                        chapterUrl = progress.chapterUrl,
                        chapterLabel = progress.chapterLabel,
                        progressAt = progress.progressAt,
                        inheritedFromSource = progress.inheritedFromSource,
                        inheritedFromUrl = progress.inheritedFromUrl,
                        updatedAt = progress.updatedAt,
                    )
                }
            }

            local_trackerQueries.getListEntries(duplicateId).executeAsList().forEach { entry ->
                local_trackerQueries.insertList(
                    workId = canonicalId,
                    listName = entry.list_name,
                    normalizedName = entry.list_name.lowercase(),
                    createdAt = entry.created_at,
                )
            }

            val latest = listOf(canonical, duplicate).maxBy { it.updatedAt }
            val latestProgress = listOf(canonical, duplicate)
                .filter { it.lastProgressAt != null }
                .maxWithOrNull(compareBy({ it.lastChapterNumber ?: Double.NEGATIVE_INFINITY }, { it.lastProgressAt!! }))
            val merged = latest.copy(
                id = canonicalId,
                lastChapterSource = latestProgress?.lastChapterSource,
                lastChapterNumber = latestProgress?.lastChapterNumber,
                lastChapterUrl = latestProgress?.lastChapterUrl,
                lastChapterLabel = latestProgress?.lastChapterLabel,
                lastProgressAt = latestProgress?.lastProgressAt,
                score = listOf(canonical, duplicate).filter { it.score != null }
                    .maxByOrNull { it.updatedAt }?.score,
                startDate = listOfNotNull(canonical.startDate, duplicate.startDate).minOrNull(),
                finishDate = listOfNotNull(canonical.finishDate, duplicate.finishDate).maxOrNull(),
                createdAt = minOf(canonical.createdAt, duplicate.createdAt),
                updatedAt = maxOf(canonical.updatedAt, duplicate.updatedAt),
            )
            local_trackerQueries.upsertWork(
                id = merged.id,
                title = merged.title,
                normalizedTitle = merged.normalizedTitle,
                status = merged.status.name,
                lastChapterSource = merged.lastChapterSource,
                lastChapterUrl = merged.lastChapterUrl,
                lastChapterNumber = merged.lastChapterNumber,
                lastChapterLabel = merged.lastChapterLabel,
                lastProgressAt = merged.lastProgressAt,
                score = merged.score,
                startDate = merged.startDate,
                finishDate = merged.finishDate,
                createdAt = merged.createdAt,
                updatedAt = merged.updatedAt,
            )
            local_trackerQueries.deleteSources(duplicateId)
            local_trackerQueries.deleteWorkSourceProgress(duplicateId)
            local_trackerQueries.deleteLists(duplicateId)
            local_trackerQueries.deleteWork(duplicateId)
        }
    }

    override suspend fun getSources(workId: String): List<LocalTrackedWorkSource> = handler.awaitList {
        local_trackerQueries.getSources(workId, localTrackedWorkSourceMapper)
    }

    override suspend fun getWorkIdBySourceUrl(source: Long, url: String): String? = handler.awaitOneOrNull {
        local_trackerQueries.getWorkIdBySourceUrl(source, url)
    }

    override fun observeWorkIdBySourceUrl(source: Long, url: String): Flow<String?> =
        handler.subscribeToOneOrNull {
            local_trackerQueries.getWorkIdBySourceUrl(source, url)
        }

    override suspend fun recordProgress(
        workId: String,
        source: Long,
        chapterNumber: Double?,
        chapterUrl: String,
        chapterLabel: String,
        progressAt: Long,
    ) {
        require(workId.isNotBlank())
        require(source > 0L)
        require(chapterUrl.isNotBlank())
        require(chapterLabel.isNotBlank())
        require(progressAt > 0L)
        handler.await {
            local_trackerQueries.recordProgress(
                workId = workId,
                source = source,
                chapterNumber = chapterNumber,
                chapterUrl = chapterUrl,
                chapterLabel = chapterLabel,
                progressAt = progressAt,
            )
        }
    }

    override suspend fun upsertSource(source: LocalTrackedWorkSource) {
        require(source.workId.isNotBlank())
        require(source.source > 0L && source.url.isNotBlank() && source.title.isNotBlank())
        require(source.confidence in 0..100)
        require(source.createdAt > 0L && source.updatedAt >= source.createdAt)
        handler.await {
            local_trackerQueries.upsertSource(
                workId = source.workId,
                source = source.source,
                url = source.url,
                title = source.title,
                confidence = source.confidence.toLong(),
                confirmation = source.confirmation.name,
                inheritanceOptedOut = if (source.inheritanceOptedOut) 1L else 0L,
                createdAt = source.createdAt,
                updatedAt = source.updatedAt,
            )
        }
    }

    override suspend fun migrateSourceRelationship(
        source: LocalTrackedWorkSource,
        progress: LocalTrackedWorkSourceProgress?,
        work: LocalTrackedWork?,
    ) {
        handler.await(inTransaction = true) {
            work?.let { upsertWork(it) }
            upsertSource(source)
            progress?.let { upsertSourceProgress(it) }
        }
    }

    override suspend fun deleteSource(workId: String, source: Long, url: String) {
        handler.await(inTransaction = true) {
            local_trackerQueries.deleteSourceProgress(workId, source, url)
            local_trackerQueries.deleteSource(workId, source, url)
        }
    }

    override suspend fun getSourceProgress(workId: String, source: Long, url: String): LocalTrackedWorkSourceProgress? =
        handler.awaitOneOrNull { local_trackerQueries.getSourceProgress(workId, source, url, localTrackedWorkSourceProgressMapper) }

    override suspend fun upsertSourceProgress(progress: LocalTrackedWorkSourceProgress) {
        require(progress.workId.isNotBlank())
        require(progress.source > 0L && progress.url.isNotBlank())
        require(progress.chapterUrl.isNotBlank() && progress.chapterLabel.isNotBlank())
        require(progress.progressAt > 0L && progress.updatedAt > 0L)
        require((progress.inheritedFromSource == null) == (progress.inheritedFromUrl == null))
        check(
            handler.awaitOne {
                local_trackerQueries.hasSource(progress.workId, progress.source, progress.url)
            },
        ) {
            "local tracker source progress requires a matching source"
        }
        handler.await {
            local_trackerQueries.upsertSourceProgress(
                workId = progress.workId,
                source = progress.source,
                url = progress.url,
                chapterNumber = progress.chapterNumber,
                chapterUrl = progress.chapterUrl,
                chapterLabel = progress.chapterLabel,
                progressAt = progress.progressAt,
                inheritedFromSource = progress.inheritedFromSource,
                inheritedFromUrl = progress.inheritedFromUrl,
                updatedAt = progress.updatedAt,
            )
        }
    }

    override suspend fun recordSourceProgress(progress: LocalTrackedWorkSourceProgress) {
        require(progress.workId.isNotBlank())
        require(progress.source > 0L && progress.url.isNotBlank())
        require(progress.chapterUrl.isNotBlank() && progress.chapterLabel.isNotBlank())
        require(progress.progressAt > 0L && progress.updatedAt > 0L)
        require((progress.inheritedFromSource == null) == (progress.inheritedFromUrl == null))
        check(
            handler.awaitOne {
                local_trackerQueries.hasSource(progress.workId, progress.source, progress.url)
            },
        ) {
            "local tracker source progress requires a matching source"
        }
        handler.await {
            local_trackerQueries.recordSourceProgress(
                workId = progress.workId,
                source = progress.source,
                url = progress.url,
                chapterNumber = progress.chapterNumber,
                chapterUrl = progress.chapterUrl,
                chapterLabel = progress.chapterLabel,
                progressAt = progress.progressAt,
                inheritedFromSource = progress.inheritedFromSource,
                inheritedFromUrl = progress.inheritedFromUrl,
                updatedAt = progress.updatedAt,
            )
        }
    }

    override suspend fun deleteSourceProgress(workId: String, source: Long, url: String) {
        handler.await { local_trackerQueries.deleteSourceProgress(workId, source, url) }
    }

    override suspend fun setInheritanceOptedOut(workId: String, source: Long, url: String, optedOut: Boolean, updatedAt: Long) {
        require(workId.isNotBlank() && source > 0L && url.isNotBlank() && updatedAt > 0L)
        handler.await {
            local_trackerQueries.setInheritanceOptedOut(
                optedOut = if (optedOut) 1L else 0L,
                updatedAt = updatedAt,
                workId = workId,
                source = source,
                url = url,
            )
        }
    }

    override suspend fun getLists(workId: String): List<String> = handler.awaitList {
        local_trackerQueries.getLists(workId)
    }

    override suspend fun getListEntries(workId: String): List<LocalTrackedWorkList> = handler.awaitList {
        local_trackerQueries.getListEntries(workId) { name, createdAt ->
            LocalTrackedWorkList(name = name, createdAt = createdAt)
        }
    }

    override suspend fun replaceLists(workId: String, lists: List<String>) {
        val normalized = lists.map { it.trim() }
            .filter(String::isNotEmpty)
            .distinctBy(String::lowercase)
        require(normalized.all { it.length <= 256 })
        replaceListEntries(workId, normalized.map { LocalTrackedWorkList(it, System.currentTimeMillis()) })
    }

    override suspend fun replaceListEntries(workId: String, lists: List<LocalTrackedWorkList>) {
        val normalized = lists.map { entry ->
            LocalTrackedWorkList(name = entry.name.trim(), createdAt = entry.createdAt)
        }.filter { it.name.isNotEmpty() }
            .distinctBy { it.name.lowercase() }
        require(normalized.all { it.name.length <= 256 && it.createdAt > 0L })
        handler.await(inTransaction = true) {
            local_trackerQueries.deleteLists(workId)
            normalized.forEach { entry ->
                local_trackerQueries.insertList(
                    workId = workId,
                    listName = entry.name,
                    normalizedName = entry.name.lowercase(),
                    createdAt = entry.createdAt,
                )
            }
        }
    }
}

private val localTrackedWorkMapper = {
        id: String,
        title: String,
        normalizedTitle: String,
        status: String,
        lastChapterSource: Long?,
        lastChapterNumber: Double?,
        lastChapterUrl: String?,
        lastChapterLabel: String?,
        lastProgressAt: Long?,
        score: Double?,
        startDate: Long?,
        finishDate: Long?,
        createdAt: Long,
        updatedAt: Long,
    ->
    LocalTrackedWork(
        id = id,
        title = title,
        normalizedTitle = normalizedTitle,
        status = LocalTrackedWorkStatus.valueOf(status),
        lastChapterSource = lastChapterSource,
        lastChapterNumber = lastChapterNumber,
        lastChapterUrl = lastChapterUrl,
        lastChapterLabel = lastChapterLabel,
        lastProgressAt = lastProgressAt,
        score = score,
        startDate = startDate,
        finishDate = finishDate,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private fun compareProgress(
    left: LocalTrackedWorkSourceProgress,
    right: LocalTrackedWorkSourceProgress,
): Int = compareValuesBy(
    left,
    right,
    { it.chapterNumber ?: Double.NEGATIVE_INFINITY },
    { it.progressAt },
    { it.updatedAt },
)

private val localTrackedWorkSourceMapper = {
        workId: String,
        source: Long,
        url: String,
        title: String,
        confidence: Long,
        confirmation: String,
        inheritanceOptedOut: Long,
        createdAt: Long,
        updatedAt: Long,
    ->
    LocalTrackedWorkSource(
        workId = workId,
        source = source,
        url = url,
        title = title,
        confidence = confidence.toInt(),
        confirmation = LocalTrackedWorkSourceConfirmation.valueOf(confirmation),
        inheritanceOptedOut = inheritanceOptedOut != 0L,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private val localTrackedWorkSourceProgressMapper = {
        workId: String,
        source: Long,
        url: String,
        chapterNumber: Double?,
        chapterUrl: String,
        chapterLabel: String,
        progressAt: Long,
        inheritedFromSource: Long?,
        inheritedFromUrl: String?,
        updatedAt: Long,
    ->
    LocalTrackedWorkSourceProgress(
        workId = workId,
        source = source,
        url = url,
        chapterNumber = chapterNumber,
        chapterUrl = chapterUrl,
        chapterLabel = chapterLabel,
        progressAt = progressAt,
        inheritedFromSource = inheritedFromSource,
        inheritedFromUrl = inheritedFromUrl,
        updatedAt = updatedAt,
    )
}
