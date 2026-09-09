package exh.perf

import tachiyomi.domain.taste.model.MangaRating

// KMK HR-2026-08-26-CONSTRAINED-DEVICE-PERFORMANCE-PROGRAM (C4) -->
/**
 * Configuration for [PerformanceFixtureGenerator]/[PerformanceFixtureSeeder] -- the reusable
 * realistic dataset section D of the recovery intake requires: "approximately 1,000 meaningful
 * manga, not 1,000 blank/random rows," modeling categories, varied chapter shapes, read/history/
 * bookmark state, ratings, cross-source links, local tracking, and a bounded slice of malformed/
 * unavailable-source rows.
 *
 * [seed] makes generation fully deterministic -- the same spec always produces the same dataset,
 * so a measurement regression can be attributed to a real app change, never to fixture drift
 * between runs. Every `*Fraction` field is a fraction of [totalManga]; [chapterProfileFor] assigns
 * a deterministic per-manga chapter shape by index (not by the RNG) so every required edge case
 * (zero, decimals, specials, gaps, duplicates, long-running) is guaranteed present even at [SMALL]
 * scale, not merely probable at [REALISTIC] scale.
 */
data class PerformanceFixtureSpec(
    val totalManga: Int,
    val categoryCount: Int,
    val favoriteFraction: Double,
    val historyFraction: Double,
    val ratingFractions: Map<MangaRating, Double>,
    val crossSourceGroupFraction: Double,
    val localTrackingFraction: Double,
    val unavailableSourceFraction: Double,
    val malformedMetadataFraction: Double,
    val longRunningCount: Int,
    val seed: Long,
) {
    init {
        require(totalManga > 0) { "totalManga must be positive" }
        require(categoryCount >= 0) { "categoryCount must not be negative" }
        require(longRunningCount in 0..totalManga) { "longRunningCount must fit within totalManga" }
        listOf(
            "favoriteFraction" to favoriteFraction,
            "historyFraction" to historyFraction,
            "crossSourceGroupFraction" to crossSourceGroupFraction,
            "localTrackingFraction" to localTrackingFraction,
            "unavailableSourceFraction" to unavailableSourceFraction,
            "malformedMetadataFraction" to malformedMetadataFraction,
        ).forEach { (name, value) -> require(value in 0.0..1.0) { "$name must be within [0,1], was $value" } }
        val ratingTotal = ratingFractions.values.sum()
        require(ratingTotal in 0.0..1.0 + 1e-9) { "ratingFractions must sum to at most 1.0, was $ratingTotal" }
    }

    companion object {
        /**
         * Small, fast-to-generate-and-seed preset for host tests -- still large enough (>= 12) that
         * every deterministic chapter-profile bucket in [chapterProfileFor] appears at least once.
         */
        val SMALL = PerformanceFixtureSpec(
            totalManga = 40,
            categoryCount = 4,
            favoriteFraction = 0.6,
            historyFraction = 0.5,
            ratingFractions = mapOf(
                MangaRating.LOVE to 0.10,
                MangaRating.LIKE to 0.15,
                MangaRating.DISLIKE to 0.05,
                MangaRating.NOT_INTERESTED to 0.05,
            ),
            crossSourceGroupFraction = 0.15,
            localTrackingFraction = 0.2,
            unavailableSourceFraction = 0.1,
            malformedMetadataFraction = 0.05,
            longRunningCount = 2,
            seed = 20260826L,
        )

        /** The real ~1,000-manga target for the on-device constrained-performance measurement pass. */
        val REALISTIC = SMALL.copy(totalManga = 1_000, categoryCount = 14, longRunningCount = 20)

        /** Stable named profiles used by the host wrapper and device evidence receipts. */
        val PROFILES: Map<String, PerformanceFixtureSpec> = linkedMapOf(
            "empty" to SMALL.copy(
                totalManga = 1,
                categoryCount = 0,
                favoriteFraction = 0.0,
                historyFraction = 0.0,
                ratingFractions = emptyMap(),
                crossSourceGroupFraction = 0.0,
                localTrackingFraction = 0.0,
                unavailableSourceFraction = 0.0,
                malformedMetadataFraction = 0.0,
                longRunningCount = 0,
                seed = 2026082801L,
            ),
            "realistic-1k" to REALISTIC,
            "source-populated" to SMALL.copy(
                totalManga = 120,
                categoryCount = 8,
                crossSourceGroupFraction = 0.3,
                unavailableSourceFraction = 0.02,
                malformedMetadataFraction = 0.02,
                longRunningCount = 4,
                seed = 2026082802L,
            ),
            "large-rated-groups" to REALISTIC.copy(
                categoryCount = 24,
                favoriteFraction = 0.95,
                historyFraction = 0.7,
                ratingFractions = MangaRating.entries.associateWith { 0.2 },
                crossSourceGroupFraction = 0.5,
                localTrackingFraction = 0.5,
                unavailableSourceFraction = 0.01,
                malformedMetadataFraction = 0.01,
                longRunningCount = 25,
                seed = 2026082803L,
            ),
            "best-version-chapters" to SMALL.copy(
                totalManga = 96,
                categoryCount = 8,
                historyFraction = 0.4,
                unavailableSourceFraction = 0.15,
                malformedMetadataFraction = 0.08,
                longRunningCount = 12,
                seed = 2026082804L,
            ),
            "archive-reader" to SMALL.copy(
                totalManga = 80,
                categoryCount = 6,
                favoriteFraction = 0.8,
                historyFraction = 0.8,
                localTrackingFraction = 0.4,
                longRunningCount = 8,
                seed = 2026082805L,
            ),
            "failure-offline" to SMALL.copy(
                totalManga = 120,
                categoryCount = 4,
                favoriteFraction = 0.5,
                historyFraction = 0.2,
                ratingFractions = emptyMap(),
                crossSourceGroupFraction = 0.1,
                localTrackingFraction = 0.1,
                unavailableSourceFraction = 0.5,
                malformedMetadataFraction = 0.25,
                longRunningCount = 4,
                seed = 2026082806L,
            ),
            "process-recreation" to SMALL.copy(
                totalManga = 40,
                categoryCount = 4,
                historyFraction = 0.6,
                longRunningCount = 2,
                seed = 2026082807L,
            ),
        )

        fun profile(name: String): PerformanceFixtureSpec =
            PROFILES[name] ?: error("Unknown performance fixture profile: $name")
    }
}

/** Deterministic per-manga chapter-generation strategy -- see [PerformanceFixtureSpec]'s own doc. */
internal enum class ChapterProfile {
    /** No chapters at all -- an added-but-never-fetched or a source with an empty catalogue entry. */
    ZERO,

    /** A handful of ordinary sequential chapters (1..5). */
    FEW,

    /** A normal-sized run (6..60) with no irregularities. */
    NORMAL,

    /** A normal-sized run with deliberate gaps in chapter numbering (e.g. 1,2,4,5,8..). */
    NORMAL_WITH_GAPS,

    /** A normal-sized run including one duplicated chapter number (two releases of the same chapter). */
    NORMAL_WITH_DUPLICATES,

    /** A normal-sized run including decimal (sub-)chapter numbers (e.g. 4.5, 12.1). */
    NORMAL_WITH_DECIMALS,

    /** A very long-running series (500..1200 chapters) -- stresses Library/chapter-list rendering. */
    LONG_RUNNING,
}

/**
 * Assigns [ChapterProfile] deterministically by [index] (0-based manga index within the fixture),
 * cycling through every non-long-running profile every 6 manga, with the first
 * [PerformanceFixtureSpec.longRunningCount] manga overridden to [ChapterProfile.LONG_RUNNING]
 * regardless of their position in that cycle -- long-running titles are rare in a real library, but
 * guaranteed present rather than merely likely.
 */
internal fun PerformanceFixtureSpec.chapterProfileFor(index: Int): ChapterProfile {
    if (index < longRunningCount) return ChapterProfile.LONG_RUNNING
    return when (index % 6) {
        0 -> ChapterProfile.ZERO
        1 -> ChapterProfile.FEW
        2 -> ChapterProfile.NORMAL
        3 -> ChapterProfile.NORMAL_WITH_GAPS
        4 -> ChapterProfile.NORMAL_WITH_DUPLICATES
        else -> ChapterProfile.NORMAL_WITH_DECIMALS
    }
}
// KMK <--
