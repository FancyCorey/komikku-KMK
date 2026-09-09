package eu.kanade.presentation.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderCandidateRow
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderGenericLabelKind
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderLoadState
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderOpaqueToken
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderPresentation
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderRecoveryReason
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderResolvingPurpose
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun AlternateSourceReaderDialog(
    presentation: AlternateSourceReaderPresentation,
    onDismiss: () -> Unit,
    onSearchMore: () -> Unit,
    onSearchInApp: () -> Unit,
    onSelectSource: (AlternateSourceReaderOpaqueToken) -> Unit,
    onConfirmSource: () -> Unit,
    onSelectChapter: (AlternateSourceReaderOpaqueToken) -> Unit,
    onConfirmChapter: () -> Unit,
    onConfirmPair: () -> Unit,
    onConfirmProvisional: () -> Unit,
    onConfirmSkip: () -> Unit,
    onRetry: () -> Unit,
) {
    when (presentation) {
        AlternateSourceReaderPresentation.Hidden -> Unit
        is AlternateSourceReaderPresentation.ChoosingSource -> SelectionDialog(
            title = stringResource(KMR.strings.alternate_source_reader_choose_source_title),
            content = presentation.content,
            onDismiss = onDismiss,
            onSelect = onSelectSource,
            selectedToken = presentation.selectedToken,
            onConfirm = onConfirmSource,
            extraAction = onSearchMore,
            inAppAction = onSearchInApp,
        )
        is AlternateSourceReaderPresentation.ChoosingChapter -> SelectionDialog(
            title = stringResource(KMR.strings.alternate_source_reader_choose_chapter_title),
            content = presentation.content,
            onDismiss = onDismiss,
            onSelect = onSelectChapter,
            selectedToken = presentation.selectedToken,
            onConfirm = onConfirmChapter,
            retryAction = onRetry,
        )
        is AlternateSourceReaderPresentation.ConfirmPair -> ConfirmationDialog(
            title = stringResource(KMR.strings.alternate_source_reader_confirm_pair_title),
            message = stringResource(KMR.strings.alternate_source_reader_confirm_pair_message),
            onDismiss = onDismiss,
            onConfirm = onConfirmPair,
        )
        is AlternateSourceReaderPresentation.ConfirmProvisional -> ConfirmationDialog(
            title = stringResource(KMR.strings.alternate_source_reader_confirm_provisional_title),
            message = stringResource(KMR.strings.alternate_source_reader_confirm_provisional_message),
            onDismiss = onDismiss,
            onConfirm = onConfirmProvisional,
        )
        AlternateSourceReaderPresentation.ConfirmSkip -> ConfirmationDialog(
            title = stringResource(KMR.strings.alternate_source_reader_confirm_skip_title),
            message = stringResource(KMR.strings.alternate_source_reader_confirm_skip_message),
            onDismiss = onDismiss,
            onConfirm = onConfirmSkip,
        )
        is AlternateSourceReaderPresentation.Resolving -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text(
                        stringResource(
                            when (presentation.purpose) {
                                AlternateSourceReaderResolvingPurpose.ENTRY -> KMR.strings.alternate_source_reader_resolving_entry
                                AlternateSourceReaderResolvingPurpose.CORRECTION -> KMR.strings.alternate_source_reader_resolving_correction
                                AlternateSourceReaderResolvingPurpose.RETURN -> KMR.strings.alternate_source_reader_resolving_return
                            },
                        ),
                    )
                }
            },
        )
        is AlternateSourceReaderPresentation.RecoverableFailure -> AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                if (presentation.canRetry) {
                    TextButton(onClick = onRetry, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                        Text(stringResource(MR.strings.action_retry))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
            text = { Text(stringResource(presentation.reason.resource)) },
        )
    }
}

@Composable
private fun SelectionDialog(
    title: String,
    content: AlternateSourceReaderLoadState<AlternateSourceReaderCandidateRow>,
    onDismiss: () -> Unit,
    onSelect: (AlternateSourceReaderOpaqueToken) -> Unit,
    selectedToken: AlternateSourceReaderOpaqueToken? = null,
    onConfirm: (() -> Unit)? = null,
    extraAction: (() -> Unit)? = null,
    inAppAction: (() -> Unit)? = null,
    retryAction: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = {
            FlowRow(
                horizontalArrangement = Arrangement.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                inAppAction?.let {
                    TextButton(onClick = it, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                        Text(stringResource(KMR.strings.alternate_source_reader_search_in_app))
                    }
                }
                extraAction?.let {
                    TextButton(onClick = it, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                        Text(stringResource(KMR.strings.alternate_source_reader_search_more))
                    }
                }
                if (content is AlternateSourceReaderLoadState.Failed) {
                    retryAction?.let {
                        TextButton(onClick = it, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                            Text(stringResource(MR.strings.action_retry))
                        }
                    }
                }
                onConfirm?.let {
                    TextButton(
                        onClick = it,
                        enabled = selectedToken != null,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    ) {
                        Text(stringResource(MR.strings.action_ok))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (content) {
                    AlternateSourceReaderLoadState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        Text(stringResource(KMR.strings.alternate_source_reader_loading))
                    }
                    AlternateSourceReaderLoadState.Empty -> Text(
                        stringResource(KMR.strings.alternate_source_reader_empty),
                    )
                    is AlternateSourceReaderLoadState.Failed -> Text(stringResource(content.reason.resource))
                    is AlternateSourceReaderLoadState.Content -> {
                        if (content.hasPartialFailure) {
                            Text(
                                text = stringResource(KMR.strings.alternate_source_reader_partial),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        content.items.forEach { row ->
                            val label = row.primaryLabel ?: row.genericLabel?.let { generic ->
                                stringResource(
                                    when (generic.kind) {
                                        AlternateSourceReaderGenericLabelKind.SOURCE ->
                                            KMR.strings.alternate_source_reader_generic_source
                                        AlternateSourceReaderGenericLabelKind.CHAPTER ->
                                            KMR.strings.alternate_source_reader_generic_chapter
                                    },
                                    generic.ordinal,
                                )
                            }.orEmpty()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .selectable(
                                        selected = row.token == selectedToken,
                                        role = Role.RadioButton,
                                        onClick = { onSelect(row.token) },
                                    )
                                    .semantics { stateDescription = label }
                                    .padding(vertical = 8.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = row.token == selectedToken,
                                        onClick = null,
                                    )
                                    Text(label, style = MaterialTheme.typography.bodyLarge)
                                }
                                row.secondaryLabel?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun ConfirmationDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

private val AlternateSourceReaderRecoveryReason.resource
    get() = when (this) {
        AlternateSourceReaderRecoveryReason.ROUTE_UNAVAILABLE -> KMR.strings.alternate_source_reader_error_route_unavailable
        AlternateSourceReaderRecoveryReason.ROUTE_CHANGED -> KMR.strings.alternate_source_reader_error_route_changed
        AlternateSourceReaderRecoveryReason.CONFLICT -> KMR.strings.alternate_source_reader_error_conflict
        AlternateSourceReaderRecoveryReason.RETRIES_EXHAUSTED -> KMR.strings.alternate_source_reader_error_retries
        AlternateSourceReaderRecoveryReason.LOOP_STOPPED -> KMR.strings.alternate_source_reader_error_loop
        AlternateSourceReaderRecoveryReason.INVALID_SESSION -> KMR.strings.alternate_source_reader_error_invalid_session
        AlternateSourceReaderRecoveryReason.FAILED -> KMR.strings.alternate_source_reader_error_failed
    }
