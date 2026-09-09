package eu.kanade.tachiyomi.ui.reader.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.AlternateSourceBridgeMappingRelation
import tachiyomi.domain.taste.model.AlternateSourceBridgeMappingState
import tachiyomi.domain.taste.model.AlternateSourceBridgePolicy
import tachiyomi.domain.taste.model.AlternateSourceBridgeReviewState
import tachiyomi.domain.taste.model.AlternateSourceBridgeState
import tachiyomi.domain.taste.model.bridge
import tachiyomi.domain.taste.model.bridgeMapping

class AlternateSourceReaderBridgePolicyTest {

    @Test
    fun `entry requires exact current anchors and distinguishes provisional evidence`() {
        val route = bridge().copy(key = readerBridgeKey())
        val confirmed = bridgeMapping(targetId = READER_TARGET_ID).let {
            it.copy(key = it.key.copy(bridge = readerBridgeKey()))
        }
        val state = AlternateSourceBridgeState(route, listOf(confirmed))

        assertEquals(
            AlternateSourceReaderBridgePolicy.EntryDecision.Confirmed(
                READER_TARGET_ID,
                "/chapter/alternate",
            ),
            AlternateSourceReaderBridgePolicy.resolveEntry(
                state,
                "/chapter/before",
                "/chapter/after",
                10_000L,
            ),
        )
        assertEquals(
            AlternateSourceReaderBridgePolicy.EntryDecision.Unavailable,
            AlternateSourceReaderBridgePolicy.resolveEntry(
                state,
                "/chapter/after",
                "/chapter/before",
                10_000L,
            ),
        )
        assertEquals(
            AlternateSourceReaderBridgePolicy.EntryDecision.RequiresConfirmation(
                READER_TARGET_ID,
                "/chapter/alternate",
            ),
            AlternateSourceReaderBridgePolicy.resolveEntry(
                state.copy(mappings = listOf(confirmed.copy(state = AlternateSourceBridgeMappingState.PROVISIONAL))),
                "/chapter/before",
                "/chapter/after",
                10_000L,
            ),
        )
    }

    @Test
    fun `legacy and conflicting mappings cannot authorize entry`() {
        val route = bridge().copy(key = readerBridgeKey())
        val mapping = bridgeMapping(targetId = READER_TARGET_ID).let {
            it.copy(key = it.key.copy(bridge = readerBridgeKey()))
        }
        val legacy = mapping.copy(
            version = AlternateSourceBridgePolicy.LEGACY_VERSION,
            precedingPrimaryChapterUrl = null,
            followingPrimaryChapterUrl = null,
        )
        assertEquals(
            AlternateSourceReaderBridgePolicy.EntryDecision.Unavailable,
            AlternateSourceReaderBridgePolicy.resolveEntry(
                AlternateSourceBridgeState(route, listOf(legacy)),
                "/chapter/before",
                "/chapter/after",
                10_000L,
            ),
        )
        assertEquals(
            AlternateSourceReaderBridgePolicy.EntryDecision.Unavailable,
            AlternateSourceReaderBridgePolicy.resolveEntry(
                AlternateSourceBridgeState(
                    route.copy(version = AlternateSourceBridgePolicy.LEGACY_VERSION),
                    listOf(mapping),
                ),
                "/chapter/before",
                "/chapter/after",
                10_000L,
            ),
        )
        assertEquals(
            AlternateSourceReaderBridgePolicy.EntryDecision.Conflict,
            AlternateSourceReaderBridgePolicy.resolveEntry(
                AlternateSourceBridgeState(
                    route.copy(reviewState = AlternateSourceBridgeReviewState.CONFLICT),
                    listOf(mapping),
                ),
                "/chapter/before",
                "/chapter/after",
                10_000L,
            ),
        )
    }

    @Test
    fun `automatic return requires current timestamps confirmed mapping and exact completion boundary`() {
        val route = bridge(
            continuationUrl = "/chapter/primary-next",
            returnAfterUrl = "/chapter/alternate",
            automaticReturn = true,
        ).copy(key = readerBridgeKey())
        val mapping = bridgeMapping(targetId = READER_TARGET_ID, updatedAt = 2_100L).let {
            it.copy(key = it.key.copy(bridge = readerBridgeKey()))
        }
        val state = AlternateSourceBridgeState(route, listOf(mapping))
        val session = readerSession()

        assertEquals(
            AlternateSourceReaderBridgePolicy.AutomaticReturnDecision.Allowed("/chapter/primary-next"),
            AlternateSourceReaderBridgePolicy.automaticReturn(
                state,
                session,
                "/chapter/alternate",
                10_000L,
            ),
        )
        assertEquals(
            AlternateSourceReaderBridgePolicy.AutomaticReturnDecision.WrongBoundary,
            AlternateSourceReaderBridgePolicy.automaticReturn(
                state,
                session,
                "/chapter/nearby",
                10_000L,
            ),
        )
        assertEquals(
            AlternateSourceReaderBridgePolicy.AutomaticReturnDecision.Provisional,
            AlternateSourceReaderBridgePolicy.automaticReturn(
                state.copy(mappings = listOf(mapping.copy(state = AlternateSourceBridgeMappingState.PROVISIONAL))),
                session,
                "/chapter/alternate",
                10_000L,
            ),
        )
    }

    @Test
    fun `timestamp drift disables restored session automation`() {
        val route = bridge(
            continuationUrl = "/chapter/primary-next",
            returnAfterUrl = "/chapter/alternate",
            automaticReturn = true,
        ).copy(key = readerBridgeKey())
        val mapping = bridgeMapping(targetId = READER_TARGET_ID, updatedAt = 2_100L).let {
            it.copy(key = it.key.copy(bridge = readerBridgeKey()))
        }
        val state = AlternateSourceBridgeState(route, listOf(mapping))

        assertEquals(
            AlternateSourceReaderBridgePolicy.AutomaticReturnDecision.Stale,
            AlternateSourceReaderBridgePolicy.automaticReturn(
                state.copy(bridge = route.copy(updatedAt = 2_001L)),
                readerSession(),
                "/chapter/alternate",
                10_000L,
            ),
        )
        assertEquals(
            AlternateSourceReaderBridgePolicy.AutomaticReturnDecision.Stale,
            AlternateSourceReaderBridgePolicy.automaticReturn(
                state.copy(mappings = listOf(mapping.copy(updatedAt = 2_101L))),
                readerSession(),
                "/chapter/alternate",
                10_000L,
            ),
        )
    }

    @Test
    fun `automatic return rejects legacy and unrelated mapping evidence`() {
        val route = bridge(
            continuationUrl = "/chapter/primary-next",
            returnAfterUrl = "/chapter/alternate",
            automaticReturn = true,
        ).copy(key = readerBridgeKey())
        val mapping = bridgeMapping(targetId = READER_TARGET_ID, updatedAt = 2_100L).let {
            it.copy(key = it.key.copy(bridge = readerBridgeKey()))
        }
        val session = readerSession()

        val legacyMapping = mapping.copy(
            version = AlternateSourceBridgePolicy.LEGACY_VERSION,
            precedingPrimaryChapterUrl = null,
            followingPrimaryChapterUrl = null,
        )
        assertEquals(
            AlternateSourceReaderBridgePolicy.AutomaticReturnDecision.Stale,
            AlternateSourceReaderBridgePolicy.automaticReturn(
                AlternateSourceBridgeState(route, listOf(legacyMapping)),
                session,
                "/chapter/alternate",
                10_000L,
            ),
        )
        assertEquals(
            AlternateSourceReaderBridgePolicy.AutomaticReturnDecision.Stale,
            AlternateSourceReaderBridgePolicy.automaticReturn(
                AlternateSourceBridgeState(
                    route,
                    listOf(
                        mapping.copy(
                            relation = AlternateSourceBridgeMappingRelation.EXACT,
                            primaryChapterUrl = "/chapter/primary",
                            precedingPrimaryChapterUrl = null,
                            followingPrimaryChapterUrl = null,
                        ),
                    ),
                ),
                session,
                "/chapter/alternate",
                10_000L,
            ),
        )
    }
}
