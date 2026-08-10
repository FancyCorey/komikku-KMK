package exh.recs.settings

// KMK --> v0.7.27: Best Version quality signal history screen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.util.Screen
import exh.util.rememberEvaluationModeEnabled
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.taste.model.MangaSourceQualitySignal
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class QualitySignalHistoryScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { QualitySignalHistoryScreenModel() }
        val state by screenModel.state.collectAsState()
        val evaluationModeEnabled = rememberEvaluationModeEnabled()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.quality_signal_history_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        if (state.groups.isNotEmpty()) {
                            AppBarActions(
                                persistentListOf(
                                    AppBar.Action(
                                        title = stringResource(KMR.strings.quality_signal_history_delete_all_button),
                                        icon = Icons.Outlined.DeleteSweep,
                                        onClick = screenModel::requestDeleteAll,
                                    ),
                                ),
                            )
                        }
                    },
                )
            },
        ) { contentPadding ->
            when {
                state.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(contentPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.groups.isEmpty() -> {
                    EmptyScreen(
                        message = stringResource(KMR.strings.quality_signal_history_empty),
                        modifier = Modifier.fillMaxSize().padding(contentPadding),
                    )
                }
                else -> {
                    LazyColumn(contentPadding = contentPadding) {
                        state.groups.forEach { group ->
                            item(key = "group_${group.originTitle}") {
                                Text(
                                    text = group.originTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(
                                        horizontal = MaterialTheme.padding.medium,
                                        vertical = MaterialTheme.padding.small,
                                    ),
                                )
                            }
                            group.records.forEach { record ->
                                item(key = "record_${record.id}") {
                                    QualitySignalRecordItem(
                                        record = record,
                                        evaluationModeEnabled = evaluationModeEnabled,
                                        onDelete = { screenModel.deleteRecord(record.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (state.showDeleteAllDialog) {
            AlertDialog(
                onDismissRequest = screenModel::dismissDeleteAllDialog,
                title = { Text(stringResource(KMR.strings.quality_signal_history_delete_all_title)) },
                text = { Text(stringResource(KMR.strings.quality_signal_history_delete_all_message)) },
                confirmButton = {
                    TextButton(onClick = screenModel::confirmDeleteAll) {
                        Text(stringResource(KMR.strings.quality_signal_history_delete_all_button))
                    }
                },
                dismissButton = {
                    TextButton(onClick = screenModel::dismissDeleteAllDialog) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun QualitySignalRecordItem(
    record: MangaSourceQualitySignal,
    evaluationModeEnabled: Boolean,
    onDelete: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.extraSmall),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.padding.medium,
                    end = MaterialTheme.padding.extraSmall,
                    top = MaterialTheme.padding.small,
                    bottom = MaterialTheme.padding.small,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        KMR.strings.quality_signal_history_selected_source,
                        QualitySignalSourceLabelPolicy.resolve(
                            evaluationModeEnabled = evaluationModeEnabled,
                            sourceId = record.selectedSourceId,
                            rawName = { record.selectedSourceName },
                        ),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (record.selectedTitle != record.originTitle) {
                    Text(
                        text = stringResource(KMR.strings.quality_signal_history_selected_title, record.selectedTitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val chapterLabel = when {
                    record.chapterName.isNotBlank() -> record.chapterName
                    record.chapterNumber != null -> "Ch. ${record.chapterNumber}"
                    else -> null
                }
                if (chapterLabel != null) {
                    Text(
                        text = stringResource(KMR.strings.quality_signal_history_chapter, chapterLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = formatDate(record.selectedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(KMR.strings.quality_signal_history_delete_record),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatDate(epochMs: Long): String {
    val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return fmt.format(Date(epochMs))
}
// KMK <--
