package exh.recs.matching

// KMK --> v0.7.8
/**
 * Resolved preferences for a bounded same-manga search session.
 * Clamps out-of-range values to safe defaults.
 */
data class SameMangaMatchSettings(
    val resultsPerSource: Int,
    val preselectResults: Boolean,
    val previewSampleSize: Int,
    val avoidFirstPages: Boolean,
) {
    companion object {
        val VALID_RESULT_CAPS = listOf(1, 2, 5, 10)
        val VALID_SAMPLE_SIZES = listOf(2, 5, 10)
        val DEFAULT_RESULT_CAP = 2
        val DEFAULT_SAMPLE_SIZE = 5

        fun clampResultCap(value: Int): Int =
            if (value in VALID_RESULT_CAPS) value else DEFAULT_RESULT_CAP

        fun clampSampleSize(value: Int): Int =
            if (value in VALID_SAMPLE_SIZES) value else DEFAULT_SAMPLE_SIZE
    }
}
// KMK <--
