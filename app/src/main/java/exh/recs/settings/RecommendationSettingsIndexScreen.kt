package exh.recs.settings

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
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
 * `RecommendationsSettingsScreen` has been retired entirely. Each detail screen was extracted from that
 * former screen's corresponding section: same controls, same `RecommendationsSettingsScreenModel`
 * methods, same preference reads/writes, zero behavior change — only the screen boundary is new.
 * Scroll-to-section is no longer relevant now that each category is its own screen (it was declined
 * in the earlier implementation specifically because it wasn't safe within one shared, variable-length screen;
 * that constraint no longer applies once each category has its own bounded `LazyColumn`).
 *
 * KMK v0.8.11: the former "Background, network, and installer behavior" index row was removed. It
 * navigated to the exact same [SourceEvaluationScreen] as the "Source Evaluation" row (installer
 * mode/batch size/cleanup controls all live inside that screen), which read as two different
 * destinations but opened one page — confirmed by the user as confusing. Source Evaluation is now
 * the single index row that opens that screen; installer/background search synonyms were folded
 * into the Source Evaluation search entry (see RecommendationSettingsSearchScreen).
 *
 * KMK v0.8.14: rebuilt to the approved five-section structure -- "For You" and "Matching and
 * versions" are retired as top-level destinations. Their controls were general taste/filter/display
 * controls and advanced version/quality controls respectively, not meaningful sections of their own.
 * Every control they owned still exists, reachable now under "Taste and filters" (rated visibility,
 * hide known manga, minimum chapter count) or "Management and diagnostics" (result budget, same-manga
 * matching, Best Version preview, group-preview budget) -- same `screenModel` methods, same
 * preference keys, zero behavior change.
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

    // KMK v0.8.14: the approved five-section Recommendation Settings structure. Do not add a sixth
    // top-level item or reintroduce "For You"/"Matching and versions" as destinations here -- see the
    // class doc above.
    private val items = listOf(
        Item(
            titleRes = KMR.strings.rec_settings_index_for_you_sources,
            subtitleRes = KMR.strings.rec_settings_index_for_you_sources_summary,
            icon = Icons.Outlined.Star,
            screen = RecommendationSourcePrioritySettingsScreen(),
        ),
        Item(
            titleRes = KMR.strings.rec_settings_index_taste_filters,
            subtitleRes = KMR.strings.rec_settings_index_taste_filters_summary,
            icon = Icons.Outlined.Label,
            screen = RecommendationTasteTagsSettingsScreen(),
        ),
        Item(
            titleRes = KMR.strings.rec_settings_index_evaluation,
            // KMK v0.8.16-fix1: dedicated short user-goal subtitle -- see the string's own doc comment.
            subtitleRes = KMR.strings.rec_settings_index_evaluation_summary,
            icon = Icons.Outlined.FactCheck,
            screen = SourceEvaluationScreen(),
        ),
        Item(
            titleRes = KMR.strings.rec_settings_index_discovery,
            // KMK v0.8.14-fix1: dedicated subtitle -- was rec_sources_to_try_header ("Sources To
            // Try"), which just repeated the row title in different casing.
            subtitleRes = KMR.strings.rec_settings_index_discovery_summary,
            icon = Icons.Outlined.ThumbUpAlt,
            screen = RecommendationNonInstalledDiscoverySettingsScreen(),
        ),
        // KMK v0.8.11: the "Background, network, and installer behavior" row was removed here -- it
        // duplicated the Source Evaluation destination above. See class doc.
        Item(
            titleRes = KMR.strings.rec_settings_index_diagnostics,
            // KMK v0.8.14-fix1: dedicated subtitle -- was rec_settings_management_header ("Source
            // management"), which undersold this section (it also holds languages, versions/quality,
            // and diagnostics, not just source management).
            subtitleRes = KMR.strings.rec_settings_index_management_summary,
            icon = Icons.Outlined.BugReport,
            screen = RecommendationDiagnosticsSettingsScreen(),
        ),
    )
}
// KMK <--
