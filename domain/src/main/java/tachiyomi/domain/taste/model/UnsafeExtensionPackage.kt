package tachiyomi.domain.taste.model

// KMK -->
/** Extension package name that is blocked from loading due to repeated native crashes. */
data class UnsafeExtensionPackage(
    val pkgName: String,
    val extensionName: String?,
    val reason: String,
    /** How the block was created: "known_seed", "probe_recovery", or "manual". */
    val source: String,
    /** User can remove this block from the Source Evaluation UI. */
    val removable: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
// KMK <--
