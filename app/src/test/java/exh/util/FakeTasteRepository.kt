package exh.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import tachiyomi.domain.taste.model.CrossSourceGroupPrimary
import tachiyomi.domain.taste.model.CrossSourceMangaLink
import tachiyomi.domain.taste.model.MangaTaste
import tachiyomi.domain.taste.model.TagAlias
import tachiyomi.domain.taste.model.TagTaste
import tachiyomi.domain.taste.repository.TasteRepository

// KMK v0.8.19 -->
/**
 * Minimal in-memory [TasteRepository] fake, keyed the same way the real DB-backed implementation is
 * (both by [MangaTaste.mangaId] and by (source, url)), so [EvaluationModeUndoService.restoreOne] and
 * [GroupUndoService.undo] can be exercised against real
 * [tachiyomi.domain.taste.interactor.GetMangaTaste]/[tachiyomi.domain.taste.interactor.SetMangaTaste]/
 * [tachiyomi.domain.taste.interactor.ClearMangaTaste]/cross-source-link interactors without a real
 * database. manga_taste and manga_cross_source_link/manga_cross_source_group_primary operations are
 * implemented with real semantics; tag/alias/disabled-source members are unused by either Undo Journal
 * and throw if called.
 */
class FakeTasteRepository : TasteRepository {

    private val byMangaId = mutableMapOf<Long, MangaTaste>()
    private val tastesFlow = MutableStateFlow<List<MangaTaste>>(emptyList())

    private fun key(source: Long, url: String) = byMangaId.values.find { it.source == source && it.url == url }

    override suspend fun getMangaTaste(mangaId: Long): MangaTaste? = byMangaId[mangaId]

    override fun getMangaTasteAsFlow(mangaId: Long): Flow<MangaTaste?> = throw NotImplementedError()

    override suspend fun getMangaTaste(source: Long, url: String): MangaTaste? = key(source, url)

    override fun getMangaTasteAsFlow(source: Long, url: String): Flow<MangaTaste?> = throw NotImplementedError()

    override suspend fun getAllMangaTastes(): List<MangaTaste> = byMangaId.values.toList()

    override fun getAllMangaTastesAsFlow(): Flow<List<MangaTaste>> = tastesFlow

    override suspend fun upsertMangaTaste(taste: MangaTaste) {
        byMangaId[taste.mangaId] = taste
        tastesFlow.value = byMangaId.values.toList()
    }

    override suspend fun deleteMangaTaste(mangaId: Long) {
        byMangaId.remove(mangaId)
        tastesFlow.value = byMangaId.values.toList()
    }

    override suspend fun deleteMangaTaste(source: Long, url: String) {
        val existing = key(source, url) ?: return
        byMangaId.remove(existing.mangaId)
        tastesFlow.value = byMangaId.values.toList()
    }

    override suspend fun deleteAllMangaTastes() {
        byMangaId.clear()
        tastesFlow.value = emptyList()
    }

    override suspend fun getTagTaste(normalizedTag: String): TagTaste? = throw NotImplementedError()
    override suspend fun getAllTagTastes(): List<TagTaste> = throw NotImplementedError()
    override fun getAllTagTastesAsFlow(): Flow<List<TagTaste>> = throw NotImplementedError()
    override suspend fun upsertTagTaste(tagTaste: TagTaste) = throw NotImplementedError()
    override suspend fun deleteTagTaste(normalizedTag: String) = throw NotImplementedError()

    override suspend fun getAllTagAliases(): List<TagAlias> = throw NotImplementedError()
    override suspend fun getTagAliasByNormalized(normalizedAlias: String): TagAlias? = throw NotImplementedError()
    override suspend fun upsertTagAlias(alias: TagAlias) = throw NotImplementedError()
    override suspend fun deleteTagAlias(alias: String) = throw NotImplementedError()

    // KMK v0.8.20: real in-memory semantics for the cross-source link/primary tables, so
    // GroupUndoServiceRestoreTest can exercise real merge/remove/ungroup/restore interactor chains.
    private val links = mutableMapOf<Pair<Long, String>, CrossSourceMangaLink>()
    private val primaries = mutableMapOf<String, CrossSourceGroupPrimary>()

    /**
     * Test hook simulating a mid-transaction failure: when non-null, [restoreCrossSourceGroupState]
     * and [deleteCrossSourceGroupCompletely] throw this after computing their writes but BEFORE
     * committing any of them, verifying that a failed "transaction" leaves the fake's state completely
     * unchanged (the same atomicity the real SQLDelight `inTransaction = true` block provides).
     */
    var transactionFailure: RuntimeException? = null

    override suspend fun getCrossSourceMangaLinksByGroupId(groupId: String): List<CrossSourceMangaLink> =
        links.values.filter { it.groupId == groupId }

    override suspend fun getCrossSourceMangaLinkBySourceUrl(source: Long, url: String): CrossSourceMangaLink? =
        links[source to url]

    override suspend fun getAllCrossSourceMangaLinks(): List<CrossSourceMangaLink> = links.values.toList()

    override suspend fun upsertCrossSourceMangaLinks(links: List<CrossSourceMangaLink>) {
        links.forEach { this.links[it.source to it.url] = it }
    }

    override suspend fun deleteCrossSourceMangaLink(source: Long, url: String) {
        links.remove(source to url)
    }

    override suspend fun deleteCrossSourceMangaLinksByGroupId(groupId: String) {
        links.entries.removeAll { it.value.groupId == groupId }
    }

    override suspend fun deleteAllCrossSourceMangaLinks() {
        links.clear()
    }

    override suspend fun deleteCrossSourceGroupCompletely(groupId: String) {
        transactionFailure?.let { throw it }
        links.entries.removeAll { it.value.groupId == groupId }
        primaries.remove(groupId)
    }

    override suspend fun restoreCrossSourceGroupState(
        linkUpserts: List<CrossSourceMangaLink>,
        linkDeletes: List<Pair<Long, String>>,
        primaryUpserts: List<CrossSourceGroupPrimary>,
        primaryDeletes: List<String>,
    ) {
        transactionFailure?.let { throw it }
        linkUpserts.forEach { links[it.source to it.url] = it }
        linkDeletes.forEach { links.remove(it) }
        primaryUpserts.forEach { primaries[it.groupId] = it }
        primaryDeletes.forEach { primaries.remove(it) }
    }

    override suspend fun getCrossSourceGroupPrimary(groupId: String): CrossSourceGroupPrimary? = primaries[groupId]
    override suspend fun getAllCrossSourceGroupPrimaries(): List<CrossSourceGroupPrimary> = primaries.values.toList()
    override suspend fun upsertCrossSourceGroupPrimary(primary: CrossSourceGroupPrimary) {
        primaries[primary.groupId] = primary
    }
    override suspend fun deleteCrossSourceGroupPrimary(groupId: String) {
        primaries.remove(groupId)
    }
    override suspend fun deleteAllCrossSourceGroupPrimaries() {
        primaries.clear()
    }

    override suspend fun getAllDisabledSourceIds(): List<Long> = throw NotImplementedError()
    override fun getAllDisabledSourceIdsAsFlow(): Flow<List<Long>> = throw NotImplementedError()
    override suspend fun disableSource(sourceId: Long) = throw NotImplementedError()
    override suspend fun enableSource(sourceId: Long) = throw NotImplementedError()
}
// KMK <--
