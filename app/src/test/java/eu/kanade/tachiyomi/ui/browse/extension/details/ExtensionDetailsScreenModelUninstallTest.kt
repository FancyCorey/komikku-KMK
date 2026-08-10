package eu.kanade.tachiyomi.ui.browse.extension.details

import android.content.Context
import eu.kanade.domain.extension.interactor.GetExtensionSources
import eu.kanade.domain.source.interactor.ToggleIncognito
import eu.kanade.domain.source.interactor.ToggleSource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.network.NetworkHelper
import exh.util.FakePreferenceStore
import exh.util.NonUndoableEventJournal
import exh.util.NonUndoableEventType
import exh.util.PackageOperationJournal
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK -->
/**
 * Direct caller test for [ExtensionDetailsScreenModel.uninstallExtension] -- the V2 pass wired this
 * call site to [exh.util.verifyAndRecordUninstall] (previously it bypassed Action History entirely) but
 * added no direct test for it, unlike every other uninstall call site this remediation touched. Filling
 * exactly that gap, per V3 Phase D item 1.
 *
 * KMK:
 * [ExtensionDetailsScreenModel] originally dispatched `uninstallExtension()`'s verification coroutine via
 * the top-level `screenModelScope.launchIO {}` extension, hardcoded to real `Dispatchers.IO` with no
 * injection seam -- these tests first shipped polling real wall-clock time with a short bounded timeout
 * as a workaround, which could only prove "nothing is recorded eagerly," not the real 10-second production
 * timeout actually elapsing. `ExtensionDetailsScreenModel` now takes an injectable `ioDispatcher`
 * constructor parameter (same pattern already used by
 * [eu.kanade.tachiyomi.ui.browse.extension.ExtensionsScreenModel] and
 * `exh.recs.bestversion.BestVersionCompareScreenModel`), so these tests share one `UnconfinedTestDispatcher`
 * across `Dispatchers.Main` and `ioDispatcher` -- the same dispatcher `runTest` itself uses -- letting
 * `advanceUntilIdle()` deterministically drive the verification coroutine through real (virtual) time,
 * including the "removal never observed" case genuinely running out the real 10-second timeout rather than
 * only sampling a short real-time window.
 */
class ExtensionDetailsScreenModelUninstallTest {

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        NonUndoableEventJournal.clear()
        PackageOperationJournal.clear()
    }

    private fun installedExtension(pkgName: String, signatureHash: String = "sig1", versionCode: Long = 1L) =
        Extension.Installed(
            name = pkgName.substringAfterLast('.'),
            pkgName = pkgName,
            versionName = "1.0.0",
            versionCode = versionCode,
            libVersion = 1.5,
            lang = "en",
            isNsfw = false,
            signatureHash = signatureHash,
            storeName = null,
            pkgFactory = null,
            sources = emptyList(),
            icon = null,
            isShared = false,
        )

    private fun buildModel(
        pkgName: String,
        installedFlow: MutableStateFlow<List<Extension.Installed>>,
        extensionManager: ExtensionManager,
        dispatcher: CoroutineDispatcher,
        preferences: SourcePreferences = SourcePreferences(FakePreferenceStore()),
    ): ExtensionDetailsScreenModel {
        Dispatchers.setMain(dispatcher)
        every { extensionManager.installedExtensionsFlow } returns installedFlow
        val getExtensionSources = mockk<GetExtensionSources>(relaxed = true)
        every { getExtensionSources.subscribe(any()) } returns MutableStateFlow(emptyList())
        return ExtensionDetailsScreenModel(
            pkgName = pkgName,
            context = mockk<Context>(relaxed = true),
            network = mockk<NetworkHelper>(relaxed = true),
            extensionManager = extensionManager,
            getExtensionSources = getExtensionSources,
            toggleSource = mockk<ToggleSource>(relaxed = true),
            toggleIncognito = mockk<ToggleIncognito>(relaxed = true),
            preferences = preferences,
            ioDispatcher = dispatcher,
        )
    }

    @Test
    fun `a verified removal records exactly one event and receipt when Evaluation Mode is on`() = runTest {
        val extension = installedExtension("eu.kanade.tachiyomi.extension.en.a")
        val preferences = SourcePreferences(FakePreferenceStore())
        preferences.evaluationMode().set(true)
        val extensionManager = mockk<ExtensionManager>(relaxed = true)
        // Starts installed (so the screen model's own init{} populates state.extension the same way it
        // would for a real, currently-installed extension); dropped from the flow right after
        // uninstallExtension() is called to simulate the OS uninstall actually completing, which is what
        // lets verifyAndRecordUninstall's first{} resolve instead of running out its 10s timeout.
        val installedFlow = MutableStateFlow(listOf(extension))
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val model = buildModel(extension.pkgName, installedFlow, extensionManager, dispatcher, preferences)
        advanceUntilIdle()
        assertTrue(model.state.value.extension != null, "sanity: state.extension must be populated before uninstalling")

        model.uninstallExtension()
        installedFlow.value = emptyList()
        advanceUntilIdle()

        verify(exactly = 1) { extensionManager.uninstallExtension(extension) }
        assertEquals(1, NonUndoableEventJournal.snapshot().size)
        assertEquals(NonUndoableEventType.EXTENSION_UNINSTALLED, NonUndoableEventJournal.snapshot().first().eventType)
        assertEquals(1, PackageOperationJournal.snapshot().size)
        assertEquals(extension.pkgName, PackageOperationJournal.snapshot().first().packageName)
    }

    @Test
    fun `removal that never verifies removed times out after the real 10s window and records neither event nor receipt`() = runTest {
        // Unlike the wall-clock-polling version of this test, this now genuinely proves the production
        // 10-second timeout in exh.util.verifyAndRecordUninstall elapses (via advanceUntilIdle()'s virtual
        // time), not merely that nothing is recorded within an arbitrary short real-time sample window.
        val extension = installedExtension("eu.kanade.tachiyomi.extension.en.stuck")
        val preferences = SourcePreferences(FakePreferenceStore())
        preferences.evaluationMode().set(true)
        val extensionManager = mockk<ExtensionManager>(relaxed = true)
        // extension never leaves the installed list -- verifyAndRecordUninstall's first{} never matches.
        val installedFlow = MutableStateFlow(listOf(extension))
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val model = buildModel(extension.pkgName, installedFlow, extensionManager, dispatcher, preferences)
        advanceUntilIdle()
        assertTrue(model.state.value.extension != null, "sanity: state.extension must be populated before uninstalling")

        model.uninstallExtension()
        advanceUntilIdle()

        assertTrue(NonUndoableEventJournal.isEmpty(), "an unverified removal must never record an event")
        assertTrue(PackageOperationJournal.isEmpty(), "an unverified removal must never record a receipt")
    }

    @Test
    fun `Evaluation Mode off records neither event nor receipt even for a verified removal`() = runTest {
        val extension = installedExtension("eu.kanade.tachiyomi.extension.en.b")
        val preferences = SourcePreferences(FakePreferenceStore())
        preferences.evaluationMode().set(false)
        val extensionManager = mockk<ExtensionManager>(relaxed = true)
        val installedFlow = MutableStateFlow(listOf(extension))
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val model = buildModel(extension.pkgName, installedFlow, extensionManager, dispatcher, preferences)
        advanceUntilIdle()
        assertTrue(model.state.value.extension != null, "sanity: state.extension must be populated before uninstalling")

        model.uninstallExtension()
        installedFlow.value = emptyList()
        advanceUntilIdle()

        verify(exactly = 1) { extensionManager.uninstallExtension(extension) }
        assertTrue(NonUndoableEventJournal.isEmpty(), "Evaluation Mode is off -- no event may be recorded")
        assertTrue(PackageOperationJournal.isEmpty(), "Evaluation Mode is off -- no receipt may be recorded")
    }
}
// KMK <--
