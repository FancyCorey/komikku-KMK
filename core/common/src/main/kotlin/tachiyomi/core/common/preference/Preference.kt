package tachiyomi.core.common.preference

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface Preference<T> {

    fun key(): String

    fun get(): T

    fun set(value: T)

    /**
     * Writes [value] and blocks the calling thread until it is durably persisted to the backing
     * store, instead of [set]'s normal fire-and-forget write.
     *
     * KMK F2-05.0 (KFC-V0.8.21-FIX2-CORRECTIVE-RECHECK-AND-RELEASE-PROGRAM): added because
     * [AndroidPreference.set] writes via `SharedPreferences.Editor.apply()` (asynchronous, queued
     * on a background handler) -- correct and necessary for ordinary UI-thread callers, but a real
     * confirmed defect for [exh.perf.PerformanceMeasurementDevicePrepTest]'s one-shot
     * `adb shell am instrument` device-prep script: that instrumentation process can exit (ending
     * the process the pending `apply()` write was queued on) before the write actually reaches
     * disk, silently losing it -- exactly the "existing preparation write did not survive a cold
     * start" defect this correction closes. [commit] exists for exactly that class of caller:
     * short-lived processes (test/tooling device prep, not app UI code) that need a durability
     * guarantee before they exit, not a general replacement for [set]. Ordinary production call
     * sites (e.g. [eu.kanade.tachiyomi.ui.more.OnboardingScreen]'s own real user-facing completion
     * write) must keep using [set] -- blocking the main thread on disk I/O is a real anti-pattern
     * this method must never be reached for from a UI callback.
     *
     * KMK F2-05.0 correction (2026-08-27, reopened by independent review): returns [Boolean] --
     * `true` only when the value was confirmed durably persisted, `false` on a reported write
     * failure -- mirroring `SharedPreferences.Editor.commit()`'s own documented contract exactly.
     * A caller that ignores this return value (as the original implementation of this method
     * itself did, by wrapping a Unit-returning convenience helper) cannot actually tell a failed
     * write from a successful one, defeating the entire reason [commit] exists over [set]. Callers
     * that need a durability guarantee (see [exh.perf.PerformanceMeasurementDevicePrepTest]) must
     * check this result and fail closed rather than assume success.
     */
    fun commit(value: T): Boolean

    fun isSet(): Boolean

    fun delete()

    fun defaultValue(): T

    fun changes(): Flow<T>

    fun stateIn(scope: CoroutineScope): StateFlow<T>

    companion object {
        /**
         * A preference that should not be exposed in places like backups without user consent.
         */
        fun isPrivate(key: String): Boolean {
            return key.startsWith(PRIVATE_PREFIX)
        }
        fun privateKey(key: String): String {
            return "$PRIVATE_PREFIX$key"
        }

        /**
         * A preference used for internal app state that isn't really a user preference
         * and therefore should not be in places like backups.
         */
        fun isAppState(key: String): Boolean {
            return key.startsWith(APP_STATE_PREFIX)
        }
        fun appStateKey(key: String): String {
            return "$APP_STATE_PREFIX$key"
        }

        private const val APP_STATE_PREFIX = "__APP_STATE_"
        private const val PRIVATE_PREFIX = "__PRIVATE_"
    }
}

inline fun <reified T, R : T> Preference<T>.getAndSet(crossinline block: (T) -> R) = set(
    block(get()),
)

operator fun <T> Preference<Set<T>>.plusAssign(item: T) {
    set(get() + item)
}

operator fun <T> Preference<Set<T>>.plusAssign(items: Iterable<T>) {
    set(get() + items)
}

operator fun <T> Preference<Set<T>>.minusAssign(item: T) {
    set(get() - item)
}

fun Preference<Boolean>.toggle(): Boolean {
    set(!get())
    return get()
}
