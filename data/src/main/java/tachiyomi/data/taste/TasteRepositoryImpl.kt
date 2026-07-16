package tachiyomi.data.taste

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.taste.model.CrossSourceGroupPrimary
import tachiyomi.domain.taste.model.CrossSourceMangaLink
import tachiyomi.domain.taste.model.MangaTaste
import tachiyomi.domain.taste.model.TagAlias
import tachiyomi.domain.taste.model.TagTaste
import tachiyomi.domain.taste.repository.TasteRepository

// KMK -->
class TasteRepositoryImpl(
    private val handler: DatabaseHandler,
) : TasteRepository {

    // region manga_taste

    override suspend fun getMangaTaste(mangaId: Long): MangaTaste? {
        return handler.awaitOneOrNull {
            manga_tasteQueries.getByMangaId(mangaId, mangaTasteMapper)
        }
    }

    override fun getMangaTasteAsFlow(mangaId: Long): Flow<MangaTaste?> {
        return handler.subscribeToOneOrNull {
            manga_tasteQueries.getByMangaId(mangaId, mangaTasteMapper)
        }
    }

    override suspend fun getAllMangaTastes(): List<MangaTaste> {
        return handler.awaitList {
            manga_tasteQueries.getAll(mangaTasteMapper)
        }
    }

    // KMK --> v0.7.29: reactive Flow for live Loved Manga updates
    override fun getAllMangaTastesAsFlow(): Flow<List<MangaTaste>> {
        return handler.subscribeToList {
            manga_tasteQueries.getAll(mangaTasteMapper)
        }
    }
    // KMK <--

    override suspend fun getMangaTaste(source: Long, url: String): MangaTaste? {
        return handler.awaitOneOrNull {
            manga_tasteQueries.getBySourceUrl(source, url, mangaTasteMapper)
        }
    }

    override fun getMangaTasteAsFlow(source: Long, url: String): Flow<MangaTaste?> {
        return handler.subscribeToOneOrNull {
            manga_tasteQueries.getBySourceUrl(source, url, mangaTasteMapper)
        }
    }

    override suspend fun upsertMangaTaste(taste: MangaTaste) {
        handler.await(inTransaction = true) {
            // Remove any existing row with the same source+url that has a different manga_id.
            // This handles cross-device restore or different-path navigation giving different local IDs.
            manga_tasteQueries.deleteBySourceUrl(taste.source, taste.url)
            // Insert/update by manga_id (no source+url conflict possible after the delete above).
            manga_tasteQueries.upsert(
                mangaId = taste.mangaId,
                source = taste.source,
                url = taste.url,
                title = taste.title,
                rating = taste.rating.toLong(),
                createdAt = taste.createdAt,
                updatedAt = taste.updatedAt,
            )
        }
    }

    override suspend fun deleteMangaTaste(mangaId: Long) {
        handler.await { manga_tasteQueries.delete(mangaId) }
    }

    override suspend fun deleteMangaTaste(source: Long, url: String) {
        handler.await { manga_tasteQueries.deleteBySourceUrl(source, url) }
    }

    override suspend fun deleteAllMangaTastes() {
        handler.await { manga_tasteQueries.deleteAll() }
    }

    // endregion

    // region tag_taste

    override suspend fun getTagTaste(normalizedTag: String): TagTaste? {
        return handler.awaitOneOrNull {
            tag_tasteQueries.getByNormalizedTag(normalizedTag, tagTasteMapper)
        }
    }

    override suspend fun getAllTagTastes(): List<TagTaste> {
        return handler.awaitList {
            tag_tasteQueries.getAll(tagTasteMapper)
        }
    }

    override fun getAllTagTastesAsFlow(): Flow<List<TagTaste>> {
        return handler.subscribeToList {
            tag_tasteQueries.getAllAsFlow(tagTasteMapper)
        }
    }

    override suspend fun upsertTagTaste(tagTaste: TagTaste) {
        handler.await(inTransaction = true) {
            tag_tasteQueries.upsert(
                normalizedTag = tagTaste.normalizedTag,
                displayName = tagTaste.displayName,
                preference = tagTaste.preference.toLong(),
                createdAt = tagTaste.createdAt,
                updatedAt = tagTaste.updatedAt,
            )
        }
    }

    override suspend fun deleteTagTaste(normalizedTag: String) {
        handler.await { tag_tasteQueries.delete(normalizedTag) }
    }

    // endregion

    // region tag_alias

    override suspend fun getAllTagAliases(): List<TagAlias> {
        return handler.awaitList {
            tag_aliasQueries.getAll(tagAliasMapper)
        }
    }

    override suspend fun getTagAliasByNormalized(normalizedAlias: String): TagAlias? {
        return handler.awaitOneOrNull {
            tag_aliasQueries.getByNormalizedAlias(normalizedAlias, tagAliasMapper)
        }
    }

    override suspend fun upsertTagAlias(alias: TagAlias) {
        handler.await(inTransaction = true) {
            tag_aliasQueries.upsert(
                alias = alias.alias,
                normalizedAlias = alias.normalizedAlias,
                groupKey = alias.groupKey,
                displayName = alias.displayName,
            )
        }
    }

    override suspend fun deleteTagAlias(alias: String) {
        handler.await { tag_aliasQueries.delete(alias) }
    }

    // endregion

    // KMK --> v0.7.0: Phase 4 – manga_cross_source_link region

    override suspend fun getCrossSourceMangaLinksByGroupId(groupId: String): List<CrossSourceMangaLink> {
        return handler.awaitList {
            manga_cross_source_linkQueries.getByGroupId(groupId, crossSourceMangaLinkMapper)
        }
    }

    override suspend fun getCrossSourceMangaLinkBySourceUrl(source: Long, url: String): CrossSourceMangaLink? {
        return handler.awaitOneOrNull {
            manga_cross_source_linkQueries.getBySourceUrl(source, url, crossSourceMangaLinkMapper)
        }
    }

    override suspend fun getAllCrossSourceMangaLinks(): List<CrossSourceMangaLink> {
        return handler.awaitList {
            manga_cross_source_linkQueries.getAll(crossSourceMangaLinkMapper)
        }
    }

    override suspend fun upsertCrossSourceMangaLinks(links: List<CrossSourceMangaLink>) {
        handler.await(inTransaction = true) {
            for (link in links) {
                manga_cross_source_linkQueries.upsert(
                    source = link.source,
                    url = link.url,
                    groupId = link.groupId,
                    title = link.title,
                    createdAt = link.createdAt,
                    updatedAt = link.updatedAt,
                )
            }
        }
    }

    override suspend fun deleteCrossSourceMangaLink(source: Long, url: String) {
        handler.await { manga_cross_source_linkQueries.deleteBySourceUrl(source, url) }
    }

    override suspend fun deleteCrossSourceMangaLinksByGroupId(groupId: String) {
        handler.await { manga_cross_source_linkQueries.deleteByGroupId(groupId) }
    }

    override suspend fun deleteAllCrossSourceMangaLinks() {
        handler.await { manga_cross_source_linkQueries.deleteAll() }
    }

    // endregion KMK <--

    // KMK --> v0.8.0: manga_cross_source_group_primary region

    override suspend fun getCrossSourceGroupPrimary(groupId: String): CrossSourceGroupPrimary? {
        return handler.awaitOneOrNull {
            manga_cross_source_group_primaryQueries.getByGroupId(groupId, crossSourceGroupPrimaryMapper)
        }
    }

    override suspend fun getAllCrossSourceGroupPrimaries(): List<CrossSourceGroupPrimary> {
        return handler.awaitList {
            manga_cross_source_group_primaryQueries.getAll(crossSourceGroupPrimaryMapper)
        }
    }

    override suspend fun upsertCrossSourceGroupPrimary(primary: CrossSourceGroupPrimary) {
        handler.await(inTransaction = true) {
            manga_cross_source_group_primaryQueries.upsert(
                groupId = primary.groupId,
                source = primary.source,
                url = primary.url,
                updatedAt = primary.updatedAt,
            )
        }
    }

    override suspend fun deleteCrossSourceGroupPrimary(groupId: String) {
        handler.await { manga_cross_source_group_primaryQueries.deleteByGroupId(groupId) }
    }

    override suspend fun deleteAllCrossSourceGroupPrimaries() {
        handler.await { manga_cross_source_group_primaryQueries.deleteAll() }
    }

    // endregion KMK <--

    // region recommendation_disabled_source

    override suspend fun getAllDisabledSourceIds(): List<Long> {
        return handler.awaitList { recommendation_disabled_sourceQueries.getAll() }
    }

    override fun getAllDisabledSourceIdsAsFlow(): Flow<List<Long>> {
        return handler.subscribeToList { recommendation_disabled_sourceQueries.getAllAsFlow() }
    }

    override suspend fun disableSource(sourceId: Long) {
        handler.await { recommendation_disabled_sourceQueries.insert(sourceId) }
    }

    override suspend fun enableSource(sourceId: Long) {
        handler.await { recommendation_disabled_sourceQueries.delete(sourceId) }
    }

    // endregion
}

private val mangaTasteMapper = {
        mangaId: Long,
        source: Long,
        url: String,
        title: String,
        rating: Long,
        createdAt: Long,
        updatedAt: Long,
    ->
    MangaTaste(
        mangaId = mangaId,
        source = source,
        url = url,
        title = title,
        rating = rating.toInt(),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private val tagTasteMapper = {
        normalizedTag: String,
        displayName: String,
        preference: Long,
        createdAt: Long,
        updatedAt: Long,
    ->
    TagTaste(
        normalizedTag = normalizedTag,
        displayName = displayName,
        preference = preference.toInt(),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private val tagAliasMapper = { alias: String, normalizedAlias: String, groupKey: String, displayName: String ->
    TagAlias(
        alias = alias,
        normalizedAlias = normalizedAlias,
        groupKey = groupKey,
        displayName = displayName,
    )
}

// KMK --> v0.7.0: Phase 4
private val crossSourceMangaLinkMapper = {
        source: Long,
        url: String,
        groupId: String,
        title: String,
        createdAt: Long,
        updatedAt: Long,
    ->
    CrossSourceMangaLink(
        source = source,
        url = url,
        groupId = groupId,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
// KMK <--

// KMK --> v0.8.0
private val crossSourceGroupPrimaryMapper = { groupId: String, source: Long, url: String, updatedAt: Long ->
    CrossSourceGroupPrimary(
        groupId = groupId,
        source = source,
        url = url,
        updatedAt = updatedAt,
    )
}
// KMK <--
// KMK <--
