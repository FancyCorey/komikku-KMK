package eu.kanade.tachiyomi.ui.main

import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.more.NewUpdateScreen
import eu.kanade.tachiyomi.ui.more.OnboardingScreen
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK F2-05.0 (KFC-V0.8.21-FIX2-CORRECTIVE-RECHECK-AND-RELEASE-PROGRAM) -->
/**
 * Direct, host-testable proof of [isHomeScreenTrulyDrawn]'s own claim: this TTFD-gating predicate
 * cannot report "drawn" during Library's own initial loading, onboarding, or a blocking app-update
 * dialog. See that function's own KDoc for the full reasoning; this test exercises every branch of
 * its conjunction against the actual production screen types, not stand-ins.
 */
class HomeScreenReadinessTest {

    @Test
    fun `false while Library has not finished its first real load, even on HomeScreen`() {
        assertFalse(isHomeScreenTrulyDrawn(currentScreen = HomeScreen, libraryInitiallyLoaded = false))
    }

    @Test
    fun `false while OnboardingScreen is the visible screen, regardless of Library load state`() {
        assertFalse(isHomeScreenTrulyDrawn(currentScreen = OnboardingScreen(), libraryInitiallyLoaded = false))
        assertFalse(isHomeScreenTrulyDrawn(currentScreen = OnboardingScreen(), libraryInitiallyLoaded = true))
    }

    @Test
    fun `false while a blocking NewUpdateScreen is the visible screen, regardless of Library load state`() {
        val updateScreen = NewUpdateScreen(
            versionName = "1.0",
            changelogInfo = "",
            releaseLink = "",
            downloadLink = "",
        )
        assertFalse(isHomeScreenTrulyDrawn(currentScreen = updateScreen, libraryInitiallyLoaded = false))
        assertFalse(isHomeScreenTrulyDrawn(currentScreen = updateScreen, libraryInitiallyLoaded = true))
    }

    @Test
    fun `true only once HomeScreen is visible AND Library has genuinely finished loading`() {
        assertTrue(isHomeScreenTrulyDrawn(currentScreen = HomeScreen, libraryInitiallyLoaded = true))
    }
}
// KMK <--
