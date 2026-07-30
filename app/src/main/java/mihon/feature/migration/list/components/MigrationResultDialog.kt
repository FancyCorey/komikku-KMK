package mihon.feature.migration.list.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

// KMK Confirmed Blocker Remediation follow-up Phase 1 2026-07-29, corrected by the Corrective Pass -->
/**
 * Shown after a bulk migration run when one or more items returned a non-[mihon.domain.migration.
 * usecases.MigrationOutcome.Success] result, or had no successful search result at all --
 * previously [MigrationListScreenModel] discarded each item's outcome entirely and always navigated
 * back as if every migration had fully succeeded. Dismissing this dialog only closes it (see
 * [MigrationListScreenModel.dismissResultDialog]) -- it does not navigate away, since the failed/
 * unresolved items named here are still visible in the list, retryable via Migrate/Copy or
 * explicitly dismissible via Skip.
 */
@Composable
fun MigrationResultDialog(
    failedCount: Int,
    skippedCount: Int,
    totalCount: Int,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(KMR.strings.migration_list_result_partial_title))
        },
        text = {
            Text(
                text = if (skippedCount > 0) {
                    stringResource(KMR.strings.migration_list_result_partial_message_with_skipped, failedCount, skippedCount, totalCount)
                } else {
                    stringResource(KMR.strings.migration_list_result_partial_message, failedCount, totalCount)
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
    )
}
// KMK <--
