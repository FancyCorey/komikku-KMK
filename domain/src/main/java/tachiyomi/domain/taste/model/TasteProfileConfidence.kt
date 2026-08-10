package tachiyomi.domain.taste.model

// KMK -->
/**
 * Evidence quality of the user's taste profile for personalized source evaluation.
 *
 * Derived cheaply from an already-computed [TasteProfile]; no extra DB query needed.
 *
 * [usablePositiveTagCount] — positive-weight learned tags + explicit positive preferences.
 * [usableNegativeTagCount] — negative-weight learned tags + explicit negative preferences + blocked groups.
 * [hasSourceAffinity] — at least one source has net-positive rating signal.
 * [isSufficientForPersonalizedEvaluation] — true when the profile has enough signals for
 *   reliable personalized scoring. When false, scores are still computed but should be
 *   labelled as low-confidence.
 */
data class TasteProfileConfidence(
    val usablePositiveTagCount: Int,
    val usableNegativeTagCount: Int,
    val hasSourceAffinity: Boolean,
) {
    val isSufficientForPersonalizedEvaluation: Boolean
        get() = (usablePositiveTagCount + usableNegativeTagCount) >= 5 && usablePositiveTagCount >= 2

    companion object {
        val EMPTY = TasteProfileConfidence(
            usablePositiveTagCount = 0,
            usableNegativeTagCount = 0,
            hasSourceAffinity = false,
        )

        fun from(profile: TasteProfile): TasteProfileConfidence {
            val posLearned = profile.learnedTagWeights.count { it.value > 0.0 }
            val negLearned = profile.learnedTagWeights.count { it.value < 0.0 }
            val posExplicit = profile.explicitTagPreferences.count { it.value > 0 }
            val negExplicit = profile.explicitTagPreferences.count { it.value < 0 }
            return TasteProfileConfidence(
                usablePositiveTagCount = posLearned + posExplicit,
                usableNegativeTagCount = negLearned + negExplicit + profile.blockedGroups.size,
                hasSourceAffinity = profile.sourceAffinity.any { it.value > 0.0 },
            )
        }
    }
}
// KMK <--
