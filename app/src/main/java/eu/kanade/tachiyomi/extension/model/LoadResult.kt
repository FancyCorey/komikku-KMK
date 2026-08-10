package eu.kanade.tachiyomi.extension.model

sealed interface LoadResult {
    data class Success(val extension: Extension.Installed) : LoadResult
    data class Untrusted(val extension: Extension.Untrusted) : LoadResult
    data object Error : LoadResult
    // KMK --> v0.6.18: extension blocked by package-level safety guard before class loading
    data class Blocked(val pkgName: String, val reason: String) : LoadResult
    // KMK <--
}
