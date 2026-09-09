package exh.recs.loved

import java.util.Locale

// KMK v0.8.10 -->
/**
 * Pure local filter for the Loved/Liked/Disliked rated-manga collections. Filters the already
 * loaded, installed-source [LovedDisplayItem] list -- never issues a network search, never mutates
 * anything, and has no Android/Compose dependency so it's directly unit-testable.
 *
 * Matches (case-insensitive, substring):
 * - the displayed title (`manga?.title ?: taste.title`),
 * - the taste's own stored title as an "alternate" already-available title (can differ from the
 *   manga's current title after a title edit or a source-side rename),
 * - the resolved source name, when the caller can supply one (source lookups happen in the
 *   Composable layer, not here -- this stays a pure function of already-known strings).
 *
 * Deliberately does NOT search group ids or any other non-human-readable identifier, and never
 * expands results to uninstalled sources or auto-merges titles -- both explicit plan non-goals.
 */
object RatedMangaSearchFilter {

    fun matches(item: LovedDisplayItem, normalizedQuery: String, sourceName: String?): Boolean {
        if (normalizedQuery.isBlank()) return true
        val title = normalize(item.manga?.title ?: item.taste.title)
        val alternateTitle = normalize(item.taste.title)
        val source = sourceName?.let(::normalize).orEmpty()
        return title.contains(normalizedQuery) ||
            alternateTitle.contains(normalizedQuery) ||
            source.contains(normalizedQuery)
    }

    fun filter(
        items: List<LovedDisplayItem>,
        query: String,
        sourceNameOf: (Long) -> String?,
    ): List<LovedDisplayItem> {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return items
        return items.filter { matches(it, normalizedQuery, sourceNameOf(it.taste.source)) }
    }

    private fun normalize(s: String): String = s.trim().lowercase(Locale.ROOT)
}
// KMK <--
