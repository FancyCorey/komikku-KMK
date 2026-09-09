package exh.recs.matching

import tachiyomi.domain.manga.model.Manga

/**
 * Attaches privacy-bounded identity evidence to one source's in-memory result.
 *
 * The source outcome remains separate, no metadata is fetched, and stable input
 * order is the final tie-breaker.
 */
object SameMangaCandidateEvidenceAttacher {

    /**
     * Live source search can return unrelated rows when a source's search endpoint is broad.
     * Keep those rows out of contextual choosers while preserving the explicit global-search
     * escape hatch for users who need to choose a differently named work manually.
     */
    fun isPlausible(origin: Manga, candidate: Manga): Boolean {
        val assessment = SameMangaIdentityEvidencePolicy.assess(
            origin = origin.toIdentitySnapshot(),
            candidate = candidate.toIdentitySnapshot(),
        )
        return SameMangaIdentityReason.TITLE_CONFLICT !in assessment.reasons &&
            SameMangaIdentityReason.CONTRIBUTOR_CONFLICT !in assessment.reasons &&
            SameMangaIdentityReason.PART_MARKER_CONFLICT !in assessment.reasons
    }

    fun attach(
        origin: Manga,
        candidates: List<Manga>,
        confirmedKeys: Set<MangaIdentityKey> = emptySet(),
    ): SameMangaCandidateResult.Success {
        if (candidates.isEmpty()) return SameMangaCandidateResult.Success(emptyList())

        val originSnapshot = origin.toIdentitySnapshot()
        val assessed = candidates
            .distinctBy { MangaIdentityKey(it.source, it.url) }
            .filter { manga ->
                val key = MangaIdentityKey(manga.source, manga.url)
                key in confirmedKeys || isPlausible(origin, manga)
            }
            .mapIndexed { index, manga ->
                AssessedCandidate(
                    manga = manga,
                    assessment = SameMangaIdentityEvidencePolicy.assess(
                        origin = originSnapshot,
                        candidate = manga.toIdentitySnapshot(),
                    ),
                    originalIndex = index,
                )
            }.sortedWith(candidateComparator)

        val evidence = LinkedHashMap<MangaIdentityKey, SameMangaIdentityAssessment>(assessed.size)
        assessed.forEach { candidate ->
            evidence[MangaIdentityKey(candidate.manga.source, candidate.manga.url)] = candidate.assessment
        }
        return SameMangaCandidateResult.Success(
            results = assessed.map(AssessedCandidate::manga),
            evidence = evidence,
        )
    }

    private fun Manga.toIdentitySnapshot() = SameMangaIdentitySnapshot(
        source = source,
        url = url,
        displayTitle = title,
        originalTitle = ogTitle.ifBlank { title },
        aliases = emptyList(),
        author = author,
        artist = artist,
        status = status.takeIf { it > 0 },
        genres = genre.orEmpty(),
        chapterNumbers = emptyList(),
    )

    private val candidateComparator = compareBy<AssessedCandidate> {
        decisionRank(it.assessment.decision)
    }.thenByDescending {
        positiveEvidenceCount(it.assessment)
    }.thenByDescending {
        it.assessment.titleSimilarity
    }.thenByDescending {
        it.assessment.sharedGenreCount
    }.thenBy {
        it.originalIndex
    }

    private fun decisionRank(decision: SameMangaIdentityDecision): Int = when (decision) {
        SameMangaIdentityDecision.EXACT -> 0
        SameMangaIdentityDecision.LIKELY -> 1
        SameMangaIdentityDecision.UNCERTAIN -> 2
        SameMangaIdentityDecision.CONFLICT -> 3
        SameMangaIdentityDecision.REJECTED -> 4
    }

    private fun positiveEvidenceCount(assessment: SameMangaIdentityAssessment): Int {
        return assessment.reasons.count { it in positiveReasons }
    }

    private val positiveReasons = setOf(
        SameMangaIdentityReason.RECORD_KEY_EQUAL,
        SameMangaIdentityReason.TITLE_EXACT_CANONICAL,
        SameMangaIdentityReason.TITLE_EXACT_COMPATIBILITY,
        SameMangaIdentityReason.TITLE_SIMILAR,
        SameMangaIdentityReason.CONTRIBUTOR_EXACT_AUTHOR,
        SameMangaIdentityReason.CONTRIBUTOR_EXACT_ARTIST,
        SameMangaIdentityReason.PART_MARKER_AGREEMENT,
        SameMangaIdentityReason.STATUS_AGREEMENT,
        SameMangaIdentityReason.GENRE_SUPPORT,
        SameMangaIdentityReason.CHAPTER_EXACT_ALIGNMENT,
        SameMangaIdentityReason.CHAPTER_SHIFTED_ALIGNMENT,
        SameMangaIdentityReason.USER_CONFIRMED,
    )

    private data class AssessedCandidate(
        val manga: Manga,
        val assessment: SameMangaIdentityAssessment,
        val originalIndex: Int,
    )
}
