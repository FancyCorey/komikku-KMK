package tachiyomi.domain.taste.repository

import tachiyomi.domain.taste.model.AlternateSourceBridge
import tachiyomi.domain.taste.model.AlternateSourceBridgeKey
import tachiyomi.domain.taste.model.AlternateSourceBridgeMapping
import tachiyomi.domain.taste.model.AlternateSourceBridgeState
import tachiyomi.domain.taste.model.AlternateSourceBridgeStateReplacement

interface AlternateSourceBridgeRepository {
    suspend fun get(key: AlternateSourceBridgeKey): AlternateSourceBridgeState?
    suspend fun getAllBridges(): List<AlternateSourceBridge>
    suspend fun getAllMappings(): List<AlternateSourceBridgeMapping>
    suspend fun upsert(state: AlternateSourceBridgeState)
    suspend fun replace(replacement: AlternateSourceBridgeStateReplacement): Boolean
    suspend fun tombstoneAll(updatedAt: Long)
}
