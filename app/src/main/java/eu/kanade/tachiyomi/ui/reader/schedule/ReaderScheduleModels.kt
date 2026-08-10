package eu.kanade.tachiyomi.ui.reader.schedule

import java.time.DayOfWeek

// KMK v0.8.5 -->
/**
 * Pure data model for the optional local reading schedule. See [ReaderScheduleResolver].
 *
 * A schedule has exactly one [ReaderScheduleMode] applied to every window it contains.
 * uses one mode per schedule to keep overlap handling deterministic: windows of the same mode
 * simply OR together (any match is a match), so there is no cross-window priority to define.
 */
enum class ReaderScheduleMode {
    /** Reading is allowed only inside a matching window; restricted everywhere else. */
    ALLOWED,

    /** Reading is restricted inside a matching window; allowed everywhere else. */
    RESTRICTED,
}

/**
 * One recurring local time window, attached to the [weekdays] it *starts* on.
 *
 * [startMinuteOfDay]/[endMinuteOfDay] are minutes since local midnight (0..1439). A window where
 * `endMinuteOfDay <= startMinuteOfDay` is a midnight-crossing window (e.g. 22:00-06:00) — this is
 * a normal, valid case, not an error; see [ReaderScheduleResolver] for exactly how the crossing
 * portion is attributed to the following day.
 */
data class ReaderScheduleWindow(
    val weekdays: Set<DayOfWeek>,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    // KMK v0.8.7: explicit whole-day representation. When true, this window matches
    // every minute of every selected weekday, regardless of [startMinuteOfDay]/[endMinuteOfDay] (kept
    // at 0/0 by convention for a whole-day window, but never read when [allDay] is true — see
    // ReaderScheduleResolver.windowMatches). This is a distinct, unambiguous flag rather than
    // overloading equal start/end times; this is a deliberate, tested model distinction.
    val allDay: Boolean = false,
) {
    val crossesMidnight: Boolean get() = !allDay && endMinuteOfDay <= startMinuteOfDay

    companion object {
        const val MINUTES_PER_DAY = 24 * 60

        /** True when every field is in-range and non-degenerate; used to drop corrupt entries rather than crash. */
        fun isValid(weekdays: Set<DayOfWeek>, startMinuteOfDay: Int, endMinuteOfDay: Int, allDay: Boolean = false): Boolean =
            weekdays.isNotEmpty() &&
                startMinuteOfDay in 0 until MINUTES_PER_DAY &&
                endMinuteOfDay in 0 until MINUTES_PER_DAY &&
                (allDay || startMinuteOfDay != endMinuteOfDay)
    }
}

data class ReaderSchedule(
    val enabled: Boolean = false,
    val mode: ReaderScheduleMode = ReaderScheduleMode.RESTRICTED,
    val windows: List<ReaderScheduleWindow> = emptyList(),
)

enum class ReaderScheduleResult {
    /** The schedule is off, or on but has no valid windows — reading is never restricted. */
    DISABLED,
    ALLOWED,
    RESTRICTED,
}
// KMK <--
