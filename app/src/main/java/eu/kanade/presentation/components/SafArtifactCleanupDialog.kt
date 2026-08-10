package eu.kanade.presentation.components

import android.content.Context
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.util.export.REMOVABLE_SAF_OUTCOMES
import eu.kanade.tachiyomi.util.export.SafArtifactOutcome
import eu.kanade.tachiyomi.util.export.SafCleanupOffer
import eu.kanade.tachiyomi.util.export.deleteSafDocument
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

// KMK: the outcome -> rendered-action mapping
// is extracted into this plain, non-@Composable function specifically so it can be exercised by a pure
// JVM behavioral test (no Compose test runtime available in this module's unit tests) -- proving
// SafArtifactOutcome.UNRESOLVED can never resolve to [SafCleanupDialogAction.REMOVE_OR_KEEP] is a
// property of this function, not of prose/comments next to the composable that uses it.
internal enum class SafCleanupDialogAction {
    /** Nothing is rendered -- [SafArtifactOutcome.IN_PROGRESS], or an unrecognized future outcome. */
    NONE,

    /** A single non-destructive acknowledge action only -- never a Remove button. */
    UNRESOLVED_ACKNOWLEDGE,

    /** The full Remove/Keep dialog -- for failed writes, or an explicit debug-success fixture. */
    REMOVE_OR_KEEP,
}

internal fun safCleanupDialogActionFor(
    outcome: SafArtifactOutcome,
    allowSuccessfulRemoval: Boolean = false,
): SafCleanupDialogAction = when {
    outcome == SafArtifactOutcome.UNRESOLVED -> SafCleanupDialogAction.UNRESOLVED_ACKNOWLEDGE
    outcome == SafArtifactOutcome.SUCCESS && allowSuccessfulRemoval -> SafCleanupDialogAction.REMOVE_OR_KEEP
    outcome in REMOVABLE_SAF_OUTCOMES -> SafCleanupDialogAction.REMOVE_OR_KEEP
    else -> SafCleanupDialogAction.NONE
}

/**
 * The exact deletion boundary the Remove button's `onClick` uses -- extracted so a pure JVM test can
 * prove [onRemoved] (which clears the retained cleanup offer) fires if and only if
 * [eu.kanade.tachiyomi.util.export.deleteSafDocument] actually succeeds, without needing a Compose
 * test runtime to click the button itself. Returns whether the deletion succeeded, same as
 * [deleteSafDocument], so the caller can choose the right toast text.
 */
internal fun performSafRemoveAction(context: Context, uri: Uri, onRemoved: (Uri) -> Unit): Boolean {
    val removed = deleteSafDocument(context, uri)
    if (removed) {
        onRemoved(uri)
    }
    return removed
}

// KMK -->
// KMK: one shared Remove/Keep cleanup dialog for
// every `CreateDocument` writer in the app, so the extension-export-specific gaps found and fixed in
// earlier passes (SUCCESSFUL vs EMPTY_OR_PARTIAL wording, exact-Uri-only deletion, never automatic)
// do not need to be independently rediscovered for recommendation-bundle, CSV, or backup exports.
// Each caller supplies its own title/body string pairs so copy can stay feature-appropriate, but the
// dialog structure, the deletion call, and the truthful removed/failed feedback are shared and
// identical everywhere. Deliberately generic: never receives or displays a source/extension/
// repository/manga/path/exception identifier -- only the four string resources and the offer itself.
@Composable
fun SafArtifactCleanupDialog(
    context: Context,
    offer: SafCleanupOffer,
    successTitleRes: StringResource,
    successBodyRes: StringResource,
    incompleteTitleRes: StringResource,
    incompleteBodyRes: StringResource,
    removeRes: StringResource,
    keepRes: StringResource,
    removedRes: StringResource,
    removeFailedRes: StringResource,
    onRemoved: (Uri) -> Unit,
    onKept: () -> Unit,
    onDismissed: () -> Unit,
    // KMK: defaulted to shared generic copy
    // so none of the existing (non-backup) call sites need updating -- SafArtifactOutcome.UNRESOLVED
    // is currently only ever produced by the backup-recovery route, but any future caller gets a
    // truthful fallback for free.
    unresolvedTitleRes: StringResource = KMR.strings.generic_export_cleanup_unresolved_title,
    unresolvedBodyRes: StringResource = KMR.strings.generic_export_cleanup_unresolved_body,
) {
    // KMK: a document is reserved and
    // its writer may still be actively streaming bytes to `offer.uri` while the outcome is
    // `IN_PROGRESS` -- Remove must be structurally unreachable during that window, not merely
    // discouraged by a comment. Rendering nothing at all (rather than a progress dialog with a
    // disabled Remove button) is deliberate: it also means no dialog window exists to intercept the
    // system back button or a screen-navigation-triggered dismiss while a write is in flight.
    // KMK: which branch renders is decided by
    // [safCleanupDialogActionFor], not by this composable re-deriving it inline -- see that function's
    // KDoc for why. NONE covers both `IN_PROGRESS` (a write may still be actively streaming bytes to
    // `offer.uri` -- Remove must be structurally unreachable, and rendering nothing at all, rather than
    // a progress dialog with a disabled Remove button, also means no dialog window exists to intercept
    // the system back button or a screen-navigation-triggered dismiss while a write is in flight) and
    // `SUCCESS` (a successful artifact is never offered for deletion unless the debug-only fixture
    // gate is explicitly enabled by the owning screen model).
    when (safCleanupDialogActionFor(offer.outcome, offer.allowSuccessfulRemoval)) {
        SafCleanupDialogAction.NONE -> return
        SafCleanupDialogAction.UNRESOLVED_ACKNOWLEDGE -> {
            AlertDialog(
                onDismissRequest = onDismissed,
                title = { Text(stringResource(unresolvedTitleRes)) },
                text = { Text(stringResource(unresolvedBodyRes)) },
                confirmButton = {
                    TextButton(onClick = onKept) {
                        Text(stringResource(KMR.strings.generic_export_cleanup_unresolved_acknowledge))
                    }
                },
            )
            return
        }
        SafCleanupDialogAction.REMOVE_OR_KEEP -> Unit
    }
    // Resolved here (a @Composable context), not inside the onClick lambda below, which is not
    // @Composable and cannot call stringResource(...) itself.
    val removedText = stringResource(removedRes)
    val removeFailedText = stringResource(removeFailedRes)
    AlertDialog(
        onDismissRequest = onDismissed,
        title = { Text(stringResource(incompleteTitleRes)) },
        text = { Text(stringResource(incompleteBodyRes)) },
        confirmButton = {
            TextButton(
                onClick = {
                    // KMK: [onRemoved]
                    // (which clears the retained cleanup offer) must only fire on a *successful*
                    // deletion. Calling it unconditionally previously cleared the offer -- and closed
                    // the dialog -- even when `deleteSafDocument` returned `false`, silently losing
                    // the only handle the user had on a document that is still actually orphaned. On
                    // failure the dialog now stays open (offer retained) so the user can see the
                    // truthful failure and retry Remove, or explicitly choose Keep/dismiss instead.
                    val removed = performSafRemoveAction(context, offer.uri, onRemoved)
                    context.toast(if (removed) removedText else removeFailedText)
                },
            ) {
                Text(stringResource(removeRes))
            }
        },
        dismissButton = {
            TextButton(onClick = onKept) {
                Text(stringResource(keepRes))
            }
        },
    )
}
// KMK <--
