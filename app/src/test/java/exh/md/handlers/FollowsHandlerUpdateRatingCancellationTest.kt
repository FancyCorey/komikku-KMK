package exh.md.handlers

import eu.kanade.tachiyomi.data.database.models.TrackImpl
import exh.md.dto.ResultDto
import exh.md.service.MangaDexAuthService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * KMK security-hardening pass: [FollowsHandler.updateRating] previously wrapped its suspend
 * `service.updateMangaRating`/`deleteMangaRating` calls in `runCatching { ... }.getOrDefault(false)`,
 * which -- because `runCatching` catches `Throwable` -- silently converted a cancelled coroutine into
 * an ordinary "rating update failed" `false` result instead of propagating the cancellation. Fixed to
 * an explicit try/catch that rethrows [CancellationException] and only falls back to `false` on an
 * ordinary [Exception]. These tests drive the real [FollowsHandler] class directly (no Robolectric
 * needed -- it has no Android Context dependency).
 */
class FollowsHandlerUpdateRatingCancellationTest {

    private fun track(score: Double) = TrackImpl().apply {
        tracking_url = "https://mangadex.org/title/abc-123/some-manga"
        this.score = score
    }

    @Test
    fun `a successful rating update returns true`() = runTest {
        val service = mockk<MangaDexAuthService>()
        coEvery { service.updateMangaRating(any(), any()) } returns ResultDto(result = "ok")
        val handler = FollowsHandler(lang = "en", service = service)

        val result = handler.updateRating(track(score = 8.0))

        assertTrue(result)
    }

    @Test
    fun `an ordinary exception from the rating service falls back to false, not a crash`() = runTest {
        val service = mockk<MangaDexAuthService>()
        coEvery { service.updateMangaRating(any(), any()) } throws IllegalStateException("network hiccup")
        val handler = FollowsHandler(lang = "en", service = service)

        val result = handler.updateRating(track(score = 8.0))

        assertFalse(result)
    }

    @Test
    fun `cancellation during the rating update propagates instead of being reported as a failed update`() = runTest {
        val service = mockk<MangaDexAuthService>()
        coEvery { service.updateMangaRating(any(), any()) } throws CancellationException("scope cancelled")
        val handler = FollowsHandler(lang = "en", service = service)

        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking { handler.updateRating(track(score = 8.0)) }
        }
    }

    @Test
    fun `a score of zero routes to delete instead of update, and cancellation there also propagates`() = runTest {
        val service = mockk<MangaDexAuthService>()
        coEvery { service.deleteMangaRating(any()) } throws CancellationException("scope cancelled")
        val handler = FollowsHandler(lang = "en", service = service)

        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking { handler.updateRating(track(score = 0.0)) }
        }
    }
}
