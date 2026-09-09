package exh.recs.bestversion

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class BestVersionPreviewLoadCoordinatorTest {

    @Test
    fun `never exceeds the configured preview concurrency`() = runBlocking {
        val coordinator = BestVersionPreviewLoadCoordinator(maxConcurrent = 2)
        val active = AtomicInteger(0)
        val peak = AtomicInteger(0)

        (1..8).map {
            async {
                coordinator.runBounded {
                    val now = active.incrementAndGet()
                    peak.updateAndGet { previous -> maxOf(previous, now) }
                    delay(10)
                    active.decrementAndGet()
                }
            }
        }.awaitAll()

        assertTrue(peak.get() <= 2, "preview peak ${peak.get()} exceeded the configured limit")
    }
}
