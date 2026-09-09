package eu.kanade.domain.track.interactor

import eu.kanade.domain.track.service.TrackPreferences
import exh.recs.matching.CrossSourceIdentityAuthorizationResolver
import kotlinx.coroutines.flow.first
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.taste.interactor.GetCrossSourceMangaLinks
import tachiyomi.domain.taste.model.AlternateSourceBridge
import tachiyomi.domain.taste.repository.AlternateSourceBridgeRepository
import tachiyomi.domain.tracker.model.LocalTrackedProgressInheritancePolicy
import tachiyomi.domain.tracker.model.LocalTrackedProgressSourceMappingPolicy
import tachiyomi.domain.tracker.model.LocalTrackedWorkSourceProgress
import tachiyomi.domain.tracker.model.LocalTrackedWorkStatus
import tachiyomi.domain.tracker.repository.LocalTrackerRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.UUID

class RecordLocalTrackedChapterProgress(
    private val localTrackerRepository: LocalTrackerRepository,
    private val trackPreferences: TrackPreferences = Injekt.get(),
    private val getCrossSourceMangaLinks: GetCrossSourceMangaLinks = Injekt.get(),
    private val identityAuthorizationResolver: CrossSourceIdentityAuthorizationResolver =
        CrossSourceIdentityAuthorizationResolver(),
    private val alternateSourceBridgeRepository: AlternateSourceBridgeRepository = Injekt.get(),
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val chapterRepository: ChapterRepository = Injekt.get(),
) {

    suspend fun await(manga: Manga, chapter: Chapter, progressAt: Long = System.currentTimeMillis()) =
        withNonCancellableContext {
            val workId = localTrackerRepository.getWorkIdBySourceUrl(manga.source, manga.url) ?: return@withNonCancellableContext
            val existingWork = localTrackerRepository.getWork(workId)
            val chapterNumber = chapter.chapterNumber.takeIf { chapter.isRecognizedNumber }
            localTrackerRepository.recordProgress(
                workId = workId,
                source = manga.source,
                chapterNumber = chapterNumber,
                chapterUrl = chapter.url,
                chapterLabel = chapter.name,
                progressAt = progressAt,
            )
            localTrackerRepository.recordSourceProgress(
                LocalTrackedWorkSourceProgress(
                    workId = workId,
                    source = manga.source,
                    url = manga.url,
                    chapterNumber = chapterNumber,
                    chapterUrl = chapter.url,
                    chapterLabel = chapter.name,
                    progressAt = progressAt,
                    updatedAt = progressAt,
                ),
            )
            existingWork?.takeIf {
                LocalTrackedProgressInheritancePolicy.acceptsAggregateProgress(
                    target = it,
                    chapterNumber = chapterNumber,
                    progressAt = progressAt,
                )
            }?.let { work ->
                val status = LocalTrackedProgressInheritancePolicy.statusAfterProgress(work.status)
                if (status != work.status || work.startDate == null ||
                    (status != LocalTrackedWorkStatus.COMPLETED && work.finishDate != null)
                ) {
                    localTrackerRepository.upsertWork(
                        work.copy(
                            status = status,
                            startDate = work.startDate ?: progressAt,
                            finishDate = work.finishDate.takeIf { status == LocalTrackedWorkStatus.COMPLETED },
                            updatedAt = maxOf(work.updatedAt, progressAt),
                        ),
                    )
                }
            }
            if (!trackPreferences.autoInheritLocalProgress().get()) return@withNonCancellableContext
            propagate(
                origin = manga,
                originWork = localTrackerRepository.getWork(workId),
                originChapterUrl = chapter.url,
                originChapterNumber = chapterNumber,
                progressAt = progressAt,
            )
        }

    /** Replays persisted local progress after refresh or after a new confirmed mapping is added. */
    suspend fun synchronize() {
        if (!trackPreferences.autoInheritLocalProgress().get()) {
            return
        }

        val bridges = alternateSourceBridgeRepository.getAllBridges()
        val mappings = alternateSourceBridgeRepository.getAllMappings()
        val works = localTrackerRepository.getAllWorksAsFlow().first()
        works.forEach { work ->
            val progressRows = localTrackerRepository.getSourceProgressForWork(work.id)
            val sourceRows = localTrackerRepository.getSources(work.id)
            val aggregateSource = work.lastChapterSource?.let { source ->
                sourceRows.singleOrNull {
                    it.source == source && it.confirmation == tachiyomi.domain.tracker.model.LocalTrackedWorkSourceConfirmation.USER_CONFIRMED
                }
            }
            val aggregateChapterUrl = work.lastChapterUrl
            val aggregateProgressAt = work.lastProgressAt
            val aggregateProgress = aggregateSource?.let { source ->
                aggregateChapterUrl?.takeIf { it.isNotBlank() }?.let { chapterUrl ->
                    aggregateProgressAt?.let { progressAt ->
                        LocalTrackedWorkSourceProgress(
                            workId = work.id,
                            source = source.source,
                            url = source.url,
                            chapterNumber = work.lastChapterNumber,
                            chapterUrl = chapterUrl,
                            chapterLabel = work.lastChapterLabel?.takeIf { it.isNotBlank() }
                                ?: "Chapter ${work.lastChapterNumber ?: "unknown"}",
                            progressAt = progressAt,
                            updatedAt = progressAt,
                        )
                    }
                }
            }
            val aggregateRow = aggregateProgress?.let { aggregate ->
                progressRows.singleOrNull { it.source == aggregate.source && it.url == aggregate.url }
            }
            if (aggregateProgress != null && !isAtLeast(aggregateRow, aggregateProgress)) {
                // The work row is the authoritative fallback when an older profile or an earlier
                // write left a source row stale. Replay it before using source rows as origins.
                localTrackerRepository.recordSourceProgress(aggregateProgress)
                propagate(
                    origin = Manga.create().copy(source = aggregateProgress.source, url = aggregateProgress.url),
                    originWork = work,
                    originChapterUrl = aggregateProgress.chapterUrl,
                    originChapterNumber = aggregateProgress.chapterNumber,
                    progressAt = aggregateProgress.progressAt,
                    bridges = bridges,
                    mappings = mappings,
                )
            }
            progressRows
                .filterNot { progress -> aggregateProgress?.let { it.source == progress.source && it.url == progress.url } == true }
                .forEach { progress ->
                    propagate(
                        origin = Manga.create().copy(source = progress.source, url = progress.url),
                        originWork = work,
                        originChapterUrl = progress.chapterUrl,
                        originChapterNumber = progress.chapterNumber,
                        progressAt = progress.progressAt,
                        bridges = bridges,
                        mappings = mappings,
                    )
                }
        }
    }

    private fun isAtLeast(
        existing: LocalTrackedWorkSourceProgress?,
        update: LocalTrackedWorkSourceProgress,
    ): Boolean {
        if (existing == null) return false
        val existingNumber = existing.chapterNumber
        val updateNumber = update.chapterNumber
        return when {
            existingNumber == null && updateNumber == null -> existing.progressAt >= update.progressAt
            existingNumber == null -> false
            updateNumber == null -> true
            existingNumber > updateNumber -> true
            existingNumber < updateNumber -> false
            else -> existing.progressAt >= update.progressAt
        }
    }

    private suspend fun propagate(
        origin: Manga,
        originWork: tachiyomi.domain.tracker.model.LocalTrackedWork?,
        originChapterUrl: String,
        originChapterNumber: Double?,
        progressAt: Long,
        bridges: List<AlternateSourceBridge>? = null,
        mappings: List<tachiyomi.domain.taste.model.AlternateSourceBridgeMapping>? = null,
    ) {
        if (!trackPreferences.autoInheritLocalProgress().get()) return
        val originLink = getCrossSourceMangaLinks.awaitBySourceUrl(origin.source, origin.url)
        if (originLink == null) return
        val confirmedMembers = identityAuthorizationResolver.confirmedGroupMembers(
            originSource = origin.source,
            originUrl = origin.url,
            members = getCrossSourceMangaLinks.awaitByGroupId(originLink.groupId),
        )
        if (confirmedMembers.size <= 1) return
        val resolvedBridges = bridges ?: alternateSourceBridgeRepository.getAllBridges()
        val resolvedMappings = mappings ?: alternateSourceBridgeRepository.getAllMappings()
        confirmedMembers.asSequence()
            .filterNot { it.source == origin.source && it.url == origin.url }
            .forEach { target ->
                inheritToTarget(
                    origin = origin,
                    originWork = originWork,
                    originChapterUrl = originChapterUrl,
                    originChapterNumber = originChapterNumber,
                    targetSource = target.source,
                    targetUrl = target.url,
                    progressAt = progressAt,
                    bridges = resolvedBridges,
                    mappings = resolvedMappings,
                )
            }
    }

    private suspend fun inheritToTarget(
        origin: Manga,
        originWork: tachiyomi.domain.tracker.model.LocalTrackedWork?,
        originChapterUrl: String,
        originChapterNumber: Double?,
        targetSource: Long,
        targetUrl: String,
        progressAt: Long,
        bridges: List<AlternateSourceBridge>,
        mappings: List<tachiyomi.domain.taste.model.AlternateSourceBridgeMapping>,
    ) {
        val targetManga = mangaRepository.getMangaByUrlAndSourceId(targetUrl, targetSource)
        if (targetManga == null) return
        val existingTargetWorkId = localTrackerRepository.getWorkIdBySourceUrl(targetSource, targetUrl)
        val existingTargetWork = existingTargetWorkId?.let { localTrackerRepository.getWork(it) }
            ?: if (existingTargetWorkId != null) return else null
        val targetSourceRow = existingTargetWork?.let { targetWork ->
            localTrackerRepository.getSources(targetWork.id)
                .singleOrNull { it.source == targetSource && it.url == targetUrl }
        }
        if (existingTargetWork != null && targetSourceRow == null) {
            // A confirmed version can be discovered after its local work was created through
            // another source. Reattach it before applying the same progress inheritance path.
            localTrackerRepository.upsertSource(
                tachiyomi.domain.tracker.model.LocalTrackedWorkSource(
                    workId = existingTargetWork.id,
                    source = targetSource,
                    url = targetUrl,
                    title = targetManga.title,
                    confidence = 100,
                    confirmation = tachiyomi.domain.tracker.model.LocalTrackedWorkSourceConfirmation.USER_CONFIRMED,
                    createdAt = progressAt,
                    updatedAt = progressAt,
                ),
            )
        }
        val mapping = LocalTrackedProgressSourceMappingPolicy.resolve(
            originSource = origin.source,
            originUrl = origin.url,
            originChapterUrl = originChapterUrl,
            targetSource = targetSource,
            targetUrl = targetUrl,
            bridges = bridges,
            mappings = mappings,
        )
        val targetChapter = mapping?.let {
            chapterRepository.getChapterByUrlAndMangaId(it.targetChapterUrl, targetManga.id)
        } ?: resolveUniqueRecognizedChapter(
            manga = targetManga,
            originChapterNumber = originChapterNumber,
        )
        if (targetChapter == null) return
        val targetChapterUrl = targetChapter.url
        val targetWork = existingTargetWork ?: run {
            val sourceWork = originWork ?: return
            val newWork = sourceWork.copy(
                id = UUID.randomUUID().toString(),
                title = targetManga.title,
                normalizedTitle = targetManga.title.trim().lowercase(),
                lastChapterSource = null,
                lastChapterNumber = null,
                lastChapterUrl = null,
                lastChapterLabel = null,
                lastProgressAt = null,
                updatedAt = progressAt,
            )
            localTrackerRepository.upsertWork(newWork)
            localTrackerRepository.upsertSource(
                tachiyomi.domain.tracker.model.LocalTrackedWorkSource(
                    workId = newWork.id,
                    source = targetSource,
                    url = targetUrl,
                    title = targetManga.title,
                    confidence = 100,
                    confirmation = tachiyomi.domain.tracker.model.LocalTrackedWorkSourceConfirmation.USER_CONFIRMED,
                    createdAt = progressAt,
                    updatedAt = progressAt,
                ),
            )
            newWork
        }
        val update = tachiyomi.domain.tracker.model.MappedLocalTrackedProgress(
            targetSource = targetSource,
            targetUrl = targetUrl,
            targetChapterUrl = targetChapterUrl,
            targetLabel = targetChapter.name,
            chapterNumber = targetChapter.chapterNumber.takeIf { targetChapter.isRecognizedNumber },
            progressAt = progressAt,
        )
        val existingTargetProgress = localTrackerRepository.getSourceProgress(
            workId = targetWork.id,
            source = targetSource,
            url = targetUrl,
        )
        val decision = LocalTrackedProgressInheritancePolicy.decide(
            enabled = true,
            confirmedGroupMember = true,
            optedOut = targetSourceRow?.inheritanceOptedOut ?: false,
            target = targetWork,
            update = update,
        )
        if (decision == tachiyomi.domain.tracker.model.LocalTrackedProgressInheritanceDecision.ALREADY_RECORDED) {
            // The work-level chapter may already match while this source's row is stale. Keep
            // the aggregate conflict decision, but advance the source-specific row independently.
            val status = LocalTrackedProgressInheritancePolicy.statusAfterProgress(targetWork.status)
            if (status != targetWork.status || targetWork.startDate == null ||
                (status != LocalTrackedWorkStatus.COMPLETED && targetWork.finishDate != null)
            ) {
                localTrackerRepository.upsertWork(
                    targetWork.copy(
                        status = status,
                        startDate = targetWork.startDate ?: progressAt,
                        finishDate = targetWork.finishDate.takeIf { status == LocalTrackedWorkStatus.COMPLETED },
                        updatedAt = maxOf(targetWork.updatedAt, progressAt),
                    ),
                )
            }
            if (LocalTrackedProgressInheritancePolicy.acceptsSourceProgress(existingTargetProgress, update)) {
                localTrackerRepository.recordSourceProgress(
                    LocalTrackedWorkSourceProgress(
                        workId = targetWork.id,
                        source = targetSource,
                        url = targetUrl,
                        chapterNumber = update.chapterNumber,
                        chapterUrl = targetChapterUrl,
                        chapterLabel = update.targetLabel,
                        progressAt = progressAt,
                        inheritedFromSource = origin.source,
                        inheritedFromUrl = origin.url,
                        updatedAt = progressAt,
                    ),
                )
            }
            return
        }
        if (decision != tachiyomi.domain.tracker.model.LocalTrackedProgressInheritanceDecision.APPLY) return
        val updatedWork = LocalTrackedProgressInheritancePolicy.apply(targetWork, update) ?: return
        localTrackerRepository.upsertWork(updatedWork)
        localTrackerRepository.recordSourceProgress(
            LocalTrackedWorkSourceProgress(
                workId = targetWork.id,
                source = targetSource,
                url = targetUrl,
                chapterNumber = update.chapterNumber,
                chapterUrl = targetChapterUrl,
                chapterLabel = update.targetLabel,
                progressAt = progressAt,
                inheritedFromSource = origin.source,
                inheritedFromUrl = origin.url,
                updatedAt = progressAt,
            ),
        )
    }

    /**
     * A confirmed group can still lack a confirmed URL mapping when a source has just refreshed.
     * Use an exact recognized chapter number only when that source exposes one unique URL; never
     * guess through duplicate or unrecognized candidates.
     */
    private suspend fun resolveUniqueRecognizedChapter(
        manga: Manga,
        originChapterNumber: Double?,
    ): Chapter? {
        if (originChapterNumber == null) return null
        return chapterRepository.getChapterByMangaId(manga.id)
            .asSequence()
            .filter { it.isRecognizedNumber && it.chapterNumber == originChapterNumber }
            .distinctBy { it.url }
            .singleOrNull()
    }
}
