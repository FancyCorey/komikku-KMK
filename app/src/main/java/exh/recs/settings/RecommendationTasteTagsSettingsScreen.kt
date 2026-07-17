package exh.recs.settings

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import tachiyomi.domain.taste.model.TagPreference
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

// KMK v0.8.8 -->
/** "Taste and Tags" detail screen — extracted verbatim (tag preference chips + add/edit dialog). Pure move, zero preference-behavior change. */
class RecommendationTasteTagsSettingsScreen(
    // KMK v0.8.10: see RecommendationForYouSettingsScreen.anchor.
    val anchor: String? = null,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { RecommendationsSettingsScreenModel() }
        val state by screenModel.state.collectAsState()
        val lazyListState = rememberLazyListState()
        val itemKeysInOrder = remember { listOf("tag_header", "tag_content") }
        ScrollToAnchorEffect(lazyListState, itemKeysInOrder, anchor)

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.rec_settings_index_taste_tags),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            LazyColumn(state = lazyListState, contentPadding = contentPadding) {
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
