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

    private val flows = mutableMapOf<String, MutableStateFlow<Any?>>()

    @Suppress("UNCHECKED_CAST")
    private fun <T> flowFor(key: String, defaultValue: T): MutableStateFlow<T> =
        flows.getOrPut(key) { MutableStateFlow(defaultValue) } as MutableStateFlow<T>

    inner class FakePreference<T> internal constructor(private val key: String, private val defaultValue: T) : Preference<T> {
        override fun key() = key
        override fun get(): T = flowFor(key, defaultValue).value
        override fun set(value: T) {
            flowFor(key, defaultValue).value = value
        }
        override fun isSet(): Boolean = flows.containsKey(key)
        override fun delete() {
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
