package exh.recs.evaluation

import eu.kanade.domain.base.BasePreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// KMK -->
class SourceEvaluationInstallerPolicyTest {

    // --- SHIZUKU mode ---

    @Test
    fun `shizuku not installed gives unavailable`() {
        val result = SourceEvaluationInstallerPolicy.validate(
            mode = SourceEvaluationInstallerPolicy.InstallerMode.SHIZUKU,
            currentGlobalInstaller = BasePreferences.ExtensionInstaller.PACKAGEINSTALLER,
            privateAvailable = true,
            shizukuInstalled = false,
            shizukuBinderAlive = false,
            shizukuPermissionGranted = false,
        )
        assertEquals(SourceEvaluationInstallerPolicy.InstallerReadiness.UNAVAILABLE, result.readiness)
    }

    @Test
    fun `shizuku installed but binder dead gives unavailable`() {
        val result = SourceEvaluationInstallerPolicy.validate(
            mode = SourceEvaluationInstallerPolicy.InstallerMode.SHIZUKU,
            currentGlobalInstaller = BasePreferences.ExtensionInstaller.PACKAGEINSTALLER,
            privateAvailable = true,
            shizukuInstalled = true,
            shizukuBinderAlive = false,
            shizukuPermissionGranted = false,
        )
        assertEquals(SourceEvaluationInstallerPolicy.InstallerReadiness.UNAVAILABLE, result.readiness)
    }

    @Test
    fun `shizuku running but permission missing gives needs_permission`() {
        val result = SourceEvaluationInstallerPolicy.validate(
            mode = SourceEvaluationInstallerPolicy.InstallerMode.SHIZUKU,
            currentGlobalInstaller = BasePreferences.ExtensionInstaller.PACKAGEINSTALLER,
            privateAvailable = true,
            shizukuInstalled = true,
            shizukuBinderAlive = true,
            shizukuPermissionGranted = false,
        )
        assertEquals(SourceEvaluationInstallerPolicy.InstallerReadiness.NEEDS_PERMISSION, result.readiness)
    }

    @Test
    fun `shizuku installed running and permission granted gives ready`() {
        val result = SourceEvaluationInstallerPolicy.validate(
            mode = SourceEvaluationInstallerPolicy.InstallerMode.SHIZUKU,
            currentGlobalInstaller = BasePreferences.ExtensionInstaller.PACKAGEINSTALLER,
            privateAvailable = true,
            shizukuInstalled = true,
            shizukuBinderAlive = true,
            shizukuPermissionGranted = true,
        )
        assertEquals(SourceEvaluationInstallerPolicy.InstallerReadiness.READY, result.readiness)
    }

    // --- stop-using fallback ---

    @Test
    fun `stop-using fallback prefers private when available`() {
        val fallback = ShizukuSetupHelper.stopUsingFallbackMode(privateAvailable = true)
        assertEquals(SourceEvaluationInstallerPolicy.InstallerMode.PRIVATE, fallback)
    }

    @Test
    fun `stop-using fallback uses current when private unavailable`() {
        val fallback = ShizukuSetupHelper.stopUsingFallbackMode(privateAvailable = false)
        assertEquals(SourceEvaluationInstallerPolicy.InstallerMode.CURRENT, fallback)
    }

    // --- PRIVATE mode ---

    @Test
    fun `private mode available gives ready`() {
        val result = SourceEvaluationInstallerPolicy.validate(
            mode = SourceEvaluationInstallerPolicy.InstallerMode.PRIVATE,
            currentGlobalInstaller = BasePreferences.ExtensionInstaller.PACKAGEINSTALLER,
            privateAvailable = true,
            shizukuInstalled = false,
            shizukuBinderAlive = false,
            shizukuPermissionGranted = false,
        )
        assertEquals(SourceEvaluationInstallerPolicy.InstallerReadiness.READY, result.readiness)
    }

    @Test
    fun `private mode unavailable gives unavailable`() {
        val result = SourceEvaluationInstallerPolicy.validate(
            mode = SourceEvaluationInstallerPolicy.InstallerMode.PRIVATE,
            currentGlobalInstaller = BasePreferences.ExtensionInstaller.PACKAGEINSTALLER,
            privateAvailable = false,
            shizukuInstalled = false,
            shizukuBinderAlive = false,
            shizukuPermissionGranted = false,
        )
        assertEquals(SourceEvaluationInstallerPolicy.InstallerReadiness.UNAVAILABLE, result.readiness)
    }
}
// KMK <--
