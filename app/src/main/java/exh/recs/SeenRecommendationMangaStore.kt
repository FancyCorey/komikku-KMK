package exh.recs

// KMK -->
data class SeenMangaKey(val sourceId: Long, val url: String) {
    fun serialize(): String = "$sourceId|$url"
}

object SeenRecommendationMangaStore {
    private const val SEPARATOR = ";"

    fun parse(raw: String): Set<SeenMangaKey> {
        if (raw.isBlank()) return emptySet()
        return raw.split(SEPARATOR)
            .filter { it.isNotBlank() }
            .mapNotNull { entry ->
                val idx = entry.indexOf('|')
                if (idx <= 0) return@mapNotNull null
                val sourceId = entry.substring(0, idx).toLongOrNull() ?: return@mapNotNull null
                val url = entry.substring(idx + 1)
                if (url.isBlank()) return@mapNotNull null
                SeenMangaKey(sourceId, url)
            }
            .toSet()
    }

    fun serialize(keys: Set<SeenMangaKey>): String =
        keys.joinToString(SEPARATOR) { it.serialize() }

    fun add(current: Set<SeenMangaKey>, key: SeenMangaKey): Set<SeenMangaKey> =
        current + key

    fun remove(current: Set<SeenMangaKey>, key: SeenMangaKey): Set<SeenMangaKey> =
        current - key

    /** Returns only the requested keys that were active in the pre-write snapshot. */
    fun activeKeys(current: Set<SeenMangaKey>, requested: Collection<SeenMangaKey>): Set<SeenMangaKey> =
        current.intersect(requested.toSet())

    /** Removes a bounded operation's keys without touching unrelated Not Interested entries. */
    fun removeKeys(current: Set<SeenMangaKey>, keys: Collection<SeenMangaKey>): Set<SeenMangaKey> =
        current - keys.toSet()

    /** Restores only keys removed by a failed operation, preserving unrelated concurrent additions. */
    fun restoreKeys(current: Set<SeenMangaKey>, keys: Collection<SeenMangaKey>): Set<SeenMangaKey> =
        current + keys
}
// KMK <--
