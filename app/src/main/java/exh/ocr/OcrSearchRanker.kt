package exh.ocr

// KMK --> OCR v0.1.1

enum class OcrMatchType { EXACT, ALL_TOKENS, PARTIAL }

data class OcrMatchScore(
    val matchType: OcrMatchType,
    val matchedWords: List<String>,
    val missingWords: List<String>,
    val score: Double,
)

object OcrSearchRanker {

    // Common short words that add noise to token matching
    private val STOP_WORDS = setOf(
        "a", "an", "the", "is", "in", "on", "at", "to", "of", "and", "or",
        "i", "it", "be", "do", "we", "he", "my", "if", "so", "up",
    )

    private const val MIN_TOKEN_LENGTH = 2
    private const val MIN_MATCH_RATIO = 0.6

    /**
     * Splits normalized text into useful search tokens.
     * Removes stop words and very short tokens.
     */
    fun tokenize(normalizedText: String): List<String> =
        normalizedText.split(" ")
            .filter { it.length >= MIN_TOKEN_LENGTH && it !in STOP_WORDS }
            .distinct()

    /**
     * Scores how well [queryTokens] match [normalizedPageText].
     * Returns null if below match threshold (should be excluded from results).
     *
     * [isExactPhrase] should be true when the caller already confirmed a substring match
     * of the full normalized query — avoids re-checking tokens in that case.
     */
    fun score(
        queryTokens: List<String>,
        normalizedPageText: String,
        isExactPhrase: Boolean,
    ): OcrMatchScore? {
        if (queryTokens.isEmpty()) return null

        if (isExactPhrase) {
            return OcrMatchScore(
                matchType = OcrMatchType.EXACT,
                matchedWords = queryTokens,
                missingWords = emptyList(),
                score = 1.0,
            )
        }

        val matched = queryTokens.filter { normalizedPageText.contains(it) }
        val missing = queryTokens.filter { !normalizedPageText.contains(it) }
        val ratio = matched.size.toDouble() / queryTokens.size

        // Single token: must match. Multi-token: at least 60%.
        val threshold = if (queryTokens.size == 1) 1.0 else MIN_MATCH_RATIO
        if (ratio < threshold) return null

        val matchType = if (matched.size == queryTokens.size) OcrMatchType.ALL_TOKENS else OcrMatchType.PARTIAL

        return OcrMatchScore(
            matchType = matchType,
            matchedWords = matched,
            missingWords = missing,
            score = ratio,
        )
    }
}

// KMK <--
