package exh.recs.settings

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
    // KMK v0.8.13-fix1: this was previously `produceState(initialValue = null, ...)` wrapped in
    // `Crossfade(targetState = result)` -- every keystroke intentionally passed through a null state
    // and cross-faded from blank to the result list. RecommendationSettingsSearchIndex.search() is a
    // pure, synchronous, local (no I/O) computation, so there is no async work to justify
    // `produceState`/an intermediate loading state, and reproduced on-device evidence (dim/faded rows
    // during the fade, `uiautomator dump` failing with "could not get idle state" while it animated)
    // confirmed the crossfade was actively harmful here, not just unnecessary. `remember` recomputes
    // synchronously and rows render fully opaque immediately -- no intermediate state, no animation.
    val result = remember(entries, searchKey) {
        RecommendationSettingsSearchIndex.search(entries, searchKey).take(10)
    }

    when {
        result.isEmpty() -> EmptyScreen(stringResource(MR.strings.no_results_found), modifier = modifier)
        else -> {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = contentPadding,
            ) {
                items(items = result, key = { it.key }) { entry ->
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
                        // KMK v0.8.9: truthful unavailable state -- an entry for a currently
                        // unreachable setting is still shown, with an explicit note appended, rather
                        // than silently omitted from results.
                        // KMK v0.8.13-fix1: category-only rows show just the category; per-control
                        // rows (entry.anchor != null) show "Category - summary" so a control row is
                        // visually distinguishable from a category row and from other control rows
                        // sharing the same category, instead of every row in a category reading
                        // identically at a glance.
                        // KMK v0.8.14-fix1: a category row's summary previously fell back to
                        // `entry.category`, which for a category-level entry always equals
                        // `entry.title` -- every such row read as a literal duplicate ("Taste and
                        // filters" / "Taste and filters"). Now a category row shows its own distinct
                        // `summary` when one adds information beyond the title, or no subtitle at all
                        // rather than a duplicate-looking one.
                        val subtitle = when {
                            !entry.available -> stringResource(KMR.strings.rec_settings_search_unavailable, entry.category)
                            entry.anchor != null -> stringResource(KMR.strings.rec_settings_search_control_subtitle, entry.category, entry.summary)
                            entry.summary.isNotBlank() && entry.summary != entry.title && entry.summary != entry.category -> entry.summary
                            else -> null
                        }
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
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
 * KMK v0.8.10: extended from category-only to category-plus-per-control indexing. The category
 * entries remain first — every entry added after them targets one specific control, subsection, or
 * action, with a stable [RecommendationSettingsSearchIndex.Entry.anchor] wherever the destination
 * screen supports scroll-to-anchor (see [RecommendationSettingsAnchorScroll]).
 *
 * KMK v0.8.11: the separate "Background, network, and installer behavior" category entry was
 * removed (it opened the exact same `SourceEvaluationScreen` as the Source Evaluation entry); its
 * synonyms were folded into the Source Evaluation category entry. The four Source Evaluation
 * control entries now carry distinct titles/summaries and real anchors into that screen's sections,
 * and [RecommendationSettingsSearchIndex.search] deduplicates logical duplicates by
 * [RecommendationSettingsSearchIndex.Entry.dedupeKey].
 */
@Composable
internal fun rememberRecommendationSettingsSearchEntries(): List<RecommendationSettingsSearchIndex.Entry> {
    // KMK v0.8.14-fix1: renamed from sourcesLanguages/"Sources and languages" -- this screen no
    // longer owns language selection, only source order/enable/like/dislike/preview. See
    // RecommendationSourcePrioritySettingsScreen's class doc.
    val forYouSources = stringResource(KMR.strings.rec_settings_index_for_you_sources)
    val tasteFilters = stringResource(KMR.strings.rec_settings_index_taste_filters)
    val evaluation = stringResource(KMR.strings.rec_settings_index_evaluation)
    val discovery = stringResource(KMR.strings.rec_settings_index_discovery)
    val diagnostics = stringResource(KMR.strings.rec_settings_index_diagnostics)

    return listOf(
        // KMK v0.8.9: category-level fallback entries -- unchanged.
        // KMK v0.8.14: "For You" and "Matching and versions" category entries removed -- neither is
        // a top-level destination anymore. Their former synonyms moved with their controls to the
        // sourcesLanguages/tasteFilters/diagnostics category entries below.
        RecommendationSettingsSearchIndex.Entry(
            key = "for_you_sources",
            title = forYouSources,
            summary = stringResource(KMR.strings.rec_settings_index_for_you_sources_summary),
            category = forYouSources,
            // KMK v0.8.12: "same manga"/"match other versions"/"best version" synonyms moved to the
            // diagnostics entry below -- this screen no longer owns those controls, so it should not
            // own their search terms either.
            // KMK v0.8.14-fix1: "languages"/"language filter" moved OUT to the diagnostics entry --
            // language selection is no longer this screen's control, see
            // RecommendationDiagnosticsSettingsScreen.
            synonyms = listOf(
                "reorder sources",
                "top three",
                "extension order",
                "source order",
                "preferred source",
                "avoided source",
                "source priority",
                "sources and languages",
                "preview for you",
            ),
            destination = RecommendationSourcePrioritySettingsScreen(),
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "taste_filters",
            title = tasteFilters,
            summary = stringResource(KMR.strings.rec_settings_index_taste_filters_summary),
            category = tasteFilters,
            // KMK v0.8.14: the former "For You" category entry's rated/known/chapter-count synonyms
            // moved in here -- this screen now owns those controls.
            synonyms = listOf(
                "known manga",
                "hide disliked",
                "hide known",
                "minimum chapters",
                "ratings",
                "rated",
                "preferred tags",
                "blocked tags",
                "aliases",
                "taste",
                "tags",
                "block",
                "prefer",
            ),
            destination = RecommendationTasteTagsSettingsScreen(),
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "evaluation",
            title = evaluation,
            // KMK v0.8.16-fix1: dedicated short user-goal summary, matching the index row -- see
            // rec_settings_index_evaluation_summary's doc comment. Synonyms below (installer,
            // background, network, shizuku, ...) are unchanged, so those searches still resolve here.
            summary = stringResource(KMR.strings.rec_settings_index_evaluation_summary),
            category = evaluation,
            // KMK v0.8.11: the former separate "Background, network, and installer behavior"
            // category entry opened the exact same SourceEvaluationScreen -- it was removed and its
            // synonyms folded in here, so those searches now resolve to one destination row.
            synonyms = listOf(
                "evaluate sources",
                "reassess",
                "outdated evaluations",
                "recommendation quality",
                "installer",
                "shizuku",
                "background",
                "network",
            ),
            destination = SourceEvaluationScreen(),
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "discovery",
            title = discovery,
            // KMK v0.8.14-fix1: was rec_sources_to_try_header ("Sources To Try"), which just repeated
            // the title in different casing -- see the shared index-row summary rule above.
            summary = stringResource(KMR.strings.rec_settings_index_discovery_summary),
            category = discovery,
            synonyms = listOf("install extensions", "sources to try"),
            destination = RecommendationNonInstalledDiscoverySettingsScreen(),
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "diagnostics",
            title = diagnostics,
            // KMK v0.8.14-fix1: was rec_settings_management_header ("Source management"), which
            // undersold this section -- it also holds languages, versions/quality, and diagnostics.
            summary = stringResource(KMR.strings.rec_settings_index_management_summary),
            category = diagnostics,
            // KMK v0.8.14: the former "For You" result-budget synonyms and the former "Matching and
            // versions" same-manga/Best-Version/group-preview synonyms moved in here -- this screen
            // now owns those controls.
            // KMK v0.8.14-fix1: "languages"/"language filter" moved in from the sources entry above --
            // language selection is now Management and diagnostics' control.
            synonyms = listOf(
                "languages",
                "language filter",
                "recommendation languages",
                "cache",
                "reset discovery",
                "diagnostics",
                "best version history",
                "enrichment cap",
                "clear discovery history",
                "for you results",
                "result budget",
                "results per source",
                "same manga",
                "other versions",
                "find other versions",
                "best version",
                "preview pages",
                "avoid first pages",
                "group preview",
                "group recommendations",
            ),
            destination = RecommendationDiagnosticsSettingsScreen(),
        ),

        // KMK v0.8.10: per-control entries below. Titles/summaries reuse the same string
        // resources those controls already render with in their own screen, so this index never
        // introduces new user-visible text.

        // -- Taste and filters (former For You visibility/filter controls) --
        // KMK v0.8.14: destination changed from the retired RecommendationForYouSettingsScreen to
        // RecommendationTasteTagsSettingsScreen -- see that screen's class doc.
        RecommendationSettingsSearchIndex.Entry(
            key = "taste_filters_rated_visibility",
            title = stringResource(KMR.strings.rec_settings_ratings_known_manga_header),
            summary = stringResource(KMR.strings.taste_visibility_hide_disliked),
            category = tasteFilters,
            synonyms = listOf("rated manga visibility", "hide disliked", "hide all rated", "show all rated"),
            destination = RecommendationTasteTagsSettingsScreen(anchor = "rated_content"),
            anchor = "rated_content",
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "taste_filters_hide_known_manga",
            title = stringResource(KMR.strings.rec_hide_known_manga),
            summary = stringResource(KMR.strings.rec_hide_known_manga_summary),
            category = tasteFilters,
            synonyms = listOf("known manga", "hide known"),
            destination = RecommendationTasteTagsSettingsScreen(anchor = "hide_known_manga"),
            anchor = "hide_known_manga",
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "taste_filters_min_chapter_count",
            title = stringResource(KMR.strings.rec_min_chapter_count),
            summary = stringResource(KMR.strings.rec_min_chapter_count_summary),
            category = tasteFilters,
            synonyms = listOf("minimum chapters", "chapter count filter"),
            destination = RecommendationTasteTagsSettingsScreen(anchor = "min_chapter_count"),
            anchor = "min_chapter_count",
        ),

        // -- For You sources --
        // KMK v0.8.14-fix1: "source_priority_languages" control entry removed here -- language
        // selection moved to Management and diagnostics, see "diagnostics_languages" below.
        RecommendationSettingsSearchIndex.Entry(
            key = "source_priority_reset",
            title = stringResource(KMR.strings.rec_restore_default_source_order),
            summary = stringResource(KMR.strings.rec_source_priority),
            category = forYouSources,
            synonyms = listOf("reset source order", "default order"),
            destination = RecommendationSourcePrioritySettingsScreen(anchor = "source_reset_button"),
            anchor = "source_reset_button",
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "source_priority_suggest_order",
            title = stringResource(KMR.strings.rec_suggest_source_order_button),
            summary = stringResource(KMR.strings.rec_suggest_source_order_note),
            category = forYouSources,
            synonyms = listOf("suggest order", "fit based order"),
            // KMK v0.8.10: this control is itself conditionally present (state.suggestFitOrderAvailable);
            // the destination screen opens correctly either way -- the anchor simply won't resolve
            // to a visible row if the button isn't currently rendered, which is a silent, safe no-op
            // (see ScrollToAnchorEffect), not a broken result.
            destination = RecommendationSourcePrioritySettingsScreen(anchor = "source_suggest_order_button"),
            anchor = "source_suggest_order_button",
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "source_priority_preview",
            title = stringResource(KMR.strings.rec_preview_for_you_title),
            summary = stringResource(KMR.strings.rec_preview_for_you_summary),
            category = forYouSources,
            synonyms = listOf("preview for you", "for you preview", "snapshot"),
            destination = RecommendationSourcePrioritySettingsScreen(anchor = "preview_for_you"),
            anchor = "preview_for_you",
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "diagnostics_languages",
            title = stringResource(KMR.strings.rec_settings_management_languages_header),
            summary = stringResource(KMR.strings.rec_settings_management_languages_summary),
            category = diagnostics,
            synonyms = listOf("languages", "language filter", "recommendation languages"),
            destination = RecommendationDiagnosticsSettingsScreen(anchor = "recommendation_languages_content"),
            anchor = "recommendation_languages_content",
        ),

        // -- Management and diagnostics: display/performance + versions and quality (former For You
        // result budget and former Matching-and-versions controls) --
        // KMK v0.8.14: destination changed from the retired RecommendationForYouSettingsScreen/
        // RecommendationMatchingVersionsSettingsScreen to RecommendationDiagnosticsSettingsScreen --
        // see that screen's class doc.
        RecommendationSettingsSearchIndex.Entry(
            key = "diagnostics_result_budget",
            title = stringResource(KMR.strings.rec_result_budget_title),
            summary = stringResource(KMR.strings.rec_result_budget_summary),
            category = diagnostics,
            synonyms = listOf("for you results", "result budget"),
            destination = RecommendationDiagnosticsSettingsScreen(anchor = "result_budget"),
            anchor = "result_budget",
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "same_manga_results_per_source",
            title = stringResource(KMR.strings.same_manga_match_results_per_source_title),
            summary = stringResource(KMR.strings.same_manga_match_results_per_source_summary),
            category = diagnostics,
            synonyms = listOf("same manga matching", "find other versions", "results per source"),
            destination = RecommendationDiagnosticsSettingsScreen(anchor = "same_manga_results_per_source"),
            anchor = "same_manga_results_per_source",
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "same_manga_preselect",
            title = stringResource(KMR.strings.same_manga_match_preselect_title),
            summary = stringResource(KMR.strings.same_manga_match_preselect_summary),
            category = diagnostics,
            synonyms = listOf("preselect results", "selection defaults"),
            destination = RecommendationDiagnosticsSettingsScreen(anchor = "same_manga_preselect"),
            anchor = "same_manga_preselect",
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "best_version_sample_size",
            title = stringResource(KMR.strings.best_version_preview_pages_title),
            summary = stringResource(KMR.strings.best_version_preview_pages_summary),
            category = diagnostics,
            synonyms = listOf("best version", "preview pages", "sample size"),
            destination = RecommendationDiagnosticsSettingsScreen(anchor = "best_version_sample_size"),
            anchor = "best_version_sample_size",
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "best_version_avoid_first_pages",
            title = stringResource(KMR.strings.best_version_avoid_first_pages_title),
            summary = stringResource(KMR.strings.best_version_avoid_first_pages_summary),
            category = diagnostics,
            synonyms = listOf("best version", "avoid first pages"),
            destination = RecommendationDiagnosticsSettingsScreen(anchor = "best_version_avoid_first_pages"),
            anchor = "best_version_avoid_first_pages",
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "group_preview_budget",
            title = stringResource(KMR.strings.rec_group_preview_budget_title),
            summary = stringResource(KMR.strings.rec_group_preview_budget_summary),
            category = diagnostics,
            synonyms = listOf("group recommendations", "group preview budget", "group preview"),
            destination = RecommendationDiagnosticsSettingsScreen(anchor = "group_preview_budget"),
            anchor = "group_preview_budget",
        ),

        // -- Source Evaluation controls --
        // KMK v0.8.11: these four entries previously all reused the same long category description
        // as both title and summary, so a search like "fil" produced a cluster of identical-looking
        // Source Evaluation rows. Each now has a distinct title/summary and a real in-screen anchor
        // (SourceEvaluationScreen gained anchor support in the same pass).
        RecommendationSettingsSearchIndex.Entry(
            key = "evaluation_batch_size",
            title = stringResource(KMR.strings.source_evaluation_batch_size),
            summary = stringResource(KMR.strings.rec_search_entry_batch_size_summary),
            category = evaluation,
            synonyms = listOf("batch size", "evaluation batch"),
            destination = SourceEvaluationScreen(anchor = "batch_size"),
            anchor = "batch_size",
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "evaluation_stale_reassessment",
            title = stringResource(KMR.strings.rec_search_entry_reassess_outdated_title),
            summary = stringResource(KMR.strings.rec_search_entry_reassess_outdated_summary),
            category = evaluation,
            synonyms = listOf("stale", "outdated", "continue reassessing", "reassess outdated"),
            destination = SourceEvaluationScreen(anchor = "reassess_header"),
            anchor = "reassess_header",
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "evaluation_diagnostics",
            title = stringResource(KMR.strings.rec_search_entry_evaluation_diagnostics_title),
            summary = stringResource(KMR.strings.rec_search_entry_evaluation_diagnostics_summary),
            category = evaluation,
            synonyms = listOf("evaluation diagnostics", "quarantine", "error rows", "retry"),
            destination = SourceEvaluationScreen(anchor = "diagnostics_header"),
            anchor = "diagnostics_header",
        ),
        RecommendationSettingsSearchIndex.Entry(
            key = "installer_mode",
            title = stringResource(KMR.strings.source_evaluation_installer_mode),
            summary = stringResource(KMR.strings.rec_search_entry_installer_mode_summary),
            category = evaluation,
            synonyms = listOf("installer", "shizuku", "cleanup", "install prompts"),
            destination = SourceEvaluationScreen(anchor = "installer_header"),
            anchor = "installer_header",
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
