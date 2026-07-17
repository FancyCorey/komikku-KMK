package exh.recs.group

// KMK -->
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import mihon.domain.manga.model.toDomainManga
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.taste.interactor.GetCrossSourceMangaLinks
import tachiyomi.domain.taste.model.CrossSourceMangaLink
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Pure builder that constructs a [GroupRecommendationSeed] from a manga entry.
 *
 * Steps:
 *  1. Look up the cross-source link group for (sourceId, url).
 *  2. If a groupId exists, fetch all group members from the DB.
 *  3. Resolve genre/tag metadata from local DB manga rows for all members.
 *  4. For members with sparse genre metadata (< [MIN_LOCAL_GENRES_THRESHOLD] genres), do bounded
 *     source detail enrichment with per-member and total timeouts.
 *  5. Build weighted seed tags: weight = memberCount / totalMetadataMembers.
 *
 * v0.7.35: first implementation — installed-source filter not applied here; callers
 * may filter the member list further if needed.
 * v0.7.38: added bounded metadata enrichment via source getMangaDetails calls,
 * weighted tag building, and early-stop when seed is already rich enough.
 */
class GroupRecommendationSeedBuilder(
    private val getCrossSourceMangaLinks: GetCrossSourceMangaLinks = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
) {
    companion object {
        /** Members with fewer than this many nonblank genres are candidates for enrichment. */
        internal const val MIN_LOCAL_GENRES_THRESHOLD = 2

        /** Maximum group members to enrich per seed build. */
        internal const val MAX_ENRICH_MEMBERS = 8

        /** Per-member enrichment call timeout. */
        internal const val ENRICH_MEMBER_TIMEOUT_MS = 5_000L

        /** Total enrichment budget — all member fetches must complete within this window. */
        internal const val TOTAL_ENRICH_TIMEOUT_MS = 20_000L

        /** If seed already has at least this many distinct tags… */
        internal const val EARLY_STOP_TAG_COUNT = 8

        /** …across at least this many members, skip enriching additional members. */
        internal const val EARLY_STOP_MEMBER_COUNT = 2

        /** Maximum seed tags kept after ranking. */
        private const val TOP_TAGS_LIMIT = 10
    }

    suspend fun build(
        sourceId: Long,
        url: String,
        primaryTitle: String,
    ): GroupRecommendationSeed {
        val link: CrossSourceMangaLink? = runCatching {
            getCrossSourceMangaLinks.awaitBySourceUrl(sourceId, url)
        }.getOrNull()

        val groupMembers: List<CrossSourceMangaLink> = if (link != null) {
            runCatching {
                getCrossSourceMangaLinks.awaitByGroupId(link.groupId)
            }.getOrDefault(listOf(link))
        } else {
            emptyList()
        }

        val memberKeys: Set<Pair<Long, String>> = groupMembers
            .map { it.source to it.url }
            .toSet()
            .plus(sourceId to url)

        val allTitles: List<String> = groupMembers.map { it.title }
            .plus(primaryTitle)
            .distinct()

        val allSourceIds: Set<Long> = groupMembers.map { it.source }.toSet().plus(sourceId)

        // Step 1: Load local DB manga for all members (used as enrichment base)
        val mangaByKey = mutableMapOf<Pair<Long, String>, Manga>()
        for ((membSource, membUrl) in memberKeys) {
            val manga = runCatching { getManga.await(membUrl, membSource) }.getOrNull() ?: continue
            mangaByKey[membSource to membUrl] = manga
        }

        // Step 2: Find members with sparse local genre metadata
        val sparseKeys = memberKeys.filter { key ->
            val genres = mangaByKey[key]?.genre?.filter { it.isNotBlank() } ?: emptyList()
            genres.size < MIN_LOCAL_GENRES_THRESHOLD
        }

        var enrichedMemberCount = 0

        // Step 3: Bounded enrichment for sparse members
        if (sparseKeys.isNotEmpty()) {
            val initialTagCount = distinctTagCount(mangaByKey)
            val initialMemberCount = mangaByKey.values.count { (it.genre?.size ?: 0) >= 1 }
            val needsEnrichment = initialTagCount < EARLY_STOP_TAG_COUNT ||
                initialMemberCount < EARLY_STOP_MEMBER_COUNT

            if (needsEnrichment) {
                withTimeoutOrNull(TOTAL_ENRICH_TIMEOUT_MS) {
                    var enrichCount = 0
                    for ((src, membUrl) in sparseKeys) {
                        if (enrichCount >= MAX_ENRICH_MEMBERS) break

                        val source = runCatching {
                            sourceManager.get(src)
                        }.getOrNull() ?: continue

                        // Build SManga for the API call — use local data if available, else URL-only
                        val localManga = mangaByKey[src to membUrl]
                        val smanga: SManga = localManga?.toSManga()
                            ?: SManga.create().also { it.url = membUrl }

                        val enrichedManga = withTimeoutOrNull(ENRICH_MEMBER_TIMEOUT_MS) {
                            runCatching {
                                val details = withContext(Dispatchers.IO) {
                                    source.getMangaUpdate(
                                        manga = smanga,
                                        chapters = emptyList(),
                                        fetchDetails = true,
                                        fetchChapters = false,
                                    ).manga
                                }
                                networkToLocalManga(listOf(details.toDomainManga(src))).firstOrNull()
                            }.onFailure { e ->
                                if (e is CancellationException) throw e
                            }.getOrNull()
                        }

                        if (enrichedManga != null) {
                            val newGenres = enrichedManga.genre?.filter { it.isNotBlank() } ?: emptyList()
                            if (newGenres.size > (localManga?.genre?.size ?: 0)) {
                                mangaByKey[src to membUrl] = enrichedManga
                                enrichedMemberCount++
                            }
                        }
                        enrichCount++

                        // Early stop: seed is now rich enough
                        val updatedTagCount = distinctTagCount(mangaByKey)
                        val updatedMemberCount = mangaByKey.values.count { (it.genre?.size ?: 0) >= 1 }
                        if (updatedTagCount >= EARLY_STOP_TAG_COUNT && updatedMemberCount >= EARLY_STOP_MEMBER_COUNT) {
                            break
                        }
                    }
                }
            }
        }

        // Step 4: Build weighted seed tags from the final member set
        val tagFrequency = mutableMapOf<String, Int>()
        var metadataMemberCount = 0
        for ((_, manga) in mangaByKey) {
            val genres = manga.genre
                ?.map { it.trim().lowercase() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            if (genres.isNotEmpty()) {
                metadataMemberCount++
                genres.forEach { tag -> tagFrequency[tag] = (tagFrequency[tag] ?: 0) + 1 }
            }
        }

        val totalMembers = metadataMemberCount.coerceAtLeast(1)
        val seedTags = tagFrequency.entries
            .sortedByDescending { it.value }
            .take(TOP_TAGS_LIMIT)
            .map { (tag, count) ->
                GroupSeedTag(
                    name = tag,
                    weight = count.toDouble() / totalMembers,
                    memberCount = count,
                )
            }

        return GroupRecommendationSeed(
            primaryTitle = primaryTitle,
            titles = allTitles,
            tags = seedTags.map { it.name },
            seedTags = seedTags,
            sourceIds = allSourceIds,
            memberKeys = memberKeys,
            groupId = link?.groupId,
            metadataMemberCount = metadataMemberCount,
            enrichedMemberCount = enrichedMemberCount,
        )
    }

    /** Count distinct normalized tags across all manga in [mangaByKey]. */
    private fun distinctTagCount(mangaByKey: Map<Pair<Long, String>, Manga>): Int {
        val allTags = mutableSetOf<String>()
        for ((_, manga) in mangaByKey) {
            manga.genre
                ?.map { it.trim().lowercase() }
                ?.filter { it.isNotBlank() }
                ?.let { allTags.addAll(it) }
        }
        return allTags.size
    }
}
// KMK <--
