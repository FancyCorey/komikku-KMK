package exh.taste

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.manga.repository.MangaRepository

/** Test stub for [MangaRepository]. Override only the methods needed by each test. */
abstract class StubMangaRepository : MangaRepository {
    override suspend fun getMangaById(id: Long): Manga = throw UnsupportedOperationException()
    override suspend fun getMangaByIdAsFlow(id: Long): Flow<Manga> = emptyFlow()
    override suspend fun getMangaByUrlAndSourceId(url: String, sourceId: Long): Manga? = null
    override fun getMangaByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Manga?> = emptyFlow()
    override suspend fun getFavorites(): List<Manga> = emptyList()
    override suspend fun getReadMangaNotInLibrary(): List<Manga> = emptyList()
    override suspend fun getLibraryManga(): List<LibraryManga> = emptyList()
    override fun getLibraryMangaAsFlow(): Flow<List<LibraryManga>> = emptyFlow()
    override fun getFavoritesBySourceId(sourceId: Long): Flow<List<Manga>> = emptyFlow()
    override suspend fun getDuplicateLibraryManga(id: Long, title: String): List<MangaWithChapterCount> = emptyList()
    override suspend fun getUpcomingManga(statuses: Set<Long>): Flow<List<Manga>> = emptyFlow()
    override suspend fun resetViewerFlags(): Boolean = false
    override suspend fun setMangaCategories(mangaId: Long, categoryIds: List<Long>) {}
    override suspend fun update(update: MangaUpdate): Boolean = false
    override suspend fun updateAll(mangaUpdates: List<MangaUpdate>): Boolean = false
    override suspend fun insertNetworkManga(manga: List<Manga>, updateInfo: Boolean): List<Manga> = emptyList()
    override suspend fun getMangaBySourceId(sourceId: Long): List<Manga> = emptyList()
    override suspend fun getAll(): List<Manga> = emptyList()
    override suspend fun deleteManga(mangaId: Long) {}
    override suspend fun getReadMangaNotInLibraryView(): List<LibraryManga> = emptyList()
    // KMK -->
    override suspend fun getKnownRecommendationMangaIds(mangaIds: Collection<Long>): Set<Long> = emptySet()
    // KMK <--
}
