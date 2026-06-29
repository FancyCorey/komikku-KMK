package exh.recs.matching

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// KMK --> v0.7.8
class SameMangaMatchSettingsTest {

    // --- Result cap ---

    @Test
    fun `default cap is 2`() {
        assertEquals(2, SameMangaMatchSettings.DEFAULT_RESULT_CAP)
    }

    @Test
    fun `valid cap 1 is accepted`() {
        assertEquals(1, SameMangaMatchSettings.clampResultCap(1))
    }

    @Test
    fun `valid cap 2 is accepted`() {
        assertEquals(2, SameMangaMatchSettings.clampResultCap(2))
    }

    @Test
    fun `valid cap 5 is accepted`() {
        assertEquals(5, SameMangaMatchSettings.clampResultCap(5))
    }

    @Test
    fun `valid cap 10 is accepted`() {
        assertEquals(10, SameMangaMatchSettings.clampResultCap(10))
    }

    @Test
    fun `invalid cap 0 clamps to default`() {
        assertEquals(2, SameMangaMatchSettings.clampResultCap(0))
    }

    @Test
    fun `invalid cap 3 clamps to default`() {
        assertEquals(2, SameMangaMatchSettings.clampResultCap(3))
    }

    @Test
    fun `invalid cap 100 clamps to default`() {
        assertEquals(2, SameMangaMatchSettings.clampResultCap(100))
    }

    @Test
    fun `invalid cap negative clamps to default`() {
        assertEquals(2, SameMangaMatchSettings.clampResultCap(-1))
    }

    // --- Sample size ---

    @Test
    fun `default sample size is 5`() {
        assertEquals(5, SameMangaMatchSettings.DEFAULT_SAMPLE_SIZE)
    }

    @Test
    fun `valid sample size 2 is accepted`() {
        assertEquals(2, SameMangaMatchSettings.clampSampleSize(2))
    }

    @Test
    fun `valid sample size 5 is accepted`() {
        assertEquals(5, SameMangaMatchSettings.clampSampleSize(5))
    }

    @Test
    fun `valid sample size 10 is accepted`() {
        assertEquals(10, SameMangaMatchSettings.clampSampleSize(10))
    }

    @Test
    fun `invalid sample size 0 clamps to default`() {
        assertEquals(5, SameMangaMatchSettings.clampSampleSize(0))
    }

    @Test
    fun `invalid sample size 3 clamps to default`() {
        assertEquals(5, SameMangaMatchSettings.clampSampleSize(3))
    }

    @Test
    fun `invalid sample size 7 clamps to default`() {
        assertEquals(5, SameMangaMatchSettings.clampSampleSize(7))
    }

    @Test
    fun `invalid sample size negative clamps to default`() {
        assertEquals(5, SameMangaMatchSettings.clampSampleSize(-5))
    }
}
// KMK <--
