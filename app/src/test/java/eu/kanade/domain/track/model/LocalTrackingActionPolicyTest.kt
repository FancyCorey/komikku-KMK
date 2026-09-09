package eu.kanade.domain.track.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.tracker.model.LocalTrackedProgressInheritancePolicy
import tachiyomi.domain.tracker.model.LocalTrackedWork
import tachiyomi.domain.tracker.model.LocalTrackedWorkStatus

class LocalTrackingActionPolicyTest {
    @Test
    fun absentWorkOffersTrack() {
        assertEquals(LocalTrackingActionPolicy.Action.TRACK, LocalTrackingActionPolicy.resolve(null))
    }

    @Test
    fun readingWorkOffersRemove() {
        assertEquals(LocalTrackingActionPolicy.Action.REMOVE, LocalTrackingActionPolicy.resolve(work(LocalTrackedWorkStatus.READING)))
    }

    @Test
    fun nonReadingWorkOffersResume() {
        LocalTrackedWorkStatus.entries.filterNot { it == LocalTrackedWorkStatus.READING }.forEach { status ->
            assertEquals(LocalTrackingActionPolicy.Action.RESUME, LocalTrackingActionPolicy.resolve(work(status)))
        }
    }

    private fun work(status: LocalTrackedWorkStatus) = LocalTrackedWork(
        id = "work-1", title = "Example", normalizedTitle = "example", status = status,
        lastChapterSource = null, lastChapterNumber = null, lastChapterUrl = null,
        lastChapterLabel = null, lastProgressAt = null, createdAt = 1L, updatedAt = 1L,
    )

    @Test
    fun `statusOrder covers every LocalTrackedWorkStatus exactly once`() {
        assertEquals(LocalTrackedWorkStatus.entries.toSet(), LocalTrackingActionPolicy.statusOrder.toSet())
        assertEquals(LocalTrackedWorkStatus.entries.size, LocalTrackingActionPolicy.statusOrder.size)
    }

    @Test
    fun `statusLabel is defined for every status`() {
        LocalTrackedWorkStatus.entries.forEach { status ->
            // Must not throw -- every status has a presentable label for the status/list dialog.
            LocalTrackingActionPolicy.statusLabel(status)
        }
    }

    @Test
    fun `finish date exists only while status is completed`() {
        val now = 42L
        assertEquals(now, LocalTrackingActionPolicy.finishDateForStatus(LocalTrackedWorkStatus.COMPLETED, now))
        LocalTrackedWorkStatus.entries.filterNot { it == LocalTrackedWorkStatus.COMPLETED }.forEach { status ->
            assertEquals(null, LocalTrackingActionPolicy.finishDateForStatus(status, now))
        }
    }

    @Test
    fun `progress resumes non-completed local work and preserves completed work`() {
        assertEquals(LocalTrackedWorkStatus.READING, LocalTrackedProgressInheritancePolicy.statusAfterProgress(LocalTrackedWorkStatus.PLANNED))
        assertEquals(LocalTrackedWorkStatus.READING, LocalTrackedProgressInheritancePolicy.statusAfterProgress(LocalTrackedWorkStatus.ON_HOLD))
        assertEquals(LocalTrackedWorkStatus.READING, LocalTrackedProgressInheritancePolicy.statusAfterProgress(LocalTrackedWorkStatus.DROPPED))
        assertEquals(LocalTrackedWorkStatus.READING, LocalTrackedProgressInheritancePolicy.statusAfterProgress(LocalTrackedWorkStatus.READING))
        assertEquals(LocalTrackedWorkStatus.COMPLETED, LocalTrackedProgressInheritancePolicy.statusAfterProgress(LocalTrackedWorkStatus.COMPLETED))
    }
}
