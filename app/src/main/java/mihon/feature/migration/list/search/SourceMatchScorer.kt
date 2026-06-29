package mihon.feature.migration.list.search

import com.aallam.similarity.NormalizedLevenshtein
import tachiyomi.domain.manga.model.Manga
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

object SourceMatchScorer {

    private val similarity = NormalizedLevenshtein()

    fun score(
        current: Manga,
        candidate: Manga,
        currentChapterCount: Int,
        currentLatestChapter: Double?,
        candidateChapterCount: Int,
        candidateLatestChapter: Double?,
    ): SourceMatchScore {
        val currentTitle = current.title.normalizedTitle()
        val candidateTitle = candidate.title.normalizedTitle()
        val titleSimilarity = when {
            currentTitle.isBlank() && candidateTitle.isBlank() -> 1.0
            currentTitle.isBlank() || candidateTitle.isBlank() -> 0.0
            else -> similarity.similarity(currentTitle, candidateTitle)
        }
        val tagScore = tagScore(current.genre.orEmpty(), candidate.genre.orEmpty())
        val statusScore = if (current.status == candidate.status) 1.0 else 0.0
        val chapterCoverageScore = chapterCoverageScore(
            currentChapterCount = currentChapterCount,
            currentLatestChapter = currentLatestChapter,
            candidateChapterCount = candidateChapterCount,
            candidateLatestChapter = candidateLatestChapter,
        )

        val total = (titleSimilarity * 45.0) +
            (tagScore.ratio * 25.0) +
            (chapterCoverageScore * 25.0) +
            (statusScore * 5.0)

        return SourceMatchScore(
            total = total,
            titleSimilarity = titleSimilarity,
            sharedTags = tagScore.sharedTags,
            candidateOnlyTags = tagScore.candidateOnlyTags,
            chapterCoverage = chapterCoverageScore,
            statusMatches = statusScore == 1.0,
        )
    }

    private fun tagScore(currentTags: List<String>, candidateTags: List<String>): TagScore {
        val current = currentTags.normalizedTags()
        val candidate = candidateTags.normalizedTags()
        if (current.isEmpty() || candidate.isEmpty()) {
            return TagScore(ratio = 0.0, sharedTags = emptySet(), candidateOnlyTags = candidate)
        }

        val shared = current intersect candidate
        val union = current union candidate
        return TagScore(
            ratio = shared.size.toDouble() / union.size.toDouble(),
            sharedTags = shared,
            candidateOnlyTags = candidate - current,
        )
    }

    private fun chapterCoverageScore(
        currentChapterCount: Int,
        currentLatestChapter: Double?,
        candidateChapterCount: Int,
        candidateLatestChapter: Double?,
    ): Double {
        val countScore = if (currentChapterCount <= 0) {
            if (candidateChapterCount > 0) 1.0 else 0.0
        } else {
            min(candidateChapterCount.toDouble() / currentChapterCount.toDouble(), 1.25) / 1.25
        }

        val latestScore = when {
            currentLatestChapter == null && candidateLatestChapter == null -> countScore
            currentLatestChapter == null -> if (candidateLatestChapter != null) 1.0 else 0.0
            candidateLatestChapter == null -> 0.0
            candidateLatestChapter >= currentLatestChapter -> 1.0
            else -> (1.0 - (abs(currentLatestChapter - candidateLatestChapter) / currentLatestChapter))
                .coerceIn(0.0, 1.0)
        }

        return ((countScore * 0.4) + (latestScore * 0.6)).coerceIn(0.0, 1.0)
    }

    private fun List<String>.normalizedTags(): Set<String> {
        return mapNotNull { tag ->
            tag.lowercase(Locale.ROOT)
                .replace(nonTagCharacterRegex, " ")
                .replace(consecutiveSpacesRegex, " ")
                .trim()
                .takeIf { it.isNotBlank() }
        }.toSet()
    }

    private fun String.normalizedTitle(): String {
        return lowercase(Locale.ROOT)
            .replace(textInBracketsRegex, " ")
            .replace(nonTitleCharacterRegex, " ")
            .replace(consecutiveSpacesRegex, " ")
            .trim()
    }

    private val textInBracketsRegex = Regex("""[\[(<{].*?[]\)>}]""")
    private val nonTitleCharacterRegex = Regex("[^\\p{L}\\p{N}]+")
    private val nonTagCharacterRegex = Regex("[^\\p{L}\\p{N}]+")
    private val consecutiveSpacesRegex = Regex(" +")
}

data class SourceMatchScore(
    val total: Double,
    val titleSimilarity: Double,
    val sharedTags: Set<String>,
    val candidateOnlyTags: Set<String>,
    val chapterCoverage: Double,
    val statusMatches: Boolean,
)

private data class TagScore(
    val ratio: Double,
    val sharedTags: Set<String>,
    val candidateOnlyTags: Set<String>,
)
