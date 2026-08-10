package eu.kanade.tachiyomi.ui.main

/**
 * Keeps explicit-component backup launches within the same URI boundary declared by the manifest.
 */
object BackupDeepLinkPolicy {

    private val allowedSchemes = setOf("file", "content")

    fun isAllowed(scheme: String?, path: String?): Boolean {
        return scheme?.lowercase() in allowedSchemes &&
            path?.endsWith(".tachibk", ignoreCase = true) == true
    }
}
