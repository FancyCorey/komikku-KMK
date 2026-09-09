package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.model.AlternateSourceBridgeKey
import tachiyomi.domain.taste.model.AlternateSourceBridgeState
import tachiyomi.domain.taste.model.AlternateSourceBridgeStateReplacement
import tachiyomi.domain.taste.repository.AlternateSourceBridgeRepository

class GetAlternateSourceBridge(
    private val repository: AlternateSourceBridgeRepository,
) {
    suspend fun await(key: AlternateSourceBridgeKey): AlternateSourceBridgeState? = repository.get(key)
    suspend fun awaitAllBridges() = repository.getAllBridges()
    suspend fun awaitAllMappings() = repository.getAllMappings()
}

class UpsertAlternateSourceBridge(
    private val repository: AlternateSourceBridgeRepository,
) {
    suspend fun await(state: AlternateSourceBridgeState) = repository.upsert(state)
}

class ReplaceAlternateSourceBridge(
    private val repository: AlternateSourceBridgeRepository,
) {
    suspend fun await(replacement: AlternateSourceBridgeStateReplacement): Boolean = repository.replace(replacement)
}

class ClearAlternateSourceBridges(
    private val repository: AlternateSourceBridgeRepository,
) {
    suspend fun await(updatedAt: Long) = repository.tombstoneAll(updatedAt)
}
