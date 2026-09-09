package eu.kanade.tachiyomi.ui.reader.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.AlternateSourceBridge
import tachiyomi.domain.taste.model.AlternateSourceBridgeMapping
import tachiyomi.domain.taste.model.AlternateSourceBridgeMappingKey
import tachiyomi.domain.taste.model.AlternateSourceBridgeMappingRelation
import tachiyomi.domain.taste.model.AlternateSourceBridgeMappingState
import tachiyomi.domain.taste.model.AlternateSourceBridgePolicy
import tachiyomi.domain.taste.model.AlternateSourceBridgeReviewState
import tachiyomi.domain.taste.model.AlternateSourceBridgeState

class AlternateSourceReaderMutationPolicyTest {

    @Test
    fun `selection creates exact provisional anchors and paired return boundary`() {
        val result = AlternateSourceReaderMutationPolicy.select(
            current = null,
            key = readerBridgeKey(),
            precedingPrimaryChapterUrl = "/chapter/primary",
            followingPrimaryChapterUrl = "/chapter/after",
            alternateChapterUrl = "/chapter/alternate",
            targetId = READER_TARGET_ID,
            now = 3_000L,
        ) as AlternateSourceReaderMutationPolicy.Result.Replacement

        val bridge = result.value.replacementBridge!!
        val mapping = result.value.mappingReplacements.single().replacement!!
        assertEquals("/chapter/after", bridge.continuationPrimaryChapterUrl)
        assertEquals("/chapter/alternate", bridge.returnAfterAlternateChapterUrl)
        assertFalse(bridge.automaticReturn)
        assertEquals(AlternateSourceBridgeMappingRelation.PRIMARY_MISSING, mapping.relation)
        assertEquals(AlternateSourceBridgeMappingState.PROVISIONAL, mapping.state)
        assertEquals("/chapter/primary", mapping.precedingPrimaryChapterUrl)
        assertEquals("/chapter/after", mapping.followingPrimaryChapterUrl)
        assertTrue(AlternateSourceBridgePolicy.isValid(bridge, 3_000L))
        assertTrue(AlternateSourceBridgePolicy.isValid(mapping, 3_000L))
    }

    @Test
    fun `selection accepts an end of source gap with only a preceding anchor`() {
        val result = AlternateSourceReaderMutationPolicy.select(
            current = null,
            key = readerBridgeKey(),
            precedingPrimaryChapterUrl = "/chapter/primary",
            followingPrimaryChapterUrl = null,
            alternateChapterUrl = "/chapter/alternate",
            targetId = READER_TARGET_ID,
            now = 3_000L,
        ) as AlternateSourceReaderMutationPolicy.Result.Replacement

        val bridge = result.value.replacementBridge!!
        val mapping = result.value.mappingReplacements.single().replacement!!
        assertEquals(null, bridge.continuationPrimaryChapterUrl)
        assertEquals("/chapter/alternate", bridge.returnAfterAlternateChapterUrl)
        assertEquals("/chapter/primary", mapping.precedingPrimaryChapterUrl)
        assertEquals(null, mapping.followingPrimaryChapterUrl)
        assertTrue(AlternateSourceBridgePolicy.isValid(bridge, 3_000L))
        assertTrue(AlternateSourceBridgePolicy.isValid(mapping, 3_000L))
    }

    @Test
    fun `selection replaces one existing anchored target when correcting the source`() {
        val result = AlternateSourceReaderMutationPolicy.select(
            current = bridgeState(),
            key = readerBridgeKey(),
            precedingPrimaryChapterUrl = "/chapter/primary",
            followingPrimaryChapterUrl = "/chapter/after",
            alternateChapterUrl = "/chapter/other",
            targetId = "323e4567-e89b-42d3-a456-426614174000",
            now = 10_000L,
        ) as AlternateSourceReaderMutationPolicy.Result.Replacement

        assertEquals(2, result.value.mappingReplacements.size)
        assertNull(result.value.mappingReplacements.first().replacement)
        assertEquals("/chapter/other", result.value.mappingReplacements.last().replacement?.alternateChapterUrl)
    }

    @Test
    fun `correction confirms exact target and updates the paired boundary atomically`() {
        val result = AlternateSourceReaderMutationPolicy.correct(
            current = bridgeState(mappingState = AlternateSourceBridgeMappingState.PROVISIONAL),
            targetId = READER_TARGET_ID,
            alternateChapterUrl = "/chapter/corrected",
            now = 10_000L,
        ) as AlternateSourceReaderMutationPolicy.Result.Replacement

        val bridge = result.value.replacementBridge!!
        val mapping = result.value.mappingReplacements.single().replacement!!
        assertEquals("/chapter/corrected", bridge.returnAfterAlternateChapterUrl)
        assertEquals("/chapter/corrected", mapping.alternateChapterUrl)
        assertEquals(AlternateSourceBridgeMappingState.CONFIRMED, mapping.state)
        assertEquals(result.value.expectedBridge, bridgeState().bridge)
    }

    @Test
    fun `skip preserves readable identity but removes gap anchors and disables automatic return`() {
        val result = AlternateSourceReaderMutationPolicy.skip(
            current = bridgeState(automaticReturn = true),
            targetId = READER_TARGET_ID,
            alternateChapterUrl = "/chapter/alternate",
            now = 10_000L,
        ) as AlternateSourceReaderMutationPolicy.Result.Replacement

        val bridge = result.value.replacementBridge!!
        val mapping = result.value.mappingReplacements.single().replacement!!
        assertFalse(bridge.automaticReturn)
        assertNull(bridge.returnAfterAlternateChapterUrl)
        assertEquals(AlternateSourceBridgeMappingRelation.ALTERNATE_SKIPPED, mapping.relation)
        assertEquals("/chapter/alternate", mapping.alternateChapterUrl)
        assertNull(mapping.precedingPrimaryChapterUrl)
        assertNull(mapping.followingPrimaryChapterUrl)
    }

    @Test
    fun `projected duplicate conflict cannot be corrected or skipped`() {
        val first = bridgeMapping()
        val second = bridgeMapping(
            targetId = "323e4567-e89b-42d3-a456-426614174000",
            preceding = "/chapter/other-before",
            following = "/chapter/other-after",
        )
        val conflict = bridgeState().copy(mappings = listOf(first, second))

        assertEquals(
            AlternateSourceReaderMutationPolicy.Result.Conflict,
            AlternateSourceReaderMutationPolicy.correct(conflict, READER_TARGET_ID, "/chapter/alternate", 10_000L),
        )
        assertEquals(
            AlternateSourceReaderMutationPolicy.Result.Conflict,
            AlternateSourceReaderMutationPolicy.skip(conflict, READER_TARGET_ID, "/chapter/alternate", 10_000L),
        )
    }

    private fun bridgeState(
        mappingState: AlternateSourceBridgeMappingState = AlternateSourceBridgeMappingState.CONFIRMED,
        automaticReturn: Boolean = false,
    ) = AlternateSourceBridgeState(
        bridge = AlternateSourceBridge(
            key = readerBridgeKey(),
            version = AlternateSourceBridgePolicy.CURRENT_VERSION,
            continuationPrimaryChapterUrl = "/chapter/after",
            returnAfterAlternateChapterUrl = "/chapter/alternate",
            automaticReturn = automaticReturn,
            reviewState = AlternateSourceBridgeReviewState.CURRENT,
            createdAt = 1_000L,
            updatedAt = 2_000L,
        ),
        mappings = listOf(bridgeMapping(state = mappingState)),
    )

    private fun bridgeMapping(
        targetId: String = READER_TARGET_ID,
        preceding: String = "/chapter/primary",
        following: String = "/chapter/after",
        state: AlternateSourceBridgeMappingState = AlternateSourceBridgeMappingState.CONFIRMED,
    ) = AlternateSourceBridgeMapping(
        key = AlternateSourceBridgeMappingKey(readerBridgeKey(), targetId),
        alternateChapterUrl = "/chapter/alternate",
        precedingPrimaryChapterUrl = preceding,
        followingPrimaryChapterUrl = following,
        relation = AlternateSourceBridgeMappingRelation.PRIMARY_MISSING,
        state = state,
        version = AlternateSourceBridgePolicy.CURRENT_VERSION,
        createdAt = 1_000L,
        updatedAt = 2_100L,
    )
}
