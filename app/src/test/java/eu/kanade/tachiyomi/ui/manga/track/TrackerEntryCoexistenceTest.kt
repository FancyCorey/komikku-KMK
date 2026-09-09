package eu.kanade.tachiyomi.ui.manga.track

import eu.kanade.test.DummyTracker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.track.model.Track
import tachiyomi.domain.tracker.model.LocalTrackedWork
import tachiyomi.domain.tracker.model.LocalTrackedWorkStatus

// KMK v0.8.21-fix5 -->
/**
 * Regression coverage for R5/AUG-18's closure: proves [TrackerEntry.Local] and
 * [TrackerEntry.External] genuinely coexist and remain structurally independent.
 *
 * R5 correction: this used to define its own local `buildEntries()` copying
 * `TrackInfoDialog.kt`'s construction expression, which is tautological -- it would still pass if
 * production drifted from it, since the test never actually invoked production code. It now calls
 * [TrackerEntry.build] directly, the one production owner both `TrackInfoDialog.kt` and this test
 * route through, so a change to the real construction logic is what these tests exercise.
 */
class TrackerEntryCoexistenceTest {

    private fun localWork(status: LocalTrackedWorkStatus = LocalTrackedWorkStatus.READING) = LocalTrackedWork(
        id = "work-1",
        title = "Example",
        normalizedTitle = "example",
        status = status,
        lastChapterSource = null,
        lastChapterNumber = null,
        lastChapterUrl = null,
        lastChapterLabel = null,
        lastProgressAt = null,
        createdAt = 1000L,
        updatedAt = 1000L,
    )

    private fun trackItem(trackerId: Long, trackerName: String) = TrackItem(
        track = Track(
            id = trackerId,
            mangaId = 1L,
            trackerId = trackerId,
            remoteId = trackerId,
            libraryId = null,
            title = "Remote Title $trackerId",
            lastChapterRead = 0.0,
            totalChapters = 0L,
            status = 1L,
            score = 0.0,
            remoteUrl = "https://example.com/$trackerId",
            startDate = 0L,
            finishDate = 0L,
            private = false,
        ),
        tracker = DummyTracker(id = trackerId, name = trackerName),
    )

    @Test
    fun `Local and External entries coexist -- exactly one Local plus every External, in order`() {
        val items = listOf(trackItem(1L, "AniList"), trackItem(2L, "MyAnimeList"))
        val work = localWork()

        val entries = TrackerEntry.build(items, work)

        assertEquals(3, entries.size)
        val locals = entries.filterIsInstance<TrackerEntry.Local>()
        assertEquals(1, locals.size)
        assertEquals(work, locals.single().work)
        val externals = entries.filterIsInstance<TrackerEntry.External>()
        assertEquals(items, externals.map { it.item })
    }

    @Test
    fun `an empty tracker list still produces the Local entry, unaffected`() {
        val work = localWork()

        val entries = TrackerEntry.build(emptyList(), work)

        assertEquals(1, entries.size)
        val local = entries.single() as TrackerEntry.Local
        assertEquals(work, local.work)
    }

    @Test
    fun `a null local work does not hide the Local row or corrupt the External entries`() {
        val items = listOf(trackItem(1L, "AniList"), trackItem(2L, "MyAnimeList"))

        val entries = TrackerEntry.build(items, null)

        assertEquals(3, entries.size)
        val local = entries.last() as TrackerEntry.Local
        assertEquals(null, local.work)
        val externals = entries.filterIsInstance<TrackerEntry.External>()
        assertEquals(items, externals.map { it.item })
    }

    @Test
    fun `changing local work status leaves the External entries byte-for-byte identical`() {
        val items = listOf(trackItem(1L, "AniList"), trackItem(2L, "MyAnimeList"))

        val readingEntries = TrackerEntry.build(items, localWork(LocalTrackedWorkStatus.READING))
        val completedEntries = TrackerEntry.build(items, localWork(LocalTrackedWorkStatus.COMPLETED))

        val readingExternals = readingEntries.filterIsInstance<TrackerEntry.External>()
        val completedExternals = completedEntries.filterIsInstance<TrackerEntry.External>()
        assertEquals(readingExternals, completedExternals)

        // Only the Local entry's own content differs between the two constructions.
        val readingLocal = readingEntries.filterIsInstance<TrackerEntry.Local>().single()
        val completedLocal = completedEntries.filterIsInstance<TrackerEntry.Local>().single()
        assertTrue(readingLocal.work!!.status != completedLocal.work!!.status)
    }
}
// KMK <--
