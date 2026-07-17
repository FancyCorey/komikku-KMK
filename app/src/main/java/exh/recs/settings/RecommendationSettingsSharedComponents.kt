package exh.recs.settings

// KMK v0.8.8 -->
// Composables shared across the per-category Recommendation Settings screens
// (RecommendationForYouSettingsScreen, RecommendationSourcePrioritySettingsScreen,
// RecommendationTasteTagsSettingsScreen, RecommendationNonInstalledDiscoverySettingsScreen,
// RecommendationDiagnosticsSettingsScreen) -- extracted verbatim from the former single
// RecommendationsSettingsScreen.kt so every screen renders pixel-identical controls with zero
// behavior change. `internal` visibility (not `private`) so every screen in this package can use
// them without duplication.

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import eu.kanade.tachiyomi.source.Source
import exh.recs.RecommendationSourceRunStatus
import exh.recs.RecommendationSourceStatus
import exh.recs.SourceFitLabel
import exh.recs.SourceFitStats
import exh.recs.discovery.NonInstalledSourceSuggestion
import exh.recs.discovery.NonInstalledSuggestionReason
import exh.recs.discovery.SuggestionConfidence
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import sh.calvin.reorderable.ReorderableCollectionItemScope
import tachiyomi.domain.taste.model.RatedMangaVisibility
import tachiyomi.domain.taste.model.TagPreference
import tachiyomi.domain.taste.model.TagTaste
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

// KMK v0.8.7: optional one-line state summary under the title (plan section 5.2). Derived from
// state by the caller (never hardcoded) so it updates automatically whenever the underlying
// preference/process state changes, same as any other Compose recomposition. Null preserves the
// original title-only header exactly, so every existing call site is unaffected unless updated.
@Composable
internal fun SectionHeader(title: String, summary: String? = null) {
    Column(
        modifier = Modifier.padding(
            horizontal = MaterialTheme.padding.medium,
            vertical = MaterialTheme.padding.small,
        ),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        if (summary != null) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun HideKnownMangaRow(
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(KMR.strings.rec_hide_known_manga),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(KMR.strings.rec_hide_known_manga_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = { onToggle() },
        )
    }
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
                label = { Text(lang.uppercase()) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                tags.forEach { tag ->
                    val pref = TagPreference.fromValue(tag.preference)
                    FilterChip(
                        selected = true,
                        onClick = { onEditClicked(tag) },
                        label = { Text(tag.displayName) },
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
                                Icon(Icons.Outlined.Delete, contentDescription = null)
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
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "${valueLabel(current)} — $summary",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { value ->
                DropdownMenuItem(
                    text = { Text(valueLabel(value)) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                    trailingIcon = if (value == current) {
                        (
                            {
                                Icon(Icons.Outlined.Done, contentDescription = null)
                            }
                            )
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
internal fun SameMangaSwitchRow(
    title: String,
    summary: String,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = MaterialTheme.padding.medium)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = { onToggle() })
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
    modifier: Modifier = Modifier,
) {
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
                contentDescription = null,
                modifier = Modifier
                    .padding(MaterialTheme.padding.small)
                    .draggableHandle(),
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = source.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f, fill = false),
                    )
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
                        RecommendationSourceStatus.Shown -> stringResource(KMR.strings.rec_source_status_shown, status.visibleCount)
                        RecommendationSourceStatus.NoMatches -> stringResource(KMR.strings.rec_source_status_no_matches)
                        RecommendationSourceStatus.FilteredOut -> stringResource(KMR.strings.rec_source_status_filtered)
                        RecommendationSourceStatus.Error -> stringResource(KMR.strings.rec_source_status_error)
                        RecommendationSourceStatus.Disabled -> stringResource(KMR.strings.rec_source_status_disabled)
                        RecommendationSourceStatus.OutsideAttemptLimit -> stringResource(KMR.strings.rec_source_status_not_searched_limit)
                        RecommendationSourceStatus.HiddenByDuplicateHandling -> stringResource(KMR.strings.rec_source_status_duplicate_hidden)
                    }
                }
                Text(
                    text = "${source.lang.uppercase()} · #$rank · $statusText",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDisliked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
            }
            IconButton(onClick = onLike) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                    contentDescription = stringResource(KMR.strings.rec_source_preference_like_for_you),
                    tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDislike) {
                Icon(
                    imageVector = if (isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                    contentDescription = stringResource(KMR.strings.rec_source_preference_dislike_for_you),
                    tint = if (isDisliked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { onToggle() },
            )
        }
    }
}

@Composable
internal fun SourceSuggestionItem(
    suggestion: NonInstalledSourceSuggestion,
    onInstall: () -> Unit,
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
                Text(
                    text = suggestion.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                )
                val confidenceLabel = when (suggestion.confidence) {
                    SuggestionConfidence.LOW -> stringResource(KMR.strings.rec_suggestion_confidence_low)
                    SuggestionConfidence.MEDIUM -> stringResource(KMR.strings.rec_suggestion_confidence_medium)
                }
                Text(
                    text = buildString {
                        append(suggestion.displayLang.uppercase())
                        if (suggestion.displayRepoName.isNotEmpty()) append(" · ${suggestion.displayRepoName}")
                        append(" · $confidenceLabel")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val reasonTexts = suggestion.reasons.mapNotNull { reason ->
                    when (reason) {
                        is NonInstalledSuggestionReason.LanguageMatch -> stringResource(KMR.strings.rec_suggestion_reason_language_match)
                        is NonInstalledSuggestionReason.SameRepoAsInstalledSources -> stringResource(KMR.strings.rec_suggestion_reason_same_repo)
                        is NonInstalledSuggestionReason.SimilarToInstalledSource -> stringResource(KMR.strings.rec_suggestion_reason_similar_source)
                        NonInstalledSuggestionReason.NeedsTesting -> stringResource(KMR.strings.rec_suggestion_reason_needs_testing)
                        NonInstalledSuggestionReason.UserLikedSource -> stringResource(KMR.strings.rec_suggestion_reason_user_liked)
                        NonInstalledSuggestionReason.EvaluatedStrongFit -> stringResource(KMR.strings.rec_suggestion_reason_evaluated_strong_fit)
                        NonInstalledSuggestionReason.EvaluatedWorthTrying -> stringResource(KMR.strings.rec_suggestion_reason_evaluated_worth_trying)
                        NonInstalledSuggestionReason.EvaluatedExplicitHeavy -> null
                        NonInstalledSuggestionReason.EvaluatedEcchiHeavy -> null
                    }
                }.distinct()
                if (reasonTexts.isNotEmpty()) {
                    Text(
                        text = reasonTexts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = MaterialTheme.padding.extraSmall),
                    )
                }
                FlowRow(
                    modifier = Modifier.padding(top = MaterialTheme.padding.extraSmall),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    if (!selectionMode) {
                        Button(onClick = onInstall, enabled = !isInstalling) {
                            Text(stringResource(KMR.strings.rec_suggestion_install))
                        }
                        OutlinedButton(onClick = onDismiss) {
                            Text(stringResource(KMR.strings.rec_suggestion_dismiss))
                        }
                    }
                    IconButton(onClick = onLike) {
                        Icon(
                            imageVector = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = stringResource(KMR.strings.rec_source_preference_like_source),
                            tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDislike) {
                        Icon(
                            imageVector = if (isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                            contentDescription = stringResource(KMR.strings.rec_source_preference_dislike_source),
                            tint = if (isDisliked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    var showQualityMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showQualityMenu = true }) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = stringResource(KMR.strings.source_quality_mark_poor),
                                tint = if (isQualityDisliked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(expanded = showQualityMenu, onDismissRequest = { showQualityMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(KMR.strings.source_quality_mark_poor)) },
                                onClick = {
                                    onMarkQualityPoor()
                                    showQualityMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(KMR.strings.source_quality_mark_explicit)) },
                                onClick = {
                                    onMarkQualityExplicit()
                                    showQualityMenu = false
                                },
                            )
                            if (isQualityDisliked || isQualityExplicit) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(KMR.strings.source_quality_clear_mark)) },
                                    onClick = {
                                        onClearQualityMark()
                                        showQualityMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
