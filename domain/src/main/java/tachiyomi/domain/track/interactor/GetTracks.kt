package tachiyomi.domain.track.interactor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.model.Track
import tachiyomi.domain.track.repository.TrackRepository

class GetTracks(
    private val trackRepository: TrackRepository,
) {

    suspend fun awaitOne(id: Long): Track? {
        return try {
            trackRepository.getTrackById(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            null
        }
    }

    // SY -->
    suspend fun await(): List<Track> {
        return try {
            trackRepository.getTracks()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    suspend fun await(mangaIds: List<Long>): Map<Long, List<Track>> {
        return try {
            trackRepository.getTracksByMangaIds(mangaIds)
                .groupBy { it.mangaId }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyMap()
        }
    }
    // SY <--

    /**
     * Like [await], but **distinguishes "no tracks" from "the lookup failed"**.
     *
     * [await] deliberately keeps its fail-soft contract (an empty map on failure) so every existing
     * caller is completely unchanged by this addition. That contract is wrong, however, for any
     * caller whose correctness depends on knowing whether the absence of a track is a fact or an
     * unknown -- notably the For You exposure reranker, where mistaking "unknown" for "untracked"
     * would let a title the user actively tracks be repeatedly demoted.
     *
     * @return the tracks grouped by manga id, or `null` if the query could not be completed.
     * Cancellation always propagates. Nothing is logged here: the caller decides what, if anything,
     * is safe to record, and no tracker identifier, account detail, or exception object is emitted.
     */
    suspend fun awaitOrNull(mangaIds: List<Long>): Map<Long, List<Track>>? {
        return try {
            trackRepository.getTracksByMangaIds(mangaIds)
                .groupBy { it.mangaId }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
    // KMK <--

    suspend fun await(mangaId: Long): List<Track> {
        return try {
            trackRepository.getTracksByMangaId(mangaId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    fun subscribe(mangaId: Long): Flow<List<Track>> {
        return trackRepository.getTracksByMangaIdAsFlow(mangaId)
    }
}
