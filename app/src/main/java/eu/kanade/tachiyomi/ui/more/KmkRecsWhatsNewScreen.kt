package eu.kanade.tachiyomi.ui.more

// KMK -->
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.WhatsNewScreen
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

        // Mark as seen when the screen is opened
        val kmkRecsLastSeenVersion = Injekt.get<PreferenceStore>().getInt(
            Preference.appStateKey("kmk_recs_last_seen_version_code"),
            0,
        )
        kmkRecsLastSeenVersion.set(KmkRecsReleaseNotes.VERSION_CODE)

        WhatsNewScreen(
            currentVersion = KmkRecsReleaseNotes.VERSION_NAME,
            versionName = KmkRecsReleaseNotes.VERSION_NAME,
            changelogInfo = KmkRecsReleaseNotes.MARKDOWN,
            onAcceptUpdate = { navigator.pop() },
        )
    }
}
// KMK <--
