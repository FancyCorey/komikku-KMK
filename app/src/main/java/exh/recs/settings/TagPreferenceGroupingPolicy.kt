package exh.recs.settings

import tachiyomi.domain.taste.model.TagPreference
import tachiyomi.domain.taste.model.TagTaste

// KMK v0.8.10-fix9 -->
/**
 * Splits a flat list of [TagTaste] preferences into the groups the Taste and Tags settings screen
 * renders separately, so Preferred and Blocked tags are both reachable without scrolling through the
 * other group first. Pure, no Compose dependency, so it is unit-testable directly.
 */
object TagPreferenceGroupingPolicy {

    data class Grouped(
        val preferred: List<TagTaste>,
        val disliked: List<TagTaste>,
        val blocked: List<TagTaste>,
        /** Tags with a null/unrecognized [TagTaste.preference] -- preserved, never dropped. */
        val other: List<TagTaste>,
    ) {
        val isEmpty: Boolean get() = preferred.isEmpty() && disliked.isEmpty() && blocked.isEmpty() && other.isEmpty()
    }

    fun group(tags: List<TagTaste>): Grouped {
        val preferred = mutableListOf<TagTaste>()
        val disliked = mutableListOf<TagTaste>()
        val blocked = mutableListOf<TagTaste>()
        val other = mutableListOf<TagTaste>()
        for (tag in tags) {
            when (TagPreference.fromValue(tag.preference)) {
                TagPreference.PREFER -> preferred.add(tag)
                TagPreference.DISLIKE -> disliked.add(tag)
                TagPreference.BLOCK -> blocked.add(tag)
                null -> other.add(tag)
            }
        }
        return Grouped(preferred = preferred, disliked = disliked, blocked = blocked, other = other)
    }
}
// KMK <--
