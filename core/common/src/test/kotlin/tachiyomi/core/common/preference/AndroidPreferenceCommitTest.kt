package tachiyomi.core.common.preference

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.emptyFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// KMK F2-05.0 (KFC-V0.8.21-FIX2-CORRECTIVE-RECHECK-AND-RELEASE-PROGRAM) -->
/**
 * Direct, host-testable proof of the exact mechanism distinction [Preference.set] and
 * [Preference.commit] rely on: [android.content.SharedPreferences.Editor.apply] is asynchronous
 * (queued on a background handler, no durability guarantee before the caller continues) while
 * [android.content.SharedPreferences.Editor.commit] blocks until the write is durably persisted.
 * This is the confirmed root cause of the F2-05.0 defect: `PerformanceMeasurementDevicePrepTest`
 * (an `adb shell am instrument` one-shot device-prep script) called `.set(true)` on
 * `shownOnboardingFlow()`, whose `apply()`-based write was not guaranteed to reach disk before the
 * instrumentation process exited, silently losing the preparation write on some runs.
 *
 * `android.content.SharedPreferences` and `SharedPreferences.Editor` are plain interfaces with no
 * real Android framework implementation to stub -- mockk can fully mock them in a normal host JVM
 * test, without Robolectric or a device, letting this test verify the EXACT `Editor` method each
 * write path calls, not merely that the written value round-trips correctly (which a fake/in-memory
 * store could satisfy trivially without proving anything about durability timing).
 */
class AndroidPreferenceCommitTest {

    private fun preferenceWithMockedEditor(): Pair<AndroidPreference.BooleanPrimitive, SharedPreferences.Editor> {
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val preferences = mockk<SharedPreferences>()
        every { preferences.edit() } returns editor

        val preference = AndroidPreference.BooleanPrimitive(preferences, emptyFlow(), "test_key", false)
        return preference to editor
    }

    @Test
    fun `set() writes the value then calls Editor-apply(), never Editor-commit()`() {
        val (preference, editor) = preferenceWithMockedEditor()

        preference.set(true)

        verify(exactly = 1) { editor.putBoolean("test_key", true) }
        verify(exactly = 1) { editor.apply() }
        verify(exactly = 0) { editor.commit() }
    }

    @Test
    fun `commit() writes the value then calls Editor-commit(), never Editor-apply()`() {
        val (preference, editor) = preferenceWithMockedEditor()
        every { editor.commit() } returns true

        val result = preference.commit(true)

        verify(exactly = 1) { editor.putBoolean("test_key", true) }
        verify(exactly = 1) { editor.commit() }
        verify(exactly = 0) { editor.apply() }
        assertEquals(true, result)
    }

    // KMK F2-05.0 correction (2026-08-27, reopened by independent review): the original
    // implementation routed through androidx.core.content.edit(commit = true, ...), whose own
    // Unit-returning signature discarded Editor.commit()'s real Boolean result -- so a failed write
    // could never actually be observed by a caller. This proves the fix: when the mocked
    // Editor.commit() reports failure, AndroidPreference.commit() must propagate that exact `false`
    // result rather than silently reporting success.
    @Test
    fun `commit() returns false when Editor-commit() reports a failed write`() {
        val (preference, editor) = preferenceWithMockedEditor()
        every { editor.commit() } returns false

        val result = preference.commit(true)

        verify(exactly = 1) { editor.putBoolean("test_key", true) }
        verify(exactly = 1) { editor.commit() }
        assertEquals(false, result)
    }

    // Proves set() and commit() write the identical value through the identical Editor method
    // (putBoolean with the same key/value) -- the ONLY difference is the flush call at the end, not
    // a divergent write path that could itself introduce a correctness gap between the two.
    @Test
    fun `set() and commit() write via the identical putBoolean call, differing only in the flush method`() {
        val (setPreference, setEditor) = preferenceWithMockedEditor()
        val (commitPreference, commitEditor) = preferenceWithMockedEditor()

        setPreference.set(true)
        commitPreference.commit(true)

        verify(exactly = 1) { setEditor.putBoolean("test_key", true) }
        verify(exactly = 1) { commitEditor.putBoolean("test_key", true) }
    }
}
// KMK <--
