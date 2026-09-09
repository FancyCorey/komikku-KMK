package eu.kanade.tachiyomi.data.backup.restore.restorers

// KMK -->
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BackupAlternateSourceBridge
import eu.kanade.tachiyomi.data.backup.models.BackupAlternateSourceBridgeMapping
import eu.kanade.tachiyomi.data.backup.models.BackupCrossSourceGroupPrimary
import eu.kanade.tachiyomi.data.backup.models.BackupCrossSourceIdentityDecision
import eu.kanade.tachiyomi.data.backup.models.BackupCrossSourceMangaLink
import eu.kanade.tachiyomi.data.backup.models.BackupDisabledRecommendationSource
import eu.kanade.tachiyomi.data.backup.models.BackupMangaSourceQualitySignal
import eu.kanade.tachiyomi.data.backup.models.BackupMangaTaste
import eu.kanade.tachiyomi.data.backup.models.BackupSavedFocusMode
import eu.kanade.tachiyomi.data.backup.models.BackupSeenMangaKey
import eu.kanade.tachiyomi.data.backup.models.BackupTagAlias
import eu.kanade.tachiyomi.data.backup.models.BackupTagTaste
import exh.recs.SavedFocusMode
import exh.recs.SavedFocusModeStore
import exh.recs.SeenRecommendationMangaStore
import kotlinx.coroutines.CancellationException
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.taste.interactor.GetCrossSourceGroupPrimary
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
import tachiyomi.domain.taste.model.CrossSourceGroupPrimary
import tachiyomi.domain.taste.model.CrossSourceMangaLink
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.MangaSourceQualitySignal
import tachiyomi.domain.taste.model.MangaTaste
import tachiyomi.domain.taste.model.TagAlias
import tachiyomi.domain.taste.model.TagPreference
import tachiyomi.domain.taste.model.TagTaste
import tachiyomi.domain.taste.model.normalizeTag
import tachiyomi.domain.taste.repository.AlternateSourceBridgeRepository
import tachiyomi.domain.taste.repository.TasteRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TasteRestorer(
    private val tasteRepository: TasteRepository = Injekt.get(),
    private val alternateSourceBridgeRepository: AlternateSourceBridgeRepository = Injekt.get(),
    private val mangaRepository: MangaRepository = Injekt.get(),
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
    // KMK --> v0.8.1-fix1: user-selected primary version per confirmed link group. Restoring writes
    // through tasteRepository directly (not SetCrossSourceGroupPrimary, which always stamps "now")
    // so the backup's own updatedAt is preserved.
    private val getCrossSourceGroupPrimary: GetCrossSourceGroupPrimary = Injekt.get(),
    // KMK <--
    // KMK --> v0.7.16: Best Version quality signals
    private val getMangaSourceQualitySignals: GetMangaSourceQualitySignals = Injekt.get(),
    private val upsertMangaSourceQualitySignal: UpsertMangaSourceQualitySignal = Injekt.get(),
    // KMK <--
    // KMK v0.8.21-fix2: AUG-14 slice 3 -- saved For You focus modes are a plain preference, not a
    // TasteRepository-owned table, so restoring them needs direct preference access.
    private val sourcePreferences: SourcePreferences = Injekt.get(),
) {

    suspend fun restoreMangaTastes(backupMangaTastes: List<BackupMangaTaste>): List<String> {
        if (backupMangaTastes.isEmpty()) return emptyList()
        val errors = mutableListOf<String>()
        val now = System.currentTimeMillis()
        backupMangaTastes.forEach { backup ->
            try {
                if (!restoreOneMangaTaste(backup, now)) {
                    errors.add("Taste rating for source ${backup.source}: missing or invalid manga identity")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors.add("Taste rating for '${backup.title}': Unknown error")
            }
        }
        return errors
    }

    private suspend fun restoreOneMangaTaste(backup: BackupMangaTaste, now: Long): Boolean {
        val rating = MangaRating.fromValue(backup.rating) ?: return false
        // Manga row IDs are device-local — resolve by (url, source), which is stable across devices
        val localManga = getManga.await(backup.url, backup.source)
            ?: getManga.await(backup.mangaId)
                ?.takeIf { it.url == backup.url && it.source == backup.source }
            ?: restoreMinimalRatedMangaIdentity(backup)
            ?: return false
        val localMangaId = localManga.id
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
        return true
    }

    private suspend fun restoreMinimalRatedMangaIdentity(backup: BackupMangaTaste): Manga? {
        if (!RatedMangaRestoreIdentityPolicy.isValid(backup)) return null
        val minimal = Manga.create().copy(
            source = backup.source,
            url = backup.url,
            ogTitle = backup.title,
            favorite = false,
            initialized = false,
        )
        return mangaRepository.insertNetworkManga(listOf(minimal), updateInfo = false).singleOrNull()
    }

    suspend fun restoreTagTastes(backupTagTastes: List<BackupTagTaste>): List<String> {
        if (backupTagTastes.isEmpty()) return emptyList()
        val errors = mutableListOf<String>()
        val now = System.currentTimeMillis()
        backupTagTastes.forEach { backup ->
            try {
                restoreOneTagTaste(backup, now)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors.add("Tag preference '${backup.displayName}': Unknown error")
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors.add("Tag alias '${backup.alias}': Unknown error")
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors.add("Disabled recommendation source ${backup.sourceId}: Unknown error")
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors.add(
                    "Source quality signal '${backup.originTitle}' → '${backup.selectedSourceName}': Unknown error",
                )
            }
        }
        return errors
    }
    // KMK <--

    // KMK v0.8.21-fix3: R1 correction -- Not Interested is a real MangaRating value now, and no
    // live code path writes new entries into the legacy seenRecommendationMangaKeys preference
    // anymore (MangaScreenModel.markSeen()/clearSeen() were deleted; every write goes through
    // setMangaTaste/clearMangaTaste like any other rating). BackupSeenMangaKey therefore only
    // round-trips through the backup format for OLD backups taken before this correction --
    // read-compatibility only. This restorer backfills a MangaRating.NOT_INTERESTED MangaTaste row
    // per key so every mangaTaste-reading consumer (isNotInterested display, Rated Manga grouping)
    // sees the restored state, using the exact same conflict rule (an existing rating always wins)
    // as the live migration -- see Plan A's "AUG-02 frozen contract", item 3. Runs after
    // restoreMangaTastes() in BackupRestorer.kt's existing call order, so a rating restored from
    // the same backup is already in place for this check.
    suspend fun restoreSeenMangaKeys(backupKeys: List<BackupSeenMangaKey>): List<String> {
        if (backupKeys.isEmpty()) return emptyList()
        val errors = mutableListOf<String>()
        val keys = backupKeys.flatMap { SeenRecommendationMangaStore.parse(it.key) }.toSet()
        keys.forEach { key ->
            try {
                val manga = getManga.await(key.url, key.sourceId) ?: return@forEach
                if (getMangaTaste.await(manga.source, manga.url) != null) return@forEach
                tasteRepository.upsertMangaTaste(
                    MangaTaste(
                        mangaId = manga.id,
                        source = manga.source,
                        url = manga.url,
                        title = manga.title,
                        rating = MangaRating.NOT_INTERESTED.value,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors.add("Not Interested marker for source ${key.sourceId}: Unknown error")
            }
        }
        return errors
    }
    // KMK <--

    // KMK v0.8.21-fix2: AUG-14 slice 3 -- saved For You focus modes. Conflict rule per the frozen
    // contract (Plan D, "Saved custom modes — frozen contract resolved", item 8): same id on both
    // sides keeps whichever has the newer updatedAt, exactly mirroring restoreOneMangaTaste's own
    // idiom elsewhere in this file. Duplicate names are never a conflict -- only a colliding id is.
    fun restoreSavedFocusModes(backupModes: List<BackupSavedFocusMode>) {
        if (backupModes.isEmpty()) return
        val existing = SavedFocusModeStore.parse(sourcePreferences.savedFocusModes().get())
        val existingById = existing.associateBy { it.id }
        val merged = existing.associateBy { it.id }.toMutableMap()
        for (backup in backupModes) {
            val current = existingById[backup.id]
            if (current == null || backup.updatedAt > current.updatedAt) {
                merged[backup.id] = SavedFocusMode(
                    id = backup.id,
                    name = backup.name,
                    includeGroups = backup.groups.toSet(),
                    excludeGroups = backup.excludeGroups.toSet(),
                    matchAll = backup.matchAll,
                    createdAt = backup.createdAt,
                    updatedAt = backup.updatedAt,
                    order = backup.order,
                )
            }
        }
        sourcePreferences.savedFocusModes().set(SavedFocusModeStore.serialize(merged.values.sortedBy { it.order }))
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors.add("Cross-source link group '$groupId': Unknown error")
            }
        }
        return errors
    }
    // KMK <--

    // KMK --> v0.8.1-fix1: user-selected primary version per confirmed link group. Restored after
    // restoreCrossSourceMangaLinks() (call-site ordering in BackupRestorer) so the group links exist
    // first; a primary pointing at a link that failed to restore is still harmless — the display
    // layer already fails open to the grouper's own choice when a stored primary is missing
    // (RatedGroupPrimaryResolver).
    suspend fun restoreCrossSourceGroupPrimaries(backupPrimaries: List<BackupCrossSourceGroupPrimary>): List<String> {
        if (backupPrimaries.isEmpty()) return emptyList()
        val errors = mutableListOf<String>()
        // Merge by groupId: a backup may (incorrectly) contain more than one row per group — keep
        // only the newest per group before comparing against the existing stored primary.
        val byGroup = backupPrimaries
            .filter { CrossSourceGroupPrimaryRestorePolicy.isValid(it) }
            .groupBy { it.groupId }
        for ((groupId, candidates) in byGroup) {
            try {
                val newest = CrossSourceGroupPrimaryRestorePolicy.newestOf(candidates)
                val existing = getCrossSourceGroupPrimary.awaitByGroupId(groupId)
                if (CrossSourceGroupPrimaryRestorePolicy.shouldRestore(existing?.updatedAt, newest.updatedAt)) {
                    // Write directly through the repository (not SetCrossSourceGroupPrimary, which
                    // always stamps "now") so the backup's own updatedAt is preserved — matching
                    // restoreCrossSourceMangaLinks' pattern above and keeping future sync-merge
                    // comparisons meaningful.
                    tasteRepository.upsertCrossSourceGroupPrimary(
                        CrossSourceGroupPrimary(
                            groupId = newest.groupId,
                            source = newest.source,
                            url = newest.url,
                            updatedAt = newest.updatedAt,
                        ),
                    )
                }
                // else: existing primary is newer — keep it, per plan §B3.
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors.add("Primary version for group '$groupId': Unknown error")
            }
        }
        return errors
    }
    // KMK <--

    suspend fun restoreCrossSourceIdentityDecisions(
        backupDecisions: List<BackupCrossSourceIdentityDecision>,
    ): List<String> = CrossSourceIdentityDecisionRestorer(tasteRepository)
        .restore(backupDecisions, System.currentTimeMillis())

    suspend fun restoreAlternateSourceBridges(
        bridges: List<BackupAlternateSourceBridge>,
        mappings: List<BackupAlternateSourceBridgeMapping>,
    ): List<String> = AlternateSourceBridgeRestorer(alternateSourceBridgeRepository)
        .restore(bridges, mappings, System.currentTimeMillis())

    // KMK F2-02 (KFC-V0.8.21-FIX2-CORRECTIVE-RECHECK-AND-RELEASE-PROGRAM): the exact taste-profile
    // restore sequence BackupRestorer.restoreTasteProfile uses (same calls, same order --
    // mangaTastes, tagTastes, tagAliases, disabledSources, crossSourceMangaLinks,
    // crossSourceGroupPrimaries, crossSourceIdentityDecisions, alternateSourceBridges,
    // mangaSourceQualitySignals, seenMangaKeys, savedFocusModes), extracted here so it is a single
    // owned, directly testable function rather than logic duplicated inline inside an
    // Android-Context/BackupNotifier-entangled orchestration method. BackupRestorer now delegates to
    // this instead of re-listing the sequence itself -- this is the change that makes a genuine
    // end-to-end create -> serialize -> decode -> restore-orchestration test possible without
    // mocking Context/BackupNotifier, closing the reopened C0 gap that "the current integrated test
    // ... does not prove the complete ... restore orchestration boundary."
    suspend fun restoreTasteProfileBundle(
        backupMangaTastes: List<BackupMangaTaste>,
        backupTagTastes: List<BackupTagTaste>,
        backupTagAliases: List<BackupTagAlias>,
        backupDisabledSources: List<BackupDisabledRecommendationSource>,
        backupCrossSourceMangaLinks: List<BackupCrossSourceMangaLink>,
        backupCrossSourceGroupPrimaries: List<BackupCrossSourceGroupPrimary>,
        backupCrossSourceIdentityDecisions: List<BackupCrossSourceIdentityDecision>,
        backupAlternateSourceBridges: List<BackupAlternateSourceBridge>,
        backupAlternateSourceBridgeMappings: List<BackupAlternateSourceBridgeMapping>,
        backupMangaSourceQualitySignals: List<BackupMangaSourceQualitySignal>,
        backupSeenMangaKeys: List<BackupSeenMangaKey>,
        backupSavedFocusModes: List<BackupSavedFocusMode>,
    ): List<String> = buildList {
        addAll(restoreMangaTastes(backupMangaTastes))
        addAll(restoreTagTastes(backupTagTastes))
        addAll(restoreTagAliases(backupTagAliases))
        addAll(restoreDisabledRecommendationSources(backupDisabledSources))
        addAll(restoreCrossSourceMangaLinks(backupCrossSourceMangaLinks))
        addAll(restoreCrossSourceGroupPrimaries(backupCrossSourceGroupPrimaries))
        addAll(restoreCrossSourceIdentityDecisions(backupCrossSourceIdentityDecisions))
        addAll(restoreAlternateSourceBridges(backupAlternateSourceBridges, backupAlternateSourceBridgeMappings))
        addAll(restoreMangaSourceQualitySignals(backupMangaSourceQualitySignals))
        addAll(restoreSeenMangaKeys(backupSeenMangaKeys))
        restoreSavedFocusModes(backupSavedFocusModes)
    }
}

internal object RatedMangaRestoreIdentityPolicy {
    private const val MAX_URL_LENGTH = 8_192
    private const val MAX_TITLE_LENGTH = 2_048

    fun isValid(backup: BackupMangaTaste): Boolean =
        backup.source != 0L &&
            backup.url.isNotBlank() &&
            backup.url.length <= MAX_URL_LENGTH &&
            backup.title.isNotBlank() &&
            backup.title.length <= MAX_TITLE_LENGTH
}
// KMK <--
