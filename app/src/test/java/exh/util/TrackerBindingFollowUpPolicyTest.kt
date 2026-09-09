package exh.util

import eu.kanade.tachiyomi.data.track.DeletableTracker
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.track.model.Track

// KMK -->
class TrackerBindingFollowUpPolicyTest {

    private val track = Track(
        id = 1L,
        mangaId = 10L,
        trackerId = 20L,
        remoteId = 30L,
        libraryId = null,
        title = "Manga",
        lastChapterRead = 0.0,
        totalChapters = 10L,
        status = 1L,
        score = 0.0,
        remoteUrl = "https://example.invalid/manga",
        startDate = 0L,
        finishDate = 0L,
        private = false,
    )
    private val tracker = mockk<DeletableTracker>(relaxed = true)

    @Test
    fun `matching deletable logged in tracker is offered`() {
        assertEquals(
            TrackerBindingFollowUpPolicy.Decision.Offered,
            TrackerBindingFollowUpPolicy.evaluate(track, tracker, true, 30L),
        )
    }

    @Test
    fun `missing track is unavailable`() {
        assertEquals(
            TrackerBindingFollowUpPolicy.Decision.Unavailable(TrackerBindingFollowUpPolicy.Reason.MISSING_TRACK),
            TrackerBindingFollowUpPolicy.evaluate(null, tracker, true, 30L),
        )
    }

    @Test
    fun `changed remote id is refused`() {
        assertEquals(
            TrackerBindingFollowUpPolicy.Decision.Unavailable(TrackerBindingFollowUpPolicy.Reason.REMOTE_ID_CHANGED),
            TrackerBindingFollowUpPolicy.evaluate(track, tracker, true, 31L),
        )
    }

    @Test
    fun `logged out tracker is refused`() {
        assertEquals(
            TrackerBindingFollowUpPolicy.Decision.Unavailable(TrackerBindingFollowUpPolicy.Reason.TRACKER_LOGGED_OUT),
            TrackerBindingFollowUpPolicy.evaluate(track, tracker, false, 30L),
        )
    }

    @Test
    fun `non deletable tracker is refused`() {
        assertEquals(
            TrackerBindingFollowUpPolicy.Decision.Unavailable(TrackerBindingFollowUpPolicy.Reason.TRACKER_NOT_DELETABLE),
            TrackerBindingFollowUpPolicy.evaluate(track, null, true, 30L),
        )
    }
}
// KMK <--
