package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.domain.source.service.SourcePreferences
import exh.util.FakeTasteRepository
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.interactor.GetCrossSourceGroupPrimary
import tachiyomi.domain.taste.interactor.GetCrossSourceMangaLinks
import tachiyomi.domain.taste.interactor.GetDisabledRecommendationSources
import tachiyomi.domain.taste.interactor.GetMangaSourceQualitySignals
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.interactor.GetTagAliases
import tachiyomi.domain.taste.interactor.GetTagTaste
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.MangaTaste
import tachiyomi.domain.taste.repository.AlternateSourceBridgeRepository
import tachiyomi.domain.taste.repository.MangaSourceQualitySignalRepository

/**
 * R1 correction: [TasteBackupCreator.backupSeenMangaKeys] used to read the raw legacy
 * `seenRecommendationMangaKeys` preference directly. It now derives `BackupSeenMangaKey` entries
 * from [MangaTaste] rows rated [MangaRating.NOT_INTERESTED] -- the sole current rating-family
 * authority -- so a fresh backup can never diverge from or resurrect stale legacy preference
 * bytes, while still emitting the legacy-shaped field for older-version downgrade-restore
 * compatibility. Exercises the real production [TasteBackupCreator] and real [GetMangaTaste]
 * interactor against a fake repository at the boundary (the same [FakeTasteRepository] pattern
 * already established for Undo/Journal tests), not a copied expression.
 */
class TasteBackupCreatorSeenMangaKeysTest {

    private fun taste(source: Long, url: String, rating: Int, mangaId: Long = source * 100) = MangaTaste(
        mangaId = mangaId,
        source = source,
        url = url,
        title = "Manga $mangaId",
        rating = rating,
        createdAt = 1000L,
        updatedAt = 1000L,
    )

    private fun creator(repository: FakeTasteRepository): TasteBackupCreator {
        val getMangaTaste = GetMangaTaste(repository)
        return TasteBackupCreator(
            tasteRepository = repository,
            alternateSourceBridgeRepository = mockk<AlternateSourceBridgeRepository>(),
            getMangaTaste = getMangaTaste,
            getTagTaste = GetTagTaste(repository),
            getTagAliases = GetTagAliases(repository),
            getDisabledSources = GetDisabledRecommendationSources(repository),
            getCrossSourceMangaLinks = GetCrossSourceMangaLinks(repository),
            getCrossSourceGroupPrimary = GetCrossSourceGroupPrimary(repository),
            getMangaSourceQualitySignals = GetMangaSourceQualitySignals(mockk<MangaSourceQualitySignalRepository>()),
            sourcePreferences = mockk<SourcePreferences>(),
        )
    }

    @Test
    fun `backup entries are derived from Not Interested MangaTaste rows, not the legacy preference`() = runTest {
        val repository = FakeTasteRepository()
        repository.upsertMangaTaste(taste(source = 1L, url = "/manga/a", rating = MangaRating.NOT_INTERESTED.value))
        repository.upsertMangaTaste(taste(source = 2L, url = "/manga/b", rating = MangaRating.LOVE.value))
        repository.upsertMangaTaste(taste(source = 3L, url = "/manga/c", rating = MangaRating.NOT_INTERESTED.value))

        val entries = creator(repository).backupSeenMangaKeys()

        assertEquals(2, entries.size)
        assertTrue(entries.any { it.key == "1|/manga/a" })
        assertTrue(entries.any { it.key == "3|/manga/c" })
    }

    @Test
    fun `no Not Interested taste rows produce an empty backup list`() = runTest {
        val repository = FakeTasteRepository()
        repository.upsertMangaTaste(taste(source = 1L, url = "/manga/a", rating = MangaRating.LIKE.value))

        val entries = creator(repository).backupSeenMangaKeys()

        assertTrue(entries.isEmpty())
    }

    @Test
    fun `a manga re-rated away from Not Interested is not exported as seen`() = runTest {
        val repository = FakeTasteRepository()
        val mangaId = 42L
        repository.upsertMangaTaste(taste(source = 1L, url = "/manga/a", rating = MangaRating.NOT_INTERESTED.value, mangaId = mangaId))
        // Re-rating overwrites the same row (one manga_taste row per manga_id) -- proves the
        // backup reflects CURRENT MangaTaste state, not a stale legacy key that a live write path
        // might otherwise have left behind under the old two-store design.
        repository.upsertMangaTaste(taste(source = 1L, url = "/manga/a", rating = MangaRating.LOVE.value, mangaId = mangaId))

        val entries = creator(repository).backupSeenMangaKeys()

        assertTrue(entries.isEmpty())
    }
}
