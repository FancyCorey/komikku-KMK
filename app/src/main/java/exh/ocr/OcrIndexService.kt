package exh.ocr

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK --> OCR v0.1.1 (updated from v0.1.0)

const val OCR_STATUS_SUCCESS = "success"
const val OCR_STATUS_EMPTY = "empty"
const val OCR_STATUS_FAILED = "failed"

data class OcrIndexProgress(
    val currentManga: String = "",
    val currentChapter: String = "",
    val completedPages: Int = 0,
    val recognizedPages: Int = 0,
    val emptyPages: Int = 0,
    val totalPages: Int = 0,
    val failedPages: Int = 0,
    val lastError: String? = null,
    val isRunning: Boolean = false,
    val isComplete: Boolean = false,
    val isCancelled: Boolean = false,
    val isFailed: Boolean = false,
)

enum class OcrRetryMode {
    /** Normal: skip existing success rows. Retry empty/failed (v0.1.0 rows). */
    SKIP_SUCCESS_RETRY_EMPTY_FAILED,
    /** Force: re-index everything regardless of existing status. */
    FORCE_ALL,
}

typealias OcrProgressCallback = suspend (OcrIndexProgress) -> Unit

class OcrIndexService(
    private val context: Context,
    private val recognizer: OcrTextRecognizer = MlKitLatinOcrTextRecognizer(context),
    private val pageProvider: OcrDownloadPageProvider = OcrDownloadPageProvider(context),
    private val repository: OcrIndexRepository = Injekt.get(),
) {

    suspend fun runIndexing(
        scope: OcrScope,
        retryMode: OcrRetryMode = OcrRetryMode.SKIP_SUCCESS_RETRY_EMPTY_FAILED,
        maxPages: Int = 0,
        onProgress: OcrProgressCallback,
    ) {
        val allPages = try {
            pageProvider.enumeratePages(scope)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "OCR: failed to enumerate pages" }
            onProgress(OcrIndexProgress(isFailed = true, lastError = e.message))
            return
        }

        val effectivePages = if (maxPages > 0) allPages.take(maxPages) else allPages
        val total = effectivePages.size
        var completed = 0
        var recognized = 0
        var empty = 0
        var failed = 0

        onProgress(OcrIndexProgress(totalPages = total, isRunning = true))

        for (pageRef in effectivePages) {
            if (!currentCoroutineContext().isActive) {
                onProgress(
                    OcrIndexProgress(
                        completedPages = completed,
                        recognizedPages = recognized,
                        emptyPages = empty,
                        totalPages = total,
                        failedPages = failed,
                        isCancelled = true,
                    ),
                )
                return
            }

            // Skip logic: only skip pages that are already successfully indexed by the
            // current engine/preprocessing version with actual recognized text.
            // Empty and failed rows are always retried unless forceAll is set.
            val state = try {
                repository.getExistingPageState(
                    chapterId = pageRef.chapter.id,
                    pageIndex = pageRef.pageIndex,
                    engineKey = recognizer.engineKey,
                    engineVersion = recognizer.engineVersion,
                )
            } catch (e: Exception) {
                null
            }

            val shouldSkip = retryMode != OcrRetryMode.FORCE_ALL &&
                state != null &&
                state.pageIdentity == pageRef.pageIdentity &&
                state.ocrStatus == OCR_STATUS_SUCCESS &&
                state.recognizedWordCount > 0

            if (shouldSkip) {
                completed++
                recognized++
                onProgress(
                    OcrIndexProgress(
                        currentManga = pageRef.manga.title,
                        currentChapter = pageRef.chapter.name,
                        completedPages = completed,
                        recognizedPages = recognized,
                        emptyPages = empty,
                        totalPages = total,
                        failedPages = failed,
                        isRunning = true,
                    ),
                )
                continue
            }

            try {
                val stream = pageRef.streamProvider()
                val rawText = if (stream != null) {
                    val result = recognizer.recognizeText(OcrImageInput(stream, pageRef.pageIdentity))
                    result.rawText
                } else {
                    ""
                }

                val normalizedText = OcrSearchQueryNormalizer.normalize(rawText)
                val wordCount = OcrSearchRanker.tokenize(normalizedText).size.toLong()
                val ocrStatus = when {
                    rawText.isBlank() -> OCR_STATUS_EMPTY
                    wordCount == 0L -> OCR_STATUS_EMPTY
                    else -> OCR_STATUS_SUCCESS
                }

                repository.upsertPage(
                    mangaId = pageRef.manga.id,
                    sourceId = pageRef.manga.source,
                    mangaUrl = pageRef.manga.url,
                    mangaTitle = pageRef.manga.title,
                    chapterId = pageRef.chapter.id,
                    chapterUrl = pageRef.chapter.url,
                    chapterName = pageRef.chapter.name,
                    pageIndex = pageRef.pageIndex,
                    pageUri = pageRef.pageIdentity,
                    pageIdentity = pageRef.pageIdentity,
                    pageModifiedAt = pageRef.pageModifiedAt,
                    pageSize = pageRef.pageSize,
                    engineKey = recognizer.engineKey,
                    engineVersion = recognizer.engineVersion,
                    languageHint = "la",
                    rawText = rawText,
                    normalizedText = normalizedText,
                    indexedAt = System.currentTimeMillis(),
                    errorMessage = null,
                    recognizedTextLength = rawText.length.toLong(),
                    recognizedWordCount = wordCount,
                    ocrStatus = ocrStatus,
                )
                completed++
                if (ocrStatus == OCR_STATUS_SUCCESS) recognized++ else empty++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) {
                    "OCR: page error manga=${pageRef.manga.title} ch=${pageRef.chapter.name} page=${pageRef.pageIndex}"
                }
                failed++
                completed++
                try {
                    repository.upsertPage(
                        mangaId = pageRef.manga.id,
                        sourceId = pageRef.manga.source,
                        mangaUrl = pageRef.manga.url,
                        mangaTitle = pageRef.manga.title,
                        chapterId = pageRef.chapter.id,
                        chapterUrl = pageRef.chapter.url,
                        chapterName = pageRef.chapter.name,
                        pageIndex = pageRef.pageIndex,
                        pageUri = pageRef.pageIdentity,
                        pageIdentity = pageRef.pageIdentity,
                        pageModifiedAt = pageRef.pageModifiedAt,
                        pageSize = pageRef.pageSize,
                        engineKey = recognizer.engineKey,
                        engineVersion = recognizer.engineVersion,
                        languageHint = "la",
                        rawText = "",
                        normalizedText = "",
                        indexedAt = System.currentTimeMillis(),
                        errorMessage = e.message ?: "Unknown error",
                        recognizedTextLength = 0L,
                        recognizedWordCount = 0L,
                        ocrStatus = OCR_STATUS_FAILED,
                    )
                } catch (dbEx: Exception) {
                    logcat(LogPriority.ERROR, dbEx) { "OCR: also failed to write error row" }
                }
            }

            onProgress(
                OcrIndexProgress(
                    currentManga = pageRef.manga.title,
                    currentChapter = pageRef.chapter.name,
                    completedPages = completed,
                    recognizedPages = recognized,
                    emptyPages = empty,
                    totalPages = total,
                    failedPages = failed,
                    isRunning = true,
                ),
            )
        }

        onProgress(
            OcrIndexProgress(
                completedPages = completed,
                recognizedPages = recognized,
                emptyPages = empty,
                totalPages = total,
                failedPages = failed,
                isComplete = true,
            ),
        )
    }
}

// KMK <--
