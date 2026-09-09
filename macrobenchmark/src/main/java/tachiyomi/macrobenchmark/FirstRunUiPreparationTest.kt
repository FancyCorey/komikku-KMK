package tachiyomi.macrobenchmark

import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// KMK F2-05.0.2 (KFC-V0.8.21-FIX2-CORRECTIVE-RECHECK-AND-RELEASE-PROGRAM) -->
/**
 * Focused, adversarial coverage for [classifyFirstRunUiState] and [nextFirstRunUiStep] -- pure
 * functions, no UiDevice/Android dependency of their own. Written as a real instrumented test
 * (`@RunWith(AndroidJUnit4ClassRunner::class)`, exercised via `connectedBenchmarkAndroidTest`, the
 * same task that runs [ColdStartupBenchmark] itself) rather than a plain JUnit host test, because
 * :macrobenchmark's `com.android.test` plugin type has no host-JVM-test source set to run a host
 * test against (confirmed: no `testBenchmarkUnitTest`-equivalent task exists for this module). See
 * this file's sibling, FirstRunUiPreparation.kt, for the full rationale on why both live in
 * :macrobenchmark rather than :core:common.
 */
@RunWith(AndroidJUnit4ClassRunner::class)
class FirstRunUiPreparationTest {

    private fun observation(
        state: FirstRunUiState,
        pkg: String = "app.komikku.benchmark",
        fingerprint: String = "fp-0",
    ) = FirstRunUiObservation(state, pkg, fingerprint)

    @Test
    fun classifiesEachRecognizedElementOnItsOwn() {
        assertEquals(
            FirstRunUiState.WHATS_NEW_DIALOG,
            classifyFirstRunUiState(FirstRunUiSnapshot(true, false, false, false)),
        )
        assertEquals(
            FirstRunUiState.KMK_WHATS_NEW_DIALOG,
            classifyFirstRunUiState(FirstRunUiSnapshot(false, true, false, false)),
        )
        assertEquals(
            FirstRunUiState.ONBOARDING_STEP,
            classifyFirstRunUiState(FirstRunUiSnapshot(false, false, true, false)),
        )
        assertEquals(
            FirstRunUiState.LIBRARY_READY,
            classifyFirstRunUiState(FirstRunUiSnapshot(false, false, false, true)),
        )
        assertEquals(
            FirstRunUiState.UNKNOWN,
            classifyFirstRunUiState(FirstRunUiSnapshot(false, false, false, false)),
        )
        assertEquals(
            FirstRunUiState.STORAGE_PICKER,
            classifyFirstRunUiState(
                FirstRunUiSnapshot(
                    false,
                    false,
                    false,
                    false,
                    storagePickerCreateFolderActionPresent = true,
                ),
            ),
        )
        assertEquals(
            FirstRunUiState.STORAGE_PICKER,
            classifyFirstRunUiState(
                FirstRunUiSnapshot(
                    false,
                    false,
                    false,
                    false,
                    storagePickerInvalidRootPresent = true,
                ),
            ),
        )
        assertEquals(
            FirstRunUiState.STORAGE_PICKER,
            classifyFirstRunUiState(
                FirstRunUiSnapshot(
                    false,
                    false,
                    false,
                    false,
                    storagePickerAllowButtonPresent = true,
                ),
            ),
        )
    }

    @Test
    fun libraryReadyAlongsideAStillPresentBlockingDialogIsNotReady() {
        // Proves the original fail-open gap stays closed: a stale/about-to-be-covered
        // library_ready_content node must not short-circuit past a still-visible blocking dialog.
        assertEquals(
            FirstRunUiState.WHATS_NEW_DIALOG,
            classifyFirstRunUiState(FirstRunUiSnapshot(true, false, false, true)),
        )
    }

    @Test
    fun libraryReadyOnFirstObservationSucceedsImmediately() {
        val transition = nextFirstRunUiStep(observation(FirstRunUiState.LIBRARY_READY), null, 0)
        assertEquals(FirstRunUiStepResult.Ready, transition.result)
        assertEquals(0, transition.repeatCountForNextCall)
    }

    @Test
    fun aLegitimateOnboardingStepTransitionResetsTheCounterEvenThoughTheCoarseStateIsUnchanged() {
        // This is the exact regression this turn's correction targets: all four onboarding steps
        // (Theme, Storage, Permission, Guides) share the SAME FirstRunUiState.ONBOARDING_STEP value.
        // A content-fingerprint change while the state stays identical must still count as progress.
        val step1 = observation(FirstRunUiState.ONBOARDING_STEP, fingerprint = "theme-step-fp")
        val step2 = observation(FirstRunUiState.ONBOARDING_STEP, fingerprint = "storage-step-fp")

        var repeatCount = 0
        val first = nextFirstRunUiStep(step1, null, repeatCount)
        repeatCount = first.repeatCountForNextCall
        assertEquals(FirstRunUiStepResult.Continue, first.result)
        assertEquals(0, repeatCount)

        // Simulate several repeats of observing the SAME step (e.g. device.waitForIdle() settling)
        repeatCount = nextFirstRunUiStep(step1, step1, repeatCount).repeatCountForNextCall
        repeatCount = nextFirstRunUiStep(step1, step1, repeatCount).repeatCountForNextCall
        assertTrue("expected some accumulated repeat count before the transition", repeatCount > 0)

        // Now the screen genuinely changes (Next was tapped, Storage step appeared) -- must reset.
        val afterTransition = nextFirstRunUiStep(step2, step1, repeatCount)
        assertEquals(FirstRunUiStepResult.Continue, afterTransition.result)
        assertEquals(0, afterTransition.repeatCountForNextCall)
    }

    @Test
    fun aStableBlockedStateWithIdenticalStatePackageAndFingerprintFailsClosed() {
        // A disabled "Next" button that never advances: state, package, AND fingerprint all stay
        // byte-for-byte identical across every observation -- must eventually fail, not loop forever.
        val stuck = observation(FirstRunUiState.ONBOARDING_STEP, fingerprint = "storage-step-disabled-fp")
        var repeatCount = 0
        var previous: FirstRunUiObservation? = null
        var lastResult: FirstRunUiStepResult = FirstRunUiStepResult.Continue
        repeat(FIRST_RUN_UI_MAX_NO_PROGRESS_REPEATS + 2) {
            val transition = nextFirstRunUiStep(stuck, previous, repeatCount)
            repeatCount = transition.repeatCountForNextCall
            lastResult = transition.result
            previous = stuck
        }
        assertTrue(
            "expected a stable blocked state to fail closed, was $lastResult",
            lastResult is FirstRunUiStepResult.Fail,
        )
    }

    @Test
    fun aPackageWindowTransitionIsDistinctProgressEvenWithTheSameClassifiedState() {
        // The classified state can stay UNKNOWN both before and after a cross-app window takes
        // focus (e.g. a system picker), but the foreground package genuinely changed -- must count
        // as progress, not as a repeat of the same stuck state.
        val beforeTransition = observation(
            FirstRunUiState.UNKNOWN,
            pkg = "app.komikku.benchmark",
            fingerprint = "same-fp",
        )
        val afterTransition = observation(
            FirstRunUiState.UNKNOWN,
            pkg = "com.google.android.documentsui",
            fingerprint = "same-fp",
        )
        var repeatCount = nextFirstRunUiStep(beforeTransition, null, 0).repeatCountForNextCall
        repeatCount = nextFirstRunUiStep(beforeTransition, beforeTransition, repeatCount).repeatCountForNextCall
        assertTrue(repeatCount > 0)

        val transition = nextFirstRunUiStep(afterTransition, beforeTransition, repeatCount)
        assertEquals(FirstRunUiStepResult.Continue, transition.result)
        assertEquals(0, transition.repeatCountForNextCall)
    }

    @Test
    fun storagePickerWindowTransitionResetsNoProgressCounter() {
        val onboarding = observation(FirstRunUiState.ONBOARDING_STEP, fingerprint = "onboarding")
        val picker = observation(
            FirstRunUiState.STORAGE_PICKER,
            pkg = "com.google.android.documentsui",
            fingerprint = "picker",
        )
        var repeatCount = nextFirstRunUiStep(onboarding, null, 0).repeatCountForNextCall
        repeatCount = nextFirstRunUiStep(onboarding, onboarding, repeatCount).repeatCountForNextCall

        val transition = nextFirstRunUiStep(picker, onboarding, repeatCount)
        assertEquals(FirstRunUiStepResult.Continue, transition.result)
        assertEquals(0, transition.repeatCountForNextCall)
    }

    @Test
    fun aSingleTransientUnknownObservationRightAfterLaunchIsTolerated() {
        val first = nextFirstRunUiStep(observation(FirstRunUiState.UNKNOWN), null, 0)
        assertEquals(FirstRunUiStepResult.Continue, first.result)
    }

    @Test
    fun sustainedUnknownStateFailsClosedRatherThanRetryingForever() {
        val stuckUnknown = observation(FirstRunUiState.UNKNOWN, fingerprint = "blank-fp")
        var repeatCount = 0
        var previous: FirstRunUiObservation? = null
        var lastResult: FirstRunUiStepResult = FirstRunUiStepResult.Continue
        repeat(FIRST_RUN_UI_MAX_NO_PROGRESS_REPEATS + 2) {
            val transition = nextFirstRunUiStep(stuckUnknown, previous, repeatCount)
            repeatCount = transition.repeatCountForNextCall
            lastResult = transition.result
            previous = stuckUnknown
        }
        assertTrue(lastResult is FirstRunUiStepResult.Fail)
        assertTrue((lastResult as FirstRunUiStepResult.Fail).reason.contains("unknown", ignoreCase = true))
    }

    @Test
    fun setupFailureIsADistinctInspectableResultType() {
        val stuck = observation(FirstRunUiState.WHATS_NEW_DIALOG, fingerprint = "same-dialog-fp")
        var repeatCount = 0
        var previous: FirstRunUiObservation? = null
        var lastResult: FirstRunUiStepResult = FirstRunUiStepResult.Continue
        repeat(FIRST_RUN_UI_MAX_NO_PROGRESS_REPEATS + 1) {
            val transition = nextFirstRunUiStep(stuck, previous, repeatCount)
            repeatCount = transition.repeatCountForNextCall
            lastResult = transition.result
            previous = stuck
        }
        val failure = lastResult as FirstRunUiStepResult.Fail
        assertTrue(failure.reason.isNotBlank())
    }
}
// KMK <--
