package tachiyomi.data.manga

import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.json.JsonObject
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount

object MangaMapper {
    fun mapManga(
        id: Long,
        source: Long,
        url: String,
        artist: String?,
        author: String?,
        description: String?,
        genre: List<String>?,
        title: String,
        status: Long,
        thumbnailUrl: String?,
        favorite: Boolean,
        lastUpdate: Long?,
        nextUpdate: Long?,
        initialized: Boolean,
        viewerFlags: Long,
        chapterFlags: Long,
        coverLastModified: Long,
        dateAdded: Long,
        // SY -->
        @Suppress("UNUSED_PARAMETER")
        filteredScanlators: String?,
        // SY <--
        updateStrategy: UpdateStrategy,
        calculateInterval: Long,
        lastModifiedAt: Long,
        favoriteModifiedAt: Long?,
        version: Long,
        @Suppress("UNUSED_PARAMETER")
        isSyncing: Long,
        notes: String,
        // KMK --> 1.14.0 reconciliation: mangas.memo column
        memo: JsonObject,
        // KMK <--
    ): Manga = Manga(
        id = id,
        source = source,
        favorite = favorite,
        lastUpdate = lastUpdate ?: 0,
        nextUpdate = nextUpdate ?: 0,
        fetchInterval = calculateInterval.toInt(),
        dateAdded = dateAdded,
        viewerFlags = viewerFlags,
        chapterFlags = chapterFlags,
        coverLastModified = coverLastModified,
        url = url,
        // SY -->
        ogTitle = title,
        ogArtist = artist,
        ogAuthor = author,
        ogThumbnailUrl = thumbnailUrl,
        ogDescription = description,
        ogGenre = genre,
        ogStatus = status,
        // SY <--
        updateStrategy = updateStrategy,
        initialized = initialized,
        lastModifiedAt = lastModifiedAt,
        favoriteModifiedAt = favoriteModifiedAt,
        version = version,
        notes = notes,
        // KMK -->
        memo = memo,
        // KMK <--
    )

    fun mapLibraryManga(
        id: Long,
        source: Long,
        url: String,
        artist: String?,
        author: String?,
        description: String?,
        genre: List<String>?,
        title: String,
        status: Long,
        thumbnailUrl: String?,
        favorite: Boolean,
        lastUpdate: Long?,
        nextUpdate: Long?,
        initialized: Boolean,
        viewerFlags: Long,
        chapterFlags: Long,
        coverLastModified: Long,
        dateAdded: Long,
        // SY -->
        filteredScanlators: String?,
        // SY <--
        updateStrategy: UpdateStrategy,
        calculateInterval: Long,
        lastModifiedAt: Long,
        favoriteModifiedAt: Long?,
        version: Long,
        isSyncing: Long,
        notes: String,
        // KMK --> 1.14.0 reconciliation: mangas.memo column
        memo: JsonObject,
        // KMK <--
        totalCount: Long,
        readCount: Double,
        latestUpload: Long,
        chapterFetchedAt: Long,
        lastRead: Long,
        bookmarkCount: Double,
        // KMK -->
        bookmarkedReadCount: Long,
        // KMK <--
        categories: String,
    ): LibraryManga = LibraryManga(
        manga = mapManga(
            id,
            source,
            url,
            artist,
            author,
            description,
            genre,
            title,
            status,
            thumbnailUrl,
            favorite,
            lastUpdate,
            nextUpdate,
            initialized,
            viewerFlags,
            chapterFlags,
            coverLastModified,
            dateAdded,
            // SY -->
            filteredScanlators,
            // SY <--
            updateStrategy,
            calculateInterval,
            lastModifiedAt,
            favoriteModifiedAt,
            version,
            isSyncing,
            notes,
            // KMK -->
            memo,
            // KMK <--
        ),
        categories = categories.split(",").map { it.toLong() },
        totalChapters = totalCount,
        readCount = readCount.toLong(),
        bookmarkCount = bookmarkCount.toLong(),
        // KMK -->
        bookmarkReadCount = bookmarkedReadCount,
        chapterFlags = chapterFlags,
        // KMK <--
        latestUpload = latestUpload,
        chapterFetchedAt = chapterFetchedAt,
        lastRead = lastRead,
    )

    fun mapMangaWithChapterCount(
        id: Long,
        source: Long,
        url: String,
        artist: String?,
        author: String?,
        description: String?,
        genre: List<String>?,
        title: String,
        status: Long,
        thumbnailUrl: String?,
        favorite: Boolean,
        lastUpdate: Long?,
        nextUpdate: Long?,
        initialized: Boolean,
        viewerFlags: Long,
        chapterFlags: Long,
        coverLastModified: Long,
        dateAdded: Long,
        // SY -->
        filteredScanlators: String?,
        // SY <--
        updateStrategy: UpdateStrategy,
        calculateInterval: Long,
        lastModifiedAt: Long,
        favoriteModifiedAt: Long?,
        version: Long,
        isSyncing: Long,
        notes: String,
        // KMK --> 1.14.0 reconciliation: mangas.memo column
        memo: JsonObject,
        // KMK <--
        totalCount: Long,
    ): MangaWithChapterCount = MangaWithChapterCount(
        manga = mapManga(
            id,
            source,
            url,
            artist,
            author,
            description,
            genre,
            title,
            status,
            thumbnailUrl,
            favorite,
            lastUpdate,
            nextUpdate,
            initialized,
            viewerFlags,
            chapterFlags,
            coverLastModified,
            dateAdded,
            // SY -->
            filteredScanlators,
            // SY <--
            updateStrategy,
            calculateInterval,
            lastModifiedAt,
            favoriteModifiedAt,
            version,
            isSyncing,
            notes,
            // KMK -->
            memo,
            // KMK <--
        ),
        chapterCount = totalCount,
    )
}
