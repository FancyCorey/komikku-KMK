package tachiyomi.domain.taste.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.taste.model.CrossSourceGroupPrimary
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

    // KMK --> v0.7.29: live Flow of all manga tastes for reactive Loved Manga screen
    fun getAllMangaTastesAsFlow(): Flow<List<MangaTaste>>
    // KMK <--

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

    // KMK v0.8.20: atomically deletes every link row AND the primary-version row for [groupId] in one
    // transaction, so "ungroup" can never leave a dangling primary pointing at a group with no links
    // (the previous two-call sequence in LovedMangaScreenModel.ungroup() was not atomic). Used by the
    // Undo Journal's snapshot-before/commit-after-transaction-success restore contract.
    suspend fun deleteCrossSourceGroupCompletely(groupId: String)

    // KMK v0.8.20: typed restore for the group-action Undo Journal. Applies exactly the previous-state
    // writes/deletes for links and primaries captured in a GroupJournalEntry snapshot, atomically -- so
    // an Undo either fully reconstructs the previous state or changes nothing at all if it fails
    // partway through. Never a generic rollback: every argument is a concrete, typed row list.
    suspend fun restoreCrossSourceGroupState(
        linkUpserts: List<CrossSourceMangaLink>,
        linkDeletes: List<Pair<Long, String>>,
        primaryUpserts: List<CrossSourceGroupPrimary>,
        primaryDeletes: List<String>,
    )

    // KMK <--

    // --- manga_cross_source_group_primary --- KMK --> v0.8.0

    suspend fun getCrossSourceGroupPrimary(groupId: String): CrossSourceGroupPrimary?

    suspend fun getAllCrossSourceGroupPrimaries(): List<CrossSourceGroupPrimary>

    suspend fun upsertCrossSourceGroupPrimary(primary: CrossSourceGroupPrimary)

    suspend fun deleteCrossSourceGroupPrimary(groupId: String)

    suspend fun deleteAllCrossSourceGroupPrimaries()

    // KMK <--

    // --- recommendation_disabled_source ---

    suspend fun getAllDisabledSourceIds(): List<Long>

    fun getAllDisabledSourceIdsAsFlow(): Flow<List<Long>>

    suspend fun disableSource(sourceId: Long)

    suspend fun enableSource(sourceId: Long)
}
// KMK <--
