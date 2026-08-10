package exh.recs.sourceprefs

// KMK v0.8.1-fix4 -->
/**
 * Pure policy for the source/library-quality preference axis — separate from
 * [RecommendationSourcePreferenceStore]'s recommendation-behavior liked/disliked axis.
 *
 * This axis answers "is this source itself worth showing/suggesting/evaluating?" (poor library,
 * too lewd, hentai/porn-heavy, misleading) rather than "do I want this source's For You rows?".
 * It reuses the same key format and the same liked/disliked serializer — only the storage slot and
 * semantics differ. A third set, [explicit], tracks which disliked keys were marked specifically
 * "too explicit" rather than generically "poor" (a labeling detail, not a filtering rule — both
 * marks hide a source identically).
 */
object SourceQualityMarkPolicy {

    data class State(
        val liked: Set<String>,
        val disliked: Set<String>,
        val explicit: Set<String>,
    )

    /** Mark [key] as "poor" — disliked, not specifically flagged explicit. */
    fun markPoor(state: State, key: String): State {
        val (newLiked, newDisliked) = RecommendationSourcePreferenceStore.dislike(state.liked, state.disliked, key)
        return State(liked = newLiked, disliked = newDisliked, explicit = state.explicit - key)
    }

    /** Mark [key] as "too explicit" — disliked and flagged explicit. */
    fun markExplicit(state: State, key: String): State {
        val (newLiked, newDisliked) = RecommendationSourcePreferenceStore.dislike(state.liked, state.disliked, key)
        return State(liked = newLiked, disliked = newDisliked, explicit = state.explicit + key)
    }

    /** Clear any source-quality mark for [key] (poor or explicit), returning it to neutral. */
    fun clear(state: State, key: String): State {
        val (newLiked, newDisliked) = RecommendationSourcePreferenceStore.reset(state.liked, state.disliked, key)
        return State(liked = newLiked, disliked = newDisliked, explicit = state.explicit - key)
    }

    /** Clear every source-quality mark, returning all three sets to empty. */
    fun clearAll(): State = State(liked = emptySet(), disliked = emptySet(), explicit = emptySet())

    fun isPoor(state: State, key: String): Boolean = key in state.disliked && key !in state.explicit

    fun isExplicit(state: State, key: String): Boolean = key in state.disliked && key in state.explicit
}
// KMK <--
