package tachiyomi.macrobenchmark

// KMK F2-05.0.2 (KFC-V0.8.21-FIX2-CORRECTIVE-RECHECK-AND-RELEASE-PROGRAM) -->
// Pure, testable decision logic for macrobenchmark/StartupBenchmark.kt's first-run preparation
// state machine (tachiyomi.macrobenchmark.dismissFirstRunUiIfPresent, this same module).
//
// 2026-08-27 correction: this file previously lived in :core:common -- a PRODUCTION module shipped
// in the real app -- even though nothing here is ordinary Komikku FC behavior. That violated the
// small-downstream-diff-and-upstream-maintainability standard's rule that test/benchmark-only state
// machines must not move into shared production modules for convenience. :macrobenchmark itself is
// the correct home: its `com.android.test` plugin type means the ENTIRE module compiles only into a
// disposable instrumentation-test APK and is never packaged into the shipped app, so a file living
// here is test/tooling-owned by construction, not production-owned, without needing a new Gradle
// module. (`com.android.test` also has no host-JVM-test source set of its own -- confirmed: no
// `testBenchmarkUnitTest`-equivalent task exists -- so FirstRunUiPreparationTest.kt, alongside this
// file, is a real @RunWith(AndroidJUnit4ClassRunner::class) instrumented test exercised via
// `connectedBenchmarkAndroidTest`, the same task that already runs StartupBenchmark itself, rather
// than a plain JUnit host test.)
//
// 2026-08-27 correction #2: the original nextFirstRunUiStep() compared only the coarse
// FirstRunUiState enum between iterations to detect "no progress." That enum collapses ALL FOUR
// onboarding steps (Theme, Storage, Permission, Guides) into the single ONBOARDING_STEP value, so a
// device genuinely advancing through onboarding -- tapping Next, the screen changing underneath --
// looked IDENTICAL, iteration to iteration, to a device stuck on one unchanging screen. Both would
// increment the same repeat counter toward the same failure threshold, meaning real progress could
// be misclassified as a stall. Replaced with FirstRunUiObservation, which additionally carries the
// foreground package name (a genuine package/window transition, e.g. focus moving to a different
// app, is its own distinct signal, not conflated with an unrecognized-but-same-app state) and an
// opaque content fingerprint of the currently visible screen (its VALUE is never inspected or
// branched on for meaning -- only compared for equality against the previous observation -- so it
// stays locale-safe even though the underlying visible text is not). This lets the state machine
// genuinely distinguish: a legitimate transition between onboarding steps (state unchanged, but
// fingerprint changes); a stable blocked state (state, package, and fingerprint all unchanged
// across repeats); an unknown/transient state (UNKNOWN, tolerated the same bounded way as any other
// state -- a single transient "nothing rendered yet" observation right after launch is expected and
// must not be confused with a genuinely stuck screen); a package/window transition (foreground
// package differs from the previous observation); and successful Library readiness.
//
// StartupBenchmark.dismissFirstRunUiIfPresent() is the only caller; it supplies the actual
// UiDevice-derived FirstRunUiSnapshot/foreground-package/content-fingerprint each loop iteration and
// performs the actual click/dump side effects this file only decides about.

/** One point-in-time observation of every first-run UI element this state machine recognizes. */
data class FirstRunUiSnapshot(
    val whatsNewDialogPresent: Boolean,
    val kmkWhatsNewDialogPresent: Boolean,
    val onboardingButtonPresent: Boolean,
    val libraryReadyPresent: Boolean,
    val storageSelectActionPresent: Boolean = false,
    val storagePickerCreateFolderActionPresent: Boolean = false,
    val storagePickerConfirmButtonPresent: Boolean = false,
    val storagePickerAllowButtonPresent: Boolean = false,
    val storagePickerInvalidRootPresent: Boolean = false,
    val storagePickerDownloadsRootPresent: Boolean = false,
)

/** Every first-run UI state this state machine can recognize and act on. */
enum class FirstRunUiState {
    /** Library is showing and no known blocking dialog/wizard remains -- the only success state. */
    LIBRARY_READY,
    WHATS_NEW_DIALOG,
    KMK_WHATS_NEW_DIALOG,
    ONBOARDING_STEP,
    STORAGE_PICKER,

    /**
     * None of the recognized elements were found. Deliberately terminal when sustained, not
     * retried silently forever -- an unrecognized screen (a crash, an unexpected system dialog, a
     * new first-run surface this state machine was never taught about) must eventually fail setup.
     */
    UNKNOWN,
}

/**
 * Classifies the current first-run UI from a snapshot. Order matters and is deliberate: a blocking
 * dialog reported alongside a stale/about-to-be-covered `library_ready_content` node must still be
 * treated as blocked -- [FirstRunUiState.LIBRARY_READY] requires it to be the ONLY thing present.
 */
fun classifyFirstRunUiState(snapshot: FirstRunUiSnapshot): FirstRunUiState = when {
    snapshot.libraryReadyPresent &&
        !snapshot.whatsNewDialogPresent &&
        !snapshot.kmkWhatsNewDialogPresent &&
        !snapshot.onboardingButtonPresent -> FirstRunUiState.LIBRARY_READY
    snapshot.whatsNewDialogPresent -> FirstRunUiState.WHATS_NEW_DIALOG
    snapshot.kmkWhatsNewDialogPresent -> FirstRunUiState.KMK_WHATS_NEW_DIALOG
    snapshot.storagePickerCreateFolderActionPresent ||
        snapshot.storagePickerConfirmButtonPresent ||
        snapshot.storagePickerAllowButtonPresent ||
        snapshot.storagePickerInvalidRootPresent ->
        FirstRunUiState.STORAGE_PICKER
    snapshot.onboardingButtonPresent -> FirstRunUiState.ONBOARDING_STEP
    else -> FirstRunUiState.UNKNOWN
}

/**
 * One loop iteration's full observation: the classified [state], the [foregroundPackage] UiDevice
 * reports at that instant, and an opaque [contentFingerprint] of the currently visible screen (any
 * stable digest of what's on-screen -- callers must never assert its literal value or branch on it
 * for meaning, only compare it for equality against a prior observation's fingerprint).
 */
data class FirstRunUiObservation(
    val state: FirstRunUiState,
    val foregroundPackage: String?,
    val contentFingerprint: String,
)

/** How many consecutive loop iterations may observe NO progress before failing. */
const val FIRST_RUN_UI_MAX_NO_PROGRESS_REPEATS = 6

/** Outcome of one state-machine step: what the caller should do next. */
sealed interface FirstRunUiStepResult {
    /** Click the state's associated element (the caller knows which, from the classified state) and continue. */
    data object Continue : FirstRunUiStepResult

    /** Library is genuinely ready with nothing blocking it -- stop, do not click anything further. */
    data object Ready : FirstRunUiStepResult

    /** Fail closed: capture diagnostics and abort setup. Must never be silently swallowed. */
    data class Fail(val reason: String) : FirstRunUiStepResult
}

/**
 * One state-machine transition: the decision for this iteration, plus the repeat-count the caller
 * must carry into the NEXT call's [FirstRunUiTransition] (single source of truth -- the caller
 * never recomputes this itself, avoiding drift between two copies of the same repeat-tracking
 * logic).
 */
data class FirstRunUiTransition(
    val result: FirstRunUiStepResult,
    val repeatCountForNextCall: Int,
)

/**
 * Decides the next step given the freshly captured [observation], the observation from the PRIOR
 * iteration ([previousObservation], `null` on the very first iteration), and the repeat count
 * carried forward from the prior call's own [FirstRunUiTransition.repeatCountForNextCall] (`0` on
 * the very first iteration). Pure and total: every input combination produces exactly one
 * [FirstRunUiTransition].
 *
 * Progress is real -- and the no-progress counter resets -- whenever ANY of [FirstRunUiObservation]'s
 * three fields differs from the previous observation: a different classified [FirstRunUiState] (a
 * dialog dismissed, onboarding finished), a different foreground package (a genuine cross-app/
 * cross-window transition), or a different content fingerprint while the coarse state stays the
 * same (a legitimate transition between onboarding steps, which this state machine's enum alone
 * cannot distinguish from a stall). The counter only advances, toward failure, when all three are
 * unchanged from the previous observation -- a genuinely stable, unadvancing screen.
 */
fun nextFirstRunUiStep(
    observation: FirstRunUiObservation,
    previousObservation: FirstRunUiObservation?,
    repeatCountBeforeThisObservation: Int,
): FirstRunUiTransition {
    if (observation.state == FirstRunUiState.LIBRARY_READY) {
        return FirstRunUiTransition(FirstRunUiStepResult.Ready, 0)
    }

    val madeProgress = previousObservation == null ||
        previousObservation.state != observation.state ||
        previousObservation.foregroundPackage != observation.foregroundPackage ||
        previousObservation.contentFingerprint != observation.contentFingerprint

    val repeatCount = if (madeProgress) 0 else repeatCountBeforeThisObservation + 1

    if (repeatCount >= FIRST_RUN_UI_MAX_NO_PROGRESS_REPEATS) {
        val reason = if (observation.state == FirstRunUiState.UNKNOWN) {
            "Stuck in an unknown first-run UI state for $repeatCount consecutive checks with no " +
                "change in classified state, foreground package, or screen content -- no " +
                "recognized dialog, onboarding step, or Library-ready content was found. This is " +
                "treated as a hard failure rather than retried indefinitely, since silently " +
                "waiting through an unrecognized, unchanging screen is exactly the fail-open " +
                "defect this state machine replaces."
        } else {
            "No progress: '${observation.state}' (foreground package " +
                "'${observation.foregroundPackage}') was observed $repeatCount times in a row with " +
                "an IDENTICAL screen content fingerprint -- not merely the same coarse state, which " +
                "onboarding step transitions also share -- without advancing to a different state, " +
                "a different foreground window, a different visible screen, or reaching " +
                "Library-ready."
        }
        return FirstRunUiTransition(FirstRunUiStepResult.Fail(reason), repeatCount)
    }
    return FirstRunUiTransition(FirstRunUiStepResult.Continue, repeatCount)
}
// KMK <--
