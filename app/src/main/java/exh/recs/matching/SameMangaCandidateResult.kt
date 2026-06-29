package exh.recs.matching

import eu.kanade.tachiyomi.source.CatalogueSource
import tachiyomi.domain.manga.model.Manga

// KMK --> v0.7.8
/** Per-source outcome for a bounded same-manga candidate search. */
sealed interface SameMangaCandidateResult {
    data object Loading : SameMangaCandidateResult
    data class Error(val throwable: Throwable) : SameMangaCandidateResult
    data class Success(val results: List<Manga>) : SameMangaCandidateResult
}

/** Source + result pair returned from [SameMangaCandidateSearcher]. */
data class SameMangaSourceResult(
    val source: CatalogueSource,
    val result: SameMangaCandidateResult,
)
// KMK <--
