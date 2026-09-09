package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupLocalTrackedWork
import eu.kanade.tachiyomi.data.backup.models.BackupLocalTrackedWorkSourceProgress
import kotlinx.coroutines.CancellationException
import tachiyomi.domain.tracker.model.LocalTrackedWork
import tachiyomi.domain.tracker.model.LocalTrackedWorkList
import tachiyomi.domain.tracker.model.LocalTrackedWorkSource
import tachiyomi.domain.tracker.model.LocalTrackedWorkSourceConfirmation
import tachiyomi.domain.tracker.model.LocalTrackedWorkStatus
import tachiyomi.domain.tracker.repository.LocalTrackerRepository

class LocalTrackerBackupRestorer(
    private val repository: LocalTrackerRepository,
) {
    suspend fun restore(rows: List<BackupLocalTrackedWork>): List<String> {
        val errors = mutableListOf<String>()
        rows.forEach { row ->
            val status = runCatching { LocalTrackedWorkStatus.valueOf(row.status) }.getOrNull()
            if (!isValid(row, status)) {
                errors += "Local tracker row '${row.id}': invalid payload"
                return@forEach
            }
            val existing = repository.getWork(row.id)
            if (existing != null && existing.updatedAt > row.updatedAt) return@forEach

            val previousGraph = existing?.let {
                LocalTrackerGraphSnapshot(
                    work = it,
                    sources = repository.getSources(it.id),
                    progress = repository.getSourceProgressForWork(it.id),
                    lists = repository.getListEntries(it.id),
                )
            }

            val ownershipConflict = row.sources.firstOrNull { source ->
                val owner = repository.getWorkIdBySourceUrl(source.source, source.url)
                owner != null && owner != row.id
            }
            if (ownershipConflict != null) {
                errors += "Local tracker row '${row.id}': source ownership conflict"
                return@forEach
            }
            val hasOrphanedProgress = row.sourceProgress.any { progress ->
                row.sources.none { source -> source.source == progress.source && source.url == progress.url }
            }
            if (hasOrphanedProgress) {
                errors += "Local tracker row '${row.id}': source progress has no matching source"
                return@forEach
            }

            val confirmations = row.sources.map { source ->
                runCatching { LocalTrackedWorkSourceConfirmation.valueOf(source.confirmation) }
                    .getOrNull()
            }
            if (confirmations.any { it == null }) {
                errors += "Local tracker row '${row.id}': invalid source confirmation"
                return@forEach
            }

            // A complete row can be newer while an individual child is older (for example after
            // merging backups from two devices). Keep the newest child by its own timestamp, and
            // collapse duplicate backup children deterministically before writing them.
            val sourcesByKey = row.sources
                .groupBy { it.source to it.url }
                .mapValues { (_, values) -> values.maxBy { it.updatedAt } }
            val progressByKey = row.sourceProgress
                .groupBy { it.source to it.url }
                .mapValues { (_, values) -> values.maxBy { it.updatedAt } }
            val currentSourcesByKey = previousGraph?.sources.orEmpty().associateBy { it.source to it.url }
            val currentProgressByKey = previousGraph?.progress.orEmpty().associateBy { it.source to it.url }
            val sourcesToRestore = sourcesByKey.values.filter { incoming ->
                currentSourcesByKey[incoming.source to incoming.url]?.let { current ->
                    incoming.updatedAt >= current.updatedAt
                } ?: true
            }
            val progressToRestore = progressByKey.values.filter { incoming ->
                currentProgressByKey[incoming.source to incoming.url]?.let { current ->
                    incoming.updatedAt >= current.updatedAt
                } ?: true
            }

            try {
                repository.upsertWork(
                    LocalTrackedWork(
                        id = row.id,
                        title = row.title,
                        normalizedTitle = row.normalizedTitle,
                        status = status!!,
                        lastChapterSource = row.lastChapterSource.takeIf { it > 0L },
                        lastChapterNumber = row.lastChapterNumber.takeIf { row.hasLastChapterNumber },
                        lastChapterUrl = row.lastChapterUrl.takeIf(String::isNotBlank),
                        lastChapterLabel = row.lastChapterLabel.takeIf(String::isNotBlank),
                        lastProgressAt = row.lastProgressAt.takeIf { it > 0L },
                        score = if (row.hasScore) normalizeScore(row) else null,
                        startDate = row.startDate.takeIf { row.hasStartDate },
                        finishDate = row.finishDate.takeIf { row.hasFinishDate },
                        createdAt = existing?.createdAt ?: row.createdAt,
                        updatedAt = row.updatedAt,
                    ),
                )
                sourcesToRestore.forEach { source ->
                    repository.upsertSource(
                        LocalTrackedWorkSource(
                            workId = row.id,
                            source = source.source,
                            url = source.url,
                            title = source.title,
                            confidence = source.confidence,
                            confirmation = LocalTrackedWorkSourceConfirmation.valueOf(source.confirmation),
                            inheritanceOptedOut = source.inheritanceOptedOut,
                            createdAt = source.createdAt,
                            updatedAt = source.updatedAt,
                        ),
                    )
                }
                progressToRestore.forEach { progress ->
                    repository.upsertSourceProgress(
                        tachiyomi.domain.tracker.model.LocalTrackedWorkSourceProgress(
                            workId = row.id,
                            source = progress.source,
                            url = progress.url,
                            chapterNumber = progress.chapterNumber.takeIf { progress.hasChapterNumber },
                            chapterUrl = progress.chapterUrl,
                            chapterLabel = progress.chapterLabel,
                            progressAt = progress.progressAt,
                            inheritedFromSource = progress.inheritedFromSource.takeIf { progress.hasInheritedFrom },
                            inheritedFromUrl = progress.inheritedFromUrl.takeIf { progress.hasInheritedFrom },
                            updatedAt = progress.updatedAt,
                        ),
                    )
                }
                // Current backups mark child-field presence explicitly. Non-empty collections are
                // also treated as present for compatibility with pre-marker payloads assembled by
                // older code, while an unmarked empty collection remains legacy-omitted.
                val sourcesPresent = row.hasSources || row.sources.isNotEmpty()
                val progressPresent = row.hasSourceProgress || row.sourceProgress.isNotEmpty()
                if (sourcesPresent) {
                    val incomingSources = row.sources.map { it.source to it.url }.toSet()
                    previousGraph?.sources
                        ?.filterNot { it.source to it.url in incomingSources }
                        ?.forEach { repository.deleteSource(row.id, it.source, it.url) }
                    val incomingProgress = row.sourceProgress.map { it.source to it.url }.toSet()
                    previousGraph?.progress
                        ?.filter {
                            it.source to it.url !in incomingProgress &&
                                it.source to it.url in incomingSources && progressPresent
                        }
                        ?.forEach { repository.deleteSourceProgress(row.id, it.source, it.url) }
                }
                if (row.hasLists || row.lists.isNotEmpty()) {
                    repository.replaceListEntries(
                        row.id,
                        row.lists.map { entry -> LocalTrackedWorkList(name = entry.name, createdAt = entry.createdAt) },
                    )
                }
            } catch (e: CancellationException) {
                rollback(row.id, previousGraph)?.let(e::addSuppressed)
                throw e
            } catch (e: Exception) {
                val rollbackFailure = rollback(row.id, previousGraph)
                errors += buildString {
                    append("Local tracker row '${row.id}': ${e::class.simpleName}")
                    rollbackFailure?.let { append("; rollback failed: ${it::class.simpleName}") }
                }
            }
        }
        return errors
    }

    /**
     * Restores the complete local-tracker graph when a row fails after one of its child writes.
     * The repository exposes graph operations rather than a transaction handle, so this keeps
     * restore truthful even for implementations whose individual calls commit independently.
     */
    private suspend fun rollback(id: String, previous: LocalTrackerGraphSnapshot?): Throwable? {
        return try {
            repository.deleteWork(id)
            previous?.let { snapshot ->
                repository.upsertWork(snapshot.work)
                snapshot.sources.forEach { source -> repository.upsertSource(source) }
                snapshot.progress.forEach { progress -> repository.upsertSourceProgress(progress) }
                repository.replaceListEntries(snapshot.work.id, snapshot.lists)
            }
            null
        } catch (rollbackFailure: Throwable) {
            if (rollbackFailure is CancellationException) throw rollbackFailure
            rollbackFailure
        }
    }

    private fun isValid(row: BackupLocalTrackedWork, status: LocalTrackedWorkStatus?): Boolean =
        row.id.isNotBlank() && row.id.length <= 128 &&
            row.title.isNotBlank() && row.title.length <= 4096 &&
            row.normalizedTitle.isNotBlank() && row.normalizedTitle.length <= 4096 &&
            status != null && row.createdAt > 0L && row.updatedAt >= row.createdAt &&
            (!row.hasLastChapterNumber || row.lastChapterNumber.isFinite()) &&
            (!row.hasScore || (row.score.isFinite() && validScore(row.score, row.scoreScaleVersion))) &&
            (!row.hasStartDate || row.startDate > 0L) &&
            (!row.hasFinishDate || row.finishDate > 0L) &&
            (!row.hasStartDate || !row.hasFinishDate || row.startDate <= row.finishDate) &&
            (row.lastChapterUrl.isBlank() || row.lastChapterUrl.length <= 4096) &&
            (row.lastChapterLabel.isBlank() || row.lastChapterLabel.length <= 4096) &&
            row.sources.all { source ->
                source.source > 0L && source.url.isNotBlank() && source.url.length <= 4096 &&
                    source.title.isNotBlank() && source.title.length <= 4096 &&
                    source.confidence in 0..100 && source.createdAt > 0L &&
                    source.updatedAt >= source.createdAt
            } && row.lists.all { it.name.isNotBlank() && it.name.length <= 256 && it.createdAt > 0L } &&
            row.sourceProgress.all(::isValidSourceProgress)

    private fun isValidSourceProgress(progress: BackupLocalTrackedWorkSourceProgress): Boolean =
        progress.source > 0L && progress.url.isNotBlank() && progress.url.length <= 4096 &&
            progress.chapterUrl.isNotBlank() && progress.chapterUrl.length <= 4096 &&
            progress.chapterLabel.isNotBlank() && progress.chapterLabel.length <= 4096 &&
            progress.progressAt > 0L && progress.updatedAt > 0L &&
            (!progress.hasChapterNumber || progress.chapterNumber.isFinite()) &&
            (!progress.hasInheritedFrom || (progress.inheritedFromSource > 0L && progress.inheritedFromUrl.isNotBlank()))

    private fun validScore(score: Double, scaleVersion: Int): Boolean = when (scaleVersion) {
        0 -> score in 0.0..10.0
        1 -> score in 1.0..100.0
        else -> false
    }

    private fun normalizeScore(row: BackupLocalTrackedWork): Double? = when (row.scoreScaleVersion) {
        0 -> row.score.takeIf { it > 0.0 }?.times(10.0)
        1 -> row.score
        else -> null
    }

    private data class LocalTrackerGraphSnapshot(
        val work: LocalTrackedWork,
        val sources: List<LocalTrackedWorkSource>,
        val progress: List<tachiyomi.domain.tracker.model.LocalTrackedWorkSourceProgress>,
        val lists: List<LocalTrackedWorkList>,
    )
}
