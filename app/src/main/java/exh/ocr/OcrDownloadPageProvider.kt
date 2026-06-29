package exh.ocr

import android.content.Context
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import mihon.core.archive.archiveReader
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.InputStream

// KMK --> OCR v0.1.0

data class OcrPageRef(
    val manga: Manga,
    val chapter: Chapter,
    val pageIndex: Int,
    val pageIdentity: String,
    val pageModifiedAt: Long?,
    val pageSize: Long?,
    val streamProvider: () -> InputStream?,
)

sealed interface OcrScope {
    data class SingleManga(val mangaId: Long) : OcrScope
    data object AllDownloaded : OcrScope
}

class OcrDownloadPageProvider(
    private val context: Context,
    private val downloadManager: DownloadManager = Injekt.get(),
    private val downloadProvider: DownloadProvider = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
) {

    suspend fun enumeratePages(scope: OcrScope): List<OcrPageRef> {
        val mangaList: List<Manga> = when (scope) {
            is OcrScope.SingleManga -> {
                val allManga = getLibraryManga.await()
                listOfNotNull(allManga.firstOrNull { it.manga.id == scope.mangaId }?.manga)
            }
            OcrScope.AllDownloaded -> getLibraryManga.await().map { it.manga }
        }

        val result = mutableListOf<OcrPageRef>()
        for (manga in mangaList) {
            val source = sourceManager.get(manga.source) ?: continue
            val chapters = getChaptersByMangaId.await(manga.id)
            for (chapter in chapters) {
                if (!downloadManager.isChapterDownloaded(
                        chapter.name,
                        chapter.scanlator,
                        chapter.url,
                        manga.ogTitle,
                        manga.source,
                    )
                ) {
                    continue
                }

                val chapterDir = downloadProvider.findChapterDir(
                    chapter.name,
                    chapter.scanlator,
                    chapter.url,
                    manga.ogTitle,
                    source,
                )

                if (chapterDir?.isFile == true) {
                    // Archive (.cbz) chapter — collect entry names, re-open archive per stream
                    try {
                        val entryNames: List<String> = chapterDir.archiveReader(context).use { reader ->
                            reader.useEntries { entries ->
                                entries
                                    .filter { it.isFile && ImageUtil.isImage(it.name) { reader.getInputStream(it.name)!! } }
                                    .sortedWith { a, b -> a.name.compareTo(b.name, ignoreCase = true) }
                                    .map { it.name }
                                    .toList()
                            }
                        }
                        entryNames.forEachIndexed { idx, entryName ->
                            val capturedDir = chapterDir
                            val identity = "${capturedDir.uri}!$entryName"
                            result.add(
                                OcrPageRef(
                                    manga = manga,
                                    chapter = chapter,
                                    pageIndex = idx,
                                    pageIdentity = identity,
                                    pageModifiedAt = null,
                                    pageSize = null,
                                    streamProvider = {
                                        runCatching {
                                            capturedDir.archiveReader(context).use { reader ->
                                                reader.getInputStream(entryName)?.readBytes()
                                            }?.inputStream()
                                        }.getOrNull()
                                    },
                                ),
                            )
                        }
                    } catch (e: Exception) {
                        continue
                    }
                } else {
                    // Directory chapter
                    try {
                        val pages = downloadManager.buildPageList(source, manga, chapter)
                        for (page in pages) {
                            val uri = page.uri ?: continue
                            val pageFile = chapterDir?.findFile(uri.lastPathSegment ?: "")
                            val identity = uri.toString()
                            result.add(
                                OcrPageRef(
                                    manga = manga,
                                    chapter = chapter,
                                    pageIndex = page.index,
                                    pageIdentity = identity,
                                    pageModifiedAt = pageFile?.lastModified()?.takeIf { it > 0 },
                                    pageSize = pageFile?.length()?.takeIf { it > 0 },
                                    streamProvider = {
                                        runCatching {
                                            context.contentResolver.openInputStream(uri)
                                        }.getOrNull()
                                    },
                                ),
                            )
                        }
                    } catch (e: Exception) {
                        continue
                    }
                }
            }
        }
        return result
    }
}

// KMK <--
