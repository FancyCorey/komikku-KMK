package mihon.domain.extension

import java.net.URI

/**
 * Defines the URL boundary for extension-store fetches and user-provided store links.
 *
 * Extension stores are fetched over the network, so the URL must identify an ordinary
 * HTTP(S) authority. Credentials and fragments are rejected to avoid accepting misleading
 * or non-portable repository links at any input boundary.
 */
object ExtensionStoreUrlPolicy {

    private val allowedSchemes = setOf("http", "https")

    fun isAllowed(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme?.lowercase() in allowedSchemes &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null &&
            uri.fragment == null
    }.getOrDefault(false)
}
