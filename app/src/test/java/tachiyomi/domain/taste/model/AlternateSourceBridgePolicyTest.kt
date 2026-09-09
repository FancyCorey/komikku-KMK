package tachiyomi.domain.taste.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlternateSourceBridgePolicyTest {

    @Test
    fun `confirmed offset is only a hint and cannot resolve a target`() {
        val route = bridge(offsetMilli = 1_000, offsetState = AlternateSourceBridgeEvidenceState.CONFIRMED)

        assertEquals(1_000L, AlternateSourceBridgePolicy.confirmedOffsetHint(route))
        assertEquals(
            AlternateSourceBridgeTargetResolution.Unavailable,
            AlternateSourceBridgePolicy.resolveTarget(route, emptyList(), BRIDGE_TARGET_ID),
        )
    }

    @Test
    fun `automatic return requires both exact boundary chapter urls`() {
        val route = bridge(
            continuationUrl = "/chapter/next",
            returnAfterUrl = "/chapter/alternate-end",
            automaticReturn = true,
        )

        assertTrue(
            AlternateSourceBridgePolicy.canAutomaticallyReturn(
                route,
                "/chapter/alternate-end",
                "/chapter/next",
            ),
        )
        assertFalse(
            AlternateSourceBridgePolicy.canAutomaticallyReturn(
                route,
                "/chapter/alternate-nearby",
                "/chapter/next",
            ),
        )
        assertFalse(
            AlternateSourceBridgePolicy.canAutomaticallyReturn(
                route,
                "/chapter/alternate-end",
                "/chapter/nearby",
            ),
        )
        assertFalse(AlternateSourceBridgePolicy.isValid(bridge(automaticReturn = true), 10_000))
    }

    @Test
    fun `missing target resolves only from the exact ordered gap anchors`() {
        val route = bridge()
        val mapping = bridgeMapping()

        assertEquals(
            "/chapter/alternate",
            (
                AlternateSourceBridgePolicy.resolveAnchoredGapTarget(
                    route,
                    listOf(mapping),
                    "/chapter/before",
                    "/chapter/after",
                ) as AlternateSourceBridgeTargetResolution.Target
                ).alternateChapterUrl,
        )
        assertEquals(
            AlternateSourceBridgeTargetResolution.Unavailable,
            AlternateSourceBridgePolicy.resolveAnchoredGapTarget(
                route,
                listOf(mapping),
                "/chapter/after",
                "/chapter/before",
            ),
        )
        assertEquals(
            AlternateSourceBridgeTargetResolution.Unavailable,
            AlternateSourceBridgePolicy.resolveAnchoredGapTarget(route, listOf(mapping), null, null),
        )
    }

    @Test
    fun `legacy missing target remains readable but cannot resolve an anchored gap`() {
        val legacy = bridgeMapping().copy(
            version = AlternateSourceBridgePolicy.LEGACY_VERSION,
            precedingPrimaryChapterUrl = null,
            followingPrimaryChapterUrl = null,
        )

        assertTrue(AlternateSourceBridgePolicy.isValid(legacy, 10_000))
        assertEquals(
            AlternateSourceBridgeTargetResolution.Unavailable,
            AlternateSourceBridgePolicy.resolveAnchoredGapTarget(
                bridge(),
                listOf(legacy),
                "/chapter/before",
                "/chapter/after",
            ),
        )
    }

    @Test
    fun `new missing targets require at least one exact primary anchor`() {
        assertFalse(
            AlternateSourceBridgePolicy.isValid(
                bridgeMapping(precedingPrimaryUrl = null, followingPrimaryUrl = null),
                10_000,
            ),
        )
    }

    @Test
    fun `anchor conflicts are projected within one role ordered bridge only`() {
        val first = bridgeMapping()
        val conflicting = bridgeMapping(
            targetId = "223e4567-e89b-42d3-a456-426614174000",
            alternateUrl = "/chapter/other",
        )
        assertTrue(
            AlternateSourceBridgePolicy.projectConflicts(listOf(first, conflicting))
                .all { it.state == AlternateSourceBridgeMappingState.CONFLICT },
        )

        val unrelated = conflicting.copy(
            key = conflicting.key.copy(
                bridge = conflicting.key.bridge.copy(alternate = CrossSourceRecordKey(3, "/other-alternate")),
            ),
        )
        assertTrue(
            AlternateSourceBridgePolicy.projectConflicts(listOf(first, unrelated))
                .all { it.state != AlternateSourceBridgeMappingState.CONFLICT },
        )
    }

    @Test
    fun `exact mapping resolves while skipped and conflicted mappings remain explicit`() {
        val route = bridge()
        val exact = bridgeMapping(
            primaryUrl = "/chapter/primary",
            relation = AlternateSourceBridgeMappingRelation.EXACT,
        )
        val target = AlternateSourceBridgePolicy.resolveTarget(route, listOf(exact), BRIDGE_TARGET_ID)
        assertEquals("/chapter/alternate", (target as AlternateSourceBridgeTargetResolution.Target).alternateChapterUrl)

        val skipped = exact.copy(relation = AlternateSourceBridgeMappingRelation.ALTERNATE_SKIPPED)
        assertEquals(
            AlternateSourceBridgeTargetResolution.Skipped,
            AlternateSourceBridgePolicy.resolveTarget(route, listOf(skipped), BRIDGE_TARGET_ID),
        )
        val conflict = exact.copy(state = AlternateSourceBridgeMappingState.CONFLICT)
        assertEquals(
            AlternateSourceBridgeTargetResolution.Conflict,
            AlternateSourceBridgePolicy.resolveTarget(route, listOf(conflict), BRIDGE_TARGET_ID),
        )
    }

    @Test
    fun `duplicate alternate targets project conflicts without changing duplicate relations`() {
        val first = bridgeMapping(primaryUrl = "/p/1", relation = AlternateSourceBridgeMappingRelation.EXACT)
        val second = bridgeMapping(
            targetId = "223e4567-e89b-42d3-a456-426614174000",
            primaryUrl = "/p/2",
            relation = AlternateSourceBridgeMappingRelation.EXACT,
        )
        val projected = AlternateSourceBridgePolicy.projectConflicts(listOf(first, second))
        assertTrue(projected.all { it.state == AlternateSourceBridgeMappingState.CONFLICT })

        val duplicate = second.copy(relation = AlternateSourceBridgeMappingRelation.DUPLICATE)
        assertTrue(
            AlternateSourceBridgePolicy.projectConflicts(listOf(first, duplicate))
                .all { it.state != AlternateSourceBridgeMappingState.CONFLICT },
        )
    }

    @Test
    fun `equal timestamp disagreement becomes deterministic conflict and tombstone wins`() {
        val left = bridge(offsetMilli = 1_000, offsetState = AlternateSourceBridgeEvidenceState.CONFIRMED)
        val right = bridge(offsetMilli = 2_000, offsetState = AlternateSourceBridgeEvidenceState.CONFIRMED)
        val conflict = AlternateSourceBridgePolicy.merge(left, right)
        assertEquals(AlternateSourceBridgeReviewState.CONFLICT, conflict.reviewState)
        assertFalse(conflict.automaticReturn)

        val tombstone = AlternateSourceBridgePolicy.tombstone(left, left.updatedAt)
        assertEquals(tombstone, AlternateSourceBridgePolicy.merge(left, tombstone))
    }

    @Test
    fun `identity confirmation authorizes only the exact pair and supplies no mapping`() {
        val decision = CrossSourceIdentityDecision(
            pair = CrossSourceIdentityDecisionPolicy.canonicalPair(bridgeKey().primary, bridgeKey().alternate),
            decision = CrossSourceIdentityDecisionValue.USER_CONFIRMED,
            decisionVersion = 1,
            evidenceVersion = 1,
            reasonCodes = setOf(CrossSourceIdentityReasonCode.USER_CONFIRMATION),
            reviewState = CrossSourceIdentityReviewState.CURRENT,
            createdAt = 1_000,
            updatedAt = 2_000,
        )
        assertTrue(AlternateSourceBridgePolicy.isIdentityAuthorizedPair(bridgeKey(), decision))
        assertFalse(
            AlternateSourceBridgePolicy.isIdentityAuthorizedPair(
                bridgeKey().copy(alternate = CrossSourceRecordKey(3, "/other")),
                decision,
            ),
        )
        assertNull(AlternateSourceBridgePolicy.confirmedOffsetHint(bridge()))
    }
}
