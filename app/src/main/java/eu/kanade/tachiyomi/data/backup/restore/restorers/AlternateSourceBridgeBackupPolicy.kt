package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupAlternateSourceBridge
import eu.kanade.tachiyomi.data.backup.models.BackupAlternateSourceBridgeMapping
import tachiyomi.domain.taste.model.AlternateSourceBridge
import tachiyomi.domain.taste.model.AlternateSourceBridgeEvidenceState
import tachiyomi.domain.taste.model.AlternateSourceBridgeKey
import tachiyomi.domain.taste.model.AlternateSourceBridgeMapping
import tachiyomi.domain.taste.model.AlternateSourceBridgeMappingKey
import tachiyomi.domain.taste.model.AlternateSourceBridgeMappingRelation
import tachiyomi.domain.taste.model.AlternateSourceBridgeMappingState
import tachiyomi.domain.taste.model.AlternateSourceBridgePolicy
import tachiyomi.domain.taste.model.AlternateSourceBridgeReviewState
import tachiyomi.domain.taste.model.CrossSourceRecordKey

internal data class AlternateSourceBridgeBackupPayload(
    val bridges: List<BackupAlternateSourceBridge>,
    val mappings: List<BackupAlternateSourceBridgeMapping>,
)

internal object AlternateSourceBridgeBackupPolicy {

    fun encode(bridge: AlternateSourceBridge) = BackupAlternateSourceBridge(
        primarySource = bridge.key.primary.source,
        primaryUrl = bridge.key.primary.url,
        alternateSource = bridge.key.alternate.source,
        alternateUrl = bridge.key.alternate.url,
        version = bridge.version,
        offsetMilli = bridge.offsetMilli ?: Long.MIN_VALUE,
        offsetState = bridge.offsetState?.name.orEmpty(),
        continuationPrimaryChapterUrl = bridge.continuationPrimaryChapterUrl.orEmpty(),
        automaticReturn = bridge.automaticReturn,
        reviewState = bridge.reviewState.name,
        createdAt = bridge.createdAt,
        updatedAt = bridge.updatedAt,
        deletedAt = bridge.deletedAt ?: 0L,
        returnAfterAlternateChapterUrl = bridge.returnAfterAlternateChapterUrl.orEmpty(),
    )

    fun encode(mapping: AlternateSourceBridgeMapping) = BackupAlternateSourceBridgeMapping(
        primarySource = mapping.key.bridge.primary.source,
        primaryUrl = mapping.key.bridge.primary.url,
        alternateSource = mapping.key.bridge.alternate.source,
        alternateUrl = mapping.key.bridge.alternate.url,
        targetId = mapping.key.targetId,
        primaryChapterUrl = mapping.primaryChapterUrl.orEmpty(),
        alternateChapterUrl = mapping.alternateChapterUrl.orEmpty(),
        relation = mapping.relation.name,
        state = mapping.state.name,
        offsetMilli = mapping.offsetMilli ?: Long.MIN_VALUE,
        version = mapping.version,
        createdAt = mapping.createdAt,
        updatedAt = mapping.updatedAt,
        deletedAt = mapping.deletedAt ?: 0L,
        precedingPrimaryChapterUrl = mapping.precedingPrimaryChapterUrl.orEmpty(),
        followingPrimaryChapterUrl = mapping.followingPrimaryChapterUrl.orEmpty(),
    )

    fun decode(row: BackupAlternateSourceBridge, now: Long): AlternateSourceBridge? {
        val offset = row.offsetMilli.takeUnless { it == Long.MIN_VALUE }
        val offsetState = row.offsetState.takeIf(String::isNotBlank)?.let {
            runCatching { AlternateSourceBridgeEvidenceState.valueOf(it) }.getOrNull()
        }
        val review = runCatching { AlternateSourceBridgeReviewState.valueOf(row.reviewState) }.getOrNull() ?: return null
        val isLegacy = row.version == AlternateSourceBridgePolicy.LEGACY_VERSION
        return AlternateSourceBridge(
            key = key(row.primarySource, row.primaryUrl, row.alternateSource, row.alternateUrl),
            version = row.version,
            offsetMilli = offset,
            offsetState = offsetState,
            continuationPrimaryChapterUrl = row.continuationPrimaryChapterUrl.takeIf(String::isNotBlank),
            returnAfterAlternateChapterUrl = row.returnAfterAlternateChapterUrl.takeIf(String::isNotBlank),
            automaticReturn = row.automaticReturn && !isLegacy,
            reviewState = review,
            createdAt = row.createdAt,
            updatedAt = row.updatedAt,
            deletedAt = row.deletedAt.takeIf { it > 0L },
        ).takeIf { AlternateSourceBridgePolicy.isValid(it, now) }
    }

    fun decode(row: BackupAlternateSourceBridgeMapping, now: Long): AlternateSourceBridgeMapping? {
        val relation = runCatching { AlternateSourceBridgeMappingRelation.valueOf(row.relation) }.getOrNull() ?: return null
        val state = runCatching { AlternateSourceBridgeMappingState.valueOf(row.state) }.getOrNull() ?: return null
        return AlternateSourceBridgeMapping(
            key = AlternateSourceBridgeMappingKey(
                key(row.primarySource, row.primaryUrl, row.alternateSource, row.alternateUrl),
                row.targetId,
            ),
            primaryChapterUrl = row.primaryChapterUrl.takeIf(String::isNotBlank),
            alternateChapterUrl = row.alternateChapterUrl.takeIf(String::isNotBlank),
            precedingPrimaryChapterUrl = row.precedingPrimaryChapterUrl.takeIf(String::isNotBlank),
            followingPrimaryChapterUrl = row.followingPrimaryChapterUrl.takeIf(String::isNotBlank),
            relation = relation,
            state = state,
            offsetMilli = row.offsetMilli.takeUnless { it == Long.MIN_VALUE },
            version = row.version,
            createdAt = row.createdAt,
            updatedAt = row.updatedAt,
            deletedAt = row.deletedAt.takeIf { it > 0L },
        ).takeIf { AlternateSourceBridgePolicy.isValid(it, now) }
    }

    fun merge(
        localBridges: List<BackupAlternateSourceBridge>?,
        remoteBridges: List<BackupAlternateSourceBridge>?,
        localMappings: List<BackupAlternateSourceBridgeMapping>?,
        remoteMappings: List<BackupAlternateSourceBridgeMapping>?,
        now: Long,
    ): AlternateSourceBridgeBackupPayload {
        val bridges = (
            localBridges.orEmpty().take(AlternateSourceBridgePolicy.MAX_BRIDGE_ROWS) +
                remoteBridges.orEmpty().take(AlternateSourceBridgePolicy.MAX_BRIDGE_ROWS)
            )
            .mapNotNull { decode(it, now) }
            .groupBy { it.key }
            .mapValues { (_, values) -> values.reduce(AlternateSourceBridgePolicy::merge) }
            .values
            .sortedWith(AlternateSourceBridgePolicy.bridgeComparator)
            .take(AlternateSourceBridgePolicy.MAX_BRIDGE_ROWS)
        val bridgeKeys = bridges.mapTo(mutableSetOf()) { it.key }
        val mappings = (
            localMappings.orEmpty().take(AlternateSourceBridgePolicy.MAX_MAPPING_ROWS) +
                remoteMappings.orEmpty().take(AlternateSourceBridgePolicy.MAX_MAPPING_ROWS)
            )
            .mapNotNull { decode(it, now) }
            .filter { it.key.bridge in bridgeKeys }
            .groupBy { it.key }
            .mapValues { (_, values) -> values.reduce(AlternateSourceBridgePolicy::merge) }
            .values
            .groupBy { it.key.bridge }
            .flatMap { (_, rows) ->
                AlternateSourceBridgePolicy.projectConflicts(rows)
                    .take(AlternateSourceBridgePolicy.MAX_MAPPINGS_PER_BRIDGE)
            }
            .sortedWith(AlternateSourceBridgePolicy.mappingComparator)
            .take(AlternateSourceBridgePolicy.MAX_MAPPING_ROWS)
        return AlternateSourceBridgeBackupPayload(bridges.map(::encode), mappings.map(::encode))
    }

    private fun key(primarySource: Long, primaryUrl: String, alternateSource: Long, alternateUrl: String) =
        AlternateSourceBridgeKey(
            CrossSourceRecordKey(primarySource, primaryUrl),
            CrossSourceRecordKey(alternateSource, alternateUrl),
        )
}
