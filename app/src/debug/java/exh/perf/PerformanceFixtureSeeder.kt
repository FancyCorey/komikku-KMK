package exh.perf

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
import tachiyomi.domain.taste.model.TagAlias
import tachiyomi.domain.taste.model.TagTaste
import tachiyomi.domain.taste.repository.TasteRepository
import tachiyomi.domain.tracker.model.LocalTrackedWork
import tachiyomi.domain.tracker.model.LocalTrackedWorkStatus
import tachiyomi.domain.tracker.repository.LocalTrackerRepository
import java.util.Date

// KMK HR-2026-08-26-CONSTRAINED-DEVICE-PERFORMANCE-PROGRAM (C4) -->
/**
 * Writes a [PerformanceFixtureDataset] into a real database through real repositories -- the exact
 * same repository/interactor entry points production code uses (`insertNetworkManga`,
 * `ChapterRepository.addAll`, `TasteRepository.upsertMangaTaste`, etc.), never raw SQL. Works
 * identically against the real production repositories (on-device) or an in-memory
 * `JdbcSqliteDriver` (host tests) -- callers supply the repository instances, this object has no
 * database/Injekt dependency of its own.
 *
 * Manga are always inserted with `favorite = false` and flipped to `favorite = true` afterward via
 * [MangaRepository.updateAll]/[MangaUpdate] -- never by constructing a [Manga] object with
 * `favorite = true` directly, which would trigger [Manga]'s private lazy `customMangaInfo` Injekt
 * lookup (see this program's own established lesson: any `Manga.copy(favorite = true)` outside a
 * fully Injekt-wired runtime throws `InjektionException`). [MangaUpdate] never constructs a
 * [Manga], so this seeder needs no Injekt registration at all to run on a bare host test.
 */
internal object PerformanceFixtureSeeder {

    data class SeedResult(
        val mangaIds: List<Long>,
        val categoryIds: List<Long>,
        val chapterCount: Int,
        val historyCount: Int,
        val ratedCount: Int,
        val crossSourceLinkCount: Int,
        val localTrackingCount: Int,
    )

    suspend fun seed(
        dataset: PerformanceFixtureDataset,
        mangaRepository: MangaRepository,
        chapterRepository: ChapterRepository,
        categoryRepository: CategoryRepository,
        historyRepository: HistoryRepository,
        tasteRepository: TasteRepository,
        localTrackerRepository: LocalTrackerRepository,
        progress: ((String) -> Unit)? = null,
    ): SeedResult {
        val now = System.currentTimeMillis()

        val categoryIds = dataset.categoryNames.map { name ->
            categoryRepository.insert(Category(id = 0L, name = name, order = 0L, flags = 0L, hidden = false))
        }
        progress?.invoke("categories complete: ${categoryIds.size}")

        val mangaIds = insertManga(dataset, mangaRepository, now)
        progress?.invoke("manga complete: ${mangaIds.size}")
        applyFavorites(dataset, mangaIds, mangaRepository)
        progress?.invoke("favorites complete")
        applyCategories(dataset, mangaIds, categoryIds, mangaRepository)
        progress?.invoke("manga categories complete")

        val (chapterCount, historyCount) = insertChaptersAndHistory(dataset, mangaIds, chapterRepository, historyRepository)
        progress?.invoke("chapters/history complete: chapters=$chapterCount history=$historyCount")
        val ratedCount = insertRatings(dataset, mangaIds, tasteRepository, now)
        progress?.invoke("ratings complete: $ratedCount")
        val crossSourceLinkCount = insertCrossSourceLinks(dataset, tasteRepository, now)
        progress?.invoke("cross-source links complete: $crossSourceLinkCount")
        val localTrackingCount = insertLocalTracking(dataset, mangaIds, localTrackerRepository, now)
        progress?.invoke("local tracking complete: $localTrackingCount")
        insertTagTasteAndAliases(dataset, tasteRepository, now)
        progress?.invoke("tags and aliases complete")

        return SeedResult(mangaIds, categoryIds, chapterCount, historyCount, ratedCount, crossSourceLinkCount, localTrackingCount)
    }

    private suspend fun insertManga(dataset: PerformanceFixtureDataset, mangaRepository: MangaRepository, now: Long): List<Long> {
        val toInsert = dataset.manga.map { m ->
            Manga.create().copy(
                source = m.source,
                url = m.url,
                ogTitle = m.title,
                ogAuthor = m.author,
                ogArtist = m.artist,
                ogDescription = m.description,
                ogGenre = m.genres,
                ogThumbnailUrl = m.thumbnailUrl,
                favorite = false,
                initialized = true,
                dateAdded = now,
            )
        }
        return mangaRepository.insertNetworkManga(toInsert, updateInfo = false).map { it.id }
    }

    private suspend fun applyFavorites(dataset: PerformanceFixtureDataset, mangaIds: List<Long>, mangaRepository: MangaRepository) {
        val updates = dataset.manga.mapIndexedNotNull { i, m -> if (m.favorite) MangaUpdate(id = mangaIds[i], favorite = true) else null }
        if (updates.isNotEmpty()) mangaRepository.updateAll(updates)
    }

    private suspend fun applyCategories(
        dataset: PerformanceFixtureDataset,
        mangaIds: List<Long>,
        categoryIds: List<Long>,
        mangaRepository: MangaRepository,
    ) {
        dataset.manga.forEachIndexed { i, m ->
            if (m.categoryIndices.isNotEmpty()) {
                mangaRepository.setMangaCategories(mangaIds[i], m.categoryIndices.map { categoryIds[it] })
            }
        }
    }

    private suspend fun insertChaptersAndHistory(
        dataset: PerformanceFixtureDataset,
        mangaIds: List<Long>,
        chapterRepository: ChapterRepository,
        historyRepository: HistoryRepository,
    ): Pair<Int, Int> {
        var chapterCount = 0
        var historyCount = 0
        dataset.manga.forEachIndexed { i, m ->
            if (m.chapters.isEmpty()) return@forEachIndexed
            val mangaId = mangaIds[i]
            val toInsert = m.chapters.map { ch ->
                Chapter.create().copy(
                    mangaId = mangaId,
                    url = ch.url,
                    name = ch.name,
                    chapterNumber = ch.chapterNumber,
                    sourceOrder = ch.sourceOrder,
                    read = ch.read,
                    bookmark = ch.bookmark,
                    lastPageRead = ch.lastPageRead,
                    dateUpload = System.currentTimeMillis(),
                    dateFetch = System.currentTimeMillis(),
                )
            }
            val inserted = chapterRepository.addAll(toInsert)
            chapterCount += inserted.size

            val historyUpdates = m.historyChapterIndices.mapNotNull { idx ->
                inserted.getOrNull(idx)?.let { HistoryUpdate(chapterId = it.id, readAt = Date(), sessionReadDuration = 600_000L) }
            }
            if (historyUpdates.isNotEmpty()) {
                historyRepository.upsertHistory(historyUpdates)
                historyCount += historyUpdates.size
            }
        }
        return chapterCount to historyCount
    }

    private suspend fun insertRatings(
        dataset: PerformanceFixtureDataset,
        mangaIds: List<Long>,
        tasteRepository: TasteRepository,
        now: Long,
    ): Int {
        var ratedCount = 0
        dataset.manga.forEachIndexed { i, m ->
            val rating = m.rating ?: return@forEachIndexed
            tasteRepository.upsertMangaTaste(
                MangaTaste(mangaId = mangaIds[i], source = m.source, url = m.url, title = m.title, rating = rating.value, createdAt = now, updatedAt = now),
            )
            ratedCount++
        }
        return ratedCount
    }

    private suspend fun insertCrossSourceLinks(dataset: PerformanceFixtureDataset, tasteRepository: TasteRepository, now: Long): Int {
        val links = dataset.manga.mapNotNull { m ->
            m.crossSourceGroupId?.let { groupId ->
                CrossSourceMangaLink(source = m.source, url = m.url, groupId = groupId, title = m.title, createdAt = now, updatedAt = now)
            }
        }
        if (links.isNotEmpty()) tasteRepository.upsertCrossSourceMangaLinks(links)

        dataset.manga.filter { it.crossSourceGroupId != null && it.crossSourceGroupPrimary }.forEach { m ->
            tasteRepository.upsertCrossSourceGroupPrimary(
                CrossSourceGroupPrimary(groupId = m.crossSourceGroupId!!, source = m.source, url = m.url, updatedAt = now),
            )
        }
        return links.size
    }

    private suspend fun insertLocalTracking(
        dataset: PerformanceFixtureDataset,
        mangaIds: List<Long>,
        localTrackerRepository: LocalTrackerRepository,
        now: Long,
    ): Int {
        var count = 0
        dataset.manga.forEachIndexed { i, m ->
            if (!m.hasLocalTracking) return@forEachIndexed
            val mangaId = mangaIds[i]
            localTrackerRepository.upsertWork(
                LocalTrackedWork(
                    id = "perf-fixture-work-$mangaId",
                    title = m.title,
                    normalizedTitle = m.title.lowercase(),
                    status = LocalTrackedWorkStatus.entries[i % LocalTrackedWorkStatus.entries.size],
                    lastChapterSource = null,
                    lastChapterNumber = null,
                    lastChapterUrl = null,
                    lastChapterLabel = null,
                    lastProgressAt = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            count++
        }
        return count
    }

    private suspend fun insertTagTasteAndAliases(dataset: PerformanceFixtureDataset, tasteRepository: TasteRepository, now: Long) {
        dataset.tagTastes.forEach { t ->
            tasteRepository.upsertTagTaste(TagTaste(normalizedTag = t.normalizedTag, displayName = t.displayName, preference = t.preference, createdAt = now, updatedAt = now))
        }
        dataset.tagAliases.forEach { a ->
            tasteRepository.upsertTagAlias(TagAlias(alias = a.alias, normalizedAlias = a.normalizedAlias, groupKey = a.groupKey, displayName = a.displayName))
        }
    }
}
// KMK <--
