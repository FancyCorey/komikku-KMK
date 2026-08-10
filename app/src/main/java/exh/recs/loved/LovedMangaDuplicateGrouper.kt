package exh.recs.loved

// KMK -->
/**
 * Pure helper for tiered display-only duplicate grouping of loved manga entries.
 *
 * Grouping priority:
 * 1. Confirmed cross-source link group (user-verified same-manga identity).
 * 2. Exact normalized title + same non-blank author.
 * 3. Exact normalized title + same non-blank artist.
 * 4. Exact normalized title + exact same description (both >= MIN_DESCRIPTION_LENGTH).
 * 5. Exact normalized title + highly similar long description (both >= MIN_LONG_DESCRIPTION_FOR_SIMILARITY).
 * 6. High title similarity + same non-blank author.
 * 7. High title similarity + same non-blank artist.
 * 8. Standalone (never grouped by title alone, or when supporting metadata is blank).
 *
 * No taste rows are deleted or merged. This is display-only.
 */
object LovedMangaDuplicateGrouper {

    internal const val MIN_DESCRIPTION_LENGTH = 50
    internal const val MIN_LONG_DESCRIPTION_FOR_SIMILARITY = 80
    internal const val TITLE_SIMILARITY_THRESHOLD = 0.80
    internal const val DESCRIPTION_SIMILARITY_THRESHOLD = 0.85

    enum class LovedMangaGroupReason {
        LINK_GROUP,
        TITLE_AND_AUTHOR,
        TITLE_AND_ARTIST,
        TITLE_AND_SIMILAR_DESCRIPTION,
        SIMILAR_TITLE_AND_AUTHOR,
        SIMILAR_TITLE_AND_ARTIST,
        STANDALONE,
    }

    data class GroupInput(
        val key: String,
        val source: Long = 0L,
        val url: String = "",
        val title: String,
        val description: String = "",
        val author: String? = null,
        val artist: String? = null,
        val linkGroupId: String? = null,
    )

    data class GroupResult(
        val primaryKey: String,
        val memberKeys: List<String>,
        val reason: LovedMangaGroupReason = LovedMangaGroupReason.STANDALONE,
    ) {
        val versionCount: Int get() = memberKeys.size
    }

    private class Slot(val primaryKey: String) {
        val memberKeys = mutableListOf(primaryKey)
        var reason = LovedMangaGroupReason.STANDALONE

        fun merge(key: String, mergeReason: LovedMangaGroupReason) {
            memberKeys.add(key)
            if (reason == LovedMangaGroupReason.STANDALONE) reason = mergeReason
        }
    }

    private data class SlotMeta(
        val slot: Slot,
        val normTitle: String,
        val normDesc: String,
        val normAuthor: String?,
        val normArtist: String?,
    )

    /**
     * Compute display groups from the given inputs (already sorted by recency).
     *
     * The first entry that starts a group becomes its representative (primaryKey).
     * Subsequent entries are merged in order of priority.
     * Order in the output list follows the first-occurrence order of each group/standalone entry.
     */
    fun computeGroups(inputs: List<GroupInput>): List<GroupResult> {
        val outputSlots = mutableListOf<Slot>()

        // Tier 1: link group fast-path
        val linkGroupToSlot = HashMap<String, Slot>()

        // Tiers 2â€“4: exact metadata fast-path for unlinked entries
        val exactAuthorToSlot = HashMap<String, Slot>() // normTitle+NUL+normAuthor â†’ slot
        val exactArtistToSlot = HashMap<String, Slot>() // normTitle+NUL+normArtist â†’ slot
        val exactDescToSlot = HashMap<String, Slot>() // normTitle+NUL+normDesc â†’ slot

        // Tiers 5â€“7: scan-based similarity for unlinked entries
        val unlinkedSlotMeta = mutableListOf<SlotMeta>()

        for (input in inputs) {
            val lgId = input.linkGroupId

            // --- Tier 1: confirmed cross-source link group ---
            if (!lgId.isNullOrBlank()) {
                val existing = linkGroupToSlot[lgId]
                if (existing != null) {
                    existing.merge(input.key, LovedMangaGroupReason.LINK_GROUP)
                } else {
                    val slot = Slot(input.key).also { it.reason = LovedMangaGroupReason.LINK_GROUP }
                    outputSlots.add(slot)
                    linkGroupToSlot[lgId] = slot
                }
                continue
            }

            val normTitle = normalizeTitle(input.title)
            if (normTitle.isBlank()) {
                // Safety: blank normalized title is always standalone
                outputSlots.add(Slot(input.key))
                continue
            }

            val normDesc = normalizeDescription(input.description)
            val normAuthor = input.author?.let { normalizeAuthorArtist(it) }?.takeIf { it.isNotBlank() }
            val normArtist = input.artist?.let { normalizeAuthorArtist(it) }?.takeIf { it.isNotBlank() }

            // --- Tier 2: exact title + author ---
            if (normAuthor != null) {
                val key = "$normTitle\u0000$normAuthor"
                val existing = exactAuthorToSlot[key]
                if (existing != null) {
                    existing.merge(input.key, LovedMangaGroupReason.TITLE_AND_AUTHOR)
                    continue
                }
            }

            // --- Tier 3: exact title + artist ---
            if (normArtist != null) {
                val key = "$normTitle\u0000$normArtist"
                val existing = exactArtistToSlot[key]
                if (existing != null) {
                    existing.merge(input.key, LovedMangaGroupReason.TITLE_AND_ARTIST)
                    continue
                }
            }

            // --- Tier 4: exact title + exact description (both >= MIN_DESCRIPTION_LENGTH) ---
            if (normDesc.length >= MIN_DESCRIPTION_LENGTH) {
                val key = "$normTitle\u0000$normDesc"
                val existing = exactDescToSlot[key]
                if (existing != null) {
                    existing.merge(input.key, LovedMangaGroupReason.TITLE_AND_SIMILAR_DESCRIPTION)
                    continue
                }
            }

            // --- Tier 5: exact title + similar long description ---
            if (normDesc.length >= MIN_LONG_DESCRIPTION_FOR_SIMILARITY) {
                var matched = false
                for (meta in unlinkedSlotMeta) {
                    if (meta.normTitle == normTitle &&
                        meta.normDesc.length >= MIN_LONG_DESCRIPTION_FOR_SIMILARITY &&
                        tokenJaccardSimilarity(normDesc, meta.normDesc) >= DESCRIPTION_SIMILARITY_THRESHOLD
                    ) {
                        meta.slot.merge(input.key, LovedMangaGroupReason.TITLE_AND_SIMILAR_DESCRIPTION)
                        matched = true
                        break
                    }
                }
                if (matched) continue
            }

            // --- Tier 6: similar title + same author ---
            if (normAuthor != null) {
                var matched = false
                for (meta in unlinkedSlotMeta) {
                    if (meta.normAuthor == normAuthor &&
                        tokenJaccardSimilarity(normTitle, meta.normTitle) >= TITLE_SIMILARITY_THRESHOLD
                    ) {
                        meta.slot.merge(input.key, LovedMangaGroupReason.SIMILAR_TITLE_AND_AUTHOR)
                        matched = true
                        break
                    }
                }
                if (matched) continue
            }

            // --- Tier 7: similar title + same artist ---
            if (normArtist != null) {
                var matched = false
                for (meta in unlinkedSlotMeta) {
                    if (meta.normArtist == normArtist &&
                        tokenJaccardSimilarity(normTitle, meta.normTitle) >= TITLE_SIMILARITY_THRESHOLD
                    ) {
                        meta.slot.merge(input.key, LovedMangaGroupReason.SIMILAR_TITLE_AND_ARTIST)
                        matched = true
                        break
                    }
                }
                if (matched) continue
            }

            // --- No match: new standalone slot ---
            val slot = Slot(input.key)
            outputSlots.add(slot)

            // Register in fast-path lookups for future entries
            if (normAuthor != null) exactAuthorToSlot.putIfAbsent("$normTitle\u0000$normAuthor", slot)
            if (normArtist != null) exactArtistToSlot.putIfAbsent("$normTitle\u0000$normArtist", slot)
            if (normDesc.length >= MIN_DESCRIPTION_LENGTH) exactDescToSlot.putIfAbsent("$normTitle\u0000$normDesc", slot)

            unlinkedSlotMeta.add(SlotMeta(slot, normTitle, normDesc, normAuthor, normArtist))
        }

        return outputSlots.map { GroupResult(it.primaryKey, it.memberKeys.toList(), it.reason) }
    }

    internal fun normalizeTitle(title: String): String =
        title.lowercase().trim()
            .replace(Regex("[\\-â€“â€”:Â·â€¦]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    internal fun normalizeDescription(description: String): String =
        description.lowercase().trim()
            .replace(Regex("\\s+"), " ")
            .trim()

    internal fun normalizeAuthorArtist(name: String): String =
        name.lowercase().trim()
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Token-based Jaccard similarity. Returns 1.0 for identical strings, 0.0 for empty or disjoint. */
    internal fun tokenJaccardSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isBlank() || b.isBlank()) return 0.0
        val tokA = a.split(' ').filter { it.isNotBlank() }.toHashSet()
        val tokB = b.split(' ').filter { it.isNotBlank() }.toHashSet()
        if (tokA.isEmpty() || tokB.isEmpty()) return 0.0
        val intersection = tokA.count { it in tokB }
        val union = (tokA + tokB).size
        return intersection.toDouble() / union
    }
}
// KMK <--
