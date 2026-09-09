package exh.perf

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.MangaRating

// KMK C4 (HR-2026-08-26-CONSTRAINED-DEVICE-PERFORMANCE-PROGRAM)
class PerformanceFixtureGeneratorTest {

    @Test
    fun `generates exactly totalManga rows`() {
        val dataset = PerformanceFixtureGenerator.generate(PerformanceFixtureSpec.SMALL)
        assertEquals(PerformanceFixtureSpec.SMALL.totalManga, dataset.manga.size)
    }

    @Test
    fun `the same spec (same seed) produces a byte-for-byte identical dataset`() {
        val first = PerformanceFixtureGenerator.generate(PerformanceFixtureSpec.SMALL)
        val second = PerformanceFixtureGenerator.generate(PerformanceFixtureSpec.SMALL)

        assertEquals(first, second, "generation must be fully deterministic for a fixed seed")
    }

    @Test
    fun `a different seed produces a different dataset`() {
        val a = PerformanceFixtureGenerator.generate(PerformanceFixtureSpec.SMALL)
        val b = PerformanceFixtureGenerator.generate(PerformanceFixtureSpec.SMALL.copy(seed = PerformanceFixtureSpec.SMALL.seed + 1))

        assertTrue(a != b, "a different seed should not coincidentally reproduce the same dataset")
    }

    @Test
    fun `every required chapter-shape edge case is present regardless of scale`() {
        val dataset = PerformanceFixtureGenerator.generate(PerformanceFixtureSpec.SMALL)

        assertTrue(dataset.manga.any { it.chapters.isEmpty() }, "zero-chapter manga must exist")
        assertTrue(
            dataset.manga.any { m -> m.chapters.map { it.chapterNumber }.toSet().size < m.chapters.size },
            "a duplicated chapter number must exist somewhere",
        )
        assertTrue(
            dataset.manga.any { m -> m.chapters.any { it.chapterNumber != it.chapterNumber.toLong().toDouble() } },
            "a decimal (sub-)chapter number must exist somewhere",
        )
        assertTrue(
            dataset.manga.any { m -> m.chapters.any { it.chapterNumber < 0 } },
            "a special/prologue sentinel chapter (negative number) must exist somewhere",
        )
        assertTrue(
            dataset.manga.any { m -> m.chapters.size >= 500 },
            "at least one long-running manga (500+ chapters) must exist",
        )
        assertTrue(
            dataset.manga.any { m ->
                val numbers = m.chapters.map { it.chapterNumber.toLong() }.filter { it > 0 }.sorted()
                numbers.zipWithNext().any { (a, b) -> b - a > 1 }
            },
            "a gap in chapter numbering must exist somewhere",
        )
    }

    @Test
    fun `longRunningCount long-running manga are guaranteed present even at SMALL scale`() {
        val dataset = PerformanceFixtureGenerator.generate(PerformanceFixtureSpec.SMALL)

        val longRunning = dataset.manga.count { it.chapters.size >= 500 }
        assertEquals(PerformanceFixtureSpec.SMALL.longRunningCount, longRunning)
    }

    @Test
    fun `rating fractions are honored and every rated manga gets exactly one rating`() {
        val spec = PerformanceFixtureSpec.SMALL
        val dataset = PerformanceFixtureGenerator.generate(spec)

        val byRating = dataset.manga.mapNotNull { it.rating }.groupingBy { it }.eachCount()
        spec.ratingFractions.forEach { (rating, fraction) ->
            val expected = (spec.totalManga * fraction).let { Math.round(it).toInt() }
            assertEquals(expected, byRating[rating] ?: 0, "rating $rating count mismatch")
        }
        // No manga has two ratings -- `rating` is a single nullable field, this is structurally guaranteed,
        // but assert the total also matches the sum of fractions as a cross-check.
        assertEquals(byRating.values.sum(), dataset.manga.count { it.rating != null })
    }

    @Test
    fun `cross-source groups always have exactly 2 members with exactly one primary`() {
        val dataset = PerformanceFixtureGenerator.generate(PerformanceFixtureSpec.SMALL)

        val byGroup = dataset.manga.filter { it.crossSourceGroupId != null }.groupBy { it.crossSourceGroupId }
        assertTrue(byGroup.isNotEmpty(), "at least one cross-source group must be generated")
        byGroup.forEach { (groupId, members) ->
            assertEquals(2, members.size, "group $groupId must have exactly 2 members")
            assertEquals(1, members.count { it.crossSourceGroupPrimary }, "group $groupId must have exactly one primary")
            assertEquals(2, members.map { it.source }.distinct().size, "a cross-source group's members must come from different sources")
        }
    }

    @Test
    fun `local tracking is only ever assigned to favorite manga`() {
        val dataset = PerformanceFixtureGenerator.generate(PerformanceFixtureSpec.SMALL)

        assertTrue(dataset.manga.any { it.hasLocalTracking }, "at least one manga should have local tracking, to exercise that path")
        assertTrue(dataset.manga.filter { it.hasLocalTracking }.all { it.favorite })
    }

    // KMK: found via the REALISTIC-scale seeder sanity check -- LocalTrackerRepository.upsertWork
    // requires a non-blank title, and a malformed manga can have a blank title. Rare enough to miss
    // at SMALL scale but confirmed reachable at 1,000-manga scale; regression-guarded here at every
    // scale so it can never resurface silently.
    @Test
    fun `local tracking is never assigned to a manga with a blank (malformed) title, at any scale`() {
        listOf(PerformanceFixtureSpec.SMALL, PerformanceFixtureSpec.REALISTIC).forEach { spec ->
            val dataset = PerformanceFixtureGenerator.generate(spec)
            assertTrue(
                dataset.manga.filter { it.hasLocalTracking }.all { it.title.isNotBlank() },
                "no manga with hasLocalTracking=true may have a blank title (spec totalManga=${spec.totalManga})",
            )
        }
    }

    @Test
    fun `history is only ever assigned to a manga that has at least one chapter`() {
        val dataset = PerformanceFixtureGenerator.generate(PerformanceFixtureSpec.SMALL)

        dataset.manga.filter { it.historyChapterIndices.isNotEmpty() }.forEach { manga ->
            assertTrue(manga.chapters.isNotEmpty(), "a manga with history entries must have chapters")
            manga.historyChapterIndices.forEach { idx ->
                assertTrue(idx in manga.chapters.indices, "history chapter index must be a valid index into this manga's own chapters")
            }
        }
    }

    @Test
    fun `unavailable-source manga are assigned one of the deliberately unresolvable source ids`() {
        val dataset = PerformanceFixtureGenerator.generate(PerformanceFixtureSpec.SMALL)

        val unavailable = dataset.manga.filter { it.sourceUnavailable }
        assertTrue(unavailable.isNotEmpty(), "at least one unavailable-source manga must be generated")
        assertTrue(unavailable.all { it.source >= 920_000_000_000_000_000L && it.source < 930_000_000_000_000_000L })
    }

    @Test
    fun `malformed metadata manga never crash generation and stay structurally valid`() {
        val dataset = PerformanceFixtureGenerator.generate(PerformanceFixtureSpec.SMALL)

        // Every manga (malformed or not) must still have a resolvable, non-null url/source -- "malformed"
        // means degraded/oversized display metadata, never a broken identity.
        dataset.manga.forEach { manga ->
            assertTrue(manga.url.isNotBlank())
            assertTrue(manga.source != 0L)
        }
        assertTrue(
            dataset.manga.any { (it.description?.length ?: 0) > 1_000 },
            "at least one manga should carry deliberately oversized metadata",
        )
    }

    @Test
    fun `every manga has a distinct source+url identity`() {
        val dataset = PerformanceFixtureGenerator.generate(PerformanceFixtureSpec.SMALL)

        val identities = dataset.manga.map { it.source to it.url }
        assertEquals(identities.size, identities.toSet().size, "no two generated manga may share the same (source, url) identity")
    }

    @Test
    fun `category indices always resolve within the generated category list`() {
        val dataset = PerformanceFixtureGenerator.generate(PerformanceFixtureSpec.SMALL)

        dataset.manga.forEach { manga ->
            manga.categoryIndices.forEach { idx ->
                assertTrue(idx in dataset.categoryNames.indices, "category index $idx must be valid")
            }
        }
    }

    @Test
    fun `REALISTIC preset targets approximately 1,000 manga`() {
        assertEquals(1_000, PerformanceFixtureSpec.REALISTIC.totalManga)
    }

    @Test
    fun `named profiles are complete deterministic and uniquely seeded`() {
        val expected = setOf(
            "empty",
            "realistic-1k",
            "source-populated",
            "large-rated-groups",
            "best-version-chapters",
            "archive-reader",
            "failure-offline",
            "process-recreation",
        )
        assertEquals(expected, PerformanceFixtureSpec.PROFILES.keys)
        assertEquals(expected.size, PerformanceFixtureSpec.PROFILES.values.map { it.seed }.toSet().size)
        PerformanceFixtureSpec.PROFILES.forEach { (_, spec) ->
            val first = PerformanceFixtureGenerator.generate(spec)
            assertEquals(spec.totalManga, first.manga.size)
            assertEquals(first, PerformanceFixtureGenerator.generate(spec))
        }
    }

    @Test
    fun `empty profile does not accidentally carry populated state`() {
        val dataset = PerformanceFixtureGenerator.generate(PerformanceFixtureSpec.profile("empty"))
        assertTrue(dataset.manga.single().chapters.isEmpty())
        assertTrue(dataset.manga.single().rating == null)
        assertTrue(dataset.categoryNames.isEmpty())
    }

    @Test
    fun `an invalid spec (fraction out of range) fails fast at construction, not deep inside generation`() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            PerformanceFixtureSpec.SMALL.copy(favoriteFraction = 1.5)
        }
    }
}
