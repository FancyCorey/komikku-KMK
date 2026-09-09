package tachiyomi.domain.tracker.model

/**
 * Conflict policy for a progress event belonging to one concrete source version.
 * Recognized chapter numbers take precedence over timestamps; timestamps arbitrate
 * equal or unknown chapter numbers.
 */
object LocalTrackedSourceProgressPolicy {
    fun accepts(
        existing: LocalTrackedWorkSourceProgress?,
        incoming: LocalTrackedWorkSourceProgress,
    ): Boolean = when {
        existing == null -> true
        existing.chapterNumber == null && incoming.chapterNumber != null -> true
        existing.chapterNumber != null && incoming.chapterNumber == null -> false
        existing.chapterNumber != null && incoming.chapterNumber != null ->
            incoming.chapterNumber > existing.chapterNumber ||
                (incoming.chapterNumber == existing.chapterNumber && incoming.progressAt >= existing.progressAt)
        else -> incoming.progressAt >= existing.progressAt
    }
}
