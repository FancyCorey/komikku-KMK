package exh.recs.bridge

import exh.util.AlternateSourceBridgeUndoJournal
import exh.util.AlternateSourceBridgeUndoRecorder
import kotlinx.coroutines.CancellationException
import tachiyomi.domain.taste.interactor.GetAlternateSourceBridge
import tachiyomi.domain.taste.interactor.ReplaceAlternateSourceBridge
import tachiyomi.domain.taste.model.AlternateSourceBridgeKey
import tachiyomi.domain.taste.model.AlternateSourceBridgeMappingReplacement
import tachiyomi.domain.taste.model.AlternateSourceBridgePolicy
import tachiyomi.domain.taste.model.AlternateSourceBridgeStateReplacement
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

enum class AlternateSourceBridgeMutation {
    CREATE,
    UPDATE,
    CORRECT_MAPPING,
    SKIP_ALTERNATE,
    CLEAR,
}

enum class AlternateSourceBridgeMutationResult { APPLIED, UNCHANGED, CONFLICT, FAILED }

class AlternateSourceBridgeController(
    private val getBridge: GetAlternateSourceBridge = Injekt.get(),
    private val replaceBridge: ReplaceAlternateSourceBridge = Injekt.get(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun apply(
        replacement: AlternateSourceBridgeStateReplacement,
        mutation: AlternateSourceBridgeMutation,
    ): AlternateSourceBridgeMutationResult {
        if (
            replacement.expectedBridge == replacement.replacementBridge &&
            replacement.mappingReplacements.all { it.expected == it.replacement }
        ) {
            return AlternateSourceBridgeMutationResult.UNCHANGED
        }
        return try {
            val entry = AlternateSourceBridgeUndoRecorder.build(replacement, mutation)
            if (!replaceBridge.await(replacement)) return AlternateSourceBridgeMutationResult.CONFLICT
            AlternateSourceBridgeUndoJournal.record(entry)
            AlternateSourceBridgeMutationResult.APPLIED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AlternateSourceBridgeMutationResult.FAILED
        }
    }

    suspend fun clear(key: AlternateSourceBridgeKey): AlternateSourceBridgeMutationResult {
        val current = getBridge.await(key) ?: return AlternateSourceBridgeMutationResult.UNCHANGED
        if (current.bridge.deletedAt != null) return AlternateSourceBridgeMutationResult.UNCHANGED
        val latest = maxOf(current.bridge.updatedAt, current.mappings.maxOfOrNull { it.updatedAt } ?: Long.MIN_VALUE)
        val timestamp = AlternateSourceBridgePolicy.nextTimestamp(latest, clock())
        return apply(
            AlternateSourceBridgeStateReplacement(
                expectedBridge = current.bridge,
                replacementBridge = AlternateSourceBridgePolicy.tombstone(current.bridge, timestamp),
                mappingReplacements = current.mappings.filter { it.deletedAt == null }.map { mapping ->
                    AlternateSourceBridgeMappingReplacement(
                        mapping,
                        AlternateSourceBridgePolicy.tombstone(mapping, timestamp),
                    )
                },
            ),
            AlternateSourceBridgeMutation.CLEAR,
        )
    }
}
