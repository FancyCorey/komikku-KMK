package eu.kanade.tachiyomi.extension.util

// KMK -->
/**
 * Pure stateless helper that decides whether an extension package should be blocked from loading.
 * Extracted so the block decision can be unit-tested without Android or DB dependencies.
 */
object ExtensionLoadSafetyPolicy {

    /**
     * Returns true if [pkgName] must not be loaded into the app process.
     *
     * Checks the static [KnownUnsafeExtensionPackages] list first (compile-time, no DB needed)
     * then the caller-supplied [userBlockedPackages] set (DB-backed, may be empty at startup).
     */
    fun shouldBlock(pkgName: String, userBlockedPackages: Set<String> = emptySet()): Boolean =
        KnownUnsafeExtensionPackages.isKnownUnsafe(pkgName) || pkgName in userBlockedPackages
}
// KMK <--
