package eu.kanade.tachiyomi.extension.util

// KMK -->
/**
 * Static compile-time list of extension packages that are known to cause native process crashes
 * (SIGSEGV / stack overflow). These are blocked in [ExtensionLoader] before any class loading
 * or source construction occurs, without depending on database readiness.
 *
 * All entries should be removable by the user through Source Evaluation diagnostics UI.
 * Keep this list very small — only confirmed repeated crash culprits belong here.
 */
object KnownUnsafeExtensionPackages {

    data class Entry(
        val pkgName: String,
        val extensionName: String,
        val reason: String,
    )

    val DIGITAL_COMIC_MUSEUM = Entry(
        pkgName = "eu.kanade.tachiyomi.extension.en.digitalcomicmuseum",
        extensionName = "Digital Comic Museum",
        reason = "Known native crash suspect from user logs: repeated SIGSEGV/stack overflow during network interception.",
    )

    val ALL: List<Entry> = listOf(DIGITAL_COMIC_MUSEUM)

    fun isKnownUnsafe(pkgName: String): Boolean = ALL.any { it.pkgName == pkgName }
}
// KMK <--
