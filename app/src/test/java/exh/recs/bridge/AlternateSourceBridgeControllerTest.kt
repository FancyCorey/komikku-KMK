package exh.recs.bridge

import exh.util.AlternateSourceBridgeUndoJournal
import exh.util.AlternateSourceBridgeUndoService
import exh.util.GroupUndoResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.interactor.GetAlternateSourceBridge
import tachiyomi.domain.taste.interactor.ReplaceAlternateSourceBridge
import tachiyomi.domain.taste.model.AlternateSourceBridge
import tachiyomi.domain.taste.model.AlternateSourceBridgeMapping
import tachiyomi.domain.taste.model.AlternateSourceBridgeMappingReplacement
import tachiyomi.domain.taste.model.AlternateSourceBridgeState
import tachiyomi.domain.taste.model.AlternateSourceBridgeStateReplacement
import tachiyomi.domain.taste.model.bridge
import tachiyomi.domain.taste.model.bridgeMapping
import tachiyomi.domain.taste.repository.AlternateSourceBridgeRepository

class AlternateSourceBridgeControllerTest {

    @AfterEach
    fun tearDown() = AlternateSourceBridgeUndoJournal.clear()

    @Test
    fun `successful mutation records after compare and swap and undo restores prior state`() = runTest {
        val original = AlternateSourceBridgeState(bridge(), listOf(bridgeMapping()))
        val repository = MutableBridgeRepository(original)
        val replacement = AlternateSourceBridgeStateReplacement(
            expectedBridge = original.bridge,
            replacementBridge = original.bridge.copy(updatedAt = 3_000),
        )
        val replace = ReplaceAlternateSourceBridge(repository)
        val controller = AlternateSourceBridgeController(GetAlternateSourceBridge(repository), replace)

        assertEquals(AlternateSourceBridgeMutationResult.APPLIED, controller.apply(replacement, AlternateSourceBridgeMutation.UPDATE))
        val entry = AlternateSourceBridgeUndoJournal.snapshot().single()
        assertEquals(3_000L, repository.state?.bridge?.updatedAt)

        assertEquals(GroupUndoResult.RESTORED, AlternateSourceBridgeUndoService(replace).undo(entry.id))
        assertEquals(original, repository.state)
        assertTrue(AlternateSourceBridgeUndoJournal.snapshot().isEmpty())
    }

    @Test
    fun `create with mappings is fully removed by undo`() = runTest {
        val repository = MutableBridgeRepository(null)
        val route = bridge()
        val mapping = bridgeMapping()
        val replacement = AlternateSourceBridgeStateReplacement(
            expectedBridge = null,
            replacementBridge = route,
            mappingReplacements = listOf(AlternateSourceBridgeMappingReplacement(null, mapping)),
        )
        val replace = ReplaceAlternateSourceBridge(repository)
        val controller = AlternateSourceBridgeController(GetAlternateSourceBridge(repository), replace)

        assertEquals(AlternateSourceBridgeMutationResult.APPLIED, controller.apply(replacement, AlternateSourceBridgeMutation.CREATE))
        val entry = AlternateSourceBridgeUndoJournal.snapshot().single()
        assertEquals(AlternateSourceBridgeState(route, listOf(mapping)), repository.state)
        assertEquals(GroupUndoResult.RESTORED, AlternateSourceBridgeUndoService(replace).undo(entry.id))
        assertNull(repository.state)
    }

    @Test
    fun `stale and failed mutations do not create history entries`() = runTest {
        val original = AlternateSourceBridgeState(bridge(), emptyList())
        val staleRepository = MutableBridgeRepository(original)
        val stale = AlternateSourceBridgeStateReplacement(
            expectedBridge = original.bridge.copy(updatedAt = 1_999),
            replacementBridge = original.bridge.copy(updatedAt = 3_000),
        )
        val staleController = AlternateSourceBridgeController(
            GetAlternateSourceBridge(staleRepository),
            ReplaceAlternateSourceBridge(staleRepository),
        )
        assertEquals(AlternateSourceBridgeMutationResult.CONFLICT, staleController.apply(stale, AlternateSourceBridgeMutation.UPDATE))
        assertTrue(AlternateSourceBridgeUndoJournal.snapshot().isEmpty())

        val failedRepository = MutableBridgeRepository(original, failure = IllegalStateException("private"))
        val failedController = AlternateSourceBridgeController(
            GetAlternateSourceBridge(failedRepository),
            ReplaceAlternateSourceBridge(failedRepository),
        )
        val valid = stale.copy(expectedBridge = original.bridge)
        assertEquals(AlternateSourceBridgeMutationResult.FAILED, failedController.apply(valid, AlternateSourceBridgeMutation.UPDATE))
        assertTrue(AlternateSourceBridgeUndoJournal.snapshot().isEmpty())
    }

    @Test
    fun `cancellation propagates and records nothing`() {
        val original = AlternateSourceBridgeState(bridge(), emptyList())
        val repository = MutableBridgeRepository(original, failure = CancellationException("stop"))
        val controller = AlternateSourceBridgeController(
            GetAlternateSourceBridge(repository),
            ReplaceAlternateSourceBridge(repository),
        )
        val replacement = AlternateSourceBridgeStateReplacement(
            expectedBridge = original.bridge,
            replacementBridge = original.bridge.copy(updatedAt = 3_000),
        )

        assertThrows(CancellationException::class.java) {
            runTest { controller.apply(replacement, AlternateSourceBridgeMutation.UPDATE) }
        }
        assertTrue(AlternateSourceBridgeUndoJournal.snapshot().isEmpty())
    }

    @Test
    fun `clear tombstones route and mappings at one monotonic boundary`() = runTest {
        val original = AlternateSourceBridgeState(bridge(updatedAt = 5_000), listOf(bridgeMapping(updatedAt = 6_000)))
        val repository = MutableBridgeRepository(original)
        val controller = AlternateSourceBridgeController(
            GetAlternateSourceBridge(repository),
            ReplaceAlternateSourceBridge(repository),
            clock = { 4_000 },
        )

        assertEquals(AlternateSourceBridgeMutationResult.APPLIED, controller.clear(original.bridge.key))
        assertEquals(6_001L, repository.state?.bridge?.deletedAt)
        assertEquals(6_001L, repository.state?.mappings?.single()?.deletedAt)
    }

    @Test
    fun `undo conflict preserves history and newer state`() = runTest {
        val original = AlternateSourceBridgeState(bridge(), emptyList())
        val repository = MutableBridgeRepository(original)
        val replace = ReplaceAlternateSourceBridge(repository)
        val controller = AlternateSourceBridgeController(GetAlternateSourceBridge(repository), replace)
        val replacement = AlternateSourceBridgeStateReplacement(
            expectedBridge = original.bridge,
            replacementBridge = original.bridge.copy(updatedAt = 3_000),
        )
        controller.apply(replacement, AlternateSourceBridgeMutation.UPDATE)
        val entry = AlternateSourceBridgeUndoJournal.snapshot().single()
        repository.state = AlternateSourceBridgeState(original.bridge.copy(updatedAt = 4_000), emptyList())

        assertEquals(GroupUndoResult.CONFLICT, AlternateSourceBridgeUndoService(replace).undo(entry.id))
        assertEquals(4_000L, repository.state?.bridge?.updatedAt)
        assertEquals(entry, AlternateSourceBridgeUndoJournal.snapshot().single())
    }

    @Test
    fun `history model carries no title source name chapter number or path fields`() {
        val names = exh.util.AlternateSourceBridgeUndoEntry::class.java.declaredFields.map { it.name.lowercase() }
        assertTrue(names.none { it.contains("title") || it.contains("name") || it.contains("number") || it.contains("path") })
    }
}

private class MutableBridgeRepository(
    initial: AlternateSourceBridgeState?,
    private val failure: Exception? = null,
) : AlternateSourceBridgeRepository {
    var state: AlternateSourceBridgeState? = initial

    override suspend fun get(key: tachiyomi.domain.taste.model.AlternateSourceBridgeKey): AlternateSourceBridgeState? =
        state?.takeIf { it.bridge.key == key }

    override suspend fun getAllBridges(): List<AlternateSourceBridge> = listOfNotNull(state?.bridge)

    override suspend fun getAllMappings(): List<AlternateSourceBridgeMapping> = state?.mappings.orEmpty()

    override suspend fun upsert(state: AlternateSourceBridgeState) {
        this.state = state
    }

    override suspend fun replace(replacement: AlternateSourceBridgeStateReplacement): Boolean {
        failure?.let { throw it }
        val current = state
        if (current?.bridge != replacement.expectedBridge) return false
        for (change in replacement.mappingReplacements) {
            if (current?.mappings.orEmpty().firstOrNull { it.key == (change.expected ?: change.replacement)?.key } != change.expected) return false
        }
        val replacementBridge = replacement.replacementBridge
        if (replacementBridge == null) {
            state = null
            return true
        }
        val mappings = current?.mappings.orEmpty().toMutableList()
        replacement.mappingReplacements.forEach { change ->
            val key = (change.expected ?: change.replacement)?.key ?: return false
            mappings.removeAll { it.key == key }
            change.replacement?.let(mappings::add)
        }
        state = AlternateSourceBridgeState(replacementBridge, mappings)
        return true
    }

    override suspend fun tombstoneAll(updatedAt: Long) = Unit
}
