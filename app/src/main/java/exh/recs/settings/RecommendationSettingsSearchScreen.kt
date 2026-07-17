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
 * [stringResource]s, mirroring `SettingsSearchScreen.getIndex()`'s shape.
 *
 * KMK v0.8.10: extended from category-only to category-plus-per-control indexing. The original
 * seven category entries remain first and unchanged (per plan: "preserve the current category-level
 * search as a fallback") — every entry added after them targets one specific control, subsection, or
 * action, with a stable [RecommendationSettingsSearchIndex.Entry.anchor] wherever the destination
 * screen supports scroll-to-anchor (see [RecommendationSettingsAnchorScroll]). Entries for
 * `SourceEvaluationScreen` controls (Evaluation/Installer categories) intentionally have no anchor
 * yet — that screen's own anchor wiring is deferred to the Phase F UI-structure pass so it isn't
 * touched twice; those entries still resolve to the correct screen, just without a scroll target,
 * matching the plan's explicit "when that screen supports it" allowance.
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
        // KMK v0.8.9: category-level fallback entries -- unchanged.
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

        // KMK v0.8.10: per-control entries below. Titles/summaries reuse the same string
        // resources those controls already render with in their own screen, so this index never
        // introduces new user-visible text.

        // -- For You --
        RecommendationSettingsSearchIndex.Entry(
            key = "for_you_languages",
            title = stringResource(KMR.strings.rec_settings_daily_recs_header),
            summary = stringResource(KMR.strings.rec_source_languages_summary),
            category = forYou,
            synonyms = listOf("languages", "language filter"),
            destination = RecommendationForYouSettingsScreen(anchor = "lang_content"),
            anchor = "lang_content",
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "for_you_rated_visibility",
            title = stringResource(KMR.strings.rec_settings_ratings_known_manga_header),
            summary = stringResource(KMR.strings.taste_visibility_hide_disliked),
            category = forYou,
            synonyms = listOf("rated manga visibility", "hide disliked", "hide all rated", "show all rated"),
            destination = RecommendationForYouSettingsScreen(anchor = "rated_content"),
            anchor = "rated_content",
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "for_you_hide_known_manga",
            title = stringResource(KMR.strings.rec_hide_known_manga),
            summary = stringResource(KMR.strings.rec_hide_known_manga_summary),
            category = forYou,
            synonyms = listOf("known manga", "hide known"),
            destination = RecommendationForYouSettingsScreen(anchor = "hide_known_manga"),
            anchor = "hide_known_manga",
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "for_you_min_chapter_count",
            title = stringResource(KMR.strings.rec_min_chapter_count),
            summary = stringResource(KMR.strings.rec_min_chapter_count_summary),
            category = forYou,
            synonyms = listOf("minimum chapters", "chapter count filter"),
            destination = RecommendationForYouSettingsScreen(anchor = "min_chapter_count"),
            anchor = "min_chapter_count",
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "for_you_result_budget",
            title = stringResource(KMR.strings.rec_result_budget_title),
            summary = stringResource(KMR.strings.rec_result_budget_summary),
            category = forYou,
            synonyms = listOf("for you results", "result budget"),
            destination = RecommendationForYouSettingsScreen(anchor = "result_budget"),
            anchor = "result_budget",
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "for_you_group_preview_budget",
            title = stringResource(KMR.strings.rec_group_preview_budget_title),
            summary = stringResource(KMR.strings.rec_group_preview_budget_summary),
            category = forYou,
            synonyms = listOf("group recommendations", "group preview budget"),
            destination = RecommendationForYouSettingsScreen(anchor = "group_preview_budget"),
            anchor = "group_preview_budget",
        ),

        // -- Source Priority --
        RecommendationSettingsSearchIndex.Entry(
            key = "source_priority_reset",
            title = stringResource(KMR.strings.rec_restore_default_source_order),
            summary = stringResource(KMR.strings.rec_source_priority),
            category = sourcePriority,
            synonyms = listOf("reset source order", "default order"),
            destination = RecommendationSourcePrioritySettingsScreen(anchor = "source_reset_button"),
            anchor = "source_reset_button",
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "source_priority_suggest_order",
            title = stringResource(KMR.strings.rec_suggest_source_order_button),
            summary = stringResource(KMR.strings.rec_suggest_source_order_note),
            category = sourcePriority,
            synonyms = listOf("suggest order", "fit based order"),
            // KMK v0.8.10: this control is itself conditionally present (state.suggestFitOrderAvailable);
            // the destination screen opens correctly either way -- the anchor simply won't resolve
            // to a visible row if the button isn't currently rendered, which is a silent, safe no-op
            // (see ScrollToAnchorEffect), not a broken result.
            destination = RecommendationSourcePrioritySettingsScreen(anchor = "source_suggest_order_button"),
            anchor = "source_suggest_order_button",
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "same_manga_results_per_source",
            title = stringResource(KMR.strings.same_manga_match_results_per_source_title),
            summary = stringResource(KMR.strings.same_manga_match_results_per_source_summary),
            category = sourcePriority,
            synonyms = listOf("same manga matching", "results per source"),
            destination = RecommendationSourcePrioritySettingsScreen(anchor = "same_manga_results_per_source"),
            anchor = "same_manga_results_per_source",
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "same_manga_preselect",
            title = stringResource(KMR.strings.same_manga_match_preselect_title),
            summary = stringResource(KMR.strings.same_manga_match_preselect_summary),
            category = sourcePriority,
            synonyms = listOf("preselect results", "selection defaults"),
            destination = RecommendationSourcePrioritySettingsScreen(anchor = "same_manga_preselect"),
            anchor = "same_manga_preselect",
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "best_version_sample_size",
            title = stringResource(KMR.strings.best_version_preview_pages_title),
            summary = stringResource(KMR.strings.best_version_preview_pages_summary),
            category = sourcePriority,
            synonyms = listOf("best version", "preview pages", "sample size"),
            destination = RecommendationSourcePrioritySettingsScreen(anchor = "best_version_sample_size"),
            anchor = "best_version_sample_size",
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "best_version_avoid_first_pages",
            title = stringResource(KMR.strings.best_version_avoid_first_pages_title),
            summary = stringResource(KMR.strings.best_version_avoid_first_pages_summary),
            category = sourcePriority,
            synonyms = listOf("best version", "avoid first pages"),
            destination = RecommendationSourcePrioritySettingsScreen(anchor = "best_version_avoid_first_pages"),
            anchor = "best_version_avoid_first_pages",
        ),

        // -- Source Evaluation / Installer (anchor deferred to Phase F, see class doc) --
        RecommendationSettingsSearchIndex.Entry(
            key = "evaluation_batch_size",
            title = stringResource(KMR.strings.source_evaluation_settings_desc),
            summary = stringResource(KMR.strings.source_evaluation_settings_desc),
            category = evaluation,
            synonyms = listOf("batch size", "evaluation batch"),
            destination = SourceEvaluationScreen(),
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "evaluation_stale_reassessment",
            title = stringResource(KMR.strings.source_evaluation_settings_desc),
            summary = stringResource(KMR.strings.source_evaluation_settings_desc),
            category = evaluation,
            synonyms = listOf("stale", "outdated", "continue reassessing", "reassess outdated"),
            destination = SourceEvaluationScreen(),
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "evaluation_diagnostics",
            title = stringResource(KMR.strings.source_evaluation_settings_desc),
            summary = stringResource(KMR.strings.source_evaluation_settings_desc),
            category = evaluation,
            synonyms = listOf("evaluation diagnostics", "quarantine", "error rows", "retry"),
            destination = SourceEvaluationScreen(),
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "installer_mode",
            title = stringResource(KMR.strings.source_evaluation_settings_experimental_header),
            summary = stringResource(KMR.strings.source_evaluation_settings_experimental_header),
            category = installer,
            synonyms = listOf("private installer", "shizuku", "root installer", "cancellation", "cleanup"),
            destination = SourceEvaluationScreen(),
        ),

        // -- Sources To Try --
        RecommendationSettingsSearchIndex.Entry(
            key = "sources_to_try_install_visible",
            title = stringResource(KMR.strings.rec_suggestion_select),
            summary = stringResource(KMR.strings.rec_sources_to_try_header),
            category = discovery,
            synonyms = listOf("install visible", "bulk install", "select suggestions"),
            destination = RecommendationNonInstalledDiscoverySettingsScreen(anchor = "suggestions_bulk_install"),
            anchor = "suggestions_bulk_install",
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "sources_to_try_clear_dismissed",
            title = stringResource(KMR.strings.rec_clear_dismissed_suggestions),
            summary = stringResource(KMR.strings.rec_sources_to_try_header),
            category = discovery,
            synonyms = listOf("dismissed suggestions", "clear dismissed"),
            destination = RecommendationNonInstalledDiscoverySettingsScreen(anchor = "suggestions_clear_dismissed"),
            anchor = "suggestions_clear_dismissed",
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "sources_to_try_quality_marks",
            title = stringResource(KMR.strings.source_quality_clear_all_marks),
            summary = stringResource(KMR.strings.rec_source_preference_scope_note),
            category = discovery,
            synonyms = listOf("source quality marks", "clear quality marks", "hidden sources recovery"),
            destination = RecommendationNonInstalledDiscoverySettingsScreen(anchor = "quality_marks_clear"),
            anchor = "quality_marks_clear",
        ),

        // -- Diagnostics --
        RecommendationSettingsSearchIndex.Entry(
            key = "best_version_history",
            title = stringResource(KMR.strings.quality_signal_history_open_button),
            summary = stringResource(KMR.strings.rec_settings_management_header),
            category = diagnostics,
            synonyms = listOf("best version history", "quality signal history"),
            destination = RecommendationDiagnosticsSettingsScreen(anchor = "quality_signal_history_entry"),
            anchor = "quality_signal_history_entry",
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "enrichment_cap",
            title = stringResource(KMR.strings.rec_enrichment_cap_title),
            summary = stringResource(KMR.strings.rec_enrichment_cap_summary),
            category = diagnostics,
            synonyms = listOf("enrichment cap", "metadata cap"),
            destination = RecommendationDiagnosticsSettingsScreen(anchor = "enrichment_cap"),
            anchor = "enrichment_cap",
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "reset_discovery_history",
            title = stringResource(KMR.strings.rec_reset_discovery_history),
            summary = stringResource(KMR.strings.rec_settings_discovery_cache_header),
            category = diagnostics,
            synonyms = listOf("reset discovery", "clear cache", "discovery cache"),
            destination = RecommendationDiagnosticsSettingsScreen(anchor = "clear_discovery_history"),
            anchor = "clear_discovery_history",
        ),
    )
}
// KMK <--
