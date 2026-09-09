package eu.kanade.presentation.track

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.domain.track.model.LocalTrackingActionPolicy
import eu.kanade.tachiyomi.ui.manga.track.LocalTrackingReconciliationPolicy
import eu.kanade.tachiyomi.ui.manga.track.TrackItem
import tachiyomi.domain.tracker.model.LocalTrackedWork
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun LocalTrackingReconciliationDialog(
    work: LocalTrackedWork,
    entries: List<TrackItem>,
    dateFormat: DateTimeFormatter,
    onImport: (TrackItem) -> Unit,
    onExport: (TrackItem) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(KMR.strings.local_tracking_reconcile_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = work.title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(KMR.strings.local_tracking_reconcile_notice),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Text(
                    text = stringResource(
                        KMR.strings.local_tracking_reconcile_local_value,
                        formatMetadata(
                            LocalTrackedWork.Metadata(
                                chapterNumber = work.lastChapterNumber,
                                score = work.score,
                                startDate = work.startDate,
                                finishDate = work.finishDate,
                            ),
                            dateFormat,
                        ),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                entries.filter { it.track != null }.forEach { item ->
                    val metadata = LocalTrackingReconciliationPolicy.externalSnapshot(item.track!!, item.tracker)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(item.tracker.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = stringResource(KMR.strings.local_tracking_reconcile_external_value, formatMetadata(metadata, dateFormat)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    metadata.status?.let { status ->
                        Text(
                            text = stringResource(
                                KMR.strings.local_tracking_reconcile_status,
                                stringResource(LocalTrackingActionPolicy.statusLabel(status)),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row {
                        TextButton(onClick = { onImport(item) }) {
                            Text(stringResource(KMR.strings.local_tracking_import))
                        }
                        TextButton(onClick = { onExport(item) }) {
                            Text(stringResource(KMR.strings.local_tracking_export))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
    )
}

private fun formatMetadata(
    metadata: LocalTrackedWork.Metadata,
    dateFormat: DateTimeFormatter,
): String = listOfNotNull(
    metadata.chapterNumber?.let { "${it.toInt()}" },
    metadata.score?.let { "score $it" },
    metadata.startDate?.let { dateFormat.format(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) },
    metadata.finishDate?.let { dateFormat.format(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) },
).joinToString(" • ").ifBlank { "-" }
