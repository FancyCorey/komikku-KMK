package exh.recs.evaluation

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import exh.recs.PersonalRecommendationScorer
import exh.recs.RecommendationQueryPlanner
import exh.recs.sources.GenreFilterMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import mihon.domain.manga.model.toDomainManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.interactor.GetTagAliases
import tachiyomi.domain.taste.model.TasteProfile
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/**
 * Bounded second-stage probe that runs a small For You-like search against an already-installed
 * source and measures recommendation quality.
 *
 * v0.7.13: Added bounded metadata enrichment (getMangaDetails for candidates without genre),
 * matching For You's behavior more closely. Raw search results from many extensions carry no
 * genre/tag metadata; without enrichment the scorer sees empty genres and produces low/zero
 * scores even for perfectly good sources. The enrichment cap keeps network work bounded.
 *
 * v0.7.35: All source/extension calls (getFilterList, getSearchManga, getMangaDetails) now run
 * on [ioDispatcher] (default Dispatchers.IO). This prevents NetworkOnMainThreadException that
 * was causing mass-fail in the recommendation-quality probe rows. NetworkOnMainThreadException
 * now only occurs if it somehow reaches the error handler; it is classified as an internal probe
 * execution error and does not influence source quality scoring.
 *
 * Constraints (all enforced here, no caller setup needed):
 * - At most [RecommendationQueryPlanner.MAX_STRATEGIES_PER_SOURCE] query plans (2).
 * - Page 1 only — no pagination, no chapter list fetch, no image fetch.
 * - Per-plan search timeout of [PLAN_TIMEOUT_MS] (30 s).
 * - Per-candidate detail enrichment timeout of [ENRICH_TIMEOUT_MS] (10 s).
 * - At most [ENRICH_CAP_PER_PLAN] candidates enriched per plan (5), sequential.
 * - Errors are recorded in the outcome, not re-thrown to the caller.
 *
 * This probe intentionally does NOT persist results to the DB itself.
 * The caller (SourceEvaluationScreenModel) persists the outcome via the repository.
 */
class SourceRecommendationFitProbe(
    private val getTagAliases: GetTagAliases = Injekt.get(),
    // KMK --> v0.7.35: injectable dispatcher so tests can supply a test dispatcher
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    // KMK <--
) {

    companion object {
        private const val PLAN_TIMEOUT_MS = 30_000L
        private const val RAW_CAP = 20

        // KMK --> v0.7.13: enrichment constants — kept small to bound network calls
        private const val ENRICH_CAP_PER_PLAN = 5
        private const val ENRICH_TIMEOUT_MS = 10_000L
        // KMK <--
    }

    /**
     * Run the bounded probe against [source] using [tasteProfile].
     * Never throws — errors become [SourceRecommendationFitProbeOutcome.errorCount] entries.
     */
    suspend fun probe(
        source: Source,
        tasteProfile: TasteProfile,
    ): SourceRecommendationFitProbeOutcome {
        val topTags = topTags(tasteProfile)
        if (topTags.isEmpty()) {
            return SourceRecommendationFitProbeOutcome(reasons = listOf("No taste profile tags"))
        }

        val aliasMap = try {
            getTagAliases.awaitAliasMap()
        } catch (_: Exception) {
            emptyMap()
        }
        val groupToAliases = try {
            getTagAliases.awaitGroupToAliasesMap()
        } catch (_: Exception) {
            emptyMap()
        }
        val aliasCandidates = buildAliasCandidates(topTags, groupToAliases)

        val plans = RecommendationQueryPlanner.buildPlans(topTags)

        var queryCount = 0
        var querySuccessCount = 0
        var rawResultCount = 0
        var visibleCandidateCount = 0
        var filteredOutCount = 0
        var blockedTagCandidateCount = 0
        var matchedGroupCount = 0
        var noMatchesCount = 0
        var errorCount = 0
        var totalScore = 0.0
        var enrichedCandidateCount = 0
        var weakMetadataCandidateCount = 0
        val reasons = mutableListOf<String>()
        val seenMatchedGroups = mutableSetOf<String>()

        for (plan in plans) {
            try {
                // KMK --> v0.7.35: force IO dispatcher — getFilterList can hit disk/network
                val filterList = try {
                    withContext(ioDispatcher) { source.getFilterList() }
                } catch (_: Exception) {
                    FilterList()
                }
                // KMK <--
                val searchParams = GenreFilterMapper.buildSearch(
                    filterList,
                    plan.tags,
                    aliasCandidates,
                    plan.forceTextOnly,
                    blockedGenres = tasteProfile.blockedGroups.toList(),
                )

                queryCount++
                // KMK --> v0.7.35: force IO dispatcher — getSearchManga performs network I/O
                val page = withTimeoutOrNull(PLAN_TIMEOUT_MS) {
                    withContext(ioDispatcher) {
                        source.getSearchManga(1, searchParams.textQuery, searchParams.filters)
                    }
                }
                // KMK <--

                if (page == null) {
                    reasons.add("Plan ${plan.type.name}: timed out")
                    errorCount++
                    continue
                }

                val rawItems = page.mangas.take(RAW_CAP).distinctBy { it.url }
                rawResultCount += rawItems.size

                if (rawItems.isEmpty()) {
                    noMatchesCount++
                    reasons.add("Plan ${plan.type.name}: no raw results")
                    continue
                }

                querySuccessCount++

                // KMK --> v0.7.13: bounded enrichment — fetch manga details for candidates
                // with no genre metadata so the scorer can see actual tags.
                // Many extensions omit genre on search results but populate it in getMangaDetails.
                val smangaByUrl = rawItems.associateBy { it.url }
                val domainMangas = rawItems.map { it.toDomainManga(source.id) }
                var planEnrichedCount = 0
                var planWeakCount = 0
                val enrichedMangas = domainMangas.map { manga ->
                    if (planEnrichedCount < ENRICH_CAP_PER_PLAN && manga.needsProbeEnrichment()) {
                        val smanga = smangaByUrl[manga.url]
                        if (smanga != null) {
                            // KMK --> v0.7.35: force IO dispatcher — getMangaDetails performs network I/O
                            val enriched = runCatching {
                                withTimeoutOrNull(ENRICH_TIMEOUT_MS) {
                                    withContext(ioDispatcher) {
                                        val details = source.getMangaUpdate(
                                            manga = smanga,
                                            chapters = emptyList(),
                                            fetchDetails = true,
                                            fetchChapters = false,
                                        ).manga
                                        details.toDomainManga(source.id)
                                    }
                                }
                            }.getOrNull()
                            // KMK <--
                            if (enriched != null) {
                                planEnrichedCount++
                                return@map enriched
                            }
                        }
                    }
                    manga
                }
                enrichedCandidateCount += planEnrichedCount
                // KMK <--

                val planVisibleBefore = visibleCandidateCount
                for (manga in enrichedMangas) {
                    // KMK --> v0.7.13: track candidates still lacking genre after enrichment
                    if (manga.needsProbeEnrichment()) planWeakCount++
                    // KMK <--
                    val scored = PersonalRecommendationScorer.score(manga, tasteProfile, aliasMap)
                    if (scored.blocked) {
                        blockedTagCandidateCount++
                        filteredOutCount++
                        continue
                    }
                    if (scored.score <= 0.0) {
                        filteredOutCount++
                        continue
                    }
                    visibleCandidateCount++
                    totalScore += scored.score
                    val newGroups = scored.matchedGroups.filter { it !in seenMatchedGroups }
                    if (newGroups.isNotEmpty()) {
                        matchedGroupCount += newGroups.size
                        seenMatchedGroups += newGroups
                    }
                }
                weakMetadataCandidateCount += planWeakCount

                val planVisible = visibleCandidateCount - planVisibleBefore
                if (planVisible > 0) {
                    // KMK --> v0.7.13: include enrichment info in reason
                    val enrichNote = if (planEnrichedCount > 0) " ($planEnrichedCount enriched)" else ""
                    reasons.add("Plan ${plan.type.name}: $planVisible visible$enrichNote")
                    // KMK <--
                } else if (planWeakCount == enrichedMangas.size && enrichedMangas.isNotEmpty()) {
                    // KMK --> v0.7.13: all results had no genre — useful diagnostic
                    reasons.add("Plan ${plan.type.name}: ${enrichedMangas.size} results, all had no genre metadata")
                    // KMK <--
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorCount++
                // KMK --> v0.7.35: classify NetworkOnMainThreadException as an internal probe
                // threading bug so it is visible in diagnostics but does not lower source quality.
                val errorLabel = if (e.javaClass.name.contains("NetworkOnMainThreadException")) {
                    "internal-threading-error"
                } else {
                    e.message?.take(60) ?: e.javaClass.simpleName
                }
                reasons.add("Plan ${plan.type.name}: error — $errorLabel")
                // KMK <--
            }
        }

        val avgCandidateScore = if (visibleCandidateCount > 0) totalScore / visibleCandidateCount else 0.0

        return SourceRecommendationFitProbeOutcome(
            queryCount = queryCount,
            querySuccessCount = querySuccessCount,
            rawResultCount = rawResultCount,
            visibleCandidateCount = visibleCandidateCount,
            filteredOutCount = filteredOutCount,
            blockedTagCandidateCount = blockedTagCandidateCount,
            matchedGroupCount = matchedGroupCount,
            topPicksContribution = 0, // bounded probe does not compute cross-source Top Picks
            noMatchesCount = noMatchesCount,
            errorCount = errorCount,
            avgCandidateScore = avgCandidateScore,
            reasons = reasons,
            enrichedCandidateCount = enrichedCandidateCount,
            weakMetadataCandidateCount = weakMetadataCandidateCount,
        )
    }

    private fun topTags(profile: TasteProfile): List<String> =
        profile.learnedTagWeights.entries
            .filter { it.value > 0.3 }
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }

    private fun buildAliasCandidates(
        topTags: List<String>,
        groupToAliases: Map<String, List<String>>,
    ): Map<String, List<String>> = buildMap {
        for (tag in topTags) {
            val builtIn = GenreFilterMapper.BUILT_IN_SYNONYMS[tag]
            val user = groupToAliases[tag]
            val merged = buildList {
                if (builtIn != null) addAll(builtIn)
                if (user != null) addAll(user)
            }
            if (merged.isNotEmpty()) put(tag, merged)
        }
    }
}

// KMK --> v0.7.13: probe-local enrichment check — genre absence is the signal, not initialized flag
private fun Manga.needsProbeEnrichment(): Boolean = genre.isNullOrEmpty()
// KMK <--
// KMK <--
