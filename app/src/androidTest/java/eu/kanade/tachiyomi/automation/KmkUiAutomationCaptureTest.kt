package eu.kanade.tachiyomi.automation

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Bounded device-side primitives for the host capture coordinator.
 *
 * This test intentionally fails closed when the host has not supplied an
 * approved Evaluation Mode marker. It does not navigate, capture, or expose
 * hierarchy content by itself.
 */
@RunWith(AndroidJUnit4::class)
class KmkUiAutomationCaptureTest {
    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun verifyTargetPackageOnly() {
        val expectedPackage = InstrumentationRegistry.getArguments()
            .getString("expected_package", "app.komikku.kmk.dev")
        assertEquals(expectedPackage, device.currentPackageName)
    }

    @Test
    fun evaluationMarkerMustBeExplicitlyConfigured() {
        val marker = InstrumentationRegistry.getArguments().getString("evaluation_marker")
            ?: error("Host must provide a safe Evaluation Mode marker")
        assertTrue("Host must provide a non-empty Evaluation Mode marker", marker.isNotBlank())
        assertTrue("Evaluation marker must resolve uniquely", device.findObjects(By.desc(marker)).size == 1)
    }
}
