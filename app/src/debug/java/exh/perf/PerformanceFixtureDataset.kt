package exh.perf

import tachiyomi.domain.taste.model.MangaRating

// KMK HR-2026-08-26-CONSTRAINED-DEVICE-PERFORMANCE-PROGRAM (C4) -->
/**
 * The fully generated, pure (no database access) contents of one [PerformanceFixtureSpec] run.
 * [PerformanceFixtureGenerator.generate] builds this; [PerformanceFixtureSeeder] writes it into a
 * real database through real repositories. Kept as plain data so the generator itself is testable
 * without any database at all.
 */
internal data class PerformanceFixtureDataset(
    val categoryNames: List<String>,
    val manga: List<GeneratedManga>,
    val tagTastes: List<GeneratedTagTaste>,
    val tagAliases: List<GeneratedTagAlias>,
)

/**
 * One generated manga row plus everything that hangs off it. [categoryIndices] refer to positions
 * in [PerformanceFixtureDataset.categoryNames]. [crossSourceGroupId] is shared by every
 * [GeneratedManga] the generator considers "the same work" from a different source -- non-null on
 * 2+ entries exactly when they belong to the same confirmed cross-source group.
 */
internal data class GeneratedManga(
    val source: Long,
    val url: String,
    val title: String,
    val author: String?,
    val artist: String?,
    val description: String?,
    val genres: List<String>,
    val thumbnailUrl: String?,
    val favorite: Boolean,
    val categoryIndices: List<Int>,
    val chapters: List<GeneratedChapter>,
    /** Indices into [chapters] that should also get a reading-history row. */
    val historyChapterIndices: List<Int>,
    val rating: MangaRating?,
    val crossSourceGroupId: String?,
    val crossSourceGroupPrimary: Boolean,
    val hasLocalTracking: Boolean,
    /** True when [source] is a deliberately unresolvable id -- models an uninstalled/missing extension. */
    val sourceUnavailable: Boolean,
)

internal data class GeneratedChapter(
    val chapterNumber: Double,
    val name: String,
    val url: String,
    val read: Boolean,
    val bookmark: Boolean,
    val lastPageRead: Long,
    val sourceOrder: Long,
)

internal data class GeneratedTagTaste(val normalizedTag: String, val displayName: String, val preference: Int)

internal data class GeneratedTagAlias(val alias: String, val normalizedAlias: String, val groupKey: String, val displayName: String)
// KMK <--
