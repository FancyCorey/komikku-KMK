package exh.ocr

import tachiyomi.data.DatabaseHandler

// KMK --> OCR v0.1.1 (updated from v0.1.0)

data class OcrPageState(
    val pageIdentity: String,
    val ocrStatus: String,
    val recognizedWordCount: Long,
    val errorMessage: String?,
)

class OcrIndexRepository(
    private val handler: DatabaseHandler,
) {

    suspend fun upsertPage(
        mangaId: Long,
        sourceId: Long,
        mangaUrl: String,
        mangaTitle: String,
        chapterId: Long,
        chapterUrl: String,
        chapterName: String,
        pageIndex: Int,
        pageUri: String?,
        pageIdentity: String,
        pageModifiedAt: Long?,
        pageSize: Long?,
        engineKey: String,
        engineVersion: String,
        languageHint: String?,
        rawText: String,
        normalizedText: String,
        indexedAt: Long,
        errorMessage: String?,
        recognizedTextLength: Long,
        recognizedWordCount: Long,
        ocrStatus: String,
    ) {
        handler.await(inTransaction = true) {
            ocr_indexed_pageQueries.upsertPage(
                mangaId = mangaId,
                sourceId = sourceId,
                mangaUrl = mangaUrl,
                mangaTitle = mangaTitle,
                chapterId = chapterId,
                chapterUrl = chapterUrl,
                chapterName = chapterName,
                pageIndex = pageIndex.toLong(),
                pageUri = pageUri,
                pageIdentity = pageIdentity,
                pageModifiedAt = pageModifiedAt,
                pageSize = pageSize,
                engineKey = engineKey,
                engineVersion = engineVersion,
                languageHint = languageHint,
                rawText = rawText,
                normalizedText = normalizedText,
                indexedAt = indexedAt,
                errorMessage = errorMessage,
                recognizedTextLength = recognizedTextLength,
                recognizedWordCount = recognizedWordCount,
                ocrStatus = ocrStatus,
            )
        }
    }

    /**
     * Smart search: exact phrase first, then per-token candidates ranked in Kotlin.
     * Only returns rows from the current [engineVersion] with status=success and word count > 0.
     */
    suspend fun searchSmart(query: String, engineVersion: String): List<OcrSearchResult> {
        val normalizedQuery = OcrSearchQueryNormalizer.normalize(query)
        if (normalizedQuery.isBlank()) return emptyList()

        val tokens = OcrSearchRanker.tokenize(normalizedQuery)
        if (tokens.isEmpty()) return emptyList()

        // 1. Exact phrase search
        val exactRows = handler.awaitList {
            ocr_indexed_pageQueries.searchSuccessful(
                engineVersion = engineVersion,
                query = normalizedQuery,
                mapper = ocrRawRowMapper(),
            )
        }
        val exactResults = exactRows.map { row ->
            val score = OcrSearchRanker.score(tokens, row.normalizedText, isExactPhrase = true)!!
            row.toSearchResult(score, query)
        }

        if (exactResults.size >= 5 || tokens.size == 1) {
            return exactResults
        }

        // 2. Per-token candidate search for top 3 tokens
        val seenIds = exactRows.map { it.id }.toHashSet()
        val candidates = mutableMapOf<Long, OcrRawRow>()
        for (token in tokens.take(3)) {
            val rows = handler.awaitList {
                ocr_indexed_pageQueries.searchCandidatesForToken(
                    engineVersion = engineVersion,
                    token = token,
                    mapper = ocrRawRowMapper(),
                )
            }
            for (row in rows) {
                if (row.id !in seenIds) candidates[row.id] = row
            }
        }

        val tokenResults = candidates.values.mapNotNull { row ->
            val score = OcrSearchRanker.score(tokens, row.normalizedText, isExactPhrase = false)
                ?: return@mapNotNull null
            row.toSearchResult(score, query)
        }.sortedByDescending { it.matchScore }

        return (exactResults + tokenResults).take(50)
    }

    /**
     * Returns the existing indexed state for a page at the current engine version.
     * Returns null if no row exists for the current engine version.
     */
    suspend fun getExistingPageState(
        chapterId: Long,
        pageIndex: Int,
        engineKey: String,
        engineVersion: String,
    ): OcrPageState? {
        return handler.awaitOneOrNull {
            ocr_indexed_pageQueries.getExistingPageState(
                chapterId = chapterId,
                pageIndex = pageIndex.toLong(),
                engineKey = engineKey,
                engineVersion = engineVersion,
            ) { identity, status, wordCount, errMsg ->
                OcrPageState(
                    pageIdentity = identity,
                    ocrStatus = status,
                    recognizedWordCount = wordCount,
                    errorMessage = errMsg,
                )
            }
        }
    }

    suspend fun deleteByManga(mangaId: Long) {
        handler.await { ocr_indexed_pageQueries.deleteByManga(mangaId) }
    }

    suspend fun deleteByChapter(chapterId: Long) {
        handler.await { ocr_indexed_pageQueries.deleteByChapter(chapterId) }
    }

    suspend fun deleteAll() {
        handler.await { ocr_indexed_pageQueries.deleteAll() }
    }

    suspend fun deleteOldEngineRows(currentEngineVersion: String) {
        handler.await { ocr_indexed_pageQueries.deleteOldEngineRows(currentEngineVersion) }
    }

    suspend fun deleteEmptyAndFailed() {
        handler.await { ocr_indexed_pageQueries.deleteEmptyAndFailed() }
    }

    suspend fun getStats(engineVersion: String): OcrIndexStats {
        val processed = handler.awaitOne { ocr_indexed_pageQueries.countProcessed(engineVersion) }
        val recognized = handler.awaitOne { ocr_indexed_pageQueries.countRecognized(engineVersion) }
        val empty = handler.awaitOne { ocr_indexed_pageQueries.countEmpty(engineVersion) }
        val failed = handler.awaitOne { ocr_indexed_pageQueries.countFailed(engineVersion) }
        val manga = handler.awaitOne { ocr_indexed_pageQueries.countDistinctManga(engineVersion) }
        val chapters = handler.awaitOne { ocr_indexed_pageQueries.countDistinctChapters(engineVersion) }
        val bytes = handler.awaitOne { ocr_indexed_pageQueries.estimatedIndexBytes() }
        val oldRows = handler.awaitOne { ocr_indexed_pageQueries.countOldEngineRows(engineVersion) }
        return OcrIndexStats(
            processedPages = processed,
            recognizedPages = recognized,
            emptyPages = empty,
            failedPages = failed,
            totalManga = manga,
            totalChapters = chapters,
            estimatedBytes = bytes,
            oldEngineRows = oldRows,
        )
    }
}

// Internal raw row: only the 9 columns selected by searchSuccessful / searchCandidatesForToken
private data class OcrRawRow(
    val id: Long,
    val mangaId: Long,
    val sourceId: Long,
    val mangaTitle: String,
    val chapterId: Long,
    val chapterName: String,
    val pageIndex: Long,
    val rawText: String,
    val normalizedText: String,
)

private fun ocrRawRowMapper(): (Long, Long, Long, String, Long, String, Long, String, String) -> OcrRawRow =
    { id, mangaId, sourceId, mangaTitle, chapterId, chapterName, pageIndex, rawText, normalizedText ->
        OcrRawRow(
            id = id,
            mangaId = mangaId,
            sourceId = sourceId,
            mangaTitle = mangaTitle,
            chapterId = chapterId,
            chapterName = chapterName,
            pageIndex = pageIndex,
            rawText = rawText,
            normalizedText = normalizedText,
        )
    }

private fun OcrRawRow.toSearchResult(score: OcrMatchScore, query: String): OcrSearchResult =
    OcrSearchResult(
        mangaId = mangaId,
        mangaTitle = mangaTitle,
        sourceId = sourceId,
        sourceName = null, // resolved in screenmodel via SourceManager
        chapterId = chapterId,
        chapterName = chapterName,
        pageIndex = pageIndex.toInt(),
        snippet = OcrSearchQueryNormalizer.buildSnippet(rawText, normalizedText, query),
        matchType = score.matchType,
        matchedWords = score.matchedWords,
        missingWords = score.missingWords,
        matchScore = score.score,
    )

// KMK <--
