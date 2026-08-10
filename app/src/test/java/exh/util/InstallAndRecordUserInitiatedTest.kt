package exh.util

import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import mihon.domain.extension.model.ExtensionStore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * KMK: direct tests for the shared
 * [ExtensionManager.installAndRecordUserInitiated] helper -- extracted after
 * `SourceEvaluationScreenModel.reinstallRuntimeHealthExtension()` was found bypassing the
 * `recordUserInitiatedInstall()`/`recordPackageOperationReceipt()` chain entirely. Both
 * `SourceEvaluationScreenModel.installEvaluatedSource()` and `reinstallRuntimeHealthExtension()`
 * now call this exact function, so testing it here (rather than mirroring its shape in a duplicated
 * copy) exercises the real production code path both call sites depend on.
 */
class InstallAndRecordUserInitiatedTest {

    @AfterEach
    fun tearDown() {
        NonUndoableEventJournal.clear()
        PackageOperationJournal.clear()
    }

    private fun availableExtension(
        pkgName: String = "eu.kanade.tachiyomi.extension.en.a",
        signatureHash: String = "sig1",
    ) = Extension.Available(
        name = "A",
        pkgName = pkgName,
        versionName = "1.0.0",
        versionCode = 1L,
        libVersion = 1.5,
        lang = "en",
        isNsfw = false,
        signatureHash = signatureHash,
        storeName = "Store",
        sources = emptyList(),
        apkUrl = "https://example.invalid/a.apk",
        iconUrl = "",
        store = ExtensionStore(
            indexUrl = "https://example.invalid/index.json",
            name = "Store",
            badgeLabel = "Store",
            signingKey = signatureHash,
            contact = ExtensionStore.Contact(website = "", discord = null),
            isLegacy = false,
            extensionListUrl = null,
        ),
    )

    @Test
    fun `a successful install returns Installed and records exactly one correlated event and receipt`() = runTest {
        val ext = availableExtension()
        val extensionManager = mockk<ExtensionManager>()
        every { extensionManager.installExtension(ext) } returns flowOf(InstallStep.Installed)

        val step = extensionManager.installAndRecordUserInitiated(ext) { true }

        assertEquals(InstallStep.Installed, step)
        val events = NonUndoableEventJournal.snapshot()
        val receipts = PackageOperationJournal.snapshot()
        assertEquals(1, events.size)
        assertEquals(1, receipts.size)
        assertEquals(events.single().id, receipts.single().id, "event and receipt must share an id for follow-up correlation")
        assertEquals(NonUndoableEventType.EXTENSION_INSTALLED, events.single().eventType)
        assertEquals(PackageOperationKind.INSTALL, receipts.single().kind)
        assertEquals(ext.pkgName, receipts.single().packageName)
        assertEquals(ext.signatureHash, receipts.single().signatureHash)
        assertEquals(ext.versionCode, receipts.single().versionCode)
        assertEquals(ext.apkUrl, receipts.single().artifactUri)
    }

    @Test
    fun `Evaluation Mode off records nothing even on a successful install`() = runTest {
        val ext = availableExtension()
        val extensionManager = mockk<ExtensionManager>()
        every { extensionManager.installExtension(ext) } returns flowOf(InstallStep.Installed)

        val step = extensionManager.installAndRecordUserInitiated(ext) { false }

        assertEquals(InstallStep.Installed, step)
        assertTrue(NonUndoableEventJournal.snapshot().isEmpty())
        assertTrue(PackageOperationJournal.snapshot().isEmpty())
    }

    @Test
    fun `InstallStep Error records nothing and is returned as the terminal step`() = runTest {
        val ext = availableExtension()
        val extensionManager = mockk<ExtensionManager>()
        every { extensionManager.installExtension(ext) } returns flowOf(InstallStep.Error)

        val step = extensionManager.installAndRecordUserInitiated(ext) { true }

        assertEquals(InstallStep.Error, step)
        assertTrue(NonUndoableEventJournal.snapshot().isEmpty())
        assertTrue(PackageOperationJournal.snapshot().isEmpty())
    }

    @Test
    fun `cancellation records nothing and propagates instead of returning normally`() = runTest {
        val ext = availableExtension()
        val extensionManager = mockk<ExtensionManager>()
        every { extensionManager.installExtension(ext) } returns flow { throw CancellationException("scope cancelled") }

        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking { extensionManager.installAndRecordUserInitiated(ext) { true } }
        }
        assertTrue(NonUndoableEventJournal.snapshot().isEmpty())
        assertTrue(PackageOperationJournal.snapshot().isEmpty())
    }

    @Test
    fun `an ordinary exception from the installer flow records nothing and propagates to the caller`() = runTest {
        val ext = availableExtension()
        val extensionManager = mockk<ExtensionManager>()
        every { extensionManager.installExtension(ext) } returns flow { throw IllegalStateException("network hiccup") }

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { extensionManager.installAndRecordUserInitiated(ext) { true } }
        }
        assertTrue(NonUndoableEventJournal.snapshot().isEmpty())
        assertTrue(PackageOperationJournal.snapshot().isEmpty())
    }

    @Test
    fun `repeated Installed emissions from the installer flow record only once`() = runTest {
        val ext = availableExtension()
        val extensionManager = mockk<ExtensionManager>()
        every { extensionManager.installExtension(ext) } returns flowOf(InstallStep.Installed, InstallStep.Installed)

        extensionManager.installAndRecordUserInitiated(ext) { true }

        assertEquals(1, NonUndoableEventJournal.snapshot().size)
        assertEquals(1, PackageOperationJournal.snapshot().size)
    }

    @Test
    fun `the installer is invoked with the exact extension passed in`() = runTest {
        val ext = availableExtension(pkgName = "eu.kanade.tachiyomi.extension.en.b", signatureHash = "sig2")
        val extensionManager = mockk<ExtensionManager>()
        every { extensionManager.installExtension(ext) } returns flowOf(InstallStep.Installed)

        extensionManager.installAndRecordUserInitiated(ext) { true }

        coVerify(exactly = 1) { extensionManager.installExtension(ext) }
    }

    @Test
    fun `the recorded receipt and event never carry a display name, repo name, or any name field`() = runTest {
        val ext = availableExtension()
        val extensionManager = mockk<ExtensionManager>()
        every { extensionManager.installExtension(ext) } returns flowOf(InstallStep.Installed)

        extensionManager.installAndRecordUserInitiated(ext) { true }

        val eventFields = NonUndoableEvent::class.java.declaredFields
            .filterNot { it.isSynthetic || it.name == "Companion" || it.name.startsWith("$") }
            .map { it.name }
            .toSet()
        assertEquals(setOf("id", "timestamp", "eventType"), eventFields)

        val receiptFields = PackageOperationReceipt::class.java.declaredFields
            .filterNot { it.isSynthetic || it.name == "Companion" || it.name.startsWith("$") }
            .map { it.name }
            .toSet()
        assertFalse("displayName" in receiptFields)
        assertFalse("storeName" in receiptFields)
        assertFalse("sourceName" in receiptFields)
    }
}
