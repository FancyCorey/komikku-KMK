package exh.recs

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withPermit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

// KMK v0.8.6 -->
class GroupPreviewLoadCoordinatorTest {

    @Test
    fun `never exceeds the configured max concurrency`() = runBlocking {
        val coordinator = GroupPreviewLoadCoordinator(maxConcurrent = 4, timeoutMs = 5_000L)
        val concurrentNow = AtomicInteger(0)
        val observedPeak = AtomicInteger(0)

        val jobs = (1..20).map {
            async {
                coordinator.runBounded {
                    val now = concurrentNow.incrementAndGet()
                    observedPeak.updateAndGet { prev -> maxOf(prev, now) }
                    delay(20)
                    concurrentNow.decrementAndGet()
                }
            }
        }
        jobs.awaitAll()

        assertTrue(observedPeak.get() <= 4, "observed peak concurrency ${observedPeak.get()} exceeded the configured limit of 4")
        assertEquals(observedPeak.get(), coordinator.observedMaxConcurrency())
    }

    @Test
    fun `operation exceeding the timeout returns null instead of throwing`() = runBlocking {
        val coordinator = GroupPreviewLoadCoordinator(maxConcurrent = 4, timeoutMs = 30L)
        val result = coordinator.runBounded {
            delay(1_000L)
            "should never complete"
        }
        assertNull(result)
    }

    @Test
    fun `operation completing before the timeout returns its value`() = runBlocking {
        val coordinator = GroupPreviewLoadCoordinator(maxConcurrent = 4, timeoutMs = 5_000L)
        val result = coordinator.runBounded { "done" }
        assertEquals("done", result)
    }

    @Test
    fun `an exception in one operation propagates but does not leak the permit`() = runBlocking {
        val coordinator = GroupPreviewLoadCoordinator(maxConcurrent = 1, timeoutMs = 5_000L)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { coordinator.runBounded { throw IllegalStateException("boom") } }
        }

        // Permit must have been released in `finally` — a subsequent call must still be able to acquire it
        // (with maxConcurrent = 1, a leaked permit would hang this call forever; the test would time out).
        val result = coordinator.runBounded { "still works" }
        assertEquals("still works", result)
    }

    @Test
    fun `CancellationException propagates through runBounded`() = runBlocking {
        val coordinator = GroupPreviewLoadCoordinator(maxConcurrent = 4, timeoutMs = 5_000L)
        assertThrows(CancellationException::class.java) {
            runBlocking { coordinator.runBounded { throw CancellationException("cancelled") } }
        }
    }

    @Test
    fun `results complete progressively in finish order, not start order, when one operation is slow`() = runBlocking {
        val coordinator = GroupPreviewLoadCoordinator(maxConcurrent = 4, timeoutMs = 5_000L)
        val completionOrder = CopyOnWriteArrayList<String>()

        val jobs = listOf(
            async {
                coordinator.runBounded {
                    delay(200)
                    completionOrder.add("slow")
                }
            },
            async {
                coordinator.runBounded {
                    delay(10)
                    completionOrder.add("fast-1")
                }
            },
            async {
                coordinator.runBounded {
                    delay(20)
                    completionOrder.add("fast-2")
                }
            },
        )
        jobs.awaitAll()

        assertEquals(listOf("fast-1", "fast-2", "slow"), completionOrder.toList())
    }

    // KMK v0.8.6: reviewer-flagged correctness fix — nested enrichment must be bounded by ONE shared
    // total across every concurrently active GROUP_PREVIEW source, not per source. Simulates 4
    // sources (matching DEFAULT_MAX_CONCURRENT) each independently firing 10 "enrichment" requests
    // (matching CrossExtensionGenreSearchSource.MAX_ENRICH_PER_SOURCE) all acquiring the SAME shared
    // semaphore, and asserts the observed peak never exceeds DEFAULT_MAX_CONCURRENT_ENRICHMENT (8) —
    // proving the shared instance actually caps the cross-source total instead of each source having
    // its own independent budget (which would allow up to 4 x 10 = 40 concurrent requests).
    @Test
    fun `shared enrichment semaphore caps total concurrent detail requests across multiple simultaneously-active sources`() = runBlocking {
        val coordinator = GroupPreviewLoadCoordinator(maxConcurrent = 4, timeoutMs = 5_000L, maxConcurrentEnrichment = 8)
        val concurrentNow = AtomicInteger(0)
        val observedPeak = AtomicInteger(0)

        val sourceCount = 4
        val enrichmentsPerSource = 10 // mirrors CrossExtensionGenreSearchSource.MAX_ENRICH_PER_SOURCE

        val allJobs = (1..sourceCount).flatMap { _ ->
            (1..enrichmentsPerSource).map {
                async {
                    coordinator.sharedEnrichmentSemaphore.withPermit {
                        val now = concurrentNow.incrementAndGet()
                        observedPeak.updateAndGet { prev -> maxOf(prev, now) }
                        delay(15)
                        concurrentNow.decrementAndGet()
                    }
                }
            }
        }
        allJobs.awaitAll()

        assertTrue(
            observedPeak.get() <= 8,
            "observed peak concurrent enrichment requests ${observedPeak.get()} exceeded the shared budget of 8 across $sourceCount sources",
        )
    }
}

class GenerationGuardTest {

    @Test
    fun `first generation starts current`() {
        val guard = GenerationGuard()
        val gen = guard.next()
        assertTrue(guard.isCurrent(gen))
    }

    @Test
    fun `starting a new generation supersedes the previous one`() {
        val guard = GenerationGuard()
        val stale = guard.next()
        val fresh = guard.next()

        assertTrue(guard.isCurrent(fresh))
        assertTrue(!guard.isCurrent(stale))
    }

    @Test
    fun `a stale generation completing later must not be reported as current`() {
        val guard = GenerationGuard()
        val gen1 = guard.next()
        // Simulate: a new load starts (e.g. seed/language/budget change) before gen1's async work finishes.
        val gen2 = guard.next()

        // gen1's late completion must be rejected.
        assertTrue(!guard.isCurrent(gen1))
        // gen2's completion is accepted.
        assertTrue(guard.isCurrent(gen2))
    }
}
// KMK <--
