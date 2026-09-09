package eu.kanade.tachiyomi.data.sync.service

import eu.kanade.tachiyomi.data.backup.restore.restorers.AlternateSourceBridgeBackupPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.AlternateSourceBridgePolicy
import tachiyomi.domain.taste.model.bridge
import tachiyomi.domain.taste.model.bridgeMapping

class SyncServiceAlternateSourceBridgeMergeTest {

    @Test
    fun `sync pure boundary uses bridge merge policy and keeps mappings attached`() {
        val older = bridge()
        val newer = older.copy(updatedAt = 3_000)
        val mapping = bridgeMapping(updatedAt = 3_000)

        val result = SyncService.mergeAlternateSourceBridgesPure(
            localBridges = listOf(AlternateSourceBridgeBackupPolicy.encode(older)),
            remoteBridges = listOf(AlternateSourceBridgeBackupPolicy.encode(newer)),
            localMappings = emptyList(),
            remoteMappings = listOf(AlternateSourceBridgeBackupPolicy.encode(mapping)),
            now = 10_000,
        )

        assertEquals(3_000L, AlternateSourceBridgeBackupPolicy.decode(result.bridges.single(), 10_000)?.updatedAt)
        assertEquals(mapping, AlternateSourceBridgeBackupPolicy.decode(result.mappings.single(), 10_000))
    }

    @Test
    fun `sync merge caps transfer and filters malformed route rows`() {
        val encoded = AlternateSourceBridgeBackupPolicy.encode(bridge())
        val result = SyncService.mergeAlternateSourceBridgesPure(
            localBridges = List(AlternateSourceBridgePolicy.MAX_BRIDGE_ROWS + 10) { encoded.copy(primarySource = 0) },
            remoteBridges = listOf(encoded),
            localMappings = emptyList(),
            remoteMappings = emptyList(),
            now = 10_000,
        )

        assertEquals(1, result.bridges.size)
        assertTrue(result.mappings.isEmpty())
    }
}
