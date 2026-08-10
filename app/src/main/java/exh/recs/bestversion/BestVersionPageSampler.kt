package exh.recs.bestversion

// KMK --> v0.7.8
/**
 * Pure helper that selects a bounded sample of page indexes from a chapter
 * page list for the Best Version visual comparison.
 *
 * Algorithm:
 * - If page count <= sample size, return all page indexes.
 * - When avoidFirstPages and enough pages exist, skip the first 1–2 pages.
 * - Avoid the final page when possible.
 * - Sample roughly from 30–75% of the chapter.
 *
 * Returns a list of 0-based page indexes.
 */
object BestVersionPageSampler {

    fun sample(
        totalPages: Int,
        sampleSize: Int,
        avoidFirstPages: Boolean,
    ): List<Int> {
        if (totalPages <= 0) return emptyList()
        if (sampleSize <= 0) return emptyList()
        if (totalPages <= sampleSize) return (0 until totalPages).toList()

        val skipStart = if (avoidFirstPages && totalPages > sampleSize + 2) 2 else 0
        // Work in 30–75% window of the chapter; end -1 to avoid final page when possible
        val windowStart = maxOf(skipStart, (totalPages * 0.30).toInt())
        val windowEnd = minOf(totalPages - 2, (totalPages * 0.75).toInt())

        return if (windowEnd - windowStart + 1 >= sampleSize) {
            // Evenly spread within window
            val step = (windowEnd - windowStart).toDouble() / (sampleSize - 1)
            (0 until sampleSize).map { i ->
                (windowStart + i * step).toInt().coerceIn(windowStart, windowEnd)
            }.distinct()
        } else {
            // Window too narrow — fall back to evenly spread across all pages
            val start = skipStart
            val end = totalPages - 1
            val step = (end - start).toDouble() / (sampleSize - 1)
            (0 until sampleSize).map { i ->
                (start + i * step).toInt().coerceIn(start, end)
            }.distinct()
        }
    }
}
// KMK <--
