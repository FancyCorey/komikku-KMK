package exh.util

import eu.kanade.tachiyomi.extension.model.InstallStep
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK v0.8.20-fix2 -->
class EvaluationModeInstallEventRecorderTest {

    @AfterEach
    fun tearDown() {
        NonUndoableEventJournal.clear()
        PackageOperationJournal.clear()
    }

    @Test
    fun `successful user install records one event before takeWhile`() = runTest {
        flowOf(InstallStep.Pending, InstallStep.Installed)
            .recordUserInitiatedInstall { true }
            .takeWhile { !it.isCompleted() }
            .toList()

        assertEquals(1, NonUndoableEventJournal.snapshot().size)
        assertEquals(NonUndoableEventType.EXTENSION_INSTALLED, NonUndoableEventJournal.snapshot().single().eventType)
    }

    // KMK -->
    @Test
    fun `an update passes EXTENSION_UPDATED, not EXTENSION_INSTALLED`() = runTest {
        flowOf(InstallStep.Pending, InstallStep.Installed)
            .recordUserInitiatedInstall(eventType = NonUndoableEventType.EXTENSION_UPDATED) { true }
            .toList()

        assertEquals(1, NonUndoableEventJournal.snapshot().size)
        assertEquals(NonUndoableEventType.EXTENSION_UPDATED, NonUndoableEventJournal.snapshot().single().eventType)
    }
    // KMK <--

    @Test
    fun `repeated installed state records once per flow`() = runTest {
        flowOf(InstallStep.Installed, InstallStep.Installed)
            .recordUserInitiatedInstall { true }
            .toList()

        assertEquals(1, NonUndoableEventJournal.snapshot().size)
    }

    @Test
    fun `error does not record`() = runTest {
        flowOf(InstallStep.Pending, InstallStep.Error)
            .recordUserInitiatedInstall { true }
            .toList()

        assertEquals(0, NonUndoableEventJournal.snapshot().size)
    }

    @Test
    fun `evaluation mode disabled does not record`() = runTest {
        flowOf(InstallStep.Installed)
            .recordUserInitiatedInstall { false }
            .toList()

        assertEquals(0, NonUndoableEventJournal.snapshot().size)
    }

    @Test
    fun `bulk user installs record one event for each successful flow`() = runTest {
        listOf(
            flowOf(InstallStep.Installed),
            flowOf(InstallStep.Error),
            flowOf(InstallStep.Pending, InstallStep.Installed),
        ).forEach { flow ->
            flow.recordUserInitiatedInstall { true }.toList()
        }

        assertEquals(2, NonUndoableEventJournal.snapshot().size)
    }

    // KMK -->
    @Test
    fun `recordPackageOperationReceipt records a typed receipt with the full identity on success`() = runTest {
        flowOf(InstallStep.Pending, InstallStep.Installed)
            .recordPackageOperationReceipt(
                kind = PackageOperationKind.INSTALL,
                packageName = "eu.kanade.tachiyomi.extension.en.a",
                signatureHash = "sig1",
                versionCode = 5L,
                artifactUri = "https://example.invalid/a.apk",
            ) { true }
            .toList()

        val receipt = PackageOperationJournal.snapshot().single()
        assertEquals(PackageOperationKind.INSTALL, receipt.kind)
        assertEquals("eu.kanade.tachiyomi.extension.en.a", receipt.packageName)
        assertEquals("sig1", receipt.signatureHash)
        assertEquals(5L, receipt.versionCode)
        assertEquals("https://example.invalid/a.apk", receipt.artifactUri)
    }

    @Test
    fun `recordPackageOperationReceipt does not record when the install errors`() = runTest {
        flowOf(InstallStep.Pending, InstallStep.Error)
            .recordPackageOperationReceipt(
                kind = PackageOperationKind.INSTALL,
                packageName = "eu.kanade.tachiyomi.extension.en.a",
                signatureHash = null,
                versionCode = null,
                artifactUri = null,
            ) { true }
            .toList()

        assertTrue(PackageOperationJournal.isEmpty())
    }

    @Test
    fun `recordPackageOperationReceipt does not record when Evaluation Mode is disabled`() = runTest {
        flowOf(InstallStep.Installed)
            .recordPackageOperationReceipt(
                kind = PackageOperationKind.INSTALL,
                packageName = "eu.kanade.tachiyomi.extension.en.a",
                signatureHash = null,
                versionCode = null,
                artifactUri = null,
            ) { false }
            .toList()

        assertTrue(PackageOperationJournal.isEmpty())
    }

    @Test
    fun `recordPackageOperationReceipt records once per flow even if Installed re-emits`() = runTest {
        flowOf(InstallStep.Installed, InstallStep.Installed)
            .recordPackageOperationReceipt(
                kind = PackageOperationKind.UPDATE,
                packageName = "eu.kanade.tachiyomi.extension.en.a",
                signatureHash = null,
                versionCode = null,
                artifactUri = null,
            ) { true }
            .toList()

        assertEquals(1, PackageOperationJournal.snapshot().size)
    }

    @Test
    fun `verifyAndRecordUninstall also records a typed UNINSTALL receipt with the passed identity`() = runTest {
        exh.util.verifyAndRecordUninstall(
            installedPackageNames = flowOf(emptyList()),
            pkgName = "eu.kanade.tachiyomi.extension.en.a",
            isEvaluationModeEnabled = { true },
            signatureHash = "sig1",
            versionCode = 3L,
        )

        val receipt = PackageOperationJournal.snapshot().single()
        assertEquals(PackageOperationKind.UNINSTALL, receipt.kind)
        assertEquals("eu.kanade.tachiyomi.extension.en.a", receipt.packageName)
        assertEquals("sig1", receipt.signatureHash)
        assertEquals(3L, receipt.versionCode)
        assertNull(receipt.artifactUri, "uninstall receipts don't carry a stored artifact URI -- reinstall eligibility resolves it live")
    }

    @Test
    fun `verifyAndRecordUninstall does not record a package receipt when removal is never observed`() = runTest {
        val installed = kotlinx.coroutines.flow.MutableStateFlow(listOf("eu.kanade.tachiyomi.extension.en.a"))
        exh.util.verifyAndRecordUninstall(
            installedPackageNames = installed,
            pkgName = "eu.kanade.tachiyomi.extension.en.a",
            isEvaluationModeEnabled = { true },
            timeoutMillis = 50L,
            signatureHash = "sig1",
            versionCode = 3L,
        )

        assertTrue(PackageOperationJournal.isEmpty())
    }
    // KMK <--
}
// KMK <--
