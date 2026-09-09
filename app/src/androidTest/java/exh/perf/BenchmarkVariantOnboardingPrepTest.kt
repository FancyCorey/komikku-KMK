package exh.perf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import eu.kanade.presentation.more.onboarding.ONBOARDING_ACCEPT_BUTTON_TEST_TAG
import eu.kanade.tachiyomi.ui.library.LibraryTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

// KMK F2-05.0.2 (KFC-V0.8.21-FIX2-CORRECTIVE-RECHECK-AND-RELEASE-PROGRAM) -->
/**
 * One-shot device prep for the `benchmark` build type target measured by
 * `macrobenchmark/src/main/java/tachiyomi/macrobenchmark/StartupBenchmark.kt`.
 *
 * [PerformanceMeasurementDevicePrepTest] cannot be reused here: it durably marks onboarding
 * complete via a real in-process `Injekt`/`Preference.commit()` call, but that requires this
 * instrumentation's own `Instrumentation.targetPackage` (`app.komikku.dev`, fixed by AGP to match
 * this androidTest APK's `debug`-build-type target) to equal the app being prepared -- and it does
 * not: this project has no `benchmark`-build-type `androidTest` variant (confirmed via `./gradlew
 * :app:tasks`: only `debug`-build-type AndroidTest tasks exist), so no instrumentation APK can
 * ever declare `app.komikku.benchmark` as its own target package without a build-config change
 * this narrow lane does not own.
 *
 * `UiDevice`/`UiAutomator`, however, operates at the OS accessibility-service level, not scoped to
 * this instrumentation's own target package -- the exact same cross-app capability
 * `KmkUiAutomationCaptureTest` already relies on. This test uses it to launch `app.komikku.benchmark`
 * directly and walk its REAL onboarding UI to completion, using the exact stable
 * `ONBOARDING_ACCEPT_BUTTON_TEST_TAG` resource-id now exposed by
 * `MainActivity`'s `Modifier.semantics { testTagsAsResourceId = true }` wiring -- itself live proof
 * that wiring works, not merely a convenience.
 *
 * 2026-08-27, two corrections found while validating this test on-device:
 *
 * 1. The first version only checked that the onboarding button "stopped appearing" -- also true
 *    when it is silently obscured and never clickable at all. On a genuinely fresh install,
 *    `MainActivity` shows a `WhatsNewDialog` ("Updated to Komikku FC ...") layered ON TOP of the
 *    onboarding wizard on first launch, blocking the underlying accept button entirely; that
 *    version reported false success (0 clicks, which its own check treated as "already complete")
 *    while onboarding was never actually touched. Fixed by dismissing that dialog first.
 *
 * 2. Verifying Library-ready via [LibraryTab.LIBRARY_READY_CONTENT_TEST_TAG] immediately after
 *    completion is NOT proof the write survived -- exactly this program's own recurring
 *    `apply()`-vs-`commit()` lesson: `OnboardingScreen`'s real completion write uses `.set(true)`
 *    (`SharedPreferences.Editor.apply()`), which updates the in-memory value SYNCHRONOUSLY (so
 *    Library correctly renders live, in the SAME process, immediately) but flushes to disk
 *    ASYNCHRONOUSLY. A screenshot taken moments later, after force-stopping and relaunching, showed
 *    onboarding again -- the process died before the pending disk write completed. This version
 *    proves durability explicitly: after first reaching Library, it force-stops and relaunches the
 *    app AGAIN within the same test, and asserts Library is reached a SECOND time on that fresh
 *    process -- not merely trusting the first, still-live in-memory state.
 */
@RunWith(AndroidJUnit4::class)
class BenchmarkVariantOnboardingPrepTest {

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    /**
     * `am force-stop` alone races with an immediately-following `am start` -- without explicit
     * verification, the "relaunch" can resume the SAME still-dying process/task instead of a
     * genuine cold restart, which would make a durability check pass even when nothing was
     * actually persisted (confirmed on-device: an early version of this test's own relaunch check
     * passed while `/data/data/app.komikku.benchmark/shared_prefs/` contained no onboarding key at
     * all). `MacrobenchmarkScope.killProcess()` documents doing exactly this same explicit wait for
     * real Macrobenchmark runs; this mirrors that discipline here.
     */
    private fun forceStopAndVerifyDead(packageName: String) {
        device.executeShellCommand("am force-stop $packageName")
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            val pid = device.executeShellCommand("pidof $packageName").trim()
            if (pid.isEmpty()) return
            Thread.sleep(200)
        }
        error("$packageName still has a live process after force-stop + 10s wait -- cannot prove a genuine cold restart")
    }

    @Test
    fun completeOnboardingForBenchmarkVariant() {
        val benchmarkPackage = "app.komikku.benchmark"
        forceStopAndVerifyDead(benchmarkPackage)
        device.executeShellCommand(
            "am start -n $benchmarkPackage/eu.kanade.tachiyomi.ui.main.MainActivity",
        )

        // Dismiss the first-launch WhatsNewDialog if present -- it has no stable test tag of its
        // own (out of this lane's scope; only LIBRARY_READY_CONTENT_TEST_TAG needed one), so this
        // uses its visible "OK" button text. Acceptable for a one-shot device-prep utility script,
        // unlike the onboarding/Library selectors this program actually ships stable tags for.
        device.wait(Until.findObject(By.text("OK")), 5_000)?.click()
        device.waitForIdle()

        // Walk the shared Next/Finish button across every onboarding step. It carries the SAME
        // test tag throughout (see OnboardingScreen's own KDoc), so repeatedly finding and
        // clicking it -- until it stops appearing -- deterministically completes the flow
        // regardless of step count or locale text.
        var clicks = 0
        val maxClicks = 10 // generous upper bound; the real flow has 4 steps
        while (clicks < maxClicks) {
            val button = device.wait(Until.findObject(By.res(ONBOARDING_ACCEPT_BUTTON_TEST_TAG)), 5_000)
                ?: break // no longer present
            button.click()
            clicks++
            device.waitForIdle()
        }

        // Library must be genuinely reached in THIS live process -- not merely "the onboarding
        // button is no longer visible" (also true if it was obscured the whole time, as the
        // WhatsNewDialog regression above proved). Also confirm the foreground package is
        // genuinely the benchmark variant, not a stale By.res() match against a DIFFERENT app's
        // own (already onboarding-complete) window -- UiDevice/By.res() operate at the OS level
        // and are not inherently scoped to any one package.
        val reachedLibraryFirstTime = device.wait(
            Until.hasObject(By.res(LibraryTab.LIBRARY_READY_CONTENT_TEST_TAG)),
            30_000,
        )
        assertNotNull(
            "onboarding prep did not reach Library-ready within 30s after $clicks accept-button " +
                "clicks -- either a blocking dialog was not dismissed, or the flow is genuinely " +
                "stuck",
            reachedLibraryFirstTime,
        )
        assertEquals(
            "LIBRARY_READY_CONTENT_TEST_TAG was found, but the foreground app was not " +
                "$benchmarkPackage -- By.res() likely matched a DIFFERENT app's own Library " +
                "window (e.g. app.komikku.dev, left ready by an earlier test this session), not " +
                "this benchmark variant's own genuinely-reached Library",
            benchmarkPackage,
            device.currentPackageName,
        )

        // Durability proof: OnboardingScreen's real completion write is `.set(true)` -- async
        // apply() -- which updates the in-memory value immediately (explaining why Library
        // rendered live, above) but flushes to disk asynchronously. Relaunching immediately after a
        // force-stop proves whether that write actually survived the process dying, rather than
        // trusting the still-live in-memory state.
        forceStopAndVerifyDead(benchmarkPackage)
        device.executeShellCommand(
            "am start -n $benchmarkPackage/eu.kanade.tachiyomi.ui.main.MainActivity",
        )
        val reachedLibraryAfterRelaunch = device.wait(
            Until.hasObject(By.res(LibraryTab.LIBRARY_READY_CONTENT_TEST_TAG)),
            30_000,
        )
        assertNotNull(
            "Library was reached once, but a fresh relaunch after force-stop landed back on " +
                "onboarding -- the completion write did not durably persist before the process " +
                "died. This is the exact async apply()-vs-commit() durability gap this program's " +
                "own Preference.commit() correction exists to close; OnboardingScreen's own " +
                "real-UI write intentionally stays on set()/apply() (see that method's KDoc), so " +
                "this device-prep script must instead wait longer for the pending write to flush, " +
                "not switch OnboardingScreen itself to commit().",
            reachedLibraryAfterRelaunch,
        )
        assertEquals(
            "LIBRARY_READY_CONTENT_TEST_TAG matched after relaunch, but the foreground app was " +
                "not $benchmarkPackage",
            benchmarkPackage,
            device.currentPackageName,
        )
    }
}
// KMK <--
