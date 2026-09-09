package exh.perf

import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.kanade.domain.base.BasePreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK F2-05 (KFC-V0.8.21-FIX2-CORRECTIVE-RECHECK-AND-RELEASE-PROGRAM) -->
/**
 * One-shot device prep for the constrained-device performance measurement pass: marks onboarding
 * as already complete so a device seeded directly via [PerformanceFixtureDeviceSeedTest] (which
 * writes library rows straight through repositories, bypassing the onboarding wizard entirely)
 * launches into the real Library/For You surfaces being measured instead of the first-run wizard --
 * a confound this receipt's own first cold-start measurement hit and disclosed.
 *
 * KMK F2-05.0 correction: an independent review correctly found the prior version's preparation
 * write did not reliably survive a cold start. Root cause, confirmed by reading source: this test
 * used to call `.set(true)`, which -- per [tachiyomi.core.common.preference.AndroidPreference.set]'s
 * own contract -- writes via `SharedPreferences.Editor.apply()`, an asynchronous, fire-and-forget
 * write queued on a background handler. `adb shell am instrument` runs this test in the app's own
 * process and terminates that process shortly after the test method returns; `apply()`'s pending
 * write is not guaranteed to have reached disk by then, so it could be silently lost -- exactly the
 * confound this receipt disclosed. This test now calls `.commit(true)` instead, which blocks this
 * method until the write is durably persisted before it (and the instrumentation process) can exit.
 * See [tachiyomi.core.common.preference.Preference.commit]'s own KDoc for why this distinction
 * exists and why ordinary UI code (e.g. [eu.kanade.tachiyomi.ui.more.OnboardingScreen]'s own
 * real completion write) must keep using `set()`, not `commit()`.
 *
 * 2026-08-27 correction (reopened by independent review): `.commit(true)` previously discarded its
 * own return value, so this test could report success even when the underlying
 * `SharedPreferences.Editor.commit()` write actually failed -- silently defeating the fail-closed
 * guarantee this device-prep step exists to provide. `commit()` now returns the real `Boolean`
 * result, and this test asserts it: a `false` result fails this test loudly instead of letting a
 * seeded device silently launch into the onboarding wizard during the actual measurement run.
 *
 * Run once per freshly seeded measurement device:
 * ```
 * adb shell am instrument -w -e class exh.perf.PerformanceMeasurementDevicePrepTest \
 *   app.komikku.dev.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 */
@RunWith(AndroidJUnit4::class)
class PerformanceMeasurementDevicePrepTest {

    @Test
    fun markOnboardingComplete() = runBlocking {
        val committed = Injekt.get<BasePreferences>().shownOnboardingFlow().commit(true)
        assertTrue(
            "onboarding-complete preference commit reported failure -- device prep did not " +
                "durably persist the value before this instrumentation process can exit",
            committed,
        )
    }
}
// KMK <--
