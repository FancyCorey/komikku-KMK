package eu.kanade.tachiyomi.ui.reader.timer

// KMK v0.8.4 -->
/** Injectable monotonic clock so [ReaderTimerReducer] and its coordinator are unit-testable without real elapsed time. */
fun interface ReaderTimerClock {
    /** A monotonic timestamp in milliseconds (e.g. `SystemClock.elapsedRealtime()`), never wall-clock. */
    fun nowMonotonicMs(): Long
}

/** Real-device clock backed by [android.os.SystemClock.elapsedRealtime], immune to wall-clock/time-zone/DST changes. */
val SystemReaderTimerClock = ReaderTimerClock { android.os.SystemClock.elapsedRealtime() }
// KMK <--
