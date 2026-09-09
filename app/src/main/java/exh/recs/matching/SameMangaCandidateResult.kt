package exh.recs.matching

import eu.kanade.tachiyomi.source.Source
import tachiyomi.domain.manga.model.Manga

// KMK --> v0.7.8
/** Per-source outcome for a bounded same-manga candidate search. */
sealed interface SameMangaCandidateResult {
    data object Loading : SameMangaCandidateResult
    data class Error(val throwable: Throwable) : SameMangaCandidateResult
    data class Success(
        val results: List<Manga>,
        val evidence: Map<MangaIdentityKey, SameMangaIdentityAssessment> = emptyMap(),
    ) : SameMangaCandidateResult
}

/** Source + result pair returned from [SameMangaCandidateSearcher]. */
data class SameMangaSourceResult(
    val source: Source,
    val result: SameMangaCandidateResult,
)
// KMK <--
