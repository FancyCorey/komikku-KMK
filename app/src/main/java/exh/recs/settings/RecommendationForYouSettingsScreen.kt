package exh.recs.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import tachiyomi.domain.taste.model.RatedMangaVisibility
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

// KMK v0.8.8 -->
/**
 * "For You" detail screen — extracted verbatim from the former single
 * `RecommendationsSettingsScreen.kt` (daily-recommendations language selector, ratings/known-manga
 * visibility, min-chapter filter, For You/group-preview result budgets). Pure move: every control,
 * `screenModel` method, and preference read/write is byte-for-byte identical to before; only the
 * screen boundary is new.
 */
class RecommendationForYouSettingsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { RecommendationsSettingsScreenModel() }
        val state by screenModel.state.collectAsState()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.rec_settings_index_for_you),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            LazyColumn(contentPadding = contentPadding) {
                item(key = "lang_header") {
                    SectionHeader(
                        stringResource(KMR.strings.rec_settings_daily_recs_header),
                        summary = stringResource(KMR.strings.rec_settings_summary_languages, state.recommendationLanguages.size),
                    )
                }
                item(key = "lang_content") {
                    LanguageSelectorContent(
                        selectedLanguages = state.recommendationLanguages,
                        availableLanguages = state.availableLanguages,
                        onToggle = screenModel::toggleRecommendationLanguage,
                    )
                }

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
                    SameMangaListPrefRow(
                        title = stringResource(KMR.strings.rec_min_chapter_count),
                        summary = stringResource(KMR.strings.rec_min_chapter_count_summary),
                        current = state.minChapterCount,
                        options = listOf(0, 5, 10, 20, 50),
                        valueLabel = { if (it == 0) offLabel else "$it" },
                        onSelect = screenModel::setMinChapterCount,
                    )
                }
                item(key = "result_budget") {
                    SameMangaListPrefRow(
                        title = stringResource(KMR.strings.rec_result_budget_title),
                        summary = stringResource(KMR.strings.rec_result_budget_summary),
                        current = state.resultBudget,
                        options = listOf(5, 10, 15, 20, 30),
                        onSelect = screenModel::setResultBudget,
                    )
                }
                item(key = "group_preview_budget") {
                    SameMangaListPrefRow(
                        title = stringResource(KMR.strings.rec_group_preview_budget_title),
                        summary = stringResource(KMR.strings.rec_group_preview_budget_summary),
                        current = state.groupPreviewBudget,
                        options = listOf(5, 10, 15, 20, 30),
                        onSelect = screenModel::setGroupPreviewBudget,
                    )
                }
                item(key = "refresh_hint") {
                    Text(
                        text = stringResource(KMR.strings.rec_settings_refresh_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = MaterialTheme.padding.medium,
                            vertical = MaterialTheme.padding.extraSmall,
                        ),
                    )
                }
            }
        }
    }
}
// KMK <--
