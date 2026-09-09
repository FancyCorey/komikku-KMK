package eu.kanade.tachiyomi.ui.reader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SerializedReaderRouteOwnerTest {

    @Test
    fun `candidate commits once and ownership is transferred`() = runTest {
        val released = mutableListOf<Candidate>()
        val owner = owner(released)
        var committed: Candidate? = null

        val result = owner.replace(
            build = { Candidate("primary") },
            commit = { candidate, _ -> committed = candidate },
        )

        assertEquals(ReaderRouteReplacementResult.Applied(1L), result)
        assertEquals("primary", committed?.name)
        assertTrue(released.isEmpty())
        assertFalse(owner.hasActiveBuild())
    }

    @Test
    fun `new request cancels and joins its predecessor before building`() = runTest {
        val released = mutableListOf<Candidate>()
        val owner = owner(released)
        val firstStarted = CompletableDeferred<Unit>()
        val firstCancelled = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()

        val first = async {
            owner.replace(
                build = {
                    firstStarted.complete(Unit)
                    try {
                        CompletableDeferred<Unit>().await()
                        Candidate("unreachable")
                    } finally {
                        order += "first-finished"
                        firstCancelled.complete(Unit)
                    }
                },
                commit = { _, _ -> error("cancelled route committed") },
            )
        }
        firstStarted.await()
        val second = async {
            owner.replace(
                build = {
                    assertTrue(firstCancelled.isCompleted)
                    order += "second-built"
                    Candidate("alternate")
                },
                commit = { _, _ -> order += "second-committed" },
            )
        }

        assertThrows<CancellationException> { first.await() }
        assertEquals(ReaderRouteReplacementResult.Applied(2L), second.await())
        assertEquals(listOf("first-finished", "second-built", "second-committed"), order)
    }

    @Test
    fun `cancellation-insensitive stale candidate is released and never committed`() = runTest {
        val released = mutableListOf<Candidate>()
        val owner = owner(released)
        val firstStarted = CompletableDeferred<Unit>()
        val allowFirstToFinish = CompletableDeferred<Unit>()
        val commits = mutableListOf<String>()

        val first = async {
            owner.replace(
                build = {
                    firstStarted.complete(Unit)
                    withContext(NonCancellable) { allowFirstToFinish.await() }
                    Candidate("stale")
                },
                commit = { candidate, _ -> commits += candidate.name },
            )
        }
        firstStarted.await()
        val second = async {
            owner.replace(
                build = { Candidate("current") },
                commit = { candidate, _ -> commits += candidate.name },
            )
        }
        allowFirstToFinish.complete(Unit)

        assertThrows<CancellationException> { first.await() }
        assertEquals(ReaderRouteReplacementResult.Applied(2L), second.await())
        assertEquals(listOf("current"), commits)
        assertEquals(listOf("stale"), released.map { it.name })
    }

    @Test
    fun `ordinary failure is typed while cancellation and fatal error propagate`() = runTest {
        val released = mutableListOf<Candidate>()
        val owner = owner(released)
        val failure = IllegalStateException("route unavailable")

        val failed = owner.replace(
            build = { throw failure },
            commit = { _, _ -> error("failed build committed") },
        )
        assertTrue(failed is ReaderRouteReplacementResult.Failed)
        assertSame(failure, (failed as ReaderRouteReplacementResult.Failed).cause)

        assertThrows<CancellationException> {
            owner.replace(
                build = { throw CancellationException("cancel") },
                commit = { _, _ -> error("cancelled build committed") },
            )
        }
        assertThrows<AssertionError> {
            owner.replace(
                build = { throw AssertionError("fatal") },
                commit = { _, _ -> error("fatal build committed") },
            )
        }
    }

    @Test
    fun `commit failure releases candidate and leaves ownership untransferred`() = runTest {
        val released = mutableListOf<Candidate>()
        val owner = owner(released)
        val failure = IllegalArgumentException("commit failed")

        val result = owner.replace(
            build = { Candidate("candidate") },
            commit = { _, _ -> throw failure },
        )

        assertTrue(result is ReaderRouteReplacementResult.Failed)
        val cause = (result as ReaderRouteReplacementResult.Failed).cause
        assertEquals(failure::class, cause::class)
        assertEquals(failure.message, cause.message)
        assertEquals(listOf("candidate"), released.map { it.name })
    }

    @Test
    fun `close cancels active work and rejects future requests`() = runTest {
        val released = mutableListOf<Candidate>()
        val owner = owner(released)
        val started = CompletableDeferred<Unit>()
        val active = async {
            owner.replace(
                build = {
                    started.complete(Unit)
                    CompletableDeferred<Unit>().await()
                    Candidate("unreachable")
                },
                commit = { _, _ -> error("closed route committed") },
            )
        }
        started.await()

        owner.close()
        advanceUntilIdle()

        assertThrows<CancellationException> { active.await() }
        assertEquals(
            ReaderRouteReplacementResult.Closed,
            owner.replace(build = { Candidate("late") }, commit = { _, _ -> }),
        )
        assertFalse(owner.hasActiveBuild())
    }

    @Test
    fun `generation exhaustion refuses work without invoking builder`() = runTest {
        val released = mutableListOf<Candidate>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val owner = SerializedReaderRouteOwner(
            scope = this,
            buildDispatcher = dispatcher,
            commitDispatcher = dispatcher,
            releaseCandidate = released::add,
            initialGeneration = Long.MAX_VALUE,
        )
        var built = false

        assertEquals(
            ReaderRouteReplacementResult.GenerationExhausted,
            owner.replace(build = {
                built = true
                Candidate("never")
            }, commit = { _, _ -> }),
        )
        assertFalse(built)
        assertTrue(released.isEmpty())
    }

    private fun kotlinx.coroutines.test.TestScope.owner(
        released: MutableList<Candidate>,
    ): SerializedReaderRouteOwner<Candidate> {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return SerializedReaderRouteOwner(
            scope = CoroutineScope(SupervisorJob() + dispatcher),
            buildDispatcher = dispatcher,
            commitDispatcher = dispatcher,
            releaseCandidate = released::add,
        )
    }

    private data class Candidate(val name: String)
}
