package eu.kanade.tachiyomi.ui.library

import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.main.isHomeScreenTrulyDrawn
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK F2-05.0.1 (KFC-V0.8.21-FIX2-CORRECTIVE-RECHECK-AND-RELEASE-PROGRAM) -->
/**
 * Adversarial proof for the lifecycle correction described on
 * [LibraryTab.resetForNewActivityInstance]'s own KDoc: [LibraryTab.isInitiallyLoaded] lives on a
 * process-lifetime Kotlin `object`, so without an explicit per-Activity-instance reset it would
 * silently survive a same-process Activity recreation (confirmed root cause, reading the actual
 * AndroidX source of `ReportDrawnWhen`/`FullyDrawnReporter`: the predicate is observed via a plain
 * `SnapshotStateObserver` read at composition time and `Activity.reportFullyDrawn()` is gated only
 * on a reporter-count reaching zero -- there is no built-in guarantee that a NEW Activity instance's
 * own Library content has actually been measured/laid out/drawn before its own `ReportDrawnWhen`
 * predicate is first evaluated) and report a stale "already loaded" `true` to a brand-new Activity
 * instance that has not actually loaded its own Library content yet.
 *
 * [LibraryTab] exposes no production setter for [LibraryTab.isInitiallyLoaded] other than its own
 * internal `Content()` composable's `LaunchedEffect` (real Compose UI, not host-unit-testable
 * without a device/Robolectric) -- this test reaches the private backing `MutableStateFlow` via
 * reflection, exactly mirroring what that real effect does (`_isInitiallyLoaded.value = true`), to
 * simulate "a previous Activity instance already finished loading Library" without composing
 * anything.
 */
class LibraryTabReadinessLifecycleTest {

    private fun backingFlow(): MutableStateFlow<Boolean> {
        val field = LibraryTab::class.java.getDeclaredField("_isInitiallyLoaded")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(LibraryTab) as MutableStateFlow<Boolean>
    }

    @AfterEach
    fun resetSharedSingletonState() {
        // LibraryTab is a process-wide singleton shared with every other test class in the same
        // JVM -- leave it exactly as every other test expects to find it: not-yet-loaded.
        LibraryTab.resetForNewActivityInstance()
    }

    @Test
    fun `isInitiallyLoaded starts false`() {
        LibraryTab.resetForNewActivityInstance()
        assertFalse(LibraryTab.isInitiallyLoaded.value)
    }

    @Test
    fun `resetForNewActivityInstance clears a latch left true by a prior Activity instance`() {
        // Simulate: a previous Activity instance's LibraryTab.Content() already observed real
        // Library data finish loading and latched this true.
        backingFlow().value = true
        assertTrue(LibraryTab.isInitiallyLoaded.value)

        // A brand-new Activity instance's onCreate() runs -- per MainActivity's own wiring --
        // before that instance's Composition (and therefore before ReportDrawnWhen or LibraryTab's
        // own Content()) can observe the stale value.
        LibraryTab.resetForNewActivityInstance()

        assertFalse(
            LibraryTab.isInitiallyLoaded.value,
            "a same-process Activity recreation must not inherit a stale process-global " +
                "isInitiallyLoaded=true from a PRIOR Activity instance -- the new instance's own " +
                "ReportDrawnWhen predicate must only pass once ITS OWN LibraryTab.Content() " +
                "observes ITS OWN LibraryScreenModel's real isLoading transition to false",
        )
    }

    @Test
    fun `resetForNewActivityInstance is a safe no-op when already false`() {
        LibraryTab.resetForNewActivityInstance()
        assertFalse(LibraryTab.isInitiallyLoaded.value)

        LibraryTab.resetForNewActivityInstance()

        assertFalse(LibraryTab.isInitiallyLoaded.value)
    }

    @Test
    fun `a recreated Activity instance's combined readiness predicate stays false even though navigator already shows HomeScreen`() {
        // Proves the END-TO-END claim behind MainActivity's own wiring: a recreated Activity whose
        // Voyager Navigator has ALREADY restored HomeScreen as navigator.lastItem (the common case
        // for a configuration-change recreation, since Navigator state is typically retained)
        // still cannot report ReportDrawnWhen-true until ITS OWN Library content has genuinely
        // reloaded -- combining the two real production signals, not a synthetic stand-in.
        backingFlow().value = true // stale latch from the PRIOR Activity instance
        LibraryTab.resetForNewActivityInstance() // MainActivity.onCreate()'s own fix, applied

        val predicateResult = isHomeScreenTrulyDrawn(
            currentScreen = HomeScreen,
            libraryInitiallyLoaded = LibraryTab.isInitiallyLoaded.value,
        )

        assertFalse(predicateResult)
    }

    @Test
    fun `without the reset, a stale latch would have falsely reported the recreated Activity as drawn -- proving the fix is load-bearing`() {
        // Negative control: proves resetForNewActivityInstance() is doing real work, not merely
        // present for documentation -- omitting it reproduces the exact defect this correction
        // exists to close.
        backingFlow().value = true // stale latch from the PRIOR Activity instance, NOT reset

        val predicateResultWithoutFix = isHomeScreenTrulyDrawn(
            currentScreen = HomeScreen,
            libraryInitiallyLoaded = LibraryTab.isInitiallyLoaded.value,
        )

        assertTrue(
            predicateResultWithoutFix,
            "this negative control is expected to reproduce the defect (predicate true on stale " +
                "state) precisely to prove the reset in the other tests is load-bearing, not inert",
        )
    }
}
// KMK <--
