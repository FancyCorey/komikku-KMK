package exh.recs

import com.aallam.similarity.NormalizedLevenshtein
import tachiyomi.domain.manga.model.Manga
import java.util.Locale

/**
 * Scores how similar a recommendation candidate is to the source manga.
 *
 * This is distinct from SourceMatchScorer (migration) which asks "is this the same manga?"
 * Here we ask "is this manga worth reading if you liked the source manga?"
 *
 * Weights:
 * - Title similarity: 60% — high because tracker recommendations may share series name fragments.
 * - Tag/genre overlap: 40% — the main signal for "similar feel".
 * - No chapter count or status signals: not meaningful for recommendation ranking.
 *
 * Gracefully handles missing genres: falls back to title-only when either side has no tags.
 */
internal object RecommendationScorer {

    private val levenshtein = NormalizedLevenshtein()

    fun score(source: Manga, candidate: Manga): Double {
        val sourceTags = source.genre.normalizedTags()
        val candidateTags = candidate.genre.normalizedTags()

        val titleScore = titleSimilarity(source.title, candidate.title)

        return if (sourceTags.isEmpty() || candidateTags.isEmpty()) {
            titleScore
        } else {
            val shared = sourceTags intersect candidateTags
            val union = sourceTags union candidateTags
            val tagScore = shared.size.toDouble() / union.size.toDouble()
            (titleScore * 0.6) + (tagScore * 0.4)
        }
    }

    private fun titleSimilarity(a: String, b: String): Double {
        val na = a.normalizedTitle()
        val nb = b.normalizedTitle()
        return when {
            na.isBlank() && nb.isBlank() -> 1.0
            na.isBlank() || nb.isBlank() -> 0.0
            else -> levenshtein.similarity(na, nb)
        }
    }

    private fun List<String>?.normalizedTags(): Set<String> {
        return this?.mapNotNull { tag ->
            tag.lowercase(Locale.ROOT)
                .replace(nonWordRegex, " ")
                .replace(multiSpaceRegex, " ")
                .trim()
                .takeIf { it.isNotBlank() }
        }?.toSet() ?: emptySet()
    }

    private fun String.normalizedTitle(): String {
        return lowercase(Locale.ROOT)
            .replace(bracketsRegex, " ")
            .replace(nonWordRegex, " ")
            .replace(multiSpaceRegex, " ")
            .trim()
    }

    private val bracketsRegex = Regex("""[\[(<{].*?[]\)>}]""")
    private val nonWordRegex = Regex("[^\\p{L}\\p{N}]+")
    private val multiSpaceRegex = Regex(" +")
}
