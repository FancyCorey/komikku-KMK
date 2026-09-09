package tachiyomi.data.taste

import tachiyomi.data.Database
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.taste.model.AlternateSourceBridge
import tachiyomi.domain.taste.model.AlternateSourceBridgeEvidenceState
import tachiyomi.domain.taste.model.AlternateSourceBridgeKey
import tachiyomi.domain.taste.model.AlternateSourceBridgeMapping
import tachiyomi.domain.taste.model.AlternateSourceBridgeMappingKey
import tachiyomi.domain.taste.model.AlternateSourceBridgeMappingRelation
import tachiyomi.domain.taste.model.AlternateSourceBridgeMappingState
import tachiyomi.domain.taste.model.AlternateSourceBridgePolicy
import tachiyomi.domain.taste.model.AlternateSourceBridgeReviewState
import tachiyomi.domain.taste.model.AlternateSourceBridgeState
import tachiyomi.domain.taste.model.AlternateSourceBridgeStateReplacement
import tachiyomi.domain.taste.model.CrossSourceRecordKey
import tachiyomi.domain.taste.repository.AlternateSourceBridgeRepository

class AlternateSourceBridgeRepositoryImpl(
    private val handler: DatabaseHandler,
) : AlternateSourceBridgeRepository {

    override suspend fun get(key: AlternateSourceBridgeKey): AlternateSourceBridgeState? {
        val bridge = handler.awaitOneOrNull {
            alternate_source_bridgeQueries.getByKey(
                key.primary.source,
                key.primary.url,
                key.alternate.source,
                key.alternate.url,
                bridgeMapper,
            )
        } ?: return null
        val mappings = handler.awaitList {
            alternate_source_bridge_mappingQueries.getByBridge(
                key.primary.source,
                key.primary.url,
                key.alternate.source,
                key.alternate.url,
                mappingMapper,
            )
        }
        return AlternateSourceBridgeState(bridge, mappings.sortedWith(AlternateSourceBridgePolicy.mappingComparator))
    }

    override suspend fun getAllBridges(): List<AlternateSourceBridge> = handler.awaitList {
        alternate_source_bridgeQueries.getAll(bridgeMapper)
    }.sortedWith(AlternateSourceBridgePolicy.bridgeComparator)

    override suspend fun getAllMappings(): List<AlternateSourceBridgeMapping> = handler.awaitList {
        alternate_source_bridge_mappingQueries.getAll(mappingMapper)
    }.sortedWith(AlternateSourceBridgePolicy.mappingComparator)

    override suspend fun upsert(state: AlternateSourceBridgeState) {
        val now = System.currentTimeMillis()
        require(AlternateSourceBridgePolicy.isValid(state.bridge, now))
        require(state.mappings.size <= AlternateSourceBridgePolicy.MAX_MAPPINGS_PER_BRIDGE)
        require(state.mappings.all { it.key.bridge == state.bridge.key && AlternateSourceBridgePolicy.isValid(it, now) })
        val mappings = AlternateSourceBridgePolicy.projectConflicts(state.mappings)
        handler.await(inTransaction = true) {
            upsertBridge(state.bridge)
            mappings.forEach { upsertMapping(it) }
        }
    }

    override suspend fun replace(replacement: AlternateSourceBridgeStateReplacement): Boolean {
        val key = stateKey(replacement) ?: return false
        val mappingChanges = replacement.mappingReplacements.map { change ->
            val mappingKey = change.replacement?.key ?: change.expected?.key ?: return false
            if (mappingKey.bridge != key) return false
            Triple(mappingKey, change.expected, change.replacement)
        }
        if (mappingChanges.map { it.first }.distinct().size != mappingChanges.size) return false
        val now = System.currentTimeMillis()
        if (replacement.replacementBridge?.let { !AlternateSourceBridgePolicy.isValid(it, now) } == true) return false
        if (mappingChanges.any { (_, _, row) -> row != null && !AlternateSourceBridgePolicy.isValid(row, now) }) return false

        return handler.await(inTransaction = true) {
            val currentBridge = alternate_source_bridgeQueries.getByKey(
                key.primary.source,
                key.primary.url,
                key.alternate.source,
                key.alternate.url,
                bridgeMapper,
            ).executeAsOneOrNull()
            if (currentBridge != replacement.expectedBridge) return@await false
            for ((mappingKey, expected, _) in mappingChanges) {
                val current = alternate_source_bridge_mappingQueries.getByKey(
                    mappingKey.bridge.primary.source,
                    mappingKey.bridge.primary.url,
                    mappingKey.bridge.alternate.source,
                    mappingKey.bridge.alternate.url,
                    mappingKey.targetId,
                    mappingMapper,
                ).executeAsOneOrNull()
                if (current != expected) return@await false
            }
            if (replacement.replacementBridge == null) {
                val currentMappingKeys = alternate_source_bridge_mappingQueries.getByBridge(
                    key.primary.source,
                    key.primary.url,
                    key.alternate.source,
                    key.alternate.url,
                    mappingMapper,
                ).executeAsList().mapTo(mutableSetOf()) { it.key }
                if (currentMappingKeys != mappingChanges.mapTo(mutableSetOf()) { it.first }) return@await false
            }

            replacement.replacementBridge?.let { upsertBridge(it) } ?: deleteBridge(key)
            for ((mappingKey, _, row) in mappingChanges) {
                row?.let { upsertMapping(it) } ?: deleteMapping(mappingKey)
            }
            true
        }
    }

    override suspend fun tombstoneAll(updatedAt: Long) {
        require(updatedAt > 0L && updatedAt <= System.currentTimeMillis() + AlternateSourceBridgePolicy.MAX_FUTURE_SKEW_MS)
        handler.await(inTransaction = true) {
            alternate_source_bridgeQueries.tombstoneAll(updatedAt)
            alternate_source_bridge_mappingQueries.tombstoneAll(updatedAt)
        }
    }

    private fun stateKey(replacement: AlternateSourceBridgeStateReplacement): AlternateSourceBridgeKey? {
        val keys = buildList {
            replacement.expectedBridge?.key?.let(::add)
            replacement.replacementBridge?.key?.let(::add)
            replacement.mappingReplacements.forEach { change ->
                change.expected?.key?.bridge?.let(::add)
                change.replacement?.key?.bridge?.let(::add)
            }
        }.distinct()
        return keys.singleOrNull()
    }

    private fun Database.upsertBridge(row: AlternateSourceBridge) {
        alternate_source_bridgeQueries.upsert(
            primarySource = row.key.primary.source,
            primaryUrl = row.key.primary.url,
            alternateSource = row.key.alternate.source,
            alternateUrl = row.key.alternate.url,
            version = row.version.toLong(),
            offsetMilli = row.offsetMilli,
            offsetState = row.offsetState?.name,
            continuationPrimaryChapterUrl = row.continuationPrimaryChapterUrl,
            returnAfterAlternateChapterUrl = row.returnAfterAlternateChapterUrl,
            automaticReturn = row.automaticReturn,
            reviewState = row.reviewState.name,
            createdAt = row.createdAt,
            updatedAt = row.updatedAt,
            deletedAt = row.deletedAt,
        )
    }

    private fun Database.upsertMapping(row: AlternateSourceBridgeMapping) {
        alternate_source_bridge_mappingQueries.upsert(
            primarySource = row.key.bridge.primary.source,
            primaryUrl = row.key.bridge.primary.url,
            alternateSource = row.key.bridge.alternate.source,
            alternateUrl = row.key.bridge.alternate.url,
            targetId = row.key.targetId,
            primaryChapterUrl = row.primaryChapterUrl,
            alternateChapterUrl = row.alternateChapterUrl,
            precedingPrimaryChapterUrl = row.precedingPrimaryChapterUrl,
            followingPrimaryChapterUrl = row.followingPrimaryChapterUrl,
            relation = row.relation.name,
            state = row.state.name,
            offsetMilli = row.offsetMilli,
            version = row.version.toLong(),
            createdAt = row.createdAt,
            updatedAt = row.updatedAt,
            deletedAt = row.deletedAt,
        )
    }

    private fun Database.deleteBridge(key: AlternateSourceBridgeKey) {
        alternate_source_bridgeQueries.deleteByKey(
            key.primary.source,
            key.primary.url,
            key.alternate.source,
            key.alternate.url,
        )
    }

    private fun Database.deleteMapping(key: AlternateSourceBridgeMappingKey) {
        alternate_source_bridge_mappingQueries.deleteByKey(
            key.bridge.primary.source,
            key.bridge.primary.url,
            key.bridge.alternate.source,
            key.bridge.alternate.url,
            key.targetId,
        )
    }
}

private val bridgeMapper = {
        primarySource: Long,
        primaryUrl: String,
        alternateSource: Long,
        alternateUrl: String,
        version: Long,
        offsetMilli: Long?,
        offsetState: String?,
        continuationPrimaryChapterUrl: String?,
        returnAfterAlternateChapterUrl: String?,
        automaticReturn: Boolean,
        reviewState: String,
        createdAt: Long,
        updatedAt: Long,
        deletedAt: Long?,
    ->
    AlternateSourceBridge(
        key = AlternateSourceBridgeKey(
            CrossSourceRecordKey(primarySource, primaryUrl),
            CrossSourceRecordKey(alternateSource, alternateUrl),
        ),
        version = version.toInt(),
        offsetMilli = offsetMilli,
        offsetState = offsetState?.let(AlternateSourceBridgeEvidenceState::valueOf),
        continuationPrimaryChapterUrl = continuationPrimaryChapterUrl,
        returnAfterAlternateChapterUrl = returnAfterAlternateChapterUrl,
        automaticReturn = automaticReturn,
        reviewState = AlternateSourceBridgeReviewState.valueOf(reviewState),
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )
}

private val mappingMapper = {
        primarySource: Long,
        primaryUrl: String,
        alternateSource: Long,
        alternateUrl: String,
        targetId: String,
        primaryChapterUrl: String?,
        alternateChapterUrl: String?,
        precedingPrimaryChapterUrl: String?,
        followingPrimaryChapterUrl: String?,
        relation: String,
        state: String,
        offsetMilli: Long?,
        version: Long,
        createdAt: Long,
        updatedAt: Long,
        deletedAt: Long?,
    ->
    AlternateSourceBridgeMapping(
        key = AlternateSourceBridgeMappingKey(
            bridge = AlternateSourceBridgeKey(
                CrossSourceRecordKey(primarySource, primaryUrl),
                CrossSourceRecordKey(alternateSource, alternateUrl),
            ),
            targetId = targetId,
        ),
        primaryChapterUrl = primaryChapterUrl,
        alternateChapterUrl = alternateChapterUrl,
        precedingPrimaryChapterUrl = precedingPrimaryChapterUrl,
        followingPrimaryChapterUrl = followingPrimaryChapterUrl,
        relation = AlternateSourceBridgeMappingRelation.valueOf(relation),
        state = AlternateSourceBridgeMappingState.valueOf(state),
        offsetMilli = offsetMilli,
        version = version.toInt(),
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )
}
