package exh.util

/**
 * Resolves only a journal-owned, positive database identity for Action History context navigation.
 * Display text, source names, and URLs are never used as fallback identity.
 */
object ActionHistoryContextNavigationPolicy {
    fun targetMangaId(contextMangaId: Long?): Long? = contextMangaId?.takeIf { it > 0L }
}
