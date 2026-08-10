package exh.recs.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import tachiyomi.domain.taste.model.RatedMangaVisibility
import tachiyomi.domain.taste.model.TagPreference
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

// KMK v0.8.8 -->
/**
 * "Taste and filters" detail screen -- tag preference chips, add/edit dialog, and rating-derived
 * suggestions, extracted verbatim from the former single settings screen.
 *
 * KMK v0.8.14: renamed from "Taste and tags" and now also owns the former For You visibility/filter
 * controls (rated/known-manga visibility, hide known manga, minimum chapter count) -- these are
 * general taste/filter controls that decide what counts as "already seen"/"rated" for scoring and
 * display, the same category as tag preferences, not a meaningful standalone "For You" destination.
 * Moved from the retired `RecommendationForYouSettingsScreen`: same `screenModel` methods
 * (`setRatedMangaVisibility`/`setHideKnownManga`/`setMinChapterCount`), same preference keys, same
 * `RatedVisibilityContent`/`HideKnownMangaRow`/`SameMangaListPrefRow` composables -- pure move, zero
 * preference-behavior change.
 */
class RecommendationTasteTagsSettingsScreen(
    // KMK v0.8.10: see former RecommendationForYouSettingsScreen.anchor.
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
                "rated_header",
                "rated_content",
                "hide_known_manga",
                "min_chapter_count",
                "tag_header",
                "tag_content",
                "suggestions_header",
                "suggestions_content",
            )
        }
        ScrollToAnchorEffect(lazyListState, itemKeysInOrder, anchor)

        // KMK v0.8.18-fix1: the right-edge quick-access panel was removed from this and every other
        // Recommendation Settings detail screen -- see exh.recs.settings shared components for its new
        // home (For You / Loved / Liked / Disliked only).
        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.rec_settings_index_taste_filters),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            val layoutDirection = LocalLayoutDirection.current
            val listContentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                end = contentPadding.calculateEndPadding(layoutDirection),
                bottom = contentPadding.calculateBottomPadding(),
            )
            Column(modifier = Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
                RecommendationSettingsQuickAccessRow(
                    current = RecommendationSettingsQuickAccessDestination.TasteAndFilters,
                    onNavigate = { destination -> navigator.replace(destination.toScreen()) },
                )
                LazyColumn(state = lazyListState, contentPadding = listContentPadding, modifier = Modifier.weight(1f)) {
                    // KMK v0.8.14: moved in from the retired For You screen -- see class doc.
                    item(key = "rated_header") {
                        val visibilityLabel = stringResource(
                            when (state.ratedMangaVisibility) {
                                RatedMangaVisibility.HIDE_DISLIKED_ONLY -> KMR.strings.taste_visibility_hide_disliked
                                RatedMangaVisibility.HIDE_ALL_RATED -> KMR.strings.taste_visibility_hide_all
                                RatedMangaVisibility.SHOW_ALL_RATED -> KMR.strings.taste_visibility_show_all
                            },
                        )
                        val ratedSummary = if (state.minChapterCount > 0) {
                            stringResource(KMR.strings.rec_settings_summary_ratings_known_manga, visibilityLabel, state.minChapterCount)
                        } else {
                            stringResource(KMR.strings.rec_settings_summary_ratings_known_manga_no_min, visibilityLabel)
                        }
                        SectionHeader(stringResource(KMR.strings.rec_settings_ratings_known_manga_header), summary = ratedSummary)
                    }
                    item(key = "rated_content") {
                        RatedVisibilityContent(
                            current = state.ratedMangaVisibility,
                            onSelect = screenModel::setRatedMangaVisibility,
                        )
                    }
                    item(key = "hide_known_manga") {
                        HideKnownMangaRow(
                            enabled = state.hideKnownManga,
                            onToggle = { screenModel.setHideKnownManga(!state.hideKnownManga) },
                        )
                    }
                    item(key = "min_chapter_count") {
                        val offLabel = stringResource(KMR.strings.rec_min_chapter_count_off)
                        // KMK v0.8.18-fix1: audited against Komikku's official SliderPreference
                        // (IntProgression-based) during the numeric-settings standardization pass --
                        // kept as a list/dialog control. Options are irregularly spaced (0, 5, 10, 20,
                        // 50; no consistent step) and 0 needs the special "Off" label, neither of which
                        // maps cleanly onto a slider without changing what values are selectable.
                        // KMK: the supported
                        // values are no longer hardcoded here -- they come from the shared
                        // RecommendationMinChapterCountPolicy that the preference read boundaries and
                        // the setter also use, so the picker can never offer a value the pipeline
                        // rejects (or omit one it accepts). Widget, section placement, and the "Off"
                        // label rule are unchanged.
                        SameMangaListPrefRow(
                            title = stringResource(KMR.strings.rec_min_chapter_count),
                            summary = stringResource(KMR.strings.rec_min_chapter_count_summary),
                            current = exh.recs.RecommendationMinChapterCountPolicy.resolve(state.minChapterCount),
                            options = exh.recs.RecommendationMinChapterCountPolicy.SUPPORTED_VALUES,
                            valueLabel = { if (it == exh.recs.RecommendationMinChapterCountPolicy.OFF) offLabel else "$it" },
                            onSelect = screenModel::setMinChapterCount,
                        )
                    }

                    item(key = "tag_header") {
                        val tagCounts = RecommendationSettingsSectionSummaries.tagCounts(state.tagPreferences)
                        SectionHeader(
                            stringResource(KMR.strings.taste_settings_tag_prefs),
                            summary = stringResource(KMR.strings.rec_settings_summary_tags, tagCounts.preferred, tagCounts.blocked),
                        )
                    }
                    item(key = "tag_content") {
                        TagPreferencesContent(
                            tags = state.tagPreferences,
                            onAddClicked = screenModel::openAddTagDialog,
                            onEditClicked = screenModel::openEditTagDialog,
                            onDeleteClicked = screenModel::removeTagPreference,
                        )
                    }
                    // KMK v0.8.10: taste suggestions -- derived purely from the user's own rated manga,
                    // added via the exact same TagPreference mutation as the dialog above.
                    item(key = "suggestions_header") {
                        SectionHeader(stringResource(KMR.strings.taste_suggestions_header))
                    }
                    item(key = "suggestions_content") {
                        TasteSuggestionsContent(
                            suggestions = state.tasteSuggestions,
                            onAddPreferred = { screenModel.addTasteSuggestion(it, TagPreference.PREFER) },
                            onAddBlocked = { screenModel.addTasteSuggestion(it, TagPreference.BLOCK) },
                        )
                    }
                }
            }
        }

        when (val dialog = state.dialog) {
            RecommendationsSettingsScreenModel.Dialog.AddTag -> {
                TagPreferenceDialog(
                    initialName = "",
                    initialPreference = TagPreference.PREFER,
                    onDismiss = screenModel::dismissDialog,
                    onConfirm = { name, pref ->
                        screenModel.setTagPreference(name, pref)
                        screenModel.dismissDialog()
                    },
                )
            }
            is RecommendationsSettingsScreenModel.Dialog.EditTag -> {
                TagPreferenceDialog(
                    initialName = dialog.tag.displayName,
                    initialPreference = TagPreference.fromValue(dialog.tag.preference) ?: TagPreference.PREFER,
                    onDismiss = screenModel::dismissDialog,
                    onConfirm = { name, pref ->
                        screenModel.setTagPreference(name, pref)
                        screenModel.dismissDialog()
                    },
                )
            }
            null -> {}
        }
    }
}
// KMK <--
