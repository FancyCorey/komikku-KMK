package tachiyomi.domain.manga.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.MangaWithChapterCount

interface MangaRepository {

    suspend fun getMangaById(id: Long): Manga

    suspend fun getMangaByIdAsFlow(id: Long): Flow<Manga>

    suspend fun getMangaByUrlAndSourceId(url: String, sourceId: Long): Manga?

    fun getMangaByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Manga?>

    suspend fun getFavorites(): List<Manga>

    suspend fun getReadMangaNotInLibrary(): List<Manga>

    suspend fun getLibraryManga(): List<LibraryManga>

    fun getLibraryMangaAsFlow(): Flow<List<LibraryManga>>

    fun getFavoritesBySourceId(sourceId: Long): Flow<List<Manga>>

    suspend fun getDuplicateLibraryManga(id: Long, title: String): List<MangaWithChapterCount>

    suspend fun getUpcomingManga(statuses: Set<Long>): Flow<List<Manga>>

    suspend fun resetViewerFlags(): Boolean

    suspend fun setMangaCategories(mangaId: Long, categoryIds: List<Long>)

    suspend fun update(update: MangaUpdate): Boolean

    suspend fun updateAll(mangaUpdates: List<MangaUpdate>): Boolean

    suspend fun insertNetworkManga(manga: List<Manga>, updateInfo: Boolean = true): List<Manga>

    // SY -->
    suspend fun getMangaBySourceId(sourceId: Long): List<Manga>

    // KMK -->
    /** Returns the subset of [mangaIds] that are "known" — rated, in library, started, or read. */
    suspend fun getKnownRecommendationMangaIds(mangaIds: Collection<Long>): Set<Long>

    // KMK --> v0.7.26: batch chapter counts for the minimum-chapter filter (local DB only, never fetches)
    /** Returns a map of mangaId → locally-stored chapter count for each ID in [mangaIds]. */
    suspend fun getChapterCountsByMangaIds(mangaIds: Collection<Long>): Map<Long, Long>
    // KMK <--
    // KMK <--

    suspend fun getAll(): List<Manga>

    suspend fun deleteManga(mangaId: Long)

    suspend fun getReadMangaNotInLibraryView(): List<LibraryManga>
    // SY <--
}
