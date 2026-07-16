package exh.recs.settings

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.ThumbUpAlt
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import exh.recs.evaluation.SourceEvaluationScreen
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import eu.kanade.presentation.util.Screen as KomikkuScreen

// KMK v0.8.8 -->
/**
 * Concise index/front-door for Recommendation Settings, mirroring
 * [eu.kanade.presentation.more.settings.screen.SettingsMainScreen]'s existing index pattern
 * (`TextPreferenceWidget` rows in a `LazyColumn`, each navigating to a detail screen) rather than
 * inventing a new settings framework.
 *
 * v0.8.8 gap-closing pass: every category now routes to its own dedicated screen (the former single
 * `RecommendationsSettingsScreen` has been retired entirely — see git history / the v0.8.8
 * implementation report for the removed file). Each detail screen was extracted verbatim from that
 * former screen's corresponding section: same controls, same `RecommendationsSettingsScreenModel`
 * methods, same preference reads/writes, zero behavior change — only the screen boundary is new.
 * Scroll-to-section is no longer relevant now that each category is its own screen (it was declined
 * in the prior pass specifically because it wasn't safe within one shared, variable-length screen;
 * that constraint no longer applies once each category has its own bounded `LazyColumn`).
 *
 * One category has a genuine structural exception, not an oversight:
 * - **Background/network/installer behavior** has no distinct content in the former
 *   `RecommendationsSettingsScreen` at all — installer mode, batch size, and network-retry behavior
 *   are controls that live entirely inside [SourceEvaluationScreen] (a separate screen already, since
 *   before this pass). Creating a new, empty "Background/Installer" screen just to redirect into
 *   `SourceEvaluationScreen` would add a pointless extra tap; this row routes directly to
 *   [SourceEvaluationScreen] instead, same as Evaluation.
 */
object RecommendationSettingsIndexScreen : KomikkuScreen() {
    @Suppress("unused")
    private fun readResolve(): Any = RecommendationSettingsIndexScreen

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.taste_settings_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        // KMK v0.8.9: same icon/semantics as main Settings search's app-bar action
                        // (Icons.Outlined.Search, pushes a dedicated search screen on top of the
                        // current stack) -- see RecommendationSettingsSearchScreen.
                        eu.kanade.presentation.components.AppBarActions(
                            kotlinx.collections.immutable.persistentListOf(
                                eu.kanade.presentation.components.AppBar.Action(
                                    title = stringResource(KMR.strings.rec_settings_search),
                                    icon = Icons.Outlined.Search,
                                    onClick = { navigator.push(RecommendationSettingsSearchScreen()) },
                                ),
                            ),
                        )
                    },
                )
            },
        ) { contentPadding ->
            LazyColumn(contentPadding = contentPadding) {
                itemsIndexed(items, key = { _, item -> "rec-settings-index-${item.titleRes.resourceId}" }) { _, item ->
                    TextPreferenceWidget(
                        title = stringResource(item.titleRes),
                        subtitle = stringResource(item.subtitleRes),
                        icon = item.icon,
                        onPreferenceClick = { navigator.push(item.screen) },
                    )
                }
            }
        }
    }

    private data class Item(
        val titleRes: StringResource,
        val subtitleRes: StringResource,
        val icon: ImageVector,
        val screen: Screen,
    )

    private val items = listOf(
        Item(
            titleRes = KMR.strings.rec_settings_index_for_you,
            subtitleRes = KMR.strings.rec_settings_daily_recs_header,
            icon = Icons.Outlined.Explore,
            screen = RecommendationForYouSettingsScreen(),
        ),
        Item(
            titleRes = KMR.strings.rec_settings_index_source_priority,
            subtitleRes = KMR.strings.rec_source_priority,
            icon = Icons.Outlined.Star,
            screen = RecommendationSourcePrioritySettingsScreen(),
        ),
        Item(
            titleRes = KMR.strings.rec_settings_index_taste_tags,
            subtitleRes = KMR.strings.taste_settings_tag_prefs,
            icon = Icons.Outlined.Label,
            screen = RecommendationTasteTagsSettingsScreen(),
        ),
        Item(
            titleRes = KMR.strings.rec_settings_index_evaluation,
            subtitleRes = KMR.strings.source_evaluation_settings_desc,
            icon = Icons.Outlined.FactCheck,
            screen = SourceEvaluationScreen(),
        ),
        Item(
            titleRes = KMR.strings.rec_settings_index_discovery,
            subtitleRes = KMR.strings.rec_sources_to_try_header,
            icon = Icons.Outlined.ThumbUpAlt,
            screen = RecommendationNonInstalledDiscoverySettingsScreen(),
        ),
        Item(
            titleRes = KMR.strings.rec_settings_index_installer,
            subtitleRes = KMR.strings.source_evaluation_settings_experimental_header,
            icon = Icons.Outlined.CloudSync,
            // KMK v0.8.8: no distinct content exists outside SourceEvaluationScreen — see class doc.
            screen = SourceEvaluationScreen(),
        ),
        Item(
            titleRes = KMR.strings.rec_settings_index_diagnostics,
            subtitleRes = KMR.strings.rec_settings_management_header,
            icon = Icons.Outlined.BugReport,
            screen = RecommendationDiagnosticsSettingsScreen(),
        ),
    )
}
// KMK <--
