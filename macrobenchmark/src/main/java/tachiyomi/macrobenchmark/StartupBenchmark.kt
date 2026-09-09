/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tachiyomi.macrobenchmark

import android.util.Log
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMacrobenchmarkApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * Run this benchmark from Studio to see startup measurements, and captured system traces
 * for investigating your app's performance from a cold state.
 */
@RunWith(AndroidJUnit4ClassRunner::class)
class ColdStartupBenchmark : AbstractStartupBenchmark(StartupMode.COLD)

/**
 * Run this benchmark from Studio to see startup measurements, and captured system traces
 * for investigating your app's performance from a warm state.
 */
@RunWith(AndroidJUnit4ClassRunner::class)
class WarmStartupBenchmark : AbstractStartupBenchmark(StartupMode.WARM)

/**
 * Run this benchmark from Studio to see startup measurements, and captured system traces
 * for investigating your app's performance from a hot state.
 */
@RunWith(AndroidJUnit4ClassRunner::class)
class HotStartupBenchmark : AbstractStartupBenchmark(StartupMode.HOT)

/**
 * Measures the populated For You route using the benchmark-only deterministic fixture.
 * The fixture flag is enabled only on the benchmark target; release and ordinary debug builds
 * retain their existing source and preference behavior. This measures navigation, first result
 * rendering, and a bounded scroll, not startup timing.
 */
@RunWith(AndroidJUnit4ClassRunner::class)
class PopulatedForYouBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @OptIn(ExperimentalMacrobenchmarkApi::class)
    @Test
    fun populatedForYouInitialLoadAndScroll() = benchmarkRule.measureRepeated(
        packageName = "app.komikku.benchmark",
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Ignore(),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
        },
    ) {
        startActivityAndWait()
        dismissFirstRunUiIfPresent()
        val browse = device.wait(Until.findObject(By.text("Browse")), 10_000)
            ?: error("Browse tab did not appear")
        browse.click()
        device.waitForIdle()
        val forYou = device.wait(Until.findObject(By.text("For You")), 10_000)
            ?: error("For You tab did not appear")
        forYou.click()
        device.wait(Until.hasObject(By.textContains("Fixture")), 20_000)
        device.waitForIdle()
        device.swipe(
            device.displayWidth / 2,
            device.displayHeight * 3 / 4,
            device.displayWidth / 2,
            device.displayHeight / 4,
            20,
        )
        device.waitForIdle()
    }
}

/**
 * Measures first presentation and a bounded scroll of the realistic-1k Library fixture.
 * This remains benchmark-only and uses the existing Library readiness semantic rather than
 * adding a production navigation or fixture path.
 */
@RunWith(AndroidJUnit4ClassRunner::class)
class PopulatedLibraryBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @OptIn(ExperimentalMacrobenchmarkApi::class)
    @Test
    fun populatedLibraryInitialLoadAndScroll() = benchmarkRule.measureRepeated(
        packageName = "app.komikku.benchmark",
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Ignore(),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
        },
    ) {
        startActivityAndWait()
        device.wait(Until.hasObject(By.res(LIBRARY_READY_CONTENT_TEST_TAG)), 20_000)
        device.waitForIdle()
        device.swipe(
            device.displayWidth / 2,
            device.displayHeight * 3 / 4,
            device.displayWidth / 2,
            device.displayHeight / 4,
            20,
        )
        device.waitForIdle()
    }

    private companion object {
        const val LIBRARY_READY_CONTENT_TEST_TAG = "library_ready_content"
    }
}

/**
 * Base class for benchmarks with different startup modes.
 * Enables app startups from various states of baseline profile or [CompilationMode]s.
 */
abstract class AbstractStartupBenchmark(private val startupMode: StartupMode) {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /**
     * 2026-08-27 (small-downstream-diff / official-guidance correction): per Android's own
     * Macrobenchmark documentation, `CompilationMode.None()` requires Android 14 (API 34)+ to
     * persist state across the compilation reset it performs; on earlier API levels that reset
     * requires reinstalling the APK. See [startupCompilationIgnored] for the documented pre-34
     * workaround (`CompilationMode.Ignore()` with compilation controlled separately), which is what
     * this program's only currently provisioned AVD (`KFC_PerfClone_API30`) must use. This method
     * is retained, unmodified, for a future API 34+ low-resource performance AVD once one is
     * provisioned -- see `KOMIKKU_FC_F2_05_AUTOMATED_PERFORMANCE_AND_EFFICIENCY_PROGRAM_2026-08-27.md`
     * for the recorded applicability decision and primary-source citation. It must not be run
     * against `KFC_PerfClone_API30` as the accepted timing-acceptance measurement.
     */
    @Test
    fun startupNoCompilation() = startup(CompilationMode.None())

    /**
     * The documented pre-Android-14 substitute for [startupNoCompilation]: skips AOT compilation
     * control (rather than triggering the compilation-state reset `CompilationMode.None()`
     * performs, which requires API 34+ to persist state without a reinstall) so app data --
     * including this run's own first-run/onboarding completion -- survives across iterations on
     * `KFC_PerfClone_API30` (API 30). This is the correct method for the current AVD until an API
     * 34+ low-resource performance AVD is provisioned.
     */
    @OptIn(ExperimentalMacrobenchmarkApi::class)
    @Test
    fun startupCompilationIgnored() = startup(CompilationMode.Ignore())

    @Test
    fun startupBaselineProfileDisabled() = startup(
        CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.Disable,
            warmupIterations = 1,
        ),
    )

    @Test
    fun startupBaselineProfile() = startup(
        CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require),
    )

    @Test
    fun startupFullCompilation() = startup(CompilationMode.Full())

    private fun startup(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = "app.komikku.benchmark",
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        iterations = 10,
        startupMode = startupMode,
        setupBlock = {
            pressHome()
            // KMK F2-05.0.2 (KFC-V0.8.21-FIX2-CORRECTIVE-RECHECK-AND-RELEASE-PROGRAM): idempotent,
            // safe on every iteration -- see dismissFirstRunUiIfPresent's own KDoc for why this
            // lives here rather than in a separate one-shot device-prep step.
            dismissFirstRunUiIfPresent()
        },
    ) {
        startActivityAndWait()
    }
}

/**
 * Without this, `StartupTimingMetric` never reports `timeToFullDisplayMs` at all (confirmed on
 * device across two full 10-iteration runs: zero `Fully drawn` logcat lines) -- a fresh install
 * of the `benchmark` target shows `OnboardingScreen`, so `MainActivity`'s `ReportDrawnWhen`
 * predicate (gated on reaching `HomeScreen`/Library) never becomes true, and
 * `reportFullyDrawn()` never fires.
 *
 * 2026-08-27 correction (reopened by independent review): the prior version of this helper was
 * fail-OPEN -- it clicked at most one `By.text("OK")`, although `MainActivity` can show
 * `WhatsNewDialog` and then, sequentially, `KmkRecsWhatsNewDialog` (confirmed via exact source,
 * `MainActivity.kt`'s `if (showChangelog) {...} else if (shouldShowKmkDialog(...)) {...}`
 * block -- both dialogs share the IDENTICAL locale-dependent "OK" confirm-button text, making
 * them indistinguishable to a single text-based dismissal). It also stopped onboarding
 * traversal the instant `onboarding_accept_button` was momentarily absent, ignored the
 * `Boolean` result of waiting for `library_ready_content`, and always called `pressHome()`
 * regardless -- silently letting `measureRepeated` proceed to measure an unprepared, still-
 * blocked launch.
 *
 * This is now a bounded, deterministic, fail-CLOSED state machine (decision logic in
 * [FirstRunUiPreparation.kt][classifyFirstRunUiState]/[nextFirstRunUiStep], this module's own
 * src/main, instrumented-tested there; this function supplies the actual `UiDevice` I/O each
 * loop iteration).
 *
 * 2026-08-27 correction (small-downstream-diff standard): `WhatsNewDialog.kt` is
 * upstream-owned, inherited Komikku code. Its earlier benchmark-only
 * `Modifier.semantics { testTagsAsResourceId = true }.testTag(...)` addition was reverted --
 * git-clean against this worktree's own HEAD -- because this benchmark only ever runs against
 * the disposable, single-purpose `KFC_PerfClone_API30` benchmark AVD, whose system locale is
 * controlled and known (English) by construction; a frozen-locale `By.text("OK")` match is
 * therefore a reliable, upstream-file-safe way to detect and dismiss it, with no inherited-file
 * edit required. `KmkRecsWhatsNewDialog.kt` is fork-owned Komikku FC code (its own screen, not
 * an upstream component), so it retains its own focused, stable
 * `KMK_RECS_WHATS_NEW_DIALOG_CONFIRM_BUTTON_TEST_TAG` selector -- also needed to disambiguate
 * it from the upstream dialog's identical "OK" text, since both can appear (never
 * simultaneously: `MainActivity`'s `if (showChangelog) {...} else if (shouldShowKmkDialog...)
 * {...}` is mutually exclusive per composition) and share the same confirm-button string
 * resource. `whatsNewDialogPresent` below is therefore computed as "a generic 'OK' dialog is
 * showing that is NOT the KMK-tagged one" -- order-independent of which dialog the OS actually
 * rendered.
 *
 * On failure (an unrecognized state, or no progress within
 * [FIRST_RUN_UI_MAX_NO_PROGRESS_REPEATS] iterations, or the overall timeout), the current
 * window hierarchy is captured and this function throws -- `measureRepeated` cannot silently
 * proceed to `measureBlock` on an unprepared device.
 *
 * Each iteration observes not just the classified [FirstRunUiState] but the full
 * [FirstRunUiObservation] -- foreground package plus a content fingerprint of the current
 * screen -- so a genuine onboarding-step transition (Theme -> Storage -> Permission -> Guides,
 * all classified as the same coarse `ONBOARDING_STEP`) is never mistaken for a stall. See
 * [nextFirstRunUiStep]'s own KDoc.
 */
internal fun MacrobenchmarkScope.dismissFirstRunUiIfPresent() {
    startActivityAndWait()

    var previousObservation: FirstRunUiObservation? = null
    var repeatCount = 0
    var lastStoragePickerClickFingerprint: String? = null
    var invalidRootDrawerAttempts = 0
    val deadline = System.currentTimeMillis() + FIRST_RUN_UI_PREPARATION_TIMEOUT_MS

    while (true) {
        val kmkWhatsNewDialogPresent = device.hasObject(By.res(KMK_RECS_WHATS_NEW_DIALOG_CONFIRM_BUTTON_TEST_TAG))
        val foregroundPackage = device.currentPackageName
        val appWindowVisible = foregroundPackage == BENCHMARK_PACKAGE
        val storagePickerVisible = foregroundPackage == DOCUMENTS_UI_PACKAGE
        val storagePermissionVisible = foregroundPackage == PERMISSION_CONTROLLER_PACKAGE
        val snapshot = FirstRunUiSnapshot(
            // Controlled-locale text match: safe only because this benchmark exclusively
            // targets the disposable, English-locale KFC_PerfClone_API30 AVD -- see this
            // function's own KDoc. Excludes the KMK-tagged dialog so the two are never
            // conflated even though both confirm buttons read "OK".
            whatsNewDialogPresent = appWindowVisible && !kmkWhatsNewDialogPresent &&
                device.hasObject(By.text(CONTROLLED_LOCALE_OK_TEXT)),
            kmkWhatsNewDialogPresent = appWindowVisible && kmkWhatsNewDialogPresent,
            onboardingButtonPresent = appWindowVisible &&
                device.hasObject(By.res(ONBOARDING_ACCEPT_BUTTON_TEST_TAG)),
            libraryReadyPresent = appWindowVisible &&
                device.hasObject(By.res(LIBRARY_READY_CONTENT_TEST_TAG)),
            storageSelectActionPresent = !storagePickerVisible &&
                device.hasObject(By.text(STORAGE_SELECT_ACTION_TEXT)),
            storagePickerCreateFolderActionPresent = storagePickerVisible && (
                device.hasObject(By.res(DOCUMENTS_UI_CREATE_FOLDER_ACTION_RESOURCE)) ||
                    device.hasObject(By.res(DOCUMENTS_UI_CREATE_FOLDER_MENU_RESOURCE))
                ),
            storagePickerConfirmButtonPresent = storagePickerVisible && device.hasObject(
                By.res(DOCUMENTS_UI_CONFIRM_BUTTON_RESOURCE),
            ),
            storagePickerAllowButtonPresent = storagePermissionVisible && (
                device.hasObject(By.text(STORAGE_PERMISSION_ALLOW_TEXT)) ||
                    device.hasObject(By.text(STORAGE_PERMISSION_ALLOW_TITLE_TEXT))
                ),
            storagePickerInvalidRootPresent = storagePickerVisible && device.hasObject(
                By.text(DOCUMENTS_UI_INVALID_ROOT_TEXT),
            ),
            storagePickerDownloadsRootPresent = storagePickerVisible && device.hasObject(
                By.text(DOCUMENTS_UI_DOWNLOADS_ROOT_TEXT),
            ),
        )
        val state = classifyFirstRunUiState(snapshot)
        val observation = FirstRunUiObservation(
            state = state,
            foregroundPackage = foregroundPackage,
            contentFingerprint = currentScreenContentFingerprint(),
        )
        val transition = nextFirstRunUiStep(observation, previousObservation, repeatCount)
        repeatCount = transition.repeatCountForNextCall
        if (state != FirstRunUiState.STORAGE_PICKER) {
            invalidRootDrawerAttempts = 0
        }

        when (val result = transition.result) {
            FirstRunUiStepResult.Ready -> {
                pressHome()
                return
            }
            is FirstRunUiStepResult.Fail -> failFirstRunSetup(result.reason, observation, snapshot)
            FirstRunUiStepResult.Continue -> {
                when (state) {
                    // Upstream dialog: no resource-id (WhatsNewDialog.kt is unmodified, see this
                    // function's own KDoc) -- click via the same controlled-locale text match
                    // used to detect it.
                    FirstRunUiState.WHATS_NEW_DIALOG -> {
                        device.findObject(By.text(CONTROLLED_LOCALE_OK_TEXT))?.click()
                        device.waitForIdle()
                    }
                    FirstRunUiState.KMK_WHATS_NEW_DIALOG -> {
                        device.findObject(By.res(KMK_RECS_WHATS_NEW_DIALOG_CONFIRM_BUTTON_TEST_TAG))?.click()
                        device.waitForIdle()
                    }
                    FirstRunUiState.ONBOARDING_STEP -> {
                        if (snapshot.storageSelectActionPresent) {
                            if (lastStoragePickerClickFingerprint != observation.contentFingerprint) {
                                device.findObject(By.text(STORAGE_SELECT_ACTION_TEXT))?.click()
                                lastStoragePickerClickFingerprint = observation.contentFingerprint
                                device.waitForIdle()
                            } else {
                                Thread.sleep(300)
                            }
                        } else {
                            val nextButton = device.findObject(By.res(ONBOARDING_ACCEPT_BUTTON_TEST_TAG))
                            if (nextButton?.isEnabled == true) {
                                nextButton.click()
                                device.waitForIdle()
                            } else {
                                Thread.sleep(300)
                            }
                        }
                    }
                    FirstRunUiState.STORAGE_PICKER -> {
                        if (snapshot.storagePickerInvalidRootPresent) {
                            if (invalidRootDrawerAttempts >= DOCUMENTS_UI_MAX_ROOT_DRAWER_ATTEMPTS) {
                                failFirstRunSetup(
                                    "DocumentsUI invalid-root recovery exhausted " +
                                        "$DOCUMENTS_UI_MAX_ROOT_DRAWER_ATTEMPTS roots-drawer attempts " +
                                        "without exposing the verified Downloads root",
                                    observation,
                                    snapshot,
                                )
                            }
                            invalidRootDrawerAttempts++
                            // A stale persisted DocumentsUI stack can reopen at an invalid root.
                            // Prefer the provider's supported roots drawer so the next launch
                            // does not immediately restore the same invalid location.
                            val downloadsRoot = device.findObject(
                                By.text(DOCUMENTS_UI_DOWNLOADS_ROOT_TEXT),
                            )
                            if (downloadsRoot != null) {
                                Log.i(FIRST_RUN_UI_LOG_TAG, "action=select_documentsui_downloads_root")
                                downloadsRoot.click()
                                device.waitForIdle()
                                Thread.sleep(STORAGE_PICKER_SETTLE_DELAY_MS)
                            } else {
                                Log.i(FIRST_RUN_UI_LOG_TAG, "action=open_documentsui_roots_drawer")
                                device.swipe(
                                    4,
                                    device.displayHeight / 2,
                                    device.displayWidth / 2,
                                    device.displayHeight / 2,
                                    20,
                                )
                                device.waitForIdle()
                                Thread.sleep(STORAGE_PICKER_SETTLE_DELAY_MS)
                            }
                            continue
                        }
                        if (snapshot.storagePickerAllowButtonPresent) {
                            Log.i(FIRST_RUN_UI_LOG_TAG, "action=click_storage_permission_allow")
                            device.findObject(By.text(STORAGE_PERMISSION_ALLOW_TEXT))?.click()
                                ?: device.findObject(By.text(STORAGE_PERMISSION_ALLOW_TITLE_TEXT))?.click()
                            device.waitForIdle()
                            Thread.sleep(STORAGE_PICKER_SETTLE_DELAY_MS)
                            continue
                        }
                        val createFolderAction = device.findObject(
                            By.res(DOCUMENTS_UI_CREATE_FOLDER_ACTION_RESOURCE),
                        ) ?: device.findObject(By.res(DOCUMENTS_UI_CREATE_FOLDER_MENU_RESOURCE))
                        val confirmButton = device.findObject(By.res(DOCUMENTS_UI_CONFIRM_BUTTON_RESOURCE))
                        when {
                            createFolderAction?.isEnabled == true -> {
                                Log.i(FIRST_RUN_UI_LOG_TAG, "action=click_documentsui_create_folder")
                                createFolderAction.click()
                                device.waitForIdle()
                                Thread.sleep(STORAGE_PICKER_SETTLE_DELAY_MS)
                            }
                            confirmButton?.isEnabled == true -> {
                                // DocumentsUI can expose an enabled framework button whose
                                // UiObject2 click does not reach the separate system window on
                                // this AVD. Use the object's current visible center so the
                                // fallback remains bounded to the verified control.
                                val bounds = confirmButton.visibleBounds
                                Log.i(
                                    FIRST_RUN_UI_LOG_TAG,
                                    "action=click_documentsui_confirm bounds=$bounds",
                                )
                                device.click(bounds.centerX(), bounds.centerY())
                                device.waitForIdle()
                                Thread.sleep(STORAGE_PICKER_SETTLE_DELAY_MS)
                            }
                            else -> Thread.sleep(300)
                        }
                    }
                    FirstRunUiState.LIBRARY_READY -> error("unreachable: Ready is handled above")
                    // UNKNOWN: nothing recognized to click yet (e.g. a dialog mid-composition
                    // right after launch) -- back off briefly and let the next iteration
                    // re-observe, rather than clicking blindly or busy-looping.
                    FirstRunUiState.UNKNOWN -> Thread.sleep(300)
                }
            }
        }
        previousObservation = observation

        if (System.currentTimeMillis() > deadline) {
            failFirstRunSetup(
                "Timed out after ${FIRST_RUN_UI_PREPARATION_TIMEOUT_MS}ms waiting for " +
                    "Library readiness (last observation: $observation)",
                observation,
                snapshot,
            )
        }
    }
}

/**
 * An opaque, locale-safe digest of what's currently on-screen: a SHA-256 hash of the raw
 * `dumpWindowHierarchy` XML bytes. Its VALUE is never inspected -- only compared for equality
 * against a prior iteration's fingerprint, in [nextFirstRunUiStep] -- so it stays safe to use as
 * a progress signal even though the underlying visible text is locale-dependent. Reused, rather
 * than a lighter-weight signal, because it is already the same primitive [failFirstRunSetup]
 * uses for failure diagnostics, and this runs only inside `setupBlock`, never the measured
 * block.
 */
private fun MacrobenchmarkScope.currentScreenContentFingerprint(): String {
    val bytes = ByteArrayOutputStream()
    device.dumpWindowHierarchy(bytes)
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}

/**
 * Fails setup closed: captures the current window hierarchy inline in the thrown message (so
 * it lands in the JUnit failure output/logcat for later evidence retention -- no extra
 * device-side file plumbing needed) and throws, which aborts `measureRepeated` before it can
 * reach `measureBlock`.
 */
private fun MacrobenchmarkScope.failFirstRunSetup(
    reason: String,
    observation: FirstRunUiObservation,
    snapshot: FirstRunUiSnapshot,
): Nothing {
    val hierarchyBytes = ByteArrayOutputStream()
    device.dumpWindowHierarchy(hierarchyBytes)
    error(
        "First-run UI preparation failed, refusing to proceed to measurement: $reason\n" +
            "observation: $observation\n" +
            "snapshot: $snapshot\n\n" +
            "Window hierarchy:\n${hierarchyBytes.toString(Charsets.UTF_8.name())}",
    )
}

private const val FIRST_RUN_UI_PREPARATION_TIMEOUT_MS = 60_000L

// Literal string values matching the production Compose testTag constants -- see this
// function's own KDoc for why they can't be imported directly.
private const val KMK_RECS_WHATS_NEW_DIALOG_CONFIRM_BUTTON_TEST_TAG = "kmk_recs_whats_new_dialog_confirm_button"
private const val ONBOARDING_ACCEPT_BUTTON_TEST_TAG = "onboarding_accept_button"
private const val LIBRARY_READY_CONTENT_TEST_TAG = "library_ready_content"

// Frozen English-locale confirm-button text for the unmodified, upstream-owned
// WhatsNewDialog (MR.strings.action_ok) -- only safe because this benchmark exclusively
// targets the disposable, English-locale KFC_PerfClone_API30 AVD. See
// dismissFirstRunUiIfPresent's own KDoc.
private const val CONTROLLED_LOCALE_OK_TEXT = "OK"
private const val STORAGE_SELECT_ACTION_TEXT = "Select a folder"
private const val DOCUMENTS_UI_PACKAGE = "com.google.android.documentsui"
private const val PERMISSION_CONTROLLER_PACKAGE = "com.google.android.permissioncontroller"
private const val BENCHMARK_PACKAGE = "app.komikku.benchmark"
private const val DOCUMENTS_UI_CREATE_FOLDER_ACTION_RESOURCE =
    "com.google.android.documentsui:id/action_button"
private const val DOCUMENTS_UI_CREATE_FOLDER_MENU_RESOURCE =
    "com.google.android.documentsui:id/option_menu_create_dir"
private const val DOCUMENTS_UI_CONFIRM_BUTTON_RESOURCE = "android:id/button1"
private const val DOCUMENTS_UI_INVALID_ROOT_TEXT = "(invalid)"
private const val DOCUMENTS_UI_DOWNLOADS_ROOT_TEXT = "Downloads"
private const val DOCUMENTS_UI_MAX_ROOT_DRAWER_ATTEMPTS = 3
private const val STORAGE_PICKER_SETTLE_DELAY_MS = 750L
private const val STORAGE_PERMISSION_ALLOW_TEXT = "ALLOW"
private const val STORAGE_PERMISSION_ALLOW_TITLE_TEXT = "Allow"
private const val FIRST_RUN_UI_LOG_TAG = "KfcFirstRunUi"
