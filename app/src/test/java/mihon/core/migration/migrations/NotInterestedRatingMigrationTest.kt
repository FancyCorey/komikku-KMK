package mihon.core.migration.migrations

import eu.kanade.domain.source.service.SourcePreferences
import exh.recs.SeenMangaKey
import exh.recs.SeenRecommendationMangaStore
import exh.taste.StubMangaRepository
import exh.util.FakePreferenceStore
import exh.util.FakeTasteRepository
import kotlinx.coroutines.test.runTest
import mihon.core.migration.MigrationContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.interactor.SetMangaTaste
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.MangaTaste

// KMK v0.8.21-fix2: AUG-02 redesign -->
/**
 * Direct production-boundary tests for [NotInterestedRatingMigration]. Drives the real migration
 * through explicit constructor overrides (not `MigrationContext.get<T>()` / global Injekt) against
 * a [FakePreferenceStore]-backed [SourcePreferences], a minimal manga-lookup fake, and a real
 * [FakeTasteRepository] -- mirroring [RecommendationLanguageInitializationMigrationTest]'s
 * "exercise the real migration, not a mirrored copy of the rule" approach.
 */
class NotInterestedRatingMigrationTest {

    private class FakeGetMangaRepository : StubMangaRepository() {
        val byUrlSource = mutableMapOf<Pair<Long, String>, Manga>()
        override suspend fun getMangaByUrlAndSourceId(url: String, sourceId: Long): Manga? = byUrlSource[sourceId to url]
    }

    private lateinit var preferenceStore: FakePreferenceStore
    private lateinit var sourcePreferences: SourcePreferences
    private lateinit var mangaRepository: FakeGetMangaRepository
    private lateinit var tasteRepository: FakeTasteRepository
    private lateinit var getManga: GetManga
    private lateinit var getMangaTaste: GetMangaTaste
    private lateinit var setMangaTaste: SetMangaTaste
    private lateinit var migration: NotInterestedRatingMigration

    @BeforeEach
    fun setUp() {
        preferenceStore = FakePreferenceStore()
        sourcePreferences = SourcePreferences(preferenceStore)
        mangaRepository = FakeGetMangaRepository()
        tasteRepository = FakeTasteRepository()
        getManga = GetManga(mangaRepository)
        getMangaTaste = GetMangaTaste(tasteRepository)
        setMangaTaste = SetMangaTaste(tasteRepository)
        migration = NotInterestedRatingMigration(sourcePreferences, getManga, getMangaTaste, setMangaTaste)
    }

    private fun seedManga(id: Long, source: Long, url: String, title: String = "Manga $id") {
        mangaRepository.byUrlSource[source to url] = Manga.create().copy(id = id, source = source, url = url, ogTitle = title)
    }

    private fun seedSeenKeys(vararg keys: SeenMangaKey) {
        sourcePreferences.seenRecommendationMangaKeys().set(SeenRecommendationMangaStore.serialize(keys.toSet()))
    }

    private suspend fun runMigration(): Boolean = migration.invoke(MigrationContext(dryrun = false))

    @Test
    fun `a resolvable seen key becomes a real NOT_INTERESTED MangaTaste row`() = runTest {
        seedManga(id = 1L, source = 10L, url = "/m/1")
        seedSeenKeys(SeenMangaKey(10L, "/m/1"))

        assertTrue(runMigration())

        assertEquals(MangaRating.NOT_INTERESTED.value, getMangaTaste.await(10L, "/m/1")?.rating)
    }

    @Test
    fun `the legacy preference is left untouched -- this is an additive backfill, not a cutover`() = runTest {
        seedManga(id = 1L, source = 10L, url = "/m/1")
        seedSeenKeys(SeenMangaKey(10L, "/m/1"))

        runMigration()

        assertTrue(
            SeenMangaKey(10L, "/m/1") in SeenRecommendationMangaStore.parse(sourcePreferences.seenRecommendationMangaKeys().get()),
            "markSeen()/clearSeen() still read this preference directly -- the migration must never clear it",
        )
    }

    @Test
    fun `an existing explicit rating always wins and is never overwritten`() = runTest {
        seedManga(id = 1L, source = 10L, url = "/m/1")
        seedSeenKeys(SeenMangaKey(10L, "/m/1"))
        tasteRepository.upsertMangaTaste(
            MangaTaste(mangaId = 1L, source = 10L, url = "/m/1", title = "Manga 1", rating = MangaRating.LOVE.value, createdAt = 0L, updatedAt = 0L),
        )

        runMigration()

        assertEquals(MangaRating.LOVE.value, getMangaTaste.await(10L, "/m/1")?.rating, "an explicit rating must never be overwritten by the migration")
    }

    @Test
    fun `a seen key whose manga cannot be resolved locally is skipped, not crashed on`() = runTest {
        seedSeenKeys(SeenMangaKey(99L, "/missing"))

        assertFalse(runMigration(), "nothing was actually migrated")
    }

    @Test
    fun `an empty preference is a no-op`() = runTest {
        assertFalse(runMigration())
    }

    @Test
    fun `running the migration twice is idempotent`() = runTest {
        seedManga(id = 1L, source = 10L, url = "/m/1")
        seedSeenKeys(SeenMangaKey(10L, "/m/1"))

        assertTrue(runMigration())
        val afterFirst = getMangaTaste.await(10L, "/m/1")
        assertFalse(runMigration(), "the second run finds an existing rating for every key and migrates nothing new")

        assertEquals(afterFirst, getMangaTaste.await(10L, "/m/1"))
    }

    @Test
    fun `multiple seen keys are each migrated independently`() = runTest {
        seedManga(id = 1L, source = 10L, url = "/m/1")
        seedManga(id = 2L, source = 10L, url = "/m/2")
        seedSeenKeys(SeenMangaKey(10L, "/m/1"), SeenMangaKey(10L, "/m/2"))

        assertTrue(runMigration())

        assertEquals(MangaRating.NOT_INTERESTED.value, getMangaTaste.await(10L, "/m/1")?.rating)
        assertEquals(MangaRating.NOT_INTERESTED.value, getMangaTaste.await(10L, "/m/2")?.rating)
    }

    @Test
    fun `the migration is registered exactly once in the production migration list`() {
        val registered = migrations.filterIsInstance<NotInterestedRatingMigration>()
        assertEquals(1, registered.size)
        assertEquals(92f, registered.single().version, "the recovery must cross the consumed version-91 boundary")
    }
}
// KMK <--
