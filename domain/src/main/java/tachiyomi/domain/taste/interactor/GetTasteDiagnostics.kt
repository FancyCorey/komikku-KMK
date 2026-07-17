package tachiyomi.domain.taste.interactor

import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.taste.model.TasteProfileConfidence
import tachiyomi.domain.taste.repository.TasteRepository

// KMK v0.8.10 -->
data class TasteDiagnosticsResult(
    val summary: TasteDiagnosticsSummary,
    val confidence: TasteProfileConfidence,
)

/**
 * Read-only interactor wiring real repository data into [TasteDiagnosticsAggregator], plus the
 * existing [TasteProfileConfidence] signal (already computed for Source Evaluation's personalized-
 * scoring gate) so the Diagnostics screen can show the same confidence read that already exists
 * elsewhere, instead of a second, possibly-inconsistent confidence calculation.
 */
class GetTasteDiagnostics(
    private val tasteRepository: TasteRepository,
    private val mangaRepository: MangaRepository,
    private val getTasteProfile: GetTasteProfile,
) {
    suspend fun await(): TasteDiagnosticsResult {
        val tastes = tasteRepository.getAllMangaTastes()
        val aliases = tasteRepository.getAllTagAliases()
        val tagPreferences = tasteRepository.getAllTagTastes()

        val ratedManga = tastes.mapNotNull { taste ->
            val genres = runCatching { mangaRepository.getMangaById(taste.mangaId).genre }.getOrNull()
                ?: return@mapNotNull null
            RatedMangaGenres(ratingValue = taste.rating, genres = genres)
        }

        val summary = TasteDiagnosticsAggregator.aggregate(
            ratedManga = ratedManga,
            aliases = aliases,
            tagPreferences = tagPreferences,
        )
        val confidence = TasteProfileConfidence.from(getTasteProfile.await())

        return TasteDiagnosticsResult(summary = summary, confidence = confidence)
    }
}
// KMK <--
