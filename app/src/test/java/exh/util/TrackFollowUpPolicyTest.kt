package exh.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.model.Track

// KMK -->
class TrackFollowUpPolicyTest {

    private val manga = Manga.create()
    private val track = Track(
        id = 1L,
        mangaId = manga.id,
        trackerId = 2L,
        remoteId = 3L,
        libraryId = null,
        title = "Manga",
        lastChapterRead = 1.0,
        totalChapters = 10L,
        status = 1L,
        score = 5.0,
        remoteUrl = "https://example.invalid/manga",
        startDate = 0L,
        finishDate = 0L,
        private = false,
    )

    @Test
    fun `missing manga is unavailable`() {
        assertEquals(
            TrackFollowUpPolicy.RestoreFollowUp.Unavailable(TrackFollowUpPolicy.RestoreFollowUp.Reason.MANGA_NOT_FOUND),
            TrackFollowUpPolicy.evaluate(null, track, trackerLoggedIn = true),
        )
    }

    @Test
    fun `missing track is unavailable`() {
        assertEquals(
            TrackFollowUpPolicy.RestoreFollowUp.Unavailable(TrackFollowUpPolicy.RestoreFollowUp.Reason.TRACK_NOT_FOUND),
            TrackFollowUpPolicy.evaluate(manga, null, trackerLoggedIn = true),
        )
    }

    @Test
    fun `logged out tracker is unavailable`() {
        assertEquals(
            TrackFollowUpPolicy.RestoreFollowUp.Unavailable(TrackFollowUpPolicy.RestoreFollowUp.Reason.TRACKER_NOT_LOGGED_IN),
            TrackFollowUpPolicy.evaluate(manga, track, trackerLoggedIn = false),
        )
    }

    @Test
    fun `existing manga track and logged in tracker are offered`() {
        assertEquals(TrackFollowUpPolicy.RestoreFollowUp.Offered, TrackFollowUpPolicy.evaluate(manga, track, true))
    }
}
// KMK <--
