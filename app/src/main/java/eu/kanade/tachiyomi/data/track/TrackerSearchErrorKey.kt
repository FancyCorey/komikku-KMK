package eu.kanade.tachiyomi.data.track

import java.io.IOException

/**
 * Bounded, localizable classification for tracker-search failures.
 * It carries no external error text and is never persisted.
 */
enum class TrackerSearchErrorKey {
    /** Connectivity or transport failure -- actionable by the user (retry once back online). */
    NoNetwork,

    /** Anything else, including server-controlled HTTP error bodies. Never rendered verbatim. */
    Unknown,
    ;

    companion object {
        /**
         * @param throwable the failure captured by the screen model, or `null` when a `Result.failure`
         * somehow carries none.
         */
        fun from(throwable: Throwable?): TrackerSearchErrorKey = when (throwable) {
            // IOException covers UnknownHostException, SocketTimeoutException and ConnectException,
            // which are the genuinely actionable "you are offline" cases.
            is IOException -> NoNetwork
            else -> Unknown
        }
    }
}
