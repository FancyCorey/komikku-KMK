package tachiyomi.domain.tracker.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.AlternateSourceBridge
import tachiyomi.domain.taste.model.AlternateSourceBridgeKey
import tachiyomi.domain.taste.model.AlternateSourceBridgeMapping
import tachiyomi.domain.taste.model.AlternateSourceBridgeMappingKey
import tachiyomi.domain.taste.model.AlternateSourceBridgeMappingRelation
import tachiyomi.domain.taste.model.AlternateSourceBridgeMappingState
import tachiyomi.domain.taste.model.CrossSourceRecordKey

class LocalTrackedProgressSourceMappingPolicyTest {

    @Test
    fun `confirmed mapping resolves in either bridge orientation`() {
        val bridge = bridge()
        val mapping = mapping(bridge.key)

        assertEquals(
            LocalTrackedProgressSourceMapping(2L, "/target", "/target/ch-12"),
            LocalTrackedProgressSourceMappingPolicy.resolve(
                originSource = 1L,
                originUrl = "/origin",
                originChapterUrl = "/origin/ch-12",
                targetSource = 2L,
                targetUrl = "/target",
                bridges = listOf(bridge),
                mappings = listOf(mapping),
            ),
        )
        assertEquals(
            LocalTrackedProgressSourceMapping(1L, "/origin", "/origin/ch-12"),
            LocalTrackedProgressSourceMappingPolicy.resolve(
                originSource = 2L,
                originUrl = "/target",
                originChapterUrl = "/target/ch-12",
                targetSource = 1L,
                targetUrl = "/origin",
                bridges = listOf(bridge),
                mappings = listOf(mapping),
            ),
        )
    }

    @Test
    fun `provisional conflict and missing target mappings are rejected`() {
        val bridge = bridge()
        val key = bridge.key
        assertNull(
            LocalTrackedProgressSourceMappingPolicy.resolve(
                1L,
                "/origin",
                "/origin/ch-12",
                2L,
                "/target",
                listOf(bridge),
                listOf(mapping(key, state = AlternateSourceBridgeMappingState.PROVISIONAL)),
            ),
        )
        assertNull(
            LocalTrackedProgressSourceMappingPolicy.resolve(
                1L,
                "/origin",
                "/origin/ch-12",
                2L,
                "/target",
                listOf(bridge),
                listOf(mapping(key, alternateChapterUrl = null)),
            ),
        )
    }

    @Test
    fun `ambiguous confirmed mappings are fail closed`() {
        val bridge = bridge()
        val first = mapping(bridge.key)
        val second = first.copy(
            key = AlternateSourceBridgeMappingKey(bridge.key, "00000000-0000-4000-8000-000000000002"),
            alternateChapterUrl = "/target/ch-12-alt",
        )
        assertNull(
            LocalTrackedProgressSourceMappingPolicy.resolve(
                1L,
                "/origin",
                "/origin/ch-12",
                2L,
                "/target",
                listOf(bridge),
                listOf(first, second),
            ),
        )
    }

    private fun bridge() = AlternateSourceBridge(
        key = AlternateSourceBridgeKey(
            CrossSourceRecordKey(1L, "/origin"),
            CrossSourceRecordKey(2L, "/target"),
        ),
        version = 2,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun mapping(
        bridge: AlternateSourceBridgeKey,
        state: AlternateSourceBridgeMappingState = AlternateSourceBridgeMappingState.CONFIRMED,
        alternateChapterUrl: String? = "/target/ch-12",
    ) = AlternateSourceBridgeMapping(
        key = AlternateSourceBridgeMappingKey(bridge, "00000000-0000-4000-8000-000000000001"),
        primaryChapterUrl = "/origin/ch-12",
        alternateChapterUrl = alternateChapterUrl,
        relation = AlternateSourceBridgeMappingRelation.EXACT,
        state = state,
        version = 2,
        createdAt = 1L,
        updatedAt = 1L,
    )
}
