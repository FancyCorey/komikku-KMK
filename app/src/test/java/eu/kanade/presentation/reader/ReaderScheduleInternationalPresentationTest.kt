package eu.kanade.presentation.reader

import eu.kanade.tachiyomi.ui.reader.schedule.ReaderScheduleWindow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.util.Locale

class ReaderScheduleInternationalPresentationTest {

    @Test
    fun `weekday labels come from the requested locale instead of enum identifiers`() {
        val frenchMonday = DayOfWeek.MONDAY.shortDisplayName(Locale.FRENCH)

        assertFalse(frenchMonday.equals("MON", ignoreCase = true))
        assertTrue(frenchMonday.lowercase(Locale.ROOT).startsWith("lun"))
    }

    @Test
    fun `all-day description uses localized day and injected resource label`() {
        val window = ReaderScheduleWindow(setOf(DayOfWeek.MONDAY), 0, 0, allDay = true)

        val description = describeWindow(window, Locale.FRENCH, "Toute la journee") { error("unused") }

        assertTrue(description.contains(DayOfWeek.MONDAY.shortDisplayName(Locale.FRENCH)))
        assertTrue(description.endsWith("(Toute la journee)"))
    }

    @Test
    fun `timed description delegates both times to the user-format boundary`() {
        val window = ReaderScheduleWindow(setOf(DayOfWeek.MONDAY), 8 * 60, 17 * 60)

        val description = describeWindow(window, Locale.ENGLISH, "Whole day") { minute -> "t$minute" }

        assertEquals("Mon t480-t1020", description)
    }
}
