package eu.kanade.tachiyomi.ui.browse.extension

import android.app.Application
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.extension.interactor.GetExtensionsByType
import eu.kanade.domain.extension.model.Extensions
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import exh.util.FakePreferenceStore
import exh.util.NonUndoableEventJournal
import exh.util.NonUndoableEventType
import exh.util.PackageOperationJournal
import io.mockk.coVerify
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
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingletonFactory

// KMK Confirmed Blocker Remediation Corrective Completion Plan V2 2026-07-29 -->
/**
 * Direct tests for [ExtensionsScreenModel.uninstallSelectedExtensions] -- previously untestable since
 * every background operation in this class used the top-level `launchIO {}` extension, hardcoded to
 * the real `Dispatchers.IO` with no way for a test to redirect it. The class now accepts `ioDispatcher`
 * as a constructor parameter (defaulting to the exact same `Dispatchers.IO` in production), which lets
 * these tests substitute a deterministic `TestDispatcher` sharing `runTest`'s own virtual-time scheduler
 * for every one of the 8 `launchIO`-turned-`launch(ioDispatcher)` call sites, including
 * `uninstallSelectedExtensions()` itself and the `uninstallExtension()` call it delegates to per item.
 *
 * `init {}` still calls `Injekt.get<Application>()` directly (not a constructor parameter, left
 * unchanged -- out of this pass's scope), so a mocked `Application` is registered into Injekt once per
 * JVM process via a companion-held instance, mirroring [exh.util.ActionHistoryRegistryPackageFollowUpTest]'s
 * documented workaround for Injekt's process-wide singleton caching (a fresh mock per JUnit5 test
 * instance would silently be ignored after the first test resolves it).
 */
class ExtensionsScreenModelUninstallSelectedTest {

    companion object {
        private val application = mockk<Application>(relaxed = true)
        private var applicationBound = false
    }

    private fun bindApplication() {
        if (applicationBound) return
        applicationBound = true
        Injekt.importModule(
            object : InjektModule {
                override fun InjektRegistrar.registerInjectables() {
                    addSingletonFactory<Application> { application }
                }
            },
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        NonUndoableEventJournal.clear()
        PackageOperationJournal.clear()
    }

    private fun installedExtension(
        pkgName: String,
        signatureHash: String = "sig1",
        versionCode: Long = 1L,
    ) = Extension.Installed(
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

    private fun untrustedExtension(pkgName: String, signatureHash: String = "sig1") = Extension.Untrusted(
        name = pkgName.substringAfterLast('.'),
        pkgName = pkgName,
        versionName = "1.0.0",
        versionCode = 1L,
        libVersion = 1.5,
        signatureHash = signatureHash,
    )

    private fun item(extension: Extension, installStep: InstallStep = InstallStep.Idle) =
        ExtensionUiModel.Item(extension, installStep)

    private fun buildModel(
        items: ItemGroups,
        selectedKeys: Set<String>,
        extensionManager: ExtensionManager,
        preferences: SourcePreferences = SourcePreferences(FakePreferenceStore()),
        dispatcher: CoroutineDispatcher,
    ): ExtensionsScreenModel {
        bindApplication()
        Dispatchers.setMain(dispatcher)
        val getExtensions = mockk<GetExtensionsByType>(relaxed = true)
        every { getExtensions.subscribe() } returns MutableStateFlow(Extensions(emptyList(), emptyList(), emptyList(), emptyList()))
        val model = ExtensionsScreenModel(
            preferences = preferences,
            basePreferences = mockk<BasePreferences>(relaxed = true),
            extensionManager = extensionManager,
            getExtensions = getExtensions,
            ioDispatcher = dispatcher,
        )
        // Bypass the (unrelated, out-of-scope) real subscribe()/combine() pipeline entirely -- seed
        // `items`/`selectedExtensionKeys` directly via the public selection API and a forced state
        // write, the same reflection pattern already used for BestVersionCompareScreenModel, so these
        // tests exercise uninstallSelectedExtensions()'s own filtering/loop/cleanup logic in isolation.
        forceItemsAndSelection(model, items, selectedKeys)
        return model
    }

    @Suppress("UNCHECKED_CAST")
    private fun forceItemsAndSelection(model: ExtensionsScreenModel, items: ItemGroups, selectedKeys: Set<String>) {
        val field = cafe.adriel.voyager.core.model.StateScreenModel::class.java.getDeclaredField("mutableState")
        field.isAccessible = true
        val flow = field.get(model) as kotlinx.coroutines.flow.MutableStateFlow<ExtensionsScreenModel.State>
        flow.value = flow.value.copy(
            isLoading = false,
            items = items,
            isExtensionSelectionMode = true,
            selectedExtensionKeys = selectedKeys,
        )
    }

    private fun key(extension: Extension): String = when (extension) {
        is Extension.Installed -> extension.pkgName + "_${extension.signatureHash}"
        is Extension.Untrusted -> extension.pkgName + "_${extension.signatureHash}"
        else -> error("unsupported")
    }

    @Test
    fun `selected installed extensions are passed to uninstall`() = runTest {
        val extA = installedExtension("eu.kanade.tachiyomi.extension.en.a")
        val extB = installedExtension("eu.kanade.tachiyomi.extension.en.b")
        val extensionManager = mockk<ExtensionManager>(relaxed = true)
        every { extensionManager.installedExtensionsFlow } returns MutableStateFlow(emptyList())
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val model = buildModel(
            items = mapOf(ExtensionUiModel.Header.Text("Installed") to listOf(item(extA), item(extB))),
            selectedKeys = setOf(key(extA), key(extB)),
            extensionManager = extensionManager,
            dispatcher = dispatcher,
        )

        model.uninstallSelectedExtensions()
        advanceUntilIdle()

        verify(exactly = 1) { extensionManager.uninstallExtension(extA) }
        verify(exactly = 1) { extensionManager.uninstallExtension(extB) }
    }

    @Test
    fun `untrusted extensions are included in bulk uninstall`() = runTest {
        val untrusted = untrustedExtension("eu.kanade.tachiyomi.extension.en.untrusted")
        val extensionManager = mockk<ExtensionManager>(relaxed = true)
        every { extensionManager.installedExtensionsFlow } returns MutableStateFlow(emptyList())
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val model = buildModel(
            items = mapOf(ExtensionUiModel.Header.Text("Installed") to listOf(item(untrusted))),
            selectedKeys = setOf(key(untrusted)),
            extensionManager = extensionManager,
            dispatcher = dispatcher,
        )

        model.uninstallSelectedExtensions()
        advanceUntilIdle()

        verify(exactly = 1) { extensionManager.uninstallExtension(untrusted) }
    }

    @Test
    fun `unselected extensions are ignored`() = runTest {
        val extA = installedExtension("eu.kanade.tachiyomi.extension.en.a")
        val extB = installedExtension("eu.kanade.tachiyomi.extension.en.b")
        val extensionManager = mockk<ExtensionManager>(relaxed = true)
        every { extensionManager.installedExtensionsFlow } returns MutableStateFlow(emptyList())
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val model = buildModel(
            items = mapOf(ExtensionUiModel.Header.Text("Installed") to listOf(item(extA), item(extB))),
            selectedKeys = setOf(key(extA)),
            extensionManager = extensionManager,
            dispatcher = dispatcher,
        )

        model.uninstallSelectedExtensions()
        advanceUntilIdle()

        verify(exactly = 1) { extensionManager.uninstallExtension(extA) }
        verify(exactly = 0) { extensionManager.uninstallExtension(extB) }
    }

    @Test
    fun `incomplete or installing extensions are ignored even when selected`() = runTest {
        val installing = installedExtension("eu.kanade.tachiyomi.extension.en.installing")
        val extensionManager = mockk<ExtensionManager>(relaxed = true)
        every { extensionManager.installedExtensionsFlow } returns MutableStateFlow(emptyList())
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val model = buildModel(
            items = mapOf(ExtensionUiModel.Header.Text("Installed") to listOf(item(installing, InstallStep.Installing))),
            selectedKeys = setOf(key(installing)),
            extensionManager = extensionManager,
            dispatcher = dispatcher,
        )

        model.uninstallSelectedExtensions()
        advanceUntilIdle()

        verify(exactly = 0) { extensionManager.uninstallExtension(any()) }
    }

    @Test
    fun `empty selection is a no-op`() = runTest {
        val extensionManager = mockk<ExtensionManager>(relaxed = true)
        every { extensionManager.installedExtensionsFlow } returns MutableStateFlow(emptyList())
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val model = buildModel(
            items = emptyMap(),
            selectedKeys = emptySet(),
            extensionManager = extensionManager,
            dispatcher = dispatcher,
        )

        model.uninstallSelectedExtensions()
        advanceUntilIdle()

        verify(exactly = 0) { extensionManager.uninstallExtension(any()) }
        assertTrue(!model.state.value.isBulkUninstallingExtensions)
    }

    @Test
    fun `bulk uninstall resets selection and bulk state once every item is processed`() = runTest {
        val extA = installedExtension("eu.kanade.tachiyomi.extension.en.a")
        val extB = installedExtension("eu.kanade.tachiyomi.extension.en.b")
        val extensionManager = mockk<ExtensionManager>(relaxed = true)
        // Both already reflect as "removed" -- verifyAndRecordUninstall's first{} resolves immediately
        // instead of waiting out its 10s timeout, keeping this test's virtual-time advance bounded.
        every { extensionManager.installedExtensionsFlow } returns MutableStateFlow(emptyList())
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val model = buildModel(
            items = mapOf(ExtensionUiModel.Header.Text("Installed") to listOf(item(extA), item(extB))),
            selectedKeys = setOf(key(extA), key(extB)),
            extensionManager = extensionManager,
            dispatcher = dispatcher,
        )

        model.uninstallSelectedExtensions()
        advanceUntilIdle()

        assertTrue(!model.state.value.isBulkUninstallingExtensions)
        assertTrue(!model.state.value.isExtensionSelectionMode)
        assertTrue(model.state.value.selectedExtensionKeys.isEmpty())
    }

    @Test
    fun `partial completion records a receipt only for the extension that actually verified removed`() = runTest {
        val removed = installedExtension("eu.kanade.tachiyomi.extension.en.removed")
        val stuck = installedExtension("eu.kanade.tachiyomi.extension.en.stuck")
        val preferences = SourcePreferences(FakePreferenceStore())
        preferences.evaluationMode().set(true)
        val extensionManager = mockk<ExtensionManager>(relaxed = true)
        // `removed` is never in this flow (its uninstall verifies immediately); `stuck` is always in
        // it (never verifies, times out after 10s of virtual time instead of ever recording).
        every { extensionManager.installedExtensionsFlow } returns MutableStateFlow(listOf(stuck))
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val model = buildModel(
            items = mapOf(ExtensionUiModel.Header.Text("Installed") to listOf(item(removed), item(stuck))),
            selectedKeys = setOf(key(removed), key(stuck)),
            extensionManager = extensionManager,
            preferences = preferences,
            dispatcher = dispatcher,
        )

        model.uninstallSelectedExtensions()
        advanceUntilIdle()

        val events = NonUndoableEventJournal.snapshot()
        assertEquals(1, events.size, "only the extension that actually verified removed may be recorded")
        assertEquals(NonUndoableEventType.EXTENSION_UNINSTALLED, events.first().eventType)
        val receipts = PackageOperationJournal.snapshot()
        assertEquals(1, receipts.size)
        assertEquals(removed.pkgName, receipts.first().packageName)
    }

    @Test
    fun `Evaluation Mode off records no receipt even for a verified removal`() = runTest {
        val extA = installedExtension("eu.kanade.tachiyomi.extension.en.a")
        val preferences = SourcePreferences(FakePreferenceStore())
        preferences.evaluationMode().set(false)
        val extensionManager = mockk<ExtensionManager>(relaxed = true)
        every { extensionManager.installedExtensionsFlow } returns MutableStateFlow(emptyList())
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val model = buildModel(
            items = mapOf(ExtensionUiModel.Header.Text("Installed") to listOf(item(extA))),
            selectedKeys = setOf(key(extA)),
            extensionManager = extensionManager,
            preferences = preferences,
            dispatcher = dispatcher,
        )

        model.uninstallSelectedExtensions()
        advanceUntilIdle()

        assertTrue(NonUndoableEventJournal.isEmpty())
        assertTrue(PackageOperationJournal.isEmpty())
    }

    @Test
    fun `an uninstall that never verifies removed times out and records no receipt`() = runTest {
        // KMK Confirmed Blocker Remediation Corrective Completion Plan V2 2026-07-29: an earlier
        // version of this test called model.onDispose() expecting it to cancel the in-flight
        // verification coroutine -- that assumption is wrong for this codebase (screenModelScope's
        // cancellation is tied to Voyager's ScreenModelStore removing the model via a real navigation
        // event, not to calling onDispose() directly) and, combined with a similar wrong assumption in
        // BestVersionCompareScreenModelConfirmMigrationTest, hung the whole test suite. This test keeps
        // its real, useful assertion -- extA never verifies removed, so no receipt may ever be recorded
        // -- without the misleading disposal framing; verifyAndRecordUninstall's own 10s timeout (fully
        // virtual time here) is what actually ends the wait, not any cancellation this test triggers.
        val extA = installedExtension("eu.kanade.tachiyomi.extension.en.a")
        val preferences = SourcePreferences(FakePreferenceStore())
        preferences.evaluationMode().set(true)
        val extensionManager = mockk<ExtensionManager>(relaxed = true)
        // extA never leaves this flow -- verifyAndRecordUninstall's first{} never matches, so it runs
        // out its own bounded timeout instead of ever recording.
        every { extensionManager.installedExtensionsFlow } returns MutableStateFlow(listOf(extA))
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val model = buildModel(
            items = mapOf(ExtensionUiModel.Header.Text("Installed") to listOf(item(extA))),
            selectedKeys = setOf(key(extA)),
            extensionManager = extensionManager,
            preferences = preferences,
            dispatcher = dispatcher,
        )

        model.uninstallSelectedExtensions()
        advanceUntilIdle()

        assertTrue(NonUndoableEventJournal.isEmpty(), "an uninstall that never verifies removed must never record a receipt")
        assertTrue(PackageOperationJournal.isEmpty())
    }

    @Test
    fun `bulk uninstall of 3 verified-removed extensions records exactly one receipt each, never duplicated`() = runTest {
        // KMK Confirmed Blocker Remediation Corrective Completion Plan V3 2026-07-29 Phase D item 2:
        // uninstallSelectedExtensions() runs one outer screenModelScope.launch(ioDispatcher) that loops
        // over every selected item and, per item, calls uninstallExtension() -- which itself launches a
        // *second*, independent screenModelScope.launch(ioDispatcher) to run verifyAndRecordUninstall().
        // That nested-launch structure means N selected items can end up with N concurrently-running
        // verification coroutines. This test proves that concurrency never causes a receipt to be
        // recorded more than once per item (e.g. from a shared mutable read racing across the nested
        // launches) -- exactly 3 events and 3 receipts for 3 distinct, all-immediately-verified items,
        // never fewer (a dropped item) or more (a duplicated one).
        val extA = installedExtension("eu.kanade.tachiyomi.extension.en.a")
        val extB = installedExtension("eu.kanade.tachiyomi.extension.en.b")
        val extC = installedExtension("eu.kanade.tachiyomi.extension.en.c")
        val preferences = SourcePreferences(FakePreferenceStore())
        preferences.evaluationMode().set(true)
        val extensionManager = mockk<ExtensionManager>(relaxed = true)
        // All three already reflect as removed -- every nested verification coroutine resolves
        // immediately and concurrently rather than serially.
        every { extensionManager.installedExtensionsFlow } returns MutableStateFlow(emptyList())
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val model = buildModel(
            items = mapOf(ExtensionUiModel.Header.Text("Installed") to listOf(item(extA), item(extB), item(extC))),
            selectedKeys = setOf(key(extA), key(extB), key(extC)),
            extensionManager = extensionManager,
            preferences = preferences,
            dispatcher = dispatcher,
        )

        model.uninstallSelectedExtensions()
        advanceUntilIdle()

        verify(exactly = 1) { extensionManager.uninstallExtension(extA) }
        verify(exactly = 1) { extensionManager.uninstallExtension(extB) }
        verify(exactly = 1) { extensionManager.uninstallExtension(extC) }
        val events = NonUndoableEventJournal.snapshot()
        assertEquals(3, events.size, "exactly one event per item -- none dropped, none duplicated")
        assertEquals(
            setOf(extA.pkgName, extB.pkgName, extC.pkgName),
            PackageOperationJournal.snapshot().map { it.packageName }.toSet(),
        )
        assertEquals(3, PackageOperationJournal.snapshot().size)
    }

    @Test
    fun `the existing helper-level uninstall verification remains covered`() = runTest {
        // Sanity check that this test file's fixtures exercise the exact same verifyAndRecordUninstall
        // contract exh.util.VerifyAndRecordUninstallTest already covers at the function level --
        // proving the screen-model call site actually reaches it, not re-testing the function itself.
        val extA = installedExtension("eu.kanade.tachiyomi.extension.en.a")
        val preferences = SourcePreferences(FakePreferenceStore())
        preferences.evaluationMode().set(true)
        val extensionManager = mockk<ExtensionManager>(relaxed = true)
        every { extensionManager.installedExtensionsFlow } returns MutableStateFlow(emptyList())
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val model = buildModel(
            items = mapOf(ExtensionUiModel.Header.Text("Installed") to listOf(item(extA))),
            selectedKeys = setOf(key(extA)),
            extensionManager = extensionManager,
            preferences = preferences,
            dispatcher = dispatcher,
        )

        model.uninstallExtension(extA)
        advanceUntilIdle()

        coVerify(exactly = 1) { extensionManager.uninstallExtension(extA) }
        assertEquals(1, NonUndoableEventJournal.snapshot().size)
    }
}
// KMK <--
