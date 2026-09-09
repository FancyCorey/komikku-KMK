package exh.recs.bestversion.fixture

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

sealed interface BestVersionPairedFixtureManifestLoad {
    data object Missing : BestVersionPairedFixtureManifestLoad
    data class Present(val manifest: BestVersionPairedFixtureManifest) : BestVersionPairedFixtureManifestLoad
    data object Corrupt : BestVersionPairedFixtureManifestLoad
}

interface BestVersionPairedFixtureRecoveryStore {
    fun load(): BestVersionPairedFixtureManifestLoad
    fun save(manifest: BestVersionPairedFixtureManifest): Boolean
    fun clear(): Boolean
}

class PreferenceBestVersionPairedFixtureRecoveryStore(
    private val preferenceStore: PreferenceStore,
) : BestVersionPairedFixtureRecoveryStore {
    private val preference
        get() = preferenceStore.getString(Preference.appStateKey(RECORD_KEY), "")

    override fun load(): BestVersionPairedFixtureManifestLoad {
        val raw = try {
            preference.get()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return BestVersionPairedFixtureManifestLoad.Corrupt
        }
        if (raw.isBlank()) return BestVersionPairedFixtureManifestLoad.Missing
        val manifest = try {
            Json.decodeFromString(BestVersionPairedFixtureManifest.serializer(), raw)
        } catch (_: Exception) {
            return BestVersionPairedFixtureManifestLoad.Corrupt
        }
        return if (manifest.isStructurallyValid()) {
            diagnostic("recovery load present operation=${manifest.operationId}")
            BestVersionPairedFixtureManifestLoad.Present(manifest)
        } else {
            BestVersionPairedFixtureManifestLoad.Corrupt
        }
    }

    override fun save(manifest: BestVersionPairedFixtureManifest): Boolean {
        if (!manifest.isStructurallyValid()) return false
        return try {
            // Fixture checkpoints must be durable before the next coordinator step or cleanup
            // begins; an async preference write can otherwise replay a stale operation UUID.
            preference.commit(Json.encodeToString(BestVersionPairedFixtureManifest.serializer(), manifest)).also {
                diagnostic("recovery save operation=${manifest.operationId} result=$it")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    override fun clear(): Boolean = try {
        // Keep the key present but blank so this reset is synchronously visible to every
        // Preference instance sharing the app store.
        preference.commit("").also {
            diagnostic("recovery clear result=$it rawBlank=${preference.get().isBlank()}")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    private fun diagnostic(message: String) {
        runCatching { android.util.Log.w(TAG, message) }
    }

    private companion object {
        const val RECORD_KEY = "best_version_paired_fixture_recovery"
        const val TAG = "KMKFixture"
    }
}
