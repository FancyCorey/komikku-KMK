package eu.kanade.tachiyomi.util.export

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.work.WorkInfo
import eu.kanade.tachiyomi.data.backup.create.BackupCreateJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.UUID

// KMK -->
// KMK_CLAUDE_FINAL_SAF_ACTION_HISTORY_RECONCILIATION_PLAN_2026-08-04 Phase 3: application-scoped SAF
// cleanup recovery store for the backup-creation route.
//
// Every other `CreateDocument` writer in this app is fully driven by a UI action that starts and
// finishes within one screen's visible lifetime -- the write itself is a `screenModelScope`
// coroutine, so a screen-scoped `SafExportCoordinator` (destroyed when the owning ScreenModel is
// disposed, i.e. when the screen is popped) is the correct owner: navigating away legitimately
// cancels that write, and `SafExportCoordinator.performWrite` correctly reports that as `CANCELLED`.
//
// Backup creation is different: `BackupCreateJob.startNow` enqueues a real `WorkManager` job that is
// designed to keep running independently of whether the user stays on `CreateBackupScreen`, and can
// outlive the app *process*, not only the screen. This store closes that gap in two layers:
//
//  1. In-memory: offer ownership lives in application scope (a plain singleton object, not tied to
//     any Activity/Screen/ScreenModel), keyed by an opaque `operationId` -- same atomic
//     beginOperation/registerUri/performWrite/clear contract as `SafExportCoordinator`
//     (KMK_CLAUDE_SAF_EXPORT_LIFECYCLE_CORRECTIONS_2026-08-05), guarded by a single `synchronized`
//     critical section so a reservation race can never report two winners or silently lose one.
//  2. Durable (Phase 4, 2026-08-05): every state transition is mirrored into a single JSON record in
//     [PreferenceStore] under an app-state key (never exposed in backups/exports, never a user
//     preference). On process recreation, [reconcileOnStartup] reloads that record, and if it names a
//     WorkManager request id, re-polls that job to a terminal state via
//     [BackupCreateJob.awaitManualJobTerminalState] -- exactly the same helper the original
//     in-process flow uses -- so a backup that finishes (or fails, or is cancelled) while no app
//     process is alive to observe it directly still produces a truthful, recoverable cleanup offer
//     once the app is reopened.
//
// Deliberately minimal, matching every other cleanup surface in this app: the durable record retains
// only an opaque operation id, the SAF `Uri` (as a string), a literal operation-type tag, the
// WorkManager request id, the outcome name, a schema version, and a creation timestamp -- never
// backup contents, file paths beyond the SAF `Uri` itself, account identifiers, or exception text.
// [MainActivity] renders the shared [eu.kanade.presentation.components.SafArtifactCleanupDialog]
// driven by [offer] at the application-composition root (alongside its other always-reachable
// dialogs, e.g. the changelog dialog), so the offer stays visible and actionable regardless of which
// screen is currently active, whether `CreateBackupScreen` itself has been popped, or whether the app
// process was recreated since the backup was started.
object BackupCleanupRecoveryStore {
    data class Entry(val operationId: String, val uri: Uri, val outcome: SafArtifactOutcome)

    // KMK_CLAUDE_SAF_BACKUP_RECOVERY_CORRECTIVE_PASS_2026-08-07: durable proof of whether
    // `BackupCreateJob.startNow` was ever actually called for an operation -- see [markEnqueueAttempted]
    // and [reconcileOnStartup]. Without this, "no matching WorkManager job found" was ambiguous
    // between "nothing was ever enqueued, safe to treat as an empty/incomplete document" and "a real
    // job was enqueued and may still be writing, or finished and was pruned" -- the former is safe to
    // classify immediately as [SafArtifactOutcome.PARTIAL_OR_EMPTY]; the latter must never be silently
    // treated the same way (see [SafArtifactOutcome.UNRESOLVED]).
    enum class BackupEnqueueState {
        /** `startNow` has definitely not been called yet for this operation. */
        NOT_ATTEMPTED,

        /** `startNow` was called (successfully returned an id) for this operation. */
        ATTEMPTED,

        /**
         * This record predates enqueue-state tracking (schema version < [CURRENT_RECORD_VERSION]), or
         * its enqueue-state field could not be parsed. Never treated as [NOT_ATTEMPTED] -- a legacy
         * record may well have reached `startNow` before this field existed to record it.
         */
        UNKNOWN,
    }

    /**
     * Durable, privacy-minimal recovery record. [version] allows the on-disk schema to evolve without
     * crashing on an old record -- [loadRecord] discards (rather than crashes on) a record from a
     * *newer* schema than this build recognizes (never trusted to drive a deletion decision), and
     * conservatively migrates a record from an *older* recognized schema (currently: version 1, which
     * predates [enqueueStateName] -- see [BackupEnqueueState.UNKNOWN]).
     */
    @Serializable
    data class BackupCleanupRecord(
        val version: Int = CURRENT_RECORD_VERSION,
        val operationId: String,
        val uriString: String,
        val operationType: String = OPERATION_TYPE_BACKUP_CREATE,
        val workRequestId: String? = null,
        val outcomeName: String,
        // Deliberately defaults to UNKNOWN, never NOT_ATTEMPTED -- every production call site that
        // actually knows a record is brand new (registerUri/adoptUnregisterableUri) sets this
        // explicitly; any code path that omits it (or a legacy decode) must fail safe to "we don't
        // know", never to a value that would let a real job's document be misclassified as never
        // written.
        val enqueueStateName: String = BackupEnqueueState.UNKNOWN.name,
        val createdAtEpochMillis: Long,
    )

    const val CURRENT_RECORD_VERSION = 2
    private const val MIN_SUPPORTED_RECORD_VERSION = 1
    const val OPERATION_TYPE_BACKUP_CREATE = "backup_create"
    private const val RECORD_PREFERENCE_KEY = "backup_cleanup_recovery_record"

    private val lock = Any()
    private var reservedOperationId: String? = null
    private val _offer = MutableStateFlow<Entry?>(null)
    val offer: StateFlow<Entry?> = _offer

    /** A fresh, unguessable id for one backup-creation attempt; pass the same id to every call below. */
    fun newOperationId(): String = UUID.randomUUID().toString()

    private fun recordPreference(): Preference<String> {
        val preferenceStore = Injekt.get<PreferenceStore>()
        return preferenceStore.getString(Preference.appStateKey(RECORD_PREFERENCE_KEY), "")
    }

    // KMK_CLAUDE_SAF_BACKUP_RECOVERY_CORRECTIVE_PASS_2026-08-07: a failed on-disk write must never
    // crash the caller (registerUri/markEnqueueAttempted/attachWorkRequest/etc. all run inside a
    // `synchronized` critical section that also drives in-memory state) -- if persistence itself
    // fails, the safest outcome is "the durable record is whatever it was before this call" (the
    // previous on-disk value, untouched), not a half-written record and not a crash. `CancellationException`
    // is rethrown, never swallowed as an ordinary failure.
    private fun persist(record: BackupCleanupRecord?): Boolean {
        try {
            val pref = recordPreference()
            if (record == null) {
                pref.delete()
            } else {
                pref.set(Json.encodeToString(BackupCleanupRecord.serializer(), record))
            }
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN) { "BackupCleanupRecoveryStore: failed to persist recovery record" }
            return false
        }
    }

    // KMK_CLAUDE_SAF_EXPORT_LIFECYCLE_CORRECTIONS_2026-08-06 Finding 3: [loadRecord] previously
    // validated only JSON syntax and the schema [BackupCleanupRecord.version] -- it trusted
    // [BackupCleanupRecord.operationType] and [BackupCleanupRecord.uriString] at face value. Since
    // this record drives a real deletion decision once restored into [offer], every field that
    // decision depends on is now structurally validated before the record is ever returned: an
    // unrecognized [operationType] is rejected (only [OPERATION_TYPE_BACKUP_CREATE] is accepted
    // today), [operationId] must be a well-formed UUID (the only format [newOperationId] ever
    // produces), and [uriString] must parse as a `content://` SAF *document* URI (either
    // `content://authority/document/...` or the tree-document form
    // `content://authority/tree/.../document/...` that `ActivityResultContracts.CreateDocument`
    // actually returns) -- never a `file://` URI, a non-document `content://` URI (e.g. a raw media
    // provider row), or an empty/malformed value. None of these checks ever log the value being
    // rejected (no raw URI, path, or id in any log line here) -- only that a record was discarded and
    // why, matching every other privacy-safe log line in this store.
    private fun loadRecord(): BackupCleanupRecord? {
        val raw = try {
            recordPreference().get()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN) { "BackupCleanupRecoveryStore: failed to read recovery record" }
            return null
        }
        if (raw.isBlank()) return null
        val record = try {
            Json.decodeFromString(BackupCleanupRecord.serializer(), raw)
        } catch (e: Exception) {
            // A corrupted/unparseable record must never be trusted to drive a deletion decision --
            // discard it silently (never crash startup over stale recovery metadata) rather than
            // guess at its contents.
            logcat(LogPriority.WARN) { "BackupCleanupRecoveryStore: discarding unparseable record" }
            return null
        }
        // KMK_CLAUDE_SAF_BACKUP_RECOVERY_CORRECTIVE_PASS_2026-08-07: a record from a *newer* schema
        // than this build understands is never trusted (unchanged from before) -- but a record from
        // an *older*, still-recognized schema (currently: version 1, which predates
        // [BackupEnqueueState] tracking) is migrated conservatively rather than discarded outright,
        // since discarding it would also silently lose the only durable trace of a backup that may
        // still be in flight.
        if (record.version !in MIN_SUPPORTED_RECORD_VERSION..CURRENT_RECORD_VERSION) {
            logcat(LogPriority.WARN) { "BackupCleanupRecoveryStore: discarding record with unsupported version ${record.version}" }
            return null
        }
        if (record.operationType != OPERATION_TYPE_BACKUP_CREATE) {
            logcat(LogPriority.WARN) { "BackupCleanupRecoveryStore: discarding record with an unrecognized operationType" }
            return null
        }
        if (!isValidOperationId(record.operationId)) {
            logcat(LogPriority.WARN) { "BackupCleanupRecoveryStore: discarding record with an invalid operationId" }
            return null
        }
        if (!isValidSafDocumentUriString(record.uriString)) {
            logcat(LogPriority.WARN) { "BackupCleanupRecoveryStore: discarding record with an invalid document uri" }
            return null
        }
        // A record older than the current schema never recorded enqueue-state at all -- force it to
        // UNKNOWN regardless of what the (nonexistent, therefore class-default) field would otherwise
        // decode to, and upgrade its version so every subsequent `persist(record.copy(...))` call
        // naturally re-writes it at the current schema (self-healing on next mutation).
        return if (record.version < CURRENT_RECORD_VERSION) {
            record.copy(version = CURRENT_RECORD_VERSION, enqueueStateName = BackupEnqueueState.UNKNOWN.name)
        } else {
            record
        }
    }

    private fun resolvedEnqueueState(record: BackupCleanupRecord): BackupEnqueueState = try {
        BackupEnqueueState.valueOf(record.enqueueStateName)
    } catch (e: IllegalArgumentException) {
        BackupEnqueueState.UNKNOWN
    }

    private fun isValidOperationId(operationId: String): Boolean {
        if (operationId.isBlank()) return false
        return try {
            UUID.fromString(operationId)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    private fun isValidSafDocumentUriString(uriString: String): Boolean {
        if (uriString.isBlank()) return false
        val uri = try {
            uriString.toUri()
        } catch (e: Exception) {
            return false
        }
        return isValidSafDocumentUri(uri)
    }

    /**
     * A `content://` SAF *document* URI, matching either the plain document form
     * (`content://authority/document/<id>`) or the tree-document form
     * (`content://authority/tree/<tree-id>/document/<id>`) that
     * `ActivityResultContracts.CreateDocument` actually returns -- deliberately narrower than "any
     * `content://` URI" (which would also accept, say, a raw `content://media/...` row that was never
     * a document this store could safely hand to `DocumentsContract.deleteDocument`).
     */
    private fun isValidSafDocumentUri(uri: Uri): Boolean {
        if (!"content".equals(uri.scheme, ignoreCase = true)) return false
        if (uri.authority.isNullOrBlank()) return false
        val segments = uri.pathSegments
        return when {
            segments.size >= 2 && segments[0] == "document" && segments[1].isNotBlank() -> true
            segments.size >= 4 && segments[0] == "tree" && segments[2] == "document" && segments[3].isNotBlank() -> true
            else -> false
        }
    }

    /**
     * Reserves this store for one new backup-creation operation, before the `CreateDocument` picker
     * is even launched. Returns a fresh opaque `operationId` on success, or `null` if another
     * operation is already reserved or has a retained offer pending resolution. Atomic: guarded by
     * [lock].
     */
    fun beginOperation(): String? = synchronized(lock) {
        if (reservedOperationId != null || _offer.value != null) {
            null
        } else {
            val id = newOperationId()
            reservedOperationId = id
            id
        }
    }

    /** Releases a reservation from [beginOperation] without creating an offer (picker returned null). */
    fun cancelReservation(operationId: String) = synchronized(lock) {
        if (reservedOperationId == operationId) {
            reservedOperationId = null
        }
    }

    /**
     * Converts a reservation into a real, non-null picker `Uri` for [operationId] -- same contract as
     * [SafExportCoordinator.registerUri]. The offer starts as [SafArtifactOutcome.IN_PROGRESS], and
     * is immediately mirrored into the durable record (with no `workRequestId` yet -- that is added
     * once the WorkManager job is actually enqueued, via [attachWorkRequest]).
     */
    fun registerUri(operationId: String, uri: Uri): Boolean = synchronized(lock) {
        if (reservedOperationId != operationId) {
            false
        } else {
            val entry = Entry(operationId, uri, SafArtifactOutcome.IN_PROGRESS)
            val persisted = persist(
                BackupCleanupRecord(
                    operationId = operationId,
                    uriString = uri.toString(),
                    outcomeName = SafArtifactOutcome.IN_PROGRESS.name,
                    // A genuinely brand-new record -- explicit, never left to the class default, so
                    // this is the one and only place a record is ever durably marked NOT_ATTEMPTED.
                    enqueueStateName = BackupEnqueueState.NOT_ATTEMPTED.name,
                    createdAtEpochMillis = System.currentTimeMillis(),
                ),
            )
            if (persisted) {
                reservedOperationId = null
                _offer.value = entry
                true
            } else {
                // The picker-created document is deliberately left to the caller's existing
                // unregisterable-URI fallback. No backup write may begin without a durable record.
                reservedOperationId = null
                false
            }
        }
    }

    /**
     * Durably records that [BackupCreateJob.startNow] is *about to be* called for [operationId] --
     * must be called immediately *before* that call, not after (see [attachWorkRequest] for the
     * "after" half: the returned request id). This is what lets [reconcileOnStartup] distinguish "no
     * matching WorkManager job because none was ever attempted" (safe to classify immediately, see
     * [BackupEnqueueState.NOT_ATTEMPTED]) from "no matching job found, but one really was attempted"
     * (never safe to assume -- see [SafArtifactOutcome.UNRESOLVED]) after a process death that could
     * have happened at any point relative to the real `startNow` call. A no-op if [operationId] no
     * longer matches the retained offer.
     */
    fun markEnqueueAttempted(operationId: String): Boolean = synchronized(lock) {
        val current = _offer.value
        if (current?.operationId == operationId) {
            val record = loadRecord()
            if (record != null && record.operationId == operationId) {
                return@synchronized persist(record.copy(enqueueStateName = BackupEnqueueState.ATTEMPTED.name))
            }
        }
        false
    }

    /**
     * Records the WorkManager request id backing [operationId]'s write, so [reconcileOnStartup] can
     * re-poll the correct job after a process recreation. A no-op if [operationId] no longer matches
     * the retained offer. [record.copy] preserves the enqueue state [markEnqueueAttempted] already set
     * -- this call never regresses it.
     */
    fun attachWorkRequest(operationId: String, workRequestId: UUID): Boolean = synchronized(lock) {
        val current = _offer.value
        if (current?.operationId == operationId) {
            val record = loadRecord()
            if (record != null && record.operationId == operationId) {
                return@synchronized persist(record.copy(workRequestId = workRequestId.toString()))
            }
        }
        false
    }

    /**
     * Runs [write] and updates [operationId]'s retained offer (and durable record) to match -- same
     * cancellation/exception contract as [SafExportCoordinator.performWrite].
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
        val current = _offer.value
        if (current?.operationId == operationId) {
            _offer.value = current.copy(outcome = outcome)
            if (outcome == SafArtifactOutcome.SUCCESS) {
                // A successful backup is never offered for cleanup and never presented as
                // reversible -- clear the durable record immediately rather than waiting for a user
                // decision that will never be requested for this outcome.
                persist(null)
            } else {
                val record = loadRecord()
                if (record != null && record.operationId == operationId) {
                    persist(record.copy(outcomeName = outcome.name))
                }
            }
        }
    }

    /**
     * Force-adopts a picker-created [uri] that could not go through the normal
     * [beginOperation]/[registerUri] reservation (i.e. [registerUri] returned `false`) into a fresh,
     * already-terminal, durably-persisted offer -- used only by
     * [eu.kanade.tachiyomi.util.export.handleUnregisterableBackupUri]. Since [registerUri] is never
     * even reached in this path, no write was ever attempted against [uri]; it is safe to mark the
     * offer terminal ([SafArtifactOutcome.PARTIAL_OR_EMPTY]) immediately rather than `IN_PROGRESS`.
     * Returns `false` without changing any state if another offer or reservation is already retained
     * -- this store's single-offer design cannot represent two concurrent unresolved offers, so the
     * caller must fall back to reporting the document as unrecoverable in that (structurally rare)
     * case rather than silently overwriting the existing offer/record.
     */
    fun adoptUnregisterableUri(uri: Uri): Boolean = synchronized(lock) {
        if (_offer.value != null || reservedOperationId != null) {
            false
        } else {
            val id = newOperationId()
            val persisted = persist(
                BackupCleanupRecord(
                    operationId = id,
                    uriString = uri.toString(),
                    outcomeName = SafArtifactOutcome.PARTIAL_OR_EMPTY.name,
                    // No write was ever attempted against this uri via this store's normal flow --
                    // registerUri is never reached in this path (see the KDoc above).
                    enqueueStateName = BackupEnqueueState.NOT_ATTEMPTED.name,
                    createdAtEpochMillis = System.currentTimeMillis(),
                ),
            )
            if (persisted) {
                _offer.value = Entry(id, uri, SafArtifactOutcome.PARTIAL_OR_EMPTY)
                true
            } else {
                false
            }
        }
    }

    /** Clears [operationId]'s retained offer and durable record -- called after Remove, Keep, or dismiss. */
    fun clear(operationId: String) = synchronized(lock) {
        val current = _offer.value
        if (current?.operationId == operationId) {
            _offer.value = null
            val record = loadRecord()
            if (record != null && record.operationId == operationId) {
                persist(null)
            }
        }
    }

    /**
     * Maps a resolved WorkManager terminal state to the outcome the *reconciliation* path (this file)
     * should report -- deliberately distinct from [eu.kanade.presentation.more.settings.screen.data.backupJobOutcomeFor],
     * which serves only the live same-session write path where a `null`/non-finished state genuinely
     * does mean "never started" or "still running". Here, a `null` state coming out of
     * [BackupCreateJob.awaitManualJobTerminalState] means WorkManager has no record of the job at all
     * (never had one, or it was pruned after finishing) -- after a process restart that is NOT proof
     * the job never ran, so it must map to [SafArtifactOutcome.UNRESOLVED], never
     * [SafArtifactOutcome.PARTIAL_OR_EMPTY].
     */
    private fun reconciledOutcomeFor(state: WorkInfo.State?): SafArtifactOutcome = when (state) {
        WorkInfo.State.SUCCEEDED -> SafArtifactOutcome.SUCCESS
        WorkInfo.State.FAILED -> SafArtifactOutcome.FAILED
        WorkInfo.State.CANCELLED -> SafArtifactOutcome.CANCELLED
        else -> SafArtifactOutcome.UNRESOLVED
    }

    /**
     * Resolves the real outcome for a persisted `IN_PROGRESS` [record], implementing the full
     * enqueue-state/WorkManager reconciliation matrix. Never uses "no WorkManager record" as proof
     * that no backup was ever enqueued unless [record]'s durable enqueue state proves
     * [BackupEnqueueState.NOT_ATTEMPTED] -- every other case that cannot find a live/finished job
     * resolves to [SafArtifactOutcome.UNRESOLVED], never [SafArtifactOutcome.PARTIAL_OR_EMPTY].
     * [kotlinx.coroutines.CancellationException] always propagates uncaught so a cancelled
     * reconciliation never mutates the durable record.
     */
    private suspend fun resolveInProgressRecord(context: Context, record: BackupCleanupRecord, uri: Uri): SafArtifactOutcome {
        val enqueueState = resolvedEnqueueState(record)
        if (enqueueState == BackupEnqueueState.NOT_ATTEMPTED) {
            // Case A: registerUri's own record, never attempted -- no backup job could have written
            // to `uri`, so it is safe to classify immediately without consulting WorkManager at all.
            return SafArtifactOutcome.PARTIAL_OR_EMPTY
        }

        // enqueueState is ATTEMPTED or UNKNOWN (legacy) from here on -- a real write may genuinely
        // have been attempted, so an absent WorkManager record is never treated as "never enqueued".
        val persistedRequestId = record.workRequestId?.let {
            try {
                UUID.fromString(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
        val requestId = if (persistedRequestId != null) {
            persistedRequestId
        } else {
            try {
                BackupCreateJob.findManualJobIdForUri(context, uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN) { "BackupCleanupRecoveryStore: WorkManager job lookup failed during startup reconciliation" }
                return SafArtifactOutcome.UNRESOLVED
            }
        }
        if (requestId == null) {
            // Cases E/F: attempted (or unknown-legacy) but no matching job can be found -- may simply
            // have been pruned, never proof the write never happened.
            return SafArtifactOutcome.UNRESOLVED
        }

        val state = try {
            BackupCreateJob.awaitManualJobTerminalState(context, requestId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN) { "BackupCleanupRecoveryStore: WorkManager terminal-state poll failed during startup reconciliation" }
            return SafArtifactOutcome.UNRESOLVED
        }
        // Case B (an active job) never reaches here: awaitManualJobTerminalState suspends until the
        // job is genuinely finished (or WorkManager has no record), so this coroutine simply hasn't
        // returned yet while a matching job may still be writing -- no offer is restored meanwhile.
        // Cases C/D/G are all covered by reconciledOutcomeFor.
        return reconciledOutcomeFor(state)
    }

    /**
     * Reloads any durable recovery record and reconciles it against current WorkManager state,
     * restoring the in-memory [offer] so the root-level cleanup UI can act on it. Call once, from
     * application startup, in [androidx.lifecycle.ProcessLifecycleOwner]'s process-scoped
     * `lifecycleScope` -- never blocks the calling thread. Safe to call when there is nothing to
     * reconcile (no-op). Never restores an offer for a record whose outcome was already `SUCCESS`
     * (that path clears the record synchronously instead) or whose schema [BackupCleanupRecord.version]
     * this build does not recognize.
     *
     * [kotlinx.coroutines.CancellationException] is always rethrown uncaught (case I) so a cancelled
     * reconciliation coroutine never clears or overwrites the durable record. Any other ordinary
     * exception raised while consulting WorkManager (case H) is caught here as a last-resort boundary,
     * on top of the narrower boundaries already inside [resolveInProgressRecord], and conservatively
     * resolves to [SafArtifactOutcome.UNRESOLVED] without touching the durable record -- logs stay
     * generic (no uri/path/account/exception-message content).
     */
    suspend fun reconcileOnStartup(context: Context) {
        val record = synchronized(lock) {
            if (_offer.value != null || reservedOperationId != null) return
            loadRecord()
        } ?: return

        val outcome = try {
            SafArtifactOutcome.valueOf(record.outcomeName)
        } catch (e: IllegalArgumentException) {
            logcat(LogPriority.WARN) { "BackupCleanupRecoveryStore: discarding record with unknown outcome" }
            persist(null)
            return
        }

        val uri = try {
            record.uriString.toUri()
        } catch (e: Exception) {
            persist(null)
            return
        }

        val resolvedOutcome = if (outcome != SafArtifactOutcome.IN_PROGRESS) {
            outcome
        } else {
            try {
                resolveInProgressRecord(context, record, uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN) { "BackupCleanupRecoveryStore: startup reconciliation failed unexpectedly" }
                SafArtifactOutcome.UNRESOLVED
            }
        }

        synchronized(lock) {
            if (_offer.value != null || reservedOperationId != null) return
            if (resolvedOutcome == SafArtifactOutcome.SUCCESS) {
                persist(null)
            } else {
                _offer.value = Entry(record.operationId, uri, resolvedOutcome)
                persist(record.copy(outcomeName = resolvedOutcome.name))
            }
        }
    }

    /**
     * Force-clears any retained offer and durable record regardless of operation id. Test-only, never
     * called from production code (production always resolves a specific operationId through
     * [clear]).
     */
    internal fun resetForTesting() {
        synchronized(lock) {
            reservedOperationId = null
            _offer.value = null
        }
        persist(null)
    }
}
// KMK <--
