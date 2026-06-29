package exh.ocr

// KMK --> OCR v0.1.1 (updated from v0.1.0)

object OcrSearchQueryNormalizer {

    fun normalize(text: String): String =
        text.lowercase().replace(Regex("[\\p{Punct}\\s]+"), " ").trim()

    /** Returns the significant search tokens for the given query. */
    fun tokenize(query: String): List<String> =
        OcrSearchRanker.tokenize(normalize(query))

    /**
     * Builds a display snippet from [rawText] using the [query] to find a good anchor position.
     * Falls back to normalized text if raw text doesn't contain a match position.
     */
    fun buildSnippet(rawText: String, normalizedText: String, query: String, contextChars: Int = 80): String {
        val normalizedQuery = normalize(query)

        // Try to find match in raw text (case-insensitive)
        val rawLower = rawText.lowercase()
        val rawIdx = rawLower.indexOf(normalizedQuery)
        if (rawIdx >= 0) {
            val start = maxOf(0, rawIdx - contextChars)
            val end = minOf(rawText.length, rawIdx + normalizedQuery.length + contextChars)
            val snippet = rawText.substring(start, end)
            return if (start > 0) "…$snippet" else snippet
        }

        // Try token-by-token in raw text
        val tokens = tokenize(query)
        for (token in tokens) {
            val idx = rawLower.indexOf(token)
            if (idx >= 0) {
                val start = maxOf(0, idx - contextChars)
                val end = minOf(rawText.length, idx + token.length + contextChars)
                val snippet = rawText.substring(start, end)
                return if (start > 0) "…$snippet" else snippet
            }
        }

        // Fall back to normalized text excerpt
        val idx = normalizedText.indexOf(normalizedQuery)
        return if (idx >= 0) {
            val start = maxOf(0, idx - contextChars)
            val end = minOf(normalizedText.length, idx + normalizedQuery.length + contextChars)
            val snippet = normalizedText.substring(start, end)
            if (start > 0) "…$snippet" else snippet
        } else {
            rawText.take(contextChars * 2)
        }
    }

    /** Compat overload used by existing tests — operates on normalized text only. */
    fun buildSnippet(normalizedText: String, query: String, contextChars: Int = 80): String =
        buildSnippet(rawText = normalizedText, normalizedText = normalizedText, query = query, contextChars = contextChars)
}

// KMK <--
