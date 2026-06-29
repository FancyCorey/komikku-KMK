package eu.kanade.tachiyomi.data.backup.restore.restorers

// KMK -->
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BackupCrossSourceMangaLink
import eu.kanade.tachiyomi.data.backup.models.BackupDisabledRecommendationSource
import eu.kanade.tachiyomi.data.backup.models.BackupMangaSourceQualitySignal
import eu.kanade.tachiyomi.data.backup.models.BackupMangaTaste
import eu.kanade.tachiyomi.data.backup.models.BackupSeenMangaKey
import eu.kanade.tachiyomi.data.backup.models.BackupTagAlias
import eu.kanade.tachiyomi.data.backup.models.BackupTagTaste
import exh.recs.SeenRecommendationMangaStore
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.taste.interactor.GetCrossSourceMangaLinks
import tachiyomi.domain.taste.interactor.GetDisabledRecommendationSources
import tachiyomi.domain.taste.interactor.GetMangaSourceQualitySignals
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.interactor.GetTagAliases
import tachiyomi.domain.taste.interactor.GetTagTaste
import tachiyomi.domain.taste.interactor.SetRecommendationSourceEnabled
import tachiyomi.domain.taste.interactor.UpsertCrossSourceMangaLinks
import tachiyomi.domain.taste.interactor.UpsertMangaSourceQualitySignal
import tachiyomi.domain.taste.interactor.UpsertTagAlias
import tachiyomi.domain.taste.model.CrossSourceMangaLink
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.MangaSourceQualitySignal
import tachiyomi.domain.taste.model.MangaTaste
import tachiyomi.domain.taste.model.TagAlias
import tachiyomi.domain.taste.model.TagPreference
import tachiyomi.domain.taste.model.TagTaste
import tachiyomi.domain.taste.model.normalizeTag
import tachiyomi.domain.taste.repository.TasteRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TasteRestorer(
    private val tasteRepository: TasteRepository = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val getMangaTaste: GetMangaTaste = Injekt.get(),
    private val getTagTaste: GetTagTaste = Injekt.get(),
    private val getTagAliases: GetTagAliases = Injekt.get(),
    private val getDisabledSources: GetDisabledRecommendationSources = Injekt.get(),
    private val setSourceEnabled: SetRecommendationSourceEnabled = Injekt.get(),
    private val upsertTagAlias: UpsertTagAlias = Injekt.get(),
    // KMK --> v0.7.0: Phase 4
    private val getCrossSourceMangaLinks: GetCrossSourceMangaLinks = Injekt.get(),
    private val upsertCrossSourceMangaLinks: UpsertCrossSourceMangaLinks = Injekt.get(),
    // KMK <--
    // KMK --> v0.7.16: Best Version quality signals
    private val getMangaSourceQualitySignals: GetMangaSourceQualitySignals = Injekt.get(),
    private val upsertMangaSourceQualitySignal: UpsertMangaSourceQualitySignal = Injekt.get(),
    // KMK <--
    // KMK --> v0.7.28: seen manga keys
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    // KMK <--
) {

    suspend fun restoreMangaTastes(backupMangaTastes: List<BackupMangaTaste>): List<String> {
        if (backupMangaTastes.isEmpty()) return emptyList()
        val errors = mutableListOf<String>()
        val now = System.currentTimeMillis()
        backupMangaTastes.forEach { backup ->
            try {
                restoreOneMangaTaste(backup, now)
            } catch (e: Exception) {
                errors.add("Taste rating for '${backup.title}': ${e.message}")
            }
        }
        return errors
    }

    private suspend fun restoreOneMangaTaste(backup: BackupMangaTaste, now: Long) {
        val rating = MangaRating.fromValue(backup.rating) ?: return
        // Manga row IDs are device-local — resolve by (url, source), which is stable across devices
        val localMangaId = getManga.await(backup.url, backup.source)?.id
            ?: getManga.await(backup.mangaId)
                ?.takeIf { it.url == backup.url && it.source == backup.source }
                ?.id
            ?: return
        val existing = getMangaTaste.await(localMangaId)
        if (existing == null || existing.updatedAt < backup.updatedAt) {
            tasteRepository.upsertMangaTaste(
                MangaTaste(
                    mangaId = localMangaId,
                    source = backup.source,
                    url = backup.url,
                    title = backup.title,
                    rating = rating.value,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = backup.updatedAt,
                ),
            )
        }
    }

    suspend fun restoreTagTastes(backupTagTastes: List<BackupTagTaste>): List<String> {
        if (backupTagTastes.isEmpty()) return emptyList()
        val errors = mutableListOf<String>()
        val now = System.currentTimeMillis()
        backupTagTastes.forEach { backup ->
            try {
                restoreOneTagTaste(backup, now)
            } catch (e: Exception) {
                errors.add("Tag preference '${backup.displayName}': ${e.message}")
            }
        }
        return errors
    }

    private suspend fun restoreOneTagTaste(backup: BackupTagTaste, now: Long) {
        TagPreference.fromValue(backup.preference) ?: return
        val normalized = backup.displayName.normalizeTag()
        val existing = getTagTaste.await(normalized)
        if (existing == null || existing.updatedAt < backup.updatedAt) {
            tasteRepository.upsertTagTaste(
                TagTaste(
                    normalizedTag = normalized,
                    displayName = backup.displayName,
                    preference = backup.preference,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = backup.updatedAt,
                ),
            )
        }
    }

    suspend fun restoreTagAliases(backupTagAliases: List<BackupTagAlias>): List<String> {
        if (backupTagAliases.isEmpty()) return emptyList()
        val errors = mutableListOf<String>()
        val existingAliases = getTagAliases.awaitAll()
            .map { it.normalizedAlias }
            .toHashSet()
        backupTagAliases.forEach { backup ->
            try {
                restoreOneTagAlias(backup, existingAliases)
            } catch (e: Exception) {
                errors.add("Tag alias '${backup.alias}': ${e.message}")
            }
        }
        return errors
    }

    private suspend fun restoreOneTagAlias(backup: BackupTagAlias, existingAliases: HashSet<String>) {
        val normalized = backup.alias.normalizeTag()
        if (normalized !in existingAliases) {
            tasteRepository.upsertTagAlias(
                TagAlias(
                    alias = backup.alias,
                    normalizedAlias = normalized,
                    groupKey = backup.groupKey,
                    displayName = backup.displayName,
                ),
            )
        }
    }

    suspend fun restoreDisabledRecommendationSources(
        backupDisabledSources: List<BackupDisabledRecommendationSource>,
    ): List<String> {
        if (backupDisabledSources.isEmpty()) return emptyList()
        val errors = mutableListOf<String>()
        val currentlyDisabled = getDisabledSources.await().toHashSet()
        backupDisabledSources.forEach { backup ->
            try {
                restoreOneDisabledSource(backup, currentlyDisabled)
            } catch (e: Exception) {
                errors.add("Disabled recommendation source ${backup.sourceId}: ${e.message}")
            }
        }
        return errors
    }

    private suspend fun restoreOneDisabledSource(
        backup: BackupDisabledRecommendationSource,
        currentlyDisabled: HashSet<Long>,
    ) {
        if (backup.sourceId !in currentlyDisabled) {
            setSourceEnabled.await(backup.sourceId, enabled = false)
        }
    }

    // KMK --> v0.7.16: Best Version quality signals
    suspend fun restoreMangaSourceQualitySignals(backupSignals: List<BackupMangaSourceQualitySignal>): List<String> {
        if (backupSignals.isEmpty()) return emptyList()
        val errors = mutableListOf<String>()
        val existing = getMangaSourceQualitySignals.getAll()
            .map { Triple(it.originSourceId, it.originUrl, it.selectedSourceId) }
            .toHashSet()
        backupSignals.forEach { backup ->
            try {
                val key = Triple(backup.originSourceId, backup.originUrl, backup.selectedSourceId)
                if (key !in existing) {
                    upsertMangaSourceQualitySignal.insert(
                        MangaSourceQualitySignal(
                            id = 0,
                            originSourceId = backup.originSourceId,
                            originUrl = backup.originUrl,
                            originTitle = backup.originTitle,
                            selectedSourceId = backup.selectedSourceId,
                            selectedUrl = backup.selectedUrl,
                            selectedTitle = backup.selectedTitle,
                            selectedSourceName = backup.selectedSourceName,
                            comparedCandidatesJson = "[]",
                            chapterNumber = if (backup.chapterNumber == 0.0) null else backup.chapterNumber,
                            chapterName = backup.chapterName,
                            sampleSize = 5,
                            sampledPagesJson = "[]",
                            selectedAt = backup.selectedAt,
                            qualitySignalVersion = backup.qualitySignalVersion,
                        ),
                    )
                }
            } catch (e: Exception) {
                errors.add("Source quality signal '${backup.originTitle}' → '${backup.selectedSourceName}': ${e.message}")
            }
        }
        return errors
    }
    // KMK <--

    // KMK --> v0.7.28: seen manga keys — additive union restore (never clears existing dismissals)
    fun restoreSeenMangaKeys(backupKeys: List<BackupSeenMangaKey>): List<String> {
        if (backupKeys.isEmpty()) return emptyList()
        val pref = sourcePreferences.seenRecommendationMangaKeys()
        val current = SeenRecommendationMangaStore.parse(pref.get()).toMutableSet()
        val beforeSize = current.size
        backupKeys.forEach { backup ->
            val parsed = SeenRecommendationMangaStore.parse(backup.key)
            current.addAll(parsed)
        }
        if (current.size != beforeSize) {
            pref.set(SeenRecommendationMangaStore.serialize(current))
        }
        return emptyList()
    }
    // KMK <--

    // KMK --> v0.7.0: Phase 4 – cross-source link groups
    suspend fun restoreCrossSourceMangaLinks(backupLinks: List<BackupCrossSourceMangaLink>): List<String> {
        if (backupLinks.isEmpty()) return emptyList()
        val errors = mutableListOf<String>()
        val now = System.currentTimeMillis()
        // Group links by groupId so we can upsert atomically per group
        val byGroup = backupLinks.groupBy { it.groupId }
        for ((groupId, groupLinks) in byGroup) {
            try {
                val existing = getCrossSourceMangaLinks.awaitByGroupId(groupId)
                    .associateBy { it.source to it.url }
                val toUpsert = groupLinks.mapNotNull { backup ->
                    val key = backup.source to backup.url
                    val ex = existing[key]
                    if (ex != null && ex.updatedAt >= backup.updatedAt) return@mapNotNull null
                    CrossSourceMangaLink(
                        source = backup.source,
                        url = backup.url,
                        groupId = backup.groupId,
                        title = backup.title,
                        createdAt = ex?.createdAt ?: now,
                        updatedAt = backup.updatedAt,
                    )
                }
                if (toUpsert.isNotEmpty()) upsertCrossSourceMangaLinks.await(toUpsert)
            } catch (e: Exception) {
                errors.add("Cross-source link group '$groupId': ${e.message}")
            }
        }
        return errors
    }
    // KMK <--
}
// KMK <--
