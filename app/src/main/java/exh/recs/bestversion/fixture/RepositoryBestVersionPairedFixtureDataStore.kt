package exh.recs.bestversion.fixture

import android.util.Log
import exh.util.ActionHistoryDiagnosticTrace
import exh.util.MigrationReceiptJournal
import exh.util.NonUndoableEventJournal
import exh.util.NonUndoableEventType
import kotlinx.coroutines.CancellationException
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.taste.model.CrossSourceGroupPrimary
import tachiyomi.domain.taste.model.CrossSourceMangaLink
import tachiyomi.domain.taste.model.MangaTaste
import tachiyomi.domain.taste.repository.MangaSourceQualitySignalRepository
import tachiyomi.domain.taste.repository.TasteRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date

/**
 * Repository-backed F2 fixture owner. Every write goes through the same domain repositories used by
 * application interactors; this class never opens the database or restores a whole-app backup.
 */
class RepositoryBestVersionPairedFixtureDataStore(
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val chapterRepository: ChapterRepository = Injekt.get(),
    private val historyRepository: HistoryRepository = Injekt.get(),
    private val categoryRepository: CategoryRepository = Injekt.get(),
    private val tasteRepository: TasteRepository = Injekt.get(),
    private val qualitySignalRepository: MangaSourceQualitySignalRepository = Injekt.get(),
    private val now: () -> Long = System::currentTimeMillis,
) : BestVersionPairedFixtureDataStore {

    override suspend fun captureSideEffectBaseline(): BestVersionPairedFixtureSideEffectBaseline =
        BestVersionPairedFixtureSideEffectBaseline(
            qualitySignalIds = qualitySignalRepository.getAll().mapTo(mutableSetOf()) { it.id },
            migrationEventIds = NonUndoableEventJournal.snapshot().mapTo(mutableSetOf()) { it.id },
            migrationReceiptIds = MigrationReceiptJournal.snapshot().mapTo(mutableSetOf()) { it.id },
        )

    override suspend fun inspect(
        spec: BestVersionPairedFixtureSpec,
        manifest: BestVersionPairedFixtureManifest?,
    ): BestVersionPairedFixtureObservedState {
        val mangas = listOfNotNull(
            mangaRepository.getMangaByUrlAndSourceId(spec.originUrl, spec.originSourceId),
            mangaRepository.getMangaByUrlAndSourceId(spec.targetUrl, spec.targetSourceId),
        )
        val chapters = mangas.flatMap { manga -> chapterRepository.getChapterByMangaId(manga.id) }
        val histories = mangas.flatMap { manga -> historyRepository.getHistoryByMangaId(manga.id) }
        val categories = if (manifest == null) {
            categoryRepository.getAll().filter { it.name.startsWith(CATEGORY_PREFIX) }
        } else {
            categoryRepository.getAll().filter { it.id == manifest.categoryId || it.name == manifest.categoryName }
        }
        val mangaCategories = mangas.flatMap { manga ->
            categoryRepository.getCategoriesByMangaId(manga.id).map { category -> "${manga.id}:${category.id}" }
        }
        val tastes = listOfNotNull(
            tasteRepository.getMangaTaste(spec.originSourceId, spec.originUrl),
            tasteRepository.getMangaTaste(spec.targetSourceId, spec.targetUrl),
        )
        val links = tasteRepository.getAllCrossSourceMangaLinks().filter { link ->
            if (manifest == null) {
                link.groupId.startsWith(GROUP_PREFIX) ||
                    (link.source == spec.originSourceId && link.url == spec.originUrl) ||
                    (link.source == spec.targetSourceId && link.url == spec.targetUrl)
            } else {
                link.groupId == manifest.groupId ||
                    (link.source == spec.originSourceId && link.url == spec.originUrl) ||
                    (link.source == spec.targetSourceId && link.url == spec.targetUrl)
            }
        }
        val primaries = tasteRepository.getAllCrossSourceGroupPrimaries().filter { primary ->
            if (manifest == null) primary.groupId.startsWith(GROUP_PREFIX) else primary.groupId == manifest.groupId
        }
        val qualitySignalIds = qualitySignalRepository.getAll().mapTo(mutableSetOf()) { it.id }
        val migrationEventIds = NonUndoableEventJournal.snapshot().mapTo(mutableSetOf()) { it.id }
        val migrationReceiptIds = MigrationReceiptJournal.snapshot().mapTo(mutableSetOf()) { it.id }

        return BestVersionPairedFixtureObservedState(
            mangaRows = mangas.map { manga ->
                listOf(
                    manga.id,
                    manga.source,
                    manga.url,
                    manga.favorite,
                    manga.dateAdded,
                    manga.lastUpdate,
                    manga.nextUpdate,
                    manga.fetchInterval,
                    manga.viewerFlags,
                    manga.chapterFlags,
                    manga.initialized,
                    manga.version,
                    manga.notes,
                ).joinToString(":")
            },
            chapterRows = chapters.map { chapter ->
                listOf(
                    chapter.id,
                    chapter.mangaId,
                    chapter.url,
                    chapter.chapterNumber,
                    chapter.read,
                    chapter.bookmark,
                    chapter.lastPageRead,
                    chapter.dateFetch,
                    chapter.sourceOrder,
                    chapter.name,
                    chapter.dateUpload,
                    chapter.scanlator,
                    chapter.lastModifiedAt,
                    chapter.version,
                ).joinToString(":")
            },
            historyRows = histories.map { history ->
                listOf(history.id, history.chapterId, history.readAt?.time, history.readDuration).joinToString(":")
            },
            categoryRows = categories.map { "${it.id}:${it.name}" } + mangaCategories,
            tasteRows = tastes.map { "${it.mangaId}:${it.source}:${it.url}:${it.rating}" },
            groupRows = links.map { "${it.groupId}:${it.source}:${it.url}" } +
                primaries.map { "${it.groupId}:${it.source}:${it.url}:primary" },
            qualitySignalIds = qualitySignalIds,
            migrationEventIds = migrationEventIds,
            migrationReceiptIds = migrationReceiptIds,
        )
    }

    override suspend fun applyStep(
        spec: BestVersionPairedFixtureSpec,
        manifest: BestVersionPairedFixtureManifest,
        step: BestVersionPairedFixtureStep,
    ): BestVersionPairedFixtureManifest = when (step) {
        BestVersionPairedFixtureStep.ORIGIN_MANGA -> {
            val manga = ensureManga(
                sourceId = spec.originSourceId,
                url = spec.originUrl,
                title = "Fixture Pair A",
                marker = manifest.ownershipMarker,
            )
            manifest.copy(originMangaId = manga.id)
        }
        BestVersionPairedFixtureStep.TARGET_MANGA -> {
            val manga = ensureManga(
                sourceId = spec.targetSourceId,
                url = spec.targetUrl,
                title = "Fixture Pair B",
                marker = manifest.ownershipMarker,
            )
            manifest.copy(targetMangaId = manga.id)
        }
        BestVersionPairedFixtureStep.CHAPTERS_AND_HISTORY -> seedChaptersAndHistory(manifest)
        BestVersionPairedFixtureStep.CATEGORY -> seedCategory(manifest)
        BestVersionPairedFixtureStep.TASTES -> seedTastes(spec, manifest)
        BestVersionPairedFixtureStep.GROUP_LINKS -> seedGroupLinks(spec, manifest)
    }

    override suspend fun cleanup(
        spec: BestVersionPairedFixtureSpec,
        manifest: BestVersionPairedFixtureManifest,
    ): Boolean {
        val mangas = listOfNotNull(
            mangaRepository.getMangaByUrlAndSourceId(spec.originUrl, spec.originSourceId),
            mangaRepository.getMangaByUrlAndSourceId(spec.targetUrl, spec.targetSourceId),
        )
        // Rows from the pre-marker fixture writer may have blank notes. They are still recoverable
        // because the query is restricted to the reserved synthetic fixture URLs; reject any
        // nonblank foreign marker so real or colliding data remains fail-closed.
        if (mangas.any { it.notes.isNotBlank() && it.notes != manifest.ownershipMarker }) return false

        val links = tasteRepository.getAllCrossSourceMangaLinks().filter {
            it.groupId == manifest.groupId ||
                (it.source == spec.originSourceId && it.url == spec.originUrl) ||
                (it.source == spec.targetSourceId && it.url == spec.targetUrl)
        }
        if (links.any { it.groupId != manifest.groupId }) return false
        val category = categoryRepository.getAll().firstOrNull {
            it.id == manifest.categoryId || it.name == manifest.categoryName
        }
        if (category != null && category.name != manifest.categoryName) return false

        val baseline = manifest.sideEffectBaseline
        val currentEvents = NonUndoableEventJournal.snapshot()
        val currentReceipts = MigrationReceiptJournal.snapshot()
        val fixtureReceipts = currentReceipts.filter { receipt ->
            receipt.id !in baseline.migrationReceiptIds &&
                receipt.originMangaId == manifest.originMangaId &&
                receipt.originSourceId == spec.originSourceId &&
                receipt.targetMangaId == manifest.targetMangaId &&
                receipt.targetSourceId == spec.targetSourceId
        }
        val fixtureReceiptIds = fixtureReceipts.mapTo(mutableSetOf()) { it.id }
        val ambiguousMigrationEvents = currentEvents.filter { event ->
            event.id !in baseline.migrationEventIds &&
                event.eventType == NonUndoableEventType.MIGRATION_COMPLETED &&
                event.id !in fixtureReceiptIds
        }
        if (ambiguousMigrationEvents.isNotEmpty()) return false

        val fixtureQualitySignals = qualitySignalRepository.getByOrigin(spec.originSourceId, spec.originUrl)
            .filter { signal ->
                signal.id !in baseline.qualitySignalIds &&
                    signal.selectedSourceId == spec.targetSourceId &&
                    signal.selectedUrl == spec.targetUrl
            }

        return try {
            fixtureQualitySignals.forEach { qualitySignalRepository.deleteById(it.id) }
            fixtureReceiptIds.forEach { id ->
                MigrationReceiptJournal.removeById(id)
                NonUndoableEventJournal.removeById(id)
                ActionHistoryDiagnosticTrace.removeByRowKey(id)
            }
            tasteRepository.deleteCrossSourceGroupCompletely(manifest.groupId)
            tasteRepository.deleteMangaTaste(spec.originSourceId, spec.originUrl)
            tasteRepository.deleteMangaTaste(spec.targetSourceId, spec.targetUrl)
            val mangaIds = mangas.map { it.id }
            historyRepository.resetHistoryByMangaIds(mangaIds)
            val chapterIds = mangas.flatMap { chapterRepository.getChapterByMangaId(it.id) }.map { it.id }
            if (chapterIds.isNotEmpty()) chapterRepository.removeChaptersWithIds(chapterIds)
            mangas.forEach { manga ->
                mangaRepository.setMangaCategories(manga.id, emptyList())
                mangaRepository.deleteManga(manga.id)
            }
            if (category != null) categoryRepository.delete(category.id)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            diagnostic("fixture cleanup failed: ${e::class.simpleName}: ${e.message}")
            false
        }
    }

    private suspend fun ensureManga(sourceId: Long, url: String, title: String, marker: String): Manga {
        mangaRepository.getMangaByUrlAndSourceId(url, sourceId)?.let { existing ->
            check(existing.notes == marker) { "fixture-manga-collision" }
            return existing
        }
        val inserted = mangaRepository.insertNetworkManga(
            listOf(
                Manga.create().copy(
                    source = sourceId,
                    url = url,
                    ogTitle = title,
                    ogDescription = "Synthetic offline Best Version fixture",
                    ogGenre = listOf("Fixture"),
                    initialized = true,
                    notes = marker,
                ),
            ),
        ).single()
        val updated = mangaRepository.update(
            MangaUpdate(
                id = inserted.id,
                favorite = true,
                dateAdded = now(),
                notes = marker,
            ),
        )
        diagnostic("fixture ownership update id=${inserted.id} result=$updated expected=$marker")
        check(updated) { "fixture-manga-update-failed" }
        val persisted = mangaRepository.getMangaByUrlAndSourceId(url, sourceId)
            ?: error("fixture-manga-insert-missing")
        diagnostic("fixture ownership persisted id=${persisted.id} notes=${persisted.notes}")
        check(persisted.notes == marker) { "fixture-manga-ownership-not-persisted" }
        return persisted
    }

    private fun diagnostic(message: String) {
        runCatching { Log.w(TAG, message) }
    }

    private suspend fun seedChaptersAndHistory(
        manifest: BestVersionPairedFixtureManifest,
    ): BestVersionPairedFixtureManifest {
        val originId = requireNotNull(manifest.originMangaId)
        val targetId = requireNotNull(manifest.targetMangaId)
        val origin = ensureChapters(originId, "origin", listOf(1.0, 2.0, 3.0), readThrough = 2.0)
        val target = ensureChapters(targetId, "target", listOf(1.0, 2.0, 3.0, 4.0), readThrough = null)
        val historyChapter = origin.first { it.chapterNumber == 2.0 }
        historyRepository.upsertHistory(HistoryUpdate(historyChapter.id, Date(FIXED_HISTORY_TIME), 45_000L))
        return manifest.copy(
            originChapterIds = origin.map { it.id },
            targetChapterIds = target.map { it.id },
        )
    }

    private suspend fun ensureChapters(
        mangaId: Long,
        slot: String,
        numbers: List<Double>,
        readThrough: Double?,
    ): List<Chapter> {
        val existing = chapterRepository.getChapterByMangaId(mangaId)
        if (existing.isNotEmpty()) {
            check(existing.all { it.url.startsWith("/kmk-fixture/f2/$slot/") }) { "fixture-chapter-collision" }
            return existing
        }
        return chapterRepository.addAll(
            numbers.mapIndexed { index, number ->
                Chapter.create().copy(
                    mangaId = mangaId,
                    read = readThrough != null && number <= readThrough,
                    bookmark = number == 2.0 && readThrough != null,
                    lastPageRead = if (number == 2.0 && readThrough != null) 5 else 0,
                    dateFetch = FIXED_FETCH_TIME + index,
                    // The reader's established source-order comparator reverses this value for
                    // forward progression. Keep the synthetic fixture in Chapter 1 -> Chapter N
                    // order so the bridge route can exercise the intended missing-middle gap.
                    sourceOrder = (numbers.size - index).toLong(),
                    url = "/kmk-fixture/f2/$slot/chapter-${number.toInt()}",
                    name = "Chapter ${number.toInt()}",
                    dateUpload = FIXED_UPLOAD_TIME + index,
                    chapterNumber = number,
                )
            },
        )
    }

    private suspend fun seedCategory(
        manifest: BestVersionPairedFixtureManifest,
    ): BestVersionPairedFixtureManifest {
        val existing = categoryRepository.getAll().firstOrNull { it.name == manifest.categoryName }
        val categoryId = existing?.id ?: categoryRepository.insert(
            Category(
                id = -1L,
                name = manifest.categoryName,
                order = categoryRepository.getAll().maxOfOrNull { it.order }?.plus(1L) ?: 0L,
                flags = 0L,
                hidden = false,
            ),
        )
        mangaRepository.setMangaCategories(requireNotNull(manifest.originMangaId), listOf(categoryId))
        return manifest.copy(categoryId = categoryId)
    }

    private suspend fun seedTastes(
        spec: BestVersionPairedFixtureSpec,
        manifest: BestVersionPairedFixtureManifest,
    ): BestVersionPairedFixtureManifest {
        val timestamp = now()
        tasteRepository.upsertMangaTaste(
            MangaTaste(
                mangaId = requireNotNull(manifest.originMangaId),
                source = spec.originSourceId,
                url = spec.originUrl,
                title = "Fixture Pair A",
                rating = 2,
                createdAt = timestamp,
                updatedAt = timestamp,
            ),
        )
        tasteRepository.upsertMangaTaste(
            MangaTaste(
                mangaId = requireNotNull(manifest.targetMangaId),
                source = spec.targetSourceId,
                url = spec.targetUrl,
                title = "Fixture Pair B",
                rating = 1,
                createdAt = timestamp,
                updatedAt = timestamp,
            ),
        )
        return manifest
    }

    private suspend fun seedGroupLinks(
        spec: BestVersionPairedFixtureSpec,
        manifest: BestVersionPairedFixtureManifest,
    ): BestVersionPairedFixtureManifest {
        val timestamp = now()
        tasteRepository.upsertCrossSourceMangaLinks(
            listOf(
                CrossSourceMangaLink(spec.originSourceId, spec.originUrl, manifest.groupId, "Fixture Pair A", timestamp, timestamp),
                CrossSourceMangaLink(spec.targetSourceId, spec.targetUrl, manifest.groupId, "Fixture Pair B", timestamp, timestamp),
            ),
        )
        tasteRepository.upsertCrossSourceGroupPrimary(
            CrossSourceGroupPrimary(manifest.groupId, spec.originSourceId, spec.originUrl, timestamp),
        )
        return manifest
    }

    private companion object {
        const val TAG = "KMKFixture"
        const val CATEGORY_PREFIX = "KMK F2 "
        const val GROUP_PREFIX = "kmk-f2-group-"
        const val FIXED_FETCH_TIME = 1_700_000_000_000L
        const val FIXED_UPLOAD_TIME = 1_690_000_000_000L
        const val FIXED_HISTORY_TIME = 1_700_000_100_000L
    }
}
