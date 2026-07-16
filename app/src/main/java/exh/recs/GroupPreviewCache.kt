package exh.recs

import tachiyomi.domain.manga.model.Manga

// KMK v0.8.6 -->
/**
 * Bounded in-memory cache for one extension's GROUP_PREVIEW row result, scoped exclusively to
 * cross-extension group recommendation previews (see [RecommendationLoadContext.GROUP_PREVIEW]).
 *
 * This must never be shared with For You (BrowsePersonalRecommendationsScreenModel's own cache
 * fingerprints) or normal global search (SearchScreenModel is uncapped and uncached by this class
 * entirely). No existing recommendation cache in this codebase safely keys a group seed together
 * with a per-source preview budget and query-policy version, so this is a new, narrowly-scoped
 * cache rather than an extension of an existing one — no database table is used or required, this
 * is process-memory only and is lost on process death (satisfying plan section 10's "invalidate on
 * ... process death" requirement trivially).
 *
 * Thread-safety: callers on this screen model already run in a single coroutine-confined mutation
 * path (see RecommendsScreenModel's per-row async blocks reading/writing via the main state flow);
 * this cache itself synchronizes internally so it is safe to call from multiple coroutines.
 */
object GroupPreviewCache {

    /** Entries older than this are treated as misses even if still present (oldest-eviction is separate, size-based). */
    const val TTL_MS = 5 * 60 * 1000L

    /** Bounded entry count — oldest entry (by insertion/access order) is evicted once this is exceeded. */
    const val MAX_ENTRIES = 64

    /**
     * Bumped whenever a change to query-plan generation/scoring logic would make previously cached
     * results stale even though every other key component is unchanged (e.g. a fallback-chain
     * rewrite). Included in every [Key] so a code change safely invalidates old entries without
     * needing a runtime migration.
     */
    const val QUERY_POLICY_VERSION = 1

    data class Key(
        val groupFingerprint: String,
        val sourceId: Long,
        val language: String,
        val normalizedSeed: String,
        val visibilityFingerprint: String,
        val previewBudget: Int,
        val queryPolicyVersion: Int = QUERY_POLICY_VERSION,
    )

    private data class Entry(
        val mangas: List<Manga>,
        val insertedAtMs: Long,
    )

    // LinkedHashMap in insertion order: iteration order is oldest-first, so the first key is always
    // the oldest entry to evict. Reads never reorder entries (insertion order, not access order),
    // so a cache hit does not protect an entry from later eviction.
    private val entries = LinkedHashMap<Key, Entry>()

    @Synchronized
    fun get(key: Key, nowMs: Long = System.currentTimeMillis()): List<Manga>? {
        val entry = entries[key] ?: return null
        if (nowMs - entry.insertedAtMs > TTL_MS) {
            entries.remove(key)
            return null
        }
        return entry.mangas
    }

    @Synchronized
    fun put(key: Key, mangas: List<Manga>, nowMs: Long = System.currentTimeMillis()) {
        entries[key] = Entry(mangas, nowMs)
        while (entries.size > MAX_ENTRIES) {
            val oldestKey = entries.keys.firstOrNull() ?: break
            entries.remove(oldestKey)
        }
    }

    /** Removes every cached entry. Called on refresh, taste/group change, language/source-order change, or disabled-source change. */
    @Synchronized
    fun invalidateAll() {
        entries.clear()
    }

    @Synchronized
    fun size(): Int = entries.size
}
// KMK <--
