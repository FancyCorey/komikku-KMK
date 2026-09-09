package eu.kanade.tachiyomi.ui.reader.bridge

import kotlinx.coroutines.CancellationException
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.taste.model.AlternateSourceBridgeKey
import tachiyomi.domain.taste.model.CrossSourceRecordKey

class AlternateSourceReaderRouteResolver(
    private val getManga: GetManga,
    private val getChapter: GetChapter,
) {
    sealed interface Result {
        data class Resolved(val route: AlternateSourceReaderRoute) : Result
        data object InvalidRequest : Result
        data object MangaUnavailable : Result
        data object ChapterUnavailable : Result
        data object IdentityMismatch : Result
        data object Failed : Result
    }

    suspend fun resolve(
        bridgeKey: AlternateSourceBridgeKey,
        role: AlternateSourceReaderRouteRole,
        chapterUrl: String,
        pageIndex: Int,
    ): Result {
        val record = when (role) {
            AlternateSourceReaderRouteRole.PRIMARY -> bridgeKey.primary
            AlternateSourceReaderRouteRole.ALTERNATE -> bridgeKey.alternate
        }
        if (!isValidRequest(bridgeKey, record, chapterUrl, pageIndex)) return Result.InvalidRequest
        return try {
            val manga = getManga.await(record.url, record.source) ?: return Result.MangaUnavailable
            if (manga.id <= 0L || manga.source != record.source || manga.url != record.url) {
                return Result.IdentityMismatch
            }
            val chapter = getChapter.await(chapterUrl, manga.id) ?: return Result.ChapterUnavailable
            if (chapter.id <= 0L || chapter.mangaId != manga.id || chapter.url != chapterUrl) {
                return Result.IdentityMismatch
            }
            Result.Resolved(
                AlternateSourceReaderRoute(
                    role = role,
                    record = record,
                    mangaId = manga.id,
                    chapterUrl = chapter.url,
                    chapterId = chapter.id,
                    pageIndex = pageIndex,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failed
        }
    }

    private fun isValidRequest(
        bridgeKey: AlternateSourceBridgeKey,
        record: CrossSourceRecordKey,
        chapterUrl: String,
        pageIndex: Int,
    ): Boolean =
        tachiyomi.domain.taste.model.AlternateSourceBridgePolicy.isValidKey(bridgeKey) &&
            record.source > 0L &&
            record.url.isNotBlank() &&
            chapterUrl.isNotBlank() &&
            chapterUrl.length <= tachiyomi.domain.taste.model.AlternateSourceBridgePolicy.MAX_URL_LENGTH &&
            pageIndex in 0..AlternateSourceReaderSession.MAX_PAGE_INDEX
}
