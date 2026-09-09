package exh.recs.matching

import exh.util.CrossSourceIdentityUndoJournal
import exh.util.CrossSourceIdentityUndoRecorder
import kotlinx.coroutines.CancellationException
import tachiyomi.domain.taste.interactor.GetCrossSourceIdentityDecisions
import tachiyomi.domain.taste.interactor.ReplaceCrossSourceIdentityDecisions
import tachiyomi.domain.taste.model.CrossSourceIdentityDecision
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionPolicy
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionValue
import tachiyomi.domain.taste.model.CrossSourceIdentityPair
import tachiyomi.domain.taste.model.CrossSourceIdentityReplacement
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

enum class CrossSourceIdentityMutation { CONFIRM, REJECT, CLEAR }

enum class CrossSourceIdentityMutationResult { APPLIED, UNCHANGED, CONFLICT, FAILED }

class CrossSourceIdentityDecisionController(
    private val getDecisions: GetCrossSourceIdentityDecisions = Injekt.get(),
    private val replaceDecisions: ReplaceCrossSourceIdentityDecisions = Injekt.get(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun mutate(
        pair: CrossSourceIdentityPair,
        mutation: CrossSourceIdentityMutation,
    ): CrossSourceIdentityMutationResult {
        return mutateAll(listOf(pair), mutation)
    }

    suspend fun mutateAll(
        pairs: List<CrossSourceIdentityPair>,
        mutation: CrossSourceIdentityMutation,
    ): CrossSourceIdentityMutationResult {
        return try {
            val canonicalPairs = pairs.map { CrossSourceIdentityDecisionPolicy.canonicalPair(it.left, it.right) }.distinct()
            val replacements = canonicalPairs.mapNotNull { pair ->
                val previous = getDecisions.await(pair)
                replacementFor(pair, previous, mutation)?.let { CrossSourceIdentityReplacement(previous, it) }
            }
            if (replacements.isEmpty()) return CrossSourceIdentityMutationResult.UNCHANGED
            val entry = CrossSourceIdentityUndoRecorder.build(replacements, mutation)
            if (!replaceDecisions.await(replacements)) return CrossSourceIdentityMutationResult.CONFLICT
            CrossSourceIdentityUndoJournal.record(entry)
            CrossSourceIdentityMutationResult.APPLIED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CrossSourceIdentityMutationResult.FAILED
        }
    }

    private fun replacementFor(
        pair: CrossSourceIdentityPair,
        previous: CrossSourceIdentityDecision?,
        mutation: CrossSourceIdentityMutation,
    ): CrossSourceIdentityDecision? = when (mutation) {
        CrossSourceIdentityMutation.CONFIRM -> {
            if (CrossSourceIdentityDecisionPolicy.isAuthoritativeConfirmation(previous)) {
                null
            } else {
                CrossSourceIdentityDecisionPolicy.userDecision(
                    pair,
                    CrossSourceIdentityDecisionValue.USER_CONFIRMED,
                    previous,
                    clock(),
                )
            }
        }
        CrossSourceIdentityMutation.REJECT -> {
            if (CrossSourceIdentityDecisionPolicy.isCurrentRejection(previous)) {
                null
            } else {
                CrossSourceIdentityDecisionPolicy.userDecision(
                    pair,
                    CrossSourceIdentityDecisionValue.USER_REJECTED,
                    previous,
                    clock(),
                )
            }
        }
        CrossSourceIdentityMutation.CLEAR -> {
            previous?.takeIf { it.deletedAt == null }?.let {
                CrossSourceIdentityDecisionPolicy.clearedDecision(it, clock())
            }
        }
    }
}
