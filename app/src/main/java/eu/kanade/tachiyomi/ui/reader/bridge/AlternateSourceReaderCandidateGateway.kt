package eu.kanade.tachiyomi.ui.reader.bridge

import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceRuntime
import eu.kanade.tachiyomi.source.SourceRuntimeOperation
import exh.recs.matching.CrossExtensionMatchQueryPlanner
import exh.recs.matching.SameMangaCandidateEvidenceAttacher
import exh.recs.matching.SameMangaCandidateResult
import exh.recs.matching.SameMangaCandidateSearcher
import exh.recs.matching.SameMangaMatchSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.taste.interactor.GetAlternateSourceBridge
import tachiyomi.domain.taste.interactor.GetCrossSourceIdentityDecisions
import tachiyomi.domain.taste.interactor.GetCrossSourceMangaLinks
import tachiyomi.domain.taste.model.AlternateSourceBridge
import tachiyomi.domain.taste.model.AlternateSourceBridgeMapping
import tachiyomi.domain.taste.model.AlternateSourceBridgePolicy
import tachiyomi.domain.taste.model.AlternateSourceBridgeReviewState
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionPolicy
import tachiyomi.domain.taste.model.CrossSourceMangaLink
import tachiyomi.domain.taste.model.CrossSourceRecordKey

data class AlternateSourceReaderCandidateRequest(
    val primaryRoute: AlternateSourceReaderRoute,
    val precedingPrimaryChapterId: Long,
    val followingPrimaryChapterId: Long?,
)

enum class AlternateSourceReaderCandidateOrigin {
    CURRENT_BRIDGE,
    CONFIRMED_PAIR,
    LEGACY_GROUP,
    LIVE_SEARCH,
}

data class AlternateSourceReaderMangaCandidate internal constructor(
    val manga: Manga,
    val sourceLabel: String?,
    val origin: AlternateSourceReaderCandidateOrigin,
    val requiresPairConfirmation: Boolean,
)

data class AlternateSourceReaderCandidateDiscovery(
    val candidates: List<AlternateSourceReaderMangaCandidate>,
    val failedSourceCount: Int,
) {
    init {
        require(failedSourceCount >= 0)
    }
}

data class AlternateSourceReaderChapterCandidate internal constructor(
    val url: String,
    val name: String,
    val chapterNumber: Float,
)

sealed interface AlternateSourceReaderChapterDiscovery {
    data class Available(val chapters: List<AlternateSourceReaderChapterCandidate>) : AlternateSourceReaderChapterDiscovery
    data object Empty : AlternateSourceReaderChapterDiscovery
    data object SourceUnavailable : AlternateSourceReaderChapterDiscovery
    data object Failed : AlternateSourceReaderChapterDiscovery
}

internal data class AlternateSourceReaderCandidateContext(
    val origin: Manga,
    val preceding: Chapter,
    val following: Chapter?,
)

internal interface AlternateSourceReaderCandidateReads {
    suspend fun resolveContext(request: AlternateSourceReaderCandidateRequest): AlternateSourceReaderCandidateContext?
    suspend fun bridges(): List<AlternateSourceBridge>
    suspend fun mappings(): List<AlternateSourceBridgeMapping>
    suspend fun links(): List<CrossSourceMangaLink>
    suspend fun confirmedRecords(origin: CrossSourceRecordKey): List<CrossSourceRecordKey>
    suspend fun manga(record: CrossSourceRecordKey): Manga?
    suspend fun isConfirmed(origin: CrossSourceRecordKey, candidate: CrossSourceRecordKey): Boolean
    fun sourceLabel(sourceId: Long): String?
    suspend fun liveSearch(origin: Manga): List<AlternateSourceReaderLiveCandidateRead>
    suspend fun chapters(manga: Manga): AlternateSourceReaderChapterDiscovery
}

internal sealed interface AlternateSourceReaderLiveCandidateRead {
    data class Candidate(val manga: Manga) : AlternateSourceReaderLiveCandidateRead
    data object SourceFailure : AlternateSourceReaderLiveCandidateRead
}

class AlternateSourceReaderCandidateGateway internal constructor(
    private val reads: AlternateSourceReaderCandidateReads,
) {
    suspend fun candidateFromSelectedManga(
        request: AlternateSourceReaderCandidateRequest,
        manga: Manga,
    ): AlternateSourceReaderMangaCandidate? {
        val context = reads.resolveContext(request) ?: return null
        val origin = CrossSourceRecordKey(context.origin.source, context.origin.url)
        val record = CrossSourceRecordKey(manga.source, manga.url)
        if (record == origin || manga.url.isBlank() || manga.source <= 0L) return null

        return AlternateSourceReaderMangaCandidate(
            manga = manga,
            sourceLabel = reads.sourceLabel(manga.source),
            origin = AlternateSourceReaderCandidateOrigin.LIVE_SEARCH,
            requiresPairConfirmation = !reads.isConfirmed(origin, record),
        )
    }

    suspend fun discover(
        request: AlternateSourceReaderCandidateRequest,
        includeLiveSearch: Boolean,
    ): AlternateSourceReaderCandidateDiscovery {
        val context = reads.resolveContext(request)
            ?: return AlternateSourceReaderCandidateDiscovery(emptyList(), 0)
        val originKey = CrossSourceRecordKey(context.origin.source, context.origin.url)
        val candidates = LinkedHashMap<CrossSourceRecordKey, AlternateSourceReaderMangaCandidate>()

        val projectedMappings = AlternateSourceBridgePolicy.projectConflicts(reads.mappings())
            .groupBy { it.key.bridge }
        reads.bridges()
            .asSequence()
            .filter { bridge ->
                bridge.key.primary == originKey &&
                    bridge.version == AlternateSourceBridgePolicy.CURRENT_VERSION &&
                    bridge.deletedAt == null &&
                    bridge.reviewState == AlternateSourceBridgeReviewState.CURRENT
            }
            .sortedByDescending { bridge ->
                maxOf(
                    bridge.updatedAt,
                    projectedMappings[bridge.key].orEmpty().maxOfOrNull { it.updatedAt } ?: Long.MIN_VALUE,
                )
            }
            .forEach { bridge ->
                addCandidate(
                    candidates = candidates,
                    origin = originKey,
                    record = bridge.key.alternate,
                    candidateOrigin = AlternateSourceReaderCandidateOrigin.CURRENT_BRIDGE,
                    requiresPairConfirmation = false,
                )
            }

        val links = reads.links()
        val originLink = links.singleOrNull { it.source == originKey.source && it.url == originKey.url }
        if (originLink != null) {
            links.asSequence()
                .filter { it.groupId == originLink.groupId }
                .map { CrossSourceRecordKey(it.source, it.url) }
                .filter { it != originKey }
                .distinct()
                .forEach { record ->
                    val confirmed = reads.isConfirmed(originKey, record)
                    addCandidate(
                        candidates = candidates,
                        origin = originKey,
                        record = record,
                        candidateOrigin = if (confirmed) {
                            AlternateSourceReaderCandidateOrigin.CONFIRMED_PAIR
                        } else {
                            AlternateSourceReaderCandidateOrigin.LEGACY_GROUP
                        },
                        requiresPairConfirmation = !confirmed,
                    )
                }
        }

        reads.confirmedRecords(originKey).forEach { record ->
            addCandidate(
                candidates = candidates,
                origin = originKey,
                record = record,
                candidateOrigin = AlternateSourceReaderCandidateOrigin.CONFIRMED_PAIR,
                requiresPairConfirmation = false,
            )
        }

        var failedSources = 0
        if (includeLiveSearch) {
            reads.liveSearch(context.origin).forEach { result ->
                when (result) {
                    is AlternateSourceReaderLiveCandidateRead.Candidate -> {
                        val manga = result.manga
                        val record = CrossSourceRecordKey(manga.source, manga.url)
                        if (record != originKey && SameMangaCandidateEvidenceAttacher.isPlausible(context.origin, manga)) {
                            val confirmed = reads.isConfirmed(originKey, record)
                            addCandidate(
                                candidates = candidates,
                                origin = originKey,
                                record = record,
                                candidateOrigin = AlternateSourceReaderCandidateOrigin.LIVE_SEARCH,
                                requiresPairConfirmation = !confirmed,
                                resolved = manga,
                            )
                        }
                    }
                    AlternateSourceReaderLiveCandidateRead.SourceFailure -> failedSources++
                }
            }
        }

        return AlternateSourceReaderCandidateDiscovery(
            candidates = candidates.values.sortedWith(
                compareBy<AlternateSourceReaderMangaCandidate> { it.origin.ordinal }
                    .thenByDescending { it.manga.favorite }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.manga.title }
                    .thenBy { it.manga.source }
                    .thenBy { it.manga.url },
            ),
            failedSourceCount = failedSources,
        )
    }

    suspend fun chapters(candidate: AlternateSourceReaderMangaCandidate): AlternateSourceReaderChapterDiscovery =
        reads.chapters(candidate.manga)

    private suspend fun addCandidate(
        candidates: LinkedHashMap<CrossSourceRecordKey, AlternateSourceReaderMangaCandidate>,
        origin: CrossSourceRecordKey,
        record: CrossSourceRecordKey,
        candidateOrigin: AlternateSourceReaderCandidateOrigin,
        requiresPairConfirmation: Boolean,
        resolved: Manga? = null,
    ) {
        if (record == origin || record in candidates) return
        val manga = resolved ?: reads.manga(record) ?: return
        if (manga.source != record.source || manga.url != record.url) return
        candidates[record] = AlternateSourceReaderMangaCandidate(
            manga = manga,
            sourceLabel = reads.sourceLabel(record.source),
            origin = candidateOrigin,
            requiresPairConfirmation = requiresPairConfirmation,
        )
    }
}

internal class DefaultAlternateSourceReaderCandidateReads(
    private val getManga: GetManga,
    private val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId,
    private val getChapter: GetChapter,
    private val getBridge: GetAlternateSourceBridge,
    private val getLinks: GetCrossSourceMangaLinks,
    private val getIdentityDecisions: GetCrossSourceIdentityDecisions,
    private val identityResolver: exh.recs.matching.CrossSourceIdentityAuthorizationResolver,
    private val sourceManager: SourceManager,
    sourcePreferences: SourcePreferences,
    networkToLocalManga: NetworkToLocalManga,
    private val syncChaptersWithSource: SyncChaptersWithSource,
    private val dispatcher: CoroutineDispatcher,
) : AlternateSourceReaderCandidateReads {
    private val searcher = SameMangaCandidateSearcher(
        sourcePreferences = sourcePreferences,
        sourceManager = sourceManager,
        networkToLocalManga = networkToLocalManga,
        coroutineDispatcher = dispatcher,
        getIdentityDecisions = getIdentityDecisions,
    )
    private val settings = SameMangaMatchSettings(
        resultsPerSource = SameMangaMatchSettings.DEFAULT_RESULT_CAP,
        preselectResults = false,
        previewSampleSize = SameMangaMatchSettings.DEFAULT_SAMPLE_SIZE,
        avoidFirstPages = true,
    )

    override suspend fun resolveContext(
        request: AlternateSourceReaderCandidateRequest,
    ): AlternateSourceReaderCandidateContext? {
        val route = request.primaryRoute
        if (
            route.role != AlternateSourceReaderRouteRole.PRIMARY ||
            route.chapterId != request.precedingPrimaryChapterId ||
            request.precedingPrimaryChapterId <= 0L ||
            request.followingPrimaryChapterId?.let { it <= 0L || it == request.precedingPrimaryChapterId } == true
        ) {
            return null
        }
        val manga = getManga.await(route.mangaId) ?: return null
        if (manga.source != route.record.source || manga.url != route.record.url) return null
        val preceding = getChapter.await(request.precedingPrimaryChapterId) ?: return null
        val following = request.followingPrimaryChapterId?.let { getChapter.await(it) ?: return null }
        if (
            preceding.mangaId != manga.id ||
            following?.mangaId?.let { it != manga.id } == true ||
            preceding.url != route.chapterUrl ||
            following?.url?.isBlank() == true
        ) {
            return null
        }
        return AlternateSourceReaderCandidateContext(manga, preceding, following)
    }

    override suspend fun bridges(): List<AlternateSourceBridge> = getBridge.awaitAllBridges()

    override suspend fun mappings(): List<AlternateSourceBridgeMapping> = getBridge.awaitAllMappings()

    override suspend fun links(): List<CrossSourceMangaLink> = getLinks.awaitAll()

    override suspend fun confirmedRecords(origin: CrossSourceRecordKey): List<CrossSourceRecordKey> =
        getIdentityDecisions.awaitAll()
            .asSequence()
            .filter(CrossSourceIdentityDecisionPolicy::isAuthoritativeConfirmation)
            .mapNotNull { decision ->
                when (origin) {
                    decision.pair.left -> decision.pair.right
                    decision.pair.right -> decision.pair.left
                    else -> null
                }
            }
            .distinct()
            .toList()

    override suspend fun manga(record: CrossSourceRecordKey): Manga? =
        getMangaByUrlAndSourceId.await(record.url, record.source)

    override suspend fun isConfirmed(origin: CrossSourceRecordKey, candidate: CrossSourceRecordKey): Boolean =
        identityResolver.isConfirmed(origin.source, origin.url, candidate.source, candidate.url)

    override fun sourceLabel(sourceId: Long): String? = sourceManager.get(sourceId)?.name

    override suspend fun liveSearch(origin: Manga): List<AlternateSourceReaderLiveCandidateRead> {
        val results = mutableListOf<AlternateSourceReaderLiveCandidateRead>()
        val sources = searcher.getMatchingSources().take(MAX_LIVE_SOURCES)
        searcher.search(
            queries = CrossExtensionMatchQueryPlanner.buildQueries(origin),
            settings = settings,
            originManga = origin,
            sources = sources,
        ) { sourceResult ->
            when (val result = sourceResult.result) {
                SameMangaCandidateResult.Loading -> Unit
                is SameMangaCandidateResult.Error -> results += AlternateSourceReaderLiveCandidateRead.SourceFailure
                is SameMangaCandidateResult.Success -> {
                    result.results.forEach { candidate ->
                        results += AlternateSourceReaderLiveCandidateRead.Candidate(candidate)
                    }
                }
            }
        }
        return results
    }

    override suspend fun chapters(manga: Manga): AlternateSourceReaderChapterDiscovery {
        val source: Source = sourceManager.get(manga.source)
            ?: return AlternateSourceReaderChapterDiscovery.SourceUnavailable
        return SourceRuntime.run(source, SourceRuntimeOperation.MangaUpdate, dispatcher) {
            getMangaUpdate(
                manga = manga.toSManga(),
                chapters = emptyList(),
                fetchDetails = false,
                fetchChapters = true,
            ).chapters.also { chapters ->
                syncChaptersWithSource.await(chapters, manga, source, manualFetch = true)
            }
        }.fold(
            onSuccess = { chapters ->
                val visible = AlternateSourceReaderChapterPolicy.normalizeAndSort(manga.title, chapters)
                    .take(MAX_CHAPTERS)
                    .map { AlternateSourceReaderChapterCandidate(it.url, it.name, it.chapter_number) }
                    .toList()
                if (visible.isEmpty()) AlternateSourceReaderChapterDiscovery.Empty else AlternateSourceReaderChapterDiscovery.Available(visible)
            },
            onFailure = { AlternateSourceReaderChapterDiscovery.Failed },
        )
    }

    private companion object {
        const val MAX_LIVE_SOURCES = 12
        const val MAX_CHAPTERS = 500
    }
}
