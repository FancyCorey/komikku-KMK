package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.model.CrossSourceIdentityDecision
import tachiyomi.domain.taste.model.CrossSourceIdentityPair
import tachiyomi.domain.taste.model.CrossSourceIdentityReplacement
import tachiyomi.domain.taste.repository.TasteRepository

class GetCrossSourceIdentityDecisions(
    private val repository: TasteRepository,
) {
    suspend fun await(pair: CrossSourceIdentityPair): CrossSourceIdentityDecision? =
        repository.getCrossSourceIdentityDecision(pair)

    suspend fun awaitAll(): List<CrossSourceIdentityDecision> =
        repository.getAllCrossSourceIdentityDecisions()
}

class UpsertCrossSourceIdentityDecisions(
    private val repository: TasteRepository,
) {
    suspend fun await(decisions: List<CrossSourceIdentityDecision>) =
        repository.upsertCrossSourceIdentityDecisions(decisions)
}

class ReplaceCrossSourceIdentityDecision(
    private val repository: TasteRepository,
) {
    suspend fun await(
        expected: CrossSourceIdentityDecision?,
        replacement: CrossSourceIdentityDecision?,
    ): Boolean = repository.replaceCrossSourceIdentityDecision(expected, replacement)
}

class ReplaceCrossSourceIdentityDecisions(
    private val repository: TasteRepository,
) {
    suspend fun await(replacements: List<CrossSourceIdentityReplacement>): Boolean =
        repository.replaceCrossSourceIdentityDecisions(replacements)
}

class ClearCrossSourceIdentityDecisions(
    private val repository: TasteRepository,
) {
    suspend fun await(updatedAt: Long = System.currentTimeMillis()) =
        repository.tombstoneAllCrossSourceIdentityDecisions(updatedAt)
}
