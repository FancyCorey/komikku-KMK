package eu.kanade.tachiyomi.data.backup.create.creators

// KMK -->
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BackupCrossSourceGroupPrimary
import eu.kanade.tachiyomi.data.backup.models.BackupCrossSourceMangaLink
import eu.kanade.tachiyomi.data.backup.models.BackupDisabledRecommendationSource
import eu.kanade.tachiyomi.data.backup.models.BackupMangaSourceQualitySignal
import eu.kanade.tachiyomi.data.backup.models.BackupMangaTaste
import eu.kanade.tachiyomi.data.backup.models.BackupSeenMangaKey
import eu.kanade.tachiyomi.data.backup.models.BackupTagAlias
import eu.kanade.tachiyomi.data.backup.models.BackupTagTaste
import exh.recs.SeenRecommendationMangaStore
import tachiyomi.domain.taste.interactor.GetCrossSourceGroupPrimary
import tachiyomi.domain.taste.interactor.GetCrossSourceMangaLinks
import tachiyomi.domain.taste.interactor.GetDisabledRecommendationSources
import tachiyomi.domain.taste.interactor.GetMangaSourceQualitySignals
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.interactor.GetTagAliases
import tachiyomi.domain.taste.interactor.GetTagTaste
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TasteBackupCreator(
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

    // KMK --> v0.7.28: seen manga keys — raw semicolon-separated "sourceId|url" pairs from preference
    fun backupSeenMangaKeys(): List<BackupSeenMangaKey> {
        val raw = sourcePreferences.seenRecommendationMangaKeys().get()
        if (raw.isBlank()) return emptyList()
        return SeenRecommendationMangaStore.parse(raw)
            .map { key -> BackupSeenMangaKey(key = key.serialize()) }
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
