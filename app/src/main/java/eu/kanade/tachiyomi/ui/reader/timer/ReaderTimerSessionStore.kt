package eu.kanade.tachiyomi.ui.reader.timer

import android.app.Application
import android.content.SharedPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Keeps paused reader-timer sessions available when a ReaderActivity is finished and the same
 * manga/chapter is opened again during the current app process. SavedStateHandle covers recreation
 * of one activity, but it is intentionally not retained after a normal activity finish.
 */
object ReaderTimerSessionStore {

    private const val MAX_SESSIONS = 16
    private const val PREFS_NAME = "reader_timer_sessions"
    private const val FIELD_SEPARATOR = "|"
    private val sessions = LinkedHashMap<String, ReaderTimerSession>(MAX_SESSIONS, 0.75f, true)
    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences(PREFS_NAME, 0)
    }

    @Synchronized
    fun get(key: String): ReaderTimerSession? {
        sessions[key]?.let { return it }
        val encoded = preferences.getString(key.toPreferenceKey(), null) ?: return null
        return decode(encoded)?.also { sessions[key] = it }
    }

    @Synchronized
    fun put(key: String, session: ReaderTimerSession) {
        if (session.phase == ReaderTimerPhase.IDLE) {
            remove(key)
            return
        }
        sessions[key] = session
        preferences.edit().putString(key.toPreferenceKey(), encode(session)).apply()
        while (sessions.size > MAX_SESSIONS) {
            remove(sessions.entries.first().key)
        }
    }

    @Synchronized
    fun remove(key: String) {
        sessions.remove(key)
        preferences.edit().remove(key.toPreferenceKey()).apply()
    }

    private fun String.toPreferenceKey(): String = "session_$this"

    private fun encode(session: ReaderTimerSession): String {
        val encoded = ReaderTimerStateCodec.encode(session)
        return listOf(
            encoded.phase,
            encoded.totalMs,
            encoded.elapsedMs,
            encoded.warningMinutesCsv,
            encoded.finishChapter,
            encoded.allowExtra,
            encoded.firedWarningsCsv,
            encoded.extraUsed,
            encoded.pausedFrom.orEmpty(),
            encoded.pauseReason.orEmpty(),
        ).joinToString(FIELD_SEPARATOR)
    }

    private fun decode(value: String): ReaderTimerSession? {
        val fields = value.split(FIELD_SEPARATOR)
        if (fields.size != 10) return null
        return ReaderTimerStateCodec.decode(
            phase = fields[0],
            totalMs = fields[1].toLongOrNull(),
            elapsedMs = fields[2].toLongOrNull(),
            warningMinutesCsv = fields[3],
            finishChapter = fields[4].toBooleanStrictOrNull(),
            allowExtra = fields[5].toBooleanStrictOrNull(),
            firedWarningsCsv = fields[6],
            extraUsed = fields[7].toBooleanStrictOrNull(),
            pausedFrom = fields[8].ifEmpty { null },
            pauseReason = fields[9].ifEmpty { null },
        ).takeUnless { it.phase == ReaderTimerPhase.IDLE && fields[0] != ReaderTimerPhase.IDLE.name }
    }
}
