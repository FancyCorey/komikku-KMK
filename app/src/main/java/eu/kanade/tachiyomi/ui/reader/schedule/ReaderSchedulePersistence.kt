package eu.kanade.tachiyomi.ui.reader.schedule

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences

/**
 * Persists the complete schedule as one change so Settings and the in-reader editor have the
 * same Save and Evaluation Mode undo behavior.
 */
object ReaderSchedulePersistence {
    fun save(
        readerPreferences: ReaderPreferences,
        sourcePreferences: SourcePreferences,
        newMode: ReaderScheduleMode,
        newWindows: List<ReaderScheduleWindow>,
    ) {
        val windowsPreference = readerPreferences.readingScheduleWindows()
        val modePreference = readerPreferences.readingScheduleMode()
        val previousSerialized = modePreference.get() to windowsPreference.get()
        val newSerialized = ReaderScheduleStore.serializeMode(newMode) to
            ReaderScheduleStore.serializeWindows(newWindows)
        val undoEntry = if (previousSerialized != newSerialized) {
            exh.util.PreferenceUndoEntry(
                id = exh.util.PreferenceUndoEntry.newId(),
                timestamp = System.currentTimeMillis(),
                actionType = exh.util.PreferenceJournalActionType.READING_SCHEDULE,
                identityKey = "readingSchedule",
                previousValue = previousSerialized,
                expectedPostValue = newSerialized,
                readCurrent = { modePreference.get() to windowsPreference.get() },
                restore = { (mode, windows) ->
                    modePreference.set(mode)
                    windowsPreference.set(windows)
                },
            )
        } else {
            null
        }
        modePreference.set(newSerialized.first)
        windowsPreference.set(newSerialized.second)
        undoEntry?.let { exh.util.PreferenceUndoJournal.record(it) }
    }
}
