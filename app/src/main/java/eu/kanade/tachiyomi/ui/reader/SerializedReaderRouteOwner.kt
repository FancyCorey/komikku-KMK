package eu.kanade.tachiyomi.ui.reader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

internal sealed interface ReaderRouteReplacementResult {
    data class Applied(val generation: Long) : ReaderRouteReplacementResult
    data class Failed(val cause: Exception) : ReaderRouteReplacementResult
    data object Stale : ReaderRouteReplacementResult
    data object Closed : ReaderRouteReplacementResult
    data object GenerationExhausted : ReaderRouteReplacementResult
}

/** Serializes candidate route construction and transfers ownership only at a current-generation commit. */
internal class SerializedReaderRouteOwner<T : Any>(
    private val scope: CoroutineScope,
    private val buildDispatcher: CoroutineDispatcher,
    private val commitDispatcher: CoroutineDispatcher,
    private val releaseCandidate: (T) -> Unit,
    initialGeneration: Long = 0L,
) {
    private val gate = Any()
    private var generation = initialGeneration
    private var activeBuild: Deferred<ReaderRouteReplacementResult>? = null
    private var closed = false

    suspend fun replace(
        build: suspend (generation: Long) -> T,
        commit: (candidate: T, generation: Long) -> Unit,
    ): ReaderRouteReplacementResult {
        val request = synchronized(gate) {
            if (closed) return ReaderRouteReplacementResult.Closed
            if (generation == Long.MAX_VALUE) return ReaderRouteReplacementResult.GenerationExhausted

            val previous = activeBuild
            val requestGeneration = ++generation
            previous?.cancel()
            val deferred = scope.async(buildDispatcher, start = CoroutineStart.LAZY) {
                previous?.join()
                buildAndCommit(requestGeneration, build, commit)
            }
            activeBuild = deferred
            deferred.invokeOnCompletion {
                synchronized(gate) {
                    if (activeBuild === deferred) activeBuild = null
                }
            }
            deferred
        }
        request.start()
        return try {
            request.await()
        } catch (e: CancellationException) {
            request.cancel(e)
            throw e
        }
    }

    fun close() {
        synchronized(gate) {
            if (closed) return
            closed = true
            generation = if (generation == Long.MAX_VALUE) generation else generation + 1L
            activeBuild?.cancel()
        }
    }

    internal fun currentGeneration(): Long = synchronized(gate) { generation }

    internal fun hasActiveBuild(): Boolean = synchronized(gate) { activeBuild?.isActive == true }

    private suspend fun buildAndCommit(
        requestGeneration: Long,
        build: suspend (generation: Long) -> T,
        commit: (candidate: T, generation: Long) -> Unit,
    ): ReaderRouteReplacementResult {
        var candidate: T? = null
        var ownershipTransferred = false
        return try {
            candidate = build(requestGeneration)
            withContext(commitDispatcher) {
                synchronized(gate) {
                    if (closed || generation != requestGeneration) {
                        ReaderRouteReplacementResult.Stale
                    } else {
                        commit(candidate, requestGeneration)
                        ownershipTransferred = true
                        ReaderRouteReplacementResult.Applied(requestGeneration)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ReaderRouteReplacementResult.Failed(e)
        } finally {
            if (!ownershipTransferred) candidate?.let(releaseCandidate)
        }
    }
}
