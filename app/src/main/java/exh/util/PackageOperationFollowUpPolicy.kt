package exh.util

import eu.kanade.tachiyomi.extension.model.Extension

// KMK Confirmed Blocker Remediation Corrective Completion Plan V2 2026-07-29 -->
/**
 * Decides whether a safe follow-up action (uninstall after install/update, reinstall after
 * uninstall) can be offered for a [PackageOperationReceipt] -- kept pure and free of any UI/Context
 * dependency so both directions of the decision are directly unit-testable.
 *
 * The plan's requirement is explicit: never show a generic Undo for an operation that is not
 * actually, verifiably reversible. A follow-up is only [Offered] when the exact package/signature/
 * version currently observed still matches what the receipt recorded -- if the package was
 * reinstalled, updated again, or removed by some other path since the receipt was recorded, the
 * follow-up is refused with a specific [Reason] rather than silently offered against stale state.
 */
object PackageOperationFollowUpPolicy {

    sealed interface UninstallFollowUp {
        data object Offered : UninstallFollowUp
        data class Unavailable(val reason: Reason) : UninstallFollowUp

        enum class Reason {
            /** The package the receipt refers to is not currently installed at all. */
            PACKAGE_NOT_INSTALLED,

            /** A package with this name is installed, but its signature no longer matches the receipt. */
            SIGNATURE_MISMATCH,

            /** A package with this name and signature is installed, but at a different version. */
            VERSION_MISMATCH,
        }
    }

    /**
     * [receipt] must be an [PackageOperationKind.INSTALL] or [PackageOperationKind.UPDATE] receipt.
     * [currentlyInstalled] is the live, freshly-queried installed extension matching the receipt's
     * package name, or `null` if none is currently installed.
     */
    fun evaluateUninstallFollowUp(
        receipt: PackageOperationReceipt,
        currentlyInstalled: Extension.Installed?,
    ): UninstallFollowUp {
        require(receipt.kind == PackageOperationKind.INSTALL || receipt.kind == PackageOperationKind.UPDATE) {
            "uninstall follow-up only applies to INSTALL/UPDATE receipts, was ${receipt.kind}"
        }
        if (currentlyInstalled == null || currentlyInstalled.pkgName != receipt.packageName) {
            return UninstallFollowUp.Unavailable(UninstallFollowUp.Reason.PACKAGE_NOT_INSTALLED)
        }
        if (receipt.signatureHash != null && currentlyInstalled.signatureHash != receipt.signatureHash) {
            return UninstallFollowUp.Unavailable(UninstallFollowUp.Reason.SIGNATURE_MISMATCH)
        }
        if (receipt.versionCode != null && currentlyInstalled.versionCode != receipt.versionCode) {
            return UninstallFollowUp.Unavailable(UninstallFollowUp.Reason.VERSION_MISMATCH)
        }
        return UninstallFollowUp.Offered
    }

    sealed interface ReinstallFollowUp {
        data object Offered : ReinstallFollowUp
        data class Unavailable(val reason: Reason) : ReinstallFollowUp

        enum class Reason {
            /** No verifiable artifact (matching package/signature, with a real download URI) is available to reinstall from. */
            ARTIFACT_NOT_AVAILABLE,

            /** A package with this name is already installed -- reinstall is not applicable. */
            PACKAGE_ALREADY_INSTALLED,
        }
    }

    /**
     * [receipt] must be an [PackageOperationKind.UNINSTALL] receipt. [currentlyInstalled] is the
     * live, freshly-queried installed extension matching the receipt's package name (should normally
     * be `null` -- a non-null value means something reinstalled it through another path since the
     * receipt was recorded). [availableMatch] is the current remote-catalogue entry for this package,
     * if one still exists.
     */
    fun evaluateReinstallFollowUp(
        receipt: PackageOperationReceipt,
        currentlyInstalled: Extension.Installed?,
        availableMatch: Extension.Available?,
    ): ReinstallFollowUp {
        require(receipt.kind == PackageOperationKind.UNINSTALL) {
            "reinstall follow-up only applies to UNINSTALL receipts, was ${receipt.kind}"
        }
        if (currentlyInstalled != null && currentlyInstalled.pkgName == receipt.packageName) {
            return ReinstallFollowUp.Unavailable(ReinstallFollowUp.Reason.PACKAGE_ALREADY_INSTALLED)
        }
        if (availableMatch == null || availableMatch.pkgName != receipt.packageName || availableMatch.apkUrl.isBlank()) {
            return ReinstallFollowUp.Unavailable(ReinstallFollowUp.Reason.ARTIFACT_NOT_AVAILABLE)
        }
        if (receipt.signatureHash != null && availableMatch.signatureHash != receipt.signatureHash) {
            return ReinstallFollowUp.Unavailable(ReinstallFollowUp.Reason.ARTIFACT_NOT_AVAILABLE)
        }
        return ReinstallFollowUp.Offered
    }
}
// KMK <--
