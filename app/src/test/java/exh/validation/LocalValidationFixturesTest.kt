package exh.validation

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalValidationFixturesTest {
    @Test
    fun `state fixture is populated and resettable`() {
        val fixture = LocalAppStateFixture(LocalAppStateFixtures.baseline())

        assertEquals(setOf(103L), fixture.snapshot().notInterested)
        fixture.replace(LocalAppStateSnapshot(ratings = mapOf(999L to 1), scheduleEnabled = true))
        fixture.reset()

        assertEquals(LocalAppStateFixtures.baseline(), fixture.snapshot())
    }

    @Test
    fun `success server exposes every source surface`() = runTest {
        LocalSourceFixtureServer().start().use { server ->
            val client = LocalSourceFixtureClient(server)

            LocalSourceEndpoint.entries.forEach { endpoint ->
                val body = client.get(endpoint)
                assertTrue(body.contains(endpoint.name.lowercase()))
            }
        }
    }

    @Test
    fun `http failure is explicit and does not look like an empty result`() = runTest {
        LocalSourceFixtureServer(LocalSourceFixtureMode.HTTP_FAILURE).start().use { server ->
            val failure = runCatching {
                LocalSourceFixtureClient(server).get(LocalSourceEndpoint.LATEST)
            }.exceptionOrNull()

            assertEquals("fixture-http-status-503", failure?.message)
        }
    }

    @Test
    fun `malformed response remains available for parser tests`() = runTest {
        LocalSourceFixtureServer(LocalSourceFixtureMode.MALFORMED_RESPONSE).start().use { server ->
            assertEquals("fixture-not-json", LocalSourceFixtureClient(server).get(LocalSourceEndpoint.PREVIEW))
        }
    }

    @Test
    fun `timeout is cancellable by the caller`() = runTest {
        LocalSourceFixtureServer(
            mode = LocalSourceFixtureMode.TIMEOUT,
            timeoutDelayMillis = 250L,
        ).start().use { server ->
            val failure = runCatching {
                withTimeout(25L) {
                    LocalSourceFixtureClient(server).get(LocalSourceEndpoint.CHAPTERS, timeoutMillis = 2_000)
                }
            }.exceptionOrNull()

            assertTrue(failure is kotlinx.coroutines.TimeoutCancellationException)
        }
    }

    @Test
    fun `cancellation does not require a successful source response`() = runTest {
        LocalSourceFixtureServer(
            mode = LocalSourceFixtureMode.TIMEOUT,
            timeoutDelayMillis = 250L,
        ).start().use { server ->
            val job: Job = launch {
                LocalSourceFixtureClient(server).get(LocalSourceEndpoint.DETAIL, timeoutMillis = 2_000)
            }
            job.cancel()
            job.join()

            assertTrue(job.isCancelled)
        }
    }
}
