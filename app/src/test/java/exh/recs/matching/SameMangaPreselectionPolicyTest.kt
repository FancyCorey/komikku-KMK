package exh.recs.matching

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga

class SameMangaPreselectionPolicyTest {
    @Test
    fun `all and none modes are explicit`() {
        val origin = manga("The Story")
        val candidate = manga("Different Story")

        assertTrue(SameMangaPreselectionPolicy.shouldSelect(SameMangaPreselectionMode.ALL, origin, candidate))
        assertFalse(SameMangaPreselectionPolicy.shouldSelect(SameMangaPreselectionMode.NONE, origin, candidate))
    }

    @Test
    fun `exact name mode ignores merely similar titles`() {
        val origin = manga("The Story")

        assertTrue(
            SameMangaPreselectionPolicy.shouldSelect(
                SameMangaPreselectionMode.EXACT_NAME,
                origin,
                manga(" the-story "),
            ),
        )
        assertFalse(
            SameMangaPreselectionPolicy.shouldSelect(
                SameMangaPreselectionMode.EXACT_NAME,
                origin,
                manga("The Story: Extra Edition"),
            ),
        )
    }

    @Test
    fun `exact name mode keeps distinct season markers from being default selected`() {
        val origin = manga("Example Season 1")

        assertTrue(
            SameMangaPreselectionPolicy.shouldSelect(
                SameMangaPreselectionMode.EXACT_NAME,
                origin,
                manga("Example Season 1"),
            ),
        )
        assertFalse(
            SameMangaPreselectionPolicy.shouldSelect(
                SameMangaPreselectionMode.EXACT_NAME,
                origin,
                manga("Example Season 2"),
            ),
        )
    }

    private fun manga(title: String): Manga = Manga.create().copy(ogTitle = title)
}
