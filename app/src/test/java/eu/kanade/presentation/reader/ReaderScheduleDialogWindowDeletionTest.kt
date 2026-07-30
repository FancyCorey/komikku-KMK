package eu.kanade.presentation.reader

import eu.kanade.tachiyomi.ui.reader.schedule.ReaderScheduleWindow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.DayOfWeek

// KMK Confirmed Blocker Remediation 2026-07-28 -->
/**
 * Tests for [ReaderScheduleWindowDeletionPolicy], extracted from [ReaderScheduleDialog]'s
 * pendingDeleteIndex confirm-button handler. No Compose UI test infrastructure exists in this
 * module (no Robolectric/Compose-test dependency is configured), so this covers the exact same
 * removal logic the dialog's confirm button calls, at the pure-function level -- the plan's
 * required behavior matrix maps onto this policy plus the dialog's already-read confirmed source:
 *
 * - Tapping delete opens confirmation and leaves the draft unchanged: confirmed by direct reading
 *   of `ReaderScheduleDialog.kt` -- `pendingDeleteIndex = index` never touches `windows`.
 * - Cancel, back, and scrim dismissal leave the window present: confirmed by direct reading --
 *   every dismissal path only clears `pendingDeleteIndex`, never calls
 *   [ReaderScheduleWindowDeletionPolicy.removeAt].
 * - Confirm removes only the selected draft window: covered here.
 * - Save persists the resulting list once: confirmed by direct reading -- `onSave(mode, windows)`
 *   is called only from the outer dialog's own confirm button, a separate code path this policy
 *   does not touch.
 */
class ReaderScheduleDialogWindowDeletionTest {

    private fun window(day: DayOfWeek, start: Int, end: Int) =
        ReaderScheduleWindow(weekdays = setOf(day), startMinuteOfDay = start, endMinuteOfDay = end, allDay = false)

    @Test
    fun `confirm removes only the selected index, leaving other windows in order`() {
        val windows = listOf(
            window(DayOfWeek.MONDAY, 0, 60),
            window(DayOfWeek.TUESDAY, 60, 120),
            window(DayOfWeek.WEDNESDAY, 120, 180),
        )

        val result = ReaderScheduleWindowDeletionPolicy.removeAt(windows, 1)

        assertEquals(listOf(windows[0], windows[2]), result)
    }

    @Test
    fun `deleting the first window preserves the rest in order`() {
        val windows = listOf(
            window(DayOfWeek.MONDAY, 0, 60),
            window(DayOfWeek.TUESDAY, 60, 120),
        )
        val result = ReaderScheduleWindowDeletionPolicy.removeAt(windows, 0)
        assertEquals(listOf(windows[1]), result)
    }

    @Test
    fun `deleting the only window leaves an empty list`() {
        val windows = listOf(window(DayOfWeek.MONDAY, 0, 60))
        val result = ReaderScheduleWindowDeletionPolicy.removeAt(windows, 0)
        assertEquals(emptyList<ReaderScheduleWindow>(), result)
    }

    @Test
    fun `an out-of-range index is a no-op, not a crash`() {
        val windows = listOf(window(DayOfWeek.MONDAY, 0, 60))
        val result = ReaderScheduleWindowDeletionPolicy.removeAt(windows, 5)
        assertEquals(windows, result)
    }

    @Test
    fun `a negative index is a no-op`() {
        val windows = listOf(window(DayOfWeek.MONDAY, 0, 60))
        val result = ReaderScheduleWindowDeletionPolicy.removeAt(windows, -1)
        assertEquals(windows, result)
    }

    @Test
    fun `an empty list stays empty regardless of index`() {
        val result = ReaderScheduleWindowDeletionPolicy.removeAt(emptyList(), 0)
        assertEquals(emptyList<ReaderScheduleWindow>(), result)
    }

    @Test
    fun `removeAt does not mutate the input list -- draft-vs-persisted semantics stay intact`() {
        val original = listOf(window(DayOfWeek.MONDAY, 0, 60), window(DayOfWeek.TUESDAY, 60, 120))
        val originalCopy = original.toList()

        ReaderScheduleWindowDeletionPolicy.removeAt(original, 0)

        assertEquals(originalCopy, original, "the source list passed in must remain unchanged -- the caller decides whether to commit the result")
    }
}
// KMK <--
