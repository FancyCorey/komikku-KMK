package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCrossSourceIdentityDecision
import kotlinx.coroutines.CancellationException
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionPolicy
import tachiyomi.domain.taste.repository.TasteRepository

internal class CrossSourceIdentityDecisionRestorer(
    private val repository: TasteRepository,
) {
    suspend fun restore(rows: List<BackupCrossSourceIdentityDecision>, now: Long): List<String> {
        if (rows.isEmpty()) return emptyList()
        val errors = mutableListOf<String>()
        if (rows.size > CrossSourceIdentityDecisionPolicy.MAX_TRANSFER_ROWS) {
            errors += "Identity decisions: transfer limit exceeded; excess rows skipped"
        }
        rows.take(CrossSourceIdentityDecisionPolicy.MAX_TRANSFER_ROWS).forEach { backup ->
            try {
                val incoming = CrossSourceIdentityBackupPolicy.decode(backup, now)
                if (incoming == null) {
                    errors += "Identity decision: malformed row skipped"
                    return@forEach
                }
                val existing = repository.getCrossSourceIdentityDecision(incoming.pair)
                val merged = if (existing == null) incoming else CrossSourceIdentityDecisionPolicy.merge(existing, incoming)
                repository.upsertCrossSourceIdentityDecisions(listOf(merged))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors += "Identity decision: row could not be restored"
            }
        }
        return errors
    }
}
