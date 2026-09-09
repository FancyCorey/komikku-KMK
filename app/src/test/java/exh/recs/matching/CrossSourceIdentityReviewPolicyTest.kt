package exh.recs.matching

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.CrossSourceIdentityDecision
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionPolicy
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionValue
import tachiyomi.domain.taste.model.CrossSourceIdentityReasonCode
import tachiyomi.domain.taste.model.CrossSourceIdentityReviewState
import tachiyomi.domain.taste.model.CrossSourceMangaLink
import tachiyomi.domain.taste.model.CrossSourceRecordKey

class CrossSourceIdentityReviewPolicyTest {
    @Test
    fun `needs review takes precedence over stored decision value`() {
        assertEquals(
            CrossSourceIdentityReviewFilter.NEEDS_REVIEW,
            CrossSourceIdentityReviewPolicy.filterFor(decision(review = CrossSourceIdentityReviewState.NEEDS_REVIEW)),
        )
    }

    @Test
    fun `legacy groups produce bounded anchor pairs and omit active decisions`() {
        val links = (1L..5L).map { source -> CrossSourceMangaLink(source, "/$source", "g", "Title", 1, 1) }
        val active = setOf(CrossSourceIdentityDecisionPolicy.canonicalPair(CrossSourceRecordKey(1, "/1"), CrossSourceRecordKey(2, "/2")))
        val result = CrossSourceIdentityReviewPolicy.legacyPairs(links, active, limit = 2)
        assertEquals(2, result.size)
        assertFalse(active.first() in result)
        assertTrue(result.all { it.left == CrossSourceRecordKey(1, "/1") })
    }

    @Test
    fun `confirmed rejected and conflict filters are distinct`() {
        assertEquals(CrossSourceIdentityReviewFilter.CONFIRMED, CrossSourceIdentityReviewPolicy.filterFor(decision()))
        assertEquals(
            CrossSourceIdentityReviewFilter.REJECTED,
            CrossSourceIdentityReviewPolicy.filterFor(decision(CrossSourceIdentityDecisionValue.USER_REJECTED)),
        )
        assertEquals(
            CrossSourceIdentityReviewFilter.NEEDS_REVIEW,
            CrossSourceIdentityReviewPolicy.filterFor(decision(review = CrossSourceIdentityReviewState.NEEDS_REVIEW)),
        )
    }

    @Test
    fun `evaluation mode display contains no manga or source identity`() {
        val display = CrossSourceIdentityReviewPolicy.display(
            evaluationMode = true,
            firstTitle = "Private title A",
            secondTitle = "Private title B",
            firstSource = "Private source A",
            secondSource = "Private source B",
        )

        assertEquals(CrossSourceIdentityReviewDisplay("", "", null, null), display)
    }

    @Test
    fun `normal display preserves available presentation labels`() {
        val display = CrossSourceIdentityReviewPolicy.display(
            evaluationMode = false,
            firstTitle = "Title A",
            secondTitle = null,
            firstSource = "Source A",
            secondSource = null,
        )

        assertEquals(CrossSourceIdentityReviewDisplay("Title A", "", "Source A", null), display)
    }

    private fun decision(
        value: CrossSourceIdentityDecisionValue = CrossSourceIdentityDecisionValue.USER_CONFIRMED,
        review: CrossSourceIdentityReviewState = CrossSourceIdentityReviewState.CURRENT,
    ) = CrossSourceIdentityDecision(
        pair = CrossSourceIdentityDecisionPolicy.canonicalPair(CrossSourceRecordKey(1, "/a"), CrossSourceRecordKey(2, "/b")),
        decision = value,
        decisionVersion = 1,
        evidenceVersion = 1,
        reasonCodes = setOf(
            if (value == CrossSourceIdentityDecisionValue.USER_CONFIRMED) {
                CrossSourceIdentityReasonCode.USER_CONFIRMATION
            } else {
                CrossSourceIdentityReasonCode.USER_REJECTION
            },
        ),
        reviewState = review,
        createdAt = 1,
        updatedAt = 2,
    )
}
