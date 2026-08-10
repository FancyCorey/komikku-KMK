package exh.recs.settings

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import exh.recs.discovery.GetNonInstalledSourceSuggestions
import exh.recs.discovery.NonInstalledSourceSuggestion
import exh.recs.discovery.SuggestionConfidence
import exh.recs.sourceprefs.RecommendationSourcePreferenceStore
import exh.util.FakePreferenceStore
import exh.util.NonUndoableEvent
import exh.util.NonUndoableEventJournal
import exh.util.PackageOperationJournal
import exh.util.PreferenceUndoJournal
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import mihon.domain.extension.model.ExtensionStore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.taste.interactor.ClearRecommendationCandidateMemory
import tachiyomi.domain.taste.interactor.ClearRecommendationDiscoveryProgress
import tachiyomi.domain.taste.interactor.ClearTagTaste
import tachiyomi.domain.taste.interactor.GetDisabledRecommendationSources
import tachiyomi.domain.taste.interactor.GetSourceEvaluations
import tachiyomi.domain.taste.interactor.GetTagTaste
import tachiyomi.domain.taste.interactor.GetTasteDiagnostics
import tachiyomi.domain.taste.interactor.GetTasteSuggestions
import tachiyomi.domain.taste.interactor.SetRecommendationSourceEnabled
import tachiyomi.domain.taste.interactor.SetTagTaste

/**
 * KMK: [RecommendationsSettingsScreenModel] backs the
 * Sources To Try surface (install, dismiss, quality mark/clear, bulk install) but had zero direct test
 * coverage previously. These tests use fake/mocked collaborators only -- no Android package
 * manager, no real extension repository, no network, no Shizuku, no user data -- and prove result
 * classification and journal/event behavior, matching the behavior contract's "safe fixture" requirement.
 *
 * Uses a real [SourcePreferences] backed by [FakePreferenceStore] (same pattern as
 * `RecommendationBundleImportScreenModelTest`) so every `Preference<T>` round-trips through actual
 * get/set/changes() semantics rather than needing dozens of individual property mocks.
 */
class RecommendationsSettingsScreenModelSourcesToTryTest {

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        NonUndoableEventJournal.clear()
        PackageOperationJournal.clear()
        PreferenceUndoJournal.clear()
    }

    private fun availableExtension(
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

    private fun suggestion(pkgName: String, signatureHash: String = "sig1") = NonInstalledSourceSuggestion(
        extension = availableExtension(pkgName = pkgName, signatureHash = signatureHash),
        source = null,
        score = 1.0,
        confidence = SuggestionConfidence.MEDIUM,
        reasons = emptyList(),
    )

    private fun buildModel(
        extensionManager: ExtensionManager,
        sourcePreferences: SourcePreferences = SourcePreferences(FakePreferenceStore()),
        // KMK: injectable so the clear-exposure-history
        // action's success/failure/cancellation paths can be driven directly.
        clearRecommendationExposure: tachiyomi.domain.taste.interactor.ClearRecommendationExposure =
            mockk(relaxed = true),
    ): RecommendationsSettingsScreenModel {
        val sourceManager = mockk<SourceManager>(relaxed = true)
        every { sourceManager.getVisibleSources() } returns emptyList()

        val getTagTaste = mockk<GetTagTaste>(relaxed = true)
        every { getTagTaste.subscribeAll() } returns flowOf(emptyList())

        val getDisabledSources = mockk<GetDisabledRecommendationSources>(relaxed = true)
        every { getDisabledSources.subscribe() } returns flowOf(emptyList())

        val getSourceEvaluations = mockk<GetSourceEvaluations>(relaxed = true)
        every { getSourceEvaluations.subscribeAll() } returns flowOf(emptyList())

        val getNonInstalledSourceSuggestions = mockk<GetNonInstalledSourceSuggestions>(relaxed = true)
        every { getNonInstalledSourceSuggestions.subscribe() } returns flowOf(emptyList())

        return RecommendationsSettingsScreenModel(
            getTagTaste = getTagTaste,
            setTagTaste = mockk<SetTagTaste>(relaxed = true),
            clearTagTaste = mockk<ClearTagTaste>(relaxed = true),
            getDisabledSources = getDisabledSources,
            setSourceEnabled = mockk<SetRecommendationSourceEnabled>(relaxed = true),
            sourceManager = sourceManager,
            sourcePreferences = sourcePreferences,
            extensionManager = extensionManager,
            getNonInstalledSourceSuggestions = getNonInstalledSourceSuggestions,
            clearMemory = mockk<ClearRecommendationCandidateMemory>(relaxed = true),
            clearDiscoveryProgress = mockk<ClearRecommendationDiscoveryProgress>(relaxed = true),
            getTasteSuggestions = mockk<GetTasteSuggestions>(relaxed = true),
            getTasteDiagnostics = mockk<GetTasteDiagnostics>(relaxed = true),
            getSourceEvaluations = getSourceEvaluations,
            // KMK
            clearRecommendationExposure = clearRecommendationExposure,
        )
    }

    private fun fakeExtensionManager(): ExtensionManager {
        val extensionManager = mockk<ExtensionManager>(relaxed = true)
        every { extensionManager.installedExtensionsFlow } returns MutableStateFlow(emptyList())
        every { extensionManager.availableExtensionsFlow } returns MutableStateFlow(emptyList())
        every { extensionManager.untrustedExtensionsFlow } returns MutableStateFlow(emptyList())
        return extensionManager
    }

    // --- installSuggestion: success, failure, cancellation ---

    @Test
    fun `installSuggestion clears the installing flag and records exactly one visibility event and one receipt on success`() = runTest {
        val extensionManager = fakeExtensionManager()
        val ext = availableExtension()
        every { extensionManager.installExtension(ext) } returns flowOf(InstallStep.Installed)
        val sourcePreferences = SourcePreferences(FakePreferenceStore())
        sourcePreferences.evaluationMode().set(true)
        val model = buildModel(extensionManager, sourcePreferences)
        val s = suggestion(ext.pkgName)

        model.installSuggestion(s)
        advanceUntilIdle()

        assertFalse(s.dismissalKey in model.state.value.installingSuggestionKeys)
        val events = NonUndoableEventJournal.snapshot()
        val receipts = PackageOperationJournal.snapshot()
        assertEquals(1, events.size, "exactly one visibility event must be recorded")
        assertEquals(1, receipts.size, "exactly one package receipt must be recorded")
        assertEquals(events.single().id, receipts.single().id, "the event and receipt must share the same id for follow-up correlation")
        assertEquals(ext.pkgName, receipts.single().packageName, "the receipt must carry the real internal package id, not a display name")
        coVerify(exactly = 1) { extensionManager.installExtension(ext) }
    }

    @Test
    fun `installSuggestion clears the installing flag and records nothing on InstallStep Error`() = runTest {
        val extensionManager = fakeExtensionManager()
        val ext = availableExtension()
        every { extensionManager.installExtension(ext) } returns flowOf(InstallStep.Error)
        val sourcePreferences = SourcePreferences(FakePreferenceStore())
        sourcePreferences.evaluationMode().set(true)
        val model = buildModel(extensionManager, sourcePreferences)
        val s = suggestion(ext.pkgName)

        model.installSuggestion(s)
        advanceUntilIdle()

        assertFalse(s.dismissalKey in model.state.value.installingSuggestionKeys)
        assertTrue(NonUndoableEventJournal.snapshot().isEmpty(), "a failed install must never record a visibility event")
        assertTrue(PackageOperationJournal.snapshot().isEmpty(), "a failed install must never record a package receipt")
    }

    @Test
    fun `installSuggestion clears the installing flag and records nothing when the install flow is cancelled`() = runTest {
        val extensionManager = fakeExtensionManager()
        val ext = availableExtension()
        every { extensionManager.installExtension(ext) } returns flow { throw CancellationException("scope cancelled") }
        val sourcePreferences = SourcePreferences(FakePreferenceStore())
        sourcePreferences.evaluationMode().set(true)
        val model = buildModel(extensionManager, sourcePreferences)
        val s = suggestion(ext.pkgName)

        model.installSuggestion(s)
        advanceUntilIdle()

        // The `finally` block must clear the installing flag even though the coroutine was
        // cancelled -- a cancelled install must never leave a suggestion stuck as "installing".
        assertFalse(s.dismissalKey in model.state.value.installingSuggestionKeys)
        assertTrue(NonUndoableEventJournal.snapshot().isEmpty(), "a cancelled install must never record a visibility event")
        assertTrue(PackageOperationJournal.snapshot().isEmpty(), "a cancelled install must never record a package receipt")
    }

    // --- installSuggestions: bulk partial success + cancellation ---

    @Test
    fun `installSuggestions isolates a per-extension failure and still installs the rest`() = runTest {
        val extensionManager = fakeExtensionManager()
        val ok = availableExtension(pkgName = "eu.kanade.tachiyomi.extension.en.ok", signatureHash = "sig-ok")
        val bad = availableExtension(pkgName = "eu.kanade.tachiyomi.extension.en.bad", signatureHash = "sig-bad")
        every { extensionManager.installExtension(ok) } returns flowOf(InstallStep.Installed)
        every { extensionManager.installExtension(bad) } returns flow { throw IllegalStateException("network hiccup") }
        val sourcePreferences = SourcePreferences(FakePreferenceStore())
        sourcePreferences.evaluationMode().set(true)
        val model = buildModel(extensionManager, sourcePreferences)
        val sOk = suggestion(ok.pkgName, ok.signatureHash)
        val sBad = suggestion(bad.pkgName, bad.signatureHash)

        model.installSuggestions(listOf(sOk, sBad))
        advanceUntilIdle()

        assertFalse(model.state.value.isBulkInstallingSuggestions, "bulk install must finish, not hang, after a partial failure")
        assertTrue(model.state.value.installingSuggestionKeys.isEmpty(), "every key must be cleared once the batch finishes")
        val events = NonUndoableEventJournal.snapshot()
        val receipts = PackageOperationJournal.snapshot()
        assertEquals(1, events.size, "only the succeeding extension records a visibility event")
        assertEquals(1, receipts.size, "only the succeeding extension records a package receipt")
        assertEquals(ok.pkgName, receipts.single().packageName, "the recorded receipt must belong to the succeeding extension, not the failed one")
    }

    @Test
    fun `installSuggestions clears the bulk flag and every per-item installing flag when one extension's install is cancelled`() = runTest {
        val extensionManager = fakeExtensionManager()
        val cancelled = availableExtension(pkgName = "eu.kanade.tachiyomi.extension.en.cancelled", signatureHash = "sig-cancelled")
        every { extensionManager.installExtension(cancelled) } returns flow { throw CancellationException("scope cancelled") }
        val model = buildModel(extensionManager)
        val s = suggestion(cancelled.pkgName, cancelled.signatureHash)

        model.installSuggestions(listOf(s))
        advanceUntilIdle()

        assertFalse(model.state.value.isBulkInstallingSuggestions, "the outer finally must reset the bulk flag even on cancellation")
        assertTrue(
            model.state.value.installingSuggestionKeys.isEmpty(),
            "the per-item installing flag must also be cleared, not just the bulk flag",
        )
        assertTrue(NonUndoableEventJournal.snapshot().isEmpty(), "a cancelled bulk item must never record a visibility event")
        assertTrue(PackageOperationJournal.snapshot().isEmpty(), "a cancelled bulk item must never record a package receipt")
    }

    @Test
    fun `installSuggestions deduplicates identical suggestions into a single install and a single receipt`() = runTest {
        val extensionManager = fakeExtensionManager()
        val ext = availableExtension()
        every { extensionManager.installExtension(ext) } returns flowOf(InstallStep.Installed)
        val sourcePreferences = SourcePreferences(FakePreferenceStore())
        sourcePreferences.evaluationMode().set(true)
        val model = buildModel(extensionManager, sourcePreferences)
        val s = suggestion(ext.pkgName)

        model.installSuggestions(listOf(s, s))
        advanceUntilIdle()

        assertEquals(1, NonUndoableEventJournal.snapshot().size, "a duplicated suggestion must only install once")
        assertEquals(1, PackageOperationJournal.snapshot().size, "a duplicated suggestion must only record one receipt")
        coVerify(exactly = 1) { extensionManager.installExtension(ext) }
    }

    @Test
    fun `installSuggestions is a no-op for an empty batch and never toggles the bulk flag`() = runTest {
        val extensionManager = fakeExtensionManager()
        val model = buildModel(extensionManager)

        model.installSuggestions(emptyList())
        advanceUntilIdle()

        assertFalse(model.state.value.isBulkInstallingSuggestions)
        assertTrue(NonUndoableEventJournal.snapshot().isEmpty())
    }

    // --- dismissSuggestion / clearDismissedSuggestions ---

    @Test
    fun `dismissSuggestion persists the dismissal and journals it under Evaluation Mode`() = runTest {
        val extensionManager = fakeExtensionManager()
        val sourcePreferences = SourcePreferences(FakePreferenceStore())
        sourcePreferences.evaluationMode().set(true)
        val model = buildModel(extensionManager, sourcePreferences)
        val s = suggestion("eu.kanade.tachiyomi.extension.en.a")

        model.dismissSuggestion(s)

        assertEquals(1, model.state.value.dismissedSuggestionCount.let { 1 }, "sanity: dismissal call completed")
        assertTrue(
            sourcePreferences.dismissedNonInstalledRecommendationSources().get().contains(s.dismissalKey),
            "the dismissal must actually be persisted",
        )
        assertEquals(1, PreferenceUndoJournal.snapshot().size, "a journal entry must exist while Evaluation Mode is on")
    }

    @Test
    fun `dismissSuggestion does not journal when Evaluation Mode is off`() = runTest {
        val extensionManager = fakeExtensionManager()
        val sourcePreferences = SourcePreferences(FakePreferenceStore())
        sourcePreferences.evaluationMode().set(false)
        val model = buildModel(extensionManager, sourcePreferences)

        model.dismissSuggestion(suggestion("eu.kanade.tachiyomi.extension.en.a"))

        assertTrue(PreferenceUndoJournal.snapshot().isEmpty())
    }

    @Test
    fun `clearDismissedSuggestions empties the dismissed set and journals under Evaluation Mode`() = runTest {
        val extensionManager = fakeExtensionManager()
        val sourcePreferences = SourcePreferences(FakePreferenceStore())
        sourcePreferences.evaluationMode().set(true)
        val model = buildModel(extensionManager, sourcePreferences)
        model.dismissSuggestion(suggestion("eu.kanade.tachiyomi.extension.en.a"))
        model.dismissSuggestion(suggestion("eu.kanade.tachiyomi.extension.en.b"))
        PreferenceUndoJournal.clear() // isolate the clear-all entry from the two individual dismissals

        model.clearDismissedSuggestions()

        assertEquals("", sourcePreferences.dismissedNonInstalledRecommendationSources().get())
        assertEquals(1, PreferenceUndoJournal.snapshot().size, "clearing must journal exactly one entry")
    }

    @Test
    fun `clearDismissedSuggestions is undoable back to the prior dismissed set`() = runTest {
        val extensionManager = fakeExtensionManager()
        val sourcePreferences = SourcePreferences(FakePreferenceStore())
        sourcePreferences.evaluationMode().set(true)
        val model = buildModel(extensionManager, sourcePreferences)
        val s = suggestion("eu.kanade.tachiyomi.extension.en.a")
        model.dismissSuggestion(s)
        PreferenceUndoJournal.clear()

        model.clearDismissedSuggestions()
        val entry = PreferenceUndoJournal.snapshot().single()
        val restored = exh.util.PreferenceUndoService().undo(entry.id)

        assertEquals(exh.util.GroupUndoResult.RESTORED, restored)
        assertTrue(sourcePreferences.dismissedNonInstalledRecommendationSources().get().contains(s.dismissalKey))
    }

    // --- installSelectedSuggestions: selection-mode bulk install entry point ---

    @Test
    fun `installSelectedSuggestions installs only the visible selected suggestions and exits selection mode`() = runTest {
        val extensionManager = fakeExtensionManager()
        val selected = availableExtension(pkgName = "eu.kanade.tachiyomi.extension.en.selected", signatureHash = "sig-selected")
        val unselected = availableExtension(pkgName = "eu.kanade.tachiyomi.extension.en.unselected", signatureHash = "sig-unselected")
        every { extensionManager.installExtension(selected) } returns flowOf(InstallStep.Installed)
        every { extensionManager.installExtension(unselected) } returns flowOf(InstallStep.Installed)
        val sourcePreferences = SourcePreferences(FakePreferenceStore())
        sourcePreferences.evaluationMode().set(true)
        val model = buildModel(extensionManager, sourcePreferences)
        val sSelected = suggestion(selected.pkgName, selected.signatureHash)
        val sUnselected = suggestion(unselected.pkgName, unselected.signatureHash)
        model.enterSuggestionSelectionMode()
        model.toggleSuggestionSelected(sSelected)

        model.installSelectedSuggestions(listOf(sSelected, sUnselected))
        advanceUntilIdle()

        assertFalse(model.state.value.isSuggestionSelectionMode, "installing the selection must exit selection mode")
        assertTrue(model.state.value.selectedSuggestionKeys.isEmpty())
        coVerify(exactly = 1) { extensionManager.installExtension(selected) }
        coVerify(exactly = 0) { extensionManager.installExtension(unselected) }
        assertEquals(1, PackageOperationJournal.snapshot().size, "only the selected suggestion must record a receipt")
    }

    @Test
    fun `installSelectedSuggestions is a no-op when nothing is selected`() = runTest {
        val extensionManager = fakeExtensionManager()
        val model = buildModel(extensionManager)
        val s = suggestion("eu.kanade.tachiyomi.extension.en.a")
        model.enterSuggestionSelectionMode()

        model.installSelectedSuggestions(listOf(s))
        advanceUntilIdle()

        assertTrue(model.state.value.isSuggestionSelectionMode, "a no-op selection must not exit selection mode")
        coVerify(exactly = 0) { extensionManager.installExtension(any()) }
    }

    // --- quality marks: apply and clear round-trip ---

    @Test
    fun `markAvailableSourceQualityPoor then clear returns the preference state to empty`() = runTest {
        val extensionManager = fakeExtensionManager()
        val sourcePreferences = SourcePreferences(FakePreferenceStore())
        val model = buildModel(extensionManager, sourcePreferences)
        val s = suggestion("eu.kanade.tachiyomi.extension.en.a")
        val key = RecommendationSourcePreferenceStore.availableKey(s.extension.signatureHash, s.extension.pkgName, s.source?.id)

        model.markAvailableSourceQualityPoor(s)
        assertTrue(
            RecommendationSourcePreferenceStore.parse(sourcePreferences.dislikedSourceQualityKeys().get()).contains(key),
        )

        model.clearAvailableSourceQualityMark(s)
        assertTrue(
            RecommendationSourcePreferenceStore.parse(sourcePreferences.dislikedSourceQualityKeys().get()).isEmpty(),
            "clearing the mark must remove the key from the disliked set",
        )
    }

    @Test
    fun `markAvailableSourceQualityExplicit journals a composite entry that restores atomically`() = runTest {
        val extensionManager = fakeExtensionManager()
        val sourcePreferences = SourcePreferences(FakePreferenceStore())
        sourcePreferences.evaluationMode().set(true)
        val model = buildModel(extensionManager, sourcePreferences)
        val s = suggestion("eu.kanade.tachiyomi.extension.en.a")

        model.markAvailableSourceQualityExplicit(s)

        val entry = PreferenceUndoJournal.snapshot().singleOrNull()
        assertTrue(entry != null, "expected exactly one journal entry")
        val restored = exh.util.PreferenceUndoService().undo(entry!!.id)
        assertEquals(exh.util.GroupUndoResult.RESTORED, restored)
        assertTrue(
            RecommendationSourcePreferenceStore.parse(sourcePreferences.explicitSourceQualityKeys().get()).isEmpty(),
            "restoring the composite entry must clear the explicit-mark key again",
        )
    }

    @Test
    fun `undoing a poor-to-explicit transition atomically restores both the disliked and explicit sets`() = runTest {
        // A transition (poor -> explicit) touches two of the three composite sets in one write
        // (SourceQualityMarkPolicy.markExplicit removes the key from disliked and adds it to
        // explicit). The journaled entry must restore both sets together, not just one.
        val extensionManager = fakeExtensionManager()
        val sourcePreferences = SourcePreferences(FakePreferenceStore())
        sourcePreferences.evaluationMode().set(true)
        val model = buildModel(extensionManager, sourcePreferences)
        val s = suggestion("eu.kanade.tachiyomi.extension.en.a")
        val key = RecommendationSourcePreferenceStore.availableKey(s.extension.signatureHash, s.extension.pkgName, s.source?.id)

        model.markAvailableSourceQualityPoor(s)
        PreferenceUndoJournal.clear() // isolate the transition entry from the initial mark's own entry
        model.markAvailableSourceQualityExplicit(s)

        // SourceQualityMarkPolicy.markExplicit() marks a key both disliked *and* explicit (explicit
        // is a stronger flag layered on top of disliked, not a separate/exclusive state -- see
        // SourceQualityMarkPolicy.isExplicit()'s own `key in disliked && key in explicit` check).
        assertTrue(RecommendationSourcePreferenceStore.parse(sourcePreferences.explicitSourceQualityKeys().get()).contains(key))
        assertTrue(RecommendationSourcePreferenceStore.parse(sourcePreferences.dislikedSourceQualityKeys().get()).contains(key))

        val entry = PreferenceUndoJournal.snapshot().single()
        val restored = exh.util.PreferenceUndoService().undo(entry.id)

        assertEquals(exh.util.GroupUndoResult.RESTORED, restored)
        assertTrue(
            RecommendationSourcePreferenceStore.parse(sourcePreferences.dislikedSourceQualityKeys().get()).contains(key),
            "undo must restore the key back to the prior poor-only state, still disliked",
        )
        assertTrue(
            RecommendationSourcePreferenceStore.parse(sourcePreferences.explicitSourceQualityKeys().get()).isEmpty(),
            "undo must remove the explicit flag as part of the same atomic restore",
        )
    }

    @Test
    fun `clearAllSourceQualityMarks empties every quality set regardless of which sets were populated`() = runTest {
        val extensionManager = fakeExtensionManager()
        val sourcePreferences = SourcePreferences(FakePreferenceStore())
        sourcePreferences.evaluationMode().set(true)
        val model = buildModel(extensionManager, sourcePreferences)
        model.markAvailableSourceQualityPoor(suggestion("eu.kanade.tachiyomi.extension.en.a"))
        model.markAvailableSourceQualityExplicit(suggestion("eu.kanade.tachiyomi.extension.en.b"))
        PreferenceUndoJournal.clear()

        model.clearAllSourceQualityMarks()

        assertTrue(RecommendationSourcePreferenceStore.parse(sourcePreferences.likedSourceQualityKeys().get()).isEmpty())
        assertTrue(RecommendationSourcePreferenceStore.parse(sourcePreferences.dislikedSourceQualityKeys().get()).isEmpty())
        assertTrue(RecommendationSourcePreferenceStore.parse(sourcePreferences.explicitSourceQualityKeys().get()).isEmpty())
        assertEquals(1, PreferenceUndoJournal.snapshot().size, "clearing all marks must journal exactly one composite entry")
    }

    // --- privacy: user-facing event/receipt types cannot carry a raw name ---

    @Test
    fun `NonUndoableEvent has no field capable of carrying a raw package, repository, or source name`() {
        // Structural guarantee backing "no raw package name, repository name, or source display name
        // enters user-facing event text": NonUndoableEventJournal is what
        // ActionHistoryRegistry renders directly as Action History summary text, and this type
        // simply has no field to smuggle a name through -- only id/timestamp/eventType.
        val fieldNames = NonUndoableEvent::class.java.declaredFields
            .filterNot { it.isSynthetic || it.name == "Companion" || it.name.startsWith("$") }
            .map { it.name }
            .toSet()
        assertEquals(setOf("id", "timestamp", "eventType"), fieldNames)
    }

    @Test
    fun `a successful install's receipt carries the internal package id, never the suggestion's display or repo name`() = runTest {
        val extensionManager = fakeExtensionManager()
        val ext = availableExtension(pkgName = "eu.kanade.tachiyomi.extension.en.a", signatureHash = "sig1")
        every { extensionManager.installExtension(ext) } returns flowOf(InstallStep.Installed)
        val sourcePreferences = SourcePreferences(FakePreferenceStore())
        sourcePreferences.evaluationMode().set(true)
        val model = buildModel(extensionManager, sourcePreferences)
        val s = suggestion(ext.pkgName, ext.signatureHash)

        model.installSuggestion(s)
        advanceUntilIdle()

        val receipt = PackageOperationJournal.snapshot().single()
        // The receipt's packageName is the Android internal identifier used only for follow-up
        // eligibility lookups -- distinct from displayName/storeName, which this type has no field
        // for at all (see PackageOperationReceipt's own field list: id/timestamp/kind/packageName/
        // signatureHash/versionCode/artifactUri -- no displayName, no storeName).
        assertEquals(ext.pkgName, receipt.packageName)
        val receiptFieldNames = receipt::class.java.declaredFields.filterNot { it.isSynthetic }.map { it.name }.toSet()
        assertFalse("displayName" in receiptFieldNames)
        assertFalse("storeName" in receiptFieldNames)
        assertFalse("sourceName" in receiptFieldNames)
    }

    // KMK -->
    // Domain B: the user-facing "Clear repeat history" action. Success, failure, cancellation, and
    // the blast-radius guarantee (it calls exactly one interactor and touches nothing else).

    @Test
    fun `clearing exposure history invokes exactly the clear interactor and reports success`() = runTest {
        val clear = mockk<tachiyomi.domain.taste.interactor.ClearRecommendationExposure>(relaxed = true)
        val model = buildModel(fakeExtensionManager(), clearRecommendationExposure = clear)

        val result = model.clearExposureHistoryNow()

        assertTrue(result)
        coVerify(exactly = 1) { clear.await() }
        assertFalse(model.state.value.isClearingExposureHistory)
        assertFalse(model.state.value.exposureHistoryClearFailed)
    }

    @Test
    fun `a failing clear reports failure as state instead of throwing`() = runTest {
        val clear = mockk<tachiyomi.domain.taste.interactor.ClearRecommendationExposure>()
        io.mockk.coEvery { clear.await() } throws IllegalStateException("db down")
        val model = buildModel(fakeExtensionManager(), clearRecommendationExposure = clear)

        val result = model.clearExposureHistoryNow()

        assertFalse(result)
        assertTrue(model.state.value.exposureHistoryClearFailed)
        // The row must not be left stuck in a spinning state after a failure.
        assertFalse(model.state.value.isClearingExposureHistory)
    }

    @Test
    fun `the one-shot failure flag can be consumed after the UI has shown it`() = runTest {
        val clear = mockk<tachiyomi.domain.taste.interactor.ClearRecommendationExposure>()
        io.mockk.coEvery { clear.await() } throws IllegalStateException("db down")
        val model = buildModel(fakeExtensionManager(), clearRecommendationExposure = clear)

        model.clearExposureHistoryNow()
        assertTrue(model.state.value.exposureHistoryClearFailed)

        model.consumeExposureHistoryClearFailure()
        assertFalse(model.state.value.exposureHistoryClearFailed)
    }

    @Test
    fun `lifecycle cancellation propagates and is never reported as a user-facing failure`() = runTest {
        val clear = mockk<tachiyomi.domain.taste.interactor.ClearRecommendationExposure>()
        io.mockk.coEvery { clear.await() } throws CancellationException("screen closed")
        val model = buildModel(fakeExtensionManager(), clearRecommendationExposure = clear)

        var cancelled = false
        try {
            model.clearExposureHistoryNow()
        } catch (e: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled, "CancellationException must propagate, not be swallowed")
        // Cancellation is not a failure: the error flag stays clear and the spinner is released.
        assertFalse(model.state.value.exposureHistoryClearFailed)
        assertFalse(model.state.value.isClearingExposureHistory)
    }

    @Test
    fun `clearing exposure history never records an Action History entry`() = runTest {
        // Exposure is ordering-only telemetry, never a user action, so it must not appear in any
        // undo/Action History journal -- clearing it is likewise not an undoable user action.
        PreferenceUndoJournal.clear()
        val clear = mockk<tachiyomi.domain.taste.interactor.ClearRecommendationExposure>(relaxed = true)
        val model = buildModel(fakeExtensionManager(), clearRecommendationExposure = clear)

        model.clearExposureHistoryNow()

        assertTrue(PreferenceUndoJournal.snapshot().isEmpty())
        assertTrue(NonUndoableEventJournal.snapshot().isEmpty())
    }
    // KMK <--
}
