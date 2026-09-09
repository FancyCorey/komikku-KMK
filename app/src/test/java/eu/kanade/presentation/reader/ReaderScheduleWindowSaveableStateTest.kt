package eu.kanade.presentation.reader

import eu.kanade.tachiyomi.ui.reader.schedule.ReaderScheduleWindow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.DayOfWeek

class ReaderScheduleWindowSaveableStateTest {

    @Test
    fun `round trip preserves regular and midnight crossing windows`() {
        val windows = listOf(
            ReaderScheduleWindow(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), 8 * 60, 17 * 60),
            ReaderScheduleWindow(setOf(DayOfWeek.SATURDAY), 22 * 60, 3 * 60),
        )

        assertEquals(windows, ReaderScheduleWindowSaveableState.decode(ReaderScheduleWindowSaveableState.encode(windows)))
        assertEquals(true, ReaderScheduleWindowSaveableState.encode(windows) is String)
    }

    @Test
    fun `round trip preserves an all day window`() {
        val windows = listOf(
            ReaderScheduleWindow(DayOfWeek.entries.toSet(), 0, 0, allDay = true),
        )

        assertEquals(windows, ReaderScheduleWindowSaveableState.decode(ReaderScheduleWindowSaveableState.encode(windows)))
    }

    @Test
    fun `empty draft remains empty`() {
        assertEquals(emptyList<ReaderScheduleWindow>(), ReaderScheduleWindowSaveableState.decode(ReaderScheduleWindowSaveableState.encode(emptyList())))
    }

    @Test
    fun `malformed rows are dropped without affecting valid rows`() {
        val valid = ReaderScheduleWindow(setOf(DayOfWeek.TUESDAY), 60, 120)
        val legacyEncoded = listOf(
            listOf(listOf(2), 60, 120, false),
            listOf(listOf(99), 0, 60, false),
            listOf(listOf(2), 60, 60, false),
            listOf(listOf(2), 60, 120, "not-a-boolean"),
            listOf(listOf(2), 60, 120, false, "extra-field"),
        )

        assertEquals(listOf(valid), ReaderScheduleWindowSaveableState.decode(legacyEncoded))
    }

    @Test
    fun `malformed serialized state becomes an empty or valid-only draft`() {
        val valid = ReaderScheduleWindow(setOf(DayOfWeek.TUESDAY), 60, 120)
        val encoded = "99:0:60;2:60:60;${ReaderScheduleWindowSaveableState.encode(listOf(valid))}"

        assertEquals(listOf(valid), ReaderScheduleWindowSaveableState.decode(encoded))
    }

    @Test
    fun `malformed root value becomes an empty draft`() {
        assertEquals(emptyList<ReaderScheduleWindow>(), ReaderScheduleWindowSaveableState.decode("not-a-list"))
    }
}
