package exh.recs.bestversion.fixture

import exh.util.FakePreferenceStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import java.util.UUID

class BestVersionPairedFixtureRecoveryStoreTest {
    @Test
    fun `manifest round trips and clears from app-state preference`() {
        val preferences = FakePreferenceStore()
        val store = PreferenceBestVersionPairedFixtureRecoveryStore(preferences)
        val manifest = BestVersionPairedFixtureManifest(
            operationId = UUID.randomUUID().toString(),
            completedSteps = setOf(BestVersionPairedFixtureStep.ORIGIN_MANGA),
            originMangaId = 41L,
        )

        assertTrue(store.save(manifest))
        assertEquals(manifest, (store.load() as BestVersionPairedFixtureManifestLoad.Present).manifest)
        assertTrue(store.clear())
        assertInstanceOf(BestVersionPairedFixtureManifestLoad.Missing::class.java, store.load())
    }

    @Test
    fun `corrupt and unsupported records fail closed`() {
        val preferences = FakePreferenceStore()
        val pref = preferences.getString(Preference.appStateKey("best_version_paired_fixture_recovery"), "")
        val store = PreferenceBestVersionPairedFixtureRecoveryStore(preferences)

        pref.set("not-json")
        assertInstanceOf(BestVersionPairedFixtureManifestLoad.Corrupt::class.java, store.load())
        pref.set(
            """{"schemaVersion":99,"revision":"future","operationId":"${UUID.randomUUID()}"}""",
        )
        assertInstanceOf(BestVersionPairedFixtureManifestLoad.Corrupt::class.java, store.load())
    }

    @Test
    fun `write failure is reported and preserves the prior record`() {
        val preferences = FakePreferenceStore()
        val store = PreferenceBestVersionPairedFixtureRecoveryStore(preferences)
        val original = BestVersionPairedFixtureManifest(operationId = UUID.randomUUID().toString())
        assertTrue(store.save(original))

        preferences.failWrites = true
        val replacement = BestVersionPairedFixtureManifest(operationId = UUID.randomUUID().toString())
        assertTrue(!store.save(replacement))
        preferences.failWrites = false
        assertEquals(original, (store.load() as BestVersionPairedFixtureManifestLoad.Present).manifest)
    }
}
