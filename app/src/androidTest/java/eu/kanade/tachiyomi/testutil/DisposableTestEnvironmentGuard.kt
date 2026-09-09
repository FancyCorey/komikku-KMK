package eu.kanade.tachiyomi.testutil

import android.content.Context
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import eu.kanade.tachiyomi.BuildConfig
import org.junit.Assume.assumeTrue
import java.io.File

// KMK F2-02 corrective slice A (KFC-V0.8.21-FIX2-CORRECTIVE-RECHECK-AND-RELEASE-PROGRAM) -->
/**
 * Hard runtime guard for instrumented tests that write through real, live-Injekt-provided
 * repositories and could therefore mutate real user data if ever run against the wrong target.
 *
 * A source-code comment, or an unlikely-to-collide fixture id, is not a safety mechanism -- a
 * previous version of [eu.kanade.tachiyomi.data.backup.restore.restorers
 * .BackupIdentityDecisionDeviceRoundTripTest] relied on exactly that (its own doc comment claimed
 * "no other pair on this device is ever touched," which was true only for the one table it
 * specifically discussed, while its `BackupOptions()`/`RestoreOptions()` calls actually used every
 * default-enabled category -- library entries, categories, app settings, extension stores, source
 * settings, saved searches/feeds, and local tracker, alongside the intended taste profile). This
 * object provides an actual, mechanically-checked precondition instead: [assumeDisposableEnvironment]
 * calls [org.junit.Assume.assumeTrue], which SKIPS (not fails) the calling test the instant any
 * check below does not hold, before any repository call in the test body ever runs.
 *
 * KMK F2-02 hardening (2026-08-28): a second independent review correctly found that the original
 * two signals below cannot distinguish the designated disposable probe AVD
 * (`KFC_RestoreProbe_API28`) from the preserved development emulator (`KMK_GoogleAPI30`) -- both
 * are genuine emulators running the `.dev` debug build, so both satisfied the guard equally. A
 * third, independent signal -- a host-provisioned marker -- was added to close that gap.
 *
 * KMK F2-02 identity-and-content correction (2026-08-27): a THIRD independent review found that
 * hardening still insufficient in two ways, both closed here:
 * 1. The provisioning script verified only that its target serial was ONLINE, not that it was
 *    actually the configured disposable AVD -- `private/tools/provision_disposable_test_marker.ps1`
 *    now calls `adb emu avd name` and refuses to provision any device whose reported name does not
 *    exactly match the configured disposable AVD, so an operator mistake in the target serial can
 *    no longer provision the wrong device.
 * 2. The marker's content was a single fixed, version-only string -- readable-file-EXISTENCE with
 *    a constant payload does not meaningfully differ from bare existence, and a stale marker left
 *    from a much earlier session could still satisfy a guard that only checks for that fixed
 *    string. [hasValidMarker] now reads the marker file's actual CONTENT and compares it against a
 *    fresh, random per-run nonce the provisioning script generates and prints for the caller to
 *    thread into this exact test invocation as an instrumentation argument
 *    (`disposableMarkerNonce`) -- so a stale marker from an earlier run, with a DIFFERENT nonce
 *    baked into its content, fails this check even though the file itself is present and readable.
 *
 * Three independent signals, all required:
 * 1. **Emulator, not a physical device.** The standard community `Build.*` heuristic (fingerprint/
 *    model/product/hardware/manufacturer markers used by real emulators, including this project's
 *    own `sdk_gphone_x86_64` AVDs). A genuine physical device -- which could be a developer's own
 *    daily-driver phone with the debug build installed for ordinary development -- fails this check.
 * 2. **The `.dev` debug application id, not a production identity.** [BuildConfig.APPLICATION_ID]
 *    must end with `.dev` -- the suffix ONLY the `debug` build type carries (see
 *    `app/build.gradle.kts`'s `debug { applicationIdSuffix = ".dev" }`). Every real end-user-facing
 *    identity (`app.komikku`, `.foss`, `.beta`, `.rt`, `.kmk`, etc.) fails this check, so this guard
 *    can never pass against an install a real end user would ever run day to day.
 * 3. **A host-provisioned disposable-environment marker whose CONTENT matches this exact test
 *    invocation's own instrumentation argument.** [hasValidMarker] checks that a marker file at
 *    [DISPOSABLE_MARKER_PATH] under `/data/local/tmp` -- world-readable on emulator (userdebug)
 *    builds, the standard Android testing convention for a host-to-device test fixture marker --
 *    both exists AND contains the exact per-run nonce passed to THIS invocation via the
 *    `disposableMarkerNonce` instrumentation argument. Only
 *    `provision_disposable_test_marker.ps1` ever writes it (after independently verifying the
 *    target device's AVD identity), and only with a freshly generated nonce printed for the caller
 *    to thread through; a device this script was never run against -- including the preserved
 *    emulator -- has no marker at all and fails this check outright, and even a device with a
 *    STALE marker from an earlier, unrelated provisioning run fails it because that marker's nonce
 *    will not match this invocation's own `disposableMarkerNonce` argument.
 *
 * Even with all three signals, this remains necessary-but-not-airtight (nothing prevents someone
 * from manually running the provisioning script against a device they should not have), but it is
 * a real mechanical check that fails closed (skips) rather than a comment that trusts the caller.
 */
internal object DisposableTestEnvironmentGuard {

    /**
     * Must match `private/tools/provision_disposable_test_marker.ps1`'s `$MarkerPath` exactly.
     * `/data/local/tmp` is a standard, world-readable/writable location on emulator builds for
     * exactly this kind of host-to-device test fixture marker.
     */
    private const val DISPOSABLE_MARKER_PATH = "/data/local/tmp/kfc_disposable_test_environment"

    /**
     * Instrumentation argument key carrying this exact test invocation's expected marker nonce.
     * Passed via `-Pandroid.testInstrumentationRunnerArguments.disposableMarkerNonce=<value>`, the
     * same value `provision_disposable_test_marker.ps1` printed when it provisioned the device.
     */
    private const val NONCE_ARGUMENT_KEY = "disposableMarkerNonce"

    /** Skips the calling test unless the emulator, `.dev` build-identity, and marker-content checks all pass. */
    fun assumeDisposableEnvironment(context: Context) {
        assumeTrue(
            "DisposableTestEnvironmentGuard: this test only runs on an emulator (Build.* markers did not match any known emulator signature) -- refusing to run against what may be a physical device.",
            isEmulator(),
        )
        assumeTrue(
            "DisposableTestEnvironmentGuard: this test only runs against the .dev debug application id, got '${BuildConfig.APPLICATION_ID}' -- refusing to run against a production-identified install.",
            BuildConfig.APPLICATION_ID.endsWith(".dev"),
        )
        val expectedNonce = InstrumentationRegistry.getArguments().getString(NONCE_ARGUMENT_KEY)
        assumeTrue(
            "DisposableTestEnvironmentGuard: no '$NONCE_ARGUMENT_KEY' instrumentation argument was provided -- " +
                "run private/tools/provision_disposable_test_marker.ps1 against the target device first, then " +
                "pass its printed NONCE value as -Pandroid.testInstrumentationRunnerArguments.$NONCE_ARGUMENT_KEY=<value>.",
            !expectedNonce.isNullOrBlank(),
        )
        assumeTrue(
            "DisposableTestEnvironmentGuard: the marker file at $DISPOSABLE_MARKER_PATH is missing, unreadable, " +
                "or its content does not contain this invocation's expected nonce -- refusing to run against a " +
                "device that was not freshly provisioned for THIS test invocation via " +
                "private/tools/provision_disposable_test_marker.ps1. This rejects both a completely unprovisioned " +
                "device (including the preserved emulator, which is never given this marker) and a device " +
                "carrying a STALE marker from an earlier, unrelated provisioning run.",
            hasValidMarker(expectedNonce!!),
        )
        // context is accepted (not merely BuildConfig) so a future stronger signal can be added
        // here without changing every call site's signature.
        context.applicationContext
    }

    internal fun isEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.PRODUCT.contains("sdk_gphone") ||
            Build.PRODUCT.contains("google_sdk") ||
            (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu")

    /**
     * Reads [DISPOSABLE_MARKER_PATH] directly as a file (not a shell `exec`, which ordinary app
     * processes cannot rely on) and returns whether its content contains [expectedNonce] exactly --
     * not merely whether the file exists and is readable. Returns `false`, never throws, for any
     * I/O or permission failure so an unreadable/absent/stale marker fails this guard closed rather
     * than crashing the test run.
     */
    internal fun hasValidMarker(expectedNonce: String): Boolean =
        try {
            val file = File(DISPOSABLE_MARKER_PATH)
            file.isFile && file.canRead() && file.readText().contains(expectedNonce)
        } catch (e: SecurityException) {
            false
        } catch (e: java.io.IOException) {
            false
        }
}
// KMK <--
