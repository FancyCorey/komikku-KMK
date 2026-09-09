package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupAlternateSourceBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.AlternateSourceBridge
import tachiyomi.domain.taste.model.AlternateSourceBridgeMapping
import tachiyomi.domain.taste.model.AlternateSourceBridgePolicy
import tachiyomi.domain.taste.model.AlternateSourceBridgeState
import tachiyomi.domain.taste.model.AlternateSourceBridgeStateReplacement
import tachiyomi.domain.taste.model.AlternateSourceBridgeTargetResolution
import tachiyomi.domain.taste.model.bridge
import tachiyomi.domain.taste.model.bridgeMapping
import tachiyomi.domain.taste.repository.AlternateSourceBridgeRepository

class AlternateSourceBridgeBackupPolicyTest {

    @Test
    fun `encode decode round trip preserves opaque identities and bridge state`() {
        val route = bridge(
            offsetMilli = 1_000,
            offsetState = tachiyomi.domain.taste.model.AlternateSourceBridgeEvidenceState.CONFIRMED,
            continuationUrl = "/chapter/next",
            returnAfterUrl = "/chapter/alternate-end",
            automaticReturn = true,
        )
        val mapping = bridgeMapping()

        assertEquals(route, AlternateSourceBridgeBackupPolicy.decode(AlternateSourceBridgeBackupPolicy.encode(route), 10_000))
        assertEquals(mapping, AlternateSourceBridgeBackupPolicy.decode(AlternateSourceBridgeBackupPolicy.encode(mapping), 10_000))
    }

    @Test
    fun `legacy backup rows remain readable but cannot place a gap or automatically return`() {
        val legacyRoute = AlternateSourceBridgeBackupPolicy.encode(bridge()).copy(
            version = AlternateSourceBridgePolicy.LEGACY_VERSION,
            continuationPrimaryChapterUrl = "/chapter/next",
            automaticReturn = true,
            returnAfterAlternateChapterUrl = "",
        )
        val legacyMapping = AlternateSourceBridgeBackupPolicy.encode(bridgeMapping()).copy(
            version = AlternateSourceBridgePolicy.LEGACY_VERSION,
            precedingPrimaryChapterUrl = "",
            followingPrimaryChapterUrl = "",
        )

        val route = requireNotNull(AlternateSourceBridgeBackupPolicy.decode(legacyRoute, 10_000))
        val mapping = requireNotNull(AlternateSourceBridgeBackupPolicy.decode(legacyMapping, 10_000))

        assertTrue(!route.automaticReturn)
        assertEquals(
            AlternateSourceBridgeTargetResolution.Unavailable,
            AlternateSourceBridgePolicy.resolveAnchoredGapTarget(
                route,
                listOf(mapping),
                "/chapter/before",
                "/chapter/after",
            ),
        )
    }

    @Test
    fun `merge is deterministic and equal-time tombstone wins`() {
        val live = bridge()
        val deleted = AlternateSourceBridgePolicy.tombstone(live, live.updatedAt)
        val forward = AlternateSourceBridgeBackupPolicy.merge(
            listOf(AlternateSourceBridgeBackupPolicy.encode(live)),
            listOf(AlternateSourceBridgeBackupPolicy.encode(deleted)),
            emptyList(),
            emptyList(),
            10_000,
        )
        val reverse = AlternateSourceBridgeBackupPolicy.merge(
            listOf(AlternateSourceBridgeBackupPolicy.encode(deleted)),
            listOf(AlternateSourceBridgeBackupPolicy.encode(live)),
            emptyList(),
            emptyList(),
            10_000,
        )

        assertEquals(forward, reverse)
        assertTrue(requireNotNull(AlternateSourceBridgeBackupPolicy.decode(forward.bridges.single(), 10_000)).deletedAt != null)
    }

    @Test
    fun `merge drops malformed and orphan mapping rows`() {
        val route = AlternateSourceBridgeBackupPolicy.encode(bridge())
        val malformed = route.copy(primarySource = 0)
        val orphan = AlternateSourceBridgeBackupPolicy.encode(bridgeMapping()).copy(alternateSource = 99)

        val merged = AlternateSourceBridgeBackupPolicy.merge(
            listOf(route, malformed),
            emptyList(),
            listOf(orphan),
            emptyList(),
            10_000,
        )

        assertEquals(1, merged.bridges.size)
        assertTrue(merged.mappings.isEmpty())
    }

    @Test
    fun `restore aggregates malformed and orphan rows into bounded categories`() = runTest {
        val repository = RecordingBridgeRepository()
        val route = AlternateSourceBridgeBackupPolicy.encode(bridge())
        val malformed = BackupAlternateSourceBridge(primarySource = 0)
        val orphan = AlternateSourceBridgeBackupPolicy.encode(bridgeMapping()).copy(alternateSource = 99)

        val errors = AlternateSourceBridgeRestorer(repository).restore(
            bridgeRows = listOf(route) + List(50) { malformed },
            mappingRows = listOf(orphan),
            now = 10_000,
        )

        assertEquals(2, errors.size)
        assertTrue(errors.any { "50 malformed route" in it })
        assertTrue(errors.any { "1 mappings without routes" in it })
        assertEquals(1, repository.states.size)
    }

    @Test
    fun `restore isolates ordinary route failure and rethrows cancellation`() {
        val route = AlternateSourceBridgeBackupPolicy.encode(bridge())
        val failed = RecordingBridgeRepository(failure = IllegalStateException("private detail"))
        val errors = runBlocking { AlternateSourceBridgeRestorer(failed).restore(listOf(route), emptyList(), 10_000) }
        assertEquals(listOf("Alternate-source bridges: 1 routes could not be restored"), errors)
        assertTrue(errors.none { "private detail" in it })

        val cancelled = RecordingBridgeRepository(failure = CancellationException("stop"))
        assertThrows(CancellationException::class.java) {
            runBlocking { AlternateSourceBridgeRestorer(cancelled).restore(listOf(route), emptyList(), 10_000) }
        }
    }
}

private class RecordingBridgeRepository(
    private val failure: Exception? = null,
) : AlternateSourceBridgeRepository {
    val states = mutableListOf<AlternateSourceBridgeState>()

    override suspend fun get(key: tachiyomi.domain.taste.model.AlternateSourceBridgeKey): AlternateSourceBridgeState? =
        states.firstOrNull { it.bridge.key == key }

    override suspend fun getAllBridges(): List<AlternateSourceBridge> = states.map { it.bridge }

    override suspend fun getAllMappings(): List<AlternateSourceBridgeMapping> = states.flatMap { it.mappings }

    override suspend fun upsert(state: AlternateSourceBridgeState) {
        failure?.let { throw it }
        states.removeAll { it.bridge.key == state.bridge.key }
        states += state
    }

    override suspend fun replace(replacement: AlternateSourceBridgeStateReplacement): Boolean = false

    override suspend fun tombstoneAll(updatedAt: Long) = Unit
}
