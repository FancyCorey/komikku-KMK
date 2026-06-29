package exh.recs.loved

// KMK -->
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.MangaTaste

internal fun filterLovedTastesByInstalledSources(
    tastes: List<MangaTaste>,
    installedSourceIds: Set<Long>,
): List<MangaTaste> = tastes.filter {
    it.rating == MangaRating.LOVE.value && it.source in installedSourceIds
}
// KMK <--
