package tachiyomi.domain.taste.model

// KMK -->
/**
 * Computed profile representing a user's content preferences.
 *
 * [learnedTagWeights] — per-group-key weight derived from manga ratings and their genres.
 *   Positive values indicate liked content, negative disliked.
 * [explicitTagPreferences] — per-group-key preference from direct tag taste rows (-1 dislike, +1 prefer).
 * [sourceAffinity] — weak per-source signal derived from rating distribution.
 * [blockedGroups] — group keys that must cause hard rejection before scoring.
 */
data class TasteProfile(
    val learnedTagWeights: Map<String, Double>,
    val explicitTagPreferences: Map<String, Int>,
    val sourceAffinity: Map<Long, Double>,
    val blockedGroups: Set<String>,
) {
    fun isEmpty(): Boolean =
        learnedTagWeights.isEmpty() && explicitTagPreferences.isEmpty() && blockedGroups.isEmpty()

    companion object {
        val EMPTY = TasteProfile(
            learnedTagWeights = emptyMap(),
            explicitTagPreferences = emptyMap(),
            sourceAffinity = emptyMap(),
            blockedGroups = emptySet(),
        )
    }
}
// KMK <--
