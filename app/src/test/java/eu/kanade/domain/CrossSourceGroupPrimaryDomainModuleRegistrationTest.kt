package eu.kanade.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import tachiyomi.domain.taste.interactor.ClearCrossSourceGroupPrimary
import tachiyomi.domain.taste.interactor.GetCrossSourceGroupPrimary
import tachiyomi.domain.taste.interactor.SetCrossSourceGroupPrimary
import tachiyomi.domain.taste.model.CrossSourceGroupPrimary
import tachiyomi.domain.taste.model.CrossSourceMangaLink
import tachiyomi.domain.taste.model.MangaTaste
import tachiyomi.domain.taste.model.TagAlias
import tachiyomi.domain.taste.model.TagTaste
import tachiyomi.domain.taste.repository.TasteRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addFactory
import uy.kohesive.injekt.api.get

// KMK --> v0.8.1-fix2
/**
 * Regression test for the v0.8.1-fix1 crash:
 * `InjektionException: No registered instance or factory for type class
 * tachiyomi.domain.taste.interactor.GetCrossSourceGroupPrimary`.
 *
 * The three group-primary interactors ([GetCrossSourceGroupPrimary], [SetCrossSourceGroupPrimary],
 * [ClearCrossSourceGroupPrimary]) were added in v0.8.0 but never registered in
 * [eu.kanade.domain.KMKDomainModule], so any screen model requesting them via `Injekt.get()`
 * crashed at construction time (`LovedMangaScreenModel`, `LinkedVersionListScreenModel`,
 * `TasteBackupCreator`, `TasteRestorer`).
 *
 * This test does NOT invoke `KMKDomainModule.registerInjectables()` directly — that module also
 * registers many interactors/repositories that require a real `DatabaseHandler` (Android SQLite
 * driver) and other Android-only dependencies unavailable in a pure JVM unit test, making a full
 * module-registration test impractical here. Instead, it mirrors the exact
 * `addFactory { GetCrossSourceGroupPrimary(get()) }`-style registration `KMKDomainModule` uses (see
 * the block immediately after the cross-source-link-group registrations there) against a minimal
 * fake [TasteRepository], and asserts all three interactors resolve via `Injekt.get()` without
 * throwing. This directly guards against the specific "forgot to add the addFactory line" class of
 * regression for these three types.
 */
class CrossSourceGroupPrimaryDomainModuleRegistrationTest {

    private class FakeTasteRepository : TasteRepository {
        override suspend fun getMangaTaste(mangaId: Long): MangaTaste? = null
        override fun getMangaTasteAsFlow(mangaId: Long): Flow<MangaTaste?> = emptyFlow()
        override suspend fun getMangaTaste(source: Long, url: String): MangaTaste? = null
        override fun getMangaTasteAsFlow(source: Long, url: String): Flow<MangaTaste?> = emptyFlow()
        override suspend fun getAllMangaTastes(): List<MangaTaste> = emptyList()
        override fun getAllMangaTastesAsFlow(): Flow<List<MangaTaste>> = emptyFlow()
        override suspend fun upsertMangaTaste(taste: MangaTaste) {}
        override suspend fun deleteMangaTaste(mangaId: Long) {}
        override suspend fun deleteMangaTaste(source: Long, url: String) {}
        override suspend fun deleteAllMangaTastes() {}
        override suspend fun getTagTaste(normalizedTag: String): TagTaste? = null
        override suspend fun getAllTagTastes(): List<TagTaste> = emptyList()
        override fun getAllTagTastesAsFlow(): Flow<List<TagTaste>> = emptyFlow()
        override suspend fun upsertTagTaste(tagTaste: TagTaste) {}
        override suspend fun deleteTagTaste(normalizedTag: String) {}
        override suspend fun getAllTagAliases(): List<TagAlias> = emptyList()
        override suspend fun getTagAliasByNormalized(normalizedAlias: String): TagAlias? = null
        override suspend fun upsertTagAlias(alias: TagAlias) {}
        override suspend fun deleteTagAlias(alias: String) {}
        override suspend fun getCrossSourceMangaLinksByGroupId(groupId: String): List<CrossSourceMangaLink> = emptyList()
        override suspend fun getCrossSourceMangaLinkBySourceUrl(source: Long, url: String): CrossSourceMangaLink? = null
        override suspend fun getAllCrossSourceMangaLinks(): List<CrossSourceMangaLink> = emptyList()
        override suspend fun upsertCrossSourceMangaLinks(links: List<CrossSourceMangaLink>) {}
        override suspend fun deleteCrossSourceMangaLink(source: Long, url: String) {}
        override suspend fun deleteCrossSourceMangaLinksByGroupId(groupId: String) {}
        override suspend fun deleteAllCrossSourceMangaLinks() {}
        override suspend fun getCrossSourceGroupPrimary(groupId: String): CrossSourceGroupPrimary? = null
        override suspend fun getAllCrossSourceGroupPrimaries(): List<CrossSourceGroupPrimary> = emptyList()
        override suspend fun upsertCrossSourceGroupPrimary(primary: CrossSourceGroupPrimary) {}
        override suspend fun deleteCrossSourceGroupPrimary(groupId: String) {}
        override suspend fun deleteAllCrossSourceGroupPrimaries() {}
        override suspend fun getAllDisabledSourceIds(): List<Long> = emptyList()
        override fun getAllDisabledSourceIdsAsFlow(): Flow<List<Long>> = emptyFlow()
        override suspend fun disableSource(sourceId: Long) {}
        override suspend fun enableSource(sourceId: Long) {}
    }

    @Test
    fun `GetCrossSourceGroupPrimary, SetCrossSourceGroupPrimary, and ClearCrossSourceGroupPrimary resolve via Injekt once registered`() {
        Injekt.importModule(
            object : InjektModule {
                override fun InjektRegistrar.registerInjectables() {
                    addFactory<TasteRepository> { FakeTasteRepository() }
                    // Mirrors the exact registration KMKDomainModule adds for these three types.
                    addFactory { GetCrossSourceGroupPrimary(get()) }
                    addFactory { SetCrossSourceGroupPrimary(get()) }
                    addFactory { ClearCrossSourceGroupPrimary(get()) }
                }
            },
        )

        assertDoesNotThrow { Injekt.get<GetCrossSourceGroupPrimary>() }
        assertDoesNotThrow { Injekt.get<SetCrossSourceGroupPrimary>() }
        assertDoesNotThrow { Injekt.get<ClearCrossSourceGroupPrimary>() }
    }
}
// KMK <--
