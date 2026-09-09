package exh.recs.settings

// KMK v0.8.8 -->
// Composables shared across the per-category Recommendation Settings screens
// (RecommendationSourcePrioritySettingsScreen ("For You sources"), RecommendationTasteTagsSettingsScreen,
// RecommendationNonInstalledDiscoverySettingsScreen, RecommendationDiagnosticsSettingsScreen) --
// extracted verbatim from the former single
// RecommendationsSettingsScreen.kt so every screen renders pixel-identical controls with zero
// behavior change. `internal` visibility (not `private`) so every screen in this package can use
// them without duplication.

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUpAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.Navigator
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.more.settings.widget.ListPreferenceWidget
import eu.kanade.presentation.more.settings.widget.PreferenceGroupHeader
import eu.kanade.presentation.more.settings.widget.PrefsHorizontalPadding
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.home.HomeScreen
import exh.recs.RecommendationSourceRunStatus
import exh.recs.RecommendationSourceStatus
import exh.recs.RecommendationSourceStatusExplanationPolicy
import exh.recs.SourceFitLabel
import exh.recs.SourceFitStats
import exh.recs.discovery.NonInstalledSourceSuggestion
import exh.recs.discovery.NonInstalledSuggestionReason
import exh.recs.discovery.SourcesToTrySortMode
import exh.recs.discovery.SuggestionConfidence
import exh.util.EvaluationModeFormatter
import exh.util.rememberEvaluationModeEnabled
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableCollectionItemScope
import tachiyomi.domain.taste.interactor.TasteDiagnosticsResult
import tachiyomi.domain.taste.interactor.TasteSuggestionCandidate
import tachiyomi.domain.taste.interactor.TasteSuggestionResult
import tachiyomi.domain.taste.model.RatedMangaVisibility
import tachiyomi.domain.taste.model.TagPreference
import tachiyomi.domain.taste.model.TagTaste
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import java.util.Locale
import kotlin.math.roundToInt

// KMK v0.8.7: optional one-line state summary under the title (plan section 5.2). Derived from
// state by the caller (never hardcoded) so it updates automatically whenever the underlying
// preference/process state changes, same as any other Compose recomposition.
// KMK v0.8.11: delegates to the official settings PreferenceGroupHeader (secondary color,
// bodyMedium, PrefsHorizontalPadding) instead of a bespoke bold primary-color header, so
// Recommendation Settings sections read like every other Komikku settings group. The optional
// summary keeps the same horizontal padding as settings widget subtitles.
@Composable
internal fun SectionHeader(title: String, summary: String? = null) {
    Column {
        HorizontalDivider(modifier = Modifier.padding(top = MaterialTheme.padding.small))
        PreferenceGroupHeader(title = title)
        if (summary != null) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = PrefsHorizontalPadding),
            )
        }
    }
}

/** Opens the For You tab through the same root-and-tab route from every settings surface. */
@Composable
internal fun rememberOpenForYouFromRecommendationSettings(navigator: Navigator): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        navigator.popUntilRoot()
        scope.launch {
            HomeScreen.openTab(HomeScreen.Tab.Browse(toForYou = true))
        }
    }
}

/**
 * Shared actions for every recommendation-settings detail screen.
 *
 * Search always returns to the detail screen through the normal Voyager stack. The optional Home
 * action is supplied by callers that expose the existing For You shortcut; keeping the callback
 * outside this helper preserves each screen's existing tab-selection and visibility rules.
 */
@Composable
internal fun RecommendationSettingsDetailActions(
    onSearch: () -> Unit,
    onHome: (() -> Unit)? = null,
) {
    val actions = if (onHome == null) {
        kotlinx.collections.immutable.persistentListOf(
            AppBar.Action(
                title = stringResource(KMR.strings.rec_settings_search),
                icon = Icons.Outlined.Search,
                onClick = onSearch,
            ),
        )
    } else {
        kotlinx.collections.immutable.persistentListOf(
            AppBar.Action(
                title = stringResource(KMR.strings.rec_settings_search),
                icon = Icons.Outlined.Search,
                onClick = onSearch,
            ),
            AppBar.Action(
                title = stringResource(KMR.strings.source_evaluation_go_to_for_you),
                icon = Icons.Filled.Home,
                onClick = onHome,
            ),
        )
    }
    AppBarActions(actions)
}

// KMK v0.8.11: official SwitchPreferenceWidget instead of a hand-built Row/Switch, so this row is
// click-anywhere, single-toggle, and visually identical to every other Komikku settings switch.
@Composable
internal fun HideKnownMangaRow(
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    SwitchPreferenceWidget(
        title = stringResource(KMR.strings.rec_hide_known_manga),
        subtitle = stringResource(KMR.strings.rec_hide_known_manga_summary),
        checked = enabled,
        onCheckedChanged = { onToggle() },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RatedVisibilityContent(
    current: RatedMangaVisibility,
    onSelect: (RatedMangaVisibility) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium),
    ) {
        listOf(
            RatedMangaVisibility.HIDE_DISLIKED_ONLY to KMR.strings.taste_visibility_hide_disliked,
            RatedMangaVisibility.HIDE_ALL_RATED to KMR.strings.taste_visibility_hide_all,
            RatedMangaVisibility.SHOW_ALL_RATED to KMR.strings.taste_visibility_show_all,
        ).forEach { (option, labelRes) ->
            FilterChip(
                selected = current == option,
                onClick = { onSelect(option) },
                label = { Text(stringResource(labelRes)) },
            )
        }
    }
}

// KMK --> EC-04 2026-09-01: configurable discovery-effort policy. Same FlowRow-of-FilterChip
// pattern as RatedVisibilityContent above -- reused, not a new picker widget. Each chip shows its
// per-level description underneath so the real, measured trade-off (see DiscoveryEffortLevel's own
// KDoc) is disclosed at the point of choice, not hidden behind a generic label.
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DiscoveryEffortLevelContent(
    current: exh.recs.memory.DiscoveryEffortLevel,
    onSelect: (exh.recs.memory.DiscoveryEffortLevel) -> Unit,
) {
    val options = listOf(
        Triple(
            exh.recs.memory.DiscoveryEffortLevel.OFF,
            KMR.strings.rec_discovery_effort_off,
            KMR.strings.rec_discovery_effort_off_description,
        ),
        Triple(
            exh.recs.memory.DiscoveryEffortLevel.STANDARD,
            KMR.strings.rec_discovery_effort_standard,
            KMR.strings.rec_discovery_effort_standard_description,
        ),
        Triple(
            exh.recs.memory.DiscoveryEffortLevel.EXTENDED,
            KMR.strings.rec_discovery_effort_extended,
            KMR.strings.rec_discovery_effort_extended_description,
        ),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
            options.forEach { (option, labelRes, _) ->
                FilterChip(
                    selected = current == option,
                    onClick = { onSelect(option) },
                    label = { Text(stringResource(labelRes)) },
                )
            }
        }
        Text(
            text = stringResource(options.first { it.first == current }.third),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = MaterialTheme.padding.extraSmall),
        )
    }
}
// KMK <--

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LanguageSelectorContent(
    selectedLanguages: ImmutableSet<String>,
    availableLanguages: ImmutableList<String>,
    onToggle: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium),
    ) {
        availableLanguages.forEach { lang ->
            val selected = lang in selectedLanguages
            FilterChip(
                selected = selected,
                onClick = { onToggle(lang) },
                label = { Text(lang.uppercase(Locale.ROOT)) },
            )
        }
    }
}

// KMK v0.8.10-fix9: tag preferences are now grouped by TagPreference (Preferred/Blocked/Disliked/
// Other) instead of one continuous FlowRow, so Preferred and Blocked are both reachable near the top
// instead of Blocked being buried behind a long Preferred list. See TagPreferenceGroupingPolicy.
// KMK v0.8.12: reveal is now bounded (10 at a time via TasteSuggestionVisibilityPolicy) instead of
// revealing every remaining tag at once -- see TasteSuggestionGroup for the same treatment.

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagPreferenceGroup(
    label: String,
    tags: List<TagTaste>,
    onEditClicked: (TagTaste) -> Unit,
    onDeleteClicked: (String) -> Unit,
    saveKey: String,
) {
    if (tags.isEmpty()) return
    var visibleCount by rememberSaveable(saveKey) { mutableStateOf(TasteSuggestionVisibilityPolicy.DEFAULT_VISIBLE) }
    val visibleTags = TasteSuggestionVisibilityPolicy.visible(tags, visibleCount)

    Column(modifier = Modifier.padding(bottom = MaterialTheme.padding.small)) {
        Text(
            text = "$label (${tags.size})",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = MaterialTheme.padding.extraSmall),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            // KMK --> v0.8.19: evaluation mode tag-label obfuscation
            val evaluationModeEnabled = rememberEvaluationModeEnabled()
            // KMK <--
            visibleTags.forEach { tag ->
                val pref = TagPreference.fromValue(tag.preference)
                FilterChip(
                    selected = true,
                    onClick = { onEditClicked(tag) },
                    // KMK -->
                    label = {
                        Text(
                            text = if (evaluationModeEnabled) {
                                when (pref) {
                                    TagPreference.PREFER -> EvaluationModeFormatter.likedTagLabel(tag.normalizedTag)
                                    TagPreference.DISLIKE, TagPreference.BLOCK ->
                                        EvaluationModeFormatter.blockedTagLabel(tag.normalizedTag)
                                    null -> tag.displayName
                                }
                            } else {
                                tag.displayName
                            },
                        )
                    },
                    // KMK <--
                    leadingIcon = {
                        Icon(
                            imageVector = when (pref) {
                                TagPreference.PREFER -> Icons.Outlined.Done
                                TagPreference.DISLIKE -> Icons.Outlined.RemoveCircleOutline
                                TagPreference.BLOCK -> Icons.Outlined.Block
                                null -> Icons.Outlined.FavoriteBorder
                            },
                            contentDescription = null,
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { onDeleteClicked(tag.normalizedTag) }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(KMR.strings.accessibility_delete_tag),
                            )
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = when (pref) {
                            TagPreference.PREFER -> MaterialTheme.colorScheme.primaryContainer
                            TagPreference.DISLIKE -> MaterialTheme.colorScheme.secondaryContainer
                            TagPreference.BLOCK -> MaterialTheme.colorScheme.errorContainer
                            null -> MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
            if (TasteSuggestionVisibilityPolicy.canShowMore(tags.size, visibleCount)) {
                TextButton(onClick = { visibleCount = TasteSuggestionVisibilityPolicy.nextVisibleCount(tags.size, visibleCount) }) {
                    Text(
                        pluralStringResource(KMR.plurals.taste_settings_tag_group_show_n_more, count = TasteSuggestionVisibilityPolicy.nextVisibleCount(tags.size, visibleCount) - visibleCount, TasteSuggestionVisibilityPolicy.nextVisibleCount(tags.size, visibleCount) - visibleCount),
                    )
                }
            }
            if (TasteSuggestionVisibilityPolicy.canShowAll(tags.size, visibleCount)) {
                TextButton(onClick = { visibleCount = tags.size }) {
                    Text(stringResource(KMR.strings.taste_settings_tag_group_show_all, tags.size))
                }
            }
            if (TasteSuggestionVisibilityPolicy.canShowFewer(visibleCount)) {
                TextButton(onClick = { visibleCount = TasteSuggestionVisibilityPolicy.DEFAULT_VISIBLE }) {
                    Text(stringResource(KMR.strings.taste_settings_tag_group_show_fewer))
                }
            }
        }
    }
}

@Composable
internal fun TagPreferencesContent(
    tags: List<TagTaste>,
    onAddClicked: () -> Unit,
    onEditClicked: (TagTaste) -> Unit,
    onDeleteClicked: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium),
    ) {
        if (tags.isEmpty()) {
            Text(
                text = stringResource(KMR.strings.taste_settings_tag_prefs_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = MaterialTheme.padding.small),
            )
        } else {
            val grouped = remember(tags) { TagPreferenceGroupingPolicy.group(tags) }
            // Preferred and Blocked are the two controls users compare most, so both are rendered
            // first (in that order) rather than Preferred pushing Blocked out of initial view.
            TagPreferenceGroup(
                label = stringResource(KMR.strings.taste_pref_prefer),
                tags = grouped.preferred,
                onEditClicked = onEditClicked,
                onDeleteClicked = onDeleteClicked,
                saveKey = "tag_group_preferred",
            )
            TagPreferenceGroup(
                label = stringResource(KMR.strings.taste_pref_block),
                tags = grouped.blocked,
                onEditClicked = onEditClicked,
                onDeleteClicked = onDeleteClicked,
                saveKey = "tag_group_blocked",
            )
            TagPreferenceGroup(
                label = stringResource(KMR.strings.taste_pref_dislike),
                tags = grouped.disliked,
                onEditClicked = onEditClicked,
                onDeleteClicked = onDeleteClicked,
                saveKey = "tag_group_disliked",
            )
            TagPreferenceGroup(
                label = stringResource(KMR.strings.taste_settings_tag_prefs),
                tags = grouped.other,
                onEditClicked = onEditClicked,
                onDeleteClicked = onDeleteClicked,
                saveKey = "tag_group_other",
            )
        }
        TextButton(
            onClick = onAddClicked,
            modifier = Modifier.padding(top = MaterialTheme.padding.extraSmall),
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = null,
                modifier = Modifier.padding(end = MaterialTheme.padding.extraSmall),
            )
            Text(stringResource(KMR.strings.taste_settings_add_tag))
        }
    }
}

@Composable
internal fun TagPreferenceDialog(
    initialName: String,
    initialPreference: TagPreference,
    onDismiss: () -> Unit,
    onConfirm: (String, TagPreference) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var preference by remember { mutableStateOf(initialPreference) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(KMR.strings.taste_settings_tag_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(KMR.strings.taste_settings_tag_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    listOf(TagPreference.PREFER, TagPreference.DISLIKE, TagPreference.BLOCK).forEach { pref ->
                        FilterChip(
                            selected = preference == pref,
                            onClick = { preference = pref },
                            label = {
                                Text(
                                    stringResource(
                                        when (pref) {
                                            TagPreference.PREFER -> KMR.strings.taste_pref_prefer
                                            TagPreference.DISLIKE -> KMR.strings.taste_pref_dislike
                                            TagPreference.BLOCK -> KMR.strings.taste_pref_block
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, preference) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

// KMK --> v0.7.8: same-manga matching setting composables
// KMK v0.8.11: now a thin wrapper over the official ListPreferenceWidget (radio-list dialog)
// instead of a bespoke clickable Column + DropdownMenu, so numeric choice rows look and behave
// like every other Komikku list preference. The subtitle shows the current value on its own line
// followed by the explanatory summary. All option lists are unchanged.
@Composable
internal fun SameMangaListPrefRow(
    title: String,
    summary: String,
    current: Int,
    options: List<Int>,
    onSelect: (Int) -> Unit,
    // KMK --> v0.7.26: optional display label override (e.g. "Off" for 0)
    valueLabel: (Int) -> String = { "$it" },
    // KMK <--
) {
    ListPreferenceWidget(
        value = current,
        title = title,
        subtitle = "${valueLabel(current)}\n$summary",
        icon = null,
        entries = options.associateWith(valueLabel),
        onValueChange = onSelect,
    )
}

// KMK v0.8.11: official SwitchPreferenceWidget -- see HideKnownMangaRow.
@Composable
internal fun SameMangaSwitchRow(
    title: String,
    summary: String,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    SwitchPreferenceWidget(
        title = title,
        subtitle = summary,
        checked = enabled,
        onCheckedChanged = { onToggle() },
    )
}

/**
 * Shared bounded numeric setting used by recommendation budgets and by preferences that need an
 * independent switch and a user-entered value. The slider is quick for ordinary changes; the
 * field handles exact values. When a switch is supplied, the value row remains visible while
 * disabled so the last configured value is discoverable and preserved for re-enabling.
 */
@Composable
internal fun BoundedIntPreferenceRow(
    title: String,
    summary: String,
    valueTitle: String,
    current: Int,
    enabled: Boolean,
    min: Int,
    max: Int,
    valueLabel: @Composable (Int) -> String,
    onEnabledChange: ((Boolean) -> Unit)? = null,
    onValueChange: (Int) -> Unit,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val hasSwitch = onEnabledChange != null
    onEnabledChange?.let { onToggle ->
        SwitchPreferenceWidget(
            title = title,
            subtitle = summary,
            checked = enabled,
            onCheckedChanged = onToggle,
        )
    }
    TextPreferenceWidget(
        title = if (hasSwitch) valueTitle else title,
        subtitle = if (hasSwitch) valueLabel(current) else "${summary}\n${valueLabel(current)}",
        onPreferenceClick = { if (enabled) showDialog = true },
    )
    if (showDialog) {
        var sliderValue by rememberSaveable(current) { mutableStateOf(current.toFloat()) }
        var textValue by rememberSaveable(current) { mutableStateOf(current.toString()) }
        val typedValue = textValue.toIntOrNull()
        val valid = typedValue != null && typedValue in min..max
        val sliderLabel = valueLabel(sliderValue.roundToInt())
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(valueTitle) },
            text = {
                Column {
                    Text(sliderLabel, style = MaterialTheme.typography.titleMedium)
                    Slider(
                        value = sliderValue,
                        onValueChange = {
                            sliderValue = it
                            textValue = it.roundToInt().toString()
                        },
                        valueRange = min.toFloat()..max.toFloat(),
                        steps = (max - min - 1).coerceAtLeast(0),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { stateDescription = sliderLabel },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { value ->
                            textValue = value.filter(Char::isDigit)
                            textValue.toIntOrNull()?.let { if (it in min..max) sliderValue = it.toFloat() }
                        },
                        label = { Text(valueTitle) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = textValue.isNotEmpty() && !valid,
                        supportingText = if (textValue.isNotEmpty() && !valid) {
                            { Text(stringResource(KMR.strings.rec_numeric_value_range, min, max)) }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onValueChange(typedValue ?: return@TextButton)
                        showDialog = false
                    },
                    enabled = valid,
                ) {
                    Text(stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        )
    }
}
// KMK <--

@Composable
internal fun ReorderableCollectionItemScope.SourcePriorityItem(
    source: Source,
    rank: Int,
    isBoosted: Boolean,
    enabled: Boolean,
    status: RecommendationSourceRunStatus?,
    fitStats: SourceFitStats? = null,
    isLiked: Boolean,
    isDisliked: Boolean,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onToggle: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val evaluationModeEnabled = rememberEvaluationModeEnabled()
    val sourceDisplayLabel = if (evaluationModeEnabled) {
        EvaluationModeFormatter.sourceLabel(source.id)
    } else {
        source.name
    }
    val moveUpLabel = stringResource(KMR.strings.action_move_up)
    val moveDownLabel = stringResource(KMR.strings.action_move_down)
    val sourceEnabledDescription = stringResource(KMR.strings.accessibility_source_enabled, sourceDisplayLabel)
    val reorderActions = buildList {
        onMoveUp?.let { move ->
            add(
                CustomAccessibilityAction(moveUpLabel) {
                    move()
                    true
                },
            )
        }
        onMoveDown?.let { move ->
            add(
                CustomAccessibilityAction(moveDownLabel) {
                    move()
                    true
                },
            )
        }
    }
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.extraSmall),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.padding.small,
                    end = MaterialTheme.padding.medium,
                    top = MaterialTheme.padding.extraSmall,
                    bottom = MaterialTheme.padding.extraSmall,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.DragHandle,
                contentDescription = stringResource(KMR.strings.accessibility_reorder_source, sourceDisplayLabel),
                modifier = Modifier
                    .padding(MaterialTheme.padding.small)
                    .draggableHandle()
                    .semantics { customActions = reorderActions },
            )
            Column(modifier = Modifier.weight(1f)) {
                // KMK --> v0.8.19: evaluation mode source-name obfuscation
                Text(
                    text = sourceDisplayLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                // KMK <--
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isBoosted) {
                        Badge(
                            modifier = Modifier.padding(start = MaterialTheme.padding.small),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Text(
                                text = stringResource(KMR.strings.rec_source_boosted),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    if (!isDisliked && enabled) {
                        val rollingLabel: SourceFitLabel? = if (fitStats != null && fitStats.runCount > 0) fitStats.fitLabel else null
                        val fitLabel: String? = when {
                            rollingLabel != null -> when (rollingLabel) {
                                SourceFitLabel.GreatFit -> stringResource(KMR.strings.rec_source_fit_great)
                                SourceFitLabel.GoodFit -> stringResource(KMR.strings.rec_source_fit_good)
                                SourceFitLabel.Mixed -> stringResource(KMR.strings.rec_source_fit_mixed)
                                SourceFitLabel.NoMatchesRecently -> stringResource(KMR.strings.rec_source_fit_no_matches)
                                SourceFitLabel.OftenFiltered -> stringResource(KMR.strings.rec_source_fit_often_filtered)
                                SourceFitLabel.OftenErrors -> stringResource(KMR.strings.rec_source_fit_often_errors)
                                SourceFitLabel.TooLittleData -> null
                            }
                            status != null -> when {
                                status.status == RecommendationSourceStatus.Shown && status.visibleCount >= 5 -> stringResource(KMR.strings.rec_source_fit_great)
                                status.status == RecommendationSourceStatus.Shown && status.visibleCount >= 2 -> stringResource(KMR.strings.rec_source_fit_good)
                                status.status == RecommendationSourceStatus.Shown && status.visibleCount >= 1 -> stringResource(KMR.strings.rec_source_fit_low)
                                status.status == RecommendationSourceStatus.NoMatches -> stringResource(KMR.strings.rec_source_fit_no_matches)
                                status.status == RecommendationSourceStatus.FilteredOut -> stringResource(KMR.strings.rec_source_fit_often_filtered)
                                status.status == RecommendationSourceStatus.Error -> stringResource(KMR.strings.rec_source_fit_often_errors)
                                status.status == RecommendationSourceStatus.HiddenByDuplicateHandling -> stringResource(KMR.strings.rec_source_fit_deduplicated)
                                else -> null
                            }
                            else -> null
                        }
                        val useRolling = rollingLabel != null
                        val fitContainerColor = when {
                            useRolling -> when (rollingLabel) {
                                SourceFitLabel.GreatFit -> MaterialTheme.colorScheme.primaryContainer
                                SourceFitLabel.GoodFit -> MaterialTheme.colorScheme.secondaryContainer
                                SourceFitLabel.OftenErrors -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                            status != null -> when {
                                status.status == RecommendationSourceStatus.Shown && status.visibleCount >= 5 -> MaterialTheme.colorScheme.primaryContainer
                                status.status == RecommendationSourceStatus.Shown && status.visibleCount >= 2 -> MaterialTheme.colorScheme.secondaryContainer
                                status.status == RecommendationSourceStatus.Shown && status.visibleCount >= 1 -> MaterialTheme.colorScheme.tertiaryContainer
                                status.status == RecommendationSourceStatus.Error -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        val fitContentColor = when {
                            useRolling -> when (rollingLabel) {
                                SourceFitLabel.GreatFit -> MaterialTheme.colorScheme.onPrimaryContainer
                                SourceFitLabel.GoodFit -> MaterialTheme.colorScheme.onSecondaryContainer
                                SourceFitLabel.OftenErrors -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            status != null -> when {
                                status.status == RecommendationSourceStatus.Shown && status.visibleCount >= 5 -> MaterialTheme.colorScheme.onPrimaryContainer
                                status.status == RecommendationSourceStatus.Shown && status.visibleCount >= 2 -> MaterialTheme.colorScheme.onSecondaryContainer
                                status.status == RecommendationSourceStatus.Shown && status.visibleCount >= 1 -> MaterialTheme.colorScheme.onTertiaryContainer
                                status.status == RecommendationSourceStatus.Error -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        if (fitLabel != null) {
                            val contributionCount = fitStats?.topPicksContributionCount ?: 0
                            val badgeText = if (useRolling && contributionCount > 0) {
                                stringResource(KMR.strings.rec_source_fit_with_top_picks_count, fitLabel, contributionCount)
                            } else {
                                fitLabel
                            }
                            Badge(
                                modifier = Modifier.padding(start = MaterialTheme.padding.small),
                                containerColor = fitContainerColor,
                                contentColor = fitContentColor,
                            ) {
                                Text(
                                    text = badgeText,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
                val statusText = when {
                    isDisliked -> stringResource(KMR.strings.rec_source_status_disliked)
                    !enabled -> stringResource(KMR.strings.rec_source_status_disabled)
                    status == null -> stringResource(KMR.strings.rec_source_status_not_checked)
                    else -> when (status.status) {
                        RecommendationSourceStatus.Shown -> pluralStringResource(KMR.plurals.rec_source_status_shown, count = status.visibleCount, status.visibleCount)
                        RecommendationSourceStatus.NoMatches -> stringResource(KMR.strings.rec_source_status_no_matches)
                        RecommendationSourceStatus.FilteredOut -> stringResource(KMR.strings.rec_source_status_filtered)
                        RecommendationSourceStatus.Error -> stringResource(KMR.strings.rec_source_status_error)
                        RecommendationSourceStatus.Disabled -> stringResource(KMR.strings.rec_source_status_disabled)
                        RecommendationSourceStatus.OutsideAttemptLimit -> stringResource(KMR.strings.rec_source_status_not_searched_limit)
                        RecommendationSourceStatus.HiddenByDuplicateHandling -> stringResource(KMR.strings.rec_source_status_duplicate_hidden)
                    }
                }
                val statusLine = buildString {
                    append("${source.lang.uppercase(Locale.ROOT)} · #$rank · $statusText")
                }
                // KMK v0.8.17 (Phase C: diagnostic-first For You quality) -- statusText above was
                // already a correct explanation, just crammed into this one dense line with no way to
                // see more. Tap it to see the full plain-language explanation instead of lengthening
                // the always-visible line.
                var showStatusExplanation by remember { mutableStateOf(false) }
                Text(
                    text = statusLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDisliked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = if (status != null) {
                        Modifier.clickable(
                            onClickLabel = stringResource(KMR.strings.accessibility_source_status_details),
                            role = Role.Button,
                            onClick = { showStatusExplanation = true },
                        )
                    } else {
                        Modifier
                    },
                )
                if (showStatusExplanation && status != null) {
                    AlertDialog(
                        onDismissRequest = { showStatusExplanation = false },
                        title = { Text(text = stringResource(KMR.strings.rec_source_status_explain_title)) },
                        text = { Text(text = stringResource(RecommendationSourceStatusExplanationPolicy.explanationFor(status.status))) },
                        confirmButton = {
                            TextButton(onClick = { showStatusExplanation = false }) {
                                Text(text = stringResource(MR.strings.action_ok))
                            }
                        },
                    )
                }
                if (fitStats != null && fitStats.runCount > 0 && fitStats.updatedAt > 0L) {
                    Text(
                        text = stringResource(
                            KMR.strings.rec_source_last_checked,
                            android.text.format.DateUtils.getRelativeTimeSpanString(
                                fitStats.updatedAt,
                                System.currentTimeMillis(),
                                android.text.format.DateUtils.MINUTE_IN_MILLIS,
                                android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE,
                            ).toString(),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isLiked) {
                    Text(
                        text = stringResource(KMR.strings.rec_source_preference_preferred_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            // KMK v0.8.11: like/dislike-for-For-You moved from two always-visible IconButtons into
            // one overflow menu (Phase E) -- drag handle, title, badges, and the enable switch
            // remain immediately visible; the current preference (if any) stays in the source
            // information column so long status lines cannot collide with it.
            var showPrefMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showPrefMenu = true }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = stringResource(MR.strings.action_menu_overflow_description),
                        tint = if (isDisliked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = showPrefMenu, onDismissRequest = { showPrefMenu = false }) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (isLiked) KMR.strings.rec_source_preference_preferred_badge else KMR.strings.rec_source_preference_like_for_you,
                                ),
                            )
                        },
                        onClick = {
                            onLike()
                            showPrefMenu = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(KMR.strings.rec_source_preference_dislike_for_you)) },
                        onClick = {
                            onDislike()
                            showPrefMenu = false
                        },
                    )
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = { onToggle() },
                modifier = Modifier.semantics {
                    contentDescription = sourceEnabledDescription
                },
            )
        }
    }
}

// KMK v0.8.10 -->
/** Sort-mode chip row for Sources To Try, mirroring RatedMangaScreen's RatedSortRow pattern. */
@Composable
internal fun SourcesToTrySortRow(
    current: SourcesToTrySortMode,
    onSelect: (SourcesToTrySortMode) -> Unit,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            SourcesToTrySortMode.BEST_FIT to KMR.strings.rec_sources_to_try_sort_best_fit,
            SourcesToTrySortMode.NAME_AZ to KMR.strings.rec_sources_to_try_sort_name,
            SourcesToTrySortMode.LANGUAGE to KMR.strings.rec_sources_to_try_sort_language,
        ).forEach { (mode, labelRes) ->
            FilterChip(
                selected = current == mode,
                onClick = { onSelect(mode) },
                label = { Text(stringResource(labelRes)) },
            )
        }
    }
}
// KMK <--

@Composable
internal fun SourceSuggestionItem(
    suggestion: NonInstalledSourceSuggestion,
    onInstall: () -> Unit,
    onCancelInstall: () -> Unit,
    onDismiss: () -> Unit,
    isLiked: Boolean,
    isDisliked: Boolean,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    isInstalling: Boolean = false,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelected: () -> Unit = {},
    isQualityDisliked: Boolean = false,
    isQualityExplicit: Boolean = false,
    onMarkQualityPoor: () -> Unit = {},
    onMarkQualityExplicit: () -> Unit = {},
    onClearQualityMark: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.extraSmall)
            .then(if (selectionMode) Modifier.clickable(onClick = onToggleSelected) else Modifier),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelected() },
                    modifier = Modifier.padding(start = MaterialTheme.padding.small),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = if (selectionMode) MaterialTheme.padding.extraSmall else MaterialTheme.padding.medium,
                        end = MaterialTheme.padding.medium,
                        top = MaterialTheme.padding.small,
                        bottom = MaterialTheme.padding.small,
                    ),
            ) {
                // KMK --> v0.8.19: evaluation mode source/repo-name obfuscation
                val evaluationModeEnabled = rememberEvaluationModeEnabled()
                Text(
                    text = if (evaluationModeEnabled) {
                        EvaluationModeFormatter.sourceLabel(suggestion.evaluationSourceKey)
                    } else {
                        suggestion.displayName
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                val confidenceLabel = when (suggestion.confidence) {
                    SuggestionConfidence.LOW -> stringResource(KMR.strings.rec_suggestion_confidence_low)
                    SuggestionConfidence.MEDIUM -> stringResource(KMR.strings.rec_suggestion_confidence_medium)
                }
                val repoNameLabel = if (evaluationModeEnabled) {
                    EvaluationModeFormatter.repoLabel(suggestion.displayRepoName)
                } else {
                    suggestion.displayRepoName
                }
                Text(
                    text = buildString {
                        append(suggestion.displayLang.uppercase(Locale.ROOT))
                        if (suggestion.displayRepoName.isNotEmpty()) {
                            append(" · $repoNameLabel")
                        }
                        append(" · $confidenceLabel")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // KMK <--
                // KMK v0.8.10: every reason now renders real text -- EvaluatedExplicitHeavy/
                // EvaluatedEcchiHeavy previously mapped to null (silently hidden) even though a
                // suggestion carrying one of these reasons can still surface (blockExplicit only
                // filters EXPLICIT_HEAVY; ECCHI_HEAVY is unaffected by that toggle) -- explaining why
                // it was flagged is exactly the "actual contributing signals" transparency the search/
                // explanation completion pass requires, not something to hide.
                val reasonTexts = suggestion.reasons.mapNotNull { reason ->
                    when (reason) {
                        is NonInstalledSuggestionReason.LanguageMatch -> stringResource(KMR.strings.rec_suggestion_reason_language_match)
                        is NonInstalledSuggestionReason.SameRepoAsInstalledSources -> stringResource(KMR.strings.rec_suggestion_reason_same_repo)
                        is NonInstalledSuggestionReason.SimilarToInstalledSource -> stringResource(KMR.strings.rec_suggestion_reason_similar_source)
                        NonInstalledSuggestionReason.NeedsTesting -> stringResource(KMR.strings.rec_suggestion_reason_needs_testing)
                        NonInstalledSuggestionReason.UserLikedSource -> stringResource(KMR.strings.rec_suggestion_reason_user_liked)
                        NonInstalledSuggestionReason.EvaluatedStrongFit -> stringResource(KMR.strings.rec_suggestion_reason_evaluated_strong_fit)
                        NonInstalledSuggestionReason.EvaluatedWorthTrying -> stringResource(KMR.strings.rec_suggestion_reason_evaluated_worth_trying)
                        NonInstalledSuggestionReason.EvaluatedExplicitHeavy -> stringResource(KMR.strings.rec_suggestion_reason_evaluated_explicit_heavy)
                        NonInstalledSuggestionReason.EvaluatedEcchiHeavy -> stringResource(KMR.strings.rec_suggestion_reason_evaluated_ecchi_heavy)
                    }
                }.distinct()
                // KMK v0.8.10: an empty reason list is shown explicitly as "not enough evidence"
                // rather than silently rendering nothing, matching the plan's "including 'not enough
                // evidence' rather than inventing certainty" requirement.
                Text(
                    text = reasonTexts.takeIf { it.isNotEmpty() }
                        ?.joinToString(" · ")
                        ?: stringResource(KMR.strings.rec_suggestion_insufficient_evidence),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = MaterialTheme.padding.extraSmall),
                )
                // KMK v0.8.11: Install remains the sole always-visible action row; Dismiss,
                // like/dislike, and quality marks moved into one overflow menu so a card no longer
                // repeats five visible controls on phone width. Selection mode still hides all of
                // this (bulk actions take over). A small badge below still shows an existing
                // like/quality mark so that state remains visible without a fifth icon.
                if (!selectionMode) {
                    FlowRow(
                        modifier = Modifier.padding(top = MaterialTheme.padding.extraSmall),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                    ) {
                        Button(onClick = onInstall, enabled = !isInstalling) {
                            Text(
                                stringResource(
                                    if (isInstalling) {
                                        KMR.strings.rec_suggestion_installing
                                    } else {
                                        KMR.strings.rec_suggestion_install
                                    },
                                ),
                            )
                        }
                        if (isInstalling) {
                            TextButton(onClick = onCancelInstall) {
                                Text(stringResource(KMR.strings.rec_suggestion_cancel_install))
                            }
                        }
                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = stringResource(MR.strings.action_menu_overflow_description),
                                    tint = if (isQualityDisliked || isDisliked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(KMR.strings.rec_suggestion_dismiss)) },
                                    onClick = {
                                        onDismiss()
                                        showMenu = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                if (isLiked) KMR.strings.rec_source_preference_preferred_badge else KMR.strings.rec_source_preference_like_source,
                                            ),
                                        )
                                    },
                                    onClick = {
                                        onLike()
                                        showMenu = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(KMR.strings.rec_source_preference_dislike_source)) },
                                    onClick = {
                                        onDislike()
                                        showMenu = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(KMR.strings.source_quality_mark_poor)) },
                                    onClick = {
                                        onMarkQualityPoor()
                                        showMenu = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(KMR.strings.source_quality_mark_explicit)) },
                                    onClick = {
                                        onMarkQualityExplicit()
                                        showMenu = false
                                    },
                                )
                                if (isQualityDisliked || isQualityExplicit) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(KMR.strings.source_quality_clear_mark)) },
                                        onClick = {
                                            onClearQualityMark()
                                            showMenu = false
                                        },
                                    )
                                }
                            }
                        }
                        if (isLiked || isQualityDisliked || isQualityExplicit) {
                            Text(
                                text = stringResource(
                                    when {
                                        isQualityExplicit -> KMR.strings.source_quality_marked_explicit_badge
                                        isQualityDisliked -> KMR.strings.source_quality_marked_poor_badge
                                        else -> KMR.strings.rec_source_preference_preferred_badge
                                    },
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isQualityDisliked || isQualityExplicit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

// KMK v0.8.10 -->
/**
 * One taste suggestion row: a tag name, its evidence count, and a single "Add" action. Adding
 * routes through the same [TagPreference] mutation every manually-added tag preference already
 * uses, so it is fully reversible from the existing tag preference chips above.
 */
@Composable
private fun TasteSuggestionRow(
    candidate: TasteSuggestionCandidate,
    actionLabel: StringResource,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaterialTheme.padding.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = candidate.displayName, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = pluralStringResource(KMR.plurals.taste_suggestions_evidence_count, count = candidate.evidenceCount, candidate.evidenceCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onAdd) {
            Text(stringResource(actionLabel))
        }
    }
}

// KMK v0.8.11: one suggestion group (Preferred or Blocked), capped at
// TasteSuggestionVisibilityPolicy.DEFAULT_VISIBLE with an independent, rememberSaveable expand
// state per group -- so a long Preferred list can no longer push Blocked suggestions out of initial
// view, and expanding one group never affects the other.
@Composable
private fun TasteSuggestionGroup(
    label: String,
    candidates: List<TasteSuggestionCandidate>,
    labelColor: androidx.compose.ui.graphics.Color,
    actionLabel: StringResource,
    onAdd: (TasteSuggestionCandidate) -> Unit,
    saveKey: String,
    topPadding: androidx.compose.ui.unit.Dp,
) {
    if (candidates.isEmpty()) return
    var visibleCount by rememberSaveable(saveKey) { mutableStateOf(TasteSuggestionVisibilityPolicy.DEFAULT_VISIBLE) }
    val visible = TasteSuggestionVisibilityPolicy.visible(candidates, visibleCount)

    Column(modifier = Modifier.padding(top = topPadding)) {
        Text(
            text = "$label (${candidates.size})",
            style = MaterialTheme.typography.labelLarge,
            color = labelColor,
        )
        visible.forEach { candidate ->
            TasteSuggestionRow(candidate = candidate, actionLabel = actionLabel, onAdd = { onAdd(candidate) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
            if (TasteSuggestionVisibilityPolicy.canShowMore(candidates.size, visibleCount)) {
                TextButton(onClick = { visibleCount = TasteSuggestionVisibilityPolicy.nextVisibleCount(candidates.size, visibleCount) }) {
                    Text(
                        pluralStringResource(KMR.plurals.taste_settings_tag_group_show_n_more, count = TasteSuggestionVisibilityPolicy.nextVisibleCount(candidates.size, visibleCount) - visibleCount, TasteSuggestionVisibilityPolicy.nextVisibleCount(candidates.size, visibleCount) - visibleCount),
                    )
                }
            }
            if (TasteSuggestionVisibilityPolicy.canShowAll(candidates.size, visibleCount)) {
                TextButton(onClick = { visibleCount = candidates.size }) {
                    Text(stringResource(KMR.strings.taste_settings_tag_group_show_all, candidates.size))
                }
            }
            if (TasteSuggestionVisibilityPolicy.canShowFewer(visibleCount)) {
                TextButton(onClick = { visibleCount = TasteSuggestionVisibilityPolicy.DEFAULT_VISIBLE }) {
                    Text(stringResource(KMR.strings.taste_settings_tag_group_show_fewer))
                }
            }
        }
    }
}

/**
 * Preferred/blocked tag suggestions derived purely from the user's own rated manga -- see
 * [TasteSuggestionAggregator][tachiyomi.domain.taste.interactor.TasteSuggestionAggregator] for the
 * aggregation rules (minimum evidence count, alias resolution, exclusion of already-set tags).
 *
 * KMK v0.8.11: Preferred and Blocked now render as two independently capped/expandable groups (see
 * [TasteSuggestionGroup]) instead of one continuous column, so Blocked suggestions are reachable
 * without scrolling through a long Preferred list first -- same treatment fix9 already gave stored
 * tag preferences (see [TagPreferenceGroup]).
 */
@Composable
internal fun TasteSuggestionsContent(
    suggestions: TasteSuggestionResult,
    onAddPreferred: (TasteSuggestionCandidate) -> Unit,
    onAddBlocked: (TasteSuggestionCandidate) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium),
    ) {
        when {
            suggestions.hasInsufficientData -> Text(
                text = stringResource(KMR.strings.taste_suggestions_insufficient_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            suggestions.preferred.isEmpty() && suggestions.blocked.isEmpty() -> Text(
                text = stringResource(KMR.strings.taste_suggestions_none_found),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {
                TasteSuggestionGroup(
                    label = stringResource(KMR.strings.taste_suggestions_preferred_header),
                    candidates = suggestions.preferred,
                    labelColor = MaterialTheme.colorScheme.primary,
                    actionLabel = KMR.strings.taste_suggestions_add_preferred,
                    onAdd = onAddPreferred,
                    saveKey = "taste_suggestions_preferred",
                    topPadding = 0.dp,
                )
                TasteSuggestionGroup(
                    label = stringResource(KMR.strings.taste_suggestions_blocked_header),
                    candidates = suggestions.blocked,
                    labelColor = MaterialTheme.colorScheme.error,
                    actionLabel = KMR.strings.taste_suggestions_add_blocked,
                    onAdd = onAddBlocked,
                    saveKey = "taste_suggestions_blocked",
                    topPadding = if (suggestions.preferred.isNotEmpty()) MaterialTheme.padding.medium else 0.dp,
                )
            }
        }
    }
}

/**
 * Local, privacy-safe diagnostics: rating counts, evidence behind currently-set tag preferences,
 * and a metadata-confidence summary -- all derived from data the app already collects, nothing new
 * is read and no raw URLs/cookies/extension internals/full manga content are ever shown.
 */
@Composable
internal fun TasteDiagnosticsContent(diagnostics: TasteDiagnosticsResult?) {
    // KMK --> v0.8.19: evaluation mode tag-label obfuscation
    val evaluationModeEnabled = rememberEvaluationModeEnabled()
    // KMK <--
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium),
    ) {
        if (diagnostics == null) {
            Text(
                text = stringResource(KMR.strings.taste_diagnostics_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        val summary = diagnostics.summary
        Text(
            text = stringResource(
                KMR.strings.taste_diagnostics_rating_counts,
                summary.ratingCounts.love,
                summary.ratingCounts.like,
                summary.ratingCounts.dislike,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(
                KMR.strings.taste_diagnostics_confidence,
                pluralStringResource(
                    KMR.plurals.taste_diagnostics_positive_signal_fragment,
                    count = diagnostics.confidence.usablePositiveTagCount,
                    diagnostics.confidence.usablePositiveTagCount,
                ),
                pluralStringResource(
                    KMR.plurals.taste_diagnostics_negative_signal_fragment,
                    count = diagnostics.confidence.usableNegativeTagCount,
                    diagnostics.confidence.usableNegativeTagCount,
                ),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = MaterialTheme.padding.extraSmall),
        )
        Text(
            text = stringResource(
                if (diagnostics.confidence.isSufficientForPersonalizedEvaluation) {
                    KMR.strings.taste_diagnostics_confidence_sufficient
                } else {
                    KMR.strings.taste_diagnostics_confidence_insufficient
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (summary.preferredTagEvidence.isNotEmpty()) {
            Text(
                text = pluralStringResource(KMR.plurals.taste_diagnostics_preferred_tags_header, count = summary.explicitPreferredTagCount, summary.explicitPreferredTagCount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = MaterialTheme.padding.medium),
            )
            // KMK --> v0.8.19: evaluation mode tag-label obfuscation
            summary.preferredTagEvidence.forEach { evidence ->
                Text(
                    text = pluralStringResource(
                        KMR.plurals.taste_diagnostics_tag_evidence_row,
                        count = evidence.evidenceCount,
                        if (evaluationModeEnabled) {
                            EvaluationModeFormatter.likedTagLabel(evidence.displayName)
                        } else {
                            evidence.displayName
                        },
                        evidence.evidenceCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // KMK <--
        }

        if (summary.blockedTagEvidence.isNotEmpty()) {
            Text(
                text = pluralStringResource(KMR.plurals.taste_diagnostics_blocked_tags_header, count = summary.explicitBlockedTagCount, summary.explicitBlockedTagCount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = MaterialTheme.padding.medium),
            )
            // KMK --> v0.8.19: evaluation mode tag-label obfuscation
            summary.blockedTagEvidence.forEach { evidence ->
                Text(
                    text = pluralStringResource(
                        KMR.plurals.taste_diagnostics_tag_evidence_row,
                        count = evidence.evidenceCount,
                        if (evaluationModeEnabled) {
                            EvaluationModeFormatter.blockedTagLabel(evidence.displayName)
                        } else {
                            evidence.displayName
                        },
                        evidence.evidenceCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // KMK <--
        }

        Text(
            text = stringResource(KMR.strings.taste_diagnostics_signals_explanation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = MaterialTheme.padding.medium),
        )
    }
}

// KMK v0.8.17-fix1 -->
/**
 * The five approved Recommendation Settings destinations, used by [RecommendationSettingsQuickAccessRow]
 * so every detail screen can jump directly to another without backing out to
 * [RecommendationSettingsIndexScreen]. Deliberately closed to these five -- do not add a sixth entry or
 * reintroduce a retired destination ("For You", "Matching and versions",
 * "Background/network/installer"); see the v0.8.14 five-section structure this mirrors.
 */
enum class RecommendationSettingsQuickAccessDestination {
    ForYouSources,
    TasteAndFilters,
    SourceEvaluation,
    SourcesToTry,
    ManagementAndDiagnostics,
}

private fun RecommendationSettingsQuickAccessDestination.titleRes(): StringResource = when (this) {
    RecommendationSettingsQuickAccessDestination.ForYouSources -> KMR.strings.rec_settings_index_for_you_sources
    RecommendationSettingsQuickAccessDestination.TasteAndFilters -> KMR.strings.rec_settings_index_taste_filters
    RecommendationSettingsQuickAccessDestination.SourceEvaluation -> KMR.strings.rec_settings_index_evaluation
    RecommendationSettingsQuickAccessDestination.SourcesToTry -> KMR.strings.rec_settings_index_discovery
    RecommendationSettingsQuickAccessDestination.ManagementAndDiagnostics -> KMR.strings.rec_settings_index_diagnostics
}

/**
 * Builds a fresh instance of the screen for [this] destination -- used with `navigator.replace(...)`
 * so quick-access navigation between detail screens keeps the back stack at a constant depth (back
 * always returns to [RecommendationSettingsIndexScreen]) instead of growing with every lateral jump.
 */
fun RecommendationSettingsQuickAccessDestination.toScreen(): eu.kanade.presentation.util.Screen = when (this) {
    RecommendationSettingsQuickAccessDestination.ForYouSources -> RecommendationSourcePrioritySettingsScreen()
    RecommendationSettingsQuickAccessDestination.TasteAndFilters -> RecommendationTasteTagsSettingsScreen()
    RecommendationSettingsQuickAccessDestination.SourceEvaluation -> exh.recs.evaluation.SourceEvaluationScreen()
    RecommendationSettingsQuickAccessDestination.SourcesToTry -> RecommendationNonInstalledDiscoverySettingsScreen()
    RecommendationSettingsQuickAccessDestination.ManagementAndDiagnostics -> RecommendationDiagnosticsSettingsScreen()
}

private fun RecommendationSettingsQuickAccessDestination.icon(): androidx.compose.ui.graphics.vector.ImageVector = when (this) {
    RecommendationSettingsQuickAccessDestination.ForYouSources -> Icons.Outlined.Star
    RecommendationSettingsQuickAccessDestination.TasteAndFilters -> Icons.Outlined.Label
    RecommendationSettingsQuickAccessDestination.SourceEvaluation -> Icons.Outlined.FactCheck
    RecommendationSettingsQuickAccessDestination.SourcesToTry -> Icons.Outlined.ThumbUpAlt
    RecommendationSettingsQuickAccessDestination.ManagementAndDiagnostics -> Icons.Outlined.BugReport
}

/**
 * Horizontally-scrollable quick-access row shown near the top of every Recommendation Settings detail
 * screen (below the app bar, before the main content), so the user can move between the five approved
 * destinations without backing out to the index. Reuses the exact same title/icon each destination's
 * index row already uses -- this does not duplicate any control, it is navigation only. The current
 * destination is shown selected and is not itself clickable (tapping it would just re-push the same
 * screen).
 */
@Composable
internal fun RecommendationSettingsQuickAccessRow(
    current: RecommendationSettingsQuickAccessDestination,
    onNavigate: (RecommendationSettingsQuickAccessDestination) -> Unit,
) {
    val quickAccessDescription = stringResource(KMR.strings.rec_settings_quick_access_group)
    val listState = rememberLazyListState()

    LaunchedEffect(current) {
        val selectedIndex = RecommendationSettingsQuickAccessDestination.entries.indexOf(current)
        if (selectedIndex >= 0) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = quickAccessDescription
            }
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        contentPadding = PaddingValues(horizontal = MaterialTheme.padding.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        items(
            items = RecommendationSettingsQuickAccessDestination.entries,
            key = { it.name },
        ) { destination ->
            val selected = destination == current
            FilterChip(
                selected = selected,
                onClick = { if (!selected) onNavigate(destination) },
                label = { Text(stringResource(destination.titleRes())) },
                leadingIcon = {
                    Icon(
                        imageVector = destination.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
    }
}

/**
 * Samsung-edge-panel-style quick launcher with a narrow right-edge tap handle.
 *
 * - Opens via tap on the edge handle (swipe-to-open was judged unreliable/risky to implement safely
 *   alongside Android's own edge-swipe back gesture -- tap-to-open only, a deliberate scope decision
 *   carried forward unchanged from v0.8.18; swipe-to-open remains a real, separate follow-up).
 * - Closes via tap-outside (scrim), the handle itself, or the system back gesture ([BackHandler]).
 * - The handle region is intentionally narrow (28dp) so it never competes with Android's own
 *   edge-swipe-back gesture area.
 */
@Composable
internal fun <T> EdgeQuickAccessPanel(
    destinations: List<T>,
    // KMK v0.8.19: nullable -- a screen using this panel to reach a different destination set
    // (e.g. Recommendation Settings sections from a collection screen) has no "current" entry
    // among [destinations] to highlight.
    current: T?,
    title: @Composable (T) -> String,
    icon: (T) -> androidx.compose.ui.graphics.vector.ImageVector,
    onNavigate: (T) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = expanded) { expanded = false }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            // Scrim -- tap outside the panel to close, same convention as a modal sheet/dialog.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { expanded = false },
                    ),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = slideInHorizontally(tween(200)) { it },
            exit = slideOutHorizontally(tween(200)) { it },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            // Layout repair. See
            // EdgeQuickAccessPanelLayoutPolicy for the audited defect list and why each value was
            // chosen. Destinations, navigation, scrim, BackHandler, and the deliberately-narrow
            // handle geometry are all unchanged -- only sizing/arrangement/semantics changed.
            //
            // Was: fillMaxHeight(0.7f) + top-packed unscrollable Column of fixed 96.dp/10.sp items.
            // That reserved a fixed fraction of the screen regardless of content (dead space and
            // poor balance on tall screens) while clipping the lower destinations entirely on short
            // ones. Now the panel wraps its content, is bounded (not fixed) at
            // MAX_HEIGHT_FRACTION, centers what fits, and scrolls when it does not.
            val itemWidth = EdgeQuickAccessPanelLayoutPolicy
                .itemWidthDp(LocalConfiguration.current.screenWidthDp).dp
            val maxPanelHeight = (
                LocalConfiguration.current.screenHeightDp *
                    EdgeQuickAccessPanelLayoutPolicy.MAX_HEIGHT_FRACTION
                ).dp
            // Reserve the full touch target at the edge so the handle never covers a wrapped
            // destination label. The painted handle remains narrow; only the safe layout inset
            // changes, which keeps the shared panel readable at phone width.
            Box(modifier = Modifier.padding(end = EdgeQuickAccessPanelLayoutPolicy.MIN_TOUCH_TARGET_DP.dp)) {
                Surface(
                    modifier = Modifier
                        // Keep the scrollable child under a finite max constraint. Combining
                        // fillMaxHeight with wrapContentHeight can otherwise measure verticalScroll
                        // with an infinite height on some Android/Compose window configurations.
                        .heightIn(max = maxPanelHeight)
                        .wrapContentHeight()
                        .wrapContentWidth(),
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(MaterialTheme.padding.small),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        destinations.forEach { destination ->
                            val selected = destination == current
                            val destinationTitle = title(destination)
                            val selectedLabel = stringResource(MR.strings.selected)
                            val notSelectedLabel = stringResource(MR.strings.not_selected)
                            Column(
                                modifier = Modifier
                                    .width(itemWidth)
                                    .heightIn(min = EdgeQuickAccessPanelLayoutPolicy.ITEM_MIN_HEIGHT_DP.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    // Selected state is no longer conveyed by colour alone -- a container
                                    // tint plus an explicit accessibility state description carry it too.
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                    )
                                    .clickable(onClickLabel = destinationTitle) {
                                        expanded = false
                                        if (!selected) onNavigate(destination)
                                    }
                                    .semantics {
                                        stateDescription = if (selected) selectedLabel else notSelectedLabel
                                    }
                                    .padding(
                                        horizontal = MaterialTheme.padding.extraSmall,
                                        vertical = MaterialTheme.padding.small,
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = icon(destination),
                                    contentDescription = null,
                                    tint = if (selected) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                Text(
                                    text = destinationTitle,
                                    // Was labelSmall forced down to 10sp; the unmodified theme token is
                                    // readable and keeps the panel consistent with the rest of settings.
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    // No maxLines cap: the widened item plus wrapping content height lets
                                    // the longest destination title wrap instead of truncating.
                                    modifier = Modifier.padding(top = MaterialTheme.padding.extraSmall),
                                )
                            }
                        }
                    }
                }
            }
        }
        // Edge handle -- the *painted* width stays 28dp so it never competes with Android's own
        // edge-swipe-back gesture area (deliberate v0.8.18 decision, preserved). Its *touch* target
        // is expanded to the platform minimum via minimumInteractiveComponentSize() instead, which
        // enlarges the interactive region without changing the drawn geometry or introducing any
        // swipe coordinates. Always visible; tapping it while expanded closes the panel.
        val handleDescription = stringResource(KMR.strings.rec_settings_quick_access_handle)
        // The handle's icon toggles (Close vs KeyboardArrowLeft) but its label did not, so the panel's
        // open/closed state was conveyed visually only. State goes on the clickable parent -- matching
        // DisclosureToggleRow -- rather than onto the icon, which stays decorative so the control is not
        // announced twice. Both strings already exist; no new resource, so no new translation gate.
        val handleState = stringResource(
            if (expanded) KMR.strings.accessibility_state_expanded else KMR.strings.accessibility_state_collapsed,
        )
        Surface(
            onClick = { expanded = !expanded },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .minimumInteractiveComponentSize()
                .width(EdgeQuickAccessPanelLayoutPolicy.HANDLE_VISUAL_WIDTH_DP.dp)
                .semantics {
                    contentDescription = handleDescription
                    stateDescription = handleState
                },
            // KMK <--
            shape = MaterialTheme.shapes.large,
            tonalElevation = 3.dp,
            shadowElevation = 3.dp,
        ) {
            Box(
                modifier = Modifier
                    .height(EdgeQuickAccessPanelLayoutPolicy.HANDLE_VISUAL_HEIGHT_DP.dp)
                    .width(EdgeQuickAccessPanelLayoutPolicy.HANDLE_VISUAL_WIDTH_DP.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Outlined.Close else Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// KMK v0.8.19: RecommendationCollectionQuickAccessDestination/Panel (For You/Loved/Liked/Disliked
// edge panel) removed -- it was fully superseded by RecommendationSettingsQuickAccessPanel above,
// which the collection screens now use instead (jumping to the five Recommendation Settings
// sections was the panel's originally-stated purpose; switching between For You/Loved/Liked/
// Disliked was redundant with the tab bar already on those screens). Confirmed no remaining
// callers before deletion.

// KMK v0.8.19 -->
/**
 * Right-edge quick-access panel for the five Recommendation Settings sections, shown on For You,
 * Loved, Liked, and Disliked (in addition to the Recommendation Settings detail screens
 * themselves, which use [RecommendationSettingsQuickAccessRow] instead). This restores the
 * panel's original stated purpose on those screens -- jumping directly into Recommendation
 * Settings -- rather than switching between For You/Loved/Liked/Disliked, which the bottom tab
 * bar and top tabs already cover.
 */
@Composable
fun RecommendationSettingsQuickAccessPanel(
    current: RecommendationSettingsQuickAccessDestination?,
    onNavigate: (RecommendationSettingsQuickAccessDestination) -> Unit,
) {
    EdgeQuickAccessPanel(
        destinations = RecommendationSettingsQuickAccessDestination.entries,
        current = current,
        title = { stringResource(it.titleRes()) },
        icon = { it.icon() },
        onNavigate = onNavigate,
    )
}
// KMK <--

// KMK <--
// KMK <--
