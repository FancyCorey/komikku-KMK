package exh.recs.bridge.fixture

import exh.util.FakePreferenceStore
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import java.util.UUID

class AlternateSourceReaderFixtureRecoveryStoreTest {
    @Test
    fun `manifest round trips and clears from its separate app-state key`() {
        val preferences = FakePreferenceStore()
        val store = PreferenceAlternateSourceReaderFixtureRecoveryStore(preferences)
        val manifest = manifest()

        assertTrue(store.save(manifest, NOW))
        assertEquals(manifest, (store.load(NOW) as AlternateSourceReaderFixtureManifestLoad.Present).manifest)
        assertTrue(store.clear())
        assertInstanceOf(AlternateSourceReaderFixtureManifestLoad.Missing::class.java, store.load(NOW))
    }

    @Test
    fun `malformed unsupported stale and oversized records fail closed`() {
        val preferences = FakePreferenceStore()
        val pref = preferences.getString(
            Preference.appStateKey(PreferenceAlternateSourceReaderFixtureRecoveryStore.RECORD_KEY),
            "",
        )
        val store = PreferenceAlternateSourceReaderFixtureRecoveryStore(preferences)

        pref.set("not-json")
        assertInstanceOf(AlternateSourceReaderFixtureManifestLoad.Corrupt::class.java, store.load(NOW))

        pref.set(Json.encodeToString(AlternateSourceReaderFixtureManifest.serializer(), manifest().copy(schemaVersion = 99)))
        assertInstanceOf(AlternateSourceReaderFixtureManifestLoad.Corrupt::class.java, store.load(NOW))

        pref.set(
            Json.encodeToString(
                AlternateSourceReaderFixtureManifest.serializer(),
                manifest().copy(createdAt = NOW - AlternateSourceReaderFixtureManifest.MAX_AGE_MS - 1L),
            ),
        )
        assertInstanceOf(AlternateSourceReaderFixtureManifestLoad.Corrupt::class.java, store.load(NOW))

        pref.set("x".repeat(PreferenceAlternateSourceReaderFixtureRecoveryStore.MAX_RECORD_CHARS + 1))
        assertInstanceOf(AlternateSourceReaderFixtureManifestLoad.Corrupt::class.java, store.load(NOW))
    }

    @Test
    fun `invalid save is refused without replacing a valid recovery record`() {
        val preferences = FakePreferenceStore()
        val store = PreferenceAlternateSourceReaderFixtureRecoveryStore(preferences)
        val original = manifest()
        assertTrue(store.save(original, NOW))

        assertTrue(!store.save(original.copy(revision = "future"), NOW))
        assertEquals(original, (store.load(NOW) as AlternateSourceReaderFixtureManifestLoad.Present).manifest)
    }

    @Test
    fun `write failure is reported and preserves prior recovery`() {
        val preferences = FakePreferenceStore()
        val store = PreferenceAlternateSourceReaderFixtureRecoveryStore(preferences)
        val original = manifest()
        assertTrue(store.save(original, NOW))

        preferences.failWrites = true
        assertTrue(!store.save(manifest().copy(operationId = UUID.randomUUID().toString()), NOW))
        preferences.failWrites = false
        assertEquals(original, (store.load(NOW) as AlternateSourceReaderFixtureManifestLoad.Present).manifest)
    }

    private fun manifest() = AlternateSourceReaderFixtureManifest(
        operationId = UUID.randomUUID().toString(),
        pairedFixtureOperationId = UUID.randomUUID().toString(),
        scenario = AlternateSourceReaderFixtureScenario.MISSING,
        createdAt = NOW,
        completedSteps = setOf(AlternateSourceReaderFixtureStep.PAIRED_GRAPH),
        pendingStep = AlternateSourceReaderFixtureStep.BASELINES,
        primaryMangaId = 11L,
        alternateMangaId = 12L,
        bridgeBaselineHash = "A".repeat(64),
        identityBaselineHash = "B".repeat(64),
    )

    private companion object {
        const val NOW = 1_000_000L
    }
}
