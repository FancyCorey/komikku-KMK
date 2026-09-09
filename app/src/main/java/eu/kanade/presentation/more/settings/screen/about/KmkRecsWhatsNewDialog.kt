package eu.kanade.presentation.more.settings.screen.about

// KMK -->
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import exh.recs.KmkRecsReleaseNotes
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

// KMK F2-05.0.2 (KFC-V0.8.21-FIX2-CORRECTIVE-RECHECK-AND-RELEASE-PROGRAM): stable, locale- and
// text-independent Compose semantics tag for this dialog's confirm/OK button. This dialog and the
// upstream-owned WhatsNewDialog show sequentially on first launch and share the identical
// locale-dependent "OK" text; this dialog is fork-owned Komikku FC code (its own screen, not an
// upstream component), so it retains this selector -- per the small-downstream-diff standard,
// WhatsNewDialog.kt itself stays unmodified/upstream-clean and is instead dismissed by
// macrobenchmark's StartupBenchmark.kt via a controlled-locale text match (see that file's own
// dismissFirstRunUiIfPresent KDoc for the full reasoning).
const val KMK_RECS_WHATS_NEW_DIALOG_CONFIRM_BUTTON_TEST_TAG = "kmk_recs_whats_new_dialog_confirm_button"

@Composable
fun KmkRecsWhatsNewDialog(
    onDismissRequest: () -> Unit,
    onOpenWhatsNew: () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(KMR.strings.kmk_recs_updated, KmkRecsReleaseNotes.DISPLAY_VERSION_NAME)) },
        // KMK v0.8.1-fix2: short body so the dialog isn't just a bare title + two buttons
        text = { Text(text = stringResource(KMR.strings.kmk_recs_updated_body)) },
        confirmButton = {
            TextButton(
                onClick = onDismissRequest,
                // KMK F2-05.0.2: AlertDialog renders in its own separate Compose window, so
                // MainActivity's root-level testTagsAsResourceId does not reach here; set locally
                // instead.
                modifier = Modifier
                    .semantics { testTagsAsResourceId = true }
                    .testTag(KMK_RECS_WHATS_NEW_DIALOG_CONFIRM_BUTTON_TEST_TAG),
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onOpenWhatsNew) {
                Text(text = stringResource(KMR.strings.kmk_recs_whats_new))
            }
        },
    )
}
// KMK <--
