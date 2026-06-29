package exh.ocr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import java.text.DecimalFormat

// KMK --> OCR v0.1.1 (updated from v0.1.0)

class OcrSearchScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { OcrSearchScreenModel(context) }
        val state by screenModel.state.collectAsState()

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(KMR.strings.ocr_search_downloads_title),
                    navigateUp = navigator::pop,
                )
            },
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = MaterialTheme.padding.medium),
            ) {
                Spacer(Modifier.height(MaterialTheme.padding.small))

                // Search field
                OutlinedTextField(
                    value = state.query,
                    onValueChange = screenModel::onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(KMR.strings.ocr_search_hint)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { screenModel.onQueryChange("") }) {
                                Icon(Icons.Outlined.Clear, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { }),
                )

                Spacer(Modifier.height(MaterialTheme.padding.small))

                // Disclaimer
                Text(
                    text = stringResource(KMR.strings.ocr_latin_only_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic,
                )

                Spacer(Modifier.height(MaterialTheme.padding.small))

                // Index stats
                val stats = state.indexStats
                if (stats.processedPages > 0 || stats.oldEngineRows > 0) {
                    Text(
                        text = stringResource(
                            KMR.strings.ocr_index_status_v2,
                            stats.recognizedPages,
                            stats.processedPages,
                            stats.totalManga,
                            stats.totalChapters,
                            formatBytes(stats.estimatedBytes),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (stats.oldEngineRows > 0) {
                        TextButton(
                            onClick = screenModel::clearOldEngineRows,
                            modifier = Modifier.padding(top = 0.dp),
                        ) {
                            Text(
                                text = stringResource(KMR.strings.ocr_index_status_old_rows, stats.oldEngineRows),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                // Max pages picker
                MaxPagesRow(
                    currentLimit = state.maxPages,
                    onSelect = screenModel::setMaxPages,
                )

                Spacer(Modifier.height(MaterialTheme.padding.small))

                // Progress
                if (state.isIndexRunning) {
                    val progress = state.indexProgress
                    if (progress != null && progress.totalPages > 0) {
                        Text(
                            text = stringResource(
                                KMR.strings.ocr_indexing_progress_v2,
                                progress.completedPages,
                                progress.totalPages,
                                progress.recognizedPages,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        LinearProgressIndicator(
                            progress = { progress.completedPages.toFloat() / progress.totalPages },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            text = stringResource(KMR.strings.ocr_indexing_starting),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(MaterialTheme.padding.extraSmall))
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    if (state.isIndexRunning) {
                        OutlinedButton(
                            onClick = screenModel::cancelIndexing,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(KMR.strings.ocr_cancel_indexing))
                        }
                    } else {
                        Button(
                            onClick = screenModel::requestIndexAll,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(KMR.strings.ocr_index_all_downloaded))
                        }
                        OutlinedButton(
                            onClick = screenModel::requestForceReindex,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(KMR.strings.ocr_force_reindex_all))
                        }
                    }
                }

                if (!state.isIndexRunning) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        OutlinedButton(
                            onClick = screenModel::requestClearIndex,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(KMR.strings.ocr_clear_index))
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = MaterialTheme.padding.small))

                // Results or empty state
                if (state.isSearching) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (state.query.isNotBlank() && state.results.isEmpty()) {
                    Text(
                        text = stringResource(KMR.strings.ocr_empty_state_no_results_partial, state.query),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = MaterialTheme.padding.medium),
                    )
                } else if (state.query.isBlank() && stats.recognizedPages == 0L && stats.processedPages > 0L) {
                    Text(
                        text = stringResource(KMR.strings.ocr_empty_state_no_recognized),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = MaterialTheme.padding.medium),
                    )
                } else if (state.query.isBlank() && stats.processedPages == 0L) {
                    Text(
                        text = stringResource(KMR.strings.ocr_empty_state_no_index),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = MaterialTheme.padding.medium),
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        items(state.results) { result ->
                            OcrResultCard(result) {
                                val intent = ReaderActivity.newIntent(
                                    context,
                                    result.mangaId,
                                    result.chapterId,
                                    result.pageIndex,
                                )
                                context.startActivity(intent)
                            }
                        }
                    }
                }
            }
        }

        // Index all confirm
        if (state.showIndexAllConfirm) {
            AlertDialog(
                onDismissRequest = screenModel::dismissIndexAllConfirm,
                title = { Text(stringResource(KMR.strings.ocr_index_all_confirm_title)) },
                text = { Text(stringResource(KMR.strings.ocr_index_all_confirm_message)) },
                confirmButton = {
                    Button(onClick = screenModel::startIndexAll) {
                        Text(stringResource(KMR.strings.ocr_index_all_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = screenModel::dismissIndexAllConfirm) {
                        Text(stringResource(tachiyomi.i18n.MR.strings.action_cancel))
                    }
                },
            )
        }

        // Force re-index confirm
        if (state.showForceReindexConfirm) {
            AlertDialog(
                onDismissRequest = screenModel::dismissForceReindexConfirm,
                title = { Text(stringResource(KMR.strings.ocr_force_reindex_confirm_title)) },
                text = { Text(stringResource(KMR.strings.ocr_force_reindex_confirm_message)) },
                confirmButton = {
                    Button(onClick = screenModel::startForceReindex) {
                        Text(stringResource(KMR.strings.ocr_force_reindex_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = screenModel::dismissForceReindexConfirm) {
                        Text(stringResource(tachiyomi.i18n.MR.strings.action_cancel))
                    }
                },
            )
        }

        // Clear confirm
        if (state.showClearConfirm) {
            AlertDialog(
                onDismissRequest = screenModel::dismissClearConfirm,
                title = { Text(stringResource(KMR.strings.ocr_clear_index_confirm_title)) },
                text = { Text(stringResource(KMR.strings.ocr_clear_index_confirm_message)) },
                confirmButton = {
                    Button(onClick = screenModel::clearIndex) {
                        Text(stringResource(KMR.strings.ocr_clear_index_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = screenModel::dismissClearConfirm) {
                        Text(stringResource(tachiyomi.i18n.MR.strings.action_cancel))
                    }
                },
            )
        }

        // Error snackbar
        state.errorMessage?.let { error ->
            Snackbar(
                action = {
                    TextButton(onClick = screenModel::clearError) {
                        Text(stringResource(tachiyomi.i18n.MR.strings.action_ok))
                    }
                },
            ) {
                Text(error)
            }
        }
    }
}

@Composable
private fun MaxPagesRow(currentLimit: Int, onSelect: (Int) -> Unit) {
    val options = listOf(0, 50, 100, 250, 500)
    var expanded by remember { mutableStateOf(false) }
    val label = if (currentLimit == 0) {
        stringResource(KMR.strings.ocr_max_pages_unlimited)
    } else {
        currentLimit.toString()
    }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(
                text = stringResource(KMR.strings.ocr_max_pages_label, label),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { limit ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (limit == 0) {
                                stringResource(KMR.strings.ocr_max_pages_unlimited)
                            } else {
                                limit.toString()
                            },
                        )
                    },
                    onClick = {
                        onSelect(limit)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun OcrResultCard(result: OcrSearchResult, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(MaterialTheme.padding.medium)) {
            Text(
                text = result.mangaTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            if (result.sourceName != null) {
                Text(
                    text = result.sourceName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
            Text(
                text = result.chapterName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(KMR.strings.ocr_result_page, result.pageIndex + 1),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = matchTypeLabel(result),
                style = MaterialTheme.typography.labelSmall,
                color = when (result.matchType) {
                    OcrMatchType.EXACT -> MaterialTheme.colorScheme.primary
                    OcrMatchType.ALL_TOKENS -> MaterialTheme.colorScheme.secondary
                    OcrMatchType.PARTIAL -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (result.snippet.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = result.snippet,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                )
            }
        }
    }
}

@Composable
private fun matchTypeLabel(result: OcrSearchResult): String = when (result.matchType) {
    OcrMatchType.EXACT -> stringResource(KMR.strings.ocr_match_exact)
    OcrMatchType.ALL_TOKENS -> stringResource(KMR.strings.ocr_match_all_tokens)
    OcrMatchType.PARTIAL -> stringResource(
        KMR.strings.ocr_match_partial,
        result.matchedWords.size,
        result.matchedWords.size + result.missingWords.size,
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return "${DecimalFormat("0.#").format(kb)} KB"
    val mb = kb / 1024.0
    return "${DecimalFormat("0.#").format(mb)} MB"
}

// KMK <--
