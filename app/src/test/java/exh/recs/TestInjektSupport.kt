package exh.recs

// KMK --> v0.7.44: shared Injekt test bootstrap
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.repository.CustomMangaRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingletonFactory

/**
 * Shared Injekt bindings for pure unit tests that construct a `Manga` with `favorite = true`.
 *
 * `Manga`'s constructor eagerly resolves [GetCustomMangaInfo] via Injekt whenever `favorite` is
 * true (see `Manga.kt`'s `customMangaInfo` property). Unit tests never bootstrap the app's real
 * Injekt graph (that only happens in `App.onCreate`), so any test building a favorite manga threw
 * `InjektionException` before this binding was registered. Call [ensureCustomMangaInfoBound] once
 * per affected test class (e.g. from a JUnit5 `@BeforeAll`) rather than duplicating the
 * registration inline — Injekt is a process-global singleton, so registering more than once across
 * test classes in the same JVM is harmless (each call simply re-registers the same factory).
 */
internal object TestInjektSupport {
    fun ensureCustomMangaInfoBound() {
        Injekt.importModule(
            object : InjektModule {
                override fun InjektRegistrar.registerInjectables() {
                    addSingletonFactory<GetCustomMangaInfo> {
                        GetCustomMangaInfo(
                            object : CustomMangaRepository {
                                override fun get(mangaId: Long): CustomMangaInfo? = null
                                override fun set(mangaInfo: CustomMangaInfo) {}
                            },
                        )
                    }
                }
            },
        )
    }
}
// KMK <--
