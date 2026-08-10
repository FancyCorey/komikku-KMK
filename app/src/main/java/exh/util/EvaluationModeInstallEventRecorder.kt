package exh.util

import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Records a successful user-requested extension install (or update) at the flow boundary.
 *
 * The recorder is opt-in: only callers for a user-facing install/update action wrap their flow. The
 * terminal event is recorded before it is emitted downstream so a consumer using `takeWhile`
 * cannot cancel collection before the event is captured. A local guard makes one flow produce at
 * most one event even if its installer state re-emits [InstallStep.Installed].
 *
 * KMK Confirmed Blocker Remediation Corrective Completion Plan V2 2026-07-29: [eventType] defaults
 * to [NonUndoableEventType.EXTENSION_INSTALLED] for a fresh install, but
 * `ExtensionsScreenModel.updateExtension()` passes [NonUndoableEventType.EXTENSION_UPDATED] --
 * previously every update was misrecorded as an install because this function only ever wrote
 * `EXTENSION_INSTALLED`.
 */
fun Flow<InstallStep>.recordUserInitiatedInstall(
    eventType: NonUndoableEventType = NonUndoableEventType.EXTENSION_INSTALLED,
    // KMK Confirmed Blocker Remediation Corrective Completion Plan V2 2026-07-29: shared with
    // recordPackageOperationReceipt() by callers that chain both operators on the same flow, so the
    // resulting NonUndoableEvent and PackageOperationReceipt carry the *same* id -- that shared id is
    // how EvaluationModeActionHistoryScreen correlates a rendered event row back to its private
    // follow-up-eligibility metadata (see ActionHistoryRegistry's NonUndoableHistorySource). Defaults
    // to a fresh id so a caller using only this operator (no receipt) is unaffected.
    id: String = NonUndoableEvent.newId(),
    isEvaluationModeEnabled: () -> Boolean,
): Flow<InstallStep> = flow {
    var recorded = false
    this@recordUserInitiatedInstall.collect { step ->
        if (step == InstallStep.Installed && !recorded) {
            recorded = true
            if (isEvaluationModeEnabled()) {
                NonUndoableEventJournal.record(
                    NonUndoableEvent(
                        id = id,
                        timestamp = System.currentTimeMillis(),
                        eventType = eventType,
                    ),
                )
            }
        }
        emit(step)
    }
}

// KMK Confirmed Blocker Remediation Corrective Completion Plan V2 2026-07-29 -->
/**
 * Records a [PackageOperationReceipt] the moment an install/update flow reaches
 * [InstallStep.Installed] -- the same success-only, dedup-guarded boundary
 * [recordUserInitiatedInstall] uses for the visibility-only [NonUndoableEvent]. Kept as a separate
 * operator (rather than folded into [recordUserInitiatedInstall]) since it needs the package's
 * identity/signature/version/artifact fields, which [recordUserInitiatedInstall] has no reason to
 * know about for its own, narrower purpose.
 */
fun Flow<InstallStep>.recordPackageOperationReceipt(
    kind: PackageOperationKind,
    packageName: String,
    signatureHash: String?,
    versionCode: Long?,
    artifactUri: String?,
    // KMK Confirmed Blocker Remediation Corrective Completion Plan V2 2026-07-29: see the matching
    // doc on recordUserInitiatedInstall()'s [id] parameter -- pass the same id to both operators so
    // the resulting event and receipt correlate.
    id: String = PackageOperationReceipt.newId(),
    isEvaluationModeEnabled: () -> Boolean,
): Flow<InstallStep> = flow {
    var recorded = false
    this@recordPackageOperationReceipt.collect { step ->
        if (step == InstallStep.Installed && !recorded) {
            recorded = true
            if (isEvaluationModeEnabled()) {
                PackageOperationJournal.record(
                    PackageOperationReceipt(
                        id = id,
                        timestamp = System.currentTimeMillis(),
                        kind = kind,
                        packageName = packageName,
                        signatureHash = signatureHash,
                        versionCode = versionCode,
                        artifactUri = artifactUri,
                    ),
                )
            }
        }
        emit(step)
    }
}
// KMK <--

// KMK Code-Only Completion Plan 2026-07-31 -->
/**
 * The single, shared "install a source and record it" contract every user-initiated fresh-install
 * call site in the app must use -- extracted after
 * `SourceEvaluationScreenModel.reinstallRuntimeHealthExtension()` was found bypassing it entirely
 * (see that function's own doc for the exact defect). Chains [recordUserInitiatedInstall] and
 * [recordPackageOperationReceipt] with one shared id so the resulting event and receipt correlate,
 * then awaits the first terminal step. Records nothing on [InstallStep.Error] or on cancellation;
 * records exactly once even if the underlying installer flow re-emits [InstallStep.Installed] (see
 * both operators' own dedup guard). Callers that need a distinct [NonUndoableEventType] (e.g. an
 * *update* rather than a fresh install) should keep using the two operators directly, as
 * `ExtensionsScreenModel.updateExtension()` already does -- this helper is deliberately scoped to
 * the fresh-install case shared by `SourceEvaluationScreenModel.installEvaluatedSource()` and
 * `SourceEvaluationScreenModel.reinstallRuntimeHealthExtension()`.
 */
suspend fun ExtensionManager.installAndRecordUserInitiated(
    extension: Extension.Available,
    isEvaluationModeEnabled: () -> Boolean,
): InstallStep {
    val receiptId = NonUndoableEvent.newId()
    return installExtension(extension)
        .recordUserInitiatedInstall(id = receiptId, isEvaluationModeEnabled = isEvaluationModeEnabled)
        .recordPackageOperationReceipt(
            kind = PackageOperationKind.INSTALL,
            packageName = extension.pkgName,
            signatureHash = extension.signatureHash,
            versionCode = extension.versionCode,
            artifactUri = extension.apkUrl,
            id = receiptId,
            isEvaluationModeEnabled = isEvaluationModeEnabled,
        )
        .first { it == InstallStep.Installed || it == InstallStep.Error }
}
// KMK <--

// KMK Confirmed Blocker Remediation Phase 5 2026-07-29 -->
/**
 * Verifies a requested uninstall actually completed before recording a non-undoable Action History
 * event. `ExtensionManager.uninstallExtension()` itself is fire-and-forget (calls
 * `installer.uninstallApk(pkgName)` and returns immediately with no completion signal) -- this is
 * why extension uninstall was previously left entirely unrepresented in Action History. Rather than
 * recording the event on the mere fact that uninstall was *requested* (which would be untruthful --
 * the OS uninstall could be cancelled by the user or fail silently), this awaits
 * [installedPackageNames] (typically `ExtensionManager.installedExtensionsFlow.map { it.map(...) }`)
 * until [pkgName] is no longer present, bounded by [timeoutMillis] so a cancelled/stalled OS
 * uninstall dialog can never hang the caller indefinitely. Returns `true` only when removal was
 * actually observed within the timeout; the caller must not assume success otherwise.
 */
suspend fun verifyAndRecordUninstall(
    installedPackageNames: Flow<List<String>>,
    pkgName: String,
    isEvaluationModeEnabled: () -> Boolean,
    timeoutMillis: Long = 10_000L,
    // KMK Confirmed Blocker Remediation Corrective Completion Plan V2 2026-07-29: optional so every
    // existing caller (SourceEvaluationScreenModel's runtime-health uninstall) remains source-compatible
    // unchanged. Callers that want a PackageOperationReceipt (needed for a future reinstall follow-up)
    // pass the uninstalled package's own signature/version/last-known-artifact fields.
    signatureHash: String? = null,
    versionCode: Long? = null,
    artifactUri: String? = null,
): Boolean {
    val removed = withTimeoutOrNull(timeoutMillis) {
        installedPackageNames.first { pkgName !in it }
    } != null
    if (removed && isEvaluationModeEnabled()) {
        // KMK Confirmed Blocker Remediation Corrective Completion Plan V2 2026-07-29: one shared id
        // for both records -- see recordUserInitiatedInstall()'s [id] parameter doc for why.
        val sharedId = NonUndoableEvent.newId()
        NonUndoableEventJournal.record(
            NonUndoableEvent(
                id = sharedId,
                timestamp = System.currentTimeMillis(),
                eventType = NonUndoableEventType.EXTENSION_UNINSTALLED,
            ),
        )
        PackageOperationJournal.record(
            PackageOperationReceipt(
                id = sharedId,
                timestamp = System.currentTimeMillis(),
                kind = PackageOperationKind.UNINSTALL,
                packageName = pkgName,
                signatureHash = signatureHash,
                versionCode = versionCode,
                artifactUri = artifactUri,
            ),
        )
    }
    return removed
}
// KMK <--
