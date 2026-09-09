package exh.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import tachiyomi.domain.taste.model.CrossSourceGroupPrimary
import tachiyomi.domain.taste.model.CrossSourceIdentityDecision
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionPolicy
import tachiyomi.domain.taste.model.CrossSourceIdentityPair
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

    var tasteReadFailure: Throwable? = null
    var tasteWriteFailure: Throwable? = null

    private val byMangaId = mutableMapOf<Long, MangaTaste>()
    private val tastesFlow = MutableStateFlow<List<MangaTaste>>(emptyList())

    private fun key(source: Long, url: String) = byMangaId.values.find { it.source == source && it.url == url }

    override suspend fun getMangaTaste(mangaId: Long): MangaTaste? {
        tasteReadFailure?.let { throw it }
        return byMangaId[mangaId]
    }

    override fun getMangaTasteAsFlow(mangaId: Long): Flow<MangaTaste?> = throw NotImplementedError()

    override suspend fun getMangaTaste(source: Long, url: String): MangaTaste? {
        tasteReadFailure?.let { throw it }
        return key(source, url)
    }

    override fun getMangaTasteAsFlow(source: Long, url: String): Flow<MangaTaste?> = throw NotImplementedError()

    override suspend fun getAllMangaTastes(): List<MangaTaste> = byMangaId.values.toList()

    override fun getAllMangaTastesAsFlow(): Flow<List<MangaTaste>> = tastesFlow

    override suspend fun upsertMangaTaste(taste: MangaTaste) {
        tasteWriteFailure?.let { throw it }
        byMangaId[taste.mangaId] = taste
        tastesFlow.value = byMangaId.values.toList()
    }

    override suspend fun deleteMangaTaste(mangaId: Long) {
        tasteWriteFailure?.let { throw it }
        byMangaId.remove(mangaId)
        tastesFlow.value = byMangaId.values.toList()
    }

    override suspend fun deleteMangaTaste(source: Long, url: String) {
        tasteWriteFailure?.let { throw it }
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
    private val identityDecisions = mutableMapOf<CrossSourceIdentityPair, CrossSourceIdentityDecision>()

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

    override suspend fun getCrossSourceIdentityDecision(pair: CrossSourceIdentityPair): CrossSourceIdentityDecision? =
        identityDecisions[CrossSourceIdentityDecisionPolicy.canonicalPair(pair.left, pair.right)]

    override suspend fun getAllCrossSourceIdentityDecisions(): List<CrossSourceIdentityDecision> =
        identityDecisions.values.toList()

    override suspend fun upsertCrossSourceIdentityDecisions(decisions: List<CrossSourceIdentityDecision>) {
        decisions.map(CrossSourceIdentityDecisionPolicy::canonicalize).forEach { identityDecisions[it.pair] = it }
    }

    override suspend fun replaceCrossSourceIdentityDecision(
        expected: CrossSourceIdentityDecision?,
        replacement: CrossSourceIdentityDecision?,
    ): Boolean {
        transactionFailure?.let { throw it }
        val pair = replacement?.pair ?: expected?.pair ?: return false
        val canonicalPair = CrossSourceIdentityDecisionPolicy.canonicalPair(pair.left, pair.right)
        if (identityDecisions[canonicalPair] != expected?.let(CrossSourceIdentityDecisionPolicy::canonicalize)) return false
        if (replacement == null) identityDecisions.remove(canonicalPair) else identityDecisions[canonicalPair] = CrossSourceIdentityDecisionPolicy.canonicalize(replacement)
        return true
    }

    override suspend fun replaceCrossSourceIdentityDecisions(
        replacements: List<tachiyomi.domain.taste.model.CrossSourceIdentityReplacement>,
    ): Boolean {
        transactionFailure?.let { throw it }
        if (replacements.isEmpty()) return false
        val canonical = replacements.map { change ->
            val pair = change.replacement?.pair ?: change.expected?.pair ?: return false
            Triple(
                CrossSourceIdentityDecisionPolicy.canonicalPair(pair.left, pair.right),
                change.expected?.let(CrossSourceIdentityDecisionPolicy::canonicalize),
                change.replacement?.let(CrossSourceIdentityDecisionPolicy::canonicalize),
            )
        }
        if (canonical.map { it.first }.distinct().size != canonical.size) return false
        if (canonical.any { (pair, expected, _) -> identityDecisions[pair] != expected }) return false
        canonical.forEach { (pair, _, replacement) ->
            if (replacement == null) identityDecisions.remove(pair) else identityDecisions[pair] = replacement
        }
        return true
    }

    override suspend fun tombstoneAllCrossSourceIdentityDecisions(updatedAt: Long) {
        identityDecisions.replaceAll { _, row ->
            if (row.deletedAt == null && row.updatedAt <= updatedAt) CrossSourceIdentityDecisionPolicy.tombstone(row, updatedAt) else row
        }
    }

    override suspend fun getAllDisabledSourceIds(): List<Long> = throw NotImplementedError()
    override fun getAllDisabledSourceIdsAsFlow(): Flow<List<Long>> = throw NotImplementedError()
    override suspend fun disableSource(sourceId: Long) = throw NotImplementedError()
    override suspend fun enableSource(sourceId: Long) = throw NotImplementedError()
}
// KMK <--
