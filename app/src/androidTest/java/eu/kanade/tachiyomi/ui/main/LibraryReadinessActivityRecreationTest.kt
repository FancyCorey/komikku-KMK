package eu.kanade.tachiyomi.ui.main

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.kanade.tachiyomi.ui.library.LibraryTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// KMK F2-05.0.2 (KFC-V0.8.21-FIX2-CORRECTIVE-RECHECK-AND-RELEASE-PROGRAM) -->
/**
 * Real, device-level proof of the F2-05.0.1 lifecycle correction, closing the exact gap an
 * independent review found: [LibraryTabReadinessLifecycleTest] proves
 * [LibraryTab.resetForNewActivityInstance] is load-bearing by reflecting a stale value into the
 * private backing `MutableStateFlow` -- it never exercises a genuine same-process
 * `Activity.recreate()`. This test does, using [ActivityScenario.recreate] (a real Android
 * framework recreation, not a simulation) against the actual production [MainActivity], run
 * on-device.
 *
 * Requires this device's onboarding to already be marked complete (see
 * [exh.perf.PerformanceMeasurementDevicePrepTest]) -- otherwise [MainActivity] would route to
 * `OnboardingScreen` instead of `HomeScreen`/Library, and [LibraryTab.isInitiallyLoaded] would
 * never latch true at all. That precondition, and the structural proof that onboarding/a blocking
 * update dialog cannot itself satisfy the Library-ready predicate, are owned by
 * [HomeScreenReadinessTest] (unchanged, still valid -- Voyager's Navigator only composes
 * `navigator.lastItem`, a property this correction does not touch). This test's own, narrower
 * job is exclusively the lifecycle claim: that a prior Activity instance's readiness cannot leak
 * into a recreated instance.
 *
 * [MainActivity] is `android:launchMode="singleTop"` with no `android:configChanges` override in
 * the manifest, so [ActivityScenario.recreate] performs a genuine destroy-and-recreate of the
 * Activity instance -- the same lifecycle event a configuration change (e.g. rotation) triggers in
 * production, not merely a callback simulation.
 */
@RunWith(AndroidJUnit4::class)
class LibraryReadinessActivityRecreationTest {

    private lateinit var collectorScope: CoroutineScope

    @Before
    fun startFromAKnownCleanState() {
        // LibraryTab is a process-wide singleton; do not assume no prior test in this process
        // already latched it true.
        LibraryTab.resetForNewActivityInstance()
        collectorScope = CoroutineScope(Dispatchers.Default + Job())
    }

    @After
    fun stopCollecting() {
        collectorScope.cancel()
    }

    @Test
    fun recreatedActivityInstanceDoesNotInheritStaleReadinessAndReachesItsOwnReadyState() {
        // Records every DISTINCT value LibraryTab.isInitiallyLoaded ever takes, in order, for the
        // whole life of this test -- StateFlow only emits on genuine value changes, so this is the
        // ordered true/false/true.../ transition history, not a pair of point-in-time snapshots
        // vulnerable to racing a fast transition that happens between two separate checks. One
        // collector runs for the entire test, across the recreation, so nothing it emits can be
        // missed by starting a second collector too late.
        val observedTransitions = Collections.synchronizedList(mutableListOf<Boolean>())
        val trueCount = java.util.concurrent.atomic.AtomicInteger(0)
        val firstReadyLatch = CountDownLatch(1)
        val secondReadyLatch = CountDownLatch(1)
        collectorScope.launch {
            LibraryTab.isInitiallyLoaded.collect { value ->
                observedTransitions.add(value)
                if (value) {
                    when (trueCount.incrementAndGet()) {
                        1 -> firstReadyLatch.countDown()
                        2 -> secondReadyLatch.countDown()
                    }
                }
            }
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // 1. The FIRST Activity instance must genuinely reach Library-ready on its own.
            assertTrue(
                "first Activity instance never reached Library-ready within 30s",
                firstReadyLatch.await(30, TimeUnit.SECONDS),
            )
            assertTrue(LibraryTab.isInitiallyLoaded.value)

            // 2. A real, framework-driven same-process recreation -- not a simulation.
            scenario.recreate()

            // 3. The recreated instance must reach ITS OWN Library-ready state -- the second
            //    distinct `true` transition, produced by the SAME collector that has been running
            //    since before recreate() was called.
            assertTrue(
                "recreated Activity instance never reached its OWN Library-ready state within 30s",
                secondReadyLatch.await(30, TimeUnit.SECONDS),
            )

            // The definitive proof: somewhere in the full ordered transition history, a `false`
            // appears AFTER the first `true` and BEFORE the second `true`. A stale-inheriting bug
            // would instead show two adjacent `true` values with no intervening `false` -- exactly
            // the defect LibraryTabReadinessLifecycleTest's own negative control reproduces on the
            // host; this proves the same fix is load-bearing on a real device recreation too.
            val transitionsSnapshot = observedTransitions.toList()
            val firstTrueIndex = transitionsSnapshot.indexOf(true)
            assertTrue(
                "expected at least one observed `true` transition for the first instance",
                firstTrueIndex >= 0,
            )
            val sawFalseAfterFirstReady = transitionsSnapshot.drop(firstTrueIndex + 1).any { !it }
            assertTrue(
                "recreated Activity instance's readiness signal never revisited false after the " +
                    "prior instance's ready transition -- the reset is not load-bearing on a real " +
                    "device recreation. Full transition history: $transitionsSnapshot",
                sawFalseAfterFirstReady,
            )

            // Cross-check against the pure predicate this whole mechanism feeds: once the
            // recreated instance is ready, the SAME production predicate used by
            // MainActivity's own ReportDrawnWhen call site must also agree.
            assertEquals(true, LibraryTab.isInitiallyLoaded.value)
        }
    }
}
// KMK <--
