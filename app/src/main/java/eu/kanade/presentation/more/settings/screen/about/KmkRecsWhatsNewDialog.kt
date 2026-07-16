package eu.kanade.presentation.more.settings.screen.about

// KMK -->
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import exh.recs.KmkRecsReleaseNotes
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun KmkRecsWhatsNewDialog(
    onDismissRequest: () -> Unit,
    onOpenWhatsNew: () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(KMR.strings.kmk_recs_updated, KmkRecsReleaseNotes.VERSION_NAME)) },
        // KMK v0.8.1-fix2: short body so the dialog isn't just a bare title + two buttons
        text = { Text(text = stringResource(KMR.strings.kmk_recs_updated_body)) },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
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
