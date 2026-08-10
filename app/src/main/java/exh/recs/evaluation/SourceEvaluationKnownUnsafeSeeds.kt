package exh.recs.evaluation

import eu.kanade.tachiyomi.extension.util.KnownUnsafeExtensionPackages

// KMK -->
/** A known-bad extension that should be pre-seeded into the Source Evaluation unsafe quarantine. */
data class KnownUnsafeSeed(
    val pkgName: String,
    val extensionName: String,
    val reason: String,
)

/**
 * Removable known-unsafe seeds for extensions that have repeatedly triggered native crashes.
 * Delegates to [KnownUnsafeExtensionPackages] so package names and reasons are defined once.
 */
object SourceEvaluationKnownUnsafeSeeds {

    val DIGITAL_COMIC_MUSEUM = KnownUnsafeSeed(
        pkgName = KnownUnsafeExtensionPackages.DIGITAL_COMIC_MUSEUM.pkgName,
        extensionName = KnownUnsafeExtensionPackages.DIGITAL_COMIC_MUSEUM.extensionName,
        reason = KnownUnsafeExtensionPackages.DIGITAL_COMIC_MUSEUM.reason,
    )

    val ALL_SEEDS: List<KnownUnsafeSeed> = KnownUnsafeExtensionPackages.ALL.map {
        KnownUnsafeSeed(pkgName = it.pkgName, extensionName = it.extensionName, reason = it.reason)
    }
}
// KMK <--
