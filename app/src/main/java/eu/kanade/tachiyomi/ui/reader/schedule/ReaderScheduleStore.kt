package eu.kanade.tachiyomi.ui.reader.schedule

import java.time.DayOfWeek

// KMK v0.8.5 -->
/**
 * Pure serialize/deserialize between [ReaderSchedule] and the primitive strings stored in
 * `ReaderPreferences` — matching this repo's existing `*Store` convention (e.g.
 * `RecommendationSourcePreferenceStore`). [parseWindows] never throws: any malformed window is
 * silently dropped rather than crashing schedule restoration, and [parseMode] falls back to
 * [ReaderScheduleMode.RESTRICTED] for any unrecognized value.
 *
 * Window format: `"<weekday>,<weekday>,...:<startMinute>:<endMinute>"`, optionally followed by a
 * fourth `:<allDay 0|1>` field (KMK v0.8.7 — see [ReaderScheduleWindow.allDay]), windows joined by
 * `;`. Weekdays use [DayOfWeek.getValue] (1=MONDAY..7=SUNDAY). The fourth field is always written
 * for new entries; parsing accepts either 3 fields (pre-v0.8.7 data, `allDay` defaults to false, so
 * every existing serialized window keeps its exact old meaning) or 4 fields.
 */
object ReaderScheduleStore {

    private const val WINDOW_SEP = ";"
    private const val FIELD_SEP = ":"
    private const val DAY_SEP = ","

    fun serializeWindows(windows: List<ReaderScheduleWindow>): String = windows.joinToString(WINDOW_SEP) { w ->
        w.weekdays.map { it.value }.sorted().joinToString(DAY_SEP) + FIELD_SEP + w.startMinuteOfDay +
            FIELD_SEP + w.endMinuteOfDay + FIELD_SEP + (if (w.allDay) "1" else "0")
    }

    fun parseWindows(raw: String): List<ReaderScheduleWindow> {
        if (raw.isBlank()) return emptyList()
        return raw.split(WINDOW_SEP).mapNotNull { entry ->
            val parts = entry.split(FIELD_SEP)
            if (parts.size != 3 && parts.size != 4) return@mapNotNull null
            val weekdays = parts[0].split(DAY_SEP)
                .mapNotNull { it.trim().toIntOrNull() }
                .mapNotNull { value -> runCatching { DayOfWeek.of(value) }.getOrNull() }
                .toSet()
            val start = parts[1].toIntOrNull() ?: return@mapNotNull null
            val end = parts[2].toIntOrNull() ?: return@mapNotNull null
            // KMK v0.8.7: pre-v0.8.7 data has no fourth field -- allDay defaults to false, preserving
            // the exact old meaning of every previously-serialized window.
            val allDay = parts.getOrNull(3) == "1"
            if (!ReaderScheduleWindow.isValid(weekdays, start, end, allDay)) return@mapNotNull null
            ReaderScheduleWindow(weekdays, start, end, allDay)
        }
    }

    fun serializeMode(mode: ReaderScheduleMode): String = mode.name

    fun parseMode(raw: String?): ReaderScheduleMode =
        raw?.let { runCatching { ReaderScheduleMode.valueOf(it) }.getOrNull() } ?: ReaderScheduleMode.RESTRICTED
}
// KMK <--
