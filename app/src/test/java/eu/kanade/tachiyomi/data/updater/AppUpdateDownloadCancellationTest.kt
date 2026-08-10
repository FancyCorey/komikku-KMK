package eu.kanade.tachiyomi.data.updater

import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppUpdateDownloadCancellationTest {

    @Test
    fun `installation cancellation is propagated instead of reported as an install failure`() {
        assertTrue(shouldPropagateInstallationCancellation(CancellationException("cancelled")))
    }

    @Test
    fun `ordinary installation failures keep the existing fallback path`() {
        assertFalse(shouldPropagateInstallationCancellation(IllegalStateException("install failed")))
    }
}
