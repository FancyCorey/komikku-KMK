package eu.kanade.tachiyomi.ui.more

// KMK -->
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.manga.components.MarkdownRender
import eu.kanade.presentation.more.settings.screen.about.KmkRecsWhatsNewPolicy
import eu.kanade.presentation.util.Screen
import exh.recs.KmkRecsReleaseNotes
import exh.recs.KmkRecsReleaseNotesGroupingPolicy
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.InfoScreen
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

        // KMK v0.8.16: the changelog has grown to many versions -- keep the current v0.8.x family
        // expanded by default and collapse older families (v0.7.x, v0.6.x, ...) behind a short
        // summary, without dropping or reformatting any historical entry. Mirrors the official
        // WhatsNewScreen's InfoScreen scaffold/heading/accept-button styling; only the body uses a
        // grouped/collapsible layout instead of one long MarkdownRender call.
        val groups = remember { KmkRecsReleaseNotesGroupingPolicy.group(KmkRecsReleaseNotes.MARKDOWN) }

        InfoScreen(
            icon = Icons.Outlined.NewReleases,
            headingText = stringResource(MR.strings.whats_new),
            subtitleText = stringResource(SYMR.strings.latest_, KmkRecsReleaseNotes.DISPLAY_VERSION_NAME) +
                " - " + stringResource(KMR.strings.current_, KmkRecsReleaseNotes.DISPLAY_VERSION_NAME),
            acceptText = stringResource(MR.strings.action_ok),
            onAcceptClick = { navigator.pop() },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = MaterialTheme.padding.large),
            ) {
                groups.forEach { group ->
                    KmkRecsWhatsNewFamilySection(group = group)
                }
            }
        }
    }
}

@Composable
private fun KmkRecsWhatsNewFamilySection(group: KmkRecsReleaseNotesGroupingPolicy.Group) {
    var expanded by rememberSaveable(group.family) { mutableStateOf(group.expandedByDefault) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = MaterialTheme.padding.medium),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = MaterialTheme.padding.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(KMR.strings.kmk_whats_new_family_title, group.family),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (!expanded) {
                    Text(
                        text = group.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
            )
        }
        if (expanded) {
            val combinedBody = group.sections.joinToString(separator = "\n\n-----\n") { it.body }
            MarkdownRender(
                content = combinedBody.trimIndent().replace("KMK-Recs", "Komikku FC"),
                flavour = GFMFlavourDescriptor(),
            )
        }
    }
}
// KMK <--
