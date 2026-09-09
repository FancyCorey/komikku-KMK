package exh

import android.content.Context
import androidx.core.net.toUri
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.getOrThrowSourceRuntimeException
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.source.online.all.EHentai
import exh.log.ResettableLogger
import exh.log.safeXLogStackTag
import exh.source.getMainSource
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import mihon.domain.source.interactor.UpdateMangaFromRemote
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.sy.SYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class GalleryAdder(
    private val updateManga: UpdateManga = Injekt.get(),
    private val updateMangaFromRemote: UpdateMangaFromRemote = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val getChapter: GetChapter = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
) {

    private val filters: Pair<Set<String>, Set<Long>> = Injekt.get<SourcePreferences>().run {
        enabledLanguages().get() to disabledSources().get().map { it.toLong() }.toSet()
    }

    private val Pair<Set<String>, Set<Long>>.enabledLangs
        get() = first
    private val Pair<Set<String>, Set<Long>>.disabledSources
        get() = second

    // KMK -->
    private val logger = ResettableLogger { safeXLogStackTag() }
    // KMK <--

    fun pickSource(url: String): List<UrlImportableSource> {
        val uri = url.toUri()
        return sourceManager.getVisibleSources()
            .mapNotNull { it.getMainSource<UrlImportableSource>() }
            .filter {
                it.lang in filters.enabledLangs &&
                    it.id !in filters.disabledSources &&
                    try {
                        it.matchesUri(uri)
                    } catch (_: Exception) {
                        false
                    }
            }
    }

    suspend fun addGallery(
        context: Context,
        url: String,
        fav: Boolean = false,
        forceSource: UrlImportableSource? = null,
        throttleFunc: suspend () -> Unit = {},
        retry: Int = 1,
    ): GalleryAddEvent {
        logger()?.d("Gallery import started; favorite=$fav; forcedSource=${forceSource != null}")
        try {
            val uri = url.toUri()

            // Find matching source
            val source = if (forceSource != null) {
                try {
                    if (forceSource.matchesUri(uri)) {
                        forceSource
                    } else {
                        return GalleryAddEvent.Fail(GalleryAddFailureKind.UNKNOWN_SOURCE)
                    }
                } catch (e: Exception) {
                    logger()?.e(context.stringResource(SYMR.strings.gallery_adder_source_uri_must_match))
                    return GalleryAddEvent.Fail(GalleryAddFailureKind.UNKNOWN_TYPE)
                }
            } else {
                sourceManager.getVisibleSources()
                    .mapNotNull { it.getMainSource<UrlImportableSource>() }
                    .find {
                        it.lang in filters.enabledLangs &&
                            it.id !in filters.disabledSources &&
                            try {
                                it.matchesUri(uri)
                            } catch (_: Exception) {
                                false
                            }
                    } ?: return GalleryAddEvent.Fail(GalleryAddFailureKind.UNKNOWN_SOURCE)
            }

            val realChapterUrl = try {
                source.mapUrlToChapterUrl(uri)
            } catch (e: Exception) {
                logger()?.e(context.stringResource(SYMR.strings.gallery_adder_uri_map_to_chapter_error))
                null
            }

            val cleanedChapterUrl = if (realChapterUrl != null) {
                try {
                    source.cleanChapterUrl(realChapterUrl)
                } catch (e: Exception) {
                    logger()?.e(context.stringResource(SYMR.strings.gallery_adder_uri_clean_error))
                    null
                }
            } else {
                null
            }

            val chapterMangaUrl = if (realChapterUrl != null) {
                source.mapChapterUrlToMangaUrl(realChapterUrl.toUri())
            } else {
                null
            }

            // Map URL to manga URL
            val realMangaUrl = try {
                chapterMangaUrl ?: source.mapUrlToMangaUrl(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger()?.e(context.stringResource(SYMR.strings.gallery_adder_uri_map_to_gallery_error))
                null
            } ?: return GalleryAddEvent.Fail(GalleryAddFailureKind.UNKNOWN_TYPE)

            // Clean URL
            val cleanedMangaUrl = try {
                source.cleanMangaUrl(realMangaUrl)
            } catch (e: Exception) {
                logger()?.e(context.stringResource(SYMR.strings.gallery_adder_uri_clean_error))
                null
            } ?: return GalleryAddEvent.Fail(GalleryAddFailureKind.UNKNOWN_TYPE)

            // Use manga in DB if possible, otherwise, make a new manga
            var manga = networkToLocalManga(
                Manga.create().copy(
                    source = source.id,
                    url = cleanedMangaUrl,
                ),
            )

            // Fetch and copy details
            // KMK v0.8.10-fix7: genuine double hole -- both this function's outer catch(Exception)
            // and the retry() helper's own catch(Exception) do not catch Error, so a raw
            // NoClassDefFoundError from .getOrThrow() would have escaped both layers uncaught.
            manga = retry(retry) {
                updateMangaFromRemote(
                    manga = manga,
                    fetchDetails = true,
                    fetchChapters = true,
                    throttleFunc = throttleFunc,
                ).getOrThrowSourceRuntimeException().manga
            }

            if (fav) {
                updateManga.awaitUpdateFavorite(manga.id, true)
                manga = manga.copy(favorite = true)
            }

            return if (cleanedChapterUrl != null) {
                val chapter = getChapter.await(cleanedChapterUrl, manga.id)
                if (chapter != null) {
                    GalleryAddEvent.Success(manga, chapter)
                } else {
                    GalleryAddEvent.Fail(GalleryAddFailureKind.CHAPTER_NOT_FOUND)
                }
            } else {
                GalleryAddEvent.Success(manga)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger()?.w("Gallery import failed")

            if (e is EHentai.GalleryNotFoundException) {
                return GalleryAddEvent.Fail(GalleryAddFailureKind.NOT_FOUND)
            }

            return GalleryAddEvent.Fail(GalleryAddFailureKind.IMPORT_FAILED)
        }
    }

    private inline fun <T : Any> retry(retryCount: Int, block: () -> T): T {
        var result: T? = null
        var lastError: Exception? = null

        for (i in 1..retryCount) {
            try {
                result = block()
                break
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is EHentai.GalleryNotFoundException) {
                    throw e
                }
                lastError = e
            }
        }

        if (lastError != null) {
            throw lastError
        }

        return result!!
    }
}

sealed class GalleryAddEvent {
    class Success(
        val manga: Manga,
        val chapter: Chapter? = null,
    ) : GalleryAddEvent()

    data class Fail(val reason: GalleryAddFailureKind) : GalleryAddEvent()
}

@Serializable
enum class GalleryAddFailureKind {
    UNKNOWN_TYPE,
    UNKNOWN_SOURCE,
    NOT_FOUND,
    CHAPTER_NOT_FOUND,
    IMPORT_FAILED,
}
