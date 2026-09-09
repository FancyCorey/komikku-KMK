package exh.recs

// KMK -->
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.stack.StackEvent
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.components.GlobalSearchCardRow
import eu.kanade.presentation.browse.components.GlobalSearchErrorResultItem
import eu.kanade.presentation.browse.components.GlobalSearchLoadingResultItem
import eu.kanade.presentation.browse.components.GlobalSearchResultItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.KmkEmptyStateArtwork
import eu.kanade.presentation.components.KmkEmptyStateIllustration
import eu.kanade.presentation.components.SafArtifactCleanupDialog
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.toast
import exh.recs.bestversion.BestVersionCompareScreen
import exh.recs.loved.LovedMangaScreen
import exh.recs.loved.RatedMangaScreen
import exh.recs.matching.CrossExtensionMatchMode
import exh.recs.matching.CrossExtensionMatchScreen
import exh.recs.matching.MangaIdentityKey
import exh.recs.settings.RecommendationSettingsIndexScreen
import exh.recs.settings.toScreen
import exh.util.EvaluationModeFormatter
import exh.util.rememberEvaluationModeEnabled
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.launch
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.normalizeTag
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.theme.active
import java.util.Locale
import tachiyomi.core.common.i18n.stringResource as contextStringResource

// KMK --> EC-04 2026-09-01: typed focus dimensions (Genre/Demographic slice). Presentation-only
// view of the domain-level [FocusDimension] classification (see RecommendationFocusPolicy.kt) --
// this only decides how the existing flat focusOptions list is grouped/labelled in the LazyColumn
// below; it changes no matching, persistence, or alias behavior.
private sealed interface FocusOptionRow {
    data class SectionHeader(val dimension: FocusDimension) : FocusOptionRow
    data class Group(val group: String) : FocusOptionRow
}

private fun FocusDimension.labelRes() = when (this) {
    FocusDimension.FORMAT -> KMR.strings.rec_for_you_focus_dimension_format
    FocusDimension.DEMOGRAPHIC -> KMR.strings.rec_for_you_focus_dimension_demographic
    FocusDimension.PRESENTATION_ACCESS -> KMR.strings.rec_for_you_focus_dimension_presentation_access
    FocusDimension.GENRE -> KMR.strings.rec_for_you_focus_dimension_genre
}
// KMK <--

@Composable
fun Screen.personalRecommendationsTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val screenModel = rememberScreenModel { BrowsePersonalRecommendationsScreenModel() }
    val state by screenModel.state.collectAsState()
    val scope = rememberCoroutineScope()

    // Recommendation settings and nested recommendation screens update shared preferences while
    // this tab is off-screen. Reuse the established Feed-tab navigation-return lifecycle so the
    // visible For You result is refreshed once when the nested stack returns to this tab.
    var nestedRecommendationScreenPushed by remember { mutableStateOf(false) }
    DisposableEffect(navigator.lastEvent) {
        if (navigator.lastEvent == StackEvent.Push) {
            nestedRecommendationScreenPushed = true
        }
        onDispose {
            if (navigator.lastEvent == StackEvent.Idle && nestedRecommendationScreenPushed) {
                nestedRecommendationScreenPushed = false
                screenModel.refresh()
            }
        }
    }

    // KMK --> v0.7.5: source row targeted for export (null = Top Picks)
    var pendingExportSource by remember { mutableStateOf<Source?>(null) }
    var pendingExportIsTopPicks by remember { mutableStateOf(false) }
    // KMK <--

    // KMK --> v0.8.16: For You long-press selection mode -- local UI state only, no domain writes
    // until an action button is pressed. Keyed by MangaIdentityKey (source+url) rather than local id
    // since recommendation results may not all be localized identically.
    var selectedManga by remember { mutableStateOf<Map<MangaIdentityKey, Manga>>(emptyMap()) }
    val selectionMode = ForYouSelectionPolicy.isSelectionMode(selectedManga)

    // KMK v0.8.17-fix1: rate-other-versions continuation -- offered only when exactly one manga was
    // selected for a Love/Like/Dislike action, per the plan's decision not to prompt per-item during
    // a multi-select bulk rate. Holds the just-rated manga id + rating so the dialog below can route
    // into the existing CrossExtensionMatchScreen flow without inventing a new grouping mechanism.
    var pendingRateOtherVersions by remember { mutableStateOf<Pair<Long, MangaRating>?>(null) }
    // KMK <--

    // KMK v0.8.21: applied For You focus is persisted separately from taste, saved modes, backups,
    // sync, exports, and Action History. Dialog edits remain local until Apply or Clear.
    // KMK v0.8.21-fix5: R4/AUG-14 completion -- direct product correction (2026-08-25). Focus is
    // a real include/exclude filter again (see RecommendationFocusPolicy's own doc for why the
    // rerank-only design was rejected): focusInclude/focusExclude are canonical (normalizeTag()'d)
    // group keys the user marked Include/Exclude, focusMatchAll governs only focusInclude (true =
    // Match All, the default; false = Match Any). showBroaderResults is a purely transient,
    // non-persisted UI toggle -- see its own doc below.
    var focusDialogOpen by remember { mutableStateOf(false) }
    val persistedFocus by screenModel.activeFocusCriteria.collectAsState()
    var focusInclude by remember(persistedFocus) { mutableStateOf(persistedFocus.include) }
    var focusExclude by remember(persistedFocus) { mutableStateOf(persistedFocus.exclude) }
    var focusMatchAll by remember(persistedFocus) { mutableStateOf(persistedFocus.matchAll) }
    var focusSearchQuery by remember { mutableStateOf("") }
    // Transient-only: reveals the pre-filter candidate set for sections a filter emptied out,
    // without weakening/mutating focusInclude/focusExclude/focusMatchAll or any saved focus mode,
    // and without touching durable taste/learned-taste state. Reset whenever the focus dialog is
    // confirmed or cleared so it never silently survives into the next filter selection.
    var showBroaderResults by remember { mutableStateOf(false) }
    val focusCriteria = remember(focusInclude, focusExclude, focusMatchAll) {
        RecommendationFocusPolicy.FocusCriteria(focusInclude, focusExclude, focusMatchAll)
    }
    // focusOptions previously came only from genres
    // present in state.items -- the confirmed "loaded-result-only" gap. Now unions those with
    // state.focusKnownGroups (every canonical group the user's persisted tag-alias table and the
    // built-in synonym list know about, from BrowsePersonalRecommendationsScreenModel.refresh's
    // buildFocusKnownGroups -- not limited to what happens to be visible right now). Deterministic
    // ordering: alphabetical by canonical (normalizeTag()'d) key, via toSortedSet().
    val focusOptions = remember(state.resultGeneration, state.items, state.focusKnownGroups, state.focusAliasMap) {
        val loadedGroups = state.items.values
            .asSequence()
            .filterIsInstance<PersonalRecommendationResult.Success>()
            .flatMap { it.result.asSequence() }
            .flatMap { it.manga.genre.orEmpty().asSequence() }
            .map { canonicalFocusGroup(it, state.focusAliasMap) }
        (loadedGroups + state.focusKnownGroups.asSequence().map { canonicalFocusGroup(it, state.focusAliasMap) })
            .filter { it.isNotBlank() }
            .toSortedSet()
            .toList()
    }
    val visibleFocusOptions = remember(focusOptions, focusSearchQuery, state.focusAliasMap) {
        filterFocusGroups(focusOptions, focusSearchQuery, state.focusAliasMap)
    }
    // KMK --> EC-04 2026-09-01: typed focus dimensions. Unknown labels remain in Genre, so the
    // sections when a Demographic-classified group is actually present -- most sources never
    // surface one, so this stays byte-identical to the pre-existing flat list for them (no header,
    // no visual change) rather than disclosing a dimension the current data can't support.
    val focusOptionRows: List<FocusOptionRow> = remember(visibleFocusOptions) {
        val grouped = visibleFocusOptions.groupBy(::classifyFocusDimension)
        val orderedDimensions = listOf(
            FocusDimension.FORMAT,
            FocusDimension.DEMOGRAPHIC,
            FocusDimension.PRESENTATION_ACCESS,
            FocusDimension.GENRE,
        )
        if (grouped.keys.all { it == FocusDimension.GENRE }) {
            grouped[FocusDimension.GENRE].orEmpty().map { FocusOptionRow.Group(it) }
        } else {
            buildList {
                orderedDimensions.forEach { dimension ->
                    grouped[dimension]?.takeIf { it.isNotEmpty() }?.let { groups ->
                        add(FocusOptionRow.SectionHeader(dimension))
                        groups.forEach { add(FocusOptionRow.Group(it)) }
                    }
                }
            }
        }
    }
    // KMK <--
    // KMK v0.8.21-fix2: AUG-14 slice 3 UI -- saved For You focus modes.
    // KMK v0.8.21-fix4: R4/AUG-14 completion -- rename/edit/delete/reorder and a truthful
    // malformed-preset indicator, previously deferred (see Plan D's "Saved custom modes" frozen
    // contract), are now implemented. Rename/delete/reorder read state was already fully built
    // and tested at the store layer (slice 3); this only adds the UI affordances and the two new
    // dialogs (rename, delete-confirm) below.
    val savedFocusModes by screenModel.savedFocusModes.collectAsState()
    var saveAsDialogOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<SavedFocusMode?>(null) }
    var deleteTarget by remember { mutableStateOf<SavedFocusMode?>(null) }
    if (focusDialogOpen) {
        AlertDialog(
            onDismissRequest = {
                focusSearchQuery = ""
                focusDialogOpen = false
            },
            title = { Text(stringResource(KMR.strings.rec_for_you_focus_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = focusSearchQuery,
                        onValueChange = { focusSearchQuery = it },
                        label = { Text(stringResource(KMR.strings.rec_for_you_focus_search_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (savedFocusModes.isNotEmpty()) {
                        Text(
                            text = stringResource(KMR.strings.rec_for_you_focus_saved_modes_title),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        savedFocusModes.forEachIndexed { index, mode ->
                            // KMK v0.8.21-fix4: a preset whose stored criteria no longer fully
                            // resolve against the live focus-dialog options must say so, not
                            // silently apply a smaller-than-saved selection or vanish -- per
                            // SavedFocusModeStore.resolveApply's own contract.
                            val resolved = SavedFocusModeStore.resolveApply(mode, focusOptions.toSet())
                            val isStale = resolved.include.size < mode.includeGroups.size ||
                                resolved.exclude.size < mode.excludeGroups.size
                            var manageMenuOpen by remember(mode.id) { mutableStateOf(false) }
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = mode.name,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                focusInclude = resolved.include
                                                focusExclude = resolved.exclude
                                                focusMatchAll = resolved.matchAll
                                                screenModel.setActiveFocus(resolved)
                                                showBroaderResults = false
                                                focusDialogOpen = false
                                            }
                                            .padding(vertical = MaterialTheme.padding.small),
                                    )
                                    IconButton(
                                        onClick = {
                                            val ids = savedFocusModes.map { it.id }.toMutableList()
                                            ids.add(index - 1, ids.removeAt(index))
                                            screenModel.reorderFocusModes(ids)
                                        },
                                        enabled = index > 0,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.KeyboardArrowUp,
                                            contentDescription = stringResource(KMR.strings.rec_for_you_focus_move_up),
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            val ids = savedFocusModes.map { it.id }.toMutableList()
                                            ids.add(index + 1, ids.removeAt(index))
                                            screenModel.reorderFocusModes(ids)
                                        },
                                        enabled = index < savedFocusModes.lastIndex,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.KeyboardArrowDown,
                                            contentDescription = stringResource(KMR.strings.rec_for_you_focus_move_down),
                                        )
                                    }
                                    Box {
                                        IconButton(onClick = { manageMenuOpen = true }) {
                                            Icon(
                                                imageVector = Icons.Outlined.MoreVert,
                                                contentDescription = stringResource(KMR.strings.rec_for_you_focus_manage),
                                            )
                                        }
                                        DropdownMenu(expanded = manageMenuOpen, onDismissRequest = { manageMenuOpen = false }) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(KMR.strings.rec_for_you_focus_rename)) },
                                                onClick = {
                                                    manageMenuOpen = false
                                                    renameTarget = mode
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(KMR.strings.rec_for_you_focus_update)) },
                                                enabled = focusCriteria.isActive,
                                                onClick = {
                                                    manageMenuOpen = false
                                                    screenModel.updateFocusModeCriteria(mode.id, focusInclude, focusExclude, focusMatchAll)
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(MR.strings.action_delete)) },
                                                onClick = {
                                                    manageMenuOpen = false
                                                    deleteTarget = mode
                                                },
                                            )
                                        }
                                    }
                                }
                                if (isStale) {
                                    Text(
                                        text = stringResource(KMR.strings.rec_for_you_focus_stale),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                    if (focusOptions.isEmpty()) {
                        Text(stringResource(KMR.strings.rec_for_you_focus_empty))
                    } else if (visibleFocusOptions.isEmpty()) {
                        Text(stringResource(KMR.strings.rec_for_you_focus_search_empty))
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                            items(focusOptionRows.size) { index ->
                                when (val row = focusOptionRows[index]) {
                                    is FocusOptionRow.SectionHeader -> {
                                        Text(
                                            text = stringResource(row.dimension.labelRes()),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                                        )
                                    }
                                    is FocusOptionRow.Group -> {
                                        val group = row.group
                                        val isIncluded = group in focusInclude
                                        val isExcluded = group in focusExclude
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = group.toFocusDisplayLabel(),
                                                modifier = Modifier.weight(1f),
                                            )
                                            // KMK v0.8.21-fix5: R4/AUG-14 completion -- Include/Exclude
                                            // are mutually exclusive per group; selecting one clears
                                            // the other.
                                            IconToggleButton(
                                                checked = isIncluded,
                                                onCheckedChange = { checked ->
                                                    focusInclude = if (checked) focusInclude + group else focusInclude - group
                                                    if (checked) focusExclude = focusExclude - group
                                                },
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Add,
                                                    contentDescription = stringResource(KMR.strings.rec_for_you_focus_include),
                                                    tint = if (isIncluded) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                                )
                                            }
                                            IconToggleButton(
                                                checked = isExcluded,
                                                onCheckedChange = { checked ->
                                                    focusExclude = if (checked) focusExclude + group else focusExclude - group
                                                    if (checked) focusInclude = focusInclude - group
                                                },
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Remove,
                                                    contentDescription = stringResource(KMR.strings.rec_for_you_focus_exclude),
                                                    tint = if (isExcluded) MaterialTheme.colorScheme.error else LocalContentColor.current,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // KMK v0.8.21-fix5: R4/AUG-14 completion -- Match All vs Match Any governs
                        // only focusInclude, and only matters with more than one include criterion;
                        // Match All is the default. Excludes always work as "match none", regardless.
                        if (focusInclude.size > 1) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = focusMatchAll,
                                    onClick = { focusMatchAll = true },
                                )
                                Text(
                                    text = stringResource(KMR.strings.rec_for_you_focus_match_all),
                                    modifier = Modifier.clickable { focusMatchAll = true },
                                )
                                Spacer(Modifier.width(MaterialTheme.padding.medium))
                                RadioButton(
                                    selected = !focusMatchAll,
                                    onClick = { focusMatchAll = false },
                                )
                                Text(
                                    text = stringResource(KMR.strings.rec_for_you_focus_match_any),
                                    modifier = Modifier.clickable { focusMatchAll = false },
                                )
                            }
                        }
                        TextButton(
                            onClick = { saveAsDialogOpen = true },
                            enabled = focusCriteria.isActive,
                        ) {
                            Text(stringResource(KMR.strings.rec_for_you_focus_save_as))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    focusSearchQuery = ""
                    showBroaderResults = false
                    screenModel.setActiveFocus(focusCriteria)
                    focusDialogOpen = false
                }) {
                    Text(stringResource(KMR.strings.rec_for_you_focus_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    focusInclude = emptySet()
                    focusExclude = emptySet()
                    focusMatchAll = true
                    focusSearchQuery = ""
                    showBroaderResults = false
                    screenModel.setActiveFocus(RecommendationFocusPolicy.FocusCriteria.EMPTY)
                    focusDialogOpen = false
                }) {
                    Text(stringResource(KMR.strings.rec_for_you_focus_clear))
                }
            },
        )
    }
    if (saveAsDialogOpen) {
        var saveAsName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { saveAsDialogOpen = false },
            title = { Text(stringResource(KMR.strings.rec_for_you_focus_save_as_title)) },
            text = {
                OutlinedTextField(
                    value = saveAsName,
                    onValueChange = { saveAsName = it },
                    label = { Text(stringResource(KMR.strings.rec_for_you_focus_save_as_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        screenModel.saveFocusMode(saveAsName, focusInclude, focusExclude, focusMatchAll)
                        saveAsDialogOpen = false
                    },
                    enabled = saveAsName.isNotBlank(),
                ) {
                    Text(stringResource(KMR.strings.rec_for_you_focus_save_as_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { saveAsDialogOpen = false }) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        )
    }
    // KMK v0.8.21-fix4: R4/AUG-14 completion -- rename dialog, mirroring the Save-as dialog's own
    // shape exactly (same OutlinedTextField/confirm-on-non-blank pattern).
    renameTarget?.let { target ->
        var renameName by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(KMR.strings.rec_for_you_focus_rename_title)) },
            text = {
                OutlinedTextField(
                    value = renameName,
                    onValueChange = { renameName = it },
                    label = { Text(stringResource(KMR.strings.rec_for_you_focus_save_as_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        screenModel.renameFocusMode(target.id, renameName)
                        renameTarget = null
                    },
                    enabled = renameName.isNotBlank(),
                ) {
                    Text(stringResource(KMR.strings.rec_for_you_focus_rename_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        )
    }
    // KMK v0.8.21-fix4: R4/AUG-14 completion -- delete requires an explicit confirmation, same as
    // every other destructive action in this feature area (RatedMangaConfirmDialog etc.).
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(KMR.strings.rec_for_you_focus_delete_confirm_title)) },
            text = { Text(context.contextStringResource(KMR.strings.rec_for_you_focus_delete_confirm_message, target.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        screenModel.deleteFocusMode(target.id)
                        deleteTarget = null
                    },
                ) {
                    Text(stringResource(MR.strings.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        )
    }

    // `exportInputSnapshot` is captured at the
    // moment the export is requested (see the two `onClick`/`onLongClickSource` sites below), before
    // the picker even opens -- not re-read from live `screenModel.state.value` after the picker
    // returns, so the recommendation results backing the export cannot change out from under a
    // bundle that may already be mid-write. The SAF document lifecycle (register-before-write,
    // retain on stale/empty/failed/cancelled, exact-Uri-only cleanup) is owned by SafExportCoordinator.
    // the
    // coordinator now lives on BrowsePersonalRecommendationsScreenModel (screenModelScope-owned), not
    // `remember`ed here.
    var exportInputSnapshot by remember { mutableStateOf<BrowsePersonalRecommendationsScreenModel.State?>(null) }
    // The operation is reserved at
    // the export-click, before the picker launches -- see the two export-click sites below.
    var exportPendingOperationId by remember { mutableStateOf<String?>(null) }
    val exportCleanupOffer by screenModel.exportCoordinator.cleanupOffer.collectAsState()
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val snapState = exportInputSnapshot
        val targetSource = pendingExportSource
        val isTopPicks = pendingExportIsTopPicks
        exportInputSnapshot = null
        pendingExportSource = null
        pendingExportIsTopPicks = false
        val operationId = exportPendingOperationId
        exportPendingOperationId = null
        if (uri == null) {
            operationId?.let { screenModel.exportCoordinator.cancelReservation(it) }
            return@rememberLauncherForActivityResult
        }
        if (operationId == null ||
            !screenModel.exportRecommendationBundle(context, operationId, uri, snapState, targetSource, isTopPicks)
        ) {
            eu.kanade.tachiyomi.util.export.handleUnregisterableUri(
                context,
                uri,
                screenModel.exportCoordinator,
                KMR.strings.saf_export_registration_failed,
                KMR.strings.saf_export_registration_failed_retained,
                KMR.strings.saf_export_registration_failed_unrecoverable,
            )
        }
    }
    // KMK <--

    // KMK v0.8.17-fix1: rate-other-versions continuation dialog -- offered only after rating exactly
    // one selected For You manga (see onRateSelected above). Routes into the existing
    // CrossExtensionMatchScreen flow already used by manga detail and Rated Manga; no new grouping
    // system. Closing/declining never undoes the rating already committed in onRateSelected.
    pendingRateOtherVersions?.let { (mangaId, rating) ->
        AlertDialog(
            onDismissRequest = { pendingRateOtherVersions = null },
            title = { Text(stringResource(KMR.strings.rec_for_you_rate_other_versions_prompt)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRateOtherVersions = null
                        navigator.push(CrossExtensionMatchScreen.fromMode(mangaId, CrossExtensionMatchMode.Rating(rating)))
                    },
                ) {
                    Text(stringResource(KMR.strings.rec_for_you_rate_other_versions_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRateOtherVersions = null }) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        )
    }

    // Exact-Uri-only Remove/Keep cleanup,
    // offered for every outcome (not only success) -- see SafExportCoordinator.
    exportCleanupOffer?.let { offer ->
        SafArtifactCleanupDialog(
            context = context,
            offer = offer,
            successTitleRes = KMR.strings.extension_export_cleanup_title,
            successBodyRes = KMR.strings.generic_export_cleanup_success_body,
            incompleteTitleRes = KMR.strings.extension_export_cleanup_incomplete_title,
            incompleteBodyRes = KMR.strings.extension_export_cleanup_incomplete_body,
            removeRes = KMR.strings.extension_export_cleanup_remove,
            keepRes = KMR.strings.extension_export_cleanup_keep,
            removedRes = KMR.strings.extension_export_cleanup_removed,
            removeFailedRes = KMR.strings.extension_export_cleanup_failed,
            onRemoved = { screenModel.exportCoordinator.clear(offer.operationId) },
            onKept = { screenModel.exportCoordinator.clear(offer.operationId) },
            onDismissed = { screenModel.exportCoordinator.clear(offer.operationId) },
        )
    }
    // KMK <--

    return TabContent(
        titleRes = KMR.strings.taste_recommendations_tab,
        // KMK v0.8.11: Loved/Liked/Disliked were three separate always-visible top-bar icons plus
        // Refresh/Export/Settings -- six icons total, crowding phone width. Loved/Liked/Disliked are
        // now grouped under one "Rated manga" menu, and Export moved to the overflow menu; Refresh
        // and Settings remain the two always-visible primary actions. No action was removed or
        // hidden behind long-press -- every destination is still one or two taps away.
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.action_webview_refresh),
                icon = Icons.Outlined.Refresh,
                onClick = screenModel::refresh,
            ),
            AppBar.ActionCompose(
                title = stringResource(KMR.strings.rec_rated_manga_menu),
            ) {
                var showRatedMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showRatedMenu = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Favorite,
                            contentDescription = stringResource(KMR.strings.rec_rated_manga_menu),
                        )
                    }
                    DropdownMenu(
                        expanded = showRatedMenu,
                        onDismissRequest = { showRatedMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(KMR.strings.loved_manga_title)) },
                            leadingIcon = { Icon(Icons.Outlined.Favorite, contentDescription = null) },
                            onClick = {
                                showRatedMenu = false
                                navigator.push(LovedMangaScreen())
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(KMR.strings.liked_manga_title)) },
                            leadingIcon = { Icon(Icons.Outlined.ThumbUp, contentDescription = null) },
                            onClick = {
                                showRatedMenu = false
                                navigator.push(RatedMangaScreen(MangaRating.LIKE.value))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(KMR.strings.disliked_manga_title)) },
                            leadingIcon = { Icon(Icons.Outlined.ThumbDown, contentDescription = null) },
                            onClick = {
                                showRatedMenu = false
                                navigator.push(RatedMangaScreen(MangaRating.DISLIKE.value))
                            },
                        )
                        // KMK v0.8.21-fix3: R1 correction -- Not Interested now pushes the same
                        // RatedMangaScreen every other rating tier uses (structural parity: same
                        // grid, grouping, export, bulk-select, focus badges), not a separate
                        // bespoke screen built on the legacy preference store.
                        DropdownMenuItem(
                            text = { Text(stringResource(KMR.strings.not_interested_manga_title)) },
                            leadingIcon = { Icon(Icons.Outlined.VisibilityOff, contentDescription = null) },
                            onClick = {
                                showRatedMenu = false
                                navigator.push(RatedMangaScreen(MangaRating.NOT_INTERESTED.value))
                            },
                        )
                    }
                }
            },
            AppBar.Action(
                title = stringResource(KMR.strings.taste_settings_title),
                icon = Icons.Outlined.Settings,
                // KMK v0.8.8: entry point is now the concise settings index, not the single big screen directly.
                onClick = { navigator.push(RecommendationSettingsIndexScreen) },
            ),
            // KMK v0.8.21-fix4: R4/AUG-14 correction -- Focus Recommendations is the page-level
            // control for the entire For You page (reorders every section/lane, not a niche
            // setting), so it must be an always-visible primary action, not buried in the overflow
            // menu. Promoted out of AppBar.OverflowAction below. Active-state tinting mirrors the
            // exact same established pattern this app already uses for Library/Updates/History's
            // filter icons (iconTint = MaterialTheme.colorScheme.active when a filter is active).
            AppBar.Action(
                title = stringResource(KMR.strings.rec_for_you_focus),
                icon = Icons.Outlined.FilterList,
                iconTint = if (focusCriteria.isActive) MaterialTheme.colorScheme.active else LocalContentColor.current,
                onClick = { focusDialogOpen = true },
            ),
            // KMK --> v0.7.5: Export Top Picks -- moved to overflow in v0.8.11 (Phase G).
            AppBar.OverflowAction(
                title = stringResource(KMR.strings.rec_bundle_export_top_picks),
                onClick = {
                    val operationId = screenModel.exportCoordinator.beginOperation()
                    if (operationId == null) {
                        context.toast(KMR.strings.saf_export_operation_pending)
                    } else {
                        pendingExportIsTopPicks = true
                        exportInputSnapshot = screenModel.state.value
                        exportPendingOperationId = operationId
                        exportLauncher.launch("kmk_top_picks.json")
                    }
                },
            ),
            // KMK <--
        ),
        content = { contentPadding, _ ->
            // KMK v0.8.18-fix1: right-edge quick-access panel overlay -- the v0.8.18-fix1 replacement
            // placement for the panel formerly on Recommendation Settings detail screens. This tab's
            // content is rendered inside BrowseTab's own shared Scaffold (TabContent, not a
            // screen-owned Scaffold), so the overlay is layered here around just this tab's content.
            Box(modifier = Modifier.fillMaxSize()) {
                PersonalRecommendationsContent(
                    state = state,
                    focusCriteria = focusCriteria,
                    showBroaderResults = showBroaderResults,
                    onShowBroaderResults = { showBroaderResults = true },
                    onShowFilteredOnly = { showBroaderResults = false },
                    getManga = screenModel::getManga,
                    onVisibleResultsRendered = screenModel::recordVisibleExposure,
                    onClickItem = { manga -> navigator.push(MangaScreen(manga.id, true)) },
                    onClickSource = { source ->
                        val ctx = state.searchContexts[source.id]
                        navigator.push(BrowseSourceScreen(source.id, ctx?.textQuery))
                    },
                    onClickTopPicks = {
                        val presentation = RecommendationFocusPresentationPolicy.apply(state.combinedDetailResult, focusCriteria, state.focusAliasMap)
                        val detail = if (showBroaderResults) presentation.broader else presentation.filtered
                        if (detail is PersonalRecommendationResult.Success && detail.result.isNotEmpty()) {
                            val isPartial = state.total > 0 && state.progress < state.total
                            navigator.push(TopPicksScreen(ArrayList(detail.result.map { it.manga.id }), isPartial))
                        }
                    },
                    // KMK --> v0.7.5: long-press source row to export
                    onLongClickSource = { source ->
                        val operationId = screenModel.exportCoordinator.beginOperation()
                        if (operationId == null) {
                            context.toast(KMR.strings.saf_export_operation_pending)
                        } else {
                            pendingExportSource = source
                            pendingExportIsTopPicks = false
                            exportInputSnapshot = screenModel.state.value
                            exportPendingOperationId = operationId
                            exportLauncher.launch("kmk_${source.name.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "_")}.json")
                        }
                    },
                    // KMK <--
                    // KMK v0.8.16: For You long-press selection mode
                    selectedManga = selectedManga,
                    onLongClickManga = { manga ->
                        val key = MangaIdentityKey(manga.source, manga.url)
                        selectedManga = ForYouSelectionPolicy.longPress(selectedManga, key, manga)
                    },
                    onToggleSelectManga = { manga ->
                        val key = MangaIdentityKey(manga.source, manga.url)
                        selectedManga = ForYouSelectionPolicy.toggle(selectedManga, key, manga)
                    },
                    onCloseSelection = { selectedManga = ForYouSelectionPolicy.clear() },
                    // KMK v0.8.17-fix1: rateSelected/markSelectedNotInterested/clearSelectedRatings are
                    // now suspend functions returning a BulkTasteActionOutcome instead of fire-and-forget
                    // -- awaited here so a real success/failure toast can be shown and errors are never
                    // silently swallowed. Selection is cleared once the action is accepted (before the
                    // suspend call returns), matching the app's existing bulk-action convention.
                    onRateSelected = { rating ->
                        val targets = selectedManga.values.toList()
                        val single = targets.singleOrNull()
                        selectedManga = ForYouSelectionPolicy.clear()
                        scope.launch {
                            val outcome = screenModel.rateSelected(targets, rating)
                            showBulkActionFeedback(context, bulkTasteActionRatingType(rating), outcome)
                            if (single != null && outcome.successCount > 0) {
                                pendingRateOtherVersions = single.id to rating
                            }
                        }
                    },
                    onNotInterestedSelected = {
                        val targets = selectedManga.values.toList()
                        selectedManga = ForYouSelectionPolicy.clear()
                        scope.launch {
                            val outcome = screenModel.markSelectedNotInterested(targets)
                            showBulkActionFeedback(context, BulkTasteActionType.NOT_INTERESTED, outcome)
                        }
                    },
                    onClearRatingSelected = {
                        val targets = selectedManga.values.toList()
                        selectedManga = ForYouSelectionPolicy.clear()
                        scope.launch {
                            val outcome = screenModel.clearSelectedRatings(targets)
                            showBulkActionFeedback(context, BulkTasteActionType.CLEAR_RATING, outcome)
                        }
                    },
                    onFindBestVersionSelected = {
                        val target = selectedManga.values.singleOrNull()
                        selectedManga = ForYouSelectionPolicy.clear()
                        if (target != null) navigator.push(BestVersionCompareScreen(target.id))
                    },
                    onOpenSelected = {
                        val target = selectedManga.values.singleOrNull()
                        selectedManga = ForYouSelectionPolicy.clear()
                        if (target != null) navigator.push(MangaScreen(target.id, true))
                    },
                    // KMK --> v0.7.25: retry after offline
                    onRetry = screenModel::retryFailedSourcesOrRefresh,
                    onRefresh = screenModel::refresh,
                    onClearFocus = {
                        screenModel.setActiveFocus(RecommendationFocusPolicy.FocusCriteria.EMPTY)
                        showBroaderResults = false
                    },
                    // KMK <--
                    contentPadding = contentPadding,
                )
                // KMK --> v0.8.19: this panel now jumps to Recommendation Settings sections
                // (its originally-stated purpose) instead of switching between For You/Loved/
                // Liked/Disliked, which the top tabs already cover.
                exh.recs.settings.RecommendationSettingsQuickAccessPanel(
                    current = null,
                    onNavigate = { destination ->
                        navigator.push(destination.toScreen())
                    },
                )
                // KMK <--
            }
        },
    )
}

@Composable
private fun PersonalRecommendationsContent(
    state: BrowsePersonalRecommendationsScreenModel.State,
    focusCriteria: RecommendationFocusPolicy.FocusCriteria = RecommendationFocusPolicy.FocusCriteria.EMPTY,
    // KMK v0.8.21-fix5: R4/AUG-14 completion -- transient "Show Broader Results" state, owned by
    // the caller (personalRecommendationsTab) so it survives recomposition the same way
    // focusInclude/focusExclude do, but is never persisted and never mutates focusCriteria itself.
    showBroaderResults: Boolean = false,
    onShowBroaderResults: () -> Unit = {},
    onShowFilteredOnly: () -> Unit = {},
    getManga: @Composable (Manga) -> State<Manga>,
    // Fired at most once per
    // [BrowsePersonalRecommendationsScreenModel.State.resultGeneration], only once every source has
    // finished and the loaded result is non-empty -- see the LaunchedEffect below.
    onVisibleResultsRendered: () -> Unit = {},
    onClickItem: (Manga) -> Unit,
    onClickSource: (Source) -> Unit,
    onClickTopPicks: () -> Unit,
    // KMK --> v0.7.5: export source row on long press
    onLongClickSource: ((Source) -> Unit)? = null,
    // KMK <--
    // KMK --> v0.7.25: retry callback for offline state
    onRetry: () -> Unit = {},
    // Pull-to-refresh and offline recovery intentionally refresh every enabled source.
    onRefresh: () -> Unit = {},
    onClearFocus: () -> Unit = {},
    // KMK <--
    // KMK v0.8.16: For You long-press selection mode
    selectedManga: Map<MangaIdentityKey, Manga> = emptyMap(),
    onLongClickManga: (Manga) -> Unit = {},
    onToggleSelectManga: (Manga) -> Unit = {},
    onCloseSelection: () -> Unit = {},
    onRateSelected: (MangaRating) -> Unit = {},
    onNotInterestedSelected: () -> Unit = {},
    // KMK v0.8.17-fix1: Clear Rating for the current selection
    onClearRatingSelected: () -> Unit = {},
    onFindBestVersionSelected: () -> Unit = {},
    onOpenSelected: () -> Unit = {},
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
) {
    val selectionMode = ForYouSelectionPolicy.isSelectionMode(selectedManga)
    val selectionList = selectedManga.values.toList()
    // KMK --> v0.8.19: evaluation mode source-name obfuscation
    val evaluationModeEnabled = rememberEvaluationModeEnabled()
    // KMK <--
    // KMK v0.8.16: while in selection mode, tapping a card toggles selection instead of opening it;
    // long-press always enters/adds to selection. Normal tap opens the manga when not selecting.
    val effectiveOnClick: (Manga) -> Unit = { manga ->
        if (selectionMode) onToggleSelectManga(manga) else onClickItem(manga)
    }
    val effectiveOnLongClick: (Manga) -> Unit = { manga -> onLongClickManga(manga) }
    when {
        // KMK --> v0.7.25: show offline error before loading spinner
        state.isOffline -> {
            EmptyScreen(
                message = stringResource(KMR.strings.rec_for_you_offline),
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                actions = persistentListOf(
                    EmptyScreenAction(
                        stringRes = MR.strings.action_retry,
                        icon = Icons.Outlined.Refresh,
                        onClick = onRefresh,
                    ),
                ),
            )
        }
        // KMK <--
        // Keep the initial empty load as a centered spinner, but retain partial source rows once
        // batched refresh work has registered them so a slow source cannot hide completed results.
        state.isLoading && state.items.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        state.profileIsEmpty -> {
            // The profile is genuinely empty here: loading and offline states were handled above.
            Column(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                KmkEmptyStateIllustration(
                    artwork = KmkEmptyStateArtwork.FOR_YOU,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(96.dp),
                )
                Spacer(Modifier.height(MaterialTheme.padding.medium))
                Text(stringResource(KMR.strings.taste_recommendations_empty))
            }
        }
        else -> {
            // KMK v0.8.21-fix5: R4/AUG-14 completion -- direct product correction (2026-08-25).
            // presentationMap now carries a genuinely filtered result per source (see
            // RecommendationFocusPolicy's own doc for why the previous rerank-only design was
            // rejected), plus the untouched pre-filter set for the transient "Show Broader
            // Results" toggle. `originalDedupedMap` (pre-filter) drives visibility/empty-state
            // gating so a filter emptying a section never looks indistinguishable from "no data
            // loaded" -- see the honest per-section empty state below.
            val originalDedupedMap = state.dedupedItems()
            val presentationMap = originalDedupedMap.mapValues { (_, result) ->
                RecommendationFocusPresentationPolicy.apply(result, focusCriteria, state.focusAliasMap)
            }.toPersistentMap()
            val effectiveMap = presentationMap.mapValues { (_, presentation) ->
                if (showBroaderResults) presentation.broader else presentation.filtered
            }
            val visibleOrderedSources = RecommendationSourceOrdering.prioritizeHealthySources(
                orderedSources = state.sourceOrder.filter { source ->
                    val original = originalDedupedMap[source]
                    original != null && (original !is PersonalRecommendationResult.Success || !original.isEmpty)
                },
                statuses = state.sourceStatuses,
            )
            val allDone = state.total > 0 && state.progress == state.total
            // Exposure is recorded
            // only for a loaded, non-empty, fully-settled ("correct") result -- never for loading,
            // partial, or empty states. Keying on (resultGeneration, allDone) restarts this effect
            // exactly once per refresh, exactly when it transitions to fully loaded; a stale
            // generation's in-flight effect is cancelled automatically when the key changes, and mere
            // recomposition with the same key never re-fires it.
            LaunchedEffect(state.resultGeneration, allDone) {
                if (allDone) onVisibleResultsRendered()
            }
            // KMK v0.8.21-fix5: combinedOriginal (pre-filter) drives the page-level "genuinely no
            // recommendations at all" gate below -- a focus filter emptying Top Picks must show the
            // honest per-section empty state (rendered further down), never the full-page "no
            // recommendations" illustration, which is reserved for a truly empty load.
            val combinedOriginal = state.combinedResult
            val combinedPresentation = RecommendationFocusPresentationPolicy.apply(combinedOriginal, focusCriteria, state.focusAliasMap)
            val combinedEffective = if (showBroaderResults) combinedPresentation.broader else combinedPresentation.filtered
            val hasCombinedOriginal = combinedOriginal is PersonalRecommendationResult.Success && !combinedOriginal.isEmpty
            val hasNoResults = allDone && !hasCombinedOriginal &&
                visibleOrderedSources.none { source ->
                    val r = originalDedupedMap[source]
                    r is PersonalRecommendationResult.Success && !r.isEmpty
                }

            if (hasNoResults) {
                // A no-results illustration is shown only after every source has finished.
                Column(
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                ) {
                    if (focusCriteria.isActive) {
                        ActiveFocusSummary(
                            focusCriteria = focusCriteria,
                            onClearFocus = onClearFocus,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        KmkEmptyStateIllustration(
                            artwork = KmkEmptyStateArtwork.FOR_YOU,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(96.dp),
                        )
                        Spacer(Modifier.height(MaterialTheme.padding.medium))
                        Text(stringResource(KMR.strings.taste_recommendations_empty))
                    }
                }
            } else {
                // KMK <--
                Column(modifier = Modifier.fillMaxSize()) {
                    if (focusCriteria.isActive) {
                        ActiveFocusSummary(
                            focusCriteria = focusCriteria,
                            onClearFocus = onClearFocus,
                        )
                    }
                    // KMK v0.8.16: selection banner -- count + close, never crowds the normal top bar
                    if (selectionMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = onCloseSelection) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(KMR.strings.rec_for_you_selection_close),
                                )
                            }
                            Text(
                                text = stringResource(KMR.strings.rec_for_you_selected_count, selectionList.size),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                    // KMK --> v0.7.29: pull-to-refresh support
                    var isRefreshing by remember { mutableStateOf(false) }
                    LaunchedEffect(state.isLoading) {
                        if (!state.isLoading) isRefreshing = false
                    }
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            isRefreshing = true
                            onRefresh()
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) {
                        // KMK <--
                        LazyColumn(contentPadding = contentPadding) {
                            // KMK v0.8.21-fix5: R4/AUG-14 completion -- a visible, explicit banner
                            // while "Show Broader Results" is active, so the transient, filter-
                            // bypassing view is never mistaken for the real filtered state.
                            if (showBroaderResults && focusCriteria.isActive) {
                                item(key = "broader_results_banner") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = stringResource(KMR.strings.rec_for_you_focus_show_broader),
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.weight(1f),
                                        )
                                        TextButton(onClick = onShowFilteredOnly) {
                                            Text(stringResource(KMR.strings.rec_for_you_focus_show_filtered))
                                        }
                                    }
                                }
                            }
                            // Top Picks row — appears first, derived from all source results
                            if (hasCombinedOriginal) {
                                item(key = "top_picks") {
                                    val combinedShown = combinedEffective as? PersonalRecommendationResult.Success
                                    val subtitle = (combinedOriginal as? PersonalRecommendationResult.Success)?.reason?.let {
                                        stringResource(KMR.strings.rec_top_picks_matched, it)
                                    } ?: stringResource(KMR.strings.rec_top_picks_subtitle)
                                    GlobalSearchResultItem(
                                        title = stringResource(KMR.strings.rec_top_picks_title),
                                        subtitle = subtitle,
                                        onClick = onClickTopPicks,
                                    ) {
                                        // KMK v0.8.21-fix5: an active filter that matches nothing in
                                        // Top Picks gets an honest empty state + "Show Broader
                                        // Results", never a silently-collapsed row.
                                        if (combinedShown != null && combinedShown.isEmpty && combinedPresentation.isFiltered && !showBroaderResults) {
                                            FocusSectionEmptyState(onShowBroaderResults = onShowBroaderResults)
                                        } else if (combinedShown != null) {
                                            GlobalSearchCardRow(
                                                titles = combinedShown.result.map { it.manga },
                                                getManga = getManga,
                                                onClick = effectiveOnClick,
                                                onLongClick = effectiveOnLongClick,
                                                selection = selectionList,
                                            )
                                        }
                                    }
                                }
                            }
                            // Per-source rows in priority order
                            visibleOrderedSources.forEach { source ->
                                item(key = source.id) {
                                    val original = originalDedupedMap[source] ?: return@item
                                    val reason = (original as? PersonalRecommendationResult.Success)?.reason
                                    GlobalSearchResultItem(
                                        // KMK -->
                                        title = if (evaluationModeEnabled) {
                                            EvaluationModeFormatter.sourceLabel(source.id)
                                        } else {
                                            source.name
                                        },
                                        // KMK <--
                                        subtitle = buildList {
                                            if (reason != null) {
                                                add(stringResource(KMR.strings.taste_matched_tags, reason))
                                            } else {
                                                add(source.lang.uppercase(Locale.ROOT))
                                            }
                                        }.joinToString(" • "),
                                        onClick = { onClickSource(source) },
                                        // KMK --> v0.7.5: long-press to export source row
                                        onLongClick = onLongClickSource?.let { handler -> { handler(source) } },
                                        // KMK <--
                                    ) {
                                        val presentation = presentationMap[source]
                                        val effective = effectiveMap[source]
                                        when (original) {
                                            PersonalRecommendationResult.Loading -> GlobalSearchLoadingResultItem()
                                            is PersonalRecommendationResult.Success -> {
                                                val shown = effective as? PersonalRecommendationResult.Success
                                                if (shown != null && shown.isEmpty && presentation?.isFiltered == true && !showBroaderResults) {
                                                    FocusSectionEmptyState(onShowBroaderResults = onShowBroaderResults)
                                                } else if (shown != null) {
                                                    GlobalSearchCardRow(
                                                        titles = shown.result.map { it.manga },
                                                        getManga = getManga,
                                                        onClick = effectiveOnClick,
                                                        onLongClick = effectiveOnLongClick,
                                                        selection = selectionList,
                                                    )
                                                }
                                            }
                                            is PersonalRecommendationResult.Error -> {
                                                GlobalSearchErrorResultItem(
                                                    message = with(LocalContext.current) {
                                                        original.throwable.formattedMessage
                                                    },
                                                    action = {
                                                        TextButton(onClick = onRetry) {
                                                            Text(text = stringResource(MR.strings.action_retry))
                                                        }
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // KMK --> v0.7.29
                    }
                    // KMK <--
                    // KMK v0.8.16: selection bottom action bar -- real actions only, never
                    // rendered unless something is actually selected.
                    if (selectionMode) {
                        ForYouSelectionBottomBar(
                            selectedCount = selectionList.size,
                            onRateLove = { onRateSelected(MangaRating.LOVE) },
                            onRateLike = { onRateSelected(MangaRating.LIKE) },
                            onRateDislike = { onRateSelected(MangaRating.DISLIKE) },
                            onNotInterested = onNotInterestedSelected,
                            onClearRating = onClearRatingSelected,
                            onFindBestVersion = onFindBestVersionSelected,
                            onOpen = onOpenSelected,
                            isSingleSelection = ForYouSelectionPolicy.isSingleSelection(selectedManga),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ForYouSelectionBottomBar(
    selectedCount: Int,
    onRateLove: () -> Unit,
    onRateLike: () -> Unit,
    onRateDislike: () -> Unit,
    onNotInterested: () -> Unit,
    // KMK v0.8.17-fix1: Clear Rating direct/overflow action
    onClearRating: () -> Unit,
    onFindBestVersion: () -> Unit,
    onOpen: () -> Unit,
    isSingleSelection: Boolean,
) {
    // Keep every selection action directly visible when width permits. Compact layouts move the
    // less-common actions into "More" so the primary actions remain usable without crowding.
    BottomAppBar {
        androidx.compose.foundation.layout.BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.padding.small),
        ) {
            val layout = ForYouSelectionActionLayoutPolicy.layoutFor(maxWidth.value.toInt())
            val overflow = ForYouSelectionActionLayoutPolicy.overflowActions(layout, isSingleSelection)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ForYouActionButton(
                    icon = Icons.Outlined.Favorite,
                    label = stringResource(KMR.strings.taste_love),
                    onClick = onRateLove,
                    enabled = selectedCount > 0,
                )
                ForYouActionButton(
                    icon = Icons.Outlined.ThumbUp,
                    label = stringResource(KMR.strings.taste_like),
                    onClick = onRateLike,
                    enabled = selectedCount > 0,
                )
                ForYouActionButton(
                    icon = Icons.Outlined.ThumbDown,
                    label = stringResource(KMR.strings.taste_dislike),
                    onClick = onRateDislike,
                    enabled = selectedCount > 0,
                )
                if (!overflow.notInterested) {
                    ForYouActionButton(
                        icon = Icons.Outlined.VisibilityOff,
                        label = stringResource(KMR.strings.rec_mark_seen),
                        onClick = onNotInterested,
                        enabled = selectedCount > 0,
                    )
                }
                if (!overflow.clearRating) {
                    ForYouActionButton(
                        icon = Icons.Outlined.DeleteOutline,
                        label = stringResource(KMR.strings.rec_for_you_selection_clear_rating),
                        onClick = onClearRating,
                        enabled = selectedCount > 0,
                    )
                }
                if (isSingleSelection && !overflow.findBestVersion) {
                    ForYouActionButton(
                        icon = Icons.AutoMirrored.Outlined.CompareArrows,
                        label = stringResource(KMR.strings.rec_for_you_selection_find_best_version),
                        onClick = onFindBestVersion,
                        enabled = true,
                    )
                }
                if (isSingleSelection && !overflow.open) {
                    ForYouActionButton(
                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
                        label = stringResource(KMR.strings.rec_for_you_selection_open),
                        onClick = onOpen,
                        enabled = true,
                    )
                }
                if (overflow.notInterested || overflow.clearRating || overflow.findBestVersion || overflow.open) {
                    var showMore by remember { mutableStateOf(false) }
                    Box {
                        ForYouActionButton(
                            icon = Icons.Outlined.MoreVert,
                            label = stringResource(KMR.strings.rec_for_you_selection_more),
                            onClick = { showMore = true },
                            enabled = selectedCount > 0,
                        )
                        DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                            if (overflow.notInterested) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(KMR.strings.rec_mark_seen)) },
                                    leadingIcon = { Icon(Icons.Outlined.VisibilityOff, contentDescription = null) },
                                    onClick = {
                                        showMore = false
                                        onNotInterested()
                                    },
                                )
                            }
                            if (overflow.clearRating) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(KMR.strings.rec_for_you_selection_clear_rating)) },
                                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                                    onClick = {
                                        showMore = false
                                        onClearRating()
                                    },
                                )
                            }
                            if (overflow.findBestVersion) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(KMR.strings.rec_for_you_selection_find_best_version)) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.CompareArrows, contentDescription = null) },
                                    onClick = {
                                        showMore = false
                                        onFindBestVersion()
                                    },
                                )
                            }
                            if (overflow.open) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(KMR.strings.rec_for_you_selection_open)) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null) },
                                    onClick = {
                                        showMore = false
                                        onOpen()
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

// KMK v0.8.17-fix1: shared direct-action button for the For You selection bottom bar -- fixes the
// plan's alignment complaint by giving every direct action the same icon size, spacing, label style,
// minimum touch height, and icon-above-label layout (rather than mixed raw `TextButton` Row content
// whose icon/text baselines drift depending on label length). A `Column` layout keeps every button's
// height identical regardless of whether its label wraps.
@Composable
private fun ForYouActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    androidx.compose.material3.TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = MaterialTheme.padding.extraSmall),
        modifier = Modifier
            .defaultMinSize(minHeight = 56.dp)
            .widthIn(min = 56.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

// KMK v0.8.17-fix1: shows one short toast summarizing a bulk For You action's outcome. Failures are
// never silently swallowed -- a partial or total failure gets distinct wording from a clean success.
// KMK v0.8.19: message text now comes from the shared bulkTasteActionMessage() (BulkTasteActionFeedback.kt),
// naming the specific action (Love/Like/Dislike/Not Interested/Clear Rating) with correct
// singular/plural wording, instead of the previous generic "N manga updated" text -- the same
// builder Loved/Liked/Disliked use, so the wording matches across screens for the same action.
// KMK v0.8.19 code-review follow-up: still no Undo here (unlike Loved/Liked/Disliked), and this is a
// real, acknowledged gap in the Undo Journal's safety-net coverage -- not merely a documentation note.
// The blocker is structural: this Tab renders inside `BrowseTab`'s own shared `Scaffold` (see the
// `RecommendationCollectionQuickAccessPanel` overlay comment further up this file), which has no
// `SnackbarHostState` and is shared across every Browse tab, not just For You. Wiring Undo here
// requires either (a) adding a `SnackbarHost` to that shared Scaffold -- a change that affects every
// Browse tab's layout and needs its own dedicated verification pass, not a drive-by edit alongside
// unrelated fixes -- or (b) giving this Tab its own local Snackbar surface layered above its content,
// mirroring the `showFabMenu`-style overlay pattern already used near line 262. Both are real,
// scoped implementation work. Until one is adopted, the Evaluation Mode Undo Journal
// (`EvaluationModeActionHistoryScreen`) remains the supported recovery surface for these actions;
// this Tab's inline feedback intentionally does not claim to provide Undo.
private fun showBulkActionFeedback(context: android.content.Context, action: BulkTasteActionType, outcome: BulkTasteOutcome) {
    val message = bulkTasteActionMessage(context, action, outcome) ?: return
    context.toast(message)
}

@Composable
private fun ActiveFocusSummary(
    focusCriteria: RecommendationFocusPolicy.FocusCriteria,
    onClearFocus: () -> Unit,
) {
    val summaryParts = buildList {
        if (focusCriteria.include.isNotEmpty()) {
            add(
                stringResource(
                    KMR.strings.rec_for_you_focus_criteria_include_label,
                    focusCriteria.include.joinToString(", "),
                ),
            )
        }
        if (focusCriteria.exclude.isNotEmpty()) {
            add(
                stringResource(
                    KMR.strings.rec_for_you_focus_criteria_exclude_label,
                    focusCriteria.exclude.joinToString(", "),
                ),
            )
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                KMR.strings.rec_for_you_focus_active_summary,
                summaryParts.joinToString("; "),
            ),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onClearFocus) {
            Text(stringResource(KMR.strings.rec_for_you_focus_clear))
        }
    }
}

// KMK v0.8.21-fix5: R4/AUG-14 completion -- honest per-section empty state for a section a real
// focus filter has genuinely emptied out (the original, pre-filter result was non-empty). Offers
// "Show Broader Results" as the one explicit escape hatch, never a spinner (this is not a loading
// state) and never a silent collapse of the row (which would look indistinguishable from "no data
// loaded").
@Composable
private fun FocusSectionEmptyState(onShowBroaderResults: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
    ) {
        Text(
            text = stringResource(KMR.strings.rec_for_you_focus_section_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onShowBroaderResults) {
            Text(stringResource(KMR.strings.rec_for_you_focus_show_broader))
        }
    }
}
// KMK <--
