package exh.recs.matching

import tachiyomi.domain.taste.model.CrossSourceIdentityDecision
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionPolicy
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionValue
import tachiyomi.domain.taste.model.CrossSourceIdentityPair
import tachiyomi.domain.taste.model.CrossSourceIdentityReviewState
import tachiyomi.domain.taste.model.CrossSourceMangaLink
import tachiyomi.domain.taste.model.CrossSourceRecordKey

data class CrossSourceIdentityReviewDisplay(
    val firstTitle: String,
    val secondTitle: String,
    val firstSource: String?,
    val secondSource: String?,
)

object CrossSourceIdentityReviewPolicy {
    fun filterFor(decision: CrossSourceIdentityDecision): CrossSourceIdentityReviewFilter = when {
        decision.reviewState == CrossSourceIdentityReviewState.NEEDS_REVIEW ->
            CrossSourceIdentityReviewFilter.NEEDS_REVIEW
        decision.decision == CrossSourceIdentityDecisionValue.USER_CONFIRMED ->
            CrossSourceIdentityReviewFilter.CONFIRMED
        else -> CrossSourceIdentityReviewFilter.REJECTED
    }

    fun legacyPairs(
        links: List<CrossSourceMangaLink>,
        activePairs: Set<CrossSourceIdentityPair>,
        limit: Int,
    ): List<CrossSourceIdentityPair> {
        require(limit > 0)
        return links.groupBy { it.groupId }.values.asSequence().flatMap { group ->
            val keys = group.map { CrossSourceRecordKey(it.source, it.url) }
                .distinct()
                .sortedWith(compareBy<CrossSourceRecordKey> { it.source }.thenBy { it.url })
            val anchor = keys.firstOrNull()
            if (anchor == null) {
                emptySequence()
            } else {
                keys.drop(1).asSequence().map { CrossSourceIdentityDecisionPolicy.canonicalPair(anchor, it) }
            }
        }.filterNot { it in activePairs }.distinct().take(limit).toList()
    }

    fun display(
        evaluationMode: Boolean,
        firstTitle: String?,
        secondTitle: String?,
        firstSource: String?,
        secondSource: String?,
    ): CrossSourceIdentityReviewDisplay = if (evaluationMode) {
        CrossSourceIdentityReviewDisplay("", "", null, null)
    } else {
        CrossSourceIdentityReviewDisplay(
            firstTitle.orEmpty(),
            secondTitle.orEmpty(),
            firstSource,
            secondSource,
        )
    }
}
