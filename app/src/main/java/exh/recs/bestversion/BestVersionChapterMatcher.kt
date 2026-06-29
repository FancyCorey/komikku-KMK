package exh.recs.bestversion

import eu.kanade.tachiyomi.source.model.SChapter
import tachiyomi.domain.chapter.model.Chapter

// KMK --> v0.7.8
/**
 * Pure helper that finds the best-matching chapter in a candidate chapter list
 * for a given origin chapter number.
 *
 * Matching priority:
 * 1. Exact chapterNumber match (or very close, within ±0.01).
 * 2. Closest chapterNumber if within ±1.
 * 3. Null (chapter unavailable).
 */
object BestVersionChapterMatcher {

    /**
     * Returns the [SChapter] from [candidates] that best matches [targetChapterNumber],
     * or null if no reasonable match exists.
     */
    fun findMatch(targetChapterNumber: Double, candidates: List<SChapter>): SChapter? {
        if (candidates.isEmpty()) return null
        // Find exact or very close match first
        val exact = candidates.minByOrNull { kotlin.math.abs(it.chapter_number - targetChapterNumber) }
            ?.takeIf { kotlin.math.abs(it.chapter_number - targetChapterNumber) <= 1.0 }
        return exact
    }

    /**
     * Selects the default chapter from [chapters] based on:
     * 1. The last-read/in-progress chapter (highest lastPageRead among non-fully-read chapters).
     * 2. Latest read chapter.
     * 3. Latest downloaded chapter.
     * 4. Latest chapter overall.
     */
    fun selectDefaultChapter(chapters: List<Chapter>): Chapter? {
        if (chapters.isEmpty()) return null

        // Prefer last in-progress (has lastPageRead > 0 and not read)
        val inProgress = chapters
            .filter { !it.read && it.lastPageRead > 0 }
            .maxByOrNull { it.lastPageRead }
        if (inProgress != null) return inProgress

        // Prefer latest read chapter
        val lastRead = chapters.filter { it.read }.maxByOrNull { it.chapterNumber }
        if (lastRead != null) return lastRead

        // Fall back to latest chapter
        return chapters.maxByOrNull { it.chapterNumber }
    }
}
// KMK <--
