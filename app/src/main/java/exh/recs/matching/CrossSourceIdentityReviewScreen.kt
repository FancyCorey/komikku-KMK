package exh.recs.matching

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import exh.util.rememberEvaluationModeEnabled
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

class CrossSourceIdentityReviewScreen : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { CrossSourceIdentityReviewScreenModel() }
        val state by screenModel.state.collectAsState()
        val evaluationMode = rememberEvaluationModeEnabled()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.identity_review_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = MaterialTheme.padding.medium),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    items(CrossSourceIdentityReviewFilter.entries) { filter ->
                        FilterChip(
                            selected = state.selectedFilter == filter,
                            onClick = { screenModel.selectFilter(filter) },
                            label = { Text(filterLabel(filter)) },
                        )
                    }
                }
                state.feedback?.let { feedback ->
                    Text(
                        text = feedbackLabel(feedback),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (feedback in setOf(CrossSourceIdentityMutationResult.CONFLICT, CrossSourceIdentityMutationResult.FAILED)) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                    )
                }
                when {
                    state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    state.failed -> Text(
                        text = stringResource(KMR.strings.identity_review_failed),
                        modifier = Modifier.padding(MaterialTheme.padding.medium),
                    )
                    state.filteredRows.isEmpty() -> Text(
                        text = stringResource(KMR.strings.identity_review_empty),
                        modifier = Modifier.padding(MaterialTheme.padding.medium),
                    )
                    else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(state.filteredRows, key = { it.token }) { row ->
                            IdentityReviewRow(
                                row = row,
                                evaluationMode = evaluationMode,
                                enabled = !state.isMutating,
                                onInspect = { screenModel.inspect(row.token) },
                                onMutate = { screenModel.mutate(row.token, it) },
                            )
                        }
                    }
                }
            }
        }

        state.inspectedRow?.let { row ->
            AlertDialog(
                onDismissRequest = screenModel::dismissInspection,
                title = { Text(stringResource(KMR.strings.identity_review_inspect)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                        Text(rowLabel(row, true, evaluationMode))
                        Text(rowLabel(row, false, evaluationMode))
                        Text(
                            when (row.filter) {
                                CrossSourceIdentityReviewFilter.LEGACY -> stringResource(KMR.strings.identity_review_legacy_notice)
                                CrossSourceIdentityReviewFilter.NEEDS_REVIEW -> stringResource(KMR.strings.identity_review_conflict_notice)
                                else -> stringResource(KMR.strings.identity_review_current_notice)
                            },
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = screenModel::dismissInspection) {
                        Text(stringResource(tachiyomi.i18n.MR.strings.action_ok))
                    }
                },
            )
        }
    }
}

@Composable
private fun IdentityReviewRow(
    row: CrossSourceIdentityReviewRow,
    evaluationMode: Boolean,
    enabled: Boolean,
    onInspect: () -> Unit,
    onMutate: (CrossSourceIdentityMutation) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(rowLabel(row, true, evaluationMode)) },
        supportingContent = { Text(rowLabel(row, false, evaluationMode)) },
        modifier = Modifier.fillMaxWidth(),
        trailingContent = {
            androidx.compose.foundation.layout.Box {
                IconButton(onClick = { menuExpanded = true }, enabled = enabled) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(KMR.strings.identity_review_inspect))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(KMR.strings.identity_review_inspect)) },
                        onClick = {
                            menuExpanded = false
                            onInspect()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(KMR.strings.identity_review_confirm)) },
                        onClick = {
                            menuExpanded = false
                            onMutate(CrossSourceIdentityMutation.CONFIRM)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(KMR.strings.identity_review_reject)) },
                        onClick = {
                            menuExpanded = false
                            onMutate(CrossSourceIdentityMutation.REJECT)
                        },
                    )
                    if (row.filter != CrossSourceIdentityReviewFilter.LEGACY) {
                        DropdownMenuItem(
                            text = { Text(stringResource(KMR.strings.identity_review_clear)) },
                            onClick = {
                                menuExpanded = false
                                onMutate(CrossSourceIdentityMutation.CLEAR)
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun rowLabel(row: CrossSourceIdentityReviewRow, first: Boolean, evaluationMode: Boolean): String {
    if (evaluationMode) {
        return stringResource(if (first) KMR.strings.identity_review_version_one else KMR.strings.identity_review_version_two)
    }
    val title = if (first) row.firstTitle else row.secondTitle
    val source = if (first) row.firstSource else row.secondSource
    val safeSource = source ?: stringResource(KMR.strings.identity_review_source_unavailable)
    return if (title.isBlank()) safeSource else "$title - $safeSource"
}

@Composable
private fun filterLabel(filter: CrossSourceIdentityReviewFilter): String = stringResource(
    when (filter) {
        CrossSourceIdentityReviewFilter.CONFIRMED -> KMR.strings.identity_review_confirmed
        CrossSourceIdentityReviewFilter.REJECTED -> KMR.strings.identity_review_rejected
        CrossSourceIdentityReviewFilter.LEGACY -> KMR.strings.identity_review_legacy
        CrossSourceIdentityReviewFilter.NEEDS_REVIEW -> KMR.strings.identity_review_needs_review
    },
)

@Composable
private fun feedbackLabel(result: CrossSourceIdentityMutationResult): String = stringResource(
    when (result) {
        CrossSourceIdentityMutationResult.APPLIED -> KMR.strings.identity_review_applied
        CrossSourceIdentityMutationResult.UNCHANGED -> KMR.strings.identity_review_unchanged
        CrossSourceIdentityMutationResult.CONFLICT -> KMR.strings.identity_review_conflict
        CrossSourceIdentityMutationResult.FAILED -> KMR.strings.identity_review_failed
    },
)
