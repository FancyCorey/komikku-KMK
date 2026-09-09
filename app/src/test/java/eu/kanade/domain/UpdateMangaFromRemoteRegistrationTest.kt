package eu.kanade.domain

import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import io.mockk.mockk
import mihon.domain.source.interactor.UpdateMangaFromRemote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.taste.interactor.ClearCrossSourceGroupPrimary
import tachiyomi.domain.taste.interactor.GetCrossSourceGroupPrimary
import tachiyomi.domain.taste.interactor.SetCrossSourceGroupPrimary
import tachiyomi.domain.taste.repository.TasteRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addFactory
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

// KMK v0.8.10-fix1 -->
/**
 * Regression coverage for the confirmed application-wide crash: `UpdateMangaFromRemote` was never
 * registered in `DomainModule.kt`, so `BulkFavoriteScreenModel`'s constructor default arg
 * `Injekt.get()` (line 60) threw an uncaught `InjektionException` immediately on construction --
 * which happens for any Browse/Global Search/manga-update bulk-selection flow (real device stack
 * trace: `BulkFavoriteScreenModel.kt:60` -> `BrowseTab.kt:90` -> `HomeScreen`).
 *
 * Unit tests never bootstrap the app's real production Injekt graph (`App.onCreate()` is the only
 * place `DomainModule`/`KMKDomainModule` are actually imported, and most of their other factories
 * need Android Context/DB access this environment doesn't have) -- so rather than importing the real
 * modules wholesale, these tests mirror the exact registration shape now added to `DomainModule.kt`
 * against mocked leaf dependencies. This directly proves the constructor arity/order that was added
 * is correct against the real `UpdateMangaFromRemote`/`BulkFavoriteScreenModel` classes, and that the
 * pre-existing `GetCrossSourceGroupPrimary`/`SetCrossSourceGroupPrimary`/`ClearCrossSourceGroupPrimary`
 * registrations this fix must not disturb still resolve correctly (the v0.8.1-fix2 regression this
 * fix class doc explicitly calls out as "do not modify unless a new test proves broken").
 */
class UpdateMangaFromRemoteRegistrationTest {

    // Injekt is a process-global singleton with last-registration-wins semantics per type; each
    // test below re-registers exactly the bindings it needs, so no explicit teardown is required
    // (matching the existing TestInjektSupport / GetCrossSourceGroupPrimary test precedent in this
    // codebase, neither of which resets Injekt between tests either).

    private fun bindUpdateMangaFromRemoteDependencies() {
        Injekt.importModule(
            object : InjektModule {
                override fun InjektRegistrar.registerInjectables() {
                    addSingletonFactory<SourceManager> { mockk(relaxed = true) }
                    addSingletonFactory<ChapterRepository> { mockk(relaxed = true) }
                    addSingletonFactory<MangaRepository> { mockk(relaxed = true) }
                    addSingletonFactory<SyncChaptersWithSource> { mockk(relaxed = true) }
                    addSingletonFactory<CoverCache> { mockk(relaxed = true) }
                    addSingletonFactory<LibraryPreferences> { mockk(relaxed = true) }
                    addSingletonFactory<DownloadManager> { mockk(relaxed = true) }
                    // Mirrors the exact registration this fix adds to DomainModule.kt.
                    addFactory {
                        UpdateMangaFromRemote(
                            get(),
                            get(),
                            get(),
                            get(),
                            get(),
                            get(),
                            get(),
                        )
                    }
                }
            },
        )
    }

    @Test
    fun `UpdateMangaFromRemote resolves through Injekt once its seven dependencies are bound`() {
        bindUpdateMangaFromRemoteDependencies()
        // A thrown InjektionException fails this test naturally (JUnit reports any uncaught
        // exception as a test failure) -- no wrapping needed for the resolution itself.
        val resolved: UpdateMangaFromRemote = Injekt.get()
        assertEquals(UpdateMangaFromRemote::class, resolved::class)
    }

    @Test
    fun `all seven UpdateMangaFromRemote constructor dependencies resolve independently`() {
        bindUpdateMangaFromRemoteDependencies()
        // Each Injekt.get() below throws InjektionException (failing this test) if unresolved --
        // no wrapping needed; reaching the final line is the assertion that all seven resolved.
        val sourceManager: SourceManager = Injekt.get()
        val chapterRepository: ChapterRepository = Injekt.get()
        val mangaRepository: MangaRepository = Injekt.get()
        val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get()
        val coverCache: CoverCache = Injekt.get()
        val libraryPreferences: LibraryPreferences = Injekt.get()
        val downloadManager: DownloadManager = Injekt.get()
        // Reaching this line is the assertion: each Injekt.get() above throws InjektionException
        // (failing the test) if its binding is missing.
        assertEquals(7, listOf(sourceManager, chapterRepository, mangaRepository, syncChaptersWithSource, coverCache, libraryPreferences, downloadManager).size)
    }

    @Test
    fun `BulkFavoriteScreenModel constructs successfully once UpdateMangaFromRemote is registered`() {
        bindUpdateMangaFromRemoteDependencies()
        Injekt.importModule(
            object : InjektModule {
                override fun InjektRegistrar.registerInjectables() {
                    addSingletonFactory<tachiyomi.domain.manga.interactor.GetDuplicateLibraryManga> { mockk(relaxed = true) }
                    addSingletonFactory<tachiyomi.domain.category.interactor.GetCategories> { mockk(relaxed = true) }
                    addSingletonFactory<tachiyomi.domain.category.interactor.SetMangaCategories> { mockk(relaxed = true) }
                    addSingletonFactory<eu.kanade.domain.manga.interactor.UpdateManga> { mockk(relaxed = true) }
                    addSingletonFactory<tachiyomi.domain.chapter.interactor.SetMangaDefaultChapterFlags> { mockk(relaxed = true) }
                    addSingletonFactory<eu.kanade.domain.track.interactor.AddTracks> { mockk(relaxed = true) }
                    // KMK: BulkFavoriteScreenModel gained a SourcePreferences
                    // default constructor arg for Evaluation Mode library-undo journaling.
                    addSingletonFactory<eu.kanade.domain.source.service.SourcePreferences> { mockk(relaxed = true) }
                }
            },
        )
        // This is exactly the crash: BulkFavoriteScreenModel's default constructor args all resolve
        // via Injekt.get() at construction time -- before this fix, this line threw.
        val screenModel = BulkFavoriteScreenModel()
        assertEquals(BulkFavoriteScreenModel::class, screenModel::class)
    }

    @Test
    fun `the pre-existing cross-source group-primary registrations this fix must not disturb still resolve`() {
        Injekt.importModule(
            object : InjektModule {
                override fun InjektRegistrar.registerInjectables() {
                    addSingletonFactory<TasteRepository> { mockk(relaxed = true) }
                    addFactory { GetCrossSourceGroupPrimary(get()) }
                    addFactory { SetCrossSourceGroupPrimary(get()) }
                    addFactory { ClearCrossSourceGroupPrimary(get()) }
                }
            },
        )
        val getPrimary: GetCrossSourceGroupPrimary = Injekt.get()
        val setPrimary: SetCrossSourceGroupPrimary = Injekt.get()
        val clearPrimary: ClearCrossSourceGroupPrimary = Injekt.get()
        assertEquals(GetCrossSourceGroupPrimary::class, getPrimary::class)
        assertEquals(SetCrossSourceGroupPrimary::class, setPrimary::class)
        assertEquals(ClearCrossSourceGroupPrimary::class, clearPrimary::class)
    }
}
// KMK <--
