package eu.kanade.tachiyomi.ui.reader.schedule

import java.time.DayOfWeek
import java.time.LocalDateTime

// KMK v0.8.5 -->
/**
 * Pure resolver: `(schedule, local date/time) -> ALLOWED | RESTRICTED | DISABLED`. No Android
 * dependencies, no I/O, no wall-clock reads of its own — the caller always supplies `at`
 * (typically `LocalDateTime.now()` at the moment of evaluation), so this function is fully
 * deterministic and time-zone/DST-safe: DST transitions only ever affect what `LocalDateTime.now()`
 * itself returns, never this resolver's logic. Never call this once and cache the result across a
 * DST boundary — always re-resolve at each evaluation point (reader open/resume/background-return
 * /schedule-edit), exactly as the plan requires.
 *
 * Kept entirely separate from [eu.kanade.tachiyomi.ui.reader.timer.ReaderTimerReducer] — the
 * active-reading timer's state machine has no schedule-specific branches; the reader wires the two
 * together at a higher level (see `ReaderViewModel`) by feeding a RESTRICTED verdict into a second,
 * independent instance of the same reusable [eu.kanade.tachiyomi.ui.reader.timer.ReaderTimerCoordinator]
 * so a restricted window gets the identical non-destructive chapter-grace behavior as timer expiry,
 * without adding any schedule awareness to the reducer itself.
 */
object ReaderScheduleResolver {

    fun resolve(schedule: ReaderSchedule, at: LocalDateTime): ReaderScheduleResult {
        if (!schedule.enabled) return ReaderScheduleResult.DISABLED
        val validWindows = schedule.windows.filter {
            ReaderScheduleWindow.isValid(it.weekdays, it.startMinuteOfDay, it.endMinuteOfDay, it.allDay)
        }
        if (validWindows.isEmpty()) return ReaderScheduleResult.DISABLED

        val dayOfWeek = at.dayOfWeek
        val minuteOfDay = at.hour * 60 + at.minute
        val matchesAnyWindow = validWindows.any { windowMatches(it, dayOfWeek, minuteOfDay) }

        return when (schedule.mode) {
            ReaderScheduleMode.ALLOWED -> if (matchesAnyWindow) ReaderScheduleResult.ALLOWED else ReaderScheduleResult.RESTRICTED
            ReaderScheduleMode.RESTRICTED -> if (matchesAnyWindow) ReaderScheduleResult.RESTRICTED else ReaderScheduleResult.ALLOWED
        }
    }

    /**
     * @param dayOfWeek the day [window] starts on is checked directly; for a midnight-crossing
     * window the following day's early-morning spillover is checked via `dayOfWeek.minus(1)`,
     * which correctly wraps SUNDAY -> MONDAY thanks to [DayOfWeek]'s cyclic arithmetic.
     */
    private fun windowMatches(window: ReaderScheduleWindow, dayOfWeek: DayOfWeek, minuteOfDay: Int): Boolean {
        // KMK v0.8.7: a whole-day window matches every minute of every selected weekday, regardless
        // of startMinuteOfDay/endMinuteOfDay (kept at 0/0 by convention but never read here).
        if (window.allDay) {
            return dayOfWeek in window.weekdays
        }
        if (!window.crossesMidnight) {
            return dayOfWeek in window.weekdays &&
                minuteOfDay >= window.startMinuteOfDay &&
                minuteOfDay < window.endMinuteOfDay
        }
        val startsToday = dayOfWeek in window.weekdays && minuteOfDay >= window.startMinuteOfDay
        val spillsFromYesterday = dayOfWeek.minus(1) in window.weekdays && minuteOfDay < window.endMinuteOfDay
        return startsToday || spillsFromYesterday
    }
}
// KMK <--
