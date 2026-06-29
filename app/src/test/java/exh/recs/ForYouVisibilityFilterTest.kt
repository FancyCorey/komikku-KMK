package exh.recs

// KMK -->
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.MangaTaste
import tachiyomi.domain.taste.model.RatedMangaVisibility

class ForYouVisibilityFilterTest {

    private val source = 1L
    private val url = "/manga/test"
    private val key = MangaTasteKey(source, url)
    private val now = System.currentTimeMillis()

    private fun mangaWith(src: Long = source, u: String = url): Manga =
        Manga.create().copy(source = src, url = u)

    private fun tasteWith(rating: MangaRating): Map<MangaTasteKey, MangaTaste> =
        mapOf(
            key to MangaTaste(
                mangaId = 1L,
                source = source,
                url = url,
                title = "Test",
                rating = rating.value,
                createdAt = now,
                updatedAt = now,
            ),
        )

    // HIDE_ALL_RATED: every rated manga is hidden regardless of rating

    @Test
    fun `HIDE_ALL_RATED hides LOVE`() {
        assertTrue(shouldHideForYou(mangaWith(), tasteWith(MangaRating.LOVE), RatedMangaVisibility.HIDE_ALL_RATED))
    }

    @Test
    fun `HIDE_ALL_RATED hides LIKE`() {
        assertTrue(shouldHideForYou(mangaWith(), tasteWith(MangaRating.LIKE), RatedMangaVisibility.HIDE_ALL_RATED))
    }

    @Test
    fun `HIDE_ALL_RATED hides DISLIKE`() {
        assertTrue(shouldHideForYou(mangaWith(), tasteWith(MangaRating.DISLIKE), RatedMangaVisibility.HIDE_ALL_RATED))
    }

    // HIDE_DISLIKED_ONLY: only DISLIKE is hidden; LIKE and LOVE pass through

    @Test
    fun `HIDE_DISLIKED_ONLY hides DISLIKE`() {
        assertTrue(shouldHideForYou(mangaWith(), tasteWith(MangaRating.DISLIKE), RatedMangaVisibility.HIDE_DISLIKED_ONLY))
    }

    @Test
    fun `HIDE_DISLIKED_ONLY allows LIKE`() {
        assertFalse(shouldHideForYou(mangaWith(), tasteWith(MangaRating.LIKE), RatedMangaVisibility.HIDE_DISLIKED_ONLY))
    }

    @Test
    fun `HIDE_DISLIKED_ONLY allows LOVE`() {
        assertFalse(shouldHideForYou(mangaWith(), tasteWith(MangaRating.LOVE), RatedMangaVisibility.HIDE_DISLIKED_ONLY))
    }

    // SHOW_ALL_RATED: nothing is hidden

    @Test
    fun `SHOW_ALL_RATED allows DISLIKE`() {
        assertFalse(shouldHideForYou(mangaWith(), tasteWith(MangaRating.DISLIKE), RatedMangaVisibility.SHOW_ALL_RATED))
    }

    @Test
    fun `SHOW_ALL_RATED allows LOVE`() {
        assertFalse(shouldHideForYou(mangaWith(), tasteWith(MangaRating.LOVE), RatedMangaVisibility.SHOW_ALL_RATED))
    }

    // Unrated manga is never hidden regardless of setting

    @Test
    fun `unrated manga not hidden under HIDE_ALL_RATED`() {
        assertFalse(shouldHideForYou(mangaWith(), emptyMap(), RatedMangaVisibility.HIDE_ALL_RATED))
    }

    @Test
    fun `unrated manga not hidden under HIDE_DISLIKED_ONLY`() {
        assertFalse(shouldHideForYou(mangaWith(), emptyMap(), RatedMangaVisibility.HIDE_DISLIKED_ONLY))
    }

    // Source-url identity: different source or url does not match

    @Test
    fun `different source is not matched`() {
        assertFalse(shouldHideForYou(mangaWith(src = 99L), tasteWith(MangaRating.DISLIKE), RatedMangaVisibility.HIDE_DISLIKED_ONLY))
    }

    @Test
    fun `different url is not matched`() {
        assertFalse(shouldHideForYou(mangaWith(u = "/manga/other"), tasteWith(MangaRating.DISLIKE), RatedMangaVisibility.HIDE_DISLIKED_ONLY))
    }
}
// KMK <--
