package exh.recs

// KMK -->
import eu.kanade.tachiyomi.source.Source

/**
 * Manages manual priority ordering of recommendation sources.
 *
 * Stored order is a comma-separated list of source ids. Sources not in the stored order
 * are appended in their default visible order. Disabled sources are always excluded from
 * the enabled result, but remain in the stored list so their position is preserved on re-enable.
 */
internal object RecommendationSourceOrdering {

    const val BOOSTED_SOURCE_COUNT = 3

    fun parse(value: String): List<Long> =
        if (value.isBlank()) {
            emptyList()
        } else {
            value.split(",").mapNotNull { it.trim().toLongOrNull() }.distinct()
        }

    fun serialize(ids: List<Long>): String = ids.joinToString(",")

    /**
     * Returns all visible sources in stored priority order, with sources not yet in the order
     * appended at the end. Disabled source ids are excluded.
     */
    fun apply(
        visibleSources: List<Source>,
        storedOrder: List<Long>,
        disabledSourceIds: Set<Long>,
    ): List<Source> {
        val visibleById = visibleSources.associateBy { it.id }
        val enabledSources = visibleSources.filter { it.id !in disabledSourceIds }
        if (storedOrder.isEmpty()) return enabledSources
        val storedEnabled = storedOrder.mapNotNull { id ->
            visibleById[id]?.takeIf { it.id !in disabledSourceIds }
        }
        val alreadyOrdered = storedEnabled.map { it.id }.toSet()
        val appended = enabledSources.filter { it.id !in alreadyOrdered }
        return storedEnabled + appended
    }

    /**
     * Returns all visible sources in stored priority order (including disabled) for display in
     * the priority settings screen.
     */
    fun applyAll(
        visibleSources: List<Source>,
        storedOrder: List<Long>,
    ): List<Source> {
        if (storedOrder.isEmpty()) return visibleSources
        val visibleById = visibleSources.associateBy { it.id }
        val ordered = storedOrder.mapNotNull { visibleById[it] }
        val alreadyOrdered = ordered.map { it.id }.toSet()
        val appended = visibleSources.filter { it.id !in alreadyOrdered }
        return ordered + appended
    }

    /** Returns the ids of the first [BOOSTED_SOURCE_COUNT] sources (the boosted set). */
    fun boostedSourceIds(orderedEnabledSources: List<Source>): Set<Long> =
        orderedEnabledSources.take(BOOSTED_SOURCE_COUNT).map { it.id }.toSet()

    /**
     * Merges a reordered visible-language subset back into the full stored order, preserving
     * ids for hidden-language sources in their relative positions.
     *
     * The resulting order is: [visibleOrderedIds] followed by any ids from [existingStoredOrder]
     * that are not in [allVisibleSourceIds] (i.e. hidden-language sources, preserved as-is).
     * Duplicate ids are removed.
     */
    fun mergeVisibleOrder(
        existingStoredOrder: List<Long>,
        visibleOrderedIds: List<Long>,
        allVisibleSourceIds: Set<Long>,
    ): List<Long> {
        val hiddenIds = existingStoredOrder.filter { it !in allVisibleSourceIds }
        return (visibleOrderedIds + hiddenIds).distinct()
    }
}
// KMK <--
