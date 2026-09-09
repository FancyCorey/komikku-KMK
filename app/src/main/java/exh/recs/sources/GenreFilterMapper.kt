package exh.recs.sources

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import tachiyomi.domain.taste.model.normalizeTag

// KMK -->
/**
 * Maps a list of genre strings to a source's [FilterList], enabling the closest matching
 * filter entries and returning any unmatched genres as a plain-text query fallback.
 *
 * Matching priority per genre:
 * 1. [Filter.Group] children of type [Filter.TriState] (set to STATE_INCLUDE)
 * 2. [Filter.Group] children of type [Filter.CheckBox] (set to true)
 * 3. [Filter.Select] values (set to matching index)
 * 4. [Filter.AutoComplete] values (appended to state list)
 * 5. Unmatched → appended to [SearchParams.textQuery], space-separated
 *
 * When [aliasCandidates] is provided, each desired genre also tries the alias labels before
 * falling back to text. [BUILT_IN_SYNONYMS] are always checked after user aliases.
 *
 * When [forceTextOnly] is true, no filters are mutated and the genres (plus first alias) become
 * the text query — useful for text-only query strategy plans.
 *
 * Normalization: lowercased, non-alphanumeric chars replaced with spaces, consecutive spaces
 * collapsed. "Sci-Fi" and "sci fi" are treated as identical.
 */
internal object GenreFilterMapper {

    data class SearchParams(
        val filters: FilterList,
        val textQuery: String,
    )

    /**
     * Small universal synonym map: normalizedGroupKey -> candidate label strings.
     * Keep this list short and universal — do not add source-specific aliases here.
     */
    // Keys must be in normalizeTag() form (lowercase, underscores/dashes → spaces).
    // "girls_love".normalizeTag() == "girls love", so keys here use spaces not underscores.
    val BUILT_IN_SYNONYMS: Map<String, List<String>> = mapOf(
        "girls love" to listOf("Girls Love", "Yuri", "GL", "Shoujo Ai"),
        "boys love" to listOf("Boys Love", "Yaoi", "BL", "Shounen Ai"),
        "sci fi" to listOf("Sci-Fi", "Sci Fi", "Science Fiction"),
        "isekai" to listOf("Isekai", "Another World"),
        "martial arts" to listOf("Martial Arts", "Wuxia", "Murim"),
        // Common source spelling variant; keep the canonical focus group as "fantasy".
        "fantasy" to listOf("Fantasy", "Fantacy"),
        "reincarnation" to listOf("Reincarnation", "Reborn"),
        "regression" to listOf("Regression", "Second Chance", "Returner"),
    )

    /**
     * @param aliasCandidates maps normalizedGroupKey -> list of alternate label strings to try.
     *   User aliases (groupKey -> aliases) should be merged with [BUILT_IN_SYNONYMS] by the caller.
     * @param forceTextOnly skip filter mutation and return a text-only query.
     * @param blockedGenres genres to push as TriState STATE_EXCLUDE when a source supports it.
     *   Post-fetch filtering always remains as the fallback — this is query-time best-effort only.
     */
    // KMK --> v0.7.0: Phase 7 — blockedGenres
    fun buildSearch(
        filterList: FilterList,
        desiredGenres: List<String>,
        aliasCandidates: Map<String, List<String>> = emptyMap(),
        forceTextOnly: Boolean = false,
        blockedGenres: List<String> = emptyList(),
    ): SearchParams {
        if (forceTextOnly) {
            // Build text query from desired genres, adding first alias variant where available
            val text = desiredGenres.flatMap { genre ->
                val normKey = genre.normalize()
                val extras = (aliasCandidates[normKey].orEmpty() + BUILT_IN_SYNONYMS[normKey].orEmpty())
                    .take(1)
                listOf(genre) + extras
            }.distinct().take(6).joinToString(" ")
            return SearchParams(filters = filterList, textQuery = text)
        }

        val unmapped = mutableListOf<String>()

        for (genre in desiredGenres) {
            val normKey = genre.normalize()
            // Build ordered normalized candidate labels: original first, then user aliases, then built-in
            val candidateNorms = buildList {
                add(normKey)
                aliasCandidates[normKey]?.forEach { add(it.normalize()) }
                BUILT_IN_SYNONYMS[normKey]?.forEach { add(it.normalize()) }
            }.distinct()

            var matched = false

            outer@ for (filter in filterList) {
                when (filter) {
                    is Filter.Group<*> -> {
                        for (child in filter.state) {
                            when (child) {
                                is Filter.TriState -> if (child.name.normalize() in candidateNorms) {
                                    child.state = Filter.TriState.STATE_INCLUDE
                                    matched = true
                                    break@outer
                                }
                                is Filter.CheckBox -> if (child.name.normalize() in candidateNorms) {
                                    child.state = true
                                    matched = true
                                    break@outer
                                }
                            }
                        }
                    }
                    is Filter.Select<*> -> {
                        val idx = filter.values.indexOfFirst { it.toString().normalize() in candidateNorms }
                        if (idx >= 0) {
                            filter.state = idx
                            matched = true
                            break@outer
                        }
                    }
                    is Filter.AutoComplete -> {
                        val matchedValue = filter.values.firstOrNull { it.normalize() in candidateNorms }
                        if (matchedValue != null && matchedValue !in filter.state) {
                            filter.state = filter.state + matchedValue
                            matched = true
                            break@outer
                        }
                    }
                    else -> {}
                }
            }

            if (!matched) unmapped.add(genre)
        }

        // KMK --> v0.7.0: Phase 7 — push blocked genres as exclusion filters where safe
        if (!forceTextOnly && blockedGenres.isNotEmpty()) {
            for (blocked in blockedGenres) {
                val normKey = blocked.normalize()
                val candidateNorms = buildList {
                    add(normKey)
                    BUILT_IN_SYNONYMS[normKey]?.forEach { add(it.normalize()) }
                }.distinct()
                outer@ for (filter in filterList) {
                    if (filter is Filter.Group<*>) {
                        for (child in filter.state) {
                            if (child is Filter.TriState && child.name.normalize() in candidateNorms &&
                                child.state == Filter.TriState.STATE_IGNORE
                            ) {
                                child.state = Filter.TriState.STATE_EXCLUDE
                                break@outer
                            }
                        }
                    }
                }
            }
        }
        // KMK <--

        return SearchParams(
            filters = filterList,
            textQuery = unmapped.joinToString(" "),
        )
    }

    // Delegates to the shared normalizeTag() in tachiyomi.domain.taste.model so all normalization
    // in genre filter mapping, scoring, and cache-key generation is identical.
    internal fun String.normalize(): String = normalizeTag()
}
// KMK <--
