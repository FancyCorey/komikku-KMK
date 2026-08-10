package exh.recs.share

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.source.Source
import exh.util.FakePreferenceStore
import exh.util.NonUndoableEventJournal
import exh.util.NonUndoableEventType
import exh.util.PackageOperationJournal
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.domain.extension.model.ExtensionStore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager

// KMK Confirmed Blocker Remediation Corrective Completion Plan V2 2026-07-29 -->
/**
 * Direct tests for [RecommendationBundleImportScreenModel]'s [RecommendationBundleImportScreenModel.load]
 * -- previously deferred as too fixture-costly since it requires a real Android [Context] (for
 * [ContentResolver.openInputStream] and moko-resources' `Context.stringResource`) with no Robolectric in
 * this module. Both are mockable directly: `openInputStream` is stubbed to a real in-memory stream, and
 * a relaxed [Context] mock lets moko-resources' internal `getString`/`resources` calls return harmless
 * defaults without crashing, since these tests only assert on the resulting [LoadErrorKey] type, never on
 * localized message text. `screenModelScope` (hardcoded to `Dispatchers.Main.immediate` by Voyager) is
 * redirected via [Dispatchers.setMain] with an [UnconfinedTestDispatcher], mirroring
 * `MigrationListScreenModelOutcomeTest`'s pattern -- `load()` itself never hops dispatchers, so no
 * injectable `ioDispatcher` parameter is needed here.
 */
class RecommendationBundleImportScreenModelTest {

    private val json = Json { encodeDefaults = false }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Uri::class)
        NonUndoableEventJournal.clear()
        PackageOperationJournal.clear()
    }

    private fun sampleBundle() = RecommendationBundle(
        kmkRecsVersion = "KMK-Recs v0.7.5",
        appVersionName = "1.13.6",
        createdAt = 1_000_000L,
        title = "Test Top Picks",
        bundleType = RecommendationBundleType.TOP_PICKS,
        requiredSources = emptyList(),
        items = listOf(
            // sourceId=0 (local source) resolves to Unsupported without touching any source/extension
            // lookups -- keeps this fixture focused on load()'s Context-dependent read/validate path.
            RecommendationBundleItem(
                title = "Test Manga",
                url = "/manga/test-slug",
                sourceId = 0L,
                sourceName = "Local",
                sourceLang = "en",
            ),
        ),
    )

    private fun fakeContext(bytes: ByteArray?): Context {
        val contentResolver = mockk<ContentResolver>()
        if (bytes != null) {
            every { contentResolver.openInputStream(any()) } returns bytes.inputStream()
        } else {
            every { contentResolver.openInputStream(any()) } returns null
        }
        val context = mockk<Context>(relaxed = true)
        every { context.contentResolver } returns contentResolver
        return context
    }

    private fun mockUri(): Uri {
        mockkStatic(Uri::class)
        val uri = mockk<Uri>(relaxed = true)
        every { Uri.parse(any()) } returns uri
        return uri
    }

    private fun fakeExtensionManager(
        installed: List<Extension.Installed> = emptyList(),
        available: List<Extension.Available> = emptyList(),
    ): ExtensionManager {
        val extensionManager = mockk<ExtensionManager>(relaxed = true)
        every { extensionManager.installedExtensionsFlow } returns MutableStateFlow(installed)
        every { extensionManager.availableExtensionsFlow } returns MutableStateFlow(available)
        return extensionManager
    }

    private fun buildModel(
        context: Context,
        extensionManager: ExtensionManager = fakeExtensionManager(),
        sourcePreferences: SourcePreferences = SourcePreferences(FakePreferenceStore()),
        getManga: GetManga = mockk(relaxed = true),
        libraryAdder: RecommendationBundleLibraryAdder = mockk(relaxed = true),
    ): RecommendationBundleImportScreenModel {
        mockUri()
        return RecommendationBundleImportScreenModel(
            uriString = "content://fake/bundle.json",
            context = context,
            sourceManager = mockk<SourceManager>(relaxed = true),
            extensionManager = extensionManager,
            sourcePreferences = sourcePreferences,
            getManga = getManga,
            networkToLocalManga = mockk<NetworkToLocalManga>(relaxed = true),
            // RecommendationBundleLibraryAdder's own default constructor resolves several
            // dependencies (including SourceManager) via Injekt.get() -- a relaxed mock avoids needing
            // a full Injekt bootstrap here unless a test specifically needs addToLibrary() stubbed.
            libraryAdder = libraryAdder,
        )
    }

    private fun fakeSource(sourceId: Long, name: String = "Src", lang: String = "en"): Source {
        val source = mockk<Source>(relaxed = true)
        every { source.id } returns sourceId
        every { source.name } returns name
        every { source.lang } returns lang
        return source
    }

    private fun installedExtensionWithSource(
        sourceId: Long,
        pkgName: String = "eu.kanade.tachiyomi.extension.en.a",
        signatureHash: String = "sig1",
    ) = Extension.Installed(
        name = "A",
        pkgName = pkgName,
        versionName = "1.0.0",
        versionCode = 1L,
        libVersion = 1.5,
        lang = "en",
        isNsfw = false,
        signatureHash = signatureHash,
        storeName = null,
        pkgFactory = null,
        sources = listOf(fakeSource(sourceId)),
        icon = null,
        isShared = false,
    )

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

    private fun readyToAddBundle(sourceId: Long) = RecommendationBundle(
        kmkRecsVersion = "KMK-Recs v0.7.5",
        appVersionName = "1.13.6",
        createdAt = 1_000_000L,
        title = "Test Top Picks",
        bundleType = RecommendationBundleType.TOP_PICKS,
        requiredSources = emptyList(),
        items = listOf(
            RecommendationBundleItem(
                title = "Test Manga",
                url = "/manga/test-slug",
                sourceId = sourceId,
                sourceName = "Src",
                sourceLang = "en",
            ),
        ),
    )

    @Test
    fun `a valid bundle loads into Preview state with items resolved`() = runTest {
        val bytes = json.encodeToString(sampleBundle()).encodeToByteArray()
        val model = buildModel(fakeContext(bytes))

        val state = model.state.value as? RecommendationBundleImportScreenModel.State.Preview
        assertTrue(state != null, "expected Preview state, got ${model.state.value}")
        assertEquals(1, state!!.items.size)
        assertTrue(
            state.items.first().itemState is RecommendationImportItemState.Unsupported,
            "sourceId=0 items must resolve to Unsupported, got ${state.items.first().itemState}",
        )
    }

    @Test
    fun `malformed json produces a LoadError instead of crashing`() = runTest {
        val bytes = "{ not valid json".encodeToByteArray()
        val model = buildModel(fakeContext(bytes))

        val state = model.state.value
        assertTrue(
            state is RecommendationBundleImportScreenModel.State.LoadError &&
                state.error is LoadErrorKey.MalformedJson,
            "expected LoadError(MalformedJson), got $state",
        )
    }

    @Test
    fun `a null input stream from the content resolver is reported as a load error, not a crash`() = runTest {
        val model = buildModel(fakeContext(bytes = null))

        val state = model.state.value
        assertTrue(
            state is RecommendationBundleImportScreenModel.State.LoadError &&
                state.error is LoadErrorKey.MalformedJson,
            "expected LoadError(MalformedJson) for a failed file open, got $state",
        )
    }

    @Test
    fun `an unsupported schema version is reported distinctly`() = runTest {
        val wrongVersionJson = """
            {
                "schema":"kmk.recommendation.bundle",
                "schemaVersion":999,
                "kmkRecsVersion":"v0.7.5",
                "createdAt":1000000,
                "title":"Test",
                "bundleType":"TOP_PICKS",
                "items":[]
            }
        """.trimIndent()
        val model = buildModel(fakeContext(wrongVersionJson.encodeToByteArray()))

        val state = model.state.value
        assertTrue(
            state is RecommendationBundleImportScreenModel.State.LoadError &&
                state.error is LoadErrorKey.UnsupportedVersion,
            "expected LoadError(UnsupportedVersion), got $state",
        )
    }

    // KMK Confirmed Blocker Remediation Corrective Completion Plan V2 2026-07-29: direct tests for
    // addSelected() and installMissingExtension() -- previously deferred as a coverage gap since a
    // full model construction was assumed too costly, but the same mocked-Context/ExtensionManager
    // fixtures already built for load() above are enough to drive both methods directly.
    @Test
    fun `addSelected adds a ReadyToAdd item to the library and records a truthful summary`() = runTest {
        val sourceId = 555L
        val manga = Manga.create().copy(id = 1L, url = "/manga/test-slug", favorite = false)
        val getManga = mockk<GetManga>(relaxed = true)
        coEvery { getManga.await("/manga/test-slug", sourceId) } returns manga
        val libraryAdder = mockk<RecommendationBundleLibraryAdder>(relaxed = true)
        coEvery { libraryAdder.addToLibrary(any(), any(), any()) } returns
            RecommendationBundleLibraryAdder.AddResult(manga, RecommendationBundleLibraryAdder.Outcome.Added)

        val bytes = json.encodeToString(readyToAddBundle(sourceId)).encodeToByteArray()
        val model = buildModel(
            context = fakeContext(bytes),
            extensionManager = fakeExtensionManager(installed = listOf(installedExtensionWithSource(sourceId))),
            getManga = getManga,
            libraryAdder = libraryAdder,
        )
        val preview = model.state.value as? RecommendationBundleImportScreenModel.State.Preview
        assertTrue(preview != null, "expected Preview state, got ${model.state.value}")
        assertTrue(
            preview!!.items.first().itemState is RecommendationImportItemState.ReadyToAdd,
            "expected ReadyToAdd, got ${preview.items.first().itemState}",
        )
        assertTrue(0 in preview.selectedIndices, "a ReadyToAdd item must be selected by default")

        model.addSelected()
        advanceUntilIdle()

        coVerify(exactly = 1) { libraryAdder.addToLibrary(manga, skipDuplicates = true, categoryIds = emptyList()) }
        val finalState = model.state.value as RecommendationBundleImportScreenModel.State.Preview
        assertEquals(AddSummary(added = 1, alreadyInLibrary = 0, failed = 0), finalState.addSummary)
        assertTrue(!finalState.isAdding)
    }

    @Test
    fun `addSelected reports a truthful partial summary when one item succeeds and one fails, never claiming full completion`() = runTest {
        // KMK Confirmed Blocker Remediation Corrective Completion Plan V3 2026-07-29 Phase D item 4:
        // the existing addSelected() test only covered a single-item, all-succeeds bundle -- this proves
        // the summary distinguishes a real partial outcome (1 added, 1 failed) instead of collapsing it
        // into a false "fully completed" report, and that addSelected() is still called directly on the
        // screen model, not through a shared/indirect operator.
        val sourceId = 555L
        val mangaOk = Manga.create().copy(id = 1L, url = "/manga/ok", favorite = false)
        val mangaBad = Manga.create().copy(id = 2L, url = "/manga/bad", favorite = false)
        val getManga = mockk<GetManga>(relaxed = true)
        coEvery { getManga.await("/manga/ok", sourceId) } returns mangaOk
        coEvery { getManga.await("/manga/bad", sourceId) } returns mangaBad
        val libraryAdder = mockk<RecommendationBundleLibraryAdder>(relaxed = true)
        coEvery { libraryAdder.addToLibrary(mangaOk, any(), any()) } returns
            RecommendationBundleLibraryAdder.AddResult(mangaOk, RecommendationBundleLibraryAdder.Outcome.Added)
        coEvery { libraryAdder.addToLibrary(mangaBad, any(), any()) } returns
            RecommendationBundleLibraryAdder.AddResult(mangaBad, RecommendationBundleLibraryAdder.Outcome.Error("boom"))

        val bundle = RecommendationBundle(
            kmkRecsVersion = "KMK-Recs v0.7.5",
            appVersionName = "1.13.6",
            createdAt = 1_000_000L,
            title = "Test Top Picks",
            bundleType = RecommendationBundleType.TOP_PICKS,
            requiredSources = emptyList(),
            items = listOf(
                RecommendationBundleItem(title = "OK", url = "/manga/ok", sourceId = sourceId, sourceName = "Src", sourceLang = "en"),
                RecommendationBundleItem(title = "Bad", url = "/manga/bad", sourceId = sourceId, sourceName = "Src", sourceLang = "en"),
            ),
        )
        val bytes = json.encodeToString(bundle).encodeToByteArray()
        val model = buildModel(
            context = fakeContext(bytes),
            extensionManager = fakeExtensionManager(installed = listOf(installedExtensionWithSource(sourceId))),
            getManga = getManga,
            libraryAdder = libraryAdder,
        )
        val preview = model.state.value as? RecommendationBundleImportScreenModel.State.Preview
        assertTrue(preview != null, "expected Preview state, got ${model.state.value}")
        assertEquals(2, preview!!.items.size)

        model.addSelected()
        advanceUntilIdle()

        coVerify(exactly = 1) { libraryAdder.addToLibrary(mangaOk, skipDuplicates = true, categoryIds = emptyList()) }
        coVerify(exactly = 1) { libraryAdder.addToLibrary(mangaBad, skipDuplicates = true, categoryIds = emptyList()) }
        val finalState = model.state.value as RecommendationBundleImportScreenModel.State.Preview
        assertEquals(
            AddSummary(added = 1, alreadyInLibrary = 0, failed = 1),
            finalState.addSummary,
            "a partial outcome must be reported truthfully, not collapsed into an all-succeeded summary",
        )
        assertTrue(!finalState.isAdding)
    }

    @Test
    fun `installMissingExtension records an install receipt and re-resolves items on success`() = runTest {
        val sourceId = 555L
        val ext = availableExtension(pkgName = "eu.kanade.tachiyomi.extension.en.a")
        val sourcePreferences = SourcePreferences(FakePreferenceStore())
        sourcePreferences.evaluationMode().set(true)
        val extensionManager = fakeExtensionManager(available = listOf(ext))
        every { extensionManager.installExtension(ext) } returns flowOf(InstallStep.Installed)

        val bytes = json.encodeToString(readyToAddBundle(sourceId)).encodeToByteArray()
        val model = buildModel(
            context = fakeContext(bytes),
            extensionManager = extensionManager,
            sourcePreferences = sourcePreferences,
        )
        // sourceId 555 isn't installed, and no extension metadata is on the bundle item, so this
        // starts as MissingSource -- installMissingExtension() doesn't depend on that item state at
        // all, it only needs a Preview state to exist and the given Extension.Available to install.

        model.installMissingExtension(ext)

        val events = NonUndoableEventJournal.snapshot()
        assertEquals(1, events.size)
        assertEquals(NonUndoableEventType.EXTENSION_INSTALLED, events.first().eventType)
        val receipts = PackageOperationJournal.snapshot()
        assertEquals(1, receipts.size)
        assertEquals(ext.pkgName, receipts.first().packageName)
        assertEquals(events.first().id, receipts.first().id, "the event and receipt must share the same id for follow-up correlation")

        val finalState = model.state.value as RecommendationBundleImportScreenModel.State.Preview
        assertNull(finalState.installingPkgName, "installingPkgName must be cleared once the install completes")
    }

    // KMK security-hardening pass 2026-07-31: resolveItems()/resolveItem() previously wrapped each
    // item's resolution in `runCatching { ... }.getOrElse { ... Error(...) }`, which -- since
    // runCatching catches Throwable -- silently converted a cancelled coroutine into a per-item
    // Error state instead of propagating the cancellation. Fixed to an explicit try/catch that
    // rethrows CancellationException. A CancellationException thrown inside a launched coroutine
    // cancels that coroutine's job rather than escaping synchronously to the caller of `launch`, so
    // this asserts the *absence* of the previous bad behavior (the item is never misreported as a
    // per-item Error, and the model never reaches a false Preview/completed state) rather than
    // asserting a synchronous throw.
    @Test
    fun `cancellation while resolving a bundle item during load never surfaces as a false per-item Error`() = runTest {
        val sourceId = 555L
        val getManga = mockk<GetManga>(relaxed = true)
        coEvery { getManga.await("/manga/test-slug", sourceId) } throws
            CancellationException("scope cancelled")

        val bytes = json.encodeToString(readyToAddBundle(sourceId)).encodeToByteArray()
        val model = buildModel(
            context = fakeContext(bytes),
            extensionManager = fakeExtensionManager(installed = listOf(installedExtensionWithSource(sourceId))),
            getManga = getManga,
        )

        // load()'s coroutine was cancelled before it could call mutableState.value = State.Preview(...),
        // so the model must still be sitting at its initial Loading state -- never a Preview whose item
        // was silently downgraded to Error the way the pre-fix runCatching{}.getOrElse{} used to do.
        assertTrue(
            model.state.value is RecommendationBundleImportScreenModel.State.Loading,
            "a cancelled load() must leave state at Loading, not a false Preview/Error, got ${model.state.value}",
        )
    }

    @Test
    fun `an ordinary exception while resolving a bundle item during load is reported as a per-item Error, not a crash`() = runTest {
        val sourceId = 555L
        val getManga = mockk<GetManga>(relaxed = true)
        coEvery { getManga.await("/manga/test-slug", sourceId) } throws IllegalStateException("db hiccup")

        val bytes = json.encodeToString(readyToAddBundle(sourceId)).encodeToByteArray()
        val model = buildModel(
            context = fakeContext(bytes),
            extensionManager = fakeExtensionManager(installed = listOf(installedExtensionWithSource(sourceId))),
            getManga = getManga,
        )

        val state = model.state.value as? RecommendationBundleImportScreenModel.State.Preview
        assertTrue(state != null, "expected Preview state, got ${model.state.value}")
        assertTrue(
            state!!.items.first().itemState is RecommendationImportItemState.Error,
            "expected a per-item Error state for the failed resolution, got ${state.items.first().itemState}",
        )
    }
}
// KMK <--
