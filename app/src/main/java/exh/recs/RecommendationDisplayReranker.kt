package exh.recs

// KMK
/**
 * Pure, deterministic, **display-only** reorder applied to one source's already-accepted candidate
 * list.
 *
 * ## Hard invariants (each has a direct test)
 *
 * 1. **Permutation, never deletion.** The output always contains exactly the same candidates as the
 *    input, in the same multiplicity. Nothing is removed, hidden, or blocked. A hard-filtered
 *    candidate never re-enters here because this runs *after* the visibility policy.
 * 2. **Base score is never mutated.** [PersonalRecommendation.score] is read but never written. The
 *    exposure penalty exists only inside this comparator.
 * 3. **A strong personalized match cannot be displaced by novelty alone.** The penalty is capped by
 *    [RecommendationExposurePolicy.MAX_PENALTY] and applies only to candidates that are *both*
 *    repeatedly exposed *and* untouched, so a genuinely better-scored candidate keeps its position
 *    whenever the score gap exceeds that cap.
 * 4. **Deterministic and jitter-free.** Ties fall back to the candidate's original index, so two
 *    identical refreshes with identical exposure data produce byte-identical ordering. No wall clock
 *    is read inside the comparator -- `now` is supplied once by the caller.
 * 5. **Fails open.** An empty exposure map, a missing entry, or a malformed timestamp yields a zero
 *    penalty, which makes the reorder an identity permutation.
 * 6. **Positive interaction is always exempt.** A candidate that is in the library, rated, or tracked
 *    is never penalised, regardless of how many times it has been shown. "Ignored" is never converted
 *    into "disliked".
 *
 * This is deliberately applied per source row, *before* the row cap and Top Picks accumulation, so
 * source/topic sections, per-source quotas, and the existing result budget all remain valid: the row
 * still shows the same number of cards from the same source, just in a less repetitive order.
 */
object RecommendationDisplayReranker {

    /**
     * Stable candidate identity: the **source id plus the manga url**, never the url alone.
     *
     * Two different sources routinely expose the same relative url (`/manga/one-piece`), and
     * [RecommendationCandidateMemoryRanker][exh.recs.memory.RecommendationCandidateMemoryRanker]'s
     * merge deliberately mixes freshly-searched candidates with *remembered* candidates that carry
     * their own originating source id. Keying exposure by url alone would therefore let one source's
     * exposure history silently penalise a different source's candidate. This type exists so that
     * collision is impossible by construction rather than by convention.
     */
    data class ExposureKey(val sourceId: Long, val url: String)

    /**
     * One candidate's local exposure facts, as loaded once per refresh by the caller.
     * Contains no raw title, url, or query text -- identity is the caller's [ExposureKey].
     */
    data class ExposureSummary(
        val lastExposedAt: Long,
        val exposureCount: Int,
        val lastInteractionAt: Long?,
    )

    /**
     * Tracker state for one refresh's candidates.
     *
     * KMK: this is a **tri-state**, not a set, because a bare
     * `Set<ExposureKey>` cannot distinguish the two cases that must behave differently:
     *
     * - `Known(emptySet())` — the tracker table was read successfully and **no** candidate is
     *   tracked. Exposure reranking may proceed normally.
     * - [Unknown] — the tracker state could not be determined (lookup failed, or was never
     *   performed). Treating this as "known untracked" would let a title the user actively tracks be
     *   repeatedly demoted, which is precisely the "ignored is not dislike" violation this whole
     *   feature is built to avoid.
     *
     * The previous implementation collapsed both cases to an empty set, so a failed tracker query
     * silently became "nothing is tracked" and could penalise a tracked title. That was the defect.
     */
    sealed interface TrackedState {
        /** Tracker state was resolved. [keys] is the complete set of tracked candidates. */
        data class Known(val keys: Set<ExposureKey>) : TrackedState

        /** Tracker state is unavailable. No candidate may receive an exposure penalty. */
        data object Unknown : TrackedState
    }

    /**
     * The three independent positive-interaction signals that exempt a candidate from any penalty.
     *
     * They are kept separate rather than pre-unioned into one set so each exemption is independently
     * testable, and so [tracked]'s availability can be represented honestly (see [TrackedState])
     * without silently disabling or weakening [library] and [rated].
     */
    data class InteractionSignals(
        val library: Set<ExposureKey> = emptySet(),
        val rated: Set<ExposureKey> = emptySet(),
        /**
         * Defaults to [TrackedState.Unknown] deliberately: a caller that forgets to resolve tracker
         * state gets the *safe* behavior (no penalties) rather than the unsafe one.
         */
        val tracked: TrackedState = TrackedState.Unknown,
    ) {
        companion object {
            /**
             * No candidate carries any positive-interaction signal, **and tracker state was
             * positively resolved as empty**. Use only where a tracker lookup genuinely succeeded.
             */
            val NONE = InteractionSignals(emptySet(), emptySet(), TrackedState.Known(emptySet()))

            /**
             * Tracker state could not be determined. Reranking becomes an identity permutation --
             * nothing is reordered, nothing is hidden, nothing is down-rated.
             */
            val TRACKER_UNAVAILABLE = InteractionSignals(emptySet(), emptySet(), TrackedState.Unknown)
        }
    }

    /**
     * Reorders [candidates] using [exposureByKey].
     *
     * @param candidates the accepted, scored candidates for one row, in their current display order.
     * @param exposureByKey exposure summaries keyed by [ExposureKey].
     * @param interactions per-signal positive-interaction sets. Never penalised, per the
     * "ignored is not dislike" contract.
     * @param now the refresh's single wall-clock reading.
     * @param windowDays the configured exposure window (validated internally).
     * @param keyOf resolves a candidate's identity. Defaults to `(manga.source, manga.url)`, which is
     * the same identity the exposure table is written with.
     */
    fun rerank(
        candidates: List<PersonalRecommendation>,
        exposureByKey: Map<ExposureKey, ExposureSummary>,
        interactions: InteractionSignals,
        now: Long,
        windowDays: Int,
        keyOf: (PersonalRecommendation) -> ExposureKey = { ExposureKey(it.manga.source, it.manga.url) },
    ): List<PersonalRecommendation> {
        if (candidates.size < 2 || exposureByKey.isEmpty()) return candidates

        // KMK: tracker state could not be determined for this
        // batch. Any penalty applied here might land on a title the user actively tracks, so no
        // candidate may be penalised. Returning the input unchanged is still a valid permutation --
        // nothing is removed, reordered, hidden, or down-rated.
        val trackedKeys = when (val tracked = interactions.tracked) {
            is TrackedState.Unknown -> return candidates
            is TrackedState.Known -> tracked.keys
        }

        val penalties = HashMap<ExposureKey, Double>(candidates.size)
        for (candidate in candidates) {
            val key = keyOf(candidate)
            if (penalties.containsKey(key)) continue
            val summary = exposureByKey[key]
            penalties[key] = if (summary == null) {
                0.0
            } else {
                RecommendationExposurePolicy.softPenalty(
                    now = now,
                    lastExposedAt = summary.lastExposedAt,
                    exposureCount = summary.exposureCount,
                    windowDays = windowDays,
                    isUntouched = RecommendationExposurePolicy.isUntouched(
                        isInLibrary = key in interactions.library,
                        isRated = key in interactions.rated,
                        isTracked = key in trackedKeys,
                        lastInteractionAt = summary.lastInteractionAt,
                    ),
                )
            }
        }

        // No candidate earned a penalty -- identity permutation, and no allocation-heavy sort.
        if (penalties.values.none { it > 0.0 }) return candidates

        return candidates
            .withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<PersonalRecommendation>> {
                    it.value.score - (penalties[keyOf(it.value)] ?: 0.0)
                }.thenBy { it.index },
            )
            .map { it.value }
    }

    /**
     * True when [reranked] is a valid permutation of [original] -- same size and same candidate
     * identities in the same multiplicity. Exposed so callers and tests can assert invariant 1
     * directly rather than trusting the sort.
     */
    fun isPermutationOf(original: List<PersonalRecommendation>, reranked: List<PersonalRecommendation>): Boolean =
        original.size == reranked.size &&
            original.groupingBy { ExposureKey(it.manga.source, it.manga.url) }.eachCount() ==
            reranked.groupingBy { ExposureKey(it.manga.source, it.manga.url) }.eachCount()
}
// KMK <--
