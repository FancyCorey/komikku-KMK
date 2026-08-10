package exh.recs.share

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.extension.model.Extension
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun LoadErrorKey.toLocalString(): String = when (this) {
    is LoadErrorKey.WrongSchema ->
        stringResource(KMR.strings.rec_bundle_load_error_wrong_schema, found)
    is LoadErrorKey.UnsupportedVersion ->
        stringResource(KMR.strings.rec_bundle_load_error_unsupported_version, found)
    LoadErrorKey.TooManyItems ->
        stringResource(KMR.strings.rec_bundle_load_error_too_many_items, RecommendationBundleValidator.MAX_ITEMS)
    LoadErrorKey.TooManySources ->
        stringResource(KMR.strings.rec_bundle_load_error_too_many_sources, RecommendationBundleValidator.MAX_SOURCES)
    LoadErrorKey.FileTooLarge ->
        stringResource(KMR.strings.rec_bundle_load_error_file_too_large)
    is LoadErrorKey.MalformedJson ->
        if (detail != null) {
            stringResource(KMR.strings.rec_bundle_load_error_malformed_json, detail)
        } else {
            stringResource(KMR.strings.rec_bundle_load_error_malformed_json_unknown)
        }
}

// KMK -->

class RecommendationBundleImportScreen(
    private val uriString: String,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel { RecommendationBundleImportScreenModel(uriString, context) }
        val state by screenModel.state.collectAsState()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.rec_bundle_import_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            when (val s = state) {
                is RecommendationBundleImportScreenModel.State.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(contentPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator()
                            Text(
                                text = stringResource(KMR.strings.rec_bundle_import_loading),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                is RecommendationBundleImportScreenModel.State.LoadError -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(contentPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = s.error.toLocalString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(MaterialTheme.padding.medium),
                        )
                    }
                }

                is RecommendationBundleImportScreenModel.State.Preview -> {
                    PreviewContent(
                        state = s,
                        screenModel = screenModel,
                        context = context,
                        contentPadding = contentPadding,
                    )
                }
            }
        }
    }

    @Composable
    private fun PreviewContent(
        state: RecommendationBundleImportScreenModel.State.Preview,
        screenModel: RecommendationBundleImportScreenModel,
        context: Context,
        contentPadding: androidx.compose.foundation.layout.PaddingValues,
    ) {
        val missingExtensions = state.items
            .mapNotNull { (_, itemState) ->
                (itemState as? RecommendationImportItemState.MissingSource)?.availableExt
            }
            .distinctBy { it.pkgName }

        val ambiguousCandidates = state.items
            .flatMap { (_, itemState) ->
                (itemState as? RecommendationImportItemState.AmbiguousSource)?.candidates ?: emptyList()
            }
            .distinctBy { it.pkgName }

        Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                // Bundle info card
                item(key = "bundle_info") {
                    BundleInfoCard(state.bundle)
                }

                // Missing extension install cards
                missingExtensions.forEach { ext ->
                    item(key = "install_${ext.pkgName}") {
                        InstallExtensionCard(
                            ext = ext,
                            isInstalling = state.installingPkgName == ext.pkgName,
                            onInstall = { screenModel.installMissingExtension(ext) },
                        )
                    }
                }

                // Ambiguous extension install cards (one per candidate)
                ambiguousCandidates.forEach { ext ->
                    item(key = "install_ambiguous_${ext.pkgName}") {
                        InstallExtensionCard(
                            ext = ext,
                            isInstalling = state.installingPkgName == ext.pkgName,
                            onInstall = { screenModel.installMissingExtension(ext) },
                            labelRes = KMR.strings.rec_bundle_ambiguous_source_prompt,
                        )
                    }
                }

                // Items
                itemsIndexed(
                    items = state.items,
                    key = { idx, entry -> "${idx}_${entry.bundleItem.sourceId}_${entry.bundleItem.url}" },
                ) { idx, entry ->
                    ImportItemRow(
                        entry = entry,
                        isSelected = idx in state.selectedIndices,
                        onToggle = { screenModel.toggleSelection(idx) },
                    )
                }
            }

            // Bottom action bar
            if (state.isAdding) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(MaterialTheme.padding.medium),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (state.selectedIndices.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            if (state.selectedIndices.size == state.selectableCount) {
                                screenModel.deselectAll()
                            } else {
                                screenModel.selectAll()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            if (state.selectedIndices.size == state.selectableCount) {
                                stringResource(KMR.strings.action_deselect_all)
                            } else {
                                stringResource(KMR.strings.action_select_all)
                            },
                        )
                    }
                    Button(
                        onClick = screenModel::addSelected,
                        modifier = Modifier.weight(2f),
                    ) {
                        Text(
                            stringResource(KMR.strings.rec_bundle_import_add_selected, state.selectedIndices.size),
                        )
                    }
                }
            }
        }

        // Add summary dialog
        state.addSummary?.let { summary ->
            AlertDialog(
                onDismissRequest = screenModel::dismissSummary,
                title = { Text(stringResource(KMR.strings.rec_bundle_import_complete_title)) },
                text = {
                    Text(
                        stringResource(
                            KMR.strings.rec_bundle_import_complete_body,
                            summary.added,
                            summary.alreadyInLibrary,
                            summary.failed,
                        ),
                    )
                },
                confirmButton = {
                    TextButton(onClick = screenModel::dismissSummary) {
                        Text(stringResource(tachiyomi.i18n.MR.strings.action_ok))
                    }
                },
            )
        }
    }

    @Composable
    private fun BundleInfoCard(bundle: RecommendationBundle) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(modifier = Modifier.padding(MaterialTheme.padding.medium), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = bundle.title, style = MaterialTheme.typography.titleMedium)
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(bundle.createdAt))
                Text(
                    text = stringResource(
                        KMR.strings.rec_bundle_import_bundle_info,
                        dateStr,
                        bundle.items.size,
                        bundle.requiredSources.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }

    @Composable
    private fun InstallExtensionCard(
        ext: Extension.Available,
        isInstalling: Boolean,
        onInstall: () -> Unit,
        labelRes: StringResource = KMR.strings.rec_bundle_missing_source_prompt,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.padding.medium, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        ) {
            Row(
                modifier = Modifier.padding(MaterialTheme.padding.medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(labelRes, ext.name),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (isInstalling) {
                    CircularProgressIndicator()
                } else {
                    OutlinedButton(onClick = onInstall) {
                        Text(stringResource(KMR.strings.rec_bundle_install_extension))
                    }
                }
            }
        }
    }

    @Composable
    private fun ImportItemRow(
        entry: ImportItemEntry,
        isSelected: Boolean,
        onToggle: () -> Unit,
    ) {
        val selectable = entry.itemState is RecommendationImportItemState.ReadyToAdd ||
            entry.itemState is RecommendationImportItemState.SourceInstalledNeedsResolve

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.padding.medium, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { if (selectable) onToggle() },
                enabled = selectable,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = entry.bundleItem.title, style = MaterialTheme.typography.bodyMedium)
                entry.bundleItem.author?.let { author ->
                    Text(text = author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ImportItemStateBadge(entry.itemState)
        }
    }

    @Composable
    private fun ImportItemStateBadge(itemState: RecommendationImportItemState) {
        val label = when (itemState) {
            is RecommendationImportItemState.ReadyToAdd ->
                stringResource(KMR.strings.rec_bundle_import_state_ready)
            is RecommendationImportItemState.AlreadyInLibrary ->
                stringResource(KMR.strings.rec_bundle_import_state_in_library)
            is RecommendationImportItemState.MissingSource ->
                stringResource(KMR.strings.rec_bundle_import_state_missing_source)
            is RecommendationImportItemState.AmbiguousSource ->
                stringResource(KMR.strings.rec_bundle_import_state_ambiguous_source)
            is RecommendationImportItemState.SourceInstalledNeedsResolve ->
                stringResource(KMR.strings.rec_bundle_import_state_resolving)
            is RecommendationImportItemState.NeedsManualMatch ->
                stringResource(KMR.strings.rec_bundle_import_state_needs_match)
            is RecommendationImportItemState.Unsupported ->
                stringResource(KMR.strings.rec_bundle_import_state_unsupported)
            is RecommendationImportItemState.Error ->
                stringResource(KMR.strings.rec_bundle_import_state_error)
        }

        SuggestionChip(
            onClick = {},
            label = { Text(text = label, style = MaterialTheme.typography.labelSmall) },
        )
    }
}

// KMK <--
