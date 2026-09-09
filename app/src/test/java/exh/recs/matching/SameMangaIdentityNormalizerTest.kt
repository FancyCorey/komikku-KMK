package exh.recs.matching

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SameMangaIdentityNormalizerTest {

    @Test
    fun `canonical equivalents produce the same canonical title`() {
        val composed = normalize("Caf\u00e9")
        val decomposed = normalize("Cafe\u0301")

        assertEquals(composed.single().canonicalBase, decomposed.single().canonicalBase)
    }

    @Test
    fun `compatibility form is supporting evidence rather than canonical equality`() {
        val fullWidth = normalize("\uff21\uff22\uff23").single()
        val ascii = normalize("ABC").single()

        assertNotEquals(fullWidth.canonicalBase, ascii.canonicalBase)
        assertEquals(fullWidth.compatibilityBase, ascii.compatibilityBase)
    }

    @Test
    fun `Japanese Korean and Chinese letters are preserved`() {
        assertEquals("\u9032\u6483\u306e\u5de8\u4eba", normalize("\u9032\u6483\u306e\u5de8\u4eba").single().canonicalBase)
        assertEquals("\ub098 \ud63c\uc790\ub9cc \ub808\ubca8\uc5c5", normalize("\ub098 \ud63c\uc790\ub9cc \ub808\ubca8\uc5c5").single().canonicalBase)
        assertEquals("\u4e00\u4eba\u4e4b\u4e0b", normalize("\u4e00\u4eba\u4e4b\u4e0b").single().canonicalBase)
    }

    @Test
    fun `mixed scripts are preserved rather than transliterated`() {
        val normalized = normalize("Boku no Hero \u30dc\u30af\u30ce\u30d2\u30fc\u30ed\u30fc").single().canonicalBase

        assertTrue(normalized.contains("boku no hero"))
        assertTrue(normalized.contains("\u30dc\u30af\u30ce\u30d2\u30fc\u30ed\u30fc"))
    }

    @Test
    fun `punctuation collapses without deleting letters or numbers`() {
        assertEquals("jojo s bizarre adventure 2", normalize("  JoJo's---Bizarre: Adventure 2 ").single().canonicalBase)
    }

    @Test
    fun `display original and aliases are deduplicated`() {
        val titles = SameMangaIdentityNormalizer.normalizeTitles("Title", "TITLE", listOf("Title", "\u5225\u540d"))

        assertEquals(2, titles.size)
    }

    @Test
    fun `season marker is extracted before base comparison`() {
        val normalized = normalize("Example (Season 2)").single()

        assertEquals("example", normalized.canonicalBase)
        assertTrue(
            SameMangaIdentityNormalizer.PartMarker(
                SameMangaIdentityNormalizer.PartMarkerKind.SEASON,
                2,
            ) in normalized.markers,
        )
    }

    @Test
    fun `roman and decimal part ordinals normalize equally`() {
        val roman = normalize("Example Part II").single()
        val decimal = normalize("Example Part 2").single()

        assertEquals(roman.markers, decimal.markers)
        assertEquals(roman.canonicalBase, decimal.canonicalBase)
    }

    @Test
    fun `ordinary bracket text is retained as title evidence`() {
        val normalized = normalize("Example [Official]").single()

        assertEquals("example official", normalized.canonicalBase)
        assertTrue(normalized.markers.isEmpty())
    }

    @Test
    fun `blank variants do not produce evidence`() {
        assertTrue(SameMangaIdentityNormalizer.normalizeTitles(" ", "", emptyList()).isEmpty())
    }

    @Test
    fun `contributors preserve international letters`() {
        val value = SameMangaIdentityNormalizer.normalizeContributor("  \u8aed\u5c71 \u5275  ")

        assertEquals("\u8aed\u5c71 \u5275", value)
        assertFalse(value.isNullOrBlank())
    }

    private fun normalize(value: String) = SameMangaIdentityNormalizer.normalizeTitles(value, value, emptyList())
}
