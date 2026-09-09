package exh.recs.share

import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.source.service.SourcePreferences
import exh.recs.RecommendationErrorClassifier
import kotlinx.coroutines.CancellationException
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
    // KMK: bundle import's library-add half is
    // exactly the "library additions" case the behavior contract's classification rules require typed local undo
    // for -- build-before-write/commit-after-success, same contract every other favorite-flip call
    // site already uses (LibraryUndoRecorder). This is a distinct receipt from the extension-install
    // half (RecommendationBundleImportScreenModel.installMissingExtension, already
    // NonUndoableEventJournal-backed) -- the plan explicitly requires these stay two separate typed
    // concerns, never one combined "undo the import" row.
    private val sourcePreferences: SourcePreferences = Injekt.get(),
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
            val duplicates = try {
                getDuplicateLibraryManga(manga)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            if (duplicates.isNotEmpty()) {
                return AddResult(manga, Outcome.Duplicate(duplicates.map { it.manga }))
            }
        }

        return try {
            // KMK: build the journal entry from the pre-write
            // manga (favorite=false is guaranteed here by the early return above) before the write,
            // commit only after updateManga.awaitUpdateFavorite returns -- see
            // exh.util.LibraryUndoRecorder's own build-before-write/commit-after-success doc.
            val journalEntry = exh.util.LibraryUndoRecorder.buildFavoriteEntry(sourcePreferences, manga, newFavorite = true)
            setMangaDefaultChapterFlags.await(manga)
            val favoriteWritten = updateManga.awaitUpdateFavorite(manga.id, true)
            if (favoriteWritten) {
                journalEntry?.let { exh.util.LibraryUndoJournal.record(it) }
            }

            val resolvedCategories = categoryIds.ifEmpty { resolveDefaultCategoryIds() }
            setMangaCategories.await(manga.id, resolvedCategories)

            if (libraryPreferences.fetchMetadataOnAdd().get()) {
                try {
                    val source = sourceManager.getOrStub(manga.source)
                    updateMangaFromRemote(
                        source = source,
                        manga = manga,
                        fetchDetails = true,
                        fetchChapters = false,
                    ).getOrThrow()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat(LogPriority.WARN) { "KMK recommendation bundle: metadata fetch failed" }
                }
            }

            AddResult(manga, Outcome.Added)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "KMK recommendation bundle: library add failed" }
            AddResult(manga, Outcome.Error(RecommendationErrorClassifier.classifyToStorageKey(e)))
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
