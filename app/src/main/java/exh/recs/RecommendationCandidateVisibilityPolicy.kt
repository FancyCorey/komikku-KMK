package exh.recs

// KMK --> v0.7.40: shared candidate visibility policy for For You and group-seeded recommendations
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.MangaTaste
import tachiyomi.domain.taste.model.RatedMangaVisibility

/**
 * Typed result of evaluating a candidate's visibility in a personalized recommendation flow.
 *
 * [VISIBLE] means the candidate passes all policy checks.
 * All other values describe why the candidate is hidden (for diagnostics and cache policy).
 */
enum class CandidateVisibility {
    VISIBLE,
    HIDDEN_FAVORITE,
    HIDDEN_RATED,
    HIDDEN_SEEN,
    HIDDEN_KNOWN,
    HIDDEN_MIN_CHAPTERS,
    HIDDEN_SEED_MEMBER,
}

/**
 * Pure, stateless policy that determines whether a localized manga candidate is eligible
 * for display in a personalized recommendation flow.
 *
 * This policy is used by For You (BrowsePersonalRecommendationsScreenModel) so that flow
 * applies the same user-controlled visibility rules as the rest of the recommendations system.
 * // KMK v0.7.43: group-seeded recommendations moved to the row-based RecommendsScreenModel
 * // pipeline, which — like the single-manga Recommendations page it now shares — does not run
 * // candidates through this heavier policy; it only excludes exact seed members (see
 * // RecommendsScreenModel's `seed.memberKeys` filter).
 *
 * The policy does NOT perform network or database work. All context data (tasteByKey,
 * knownIds, etc.) must be pre-loaded by the caller and passed explicitly.
 *
 * Evaluation order mirrors For You's existing filter pipeline:
 *   1. Seed member (group-specific; always excluded regardless of other rules).
 *   2. Favorite (library membership).
 *   3. Rated visibility setting (HIDE_ALL_RATED / HIDE_DISLIKED_ONLY / SHOW_ALL_RATED).
 *   4. Seen (user explicitly suppressed the candidate via swipe/dismiss).
 *   5. Known (in user's library or history, when hide-known-manga is enabled).
 *   6. Min-chapter count (when enabled and local chapter data is available).
 */
internal object RecommendationCandidateVisibilityPolicy {

    /**
     * Evaluates the visibility of [manga] against the given context.
     *
     * @param manga The localized manga to evaluate.
     * @param tasteByKey All user taste entries keyed by (source, url) — used for rated check.
     * @param visibility The user's rated-manga visibility setting.
     * @param seenKeys Set of (sourceId, url) keys the user has dismissed — always excluded.
     * @param knownIds Set of manga IDs in the user's library/history (empty = don't filter known).
     * @param minChapterCount Minimum chapter count threshold (0 = off).
     * @param chapterCounts Map of manga ID → locally-known chapter count (empty = skip check).
     * @param seedMemberKeys Set of (sourceId, url) pairs that are group seed members.
     */
    fun evaluate(
        manga: Manga,
        tasteByKey: Map<MangaTasteKey, MangaTaste>,
        visibility: RatedMangaVisibility,
        seenKeys: Set<SeenMangaKey>,
        knownIds: Set<Long>,
        minChapterCount: Int = 0,
        chapterCounts: Map<Long, Long> = emptyMap(),
        seedMemberKeys: Set<Pair<Long, String>> = emptySet(),
    ): CandidateVisibility {
        if (seedMemberKeys.isNotEmpty() && (manga.source to manga.url) in seedMemberKeys) {
            return CandidateVisibility.HIDDEN_SEED_MEMBER
        }
        if (manga.favorite) return CandidateVisibility.HIDDEN_FAVORITE
        if (shouldHideForYou(manga, tasteByKey, visibility)) return CandidateVisibility.HIDDEN_RATED
        if (SeenMangaKey(manga.source, manga.url) in seenKeys) return CandidateVisibility.HIDDEN_SEEN
        if (knownIds.isNotEmpty() && manga.id in knownIds) return CandidateVisibility.HIDDEN_KNOWN
        if (minChapterCount > 0 && chapterCounts.isNotEmpty()) {
            val count = chapterCounts[manga.id] ?: 0L
            if (count > 0L && count < minChapterCount) return CandidateVisibility.HIDDEN_MIN_CHAPTERS
        }
        return CandidateVisibility.VISIBLE
    }
}
// KMK <--
