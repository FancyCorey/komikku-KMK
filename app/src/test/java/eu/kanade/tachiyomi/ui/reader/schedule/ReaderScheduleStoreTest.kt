package eu.kanade.tachiyomi.ui.reader.schedule

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.DayOfWeek

// KMK v0.8.5 -->
class ReaderScheduleStoreTest {

    @Test
    fun `round trip preserves a single window`() {
        val windows = listOf(ReaderScheduleWindow(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), 480, 1020))
        val raw = ReaderScheduleStore.serializeWindows(windows)
        assertEquals(windows, ReaderScheduleStore.parseWindows(raw))
    }

    @Test
    fun `round trip preserves multiple windows including a midnight-crossing one`() {
        val windows = listOf(
            ReaderScheduleWindow(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), 600, 1200),
            ReaderScheduleWindow(setOf(DayOfWeek.FRIDAY), 22 * 60, 6 * 60),
        )
        val raw = ReaderScheduleStore.serializeWindows(windows)
        assertEquals(windows.toSet(), ReaderScheduleStore.parseWindows(raw).toSet())
    }

    @Test
    fun `blank raw string parses to an empty window list`() {
        assertTrue(ReaderScheduleStore.parseWindows("").isEmpty())
    }

    @Test
    fun `a window entry with the wrong number of fields is dropped rather than crashing`() {
        val result = ReaderScheduleStore.parseWindows("1,2:480")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `an out-of-range weekday token is dropped but a sibling valid weekday in the same window is kept`() {
        val result = ReaderScheduleStore.parseWindows("1,99:480:600")
        assertEquals(1, result.size)
        assertEquals(setOf(DayOfWeek.MONDAY), result.first().weekdays)
    }

    @Test
    fun `a window whose weekday list is entirely invalid ends up with no valid weekdays and is dropped`() {
        val result = ReaderScheduleStore.parseWindows("99,100:480:600")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `a window entry with non-numeric minutes is dropped rather than crashing`() {
        val result = ReaderScheduleStore.parseWindows("1:not-a-number:600")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `one entirely-corrupt window entry does not prevent a sibling valid entry from parsing`() {
        val result = ReaderScheduleStore.parseWindows("99,100:480:600;2:480:600")
        assertEquals(1, result.size)
        assertEquals(setOf(DayOfWeek.TUESDAY), result.first().weekdays)
    }

    @Test
    fun `parseMode round-trips a valid mode`() {
        assertEquals(ReaderScheduleMode.ALLOWED, ReaderScheduleStore.parseMode(ReaderScheduleStore.serializeMode(ReaderScheduleMode.ALLOWED)))
    }

    @Test
    fun `parseMode falls back to RESTRICTED for null or corrupt values`() {
        assertEquals(ReaderScheduleMode.RESTRICTED, ReaderScheduleStore.parseMode(null))
        assertEquals(ReaderScheduleMode.RESTRICTED, ReaderScheduleStore.parseMode("NOT_A_REAL_MODE"))
        assertEquals(ReaderScheduleMode.RESTRICTED, ReaderScheduleStore.parseMode(""))
    }

    // KMK v0.8.7: whole-day representation -->

    @Test
    fun `round trip preserves a whole-day window`() {
        val windows = listOf(ReaderScheduleWindow(setOf(DayOfWeek.SUNDAY), 0, 0, allDay = true))
        val raw = ReaderScheduleStore.serializeWindows(windows)
        assertEquals(windows, ReaderScheduleStore.parseWindows(raw))
    }

    @Test
    fun `round trip preserves a mix of whole-day and timed windows`() {
        val windows = listOf(
            ReaderScheduleWindow(setOf(DayOfWeek.SATURDAY), 0, 0, allDay = true),
            ReaderScheduleWindow(setOf(DayOfWeek.MONDAY), 480, 1020, allDay = false),
        )
        val raw = ReaderScheduleStore.serializeWindows(windows)
        assertEquals(windows.toSet(), ReaderScheduleStore.parseWindows(raw).toSet())
    }

    @Test
    fun `pre-v0_8_7 three-field data parses with allDay defaulting to false, preserving old meaning`() {
        val result = ReaderScheduleStore.parseWindows("1:480:600")
        assertEquals(1, result.size)
        assertEquals(false, result.first().allDay)
        assertEquals(480, result.first().startMinuteOfDay)
        assertEquals(600, result.first().endMinuteOfDay)
    }

    @Test
    fun `an unrecognized allDay token is treated as false rather than crashing`() {
        val result = ReaderScheduleStore.parseWindows("1:480:600:garbage")
        assertEquals(1, result.size)
        assertEquals(false, result.first().allDay)
    }

    @Test
    fun `a five-field entry is dropped rather than crashing`() {
        val result = ReaderScheduleStore.parseWindows("1:480:600:0:extra")
        assertTrue(result.isEmpty())
    }
}
// KMK <--
