package eu.kanade.tachiyomi.ui.reader.schedule

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

// KMK v0.8.5 -->
class ReaderScheduleResolverTest {

    // Monday 2026-07-13 (a fixed reference date used throughout).
    private fun mondayAt(hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 7, 13, hour, minute)
    private fun tuesdayAt(hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 7, 14, hour, minute)
    private fun sundayAt(hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 7, 12, hour, minute)

    private fun resolve(schedule: ReaderSchedule, at: LocalDateTime) = ReaderScheduleResolver.resolve(schedule, at)

    // --- Disabled ---

    @Test
    fun `a disabled schedule is always DISABLED regardless of time`() {
        val schedule = ReaderSchedule(enabled = false, mode = ReaderScheduleMode.RESTRICTED, windows = listOf(allDayWindow()))
        assertEquals(ReaderScheduleResult.DISABLED, resolve(schedule, mondayAt(12)))
    }

    @Test
    fun `an enabled schedule with zero valid windows is effectively DISABLED, not locking the user out`() {
        val schedule = ReaderSchedule(enabled = true, mode = ReaderScheduleMode.ALLOWED, windows = emptyList())
        assertEquals(ReaderScheduleResult.DISABLED, resolve(schedule, mondayAt(12)))
    }

    // --- Normal (non-crossing) windows ---

    @Test
    fun `ALLOWED mode permits reading only inside a matching window`() {
        val schedule = ReaderSchedule(
            enabled = true,
            mode = ReaderScheduleMode.ALLOWED,
            windows = listOf(ReaderScheduleWindow(setOf(DayOfWeek.MONDAY), 9 * 60, 17 * 60)),
        )
        assertEquals(ReaderScheduleResult.ALLOWED, resolve(schedule, mondayAt(12)))
        assertEquals(ReaderScheduleResult.RESTRICTED, resolve(schedule, mondayAt(20)))
    }

    @Test
    fun `RESTRICTED mode restricts reading only inside a matching window`() {
        val schedule = ReaderSchedule(
            enabled = true,
            mode = ReaderScheduleMode.RESTRICTED,
            windows = listOf(ReaderScheduleWindow(setOf(DayOfWeek.MONDAY), 22 * 60, 23 * 60 + 59)),
        )
        assertEquals(ReaderScheduleResult.RESTRICTED, resolve(schedule, mondayAt(22, 30)))
        assertEquals(ReaderScheduleResult.ALLOWED, resolve(schedule, mondayAt(12)))
    }

    @Test
    fun `window boundaries are start-inclusive and end-exclusive`() {
        val schedule = ReaderSchedule(
            enabled = true,
            mode = ReaderScheduleMode.RESTRICTED,
            windows = listOf(ReaderScheduleWindow(setOf(DayOfWeek.MONDAY), 60, 120)), // 01:00-02:00
        )
        assertEquals(ReaderScheduleResult.RESTRICTED, resolve(schedule, mondayAt(1, 0)))
        assertEquals(ReaderScheduleResult.ALLOWED, resolve(schedule, mondayAt(2, 0)))
    }

    // --- Weekday boundaries ---

    @Test
    fun `a window only applies on its configured weekdays`() {
        val schedule = ReaderSchedule(
            enabled = true,
            mode = ReaderScheduleMode.RESTRICTED,
            windows = listOf(ReaderScheduleWindow(setOf(DayOfWeek.MONDAY), 0, 23 * 60 + 59)),
        )
        assertEquals(ReaderScheduleResult.RESTRICTED, resolve(schedule, mondayAt(12)))
        assertEquals(ReaderScheduleResult.ALLOWED, resolve(schedule, tuesdayAt(12)))
    }

    @Test
    fun `weekday wraparound from Sunday to Monday works for a crossing window`() {
        val schedule = ReaderSchedule(
            enabled = true,
            mode = ReaderScheduleMode.RESTRICTED,
            windows = listOf(ReaderScheduleWindow(setOf(DayOfWeek.SUNDAY), 23 * 60, 60)), // Sun 23:00 -> Mon 01:00
        )
        assertEquals(ReaderScheduleResult.RESTRICTED, resolve(schedule, sundayAt(23, 30)))
        assertEquals(ReaderScheduleResult.RESTRICTED, resolve(schedule, mondayAt(0, 30))) // spillover into Monday
        assertEquals(ReaderScheduleResult.ALLOWED, resolve(schedule, mondayAt(1, 30)))
    }

    // --- Midnight crossing ---

    @Test
    fun `a midnight-crossing window matches both the starting evening and the spillover morning`() {
        val schedule = ReaderSchedule(
            enabled = true,
            mode = ReaderScheduleMode.RESTRICTED,
            windows = listOf(ReaderScheduleWindow(setOf(DayOfWeek.MONDAY), 22 * 60, 6 * 60)), // Mon 22:00 -> Tue 06:00
        )
        assertEquals(ReaderScheduleResult.RESTRICTED, resolve(schedule, mondayAt(23, 0)))
        assertEquals(ReaderScheduleResult.RESTRICTED, resolve(schedule, tuesdayAt(3, 0)))
        assertEquals(ReaderScheduleResult.ALLOWED, resolve(schedule, tuesdayAt(7, 0)))
        assertEquals(ReaderScheduleResult.ALLOWED, resolve(schedule, mondayAt(12, 0)))
    }

    @Test
    fun `a midnight-crossing window is normalized, not treated as invalid or empty`() {
        // end <= start is definitionally a crossing window, per ReaderScheduleWindow.crossesMidnight.
        val window = ReaderScheduleWindow(setOf(DayOfWeek.MONDAY), 22 * 60, 6 * 60)
        assertEquals(true, window.crossesMidnight)
        assertEquals(true, ReaderScheduleWindow.isValid(window.weekdays, window.startMinuteOfDay, window.endMinuteOfDay))
    }

    // --- Overlap ("precedence") within one mode ---

    @Test
    fun `multiple windows of the same schedule OR together - any match is a match`() {
        val schedule = ReaderSchedule(
            enabled = true,
            mode = ReaderScheduleMode.ALLOWED,
            windows = listOf(
                ReaderScheduleWindow(setOf(DayOfWeek.MONDAY), 6 * 60, 8 * 60),
                ReaderScheduleWindow(setOf(DayOfWeek.MONDAY), 18 * 60, 21 * 60),
            ),
        )
        assertEquals(ReaderScheduleResult.ALLOWED, resolve(schedule, mondayAt(7)))
        assertEquals(ReaderScheduleResult.ALLOWED, resolve(schedule, mondayAt(19)))
        assertEquals(ReaderScheduleResult.RESTRICTED, resolve(schedule, mondayAt(12)))
    }

    @Test
    fun `overlapping windows of the same mode are still deterministic - overlap region behaves as a single match`() {
        val schedule = ReaderSchedule(
            enabled = true,
            mode = ReaderScheduleMode.RESTRICTED,
            windows = listOf(
                ReaderScheduleWindow(setOf(DayOfWeek.MONDAY), 8 * 60, 12 * 60),
                ReaderScheduleWindow(setOf(DayOfWeek.MONDAY), 10 * 60, 14 * 60),
            ),
        )
        assertEquals(ReaderScheduleResult.RESTRICTED, resolve(schedule, mondayAt(11))) // in both
        assertEquals(ReaderScheduleResult.RESTRICTED, resolve(schedule, mondayAt(9))) // in first only
        assertEquals(ReaderScheduleResult.RESTRICTED, resolve(schedule, mondayAt(13))) // in second only
        assertEquals(ReaderScheduleResult.ALLOWED, resolve(schedule, mondayAt(15))) // in neither
    }

    // --- Invalid values fall back safely ---

    @Test
    fun `a window with empty weekdays is invalid and ignored`() {
        val schedule = ReaderSchedule(
            enabled = true,
            mode = ReaderScheduleMode.RESTRICTED,
            windows = listOf(ReaderScheduleWindow(emptySet(), 0, 100)),
        )
        assertEquals(ReaderScheduleResult.DISABLED, resolve(schedule, mondayAt(1)))
    }

    @Test
    fun `a window with out-of-range minutes is invalid and ignored`() {
        val schedule = ReaderSchedule(
            enabled = true,
            mode = ReaderScheduleMode.RESTRICTED,
            windows = listOf(ReaderScheduleWindow(setOf(DayOfWeek.MONDAY), -5, 5000)),
        )
        assertEquals(ReaderScheduleResult.DISABLED, resolve(schedule, mondayAt(1)))
    }

    @Test
    fun `a mix of one invalid and one valid window still evaluates using the valid window only`() {
        val schedule = ReaderSchedule(
            enabled = true,
            mode = ReaderScheduleMode.RESTRICTED,
            windows = listOf(
                ReaderScheduleWindow(emptySet(), 0, 100),
                ReaderScheduleWindow(setOf(DayOfWeek.MONDAY), 8 * 60, 9 * 60),
            ),
        )
        assertEquals(ReaderScheduleResult.RESTRICTED, resolve(schedule, mondayAt(8, 30)))
        assertEquals(ReaderScheduleResult.ALLOWED, resolve(schedule, mondayAt(12)))
    }

    // --- Determinism / "time-zone changes" (the resolver only ever depends on the LocalDateTime it's given) ---

    @Test
    fun `resolve is a pure function of its inputs - identical inputs always produce identical output`() {
        val schedule = ReaderSchedule(
            enabled = true,
            mode = ReaderScheduleMode.RESTRICTED,
            windows = listOf(ReaderScheduleWindow(setOf(DayOfWeek.MONDAY), 60, 120)),
        )
        val at = mondayAt(1, 30)
        assertEquals(resolve(schedule, at), resolve(schedule, at))
        assertEquals(resolve(schedule, at), resolve(schedule, LocalDateTime.of(2026, 7, 13, 1, 30)))
    }

    private fun allDayWindow() = ReaderScheduleWindow(DayOfWeek.entries.toSet(), 0, ReaderScheduleWindow.MINUTES_PER_DAY - 1)

    // KMK v0.8.7: explicit allDay flag (plan Finding C) -->

    @Test
    fun `an allDay window matches every minute of its configured weekday`() {
        val schedule = ReaderSchedule(
            enabled = true,
            mode = ReaderScheduleMode.RESTRICTED,
            windows = listOf(ReaderScheduleWindow(setOf(DayOfWeek.MONDAY), 0, 0, allDay = true)),
        )
        assertEquals(ReaderScheduleResult.RESTRICTED, resolve(schedule, mondayAt(0, 0)))
        assertEquals(ReaderScheduleResult.RESTRICTED, resolve(schedule, mondayAt(12, 0)))
        assertEquals(ReaderScheduleResult.RESTRICTED, resolve(schedule, mondayAt(23, 59)))
    }

    @Test
    fun `an allDay window does not apply on a weekday it was not configured for`() {
        val schedule = ReaderSchedule(
            enabled = true,
            mode = ReaderScheduleMode.RESTRICTED,
            windows = listOf(ReaderScheduleWindow(setOf(DayOfWeek.MONDAY), 0, 0, allDay = true)),
        )
        assertEquals(ReaderScheduleResult.ALLOWED, resolve(schedule, tuesdayAt(12)))
    }

    @Test
    fun `an allDay window is valid even though start equals end`() {
        assertEquals(true, ReaderScheduleWindow.isValid(setOf(DayOfWeek.MONDAY), 0, 0, allDay = true))
    }

    @Test
    fun `a non-allDay window with equal start and end minutes remains invalid`() {
        assertEquals(false, ReaderScheduleWindow.isValid(setOf(DayOfWeek.MONDAY), 480, 480, allDay = false))
    }

    @Test
    fun `an allDay window never reports crossesMidnight`() {
        val window = ReaderScheduleWindow(setOf(DayOfWeek.MONDAY), 0, 0, allDay = true)
        assertEquals(false, window.crossesMidnight)
    }
}
// KMK <--
