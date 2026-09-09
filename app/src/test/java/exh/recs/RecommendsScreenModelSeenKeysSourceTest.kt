package exh.recs

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * R1 correction: [RecommendsScreenModel]'s group-recommendation `seenKeys` (Not Interested
 * exclusion) used to be read from the legacy `seenRecommendationMangaKeys` preference. It is now
 * derived from `MangaTaste` rows rated `NOT_INTERESTED` -- the exact pattern
 * [BrowsePersonalRecommendationsScreenModel] already uses for the main For You pipeline (see
 * that file's own `allTastes.filter { it.rating == NOT_INTERESTED.value }` derivation).
 *
 * Source-text guard, not a full-load behavioral test: this screen model's group-seeded load path
 * requires a `RecommendsScreen.Args.CrossSourceGroupSeed`, `GroupRecommendationSeedBuilder`, and
 * the full `RecommendationPagingSource`/source-runtime graph to reach the private load function
 * that computes `seenKeys` -- a harness disproportionate to a mechanical, already-proven-pattern
 * refactor. This guard, combined with the clean compile and the identical live pattern's own
 * coverage in [BrowsePersonalRecommendationsScreenModel], is the disclosed verification for this
 * file, consistent with this codebase's established source-guard convention for cases where full
 * instantiation is impractical.
 */
class RecommendsScreenModelSeenKeysSourceTest {

    private fun source(): String = File("src/main/java/exh/recs/RecommendsScreenModel.kt").also {
        assertTrue(it.isFile, "expected source file at ${it.path}")
    }.readText()

    @Test
    fun `group recommendation seenKeys is derived from MangaTaste, not the legacy preference`() {
        val text = source()
        assertTrue(
            text.contains("MangaRating.NOT_INTERESTED.value") &&
                text.contains(".filter { it.rating == tachiyomi.domain.taste.model.MangaRating.NOT_INTERESTED.value }"),
            "seenKeys must be filtered from MangaTaste rows rated NOT_INTERESTED",
        )
        assertTrue(
            text.contains("val allTastes: List<MangaTaste> ="),
            "seenKeys and tasteByKey must share one getMangaTaste.awaitAll() call, not two",
        )
    }

    @Test
    fun `the legacy seen-manga preference is never read live in this file`() {
        val text = source()
        assertFalse(
            text.contains("sourcePreferences.seenRecommendationMangaKeys()"),
            "no live read of the legacy preference should remain -- MangaTaste is the sole rating-family authority",
        )
    }
}
