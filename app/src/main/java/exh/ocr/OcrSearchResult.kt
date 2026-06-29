package exh.ocr

// KMK --> OCR v0.1.1 (updated from v0.1.0)

data class OcrSearchResult(
    val mangaId: Long,
    val mangaTitle: String,
    val sourceId: Long,
    val sourceName: String?,
    val chapterId: Long,
    val chapterName: String,
    val pageIndex: Int,
    val snippet: String,
    val matchType: OcrMatchType,
    val matchedWords: List<String>,
    val missingWords: List<String>,
    val matchScore: Double,
)

data class OcrIndexStats(
    val processedPages: Long,
    val recognizedPages: Long,
    val emptyPages: Long,
    val failedPages: Long,
    val totalManga: Long,
    val totalChapters: Long,
    val estimatedBytes: Long,
    val oldEngineRows: Long,
)

// KMK <--
