package tachiyomi.domain.track.interactor

import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.repository.TrackRepository

class DeleteTrack(
    private val trackRepository: TrackRepository,
) {

    suspend fun await(mangaId: Long, trackerId: Long) {
        try {
            trackRepository.delete(mangaId, trackerId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
