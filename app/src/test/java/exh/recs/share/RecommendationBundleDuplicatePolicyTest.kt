package exh.recs.share

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK -->

class RecommendationBundleDuplicatePolicyTest {

    private fun item(
        title: String,
        author: String? = null,
        artist: String? = null,
    ) = RecommendationBundleItem(
        title = title,
        url = "/manga/test",
        sourceId = 1L,
        author = author,
        artist = artist,
    )

    // ----- Title-only is never enough -----

    @Test
    fun `title match alone does not flag as duplicate`() {
        val result = RecommendationBundleSourceResolver.isDuplicateByMetadata(
            item("Berserk"),
            localTitle = "Berserk",
            localAuthor = null,
            localArtist = null,
        )
        assertFalse(result)
    }

    @Test
    fun `normalized title match alone does not flag as duplicate`() {
        val result = RecommendationBundleSourceResolver.isDuplicateByMetadata(
            item("Berserk (2016)"),
            localTitle = "Berserk",
            localAuthor = null,
            localArtist = null,
        )
        assertFalse(result)
    }

    // ----- Title + author is sufficient -----

    @Test
    fun `title and author match flags as duplicate`() {
        val result = RecommendationBundleSourceResolver.isDuplicateByMetadata(
            item("Attack on Titan", author = "Hajime Isayama"),
            localTitle = "Attack on Titan",
            localAuthor = "Hajime Isayama",
            localArtist = null,
        )
        assertTrue(result)
    }

    @Test
    fun `title and author match is case insensitive`() {
        val result = RecommendationBundleSourceResolver.isDuplicateByMetadata(
            item("Attack On Titan", author = "HAJIME ISAYAMA"),
            localTitle = "attack on titan",
            localAuthor = "hajime isayama",
            localArtist = null,
        )
        assertTrue(result)
    }

    // ----- Title + artist is sufficient -----

    @Test
    fun `title and artist match flags as duplicate`() {
        val result = RecommendationBundleSourceResolver.isDuplicateByMetadata(
            item("One Piece", artist = "Eiichiro Oda"),
            localTitle = "One Piece",
            localAuthor = null,
            localArtist = "Eiichiro Oda",
        )
        assertTrue(result)
    }

    @Test
    fun `title and artist match is case insensitive`() {
        val result = RecommendationBundleSourceResolver.isDuplicateByMetadata(
            item("One Piece", artist = "EIICHIRO ODA"),
            localTitle = "one piece",
            localAuthor = null,
            localArtist = "eiichiro oda",
        )
        assertTrue(result)
    }

    // ----- Author mismatch prevents duplicate flag -----

    @Test
    fun `matching title but different author does not flag as duplicate`() {
        val result = RecommendationBundleSourceResolver.isDuplicateByMetadata(
            item("My Manga", author = "Author A"),
            localTitle = "My Manga",
            localAuthor = "Author B",
            localArtist = null,
        )
        assertFalse(result)
    }

    // ----- Artist mismatch prevents duplicate flag -----

    @Test
    fun `matching title but different artist does not flag as duplicate`() {
        val result = RecommendationBundleSourceResolver.isDuplicateByMetadata(
            item("My Manga", artist = "Artist A"),
            localTitle = "My Manga",
            localAuthor = null,
            localArtist = "Artist B",
        )
        assertFalse(result)
    }

    // ----- Title with brackets/parens is normalized before comparison -----

    @Test
    fun `normalized titles with suffixes are compared correctly`() {
        val result = RecommendationBundleSourceResolver.isDuplicateByMetadata(
            item("Berserk (2016)", author = "Kentaro Miura"),
            localTitle = "Berserk",
            localAuthor = "Kentaro Miura",
            localArtist = null,
        )
        assertTrue(result)
    }

    // ----- Title mismatch prevents flag even with author match -----

    @Test
    fun `different title with same author does not flag as duplicate`() {
        val result = RecommendationBundleSourceResolver.isDuplicateByMetadata(
            item("Different Manga", author = "Same Author"),
            localTitle = "My Manga",
            localAuthor = "Same Author",
            localArtist = null,
        )
        assertFalse(result)
    }
}

// KMK <--
