package exh.recs.evaluation

// KMK v0.8.15-fix1 -->
/**
 * Pure decisions for the per-row `Details` / `Errors` / `Install` action model clarified by the
 * v0.8.15-fix1 plan. No Android/Compose dependency, so directly unit-testable.
 */
object SourceEvaluationRowActionPolicy {

    enum class InstallEligibility {
        /** Safe to offer a direct Install action for this row's extension. */
        ELIGIBLE,
        /** Already installed -- offering Install again would be misleading. */
        ALREADY_INSTALLED,
        /** Blocked/quarantined (unsafe extension or blocked package) -- must not offer Install. */
        BLOCKED,
        /** No longer present in the available-extensions repository listing. */
        UNAVAILABLE,
    }

    /**
     * @param isInstalled true when an extension with this package name is currently installed.
     * @param isBlocked true when this package/signature is quarantined (crash-unsafe) or on the
     * blocked-extension-package list.
     * @param isAvailable true when this extension is still present in the available-extensions
     * repository listing (an extension can disappear from a repo after being evaluated).
     */
    fun installEligibility(
        isInstalled: Boolean,
        isBlocked: Boolean,
        isAvailable: Boolean,
    ): InstallEligibility = when {
        isInstalled -> InstallEligibility.ALREADY_INSTALLED
        isBlocked -> InstallEligibility.BLOCKED
        !isAvailable -> InstallEligibility.UNAVAILABLE
        else -> InstallEligibility.ELIGIBLE
    }

    /** True when the row should offer an active, non-misleading Install action. */
    fun canOfferInstall(eligibility: InstallEligibility): Boolean = eligibility == InstallEligibility.ELIGIBLE

    /**
     * True when the row has error-specific information worth surfacing as a separate `Errors`
     * action, distinct from the full evidence `Details` action.
     *
     * @param isCatalogueError true when the stored evaluation's own verdict is ERROR with a message.
     * @param hasSearchFailureKind true when a classified For You search-compatibility failure kind
     * is available for this row (see `failureKindLabel`).
     * @param hasSearchReasonHint true when a current-outcome search-compatibility reason/error hint
     * is available for this row.
     */
    fun hasErrorInfo(
        isCatalogueError: Boolean,
        hasSearchFailureKind: Boolean,
        hasSearchReasonHint: Boolean,
    ): Boolean = isCatalogueError || hasSearchFailureKind || hasSearchReasonHint
}
// KMK <--
