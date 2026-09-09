package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BackupMangaTaste
import exh.taste.StubMangaRepository
import exh.util.FakeTasteRepository
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.repository.AlternateSourceBridgeRepository

class TasteRestorerRatedMangaIdentityTest {

    private class RecordingMangaRepository : StubMangaRepository() {
        private val mangas = linkedMapOf<Pair<Long, String>, Manga>()
        var nextId = 1_000L

        override suspend fun getMangaById(id: Long): Manga =
            mangas.values.first { it.id == id }

        override suspend fun getMangaByUrlAndSourceId(url: String, sourceId: Long): Manga? =
            mangas[sourceId to url]

        override suspend fun insertNetworkManga(manga: List<Manga>, updateInfo: Boolean): List<Manga> =
            manga.map { incoming ->
                mangas.getOrPut(incoming.source to incoming.url) {
                    incoming.copy(id = nextId++)
                }
            }

        fun all() = mangas.values.toList()
    }

    private fun restorer(
        tasteRepository: FakeTasteRepository,
        mangaRepository: RecordingMangaRepository,
    ) = TasteRestorer(
        tasteRepository = tasteRepository,
        alternateSourceBridgeRepository = mockk<AlternateSourceBridgeRepository>(relaxed = true),
        mangaRepository = mangaRepository,
        getManga = GetManga(mangaRepository),
        getMangaTaste = GetMangaTaste(tasteRepository),
        getTagTaste = mockk(relaxed = true),
        getTagAliases = mockk(relaxed = true),
        getDisabledSources = mockk(relaxed = true),
        setSourceEnabled = mockk(relaxed = true),
        upsertTagAlias = mockk(relaxed = true),
        getCrossSourceMangaLinks = mockk(relaxed = true),
        upsertCrossSourceMangaLinks = mockk(relaxed = true),
        getCrossSourceGroupPrimary = mockk(relaxed = true),
        getMangaSourceQualitySignals = mockk(relaxed = true),
        upsertMangaSourceQualitySignal = mockk(relaxed = true),
        sourcePreferences = mockk<SourcePreferences>(relaxed = true),
    )

    @Test
    fun `all rating-family members restore when their non-library manga rows are absent`() = runTest {
        val tastes = FakeTasteRepository()
        val mangas = RecordingMangaRepository()
        val backups = MangaRating.entries.mapIndexed { index, rating ->
            BackupMangaTaste(
                mangaId = index.toLong() + 1,
                source = index.toLong() + 10,
                url = "/alternate/$index",
                title = "Alternate $index",
                rating = rating.value,
                updatedAt = 1_000L + index,
            )
        }

        val errors = restorer(tastes, mangas).restoreMangaTastes(backups)

        assertEquals(emptyList<String>(), errors)
        assertEquals(MangaRating.entries.map { it.value }.toSet(), tastes.getAllMangaTastes().map { it.rating }.toSet())
        assertEquals(backups.map { it.source to it.url }.toSet(), mangas.all().map { it.source to it.url }.toSet())
        mangas.all().forEach { manga ->
            assertFalse(manga.favorite)
            assertFalse(manga.initialized)
        }
    }

    @Test
    fun `malformed missing identity is skipped without creating a manga or rating`() = runTest {
        val tastes = FakeTasteRepository()
        val mangas = RecordingMangaRepository()

        val errors = restorer(tastes, mangas).restoreMangaTastes(
            listOf(
                BackupMangaTaste(
                    mangaId = 42,
                    source = 7,
                    url = "",
                    title = "Missing URL",
                    rating = MangaRating.LOVE.value,
                    updatedAt = 1_000,
                ),
            ),
        )

        assertEquals(listOf("Taste rating for source 7: missing or invalid manga identity"), errors)
        assertEquals(emptyList<Manga>(), mangas.all())
        assertEquals(emptyList<Any>(), tastes.getAllMangaTastes())
    }
}
