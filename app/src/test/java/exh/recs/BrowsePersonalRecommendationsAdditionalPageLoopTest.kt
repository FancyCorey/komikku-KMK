package exh.recs

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// KMK --> EC-04 2026-09-01: configurable discovery-effort policy -- call-site loop coverage.
/**
 * `BrowsePersonalRecommendationsScreenModel.searchSource()` can't be constructed in this pure-JVM
 * test module (no Robolectric -- it needs a real Android `Context`), matching the same constraint
 * already documented by [BrowsePersonalRecommendationsAdditionalPageCancellationTest]. This test
 * mirrors the exact while-loop control-flow shape now wired around `discoverAdditionalPage()` (see
 * that call site's own `// KMK --> EC-04 2026-09-01` comment), proving:
 * - [exh.recs.memory.DiscoveryEffortLevel.OFF] (0 additional pages) never probes at all.
 * - [exh.recs.memory.DiscoveryEffortLevel.STANDARD] (1 additional page) probes exactly once,
 *   reproducing the pre-existing single-probe behavior byte-for-byte.
 * - [exh.recs.memory.DiscoveryEffortLevel.EXTENDED] (3 additional pages) probes up to three times,
 *   accumulating results across every successfully probed page.
 * - A probe returning page `0` (discoverAdditionalPage's own signal for "nothing left to probe" --
 *   the real planner decided there is no due/eligible next page) stops the loop immediately, even
 *   with budget remaining, rather than looping the full configured count regardless of outcome.
 * - Progress is only reloaded between iterations when another iteration will actually run, never
 *   after the loop's own final iteration.
 */
class BrowsePersonalRecommendationsAdditionalPageLoopTest {

    /** Mirrors the exact while-loop shape wired around discoverAdditionalPage(). */
    private suspend fun runAdditionalPageLoop(
        additionalPagesPerRefresh: Int,
        candidateBudget: Int = Int.MAX_VALUE,
        probe: suspend (progressToken: Int) -> Pair<List<String>, Int>,
        reloadProgress: suspend () -> Int,
    ): Pair<List<String>, Int> {
        val accumulated = mutableListOf<String>()
        var remainingCandidateBudget = candidateBudget
        var lastPage = 0
        var currentProgressToken = 0
        var iteration = 0
        while (iteration < additionalPagesPerRefresh && remainingCandidateBudget > 0) {
            val (pageResults, probedPage) = probe(currentProgressToken)
            if (probedPage == 0) break
            lastPage = probedPage
            if (pageResults.isNotEmpty()) accumulated += pageResults
            remainingCandidateBudget -= pageResults.size
            iteration++
            if (iteration < additionalPagesPerRefresh) {
                currentProgressToken = reloadProgress()
            }
        }
        return accumulated.toList() to lastPage
    }

    @Test
    fun `OFF (0 additional pages) never probes`() = runTest {
        var probeCalls = 0
        val (results, lastPage) = runAdditionalPageLoop(
            additionalPagesPerRefresh = 0,
            probe = {
                probeCalls++
                listOf("should-not-happen") to 99
            },
            reloadProgress = { 0 },
        )
        assertEquals(0, probeCalls)
        assertEquals(emptyList<String>(), results)
        assertEquals(0, lastPage)
    }

    @Test
    fun `STANDARD (1 additional page) probes exactly once`() = runTest {
        var probeCalls = 0
        val (results, lastPage) = runAdditionalPageLoop(
            additionalPagesPerRefresh = 1,
            probe = {
                probeCalls++
                listOf("manga-page-2") to 2
            },
            reloadProgress = { error("must not reload after the only iteration") },
        )
        assertEquals(1, probeCalls)
        assertEquals(listOf("manga-page-2"), results)
        assertEquals(2, lastPage)
    }

    @Test
    fun `EXTENDED (3 additional pages) probes up to three times, accumulating every page`() = runTest {
        var probeCalls = 0
        var reloadCalls = 0
        val (results, lastPage) = runAdditionalPageLoop(
            additionalPagesPerRefresh = 3,
            probe = { token ->
                probeCalls++
                listOf("manga-from-token-$token") to (token + 2)
            },
            reloadProgress = {
                reloadCalls++
                reloadCalls
            },
        )
        assertEquals(3, probeCalls)
        assertEquals(2, reloadCalls, "reloads between iterations only, never after the final one")
        assertEquals(listOf("manga-from-token-0", "manga-from-token-1", "manga-from-token-2"), results)
        assertEquals(4, lastPage)
    }

    @Test
    fun `a probe returning page 0 stops the loop early, even with budget remaining`() = runTest {
        var probeCalls = 0
        val (results, lastPage) = runAdditionalPageLoop(
            additionalPagesPerRefresh = 3,
            probe = { token ->
                probeCalls++
                // Second call signals "nothing left to probe" (e.g. cap reached, or a not-yet-due
                // retry) exactly as the real discoverAdditionalPage() does by returning page 0.
                if (probeCalls == 2) emptyList<String>() to 0 else listOf("manga-$probeCalls") to (token + 2)
            },
            reloadProgress = { probeCalls },
        )
        assertEquals(2, probeCalls, "the loop must stop after the page-0 result, not continue to a third attempt")
        assertEquals(listOf("manga-1"), results, "only the first, genuinely successful page contributes results")
        assertEquals(2, lastPage, "lastPage reflects the last successfully probed page, not the stopping page 0")
    }

    @Test
    fun `an empty-results page (no error, just nothing new) still counts as a probed iteration`() = runTest {
        var probeCalls = 0
        val (results, lastPage) = runAdditionalPageLoop(
            additionalPagesPerRefresh = 2,
            probe = { token ->
                probeCalls++
                emptyList<String>() to (token + 2)
            },
            reloadProgress = { probeCalls },
        )
        assertEquals(2, probeCalls)
        assertEquals(emptyList<String>(), results)
        assertEquals(3, lastPage, "the second probed page number, even though it had no new results")
    }

    @Test
    fun `candidate budget stops planned probing when the aggregate bound is exhausted`() = runTest {
        var probeCalls = 0
        val (results, lastPage) = runAdditionalPageLoop(
            additionalPagesPerRefresh = 3,
            candidateBudget = 2,
            probe = {
                probeCalls++
                listOf("manga-$probeCalls", "manga-extra-$probeCalls") to (probeCalls + 1)
            },
            reloadProgress = { probeCalls },
        )
        assertEquals(1, probeCalls)
        assertEquals(listOf("manga-1", "manga-extra-1"), results)
        assertEquals(2, lastPage)
    }

    @Test
    fun `candidate budget allows later pages when earlier pages consume less than the bound`() = runTest {
        var probeCalls = 0
        val (results, lastPage) = runAdditionalPageLoop(
            additionalPagesPerRefresh = 3,
            candidateBudget = 3,
            probe = {
                probeCalls++
                listOf("manga-$probeCalls") to (probeCalls + 1)
            },
            reloadProgress = { probeCalls },
        )
        assertEquals(3, probeCalls)
        assertEquals(listOf("manga-1", "manga-2", "manga-3"), results)
        assertEquals(4, lastPage)
    }
}
// KMK <--
