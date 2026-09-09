package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupLocalTrackedWork
import eu.kanade.tachiyomi.data.backup.models.BackupLocalTrackedWorkList
import eu.kanade.tachiyomi.data.backup.models.BackupLocalTrackedWorkSource
import eu.kanade.tachiyomi.data.backup.models.BackupLocalTrackedWorkSourceProgress
import kotlinx.coroutines.flow.first
import tachiyomi.domain.tracker.repository.LocalTrackerRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class LocalTrackerBackupCreator(
    private val repository: LocalTrackerRepository = Injekt.get(),
) {
    suspend operator fun invoke(): List<BackupLocalTrackedWork> = repository.getAllWorksAsFlow()
        .first()
        .map { work ->
            BackupLocalTrackedWork(
                id = work.id,
                title = work.title,
                normalizedTitle = work.normalizedTitle,
                status = work.status.name,
                lastChapterSource = work.lastChapterSource ?: 0L,
                lastChapterNumber = work.lastChapterNumber ?: 0.0,
                hasLastChapterNumber = work.lastChapterNumber != null,
                lastChapterUrl = work.lastChapterUrl.orEmpty(),
                lastChapterLabel = work.lastChapterLabel.orEmpty(),
                lastProgressAt = work.lastProgressAt ?: 0L,
                createdAt = work.createdAt,
                updatedAt = work.updatedAt,
                sources = repository.getSources(work.id).map { source ->
                    BackupLocalTrackedWorkSource(
                        source = source.source,
                        url = source.url,
                        title = source.title,
                        confidence = source.confidence,
                        confirmation = source.confirmation.name,
                        createdAt = source.createdAt,
                        updatedAt = source.updatedAt,
                        inheritanceOptedOut = source.inheritanceOptedOut,
                    )
                },
                sourceProgress = repository.getSources(work.id).mapNotNull { source ->
                    repository.getSourceProgress(work.id, source.source, source.url)?.let { progress ->
                        BackupLocalTrackedWorkSourceProgress(
                            source = progress.source,
                            url = progress.url,
                            chapterNumber = progress.chapterNumber ?: 0.0,
                            hasChapterNumber = progress.chapterNumber != null,
                            chapterUrl = progress.chapterUrl,
                            chapterLabel = progress.chapterLabel,
                            progressAt = progress.progressAt,
                            inheritedFromSource = progress.inheritedFromSource ?: 0L,
                            inheritedFromUrl = progress.inheritedFromUrl.orEmpty(),
                            hasInheritedFrom = progress.inheritedFromSource != null,
                            updatedAt = progress.updatedAt,
                        )
                    }
                },
                hasSources = true,
                hasLists = true,
                hasSourceProgress = true,
                lists = repository.getListEntries(work.id).map { entry ->
                    BackupLocalTrackedWorkList(name = entry.name, createdAt = entry.createdAt)
                },
                score = work.score ?: 0.0,
                hasScore = work.score != null,
                startDate = work.startDate ?: 0L,
                finishDate = work.finishDate ?: 0L,
                hasStartDate = work.startDate != null,
                hasFinishDate = work.finishDate != null,
                scoreScaleVersion = 1,
            )
        }
}
