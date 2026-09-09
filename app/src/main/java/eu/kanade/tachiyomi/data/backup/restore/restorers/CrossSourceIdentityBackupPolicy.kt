package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCrossSourceIdentityDecision
import tachiyomi.domain.taste.model.CrossSourceIdentityDecision
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionPolicy
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionValue
import tachiyomi.domain.taste.model.CrossSourceIdentityPair
import tachiyomi.domain.taste.model.CrossSourceIdentityReasonCode
import tachiyomi.domain.taste.model.CrossSourceIdentityReviewState
import tachiyomi.domain.taste.model.CrossSourceRecordKey

internal object CrossSourceIdentityBackupPolicy {

    fun decode(row: BackupCrossSourceIdentityDecision, now: Long): CrossSourceIdentityDecision? {
        val reasonCodes = runCatching {
            row.reasonCodes.map(CrossSourceIdentityReasonCode::valueOf).toSet()
        }.getOrNull() ?: return null
        val decision = runCatching { CrossSourceIdentityDecisionValue.valueOf(row.decision) }.getOrNull() ?: return null
        val reviewState = runCatching { CrossSourceIdentityReviewState.valueOf(row.reviewState) }.getOrNull() ?: return null
        val pair = CrossSourceIdentityDecisionPolicy.canonicalPair(
            CrossSourceRecordKey(row.leftSource, row.leftUrl),
            CrossSourceRecordKey(row.rightSource, row.rightUrl),
        )
        return CrossSourceIdentityDecision(
            pair = pair,
            decision = decision,
            decisionVersion = row.decisionVersion,
            evidenceVersion = row.evidenceVersion,
            reasonCodes = reasonCodes,
            reviewState = reviewState,
            createdAt = row.createdAt,
            updatedAt = row.updatedAt,
            deletedAt = row.deletedAt.takeIf { it > 0L },
        ).takeIf { CrossSourceIdentityDecisionPolicy.isValid(it, now) }
    }

    fun encode(decision: CrossSourceIdentityDecision): BackupCrossSourceIdentityDecision {
        val row = CrossSourceIdentityDecisionPolicy.canonicalize(decision)
        return BackupCrossSourceIdentityDecision(
            leftSource = row.pair.left.source,
            leftUrl = row.pair.left.url,
            rightSource = row.pair.right.source,
            rightUrl = row.pair.right.url,
            decision = row.decision.name,
            decisionVersion = row.decisionVersion,
            evidenceVersion = row.evidenceVersion,
            reasonCodes = row.reasonCodes.sortedBy { it.ordinal }.map { it.name },
            reviewState = row.reviewState.name,
            createdAt = row.createdAt,
            updatedAt = row.updatedAt,
            deletedAt = row.deletedAt ?: 0L,
        )
    }

    fun merge(
        local: List<BackupCrossSourceIdentityDecision>?,
        remote: List<BackupCrossSourceIdentityDecision>?,
        now: Long,
    ): List<BackupCrossSourceIdentityDecision> {
        fun decodeBounded(rows: List<BackupCrossSourceIdentityDecision>?): Map<CrossSourceIdentityPair, CrossSourceIdentityDecision> {
            return rows.orEmpty()
                .take(CrossSourceIdentityDecisionPolicy.MAX_TRANSFER_ROWS)
                .mapNotNull { decode(it, now) }
                .groupBy { it.pair }
                .mapValues { (_, values) -> values.reduce(CrossSourceIdentityDecisionPolicy::merge) }
        }

        val localRows = decodeBounded(local)
        val remoteRows = decodeBounded(remote)
        return (localRows.keys + remoteRows.keys)
            .sortedWith(compareBy({ it.left.source }, { it.left.url }, { it.right.source }, { it.right.url }))
            .take(CrossSourceIdentityDecisionPolicy.MAX_SYNC_ROWS)
            .map { pair ->
                val left = localRows[pair]
                val right = remoteRows[pair]
                encode(
                    when {
                        left == null -> requireNotNull(right)
                        right == null -> left
                        else -> CrossSourceIdentityDecisionPolicy.merge(left, right)
                    },
                )
            }
    }
}
