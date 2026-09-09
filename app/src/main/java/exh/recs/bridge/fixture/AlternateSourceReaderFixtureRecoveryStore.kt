package exh.recs.bridge.fixture

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

sealed interface AlternateSourceReaderFixtureManifestLoad {
    data object Missing : AlternateSourceReaderFixtureManifestLoad
    data class Present(val manifest: AlternateSourceReaderFixtureManifest) : AlternateSourceReaderFixtureManifestLoad
    data object Corrupt : AlternateSourceReaderFixtureManifestLoad
}

interface AlternateSourceReaderFixtureRecoveryStore {
    fun load(now: Long = System.currentTimeMillis()): AlternateSourceReaderFixtureManifestLoad
    fun save(manifest: AlternateSourceReaderFixtureManifest, now: Long = System.currentTimeMillis()): Boolean
    fun clear(): Boolean
}

class PreferenceAlternateSourceReaderFixtureRecoveryStore(
    private val preferenceStore: PreferenceStore,
) : AlternateSourceReaderFixtureRecoveryStore {
    private val preference
        get() = preferenceStore.getString(Preference.appStateKey(RECORD_KEY), "")

    override fun load(now: Long): AlternateSourceReaderFixtureManifestLoad {
        val raw = try {
            preference.get()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return AlternateSourceReaderFixtureManifestLoad.Corrupt
        }
        if (raw.isBlank()) return AlternateSourceReaderFixtureManifestLoad.Missing
        if (raw.length > MAX_RECORD_CHARS) return AlternateSourceReaderFixtureManifestLoad.Corrupt
        val manifest = try {
            Json.decodeFromString(AlternateSourceReaderFixtureManifest.serializer(), raw)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return AlternateSourceReaderFixtureManifestLoad.Corrupt
        }
        return if (manifest.isStructurallyValid(now)) {
            AlternateSourceReaderFixtureManifestLoad.Present(manifest)
        } else {
            AlternateSourceReaderFixtureManifestLoad.Corrupt
        }
    }

    override fun save(manifest: AlternateSourceReaderFixtureManifest, now: Long): Boolean {
        if (!manifest.isStructurallyValid(now)) return false
        val encoded = try {
            Json.encodeToString(AlternateSourceReaderFixtureManifest.serializer(), manifest)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return false
        }
        if (encoded.length > MAX_RECORD_CHARS) return false
        return try {
            // This key shares the app preference file with the nested paired-fixture checkpoint.
            // Async writes can replay an older snapshot after nested cleanup commits its clear.
            preference.commit(encoded)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    override fun clear(): Boolean = try {
        // Keep cleanup ordering durable across the outer and nested fixture coordinators.
        preference.commit("")
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    internal companion object {
        const val RECORD_KEY = "alternate_source_reader_fixture_recovery"
        const val MAX_RECORD_CHARS = 64 * 1_024
    }
}
