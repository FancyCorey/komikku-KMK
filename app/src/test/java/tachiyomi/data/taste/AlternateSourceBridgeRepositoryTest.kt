package tachiyomi.data.taste

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.data.AndroidDatabaseHandler
import tachiyomi.data.Chapters
import tachiyomi.data.Database
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.History
import tachiyomi.data.Mangas
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.UpdateStrategyColumnAdapter
import tachiyomi.domain.taste.model.AlternateSourceBridgeMappingReplacement
import tachiyomi.domain.taste.model.AlternateSourceBridgePolicy
import tachiyomi.domain.taste.model.AlternateSourceBridgeState
import tachiyomi.domain.taste.model.AlternateSourceBridgeStateReplacement
import tachiyomi.domain.taste.model.bridge
import tachiyomi.domain.taste.model.bridgeMapping

class AlternateSourceBridgeRepositoryTest {

    private fun repository(): AlternateSourceBridgeRepositoryImpl {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.create(driver)
        val database = Database(
            driver = driver,
            historyAdapter = History.Adapter(last_readAdapter = DateColumnAdapter),
            mangasAdapter = Mangas.Adapter(
                genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = UpdateStrategyColumnAdapter,
                memoAdapter = MemoColumnAdapter,
            ),
            chaptersAdapter = Chapters.Adapter(memoAdapter = MemoColumnAdapter),
        )
        return AlternateSourceBridgeRepositoryImpl(AndroidDatabaseHandler(database, driver))
    }

    @Test
    fun `upsert round trips role ordered route and mapping`() = runTest {
        val repository = repository()
        val state = AlternateSourceBridgeState(bridge(), listOf(bridgeMapping()))

        repository.upsert(state)

        assertEquals(state, repository.get(state.bridge.key))
        assertEquals(listOf(state.bridge), repository.getAllBridges())
        assertEquals(state.mappings, repository.getAllMappings())
    }

    @Test
    fun `replacement is atomic when any expected mapping is stale`() = runTest {
        val repository = repository()
        val original = AlternateSourceBridgeState(bridge(), listOf(bridgeMapping()))
        repository.upsert(original)
        val newerBridge = original.bridge.copy(updatedAt = 3_000)
        val newerMapping = original.mappings.single().copy(updatedAt = 3_000)

        val replaced = repository.replace(
            AlternateSourceBridgeStateReplacement(
                expectedBridge = original.bridge,
                replacementBridge = newerBridge,
                mappingReplacements = listOf(
                    AlternateSourceBridgeMappingReplacement(
                        original.mappings.single().copy(updatedAt = 1_999),
                        newerMapping,
                    ),
                ),
            ),
        )

        assertFalse(replaced)
        assertEquals(original, repository.get(original.bridge.key))
    }

    @Test
    fun `replacement succeeds only against exact current state`() = runTest {
        val repository = repository()
        val original = AlternateSourceBridgeState(bridge(), listOf(bridgeMapping()))
        repository.upsert(original)
        val replacement = AlternateSourceBridgeStateReplacement(
            expectedBridge = original.bridge,
            replacementBridge = original.bridge.copy(updatedAt = 3_000),
            mappingReplacements = listOf(
                AlternateSourceBridgeMappingReplacement(
                    original.mappings.single(),
                    original.mappings.single().copy(updatedAt = 3_000),
                ),
            ),
        )

        assertTrue(repository.replace(replacement))
        assertFalse(repository.replace(replacement))
        assertEquals(3_000L, repository.get(original.bridge.key)?.bridge?.updatedAt)
    }

    @Test
    fun `physical removal through compare and swap removes route and selected mappings`() = runTest {
        val repository = repository()
        val original = AlternateSourceBridgeState(bridge(), listOf(bridgeMapping()))
        repository.upsert(original)

        assertTrue(
            repository.replace(
                AlternateSourceBridgeStateReplacement(
                    expectedBridge = original.bridge,
                    replacementBridge = null,
                    mappingReplacements = listOf(AlternateSourceBridgeMappingReplacement(original.mappings.single(), null)),
                ),
            ),
        )
        assertNull(repository.get(original.bridge.key))
        assertTrue(repository.getAllMappings().isEmpty())
    }

    @Test
    fun `route removal refuses to orphan mappings omitted by the caller`() = runTest {
        val repository = repository()
        val original = AlternateSourceBridgeState(bridge(), listOf(bridgeMapping()))
        repository.upsert(original)

        assertFalse(
            repository.replace(
                AlternateSourceBridgeStateReplacement(
                    expectedBridge = original.bridge,
                    replacementBridge = null,
                ),
            ),
        )
        assertEquals(original, repository.get(original.bridge.key))
    }

    @Test
    fun `clear creates monotonic tombstones without overwriting newer rows`() = runTest {
        val repository = repository()
        val old = AlternateSourceBridgeState(bridge(), listOf(bridgeMapping()))
        val newer = AlternateSourceBridgeState(
            bridge().copy(
                key = bridge().key.copy(alternate = bridge().key.alternate.copy(source = 3, url = "/newer")),
                updatedAt = 5_000,
            ),
            emptyList(),
        )
        repository.upsert(old)
        repository.upsert(newer)

        repository.tombstoneAll(4_000)

        assertEquals(4_000L, repository.get(old.bridge.key)?.bridge?.deletedAt)
        assertNull(repository.get(newer.bridge.key)?.bridge?.deletedAt)
        assertTrue(repository.getAllMappings().single().deletedAt == 4_000L)
    }
}
