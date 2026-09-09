package exh.perf

import tachiyomi.domain.taste.model.MangaRating
import kotlin.math.roundToInt
import kotlin.random.Random

// KMK HR-2026-08-26-CONSTRAINED-DEVICE-PERFORMANCE-PROGRAM (C4) -->
/**
 * Pure, deterministic generator for [PerformanceFixtureDataset] -- builds the entire realistic
 * dataset described by a [PerformanceFixtureSpec] as plain in-memory data, with no database or
 * repository dependency at all. This is what makes the fixture genuinely reusable and fast to
 * validate: every distribution/edge-case guarantee below is proven by
 * `PerformanceFixtureGeneratorTest` on the host, in milliseconds, before any of it ever touches a
 * real (or in-memory) database via [PerformanceFixtureSeeder].
 *
 * Two kinds of randomness are deliberately kept separate: [ChapterProfile] assignment (see
 * [chapterProfileFor]) is INDEX-deterministic, guaranteeing every required chapter shape exists
 * regardless of [PerformanceFixtureSpec.seed] or scale; everything else (which manga get which
 * rating/favorite/history/cross-source-group/local-tracking/malformed-metadata treatment, genre
 * picks, title variety) is seed-deterministic via a single sequential [Random] instance, so the
 * exact same spec always reproduces the exact same dataset byte-for-byte.
 */
internal object PerformanceFixtureGenerator {

    private val GENRES = listOf(
        "Action", "Romance", "Comedy", "Drama", "Fantasy", "Horror", "Isekai",
        "Martial Arts", "Mecha", "Mystery", "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Thriller",
    )

    /** Deliberately large, unlikely-to-collide ids -- models an uninstalled/missing extension. */
    private val UNAVAILABLE_SOURCE_IDS = listOf(
        920_000_000_000_000_001L,
        920_000_000_000_000_002L,
        920_000_000_000_000_003L,
    )

    /** A handful of "installed-like" source ids to spread manga across multiple source lanes. */
    private val AVAILABLE_SOURCE_IDS = listOf(
        930_000_000_000_000_001L,
        930_000_000_000_000_002L,
        930_000_000_000_000_003L,
        930_000_000_000_000_004L,
        930_000_000_000_000_005L,
    )

    fun generate(spec: PerformanceFixtureSpec): PerformanceFixtureDataset {
        val random = Random(spec.seed)
        val categoryNames = (1..spec.categoryCount).map { "Fixture Category $it" }

        val unavailableIndices = pickIndices(spec.totalManga, spec.unavailableSourceFraction, random)
        val favoriteIndices = pickIndices(spec.totalManga, spec.favoriteFraction, random)
        val malformedIndices = pickIndices(spec.totalManga, spec.malformedMetadataFraction, random)
        val localTrackingIndices = pickIndices(favoriteIndices.size, spec.localTrackingFraction, random)
            .map { favoriteIndices.toList()[it] }
            .toSet()
        val historyIndices = pickIndices(favoriteIndices.size, spec.historyFraction, random)
            .map { favoriteIndices.toList()[it] }
            .toSet()

        val ratingByIndex = assignRatings(spec, random)
        val groupIdByIndex = assignCrossSourceGroups(spec, random)

        val manga = (0 until spec.totalManga).map { index ->
            buildManga(
                spec = spec,
                index = index,
                random = random,
                categoryCount = categoryNames.size,
                sourceUnavailable = index in unavailableIndices,
                favorite = index in favoriteIndices,
                malformed = index in malformedIndices,
                hasLocalTracking = index in localTrackingIndices,
                wantsHistory = index in historyIndices,
                rating = ratingByIndex[index],
                groupId = groupIdByIndex[index]?.first,
                groupPrimary = groupIdByIndex[index]?.second == true,
            )
        }

        val tagTastes = GENRES.take(6).mapIndexed { i, g ->
            GeneratedTagTaste(
                normalizedTag = g.lowercase(),
                displayName = g,
                preference = if (i % 3 == 0) {
                    1
                } else if (i % 3 == 1) {
                    -1
                } else {
                    -2
                },
            )
        }
        val tagAliases = listOf(
            GeneratedTagAlias("Shonen Ai", "shonen ai", "boys love", "Boys Love"),
            GeneratedTagAlias("Isekai Fantasy", "isekai fantasy", "isekai", "Isekai"),
        )

        return PerformanceFixtureDataset(categoryNames, manga, tagTastes, tagAliases)
    }

    private fun buildManga(
        spec: PerformanceFixtureSpec,
        index: Int,
        random: Random,
        categoryCount: Int,
        sourceUnavailable: Boolean,
        favorite: Boolean,
        malformed: Boolean,
        hasLocalTracking: Boolean,
        wantsHistory: Boolean,
        rating: MangaRating?,
        groupId: String?,
        groupPrimary: Boolean,
    ): GeneratedManga {
        val source = if (sourceUnavailable) {
            UNAVAILABLE_SOURCE_IDS[index % UNAVAILABLE_SOURCE_IDS.size]
        } else {
            AVAILABLE_SOURCE_IDS[index % AVAILABLE_SOURCE_IDS.size]
        }
        val baseTitle = groupId?.let { "Fixture Shared Work $it" } ?: "Fixture Manga $index"
        val title = if (malformed && random.nextBoolean()) {
            ""
        } else if (groupId != null) {
            "$baseTitle (${source % 100})"
        } else {
            baseTitle
        }
        val description = if (malformed) "x".repeat(20_000) else "Deterministic fixture description for manga $index."
        val genres = (0 until 2 + random.nextInt(3)).map { GENRES[(index + it) % GENRES.size] }.distinct()

        val profile = spec.chapterProfileFor(index)
        val chapters = buildChapters(profile, index, random)
        val readCount = if (chapters.isEmpty()) 0 else (chapters.size * (0.2 + random.nextDouble() * 0.6)).roundToInt().coerceIn(0, chapters.size)
        val chaptersWithState = chapters.mapIndexed { i, ch ->
            val read = i < readCount
            val isCurrent = i == readCount && chapters.isNotEmpty()
            ch.copy(
                read = read,
                bookmark = !read && random.nextInt(10) == 0,
                lastPageRead = if (isCurrent) (1 + random.nextInt(20)).toLong() else 0L,
            )
        }
        val historyChapterIndices = if (wantsHistory && chaptersWithState.isNotEmpty()) {
            listOf((readCount - 1).coerceIn(0, chaptersWithState.size - 1))
        } else {
            emptyList()
        }

        val categoryIndices = if (favorite && categoryCount > 0) {
            listOf(index % categoryCount)
        } else {
            emptyList()
        }

        return GeneratedManga(
            source = source,
            url = "/fixture/manga/$index",
            title = title,
            author = if (malformed) null else "Fixture Author ${index % 37}",
            artist = if (malformed) null else "Fixture Artist ${index % 23}",
            description = description,
            genres = genres,
            thumbnailUrl = "https://example.invalid/fixture-cover/$index.jpg",
            favorite = favorite,
            categoryIndices = categoryIndices,
            chapters = chaptersWithState,
            historyChapterIndices = historyChapterIndices,
            rating = rating,
            crossSourceGroupId = groupId,
            crossSourceGroupPrimary = groupPrimary,
            // KMK: LocalTrackerRepository.upsertWork requires a non-blank title (a real, correct
            // invariant, not a defect to route around) -- a malformed manga with a deliberately
            // blank title must never also be assigned local tracking. Rare in practice (both
            // conditions must coincide) but confirmed reachable at REALISTIC (~1,000-manga) scale by
            // this fixture's own sanity test, not merely theoretical.
            hasLocalTracking = hasLocalTracking && favorite && title.isNotBlank(),
            sourceUnavailable = sourceUnavailable,
        )
    }

    private fun buildChapters(profile: ChapterProfile, index: Int, random: Random): List<GeneratedChapter> {
        fun chapter(number: Double, order: Long, name: String = "Chapter ${formatNumber(number)}") =
            GeneratedChapter(
                chapterNumber = number,
                name = name,
                url = "/fixture/manga/$index/ch/${formatNumber(number)}-$order",
                read = false,
                bookmark = false,
                lastPageRead = 0L,
                sourceOrder = order,
            )

        val includeSpecial = index % 2 == 0 && profile != ChapterProfile.ZERO
        val body: List<GeneratedChapter> = when (profile) {
            ChapterProfile.ZERO -> emptyList()
            ChapterProfile.FEW -> (1..(1 + random.nextInt(5))).map { chapter(it.toDouble(), it.toLong()) }
            ChapterProfile.NORMAL -> {
                val count = 6 + random.nextInt(55)
                (1..count).map { chapter(it.toDouble(), it.toLong()) }
            }
            ChapterProfile.NORMAL_WITH_GAPS -> {
                val count = 6 + random.nextInt(55)
                (1..count).filter { it % 7 != 0 }.map { chapter(it.toDouble(), it.toLong()) }
            }
            ChapterProfile.NORMAL_WITH_DUPLICATES -> {
                val count = 6 + random.nextInt(55)
                val base = (1..count).map { chapter(it.toDouble(), it.toLong()) }
                val duplicateAt = (count / 2).coerceAtLeast(1)
                base + chapter(duplicateAt.toDouble(), (count + 1).toLong(), name = "Chapter $duplicateAt (Alt Scanlator)")
            }
            ChapterProfile.NORMAL_WITH_DECIMALS -> {
                val count = 6 + random.nextInt(55)
                (1..count).flatMap { n ->
                    if (n % 5 == 0) {
                        listOf(chapter(n.toDouble(), (n * 2).toLong()), chapter(n + 0.5, (n * 2 + 1).toLong()))
                    } else {
                        listOf(chapter(n.toDouble(), (n * 2).toLong()))
                    }
                }
            }
            ChapterProfile.LONG_RUNNING -> {
                val count = 500 + random.nextInt(700)
                (1..count).map { chapter(it.toDouble(), it.toLong()) }
            }
        }
        return if (includeSpecial) {
            listOf(chapter(-1.0, 0L, name = "Prologue")) + body
        } else {
            body
        }
    }

    private fun formatNumber(number: Double): String =
        if (number == number.toLong().toDouble()) number.toLong().toString() else number.toString()

    /** Deterministically picks `round(total * fraction)` distinct indices in `0 until total`. */
    private fun pickIndices(total: Int, fraction: Double, random: Random): Set<Int> {
        if (total <= 0 || fraction <= 0.0) return emptySet()
        val count = (total * fraction).roundToInt().coerceIn(0, total)
        return (0 until total).shuffled(random).take(count).toSet()
    }

    private fun assignRatings(spec: PerformanceFixtureSpec, random: Random): Map<Int, MangaRating> {
        val remaining = (0 until spec.totalManga).shuffled(random).toMutableList()
        val result = mutableMapOf<Int, MangaRating>()
        spec.ratingFractions.forEach { (rating, fraction) ->
            val count = (spec.totalManga * fraction).roundToInt().coerceIn(0, remaining.size)
            repeat(count) {
                if (remaining.isNotEmpty()) result[remaining.removeAt(0)] = rating
            }
        }
        return result
    }

    /** Groups manga into pairs sharing a [GeneratedManga.crossSourceGroupId]; first member is primary. */
    private fun assignCrossSourceGroups(spec: PerformanceFixtureSpec, random: Random): Map<Int, Pair<String, Boolean>> {
        val participantCount = (spec.totalManga * spec.crossSourceGroupFraction).roundToInt().let { it - (it % 2) }
        if (participantCount < 2) return emptyMap()
        val participants = (0 until spec.totalManga).shuffled(random).take(participantCount)
        val result = mutableMapOf<Int, Pair<String, Boolean>>()
        participants.chunked(2).forEachIndexed { groupIndex, pair ->
            if (pair.size == 2) {
                val groupId = "perf-group-$groupIndex"
                result[pair[0]] = groupId to true
                result[pair[1]] = groupId to false
            }
        }
        return result
    }
}
// KMK <--
