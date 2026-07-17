package exh.recs.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

// KMK v0.8.8 -->
/**
 * "Management and Diagnostics" detail screen — extracted verbatim: Best Version history entry
 * point, discovery/cache management (enrichment cap, reset discovery history). The former screen's
 * "Open Source Evaluation" button/description was dropped here rather than duplicated, since
 * `RecommendationSettingsIndexScreen` already routes Evaluation directly to its own dedicated
 * `SourceEvaluationScreen` — keeping a second entry point to the same destination inside this screen
 * would be redundant navigation, not a preserved behavior. Every other control, `screenModel`
 * method, and preference read/write is byte-for-byte identical to before.
 */
class RecommendationDiagnosticsSettingsScreen(
    // KMK v0.8.10: see RecommendationForYouSettingsScreen.anchor.
    val anchor: String? = null,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { RecommendationsSettingsScreenModel() }
        val state by screenModel.state.collectAsState()
        val lazyListState = rememberLazyListState()
        val itemKeysInOrder = remember {
            listOf(
                "management_header",
                "quality_signal_history_entry",
                "discovery_cache_header",
                "enrichment_cap",
                "clear_discovery_history",
            )
        }
        ScrollToAnchorEffect(lazyListState, itemKeysInOrder, anchor)

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.rec_settings_index_diagnostics),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            LazyColumn(state = lazyListState, contentPadding = contentPadding) {
                item(key = "management_header") {
                    SectionHeader(stringResource(KMR.strings.rec_settings_management_header))
                }
                item(key = "quality_signal_history_entry") {
                    TextButton(
                        onClick = { navigator.push(QualitySignalHistoryScreen()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MaterialTheme.padding.medium),
                    ) {
                        Text(stringResource(KMR.strings.quality_signal_history_open_button))
                    }
                }

                item(key = "discovery_cache_header") {
                    SectionHeader(stringResource(KMR.strings.rec_settings_discovery_cache_header))
                }
                item(key = "enrichment_cap") {
                    SameMangaListPrefRow(
                        title = stringResource(KMR.strings.rec_enrichment_cap_title),
                        summary = stringResource(KMR.strings.rec_enrichment_cap_summary),
                        current = state.enrichmentCap,
                        options = listOf(1, 2, 3, 5, 10, 15, 20),
                        onSelect = screenModel::setEnrichmentCap,
                    )
                }
                item(key = "clear_discovery_history") {
                    TextButton(
                        onClick = screenModel::requestClearDiscoveryHistory,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MaterialTheme.padding.medium),
                    ) {
                        Text(stringResource(KMR.strings.rec_reset_discovery_history))
                    }
                }
            }
        }

        if (state.showClearDiscoveryHistoryDialog) {
            AlertDialog(
                onDismissRequest = screenModel::dismissClearDiscoveryHistoryDialog,
                title = { Text(stringResource(KMR.strings.rec_reset_discovery_history)) },
                text = { Text(stringResource(KMR.strings.rec_reset_discovery_history_confirm)) },
                confirmButton = {
                    TextButton(onClick = screenModel::confirmClearDiscoveryHistory) {
                        Text(stringResource(MR.strings.action_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = screenModel::dismissClearDiscoveryHistoryDialog) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }
    }
}
// KMK <--
