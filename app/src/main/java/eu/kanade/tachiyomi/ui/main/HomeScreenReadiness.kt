package eu.kanade.tachiyomi.ui.main

import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.tachiyomi.ui.home.HomeScreen

// KMK F2-05.0 (KFC-V0.8.21-FIX2-CORRECTIVE-RECHECK-AND-RELEASE-PROGRAM) -->
/**
 * Pure, host-testable readiness predicate for gating TTFD reporting
 * (`androidx.activity.compose.ReportDrawnWhen`/`Activity.reportFullyDrawn()`) behind a genuinely
 * truthful "the app's first meaningful content is on screen" signal.
 *
 * Extracted out of [MainActivity]'s own Compose content so it can be exercised by a plain JVM host
 * test without Robolectric or a device, the same discipline this program has used throughout for
 * pure logic pulled out of Compose call sites.
 *
 * Both conditions are load-bearing:
 * - [currentScreen] must be [HomeScreen] itself (compared by reference, since it is a singleton
 *   `object`) -- Voyager's root `Navigator` only composes `navigator.lastItem`, so this is false
 *   whenever `OnboardingScreen` or `NewUpdateScreen` (or any other pushed screen) is the visible
 *   screen instead. This is what proves the predicate cannot fire during onboarding or a blocking
 *   app-update dialog: those are both implemented as screens pushed on top of [HomeScreen], not as
 *   overlays composed alongside it.
 * - [libraryInitiallyLoaded] must be true -- sourced from
 *   [eu.kanade.tachiyomi.ui.library.LibraryTab.isInitiallyLoaded], which only latches true once
 *   [eu.kanade.tachiyomi.ui.library.LibraryScreenModel.State.isLoading] has made its first genuine
 *   transition to `false`. This is what proves the predicate cannot fire during Library's own
 *   initial loading state.
 *
 * Deliberately conjunctive (`&&`), not `MainActivity.ready` (which this program's own F2-05.0 trace
 * found becomes `true` unconditionally at the end of `handleIntentAction()`, independent of whether
 * Library has actually finished loading -- a coarser signal correct for gating the splash screen's
 * own keep-on-screen condition, but not truthful enough for TTFD).
 */
fun isHomeScreenTrulyDrawn(currentScreen: Screen, libraryInitiallyLoaded: Boolean): Boolean {
    return currentScreen === HomeScreen && libraryInitiallyLoaded
}
// KMK <--
