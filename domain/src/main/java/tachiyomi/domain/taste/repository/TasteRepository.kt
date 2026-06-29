package tachiyomi.domain.taste.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.taste.model.CrossSourceMangaLink
import tachiyomi.domain.taste.model.MangaTaste
import tachiyomi.domain.taste.model.TagAlias
import tachiyomi.domain.taste.model.TagTaste

// KMK -->
interface TasteRepository {

    // --- manga_taste ---

    suspend fun getMangaTaste(mangaId: Long): MangaTaste?

    fun getMangaTasteAsFlow(mangaId: Long): Flow<MangaTaste?>

    suspend fun getMangaTaste(source: Long, url: String): MangaTaste?

    fun getMangaTasteAsFlow(source: Long, url: String): Flow<MangaTaste?>

    suspend fun getAllMangaTastes(): List<MangaTaste>

    suspend fun upsertMangaTaste(taste: MangaTaste)

    suspend fun deleteMangaTaste(mangaId: Long)

    suspend fun deleteMangaTaste(source: Long, url: String)

    suspend fun deleteAllMangaTastes()

    // --- tag_taste ---

    suspend fun getTagTaste(normalizedTag: String): TagTaste?

    suspend fun getAllTagTastes(): List<TagTaste>

    fun getAllTagTastesAsFlow(): Flow<List<TagTaste>>

    suspend fun upsertTagTaste(tagTaste: TagTaste)

    suspend fun deleteTagTaste(normalizedTag: String)

    // --- tag_alias ---

    suspend fun getAllTagAliases(): List<TagAlias>

    suspend fun getTagAliasByNormalized(normalizedAlias: String): TagAlias?

    suspend fun upsertTagAlias(alias: TagAlias)

    suspend fun deleteTagAlias(alias: String)

    // --- manga_cross_source_link --- KMK --> v0.7.0: Phase 4

    suspend fun getCrossSourceMangaLinksByGroupId(groupId: String): List<CrossSourceMangaLink>

    suspend fun getCrossSourceMangaLinkBySourceUrl(source: Long, url: String): CrossSourceMangaLink?

    suspend fun getAllCrossSourceMangaLinks(): List<CrossSourceMangaLink>

    suspend fun upsertCrossSourceMangaLinks(links: List<CrossSourceMangaLink>)

    suspend fun deleteCrossSourceMangaLink(source: Long, url: String)

    suspend fun deleteCrossSourceMangaLinksByGroupId(groupId: String)

    suspend fun deleteAllCrossSourceMangaLinks()

    // KMK <--

    // --- recommendation_disabled_source ---

    suspend fun getAllDisabledSourceIds(): List<Long>

    fun getAllDisabledSourceIdsAsFlow(): Flow<List<Long>>

    suspend fun disableSource(sourceId: Long)

    suspend fun enableSource(sourceId: Long)
}
// KMK <--
