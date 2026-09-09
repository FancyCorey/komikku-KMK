package exh.recs.matching

import tachiyomi.domain.manga.model.Manga

// KMK --> v0.8.21-fix2: explicit, user-configurable default selection for other versions.
object SameMangaPreselectionPolicy {
    fun shouldSelect(
        mode: SameMangaPreselectionMode,
        origin: Manga,
        candidate: Manga,
    ): Boolean = when (mode) {
        SameMangaPreselectionMode.ALL -> true
        SameMangaPreselectionMode.NONE -> false
        SameMangaPreselectionMode.EXACT_NAME -> {
            val originTitles = SameMangaIdentityNormalizer.normalizeTitles(origin.title, origin.ogTitle, emptyList())
            val candidateTitles = SameMangaIdentityNormalizer.normalizeTitles(candidate.title, candidate.ogTitle, emptyList())
            originTitles.any { originTitle ->
                candidateTitles.any { candidateTitle ->
                    originTitle.markers == candidateTitle.markers &&
                        (
                            originTitle.canonicalBase == candidateTitle.canonicalBase ||
                                originTitle.compatibilityBase == candidateTitle.compatibilityBase
                            )
                }
            }
        }
    }
}
// KMK <--
