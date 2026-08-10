package exh.recs.loved

// KMK -->
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.model.rememberScreenModel
import eu.kanade.presentation.util.Screen
import tachiyomi.domain.taste.model.MangaRating

/**
 * Compatibility route for Loved Manga (LOVE tier).
 * v0.7.36: delegates to [RatedMangaCollectionContent] — all features are now shared across
 * LOVE/LIKE/DISLIKE via [RatedMangaScreen] and this screen.
 */
class LovedMangaScreen : Screen() {

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { LovedMangaScreenModel() }
        RatedMangaCollectionContent(rating = MangaRating.LOVE, screenModel = screenModel)
    }
}
// KMK <--
