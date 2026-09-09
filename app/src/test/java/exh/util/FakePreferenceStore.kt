package exh.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

// KMK v0.8.19 -->
/**
 * Minimal in-memory [PreferenceStore]/[Preference] fake for interactor-level tests that need a real
 * [eu.kanade.domain.source.service.SourcePreferences] without a database or Android `SharedPreferences`.
 * Only the operations [EvaluationModeUndoServiceRestoreTest] actually exercises are implemented with
 * real semantics (get/set/changes backed by a [MutableStateFlow]); unused [PreferenceStore] methods are
 * still implemented generically since the interface requires them.
 */
class FakePreferenceStore : PreferenceStore {

    var failWrites: Boolean = false

    private val flows = mutableMapOf<String, MutableStateFlow<Any?>>()

    /**
     * Fully resets this store, including **key presence**.
     *
     * `Preference.delete()` deliberately only restores the default *value*; the key stays in [flows], so
     * `isSet()` remains `true` afterwards. That is faithful to how a real store behaves for a written-then
     * -reset key, but it means `delete()` cannot restore the "never written" state that
     * `isSet()`-guarded production code branches on.
     *
     * Injekt is a process-global singleton, so a test class that binds [PreferenceStore] shares one
     * instance with every other test class in the JVM. Without this, such a class can only stay correct by
     * *overwriting* the global binding, which breaks whichever other class resolved first. Calling this in
     * `@BeforeEach` lets classes share the instance Injekt hands out and still start from a clean slate.
     */
    fun clearAll() {
        flows.clear()
        failWrites = false
    }
    // KMK <--

    @Suppress("UNCHECKED_CAST")
    private fun <T> flowFor(key: String, defaultValue: T): MutableStateFlow<T> =
        flows.getOrPut(key) { MutableStateFlow(defaultValue) } as MutableStateFlow<T>

    inner class FakePreference<T> internal constructor(private val key: String, private val defaultValue: T) : Preference<T> {
        override fun key() = key
        override fun get(): T = flowFor(key, defaultValue).value
        override fun set(value: T) {
            check(!failWrites) { "test-injected preference write failure" }
            flowFor(key, defaultValue).value = value
        }
        // KMK F2-05.0: this fake has no disk I/O to defer, so commit() writes identically to set().
        // 2026-08-27 correction: commit() now returns Boolean. set() throws (rather than returning
        // false) when failWrites injects a failure, so any return from this line means the write
        // itself succeeded -- this unconditionally reports true on that path.
        override fun commit(value: T): Boolean {
            set(value)
            return true
        }
        override fun isSet(): Boolean = flows.containsKey(key)
        override fun delete() {
            check(!failWrites) { "test-injected preference delete failure" }
            flowFor(key, defaultValue).value = defaultValue
        }
        override fun defaultValue(): T = defaultValue
        override fun changes(): Flow<T> = flowFor(key, defaultValue)
        override fun stateIn(scope: CoroutineScope): StateFlow<T> = flowFor(key, defaultValue)
    }

    override fun getString(key: String, defaultValue: String) = FakePreference(key, defaultValue)
    override fun getLong(key: String, defaultValue: Long) = FakePreference(key, defaultValue)
    override fun getInt(key: String, defaultValue: Int) = FakePreference(key, defaultValue)
    override fun getFloat(key: String, defaultValue: Float) = FakePreference(key, defaultValue)
    override fun getBoolean(key: String, defaultValue: Boolean) = FakePreference(key, defaultValue)
    override fun getStringSet(key: String, defaultValue: Set<String>) = FakePreference(key, defaultValue)

    override fun <T> getObjectFromString(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ) = FakePreference(key, defaultValue)

    override fun <T> getObjectFromInt(
        key: String,
        defaultValue: T,
        serializer: (T) -> Int,
        deserializer: (Int) -> T,
    ) = FakePreference(key, defaultValue)

    override fun getAll(): Map<String, *> = flows.mapValues { it.value.value as Any }
}
// KMK <--
