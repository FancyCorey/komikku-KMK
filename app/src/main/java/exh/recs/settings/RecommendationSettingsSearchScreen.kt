package exh.recs.settings

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.paddingFromBaseline
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.UpIcon
import eu.kanade.presentation.util.Screen
import exh.recs.evaluation.SourceEvaluationScreen
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.util.runOnEnterKeyPressed

// KMK v0.8.9 -->
/**
 * Recommendation Settings search — mirrors `SettingsSearchScreen.kt`'s exact UI shape (TopAppBar
 * with an inline `BasicTextField`, clear button, `HorizontalDivider`, `Crossfade`-animated
 * `LazyColumn`/`EmptyScreen` result area) so the experience is indistinguishable in feel from main
 * Settings search, even though the underlying index is a parallel, purpose-built one (see
 * [RecommendationSettingsSearchIndex] for why). Opening this screen pushes on top of the current
 * Recommendation Settings navigation stack (it does not replace/clear it), and back returns to
 * whatever screen was open before search — never straight to the app root.
 */
class RecommendationSettingsSearchScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val softKeyboardController = LocalSoftwareKeyboardController.current
        val focusManager = LocalFocusManager.current
        val focusRequester = remember { FocusRequester() }
        val listState = rememberLazyListState()

        DisposableEffect(Unit) {
            onDispose { softKeyboardController?.hide() }
        }

        LaunchedEffect(listState.isScrollInProgress) {
            if (listState.isScrollInProgress) {
                focusManager.clearFocus()
            }
        }

        LaunchedEffect(focusRequester) {
            focusRequester.requestFocus()
        }

        val textFieldState = rememberTextFieldState()
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = navigator::pop) {
                                UpIcon()
                            }
                        },
                        title = {
                            BasicTextField(
                                state = textFieldState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                                    .runOnEnterKeyPressed(action = focusManager::clearFocus),
                                textStyle = MaterialTheme.typography.bodyLarge
                                    .copy(color = MaterialTheme.colorScheme.onSurface),
                                lineLimits = TextFieldLineLimits.SingleLine,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                onKeyboardAction = { focusManager.clearFocus() },
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorator = {
                                    if (textFieldState.text.isEmpty()) {
                                        Text(
                                            text = stringResource(KMR.strings.rec_settings_search_hint),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    }
                                    it()
                                },
                            )
                        },
                        actions = {
                            if (textFieldState.text.isNotEmpty()) {
                                IconButton(onClick = { textFieldState.clearText() }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = stringResource(KMR.strings.rec_settings_search_clear),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                    )
                    HorizontalDivider()
                }
            },
        ) { contentPadding ->
            RecommendationSettingsSearchResult(
                searchKey = textFieldState.text.toString(),
                contentPadding = contentPadding,
                onItemClick = { entry ->
                    navigator.push(entry.destination)
                },
            )
        }
    }
}

@Composable
private fun RecommendationSettingsSearchResult(
    searchKey: String,
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(),
    onItemClick: (RecommendationSettingsSearchIndex.Entry) -> Unit,
) {
    if (searchKey.isEmpty()) return

    val entries = rememberRecommendationSettingsSearchEntries()
    val result by produceState<List<RecommendationSettingsSearchIndex.Entry>?>(initialValue = null, searchKey, entries) {
        value = RecommendationSettingsSearchIndex.search(entries, searchKey).take(10)
    }

    Crossfade(targetState = result) { current ->
        when {
            current == null -> {}
            current.isEmpty() -> EmptyScreen(stringResource(MR.strings.no_results_found), modifier = modifier)
            else -> {
                LazyColumn(
                    modifier = modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                ) {
                    items(items = current, key = { it.key }) { entry ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onItemClick(entry) }
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                        ) {
                            Text(
                                text = entry.title,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                                fontWeight = FontWeight.Normal,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                // KMK v0.8.9: truthful unavailable state -- an entry for a currently
                                // unreachable setting is still shown, with an explicit note appended,
                                // rather than silently omitted from results.
                                text = if (entry.available) {
                                    entry.category
                                } else {
                                    stringResource(KMR.strings.rec_settings_search_unavailable, entry.category)
                                },
                                modifier = Modifier.paddingFromBaseline(top = 4.dp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The Recommendation Settings search index. Built once per composition with resolved
 * [stringResource]s, mirroring `SettingsSearchScreen.getIndex()`'s shape. Covers every category the
 * v0.8.9 plan names as a required example search term; see the v0.8.9 implementation report for the
 * explicit "full per-control enumeration was not attempted" scope decision.
 */
@Composable
internal fun rememberRecommendationSettingsSearchEntries(): List<RecommendationSettingsSearchIndex.Entry> {
    val forYou = stringResource(KMR.strings.rec_settings_index_for_you)
    val sourcePriority = stringResource(KMR.strings.rec_settings_index_source_priority)
    val tasteTags = stringResource(KMR.strings.rec_settings_index_taste_tags)
    val evaluation = stringResource(KMR.strings.rec_settings_index_evaluation)
    val discovery = stringResource(KMR.strings.rec_settings_index_discovery)
    val installer = stringResource(KMR.strings.rec_settings_index_installer)
    val diagnostics = stringResource(KMR.strings.rec_settings_index_diagnostics)

    return listOf(
        RecommendationSettingsSearchIndex.Entry(
            key = "for_you",
            title = forYou,
            summary = stringResource(KMR.strings.rec_settings_daily_recs_header),
            category = forYou,
            synonyms = listOf(
                "languages",
                "known manga",
                "hide disliked",
                "minimum chapters",
                "results per source",
                "ratings",
                "for you",
            ),
            destination = RecommendationForYouSettingsScreen(),
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "source_priority",
            title = sourcePriority,
            summary = stringResource(KMR.strings.rec_source_priority),
            category = sourcePriority,
            synonyms = listOf(
                "reorder sources",
                "top three",
                "extension order",
                "source order",
                "same manga",
                "match other versions",
                "results per extension",
                "best version",
            ),
            destination = RecommendationSourcePrioritySettingsScreen(),
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "taste_tags",
            title = tasteTags,
            summary = stringResource(KMR.strings.taste_settings_tag_prefs),
            category = tasteTags,
            synonyms = listOf("preferred tags", "blocked tags", "aliases", "taste"),
            destination = RecommendationTasteTagsSettingsScreen(),
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "evaluation",
            title = evaluation,
            summary = stringResource(KMR.strings.source_evaluation_settings_desc),
            category = evaluation,
            synonyms = listOf(
                "evaluate sources",
                "reassess",
                "outdated evaluations",
                "recommendation quality",
            ),
            destination = SourceEvaluationScreen(),
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "discovery",
            title = discovery,
            summary = stringResource(KMR.strings.rec_sources_to_try_header),
            category = discovery,
            synonyms = listOf("install extensions", "sources to try"),
            destination = RecommendationNonInstalledDiscoverySettingsScreen(),
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "installer",
            title = installer,
            summary = stringResource(KMR.strings.source_evaluation_settings_experimental_header),
            category = installer,
            synonyms = listOf("private installer", "shizuku", "background", "network", "batch size"),
            destination = SourceEvaluationScreen(),
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "diagnostics",
            title = diagnostics,
            summary = stringResource(KMR.strings.rec_settings_management_header),
            category = diagnostics,
            synonyms = listOf(
                "cache",
                "reset discovery",
                "diagnostics",
                "best version history",
                "enrichment cap",
                "clear discovery history",
            ),
            destination = RecommendationDiagnosticsSettingsScreen(),
        ),
    )
}
// KMK <--
