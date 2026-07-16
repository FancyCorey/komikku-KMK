package eu.kanade.tachiyomi.ui.more

// KMK -->
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.WhatsNewScreen
import eu.kanade.presentation.more.settings.screen.about.KmkRecsWhatsNewPolicy
import eu.kanade.presentation.util.Screen
import exh.recs.KmkRecsReleaseNotes
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class KmkRecsWhatsNewScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        // KMK v0.8.1-fix2: mark as seen exactly once when the screen is opened, via a proper
        // Compose side-effect scope — this used to run directly in the composable body, which
        // Compose is free to invoke more than once per actual "open" (e.g. on any recomposition),
        // an unsafe-side-effect anti-pattern even though it was idempotent here.
        val kmkRecsLastSeenVersion = Injekt.get<PreferenceStore>().getInt(
            Preference.appStateKey("kmk_recs_last_seen_version_code"),
            0,
        )
        LaunchedEffect(Unit) {
            kmkRecsLastSeenVersion.set(KmkRecsWhatsNewPolicy.seenVersionCodeOnAcknowledge(KmkRecsReleaseNotes.VERSION_CODE))
        }

        WhatsNewScreen(
            currentVersion = KmkRecsReleaseNotes.VERSION_NAME,
            versionName = KmkRecsReleaseNotes.VERSION_NAME,
            changelogInfo = KmkRecsReleaseNotes.MARKDOWN,
            onAcceptUpdate = { navigator.pop() },
        )
    }
}
// KMK <--
