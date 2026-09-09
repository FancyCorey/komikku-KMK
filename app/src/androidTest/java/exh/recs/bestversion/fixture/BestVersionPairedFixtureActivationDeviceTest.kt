package exh.recs.bestversion.fixture

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.kanade.tachiyomi.extension.ExtensionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Reports the real ExtensionManager boundary used by the paired fixture gate. */
@RunWith(AndroidJUnit4::class)
class BestVersionPairedFixtureActivationDeviceTest {

    @Test
    fun pairedFixtureExtensionsAreLoadedAsExpected() {
        val installed = Injekt.get<ExtensionManager>().installedExtensionsFlow.value
        val fixtures = installed.filter { it.pkgName in EXPECTED_PACKAGES }

        fixtures.forEach { extension ->
            Log.i(
                TAG,
                "fixture package=${extension.pkgName} name=${extension.name} version=${extension.versionName} " +
                    "versionCode=${extension.versionCode} signer=${extension.signatureHash} " +
                    "sources=${extension.sources.map { source -> "${source.id}:${source.name}:${source.lang}" }}",
            )
        }

        assertEquals(
            "ExtensionManager loaded fixture packages: ${fixtures.map { it.pkgName }}",
            EXPECTED_PACKAGES,
            fixtures.map { it.pkgName }.toSet(),
        )
        assertTrue(
            "Every paired fixture must expose exactly one source: ${fixtures.map { it.pkgName to it.sources.size }}",
            fixtures.all { it.sources.size == 1 },
        )
        assertTrue(
            "Every paired fixture must expose its expected source id: ${fixtures.map { it.pkgName to it.sources.map { source -> source.id } }}",
            fixtures.all { extension -> extension.sources.single().id in EXPECTED_SOURCE_IDS },
        )

        val activation = BestVersionPairedFixtureRuntime().activation()
        Log.i(
            TAG,
            "activation debug=${activation.isDebugBuild} mode=${activation.mode} " +
                "evaluation=${activation.evaluationModeEnabled} profile=${activation.fixtureProfile} " +
                "expectedSigner=${activation.expectedSignerSha256} allowed=${BestVersionPairedFixtureGate.isAllowed(activation)}",
        )
    }

    private companion object {
        const val TAG = "BestVersionFixtureDevice"
        val EXPECTED_PACKAGES = setOf(
            BestVersionPairedFixtureGate.ALPHA_PACKAGE,
            BestVersionPairedFixtureGate.BETA_PACKAGE,
        )
        val EXPECTED_SOURCE_IDS = setOf(
            BestVersionPairedFixtureGate.ALPHA_SOURCE_ID,
            BestVersionPairedFixtureGate.BETA_SOURCE_ID,
        )
    }
}
