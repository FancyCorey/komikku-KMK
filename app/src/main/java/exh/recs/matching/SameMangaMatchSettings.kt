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
    val preselectionMode: SameMangaPreselectionMode =
        if (preselectResults) SameMangaPreselectionMode.ALL else SameMangaPreselectionMode.NONE,
) {
    companion object {
        const val MIN_RESULT_CAP = 1
        const val MAX_RESULT_CAP = 10
        const val MIN_SAMPLE_SIZE = 2
        const val MAX_SAMPLE_SIZE = 10
        val VALID_RESULT_CAPS = (MIN_RESULT_CAP..MAX_RESULT_CAP).toList()
        val VALID_SAMPLE_SIZES = (MIN_SAMPLE_SIZE..MAX_SAMPLE_SIZE).toList()
        val DEFAULT_RESULT_CAP = 2
        val DEFAULT_SAMPLE_SIZE = 5

        fun clampResultCap(value: Int): Int =
            if (value in VALID_RESULT_CAPS) value else DEFAULT_RESULT_CAP

        fun clampSampleSize(value: Int): Int =
            if (value in VALID_SAMPLE_SIZES) value else DEFAULT_SAMPLE_SIZE
    }
}

enum class SameMangaPreselectionMode(val storedValue: String) {
    ALL("all"),
    NONE("none"),
    EXACT_NAME("exact_name"),
    ;

    companion object {
        // KMK --> EC-04 2026-09-01: migration-safe legacy-boolean fallback.
        // `legacyPreselect` cannot distinguish "the user never touched the old toggle" (still at its
        // own true default) from "the user explicitly re-confirmed enabled" -- both read back as
        // `true`. A `false` reading, however, IS an unambiguous signal: the old toggle's own default
        // was `true`, so `false` only occurs when a user actually disabled it. Preserve that explicit
        // opt-out as NONE. Otherwise -- no explicit mode stored, and no unambiguous legacy signal --
        // resolve to the same EXACT_NAME default a fresh install gets, so the promised exact-title
        // default is truthful for restored/legacy-only profiles instead of silently staying on the
        // old ALL behavior forever.
        fun resolve(storedValue: String, legacyPreselect: Boolean): SameMangaPreselectionMode =
            entries.firstOrNull { it.storedValue == storedValue }
                ?: if (legacyPreselect) EXACT_NAME else NONE
        // KMK <--
    }
}
// KMK <--
