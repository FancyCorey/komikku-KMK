package eu.kanade.tachiyomi.data.backup.create.creators

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
import eu.kanade.tachiyomi.data.backup.restore.restorers.AlternateSourceBridgeBackupPolicy
import eu.kanade.tachiyomi.data.backup.restore.restorers.CrossSourceIdentityBackupPolicy
import exh.recs.SavedFocusModeStore
import exh.recs.SeenMangaKey
import tachiyomi.domain.taste.interactor.GetCrossSourceGroupPrimary
import tachiyomi.domain.taste.interactor.GetCrossSourceMangaLinks
import tachiyomi.domain.taste.interactor.GetDisabledRecommendationSources
import tachiyomi.domain.taste.interactor.GetMangaSourceQualitySignals
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.interactor.GetTagAliases
import tachiyomi.domain.taste.interactor.GetTagTaste
import tachiyomi.domain.taste.repository.AlternateSourceBridgeRepository
import tachiyomi.domain.taste.repository.TasteRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TasteBackupCreator(
    private val tasteRepository: TasteRepository = Injekt.get(),
    private val alternateSourceBridgeRepository: AlternateSourceBridgeRepository = Injekt.get(),
    private val getMangaTaste: GetMangaTaste = Injekt.get(),
    private val getTagTaste: GetTagTaste = Injekt.get(),
    private val getTagAliases: GetTagAliases = Injekt.get(),
    private val getDisabledSources: GetDisabledRecommendationSources = Injekt.get(),
    // KMK --> v0.7.0: Phase 4
    private val getCrossSourceMangaLinks: GetCrossSourceMangaLinks = Injekt.get(),
    // KMK <--
    // KMK --> v0.8.1-fix1: user-selected primary version per confirmed link group
    private val getCrossSourceGroupPrimary: GetCrossSourceGroupPrimary = Injekt.get(),
    // KMK <--
    // KMK --> v0.7.16: Best Version quality signals
    private val getMangaSourceQualitySignals: GetMangaSourceQualitySignals = Injekt.get(),
    // KMK <--
    // KMK --> v0.7.28: seen manga keys
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    // KMK <--
) {

    suspend fun backupMangaTastes(): List<BackupMangaTaste> =
        getMangaTaste.awaitAll().map { taste ->
            BackupMangaTaste(
                mangaId = taste.mangaId,
                source = taste.source,
                url = taste.url,
                title = taste.title,
                rating = taste.rating,
                updatedAt = taste.updatedAt,
            )
        }

    suspend fun backupTagTastes(): List<BackupTagTaste> =
        getTagTaste.awaitAll().map { tag ->
            BackupTagTaste(
                displayName = tag.displayName,
                preference = tag.preference,
                updatedAt = tag.updatedAt,
            )
        }

    suspend fun backupTagAliases(): List<BackupTagAlias> =
        getTagAliases.awaitAll().map { alias ->
            BackupTagAlias(
                alias = alias.alias,
                groupKey = alias.groupKey,
                displayName = alias.displayName,
            )
        }

    suspend fun backupDisabledRecommendationSources(): List<BackupDisabledRecommendationSource> =
        getDisabledSources.await().map { sourceId ->
            BackupDisabledRecommendationSource(sourceId = sourceId)
        }

    // KMK --> v0.7.0: Phase 4
    suspend fun backupCrossSourceMangaLinks(): List<BackupCrossSourceMangaLink> =
        getCrossSourceMangaLinks.awaitAll().map { link ->
            BackupCrossSourceMangaLink(
                source = link.source,
                url = link.url,
                groupId = link.groupId,
                title = link.title,
                updatedAt = link.updatedAt,
            )
        }
    // KMK <--

    // KMK --> v0.8.1-fix1: user-selected primary version per confirmed link group
    suspend fun backupCrossSourceGroupPrimaries(): List<BackupCrossSourceGroupPrimary> =
        getCrossSourceGroupPrimary.awaitAll().map { primary ->
            BackupCrossSourceGroupPrimary(
                groupId = primary.groupId,
                source = primary.source,
                url = primary.url,
                updatedAt = primary.updatedAt,
            )
        }
    // KMK <--

    suspend fun backupCrossSourceIdentityDecisions(): List<BackupCrossSourceIdentityDecision> =
        tasteRepository.getAllCrossSourceIdentityDecisions().map(CrossSourceIdentityBackupPolicy::encode)

    suspend fun backupAlternateSourceBridges(): List<BackupAlternateSourceBridge> =
        alternateSourceBridgeRepository.getAllBridges().map(AlternateSourceBridgeBackupPolicy::encode)

    suspend fun backupAlternateSourceBridgeMappings(): List<BackupAlternateSourceBridgeMapping> =
        alternateSourceBridgeRepository.getAllMappings().map(AlternateSourceBridgeBackupPolicy::encode)

    // KMK --> v0.7.28: seen manga keys
    // KMK v0.8.21-fix4: R1 correction -- this used to read the raw legacy
    // seenRecommendationMangaKeys preference directly, which is no longer live-written and can
    // drift stale relative to MangaTaste (the sole current rating-family authority). Downgrade/
    // compatibility decision, recorded rather than guessed: NEW backups still emit
    // BackupSeenMangaKey entries, because an older Komikku FC version restoring a backup taken on
    // a newer version only understands this legacy field (its MangaRating enum predates
    // NOT_INTERESTED as a fourth value) -- omitting it would silently lose "Not interested" state
    // on a downgrade-restore. The entries are now DERIVED from MangaTaste rows rated
    // NOT_INTERESTED, not read from the preference, so this field can never diverge from the
    // authoritative store and a fresh backup always reflects current state, not stale legacy bytes.
    suspend fun backupSeenMangaKeys(): List<BackupSeenMangaKey> =
        getMangaTaste.awaitAll()
            .asSequence()
            .filter { it.rating == tachiyomi.domain.taste.model.MangaRating.NOT_INTERESTED.value }
            .map { taste -> BackupSeenMangaKey(key = SeenMangaKey(taste.source, taste.url).serialize()) }
            .toList()
    // KMK <--

    // KMK v0.8.21-fix2: AUG-14 slice 3 -- saved For You focus modes
    fun backupSavedFocusModes(): List<BackupSavedFocusMode> =
        SavedFocusModeStore.parse(sourcePreferences.savedFocusModes().get()).map { mode ->
            BackupSavedFocusMode(
                id = mode.id,
                name = mode.name,
                groups = mode.includeGroups.toList(),
                createdAt = mode.createdAt,
                updatedAt = mode.updatedAt,
                order = mode.order,
                excludeGroups = mode.excludeGroups.toList(),
                matchAll = mode.matchAll,
            )
        }
    // KMK <--

    // KMK --> v0.7.16: Best Version quality signals
    suspend fun backupMangaSourceQualitySignals(): List<BackupMangaSourceQualitySignal> =
        getMangaSourceQualitySignals.getAll().map { signal ->
            BackupMangaSourceQualitySignal(
                originSourceId = signal.originSourceId,
                originUrl = signal.originUrl,
                originTitle = signal.originTitle,
                selectedSourceId = signal.selectedSourceId,
                selectedUrl = signal.selectedUrl,
                selectedTitle = signal.selectedTitle,
                selectedSourceName = signal.selectedSourceName,
                chapterNumber = signal.chapterNumber ?: 0.0,
                chapterName = signal.chapterName,
                selectedAt = signal.selectedAt,
                qualitySignalVersion = signal.qualitySignalVersion,
            )
        }
    // KMK <--
}
// KMK <--
