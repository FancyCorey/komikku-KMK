package exh.recs.bestversion

import eu.kanade.tachiyomi.source.model.SChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.service.ChapterRecognition

// KMK --> v0.7.8
// KMK R2-AUG-05-ACTUAL-BEST-VERSION-FAILURE-PATH: findMatch used to return a plain SChapter? and
// silently gave up (Unavailable) whenever the closest candidate chapter was more than +/-1.0 away --
// even when the candidate source had plenty of readable chapters, just renumbered/restarted (e.g.
// origin at ch.500, candidate source only goes 1-50). That was a false "unavailable", not intentional
// strictness. findMatch now returns a typed [ChapterMatchResult] so a caller can never present a
// distant fallback chapter as if it were the same chapter as the origin: [ChapterMatchResult.Exact]
// only for a real (+/-0.01) match, [ChapterMatchResult.Nearest] for the disclosed, non-exact fallback
// (which now applies at ANY distance, as long as the candidate source has at least one readable
// chapter), and null only when the candidate list itself has no readable chapters at all.
/**
 * Pure helper that finds the best-matching chapter in a candidate chapter list
 * for a given origin chapter number.
 *
 * Matching priority:
 * 1. Exact chapterNumber match (or very close, within +/-0.01) -> [ChapterMatchResult.Exact].
 * 2. Otherwise, the closest readable chapter regardless of distance -> [ChapterMatchResult.Nearest],
 *    which callers must disclose as non-exact (never presented as equivalent to the origin chapter).
 * 3. Null only when [candidates] has no chapter with a finite chapter number at all (genuinely
 *    unavailable -- there is nothing to fall back to).
 */
object BestVersionChapterMatcher {

    private const val EXACT_MATCH_TOLERANCE = 0.01

    /**
     * Disclosure-safe outcome of [findMatch]. [chapter] is always the SChapter to use; [Nearest]
     * additionally carries both chapter numbers so the UI can render e.g. "Origin: Ch. 145 ->
     * Nearest available: Ch. 12" instead of silently treating them as the same chapter.
     */
    sealed interface ChapterMatchResult {
        val chapter: SChapter

        data class Exact(override val chapter: SChapter) : ChapterMatchResult

        data class Nearest(
            override val chapter: SChapter,
            val originChapterNumber: Double,
            val candidateChapterNumber: Double,
        ) : ChapterMatchResult
    }

    /**
     * Returns a [ChapterMatchResult] describing the best match for [targetChapterNumber] within
     * [candidates], or null if [candidates] has no chapter with a finite chapter number.
     */
    fun findMatch(
        targetChapterNumber: Double,
        candidates: List<SChapter>,
        mangaTitle: String = "",
    ): ChapterMatchResult? {
        if (!targetChapterNumber.isFinite()) return null
        val finiteCandidates = resolveCandidates(candidates, mangaTitle)
        if (finiteCandidates.isEmpty()) return null

        // Prefer an exact or very close chapter even when a malformed source list happens to put
        // a different nearby number first. Invalid numeric metadata never becomes an available
        // chapter by accident.
        val distance = { chapter: ResolvedChapter ->
            kotlin.math.abs(chapter.number - targetChapterNumber)
        }
        val comparator =
            compareBy<ResolvedChapter> { distance(it) }.thenBy { it.number }.thenBy { it.chapter.url }
        val exact = finiteCandidates
            .filter { distance(it) <= EXACT_MATCH_TOLERANCE }
            .minWithOrNull(comparator)
        if (exact != null) return ChapterMatchResult.Exact(exact.chapter)

        // KMK R2-AUG-05: no cap here anymore -- a candidate source that genuinely has readable
        // chapters must never be reported as Unavailable just because none of them are numerically
        // close to the origin's chapter number. The fallback is always disclosed as Nearest, never
        // silently substituted as if it were the same chapter.
        val nearest = finiteCandidates.minWithOrNull(comparator) ?: return null
        return ChapterMatchResult.Nearest(
            chapter = nearest.chapter,
            originChapterNumber = targetChapterNumber,
            candidateChapterNumber = nearest.number,
        )
    }

    /**
     * Selects the latest usable candidate after applying the same title-aware normalization as
     * [findMatch]. This is used when the origin has no numeric target, so the raw source sentinel
     * must not win over a number embedded in a chapter label.
     */
    fun selectLatestCandidate(
        candidates: List<SChapter>,
        mangaTitle: String = "",
    ): SChapter? = resolveCandidates(candidates, mangaTitle)
        .maxWithOrNull(compareBy<ResolvedChapter> { it.number }.thenBy { it.chapter.url })
        ?.chapter

    /** Applies the same title-aware chapter-label normalization to a manually selected chapter. */
    fun normalizeChapter(chapter: SChapter, mangaTitle: String = ""): SChapter {
        val rawNumber = chapter.chapter_number.toDouble()
        val resolvedNumber = if (mangaTitle.isBlank()) {
            rawNumber.takeIf { it.isFinite() }
        } else {
            ChapterRecognition.parseChapterNumber(
                mangaTitle,
                chapter.name,
                rawNumber.takeIf { it.isFinite() },
            )
        } ?: return chapter
        if (resolvedNumber == rawNumber) return chapter
        return SChapter.create().also {
            it.copyFrom(chapter)
            it.chapter_number = resolvedNumber.toFloat()
        }
    }

    private fun resolveCandidates(
        candidates: List<SChapter>,
        mangaTitle: String,
    ): List<ResolvedChapter> {
        // Fresh source responses do not always run through SyncChaptersWithSource, so a source can
        // leave chapter_number at -1 even when the label contains a usable number (for example,
        // "Ch.012"). Resolve those labels through the same shared parser used by synchronization.
        return candidates.mapNotNull { chapter ->
            val normalizedChapter = normalizeChapter(chapter, mangaTitle)
            val resolvedNumber = normalizedChapter.chapter_number.toDouble()
            resolvedNumber.takeIf { it.isFinite() }?.let { number ->
                ResolvedChapter(normalizedChapter, number)
            }
        }
    }

    private data class ResolvedChapter(
        val chapter: SChapter,
        val number: Double,
    )

    /**
     * Selects the default chapter from [chapters] based on:
     * 1. The last-read/in-progress chapter (highest lastPageRead among non-fully-read chapters).
     * 2. Latest read chapter.
     * 3. Latest downloaded chapter.
     * 4. Latest chapter overall.
     */
    fun selectDefaultChapter(chapters: List<Chapter>): Chapter? {
        if (chapters.isEmpty()) return null

        // An unrecognized number (-1 is the normal sentinel) is useful only when a source has no
        // numbered chapters at all. Once a valid numbered chapter exists, it must win every default
        // selection tier; otherwise the comparison route can disclose the sentinel as "Ch. -1".
        val selectableChapters = chapters.filter { it.isRecognizedNumber }
            .ifEmpty { chapters }

        // Prefer last in-progress (has lastPageRead > 0 and not read)
        val inProgress = selectableChapters
            .filter { !it.read && it.lastPageRead > 0 }
            .maxByOrNull { it.lastPageRead }
        if (inProgress != null) return inProgress

        // Prefer latest read chapter
        val lastRead = selectableChapters.filter { it.read }.maxByOrNull { it.chapterNumber }
        if (lastRead != null) return lastRead

        // Fall back to latest chapter
        return selectableChapters.maxByOrNull { it.chapterNumber }
    }
}
// KMK <--
