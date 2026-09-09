package exh.recs.matching

// KMK -->
import tachiyomi.domain.manga.model.Manga
import java.util.Locale

/**
 * Builds a deduplicated, capped list of search queries for cross-extension matching.
 *
 * Strategy:
 * 1. Always include [Manga.title] (which may be a custom title overriding the original).
 * 2. Include [Manga.ogTitle] if it differs from [Manga.title] — covers sources that index by
 *    original/romanized/translated title when the other source uses a different name variant.
 * 3. Include a bracket-stripped variant of whichever title is longer, if it differs from the above.
 * 4. Deduplicate after normalisation; cap at [MAX_QUERIES].
 *
 * The returned list is ordered from most-specific (full title) to broadest (stripped variant).
 * Callers are responsible for applying [tachiyomi.core.common.util.QuerySanitizer.sanitize]
 * before issuing network requests.
 */
internal object CrossExtensionMatchQueryPlanner {

    const val MAX_QUERIES = 3

    private val BRACKET_PATTERN = Regex("""\s*[(\[{][^)\]]*[)\]}]""")

    fun buildQueries(manga: Manga): List<String> {
        val queries = mutableListOf<String>()
        queries.add(manga.title)
        if (manga.ogTitle != manga.title) {
            queries.add(manga.ogTitle)
        }
        // Bracket-stripped version: remove (Season 2), [Official], etc.
        val base = if (manga.ogTitle.length >= manga.title.length) manga.ogTitle else manga.title
        val stripped = BRACKET_PATTERN.replace(base, "").trim()
        if (stripped.isNotBlank() && queries.none { normalizeForDedup(it) == normalizeForDedup(stripped) }) {
            queries.add(stripped)
        }
        return queries.take(MAX_QUERIES)
    }

    private fun normalizeForDedup(s: String): String = s.lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")
}
// KMK <--
