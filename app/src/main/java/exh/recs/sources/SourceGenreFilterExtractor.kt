package exh.recs.sources

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import tachiyomi.domain.taste.model.normalizeTag

// KMK HR-2026-08-26-FOCUS-CRITERION-CATALOG-COMPLETENESS (C3/E4.3) -->
/**
 * Extracts the genre/tag-like option labels a single source's own [FilterList] actually supports,
 * for use as one input into the For You focus criterion catalog (see
 * `exh.recs.buildFocusKnownGroups` in RecommendationFocusPolicy.kt). This is the missing piece
 * E4.3 identified: the prior catalog was built only from loaded-result genres, the user's alias
 * table, and a small built-in synonym list -- never from what eligible sources themselves report
 * they can filter by.
 *
 * Deliberately reads only [Filter.Group] children ([Filter.TriState]/[Filter.CheckBox]) and
 * [Filter.AutoComplete] candidate values -- in real extensions these two shapes are overwhelmingly
 * used for genre/tag checklists and tag-search autocomplete, never sort order or status. A
 * [Filter.Select] is deliberately excluded: extensions use it just as often for "Sort by"/"Status"/
 * "Order" pickers as for genre lists, and unlike [GenreFilterMapper.buildSearch] (which only needs
 * a name to MATCH against, so a false-positive match on a stray Select value is harmless) this
 * function POPULATES a user-facing picker -- a wrong inclusion here would visibly offer "Focus:
 * Ascending" as a real option, not just silently fail to match. This narrower scope is a
 * deliberate, disclosed trade-off, not an oversight.
 */
internal object SourceGenreFilterExtractor {

    /** Caps how many labels a single source's filter list can contribute -- see the class doc. */
    private const val MAX_LABELS_PER_SOURCE = 300

    fun extract(filterList: FilterList): Set<String> {
        val labels = mutableSetOf<String>()
        for (filter in filterList) {
            if (labels.size >= MAX_LABELS_PER_SOURCE) break
            when (filter) {
                is Filter.Group<*> -> {
                    for (child in filter.state) {
                        if (labels.size >= MAX_LABELS_PER_SOURCE) break
                        when (child) {
                            is Filter.TriState -> if (child.name.isNotBlank()) labels += child.name
                            is Filter.CheckBox -> if (child.name.isNotBlank()) labels += child.name
                            else -> {}
                        }
                    }
                }
                is Filter.AutoComplete -> {
                    for (value in filter.values) {
                        if (labels.size >= MAX_LABELS_PER_SOURCE) break
                        if (value.isNotBlank()) labels += value
                    }
                }
                else -> {}
            }
        }
        return labels
            .asSequence()
            .map { it.normalizeTag() }
            .filter { it.isNotBlank() }
            .toSet()
    }
}
// KMK <--
