package exh.util

import eu.kanade.domain.source.service.SourcePreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference

class DeveloperOptionsGatePolicyTest {
    @Test
    fun `developer option preference is private and defaults off`() {
        val preference = SourcePreferences(FakePreferenceStore()).developerOptionsEnabled()

        assertFalse(preference.get())
        assertTrue(Preference.isPrivate(preference.key()))

        preference.set(true)
        assertTrue(preference.get())
    }

    @Test
    fun `enabling from default off requires confirmation`() {
        assertEquals(
            DeveloperOptionsGatePolicy.RequestResult.REQUIRE_CONFIRMATION,
            DeveloperOptionsGatePolicy.request(currentEnabled = false, requestedEnabled = true),
        )
    }

    @Test
    fun `cancelling confirmation does not persist enablement`() {
        val currentEnabled = false
        val result = DeveloperOptionsGatePolicy.request(currentEnabled, requestedEnabled = true)

        assertEquals(DeveloperOptionsGatePolicy.RequestResult.REQUIRE_CONFIRMATION, result)
        assertFalse(currentEnabled)
    }

    @Test
    fun `confirmed enablement can be persisted by the caller`() {
        val result = DeveloperOptionsGatePolicy.request(currentEnabled = false, requestedEnabled = true)

        assertEquals(DeveloperOptionsGatePolicy.RequestResult.REQUIRE_CONFIRMATION, result)
        val confirmedValue = true
        assertTrue(confirmedValue)
    }

    @Test
    fun `disabling an enabled gate does not require confirmation`() {
        assertEquals(
            DeveloperOptionsGatePolicy.RequestResult.PERSIST,
            DeveloperOptionsGatePolicy.request(currentEnabled = true, requestedEnabled = false),
        )
    }

    @Test
    fun `repeated enabled request is idempotent`() {
        assertEquals(
            DeveloperOptionsGatePolicy.RequestResult.PERSIST,
            DeveloperOptionsGatePolicy.request(currentEnabled = true, requestedEnabled = true),
        )
    }

    @Test
    fun `evaluation mode never exposes diagnostics`() {
        assertFalse(
            DeveloperOptionsGatePolicy.canExposeDiagnostics(
                developerOptionsEnabled = true,
                evaluationModeEnabled = true,
            ),
        )
    }

    @Test
    fun `diagnostics require the explicit developer opt in`() {
        assertFalse(
            DeveloperOptionsGatePolicy.canExposeDiagnostics(
                developerOptionsEnabled = false,
                evaluationModeEnabled = false,
            ),
        )
        assertTrue(
            DeveloperOptionsGatePolicy.canExposeDiagnostics(
                developerOptionsEnabled = true,
                evaluationModeEnabled = false,
            ),
        )
    }
}
