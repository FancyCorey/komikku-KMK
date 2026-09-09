package eu.kanade.tachiyomi.ui.reader

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** Keeps the reader shortcut attached to the canonical settings owner and reader task. */
class ReaderScheduleNavigationSourceTest {

    @Test
    fun `reader configure schedule stays in the active reader context`() {
        val source = File("src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt").readText()
        val viewModel = File("src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt").readText()

        assertTrue(
            source.contains("viewModel.openReadingScheduleDialog()"),
            "Reader timer configuration must open the schedule editor without abandoning the reader session",
        )
        assertTrue(
            source.contains("ReaderScheduleDialog("),
            "ReaderActivity must reuse the established schedule editor in the active reader context",
        )
        assertTrue(
            source.contains("onSave = viewModel::saveReadingSchedule"),
            "Reader schedule saves must use the ViewModel persistence seam",
        )
        assertTrue(
            source.contains("onDismissRequest = viewModel::returnToReadingTimerDialog"),
            "Schedule cancel/back must return to the active timer dialog",
        )
        assertTrue(
            viewModel.contains("KEY_SCHEDULE_ENTITLEMENT") && viewModel.contains("savedState"),
            "Schedule entitlement must survive process recreation within the reader session",
        )
    }

    @Test
    fun `main activity owns the schedule destination`() {
        val source = File("src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt").readText()

        assertTrue(source.contains("Constants.OPEN_READER_SCHEDULE_SETTINGS"))
        assertTrue(source.contains("SearchableSettings.highlightKey"))
        assertTrue(source.contains("KMR.strings.reading_schedule_configure"))
        assertTrue(source.contains("navigator.push(SettingsReaderScreen)"))
    }

    @Test
    fun `reader lifecycle and schedule persistence retain their shared contracts`() {
        val reader = File("src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt").readText()
        val persistence = File("src/main/java/eu/kanade/tachiyomi/ui/reader/schedule/ReaderSchedulePersistence.kt").readText()

        assertTrue(reader.contains("viewModel.onReaderBackground()"))
        assertTrue(reader.contains("viewModel.onReaderForeground()"))
        assertTrue(persistence.contains("PreferenceJournalActionType.READING_SCHEDULE"))
        assertTrue(persistence.contains("PreferenceUndoJournal.record(it)"))
    }

    @Test
    fun `reader timer stays discoverable and schedule copy explains its scope`() {
        val bottomBar = File("src/main/java/eu/kanade/presentation/reader/appbars/ReaderBottomBar.kt").readText()
        val timer = File("src/main/java/eu/kanade/presentation/reader/ReaderTimerDialog.kt").readText()
        val schedule = File("src/main/java/eu/kanade/presentation/reader/ReaderScheduleDialog.kt").readText()
        val strings = File("../i18n-kmk/src/commonMain/moko-resources/base/strings.xml").readText()

        assertTrue(
            bottomBar.contains("onClickReadingTimer") &&
                bottomBar.contains("KMR.strings.reading_timer_action"),
            "Reader controls must expose the timer with an accessible action label",
        )
        assertTrue(
            timer.contains("KMR.strings.reading_timer_pause_hint") &&
                timer.contains("KMR.strings.reading_schedule_configure") &&
                timer.contains("onClick = onConfigureSchedule") &&
                timer.contains("Modifier.fillMaxWidth().padding(top = MaterialTheme.padding.small)"),
            "Timer setup must retain explanatory copy and a discoverable schedule entry",
        )
        assertTrue(
            strings.contains(
                "reading_timer_pause_hint\">Set a reading timer for how long you can read. It pauses automatically when you leave the reader.</",
            ),
            "Timer explanation must use clear action-oriented sentence punctuation",
        )
        assertFalse(
            strings.substringAfter("name=\"reading_timer_pause_hint\"").substringBefore("</string>").contains("—"),
            "Timer explanation must not use the confusing dash-style sentence",
        )
        assertFalse(
            strings.substringAfter("name=\"reading_timer_status_chapter_grace\"").substringBefore("</string>").contains("—"),
            "Timer expiry status must use clear sentence punctuation",
        )
        assertFalse(
            strings.substringAfter("name=\"reading_schedule_restricted_grace_toast\"").substringBefore("</string>").contains("—"),
            "Schedule grace status must use clear sentence punctuation",
        )
        assertTrue(
            schedule.contains("KMR.strings.reading_schedule_scope_note") &&
                schedule.contains("MR.strings.action_cancel") &&
                schedule.contains("MR.strings.action_save"),
            "Schedule editing must explain its reader-only scope and expose cancel/save actions",
        )
    }

    @Test
    fun `alternate source global search may run above the reader for a result`() {
        val mainActivity = File("src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt").readText()

        assertTrue(
            mainActivity.contains("!isTaskRoot && !isAlternateSourceReturnSelection"),
            "Result-returning alternate-source search must not be discarded by the launcher duplicate guard",
        )
        assertTrue(
            File("src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt").readText()
                .contains("EXTRA_ALTERNATE_SOURCE_RETURN_SELECTION"),
            "Reader global search must retain its result-return contract",
        )
        assertTrue(
            File("src/main/java/eu/kanade/tachiyomi/ui/browse/source/globalsearch/GlobalSearchScreen.kt")
                .readText()
                .contains("if (returnSelection)"),
            "Backing out of result-returning global search must finish the temporary activity",
        )
    }
}
