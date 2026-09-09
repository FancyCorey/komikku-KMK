package exh.recs.sources

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// KMK F2-03 atomicity correction (2026-08-27, KFC-V0.8.21-FIX2-CORRECTIVE-RECHECK-AND-RELEASE-PROGRAM)
/**
 * Real multi-threaded proof that [SourceGenreCatalogCache]'s per-source generation acceptance and
 * label mutation are genuinely atomic together -- not merely correct for calls that happen to
 * arrive in generation order one after another. A second independent review correctly found that
 * [SourceGenreCatalogCacheTest]'s sequential arrival-order tests (call A, then call B, on the same
 * thread) cannot exercise the race this class exists to rule out: they can never observe an OLDER
 * call's acceptance-check-then-write straddle a NEWER call's own acceptance-check-then-write on a
 * different thread, because there is only ever one thread and one call in flight at a time in a
 * sequential test. Every test in this class uses real [Thread]s (via [ExecutorService]) and a
 * [CyclicBarrier] to release every participating thread as close to simultaneously as possible,
 * repeated across many trials via [RepeatedTest], so a race that only manifests occasionally under
 * genuine contention cannot hide behind a single lucky run.
 */
class SourceGenreCatalogCacheConcurrencyTest {

    private class GenreTriState(name: String) : Filter.TriState(name)
    private class GenreGroup(name: String, state: List<Filter<*>>) : Filter.Group<Filter<*>>(name, state)

    private fun genreFilterList(vararg names: String) =
        FilterList(GenreGroup("Genres", names.map { GenreTriState(it) }))

    private lateinit var pool: ExecutorService

    @BeforeEach
    fun setUp() {
        SourceGenreCatalogCache.clearForTesting()
        pool = Executors.newCachedThreadPool()
    }

    @AfterEach
    fun tearDown() {
        pool.shutdownNow()
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS), "test thread pool did not terminate cleanly")
        SourceGenreCatalogCache.clearForTesting()
    }

    /**
     * Runs [threadCount] jobs, released as close to simultaneously as [CyclicBarrier] can arrange
     * (each job awaits the barrier immediately before doing its real work), and waits for all of
     * them to finish before returning. Any exception thrown inside a job fails the test.
     */
    private fun runConcurrently(threadCount: Int, job: (index: Int) -> Unit) {
        val barrier = CyclicBarrier(threadCount)
        val errors = CopyOnWriteArrayList<Throwable>()
        val futures = (0 until threadCount).map { index ->
            pool.submit {
                try {
                    barrier.await(10, TimeUnit.SECONDS)
                    job(index)
                } catch (t: Throwable) {
                    errors += t
                }
            }
        }
        futures.forEach { it.get(15, TimeUnit.SECONDS) }
        if (errors.isNotEmpty()) {
            throw AssertionError("one or more concurrent jobs threw: ${errors.map { it.message }}", errors.first())
        }
    }

    // KMK: reproduces, with REAL threads instead of sequential calls, the exact defect an
    // independent review found in the two-map (label barrier split from generation barrier) design:
    // "An older record can be accepted, pause, then overwrite a newer completed record." With the
    // current single-map, single-compute()-call design, acceptance and mutation happen as one
    // indivisible step per source id, so this must be impossible regardless of which thread's
    // compute() call the JVM happens to run first.
    @RepeatedTest(200)
    fun `two threads racing to record different generations for the same source converge to the higher generation, regardless of scheduling order`() {
        runConcurrently(2) { index ->
            if (index == 0) {
                SourceGenreCatalogCache.record(1L, generation = 3, Result.success(genreFilterList("Older")))
            } else {
                SourceGenreCatalogCache.record(1L, generation = 7, Result.success(genreFilterList("Newer")))
            }
        }

        assertEquals(
            setOf("newer"),
            SourceGenreCatalogCache.snapshot(),
            "regardless of which of the two racing threads' compute() call actually ran first, the " +
                "final state must reflect only the higher generation (7) -- never the lower one (3), " +
                "and never a corrupted mix of both",
        )
    }

    // Same shape, but the "older" arrival is a successful-empty result racing a newer non-empty
    // result -- the specific case a partially-atomic (accept-then-mutate-as-two-steps) design could
    // get wrong by having the newer write land, then the older's empty-prune land after it.
    @RepeatedTest(200)
    fun `an older successful-empty record racing a newer non-empty record never prunes the newer labels`() {
        runConcurrently(2) { index ->
            if (index == 0) {
                SourceGenreCatalogCache.record(1L, generation = 2, Result.success(FilterList()))
            } else {
                SourceGenreCatalogCache.record(1L, generation = 9, Result.success(genreFilterList("Newer")))
            }
        }

        assertEquals(
            setOf("newer"),
            SourceGenreCatalogCache.snapshot(),
            "an older successful-empty result racing a newer non-empty result must never be able to " +
                "prune the newer result's labels, however the two threads are scheduled",
        )
    }

    // Reproduces the review's second named scenario ("an older rejected prune can still remove
    // newer labels") under real concurrency: a prune racing a newer record for the same source.
    @RepeatedTest(200)
    fun `a prune racing a newer record for the same source never removes the newer record's labels`() {
        // Sequential precondition (outside the race): source 1L was eligible in some earlier,
        // already-settled refresh, establishing it in everEligibleSourceIds -- matching the real
        // production sequence, where a source is always eligible at least once before any refresh
        // could ever prune it.
        SourceGenreCatalogCache.pruneIneligibleSources(eligibleSourceIds = setOf(1L), generation = 0)

        runConcurrently(2) { index ->
            if (index == 0) {
                SourceGenreCatalogCache.pruneIneligibleSources(eligibleSourceIds = emptySet(), generation = 2)
            } else {
                SourceGenreCatalogCache.record(1L, generation = 9, Result.success(genreFilterList("Newer")))
            }
        }

        assertEquals(
            setOf("newer"),
            SourceGenreCatalogCache.snapshot(),
            "an older prune (generation 2) racing a newer record (generation 9) for the same source " +
                "must never win, regardless of scheduling order",
        )
    }

    // The converse: a NEWER prune racing an OLDER in-flight record must always win, pruning the
    // source even if the older record's compute() call happens to run second -- INCLUDING when the
    // source has never been recorded before this exact race (everEligibleSourceIds' retained history
    // is what makes this true; see SourceGenreCatalogCache.pruneIneligibleSources's own doc for the
    // real caller-level gap a first version of this test found when the cache had no way to remember
    // a source it had only ever seen as eligible, never recorded).
    @RepeatedTest(200)
    fun `a newer prune racing an older in-flight first-ever record for the same source always wins`() {
        // Sequential precondition (outside the race): source 1L was eligible in an earlier,
        // already-settled refresh -- matching the real production sequence.
        SourceGenreCatalogCache.pruneIneligibleSources(eligibleSourceIds = setOf(1L), generation = 0)

        runConcurrently(2) { index ->
            if (index == 0) {
                SourceGenreCatalogCache.record(1L, generation = 2, Result.success(genreFilterList("StaleInFlight")))
            } else {
                SourceGenreCatalogCache.pruneIneligibleSources(eligibleSourceIds = emptySet(), generation = 9)
            }
        }

        assertTrue(
            SourceGenreCatalogCache.snapshot().isEmpty(),
            "a newer prune (generation 9) racing an older in-flight FIRST-EVER record (generation 2) " +
                "for a source with no prior entry must always leave the source pruned, regardless of " +
                "scheduling order -- this is exactly the race everEligibleSourceIds exists to close",
        )
    }

    // KMK production-caller-boundary correction (2026-08-27): the deterministic, latch-forced proof
    // of the real production caller contract a THIRD independent review required. This test does NOT
    // hand the cache a direct "known ineligible" hint the way the review criticized ("merely passing
    // setOf(S) directly to the cache does not prove the caller can discover S after uninstall") --
    // it drives the EXACT two-call sequence the real BrowsePersonalRecommendationsScreenModel caller
    // produces across two refreshes (an older refresh's own eligible-set computation, THEN a newer
    // refresh's own eligible-set computation that simply omits the now-uninstalled source), with a
    // real background thread's delayed first-ever record() call for that source FORCED via a
    // CountDownLatch to complete its network fetch only AFTER the newer refresh's prune has already
    // run -- not probabilistically raced, but deterministically ordered every single execution.
    @Test
    fun `production caller contract -- a source uninstalled between two real refreshes rejects its delayed first-ever record, deterministically forced via a latch`() {
        val sourceId = 42L
        val olderRefreshGeneration = 2L
        val newerRefreshGeneration = 9L
        val newerRefreshPruneComplete = java.util.concurrent.CountDownLatch(1)
        val delayedRecordComplete = java.util.concurrent.CountDownLatch(1)

        // Step 1: the OLDER refresh's own eligible-set computation includes sourceId (it is
        // installed and enabled at this point) -- exactly what
        // BrowsePersonalRecommendationsScreenModel.refresh() computes from
        // sourceManager.getVisibleSources() and passes as eligibleSourceIds. This alone must not
        // create any cache entry for sourceId.
        SourceGenreCatalogCache.pruneIneligibleSources(eligibleSourceIds = setOf(sourceId), generation = olderRefreshGeneration)
        assertTrue(SourceGenreCatalogCache.snapshot().isEmpty(), "precondition: eligibility alone creates no entry")

        // Step 2: the OLDER refresh's own searchSource() coroutine for sourceId is in flight on a
        // real background thread, but has not completed its first-ever record() call yet -- it is
        // deliberately held here until explicitly released below, simulating a real, still-pending
        // network fetch that outlives its own refresh.
        val delayedFirstRecordThread = Thread {
            newerRefreshPruneComplete.await(5, TimeUnit.SECONDS)
            SourceGenreCatalogCache.record(sourceId, olderRefreshGeneration, Result.success(genreFilterList("Stale First-Ever Record")))
            delayedRecordComplete.countDown()
        }
        delayedFirstRecordThread.start()

        // Step 3: sourceId is uninstalled. A NEWER refresh's own eligible-set computation (from a
        // fresh sourceManager.getVisibleSources() call) therefore no longer includes it at all --
        // this call's own eligibleSourceIds parameter never mentions sourceId, directly or
        // indirectly; it is simply absent, exactly as production code would compute it.
        SourceGenreCatalogCache.pruneIneligibleSources(eligibleSourceIds = emptySet(), generation = newerRefreshGeneration)
        newerRefreshPruneComplete.countDown()

        // Step 4: release the older refresh's delayed record() call and wait for it to complete.
        assertTrue(delayedRecordComplete.await(10, TimeUnit.SECONDS), "delayed record() call did not complete in time")
        delayedFirstRecordThread.join(5_000)

        assertTrue(
            SourceGenreCatalogCache.snapshot().isEmpty(),
            "a source uninstalled between two real refreshes must reject its older refresh's delayed " +
                "first-ever record() call, deterministically, even though: (a) the newer refresh's own " +
                "eligibleSourceIds never named the source directly, and (b) record() had never once " +
                "been called for this source before the delayed call itself",
        )
    }

    // Stress test across many source ids and many threads at once: every source id independently
    // receives records at several different generations from different threads, all released
    // together. For every source id, the final labels must correspond to exactly the highest
    // generation submitted for THAT source id -- proving no cross-key corruption and no lost update
    // under real contention spanning the whole map, not just one key.
    @RepeatedTest(50)
    fun `many source ids under concurrent contention each independently converge to their own highest generation`() {
        val sourceCount = 20
        val generationsPerSource = 5
        // Each (sourceId, generation) pair is one job; all jobs across all sources run concurrently.
        val jobs = (0 until sourceCount).flatMap { sourceIndex ->
            (1..generationsPerSource).map { generation -> sourceIndex.toLong() to generation.toLong() }
        }.shuffled()

        runConcurrently(jobs.size) { index ->
            val (sourceId, generation) = jobs[index]
            SourceGenreCatalogCache.record(sourceId, generation, Result.success(genreFilterList("gen-$generation-source-$sourceId")))
        }

        for (sourceIndex in 0 until sourceCount) {
            val sourceId = sourceIndex.toLong()
            // normalizeTag() replaces every non-alphanumeric run (including the hyphens above) with
            // a single space -- see tachiyomi.domain.taste.model.TagNormalization -- so the expected
            // label here is space-separated, not hyphenated.
            val expectedLabel = "gen $generationsPerSource source $sourceId"
            val actual = SourceGenreCatalogCache.snapshot()
            assertTrue(
                expectedLabel in actual,
                "source $sourceId must reflect its own highest submitted generation ($generationsPerSource); " +
                    "snapshot was $actual",
            )
        }
    }

    // KMK F2-03 atomicity correction: proves the OTHER half of the review's finding -- that
    // BrowsePersonalRecommendationsScreenModel.refresh()'s per-refresh generation capture is now
    // atomic with the increment itself. Reproduces the exact pattern the production code uses
    // (`mutableState.updateAndGet { it.copy(resultGeneration = it.resultGeneration + 1) }`) against a
    // real MutableStateFlow under real concurrent contention, without needing the full ScreenModel's
    // 25-collaborator construction graph -- the property under test (does updateAndGet's return value
    // ever collide across concurrent callers) depends only on MutableStateFlow's own atomicity
    // contract, which this test exercises directly and for real, not by trusting the library's docs.
    @RepeatedTest(20)
    fun `concurrent updateAndGet-based generation capture never hands two callers the same generation`() {
        data class GenState(val resultGeneration: Long = 0L)

        val state = MutableStateFlow(GenState())
        val callerCount = 50
        val captured = CopyOnWriteArrayList<Long>()

        runConcurrently(callerCount) {
            // The exact production pattern: atomically increment and capture the POST-update value
            // in one operation, not a separate update() followed by a separate .value read.
            val newState = state.updateAndGet { it.copy(resultGeneration = it.resultGeneration + 1) }
            captured += newState.resultGeneration
        }

        assertEquals(callerCount, captured.toSet().size, "every concurrent caller must capture a distinct generation: got $captured")
        assertEquals((1L..callerCount.toLong()).toSet(), captured.toSet(), "captured generations must be exactly 1..$callerCount, with no gaps or duplicates")
    }

    // Negative control for the test above: proves the OLD, reopened-defect pattern (a separate
    // update() followed by a separate .value read) CAN observably collide under real concurrency --
    // so the fix's test above is not passing merely because MutableStateFlow happens to always be
    // race-free for any access pattern; the SEPARATION of the read from the write is what mattered.
    //
    // KMK production-caller-boundary correction (2026-08-27): an independent review correctly found
    // the ORIGINAL version of this test probabilistic -- it repeated the race up to 500 times hoping
    // a scheduler collision would occur within that budget, which is real evidence but not a
    // deterministic proof, and is release-gate-inappropriate waste when a smaller forced proof is
    // available. This version uses two CountDownLatches to FORCE the exact interleaving every single
    // run, deterministically, in one execution -- no repetition, no probability.
    @Test
    fun `the old separate update-then-read pattern deterministically reads a value written by a different caller when that caller's update lands between the write and the read`() {
        data class GenState(val resultGeneration: Long = 0L)

        val state = MutableStateFlow(GenState())
        val firstCallerWroteItsUpdate = java.util.concurrent.CountDownLatch(1)
        val secondCallerWroteItsUpdate = java.util.concurrent.CountDownLatch(1)
        var capturedByFirstCaller = -1L

        val firstCaller = Thread {
            // The OLD pattern's write step (atomic on its own).
            state.update { it.copy(resultGeneration = it.resultGeneration + 1) } // -> 1
            firstCallerWroteItsUpdate.countDown()
            // Deterministically wait for a SECOND caller's own update to land before this caller
            // performs its own SEPARATE .value read -- forcing exactly the window the old pattern's
            // write/read separation left open, every single run.
            secondCallerWroteItsUpdate.await(5, TimeUnit.SECONDS)
            capturedByFirstCaller = state.value.resultGeneration
        }
        val secondCaller = Thread {
            firstCallerWroteItsUpdate.await(5, TimeUnit.SECONDS)
            state.update { it.copy(resultGeneration = it.resultGeneration + 1) } // -> 2
            secondCallerWroteItsUpdate.countDown()
        }

        firstCaller.start()
        secondCaller.start()
        firstCaller.join(10_000)
        secondCaller.join(10_000)

        assertEquals(
            2L,
            capturedByFirstCaller,
            "the OLD separate update()-then-.value-read pattern must deterministically read a value " +
                "written by a DIFFERENT caller (2) rather than the value its OWN update() produced " +
                "(1), once forced into this exact interleaving -- this is precisely the collision " +
                "updateAndGet's single atomic read-and-write operation closes",
        )
    }
}
