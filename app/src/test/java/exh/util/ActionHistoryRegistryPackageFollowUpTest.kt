package exh.util

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import mihon.domain.extension.model.ExtensionStore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK Confirmed Blocker Remediation Corrective Completion Plan V2 2026-07-29 -->
/**
 * Direct tests for [ActionHistoryRegistry]'s `packageFollowUpFor` -- the shared-id correlation that
 * connects a rendered [NonUndoableEvent] Action History row back to its private
 * [PackageOperationReceipt] twin and, only when [PackageOperationFollowUpPolicy] currently judges it
 * safe, exposes a real "Uninstall"/"Reinstall" [ActionHistoryFollowUp].
 *
 * KMK Confirmed Blocker Remediation Corrective Completion Plan V3 2026-07-29 Phase D item 3: previously
 * [ExtensionManager]/[SourcePreferences] were looked up via `Injekt.get()` inside production code, and
 * this file registered fakes into the process-global Injekt graph to reach them -- Injekt's process-wide
 * singleton caching (the first resolved instance for a type is cached for the whole JVM process) made a
 * direct `ActionHistoryFollowUp.trigger()` invocation test order-dependent across the full suite (it
 * passed in isolation, then threw a ClassCastException only when run alongside ~1888 other tests, per the
 * V2-era comment this file used to carry). That test was deleted rather than fixed, which V3 explicitly
 * forbids repeating. Production code now exposes `actionHistoryFollowUpExtensionManagerProvider` /
 * `actionHistoryFollowUpSourcePreferencesProvider` (both `internal var`s in ActionHistoryRegistry.kt) as
 * a narrow seam this file sets directly, bypassing Injekt (and its cache) entirely -- no process-wide
 * state, no fixture-ordering hazard, restored to the real Injekt-backed default in @AfterEach.
 */
class ActionHistoryRegistryPackageFollowUpTest {

    private val extensionManager = mockk<ExtensionManager>(relaxed = true)
    private val sourcePreferences = SourcePreferences(FakePreferenceStore())

    @AfterEach
    fun tearDown() {
        NonUndoableEventJournal.clear()
        PackageOperationJournal.clear()
        actionHistoryFollowUpExtensionManagerProvider = { Injekt.get() }
        actionHistoryFollowUpSourcePreferencesProvider = { Injekt.get() }
    }

    private fun bindFakes() {
        sourcePreferences.evaluationMode().set(true)
        actionHistoryFollowUpExtensionManagerProvider = { extensionManager }
        actionHistoryFollowUpSourcePreferencesProvider = { sourcePreferences }
    }

    private fun installed(
        pkgName: String = "eu.kanade.tachiyomi.extension.en.a",
        signatureHash: String = "sig1",
        versionCode: Long = 1L,
    ) = Extension.Installed(
        name = "A",
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

    private fun available(
        pkgName: String = "eu.kanade.tachiyomi.extension.en.a",
        signatureHash: String = "sig1",
        apkUrl: String = "https://example.invalid/a.apk",
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
        apkUrl = apkUrl,
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

    private fun seed(eventType: NonUndoableEventType, receiptKind: PackageOperationKind, packageName: String = "eu.kanade.tachiyomi.extension.en.a"): String {
        val id = NonUndoableEvent.newId()
        NonUndoableEventJournal.record(NonUndoableEvent(id = id, timestamp = System.currentTimeMillis(), eventType = eventType))
        PackageOperationJournal.record(
            PackageOperationReceipt(
                id = id,
                timestamp = System.currentTimeMillis(),
                kind = receiptKind,
                packageName = packageName,
                signatureHash = "sig1",
                versionCode = 1L,
                artifactUri = "https://example.invalid/a.apk",
            ),
        )
        return id
    }

    @Test
    fun `an event with no matching receipt gets no follow-up`() {
        bindFakes()
        every { extensionManager.installedExtensionsFlow } returns MutableStateFlow(emptyList())
        every { extensionManager.availableExtensionsFlow } returns MutableStateFlow(emptyList())
        NonUndoableEventJournal.record(
            NonUndoableEvent(id = NonUndoableEvent.newId(), timestamp = System.currentTimeMillis(), eventType = NonUndoableEventType.EXTENSION_INSTALLED),
        )

        val row = ActionHistoryRegistry.snapshot().first()

        assertNull(row.followUp, "no PackageOperationReceipt correlates with this event id -- no follow-up should be offered")
    }

    @Test
    fun `a non-package event type never gets a follow-up`() {
        bindFakes()
        every { extensionManager.installedExtensionsFlow } returns MutableStateFlow(emptyList())
        every { extensionManager.availableExtensionsFlow } returns MutableStateFlow(emptyList())
        NonUndoableEventJournal.record(
            NonUndoableEvent(id = NonUndoableEvent.newId(), timestamp = System.currentTimeMillis(), eventType = NonUndoableEventType.MIGRATION_COMPLETED),
        )

        val row = ActionHistoryRegistry.snapshot().first()

        assertNull(row.followUp)
    }

    @Test
    fun `an install event with the exact package still installed offers an Uninstall follow-up`() {
        bindFakes()
        every { extensionManager.installedExtensionsFlow } returns MutableStateFlow(listOf(installed()))
        every { extensionManager.availableExtensionsFlow } returns MutableStateFlow(emptyList())
        seed(NonUndoableEventType.EXTENSION_INSTALLED, PackageOperationKind.INSTALL)

        val row = ActionHistoryRegistry.snapshot().first()

        assertTrue(row.followUp != null, "an exact live match must offer a follow-up")
    }

    @Test
    fun `an install event whose package was since removed offers no follow-up`() {
        bindFakes()
        every { extensionManager.installedExtensionsFlow } returns MutableStateFlow(emptyList())
        every { extensionManager.availableExtensionsFlow } returns MutableStateFlow(emptyList())
        seed(NonUndoableEventType.EXTENSION_INSTALLED, PackageOperationKind.INSTALL)

        val row = ActionHistoryRegistry.snapshot().first()

        assertNull(row.followUp, "the package is no longer installed -- PackageOperationFollowUpPolicy must refuse this")
    }

    // KMK Confirmed Blocker Remediation Corrective Completion Plan V3 2026-07-29 Phase D item 3: the
    // direct followUp.trigger() invocation tests V2 deleted after a full-suite-only ClassCastException
    // (see this file's class doc for the root cause and the seam that now fixes it). Restored below,
    // covering both directions (uninstall-offer, install-offer), a real ExtensionManager method
    // invocation, a new receipt on success, Failed on failure, and no-record when Evaluation Mode is off.

    @Test
    fun `uninstall follow-up trigger calls ExtensionManager uninstallExtension and records a new receipt on verified removal`() = runTest {
        bindFakes()
        val target = installed()
        val installedFlow = MutableStateFlow(listOf(target))
        every { extensionManager.installedExtensionsFlow } returns installedFlow
        every { extensionManager.availableExtensionsFlow } returns MutableStateFlow(emptyList())
        // Simulates the OS uninstall actually completing the moment it's requested -- the flow update
        // is what lets verifyAndRecordUninstall's first{} resolve instead of running out its timeout.
        every { extensionManager.uninstallExtension(target) } answers { installedFlow.value = emptyList() }
        seed(NonUndoableEventType.EXTENSION_INSTALLED, PackageOperationKind.INSTALL)
        val followUp = ActionHistoryRegistry.snapshot().first().followUp ?: error("expected a follow-up to be offered")

        val result = followUp.trigger()

        verify(exactly = 1) { extensionManager.uninstallExtension(target) }
        assertEquals(ActionHistoryFollowUpResult.Started, result)
        assertEquals(1, NonUndoableEventJournal.snapshot().count { it.eventType == NonUndoableEventType.EXTENSION_UNINSTALLED })
        assertEquals(1, PackageOperationJournal.snapshot().count { it.kind == PackageOperationKind.UNINSTALL })
    }

    @Test
    fun `uninstall follow-up trigger returns Failed and records nothing when removal is never observed`() = runTest {
        bindFakes()
        val target = installed()
        // Never leaves the installed list -- verifyAndRecordUninstall's first{} never matches, so its
        // (virtual-time, via runTest) 10s timeout elapses instead of ever recording.
        every { extensionManager.installedExtensionsFlow } returns MutableStateFlow(listOf(target))
        every { extensionManager.availableExtensionsFlow } returns MutableStateFlow(emptyList())
        seed(NonUndoableEventType.EXTENSION_INSTALLED, PackageOperationKind.INSTALL)
        val followUp = ActionHistoryRegistry.snapshot().first().followUp ?: error("expected a follow-up to be offered")

        val result = followUp.trigger()

        verify(exactly = 1) { extensionManager.uninstallExtension(target) }
        assertEquals(ActionHistoryFollowUpResult.Failed, result)
        assertTrue(NonUndoableEventJournal.snapshot().none { it.eventType == NonUndoableEventType.EXTENSION_UNINSTALLED })
    }

    @Test
    fun `uninstall follow-up trigger records nothing when Evaluation Mode is off, even though removal is truthfully observed`() = runTest {
        bindFakes()
        sourcePreferences.evaluationMode().set(false)
        val target = installed()
        val installedFlow = MutableStateFlow(listOf(target))
        every { extensionManager.installedExtensionsFlow } returns installedFlow
        every { extensionManager.availableExtensionsFlow } returns MutableStateFlow(emptyList())
        every { extensionManager.uninstallExtension(target) } answers { installedFlow.value = emptyList() }
        seed(NonUndoableEventType.EXTENSION_INSTALLED, PackageOperationKind.INSTALL)
        val followUp = ActionHistoryRegistry.snapshot().first().followUp ?: error("expected a follow-up to be offered")

        followUp.trigger()

        assertTrue(NonUndoableEventJournal.snapshot().none { it.eventType == NonUndoableEventType.EXTENSION_UNINSTALLED })
        assertTrue(PackageOperationJournal.snapshot().none { it.kind == PackageOperationKind.UNINSTALL })
    }

    @Test
    fun `reinstall follow-up trigger calls ExtensionManager installExtension and records a new INSTALL receipt on success`() = runTest {
        bindFakes()
        val artifact = available()
        every { extensionManager.installedExtensionsFlow } returns MutableStateFlow(emptyList())
        every { extensionManager.availableExtensionsFlow } returns MutableStateFlow(listOf(artifact))
        every { extensionManager.installExtension(artifact) } returns flowOf(InstallStep.Installed)
        seed(NonUndoableEventType.EXTENSION_UNINSTALLED, PackageOperationKind.UNINSTALL)
        val followUp = ActionHistoryRegistry.snapshot().first().followUp ?: error("expected a follow-up to be offered")

        val result = followUp.trigger()

        verify(exactly = 1) { extensionManager.installExtension(artifact) }
        assertEquals(ActionHistoryFollowUpResult.Started, result)
        assertEquals(1, NonUndoableEventJournal.snapshot().count { it.eventType == NonUndoableEventType.EXTENSION_INSTALLED })
        assertEquals(1, PackageOperationJournal.snapshot().count { it.kind == PackageOperationKind.INSTALL })
    }

    @Test
    fun `reinstall follow-up trigger returns Failed and records nothing when the install step errors`() = runTest {
        bindFakes()
        val artifact = available()
        every { extensionManager.installedExtensionsFlow } returns MutableStateFlow(emptyList())
        every { extensionManager.availableExtensionsFlow } returns MutableStateFlow(listOf(artifact))
        every { extensionManager.installExtension(artifact) } returns flowOf(InstallStep.Error)
        seed(NonUndoableEventType.EXTENSION_UNINSTALLED, PackageOperationKind.UNINSTALL)
        val followUp = ActionHistoryRegistry.snapshot().first().followUp ?: error("expected a follow-up to be offered")

        val result = followUp.trigger()

        assertEquals(ActionHistoryFollowUpResult.Failed, result)
        assertTrue(NonUndoableEventJournal.snapshot().none { it.eventType == NonUndoableEventType.EXTENSION_INSTALLED })
        assertTrue(PackageOperationJournal.snapshot().none { it.kind == PackageOperationKind.INSTALL })
    }

    @Test
    fun `reinstall follow-up trigger records nothing when Evaluation Mode is off, even on a successful install`() = runTest {
        bindFakes()
        sourcePreferences.evaluationMode().set(false)
        val artifact = available()
        every { extensionManager.installedExtensionsFlow } returns MutableStateFlow(emptyList())
        every { extensionManager.availableExtensionsFlow } returns MutableStateFlow(listOf(artifact))
        every { extensionManager.installExtension(artifact) } returns flowOf(InstallStep.Installed)
        seed(NonUndoableEventType.EXTENSION_UNINSTALLED, PackageOperationKind.UNINSTALL)
        val followUp = ActionHistoryRegistry.snapshot().first().followUp ?: error("expected a follow-up to be offered")

        val result = followUp.trigger()

        assertEquals(ActionHistoryFollowUpResult.Started, result, "the install itself must still truthfully report success")
        assertTrue(NonUndoableEventJournal.snapshot().none { it.eventType == NonUndoableEventType.EXTENSION_INSTALLED })
        assertTrue(PackageOperationJournal.snapshot().none { it.kind == PackageOperationKind.INSTALL })
    }

    @Test
    fun `an uninstall event with a matching available artifact and nothing reinstalled offers an Install follow-up`() {
        bindFakes()
        every { extensionManager.installedExtensionsFlow } returns MutableStateFlow(emptyList())
        every { extensionManager.availableExtensionsFlow } returns MutableStateFlow(listOf(available()))
        seed(NonUndoableEventType.EXTENSION_UNINSTALLED, PackageOperationKind.UNINSTALL)

        val row = ActionHistoryRegistry.snapshot().first()

        assertTrue(row.followUp != null, "a still-available matching artifact must offer a reinstall follow-up")
    }

    @Test
    fun `an uninstall event with no matching available artifact offers no follow-up`() {
        bindFakes()
        every { extensionManager.installedExtensionsFlow } returns MutableStateFlow(emptyList())
        every { extensionManager.availableExtensionsFlow } returns MutableStateFlow(emptyList())
        seed(NonUndoableEventType.EXTENSION_UNINSTALLED, PackageOperationKind.UNINSTALL)

        val row = ActionHistoryRegistry.snapshot().first()

        assertNull(row.followUp, "no matching available extension exists -- reinstall cannot be offered")
    }

    @Test
    fun `clearing Action History clears both the visible event journal and the private receipt journal`() {
        bindFakes()
        every { extensionManager.installedExtensionsFlow } returns MutableStateFlow(listOf(installed()))
        every { extensionManager.availableExtensionsFlow } returns MutableStateFlow(emptyList())
        seed(NonUndoableEventType.EXTENSION_INSTALLED, PackageOperationKind.INSTALL)
        assertTrue(PackageOperationJournal.isEmpty().not())

        ActionHistoryRegistry.clearAll()

        assertTrue(NonUndoableEventJournal.isEmpty())
        assertTrue(PackageOperationJournal.isEmpty())
    }
}
// KMK <--
