package exh.recs.share

import eu.kanade.domain.manga.interactor.UpdateManga
import kotlinx.coroutines.flow.firstOrNull
import logcat.LogPriority
import mihon.domain.source.interactor.UpdateMangaFromRemote
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

class RecommendationBundleLibraryAdder(
    private val sourceManager: SourceManager = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val updateMangaFromRemote: UpdateMangaFromRemote = Injekt.get(),
    private val setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags = Injekt.get(),
) {

    sealed interface Outcome {
        data object Added : Outcome
        data object AlreadyFavorite : Outcome
        data class Duplicate(val duplicates: List<Manga>) : Outcome
        data class Error(val message: String) : Outcome
    }

    data class AddResult(val manga: Manga, val outcome: Outcome)

    suspend fun addToLibrary(
        manga: Manga,
        skipDuplicates: Boolean = false,
        categoryIds: List<Long> = emptyList(),
    ): AddResult {
        if (manga.favorite) return AddResult(manga, Outcome.AlreadyFavorite)

        if (!skipDuplicates) {
            val duplicates = runCatching { getDuplicateLibraryManga(manga) }.getOrDefault(emptyList())
            if (duplicates.isNotEmpty()) {
                return AddResult(manga, Outcome.Duplicate(duplicates.map { it.manga }))
            }
        }

        return try {
            setMangaDefaultChapterFlags.await(manga)
            updateManga.awaitUpdateFavorite(manga.id, true)

            val resolvedCategories = categoryIds.ifEmpty { resolveDefaultCategoryIds() }
            setMangaCategories.await(manga.id, resolvedCategories)

            if (libraryPreferences.fetchMetadataOnAdd().get()) {
                runCatching {
                    val source = sourceManager.getOrStub(manga.source)
                    updateMangaFromRemote(
                        source = source,
                        manga = manga,
                        fetchDetails = true,
                        fetchChapters = false,
                    ).getOrThrow()
                }.onFailure { e ->
                    logcat(LogPriority.WARN, e) { "Metadata fetch failed for imported manga: ${manga.title}" }
                }
            }

            AddResult(manga, Outcome.Added)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to add ${manga.title} to library" }
            AddResult(manga, Outcome.Error(e.message ?: "Unknown error"))
        }
    }

    private suspend fun resolveDefaultCategoryIds(): List<Long> {
        val defaultCategoryId = libraryPreferences.defaultCategory().get()
        if (defaultCategoryId == 0) return emptyList()
        val allCategories = getCategories.subscribe().firstOrNull().orEmpty()
        val found = allCategories.firstOrNull { it.id == defaultCategoryId.toLong() }
        return if (found != null) listOf(found.id) else emptyList()
    }

    suspend fun addMultiple(
        mangas: List<Manga>,
        skipDuplicates: Boolean = false,
        categoryIds: List<Long> = emptyList(),
    ): List<AddResult> = mangas.map { manga ->
        addToLibrary(manga, skipDuplicates = skipDuplicates, categoryIds = categoryIds)
    }
}

// KMK <--
