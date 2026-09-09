package eu.kanade.tachiyomi.util.export

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

// KMK -->
// Shared SAF created-document lifecycle.
//
// Finding this codifies (already independently discovered and fixed for extension export in
// ExtensionsTab.kt/ExtensionDetailsScreen.kt): `ActivityResultContracts.CreateDocument`'s system
// document picker creates the destination document -- possibly zero bytes -- the moment the user
// confirms a filename/location, *before* any app code runs. A non-null `Uri` returned to a launcher
// callback therefore always means a real, possibly-orphaned document exists, regardless of what the
// write attempt that follows does next: succeeds, writes nothing, fails to open the destination,
// fails mid-stream, or is never even attempted because the input that triggered the export became
// stale/empty by the time the picker returned.
//
// this coordinator was redesigned after an
// independent review found three structural defects in the prior (2026-08-04) version:
//
//  1. `registerUri` set the offer straight to `PARTIAL_OR_EMPTY`, which is a *terminal* outcome as
//     far as every cleanup dialog is concerned -- the dialog rendered, and its Remove button was
//     live, before `performWrite` had even started, let alone finished. A user could press Remove
//     while the writer was actively streaming bytes to that exact `Uri`, deleting the destination
//     out from under an in-flight write. Fixed by introducing [SafArtifactOutcome.IN_PROGRESS] as
//     the true initial state, and by having every cleanup dialog refuse to render Remove/Keep while
//     an offer's outcome is `IN_PROGRESS` (see `SafArtifactCleanupDialog`'s own KDoc).
//  2. The old `registerUri(uri): Boolean` was a single implicit "reservation", made only *after* the
//     picker had already returned a real `Uri` -- if it returned `false` (another offer pending),
//     every production caller ignored the result and wrote anyway, or discarded the picker-created
//     document with no cleanup path at all. Fixed by splitting registration into an explicit
//     two-step contract: [beginOperation] must be called *before* the picker is even launched, and
//     only its returned, non-null `operationId` may be used to [registerUri]/[performWrite]/[clear].
//     A caller that cannot obtain an `operationId` must not launch the picker at all -- see each
//     adapter's own confirm-click handler for the guard.
//  3. Both the old `registerUri` and `BackupCleanupRecoveryStore.register` set a local `Boolean` from
//     *inside* a `MutableStateFlow.update { ... }` lambda. `update`'s lambda is permitted to be
//     invoked more than once under contention (optimistic retry on a failed compare-and-set of the
//     underlying atomic reference) -- a `var` captured and mutated from inside that lambda is not
//     guaranteed to reflect the value that was actually committed if the lambda re-runs. Fixed by
//     making reservation a single `synchronized` critical section with no StateFlow update ambiguity:
//     the lock guards a plain `var`, and the `StateFlow` itself is only ever assigned a fully-formed
//     value once the reservation has unambiguously succeeded.
//
// Every adapter using this coordinator must, in order:
//
//  1. snapshot its own write input (extension, selection, manga list, backup options, ...) at the
//     same user-confirmation boundary as the next step -- this coordinator cannot enforce that part
//     since the input type differs per adapter, but every adapter in this codebase using it does so;
//  2. call [beginOperation] at that same boundary, before launching `CreateDocument`. If it returns
//     `null`, do not launch the picker -- another operation is still pending resolution.
//  3. when the picker returns a non-null `Uri`, call [registerUri] with the reserved `operationId`.
//     If it returns `false` (defensive: the reservation was somehow lost), the picker-created
//     document cannot be tracked by this coordinator -- the caller must still not silently discard
//     it (see each adapter's stale-registration fallback, which attempts a best-effort
//     [deleteSafDocument] and reports the truthful outcome to the user).
//     If the picker returns `null` (user cancelled before any document existed), call
//     [cancelReservation] instead -- there is nothing to clean up.
//  4. run the actual write through [performWrite], which guarantees a `CancellationException` is
//     rethrown (never swallowed, never turned into a false success/failure) and that the retained
//     cleanup offer transitions to [SafArtifactOutcome.CANCELLED] rather than silently disappearing.
enum class SafArtifactOutcome {
    /** A `Uri` is reserved and its writer is running (or about to start); not yet terminal. */
    IN_PROGRESS,

    /** A real, complete artifact was written. */
    SUCCESS,

    /** The destination document exists but contains no usable content, or only part of it. */
    PARTIAL_OR_EMPTY,

    /** A write attempt was made and failed (destination-open failure, mid-stream failure, ...). */
    FAILED,

    /** The write coroutine was cancelled (e.g. the screen was navigated away from) mid-flight. */
    CANCELLED,

    /**
     * the real result could not be
     * determined -- e.g. a durable backup-recovery record survives a process death but WorkManager
     * has since pruned (or never had) any record of the job that may have been writing to it. This is
     * deliberately distinct from [PARTIAL_OR_EMPTY]/[FAILED]/[CANCELLED]: those mean the outcome *is*
     * known (an artifact exists but is incomplete, a write failed, or a write was cancelled).
     * `UNRESOLVED` means the outcome is *not* known -- the destination `Uri` might hold a real,
     * complete artifact, or might not; deleting it would be a guess, never a verified action. Never
     * offered for Remove (see [REMOVABLE_SAF_OUTCOMES]); a cleanup surface may still let the user
     * acknowledge/dismiss the offer, but must never call `DocumentsContract.deleteDocument` for it.
     */
    UNRESOLVED,
}

/**
 * Every [SafArtifactOutcome] that is not actively in progress -- the write (or its recovery
 * classification) has concluded one way or another, including [SafArtifactOutcome.UNRESOLVED] (a
 * concluded-but-uncertain classification). Note this is broader than [REMOVABLE_SAF_OUTCOMES]: e.g.
 * [SafArtifactOutcome.SUCCESS] is terminal but never removable (a successful artifact is never
 * offered for deletion), and [SafArtifactOutcome.UNRESOLVED] is terminal but never removable either.
 */
val TERMINAL_SAF_OUTCOMES: Set<SafArtifactOutcome> = setOf(
    SafArtifactOutcome.SUCCESS,
    SafArtifactOutcome.PARTIAL_OR_EMPTY,
    SafArtifactOutcome.FAILED,
    SafArtifactOutcome.CANCELLED,
    SafArtifactOutcome.UNRESOLVED,
)

/**
 * Every [SafArtifactOutcome] a cleanup dialog is allowed to offer Remove/Keep for -- the outcome is
 * both terminal (see [TERMINAL_SAF_OUTCOMES]) *and* actually known. [SafArtifactOutcome.SUCCESS]
 * (never offered for cleanup at all -- see [SafArtifactCleanupDialog]) and
 * [SafArtifactOutcome.UNRESOLVED] (offered only a non-destructive acknowledge/keep action, never
 * Remove -- deleting a document whose fate is unknown would be a guess, not a verified action) are
 * both deliberately excluded.
 */
val REMOVABLE_SAF_OUTCOMES: Set<SafArtifactOutcome> = setOf(
    SafArtifactOutcome.PARTIAL_OR_EMPTY,
    SafArtifactOutcome.FAILED,
    SafArtifactOutcome.CANCELLED,
)

/**
 * A SAF document the system picker created that may need an explicit Remove/Keep cleanup offer,
 * keyed by [operationId] so a stale callback from a since-cleared or since-superseded operation can
 * never be mistaken for the currently retained one.
 */
data class SafCleanupOffer(
    val operationId: String,
    val uri: Uri,
    val outcome: SafArtifactOutcome,
    /** Debug fixture only: permits explicit deletion of this verified successful artifact. */
    val allowSuccessfulRemoval: Boolean = false,
)

/**
 * Deletes exactly the SAF document identified by [uri] -- the standard `DocumentsContract` document
 * -deletion API, which can only ever affect the single document a `Uri` identifies. Never accepts a
 * filesystem path, filename, or directory, and must never gain such an overload. Never called
 * automatically by this coordinator; every caller must gate this behind an explicit user
 * confirmation (Remove), never behind Keep or a dismissed dialog.
 */
fun deleteSafDocument(context: Context, uri: Uri): Boolean = try {
    DocumentsContract.deleteDocument(context.contentResolver, uri)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    false
}

// [handleUnregisterableUri] used to
// call [deleteSafDocument] and then unconditionally show [deletedMessage], regardless of whether the
// deletion actually succeeded -- a failed defensive-fallback deletion was reported to the user as a
// success, and the document (which the picker really did create) was left permanently untracked with
// no cleanup handle anywhere. [UnregisterableUriOutcome] makes this a typed, truthful result instead:
// a failed deletion is now adopted into the owning coordinator/store's normal offer (so the existing
// Remove/Keep cleanup UI picks it up automatically the next time that coordinator's `cleanupOffer`/
// `offer` StateFlow recomposes) rather than being silently dropped. Adoption can only fail if another
// offer is already retained by that same coordinator -- structurally rare (this fallback itself is
// already documented as "should not happen in normal operation"), but reported truthfully as
// [UnregisterableUriOutcome.UNRECOVERABLE] rather than papered over.
enum class UnregisterableUriOutcome {
    /** [DocumentsContract.deleteDocument] succeeded -- the document is gone, nothing left to track. */
    DELETED,

    /**
     * Deletion failed, but [uri] was adopted into the owning coordinator/store's retained offer --
     * it is now reachable through that route's normal Remove/Keep cleanup dialog for a retry.
     */
    RETAINED_FOR_CLEANUP,

    /**
     * Deletion failed AND [uri] could not be adopted (another offer was already retained by the same
     * coordinator/store). The document is orphaned with no cleanup handle in this run. Reported
     * truthfully rather than silently discarded.
     */
    UNRECOVERABLE,
}

private fun classifyUnregisterableUri(deleted: Boolean, adopted: Boolean): UnregisterableUriOutcome = when {
    deleted -> UnregisterableUriOutcome.DELETED
    adopted -> UnregisterableUriOutcome.RETAINED_FOR_CLEANUP
    else -> UnregisterableUriOutcome.UNRECOVERABLE
}

private fun toastForUnregisterableUri(
    context: Context,
    outcome: UnregisterableUriOutcome,
    deletedMessage: StringResource,
    retainedMessage: StringResource,
    unrecoverableMessage: StringResource,
) {
    val message = when (outcome) {
        UnregisterableUriOutcome.DELETED -> deletedMessage
        UnregisterableUriOutcome.RETAINED_FOR_CLEANUP -> retainedMessage
        UnregisterableUriOutcome.UNRECOVERABLE -> unrecoverableMessage
    }
    context.toast(message)
}

/**
 * Defensive fallback for the case a picker-created [uri] could not be registered against any
 * operation (i.e. [SafExportCoordinator.registerUri] returned `false`) -- this should not happen in
 * normal operation (see [SafExportCoordinator]'s KDoc), but if it does, the document must not simply
 * be discarded. Makes a single best-effort [deleteSafDocument] attempt; if that fails, adopts [uri]
 * into [coordinator] via [SafExportCoordinator.adoptUnregisterableUri] instead of losing it, so the
 * route's own `SafArtifactCleanupDialog` can offer a retry. Reports the truthful outcome via
 * [eu.kanade.tachiyomi.util.system.toast] -- generic and identity-free like every other cleanup
 * message in this app -- and returns it as a typed [UnregisterableUriOutcome] for callers/tests that
 * need to observe it directly. `CancellationException` from [deleteSafDocument] is never caught here
 * and propagates to the caller untouched. Never retried automatically, never silent.
 */
fun handleUnregisterableUri(
    context: Context,
    uri: Uri,
    coordinator: SafExportCoordinator,
    deletedMessage: StringResource,
    retainedMessage: StringResource,
    unrecoverableMessage: StringResource,
): UnregisterableUriOutcome {
    val deleted = deleteSafDocument(context, uri)
    val adopted = !deleted && coordinator.adoptUnregisterableUri(uri)
    val outcome = classifyUnregisterableUri(deleted, adopted)
    toastForUnregisterableUri(context, outcome, deletedMessage, retainedMessage, unrecoverableMessage)
    return outcome
}

/**
 * [handleUnregisterableUri] variant for the backup-creation route, which is owned by
 * [BackupCleanupRecoveryStore] (a plain application-scoped singleton, not a `SafExportCoordinator`
 * instance) rather than a screen-model-owned coordinator -- see that store's own KDoc for why.
 */
fun handleUnregisterableBackupUri(
    context: Context,
    uri: Uri,
    deletedMessage: StringResource,
    retainedMessage: StringResource,
    unrecoverableMessage: StringResource,
): UnregisterableUriOutcome {
    val deleted = deleteSafDocument(context, uri)
    val adopted = !deleted && BackupCleanupRecoveryStore.adoptUnregisterableUri(uri)
    val outcome = classifyUnregisterableUri(deleted, adopted)
    toastForUnregisterableUri(context, outcome, deletedMessage, retainedMessage, unrecoverableMessage)
    return outcome
}

/**
 * Owns the pending-cleanup-offer state for one export/write flow. Intended to be held by a
 * screen model or `remember`ed alongside a screen's `CreateDocument` launcher -- a small, testable
 * coordinator rather than duplicating ad hoc `var uriForCleanup by remember { ... }` state (and its
 * associated retention bugs) in every Composable.
 *
 * At most one operation may be reserved/pending at a time. This is a deliberate, documented
 * simplification (not a multi-operation queue): every route using this coordinator drives its
 * `CreateDocument` picker from a single trigger control that is itself blocked by
 * `SafArtifactCleanupDialog`'s modality once an offer exists (see that dialog's KDoc), so in
 * practice a second reservation attempt while one is pending cannot happen through the UI. The
 * [beginOperation]/[registerUri] split, backed by a single `synchronized` critical section, is the
 * defensive layer for the case that assumption is ever violated (e.g. a future caller, a test, or a
 * platform behavior change): it never corrupts the retained offer and never reports a losing
 * reservation as a winning one.
 *
 * Deliberately bounded, not
 * durable: this coordinator's cleanup offer lives only in a `MutableStateFlow` field, exactly as long
 * as the owning `ScreenModel` (or Composable `remember`) does. **A process death while a write is in
 * flight silently loses that offer** -- there is no on-disk record to reconcile on the next launch,
 * unlike [BackupCleanupRecoveryStore]. This is an intentional, audited scope decision, not an
 * oversight: every route that uses this coordinator (single/bulk extension export, recommendation
 * bundle export, CSV export) runs its write as a `screenModelScope` coroutine that starts and
 * finishes entirely within one foregrounded screen's visible lifetime -- there is no `WorkManager` job
 * or any other mechanism that keeps the write running independently of the app process, unlike backup
 * creation (`BackupCreateJob`, a real `WorkManager` job designed to survive exactly that). A process
 * death during one of these writes needs the OS to kill the process while the app is foregrounded and
 * actively writing a typically-small, single-pass artifact (a JSON/CSV/ZIP bundle) -- a narrow window
 * compared to backup creation, which can run for a long time over a large library and is designed to
 * be started and then left running. If this coordinator's process-death exposure is ever found to
 * matter in practice, the fix is to give it the same durable-record treatment as
 * [BackupCleanupRecoveryStore] (a shared, application-scoped persistence helper, not six duplicated
 * per-route copies) -- not to route each of these six adapters through [BackupCleanupRecoveryStore]
 * itself, which is purpose-built for WorkManager reconciliation these routes have no need for.
 */
class SafExportCoordinator(
    private val allowSuccessfulRemoval: Boolean = false,
) {
    private val lock = Any()
    private var reservedOperationId: String? = null
    private val _cleanupOffer = MutableStateFlow<SafCleanupOffer?>(null)
    val cleanupOffer: StateFlow<SafCleanupOffer?> = _cleanupOffer

    /**
     * Reserves this coordinator for one new operation, before the `CreateDocument` picker is even
     * launched. Returns a fresh opaque `operationId` on success, or `null` if another operation is
     * already reserved or has a retained offer pending resolution -- the caller must not launch the
     * picker in that case. Atomic: guarded by [lock], so two callers racing this call can never both
     * receive a non-null id.
     */
    fun beginOperation(): String? = synchronized(lock) {
        if (reservedOperationId != null || _cleanupOffer.value != null) {
            null
        } else {
            val id = UUID.randomUUID().toString()
            reservedOperationId = id
            id
        }
    }

    /**
     * Releases a reservation obtained from [beginOperation] without ever creating an offer -- call
     * this when the picker returns `null` (user cancelled before any document existed). A no-op if
     * [operationId] is not the currently reserved id (e.g. a stale callback after [registerUri] or a
     * second [beginOperation] already ran) -- never releases someone else's reservation.
     */
    fun cancelReservation(operationId: String) = synchronized(lock) {
        if (reservedOperationId == operationId) {
            reservedOperationId = null
        }
    }

    /**
     * Converts a reservation from [beginOperation] into a real, non-null picker `Uri`, before any
     * write is attempted. The offer starts as [SafArtifactOutcome.IN_PROGRESS] -- deliberately not
     * terminal, so no cleanup dialog offers Remove/Keep until [performWrite] reaches a real outcome.
     * Returns `false` (defensive; should not happen in normal operation) if [operationId] is not the
     * currently reserved id -- the caller must not proceed to write in that case, and must not
     * silently discard [uri] (see this class's own KDoc for the required fallback).
     */
    fun registerUri(operationId: String, uri: Uri): Boolean = synchronized(lock) {
        if (reservedOperationId != operationId) {
            false
        } else {
            reservedOperationId = null
            _cleanupOffer.value = SafCleanupOffer(
                operationId = operationId,
                uri = uri,
                outcome = SafArtifactOutcome.IN_PROGRESS,
                allowSuccessfulRemoval = allowSuccessfulRemoval,
            )
            true
        }
    }

    /**
     * Runs [write] (which must already be scoped to the registered `Uri` and returns the outcome it
     * observed) and updates the retained offer to match, transitioning it out of `IN_PROGRESS` into a
     * terminal [SafArtifactOutcome]. If [write] throws `CancellationException`, the offer is
     * downgraded to [SafArtifactOutcome.CANCELLED] and the exception is rethrown -- never swallowed,
     * never reported as an ordinary outcome. If [write] throws any other exception -- a caller bug,
     * or an exception thrown before that caller's own try/catch ever converts it to
     * [SafArtifactOutcome.FAILED] -- this is defense-in-depth: the offer is still marked `FAILED`
     * (never silently left at `IN_PROGRESS`) and `FAILED` is returned rather than letting the
     * exception propagate uncaught into the caller's coroutine. [operationId] must match the
     * currently registered offer's id or the update is silently skipped (defensive against a stale
     * callback from an operation that was already cleared or superseded).
     */
    suspend fun performWrite(operationId: String, write: suspend () -> SafArtifactOutcome): SafArtifactOutcome {
        return try {
            val outcome = write()
            updateOfferIfMatching(operationId, outcome)
            outcome
        } catch (e: CancellationException) {
            updateOfferIfMatching(operationId, SafArtifactOutcome.CANCELLED)
            throw e
        } catch (e: Exception) {
            updateOfferIfMatching(operationId, SafArtifactOutcome.FAILED)
            SafArtifactOutcome.FAILED
        }
    }

    private fun updateOfferIfMatching(operationId: String, outcome: SafArtifactOutcome) = synchronized(lock) {
        val current = _cleanupOffer.value
        if (current?.operationId == operationId) {
            _cleanupOffer.value = current.copy(outcome = outcome)
        }
    }

    /**
     * Clears the retained offer -- called after Remove, Keep, or dismiss. Only clears if
     * [operationId] matches the currently retained offer, so a stale call (e.g. a delayed callback
     * from an operation already cleared, or from before a second operation began) can never clear an
     * unrelated one.
     */
    fun clear(operationId: String) = synchronized(lock) {
        val current = _cleanupOffer.value
        if (current?.operationId == operationId) {
            _cleanupOffer.value = null
        }
    }

    /**
     * Force-adopts a picker-created [uri] that could not go through the normal
     * [beginOperation]/[registerUri] reservation (i.e. [registerUri] returned `false`) into a fresh,
     * already-terminal offer -- used only by [handleUnregisterableUri]. Since [registerUri] is never
     * even reached in this path, no write was ever attempted against [uri]; it is safe to mark the
     * offer terminal ([SafArtifactOutcome.PARTIAL_OR_EMPTY]) immediately rather than `IN_PROGRESS`.
     * Returns `false` without changing any state if another offer is already retained -- this
     * coordinator's single-offer design cannot represent two concurrent unresolved offers, so the
     * caller must fall back to reporting the document as unrecoverable in that (structurally rare)
     * case rather than silently overwriting the existing offer. Also refuses to adopt while a
     * reservation is outstanding ([beginOperation] succeeded but [registerUri]/[cancelReservation]
     * has not yet resolved it) -- adopting in that window could otherwise be silently clobbered the
     * moment that reservation's own (stale) [registerUri] call finally runs, since [registerUri]
     * unconditionally overwrites `_cleanupOffer` once its `operationId` matches.
     */
    fun adoptUnregisterableUri(uri: Uri): Boolean = synchronized(lock) {
        if (_cleanupOffer.value != null || reservedOperationId != null) {
            false
        } else {
            val id = UUID.randomUUID().toString()
            _cleanupOffer.value = SafCleanupOffer(id, uri, SafArtifactOutcome.PARTIAL_OR_EMPTY)
            true
        }
    }
}
// KMK <--
