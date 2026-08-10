package mihon.feature.migration.list.search

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga

class SourceMatchScorerTest {

    @Test
    fun `tag overlap can outrank a source with only more chapters`() {
        val current = manga(
            title = "I Am Not a Villain",
            tags = listOf("Action", "Villainess", "Fantasy", "Comedy"),
        )
        val matchingTags = manga(
            title = "I Am Not a Villain",
            tags = listOf("Action", "Villainess", "Fantasy", "Comedy"),
        )
        val moreChaptersWrongTags = manga(
            title = "I Am Not a Villain",
            tags = listOf("Drama", "Historical"),
        )

        val matchingScore = SourceMatchScorer.score(
            current = current,
            candidate = matchingTags,
            currentChapterCount = 70,
            currentLatestChapter = 70.0,
            candidateChapterCount = 68,
            candidateLatestChapter = 68.0,
        )
        val chapterOnlyScore = SourceMatchScorer.score(
            current = current,
            candidate = moreChaptersWrongTags,
            currentChapterCount = 70,
            currentLatestChapter = 70.0,
            candidateChapterCount = 90,
            candidateLatestChapter = 90.0,
        )

        assertTrue(matchingScore.total > chapterOnlyScore.total)
        assertTrue(matchingScore.sharedTags.contains("villainess"))
    }

    @Test
    fun `better chapter coverage wins when title and tags are similar`() {
        val current = manga(
            title = "I Am Not a Villain",
            tags = listOf("Action", "Fantasy"),
        )
        val lowerCoverage = manga(
            title = "I Am Not a Villain",
            tags = listOf("Action", "Fantasy"),
        )
        val higherCoverage = manga(
            title = "I Am Not a Villain",
            tags = listOf("Action", "Fantasy"),
        )

        val lowerScore = SourceMatchScorer.score(
            current = current,
            candidate = lowerCoverage,
            currentChapterCount = 100,
            currentLatestChapter = 100.0,
            candidateChapterCount = 80,
            candidateLatestChapter = 80.0,
        )
        val higherScore = SourceMatchScorer.score(
            current = current,
            candidate = higherCoverage,
            currentChapterCount = 100,
            currentLatestChapter = 100.0,
            candidateChapterCount = 110,
            candidateLatestChapter = 110.0,
        )

        assertTrue(higherScore.total > lowerScore.total)
    }

    private fun manga(title: String, tags: List<String>): Manga {
        return Manga.create().copy(
            ogTitle = title,
            ogGenre = tags,
        )
    }
}
