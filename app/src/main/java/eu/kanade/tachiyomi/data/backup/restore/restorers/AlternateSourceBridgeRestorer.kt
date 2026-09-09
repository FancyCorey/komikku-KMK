package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupAlternateSourceBridge
import eu.kanade.tachiyomi.data.backup.models.BackupAlternateSourceBridgeMapping
import kotlinx.coroutines.CancellationException
import tachiyomi.domain.taste.model.AlternateSourceBridgePolicy
import tachiyomi.domain.taste.model.AlternateSourceBridgeState
import tachiyomi.domain.taste.repository.AlternateSourceBridgeRepository

internal class AlternateSourceBridgeRestorer(
    private val repository: AlternateSourceBridgeRepository,
) {
    suspend fun restore(
        bridgeRows: List<BackupAlternateSourceBridge>,
        mappingRows: List<BackupAlternateSourceBridgeMapping>,
        now: Long,
    ): List<String> {
        if (bridgeRows.isEmpty() && mappingRows.isEmpty()) return emptyList()
        val errors = mutableListOf<String>()
        if (bridgeRows.size > AlternateSourceBridgePolicy.MAX_BRIDGE_ROWS) {
            errors += "Alternate-source bridges: route limit exceeded; excess rows skipped"
        }
        if (mappingRows.size > AlternateSourceBridgePolicy.MAX_MAPPING_ROWS) {
            errors += "Alternate-source bridges: mapping limit exceeded; excess rows skipped"
        }
        var malformedBridgeCount = 0
        var malformedMappingCount = 0
        val decodedBridges = bridgeRows.take(AlternateSourceBridgePolicy.MAX_BRIDGE_ROWS)
            .mapNotNull { row ->
                AlternateSourceBridgeBackupPolicy.decode(row, now).also {
                    if (it == null) malformedBridgeCount++
                }
            }
            .groupBy { it.key }
            .mapValues { (_, rows) -> rows.reduce(AlternateSourceBridgePolicy::merge) }
        val decodedMappings = mappingRows.take(AlternateSourceBridgePolicy.MAX_MAPPING_ROWS)
            .mapNotNull { row ->
                AlternateSourceBridgeBackupPolicy.decode(row, now).also {
                    if (it == null) malformedMappingCount++
                }
            }
            .groupBy { it.key.bridge }

        if (malformedBridgeCount > 0) {
            errors += "Alternate-source bridges: $malformedBridgeCount malformed route rows skipped"
        }
        if (malformedMappingCount > 0) {
            errors += "Alternate-source bridges: $malformedMappingCount malformed mapping rows skipped"
        }
        val orphanMappingCount = decodedMappings
            .filterKeys { it !in decodedBridges }
            .values
            .sumOf(List<*>::size)
        if (orphanMappingCount > 0) {
            errors += "Alternate-source bridges: $orphanMappingCount mappings without routes skipped"
        }
        var failedRouteCount = 0
        decodedBridges.toSortedMap(
            compareBy(
                { it.primary.source },
                { it.primary.url },
                { it.alternate.source },
                { it.alternate.url },
            ),
        ).forEach { (key, incomingBridge) ->
            try {
                val existing = repository.get(key)
                val bridge = existing?.bridge?.let { AlternateSourceBridgePolicy.merge(it, incomingBridge) } ?: incomingBridge
                val mappings = (existing?.mappings.orEmpty() + decodedMappings[key].orEmpty())
                    .groupBy { it.key }
                    .mapValues { (_, rows) -> rows.reduce(AlternateSourceBridgePolicy::merge) }
                    .values
                    .toList()
                    .let { AlternateSourceBridgePolicy.projectConflicts(it) }
                    .take(AlternateSourceBridgePolicy.MAX_MAPPINGS_PER_BRIDGE)
                repository.upsert(AlternateSourceBridgeState(bridge, mappings))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failedRouteCount++
            }
        }
        if (failedRouteCount > 0) {
            errors += "Alternate-source bridges: $failedRouteCount routes could not be restored"
        }
        return errors
    }
}
