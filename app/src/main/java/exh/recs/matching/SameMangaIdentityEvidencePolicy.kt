package exh.recs.matching

// KMK --> A11.1
import com.aallam.similarity.NormalizedLevenshtein
import kotlin.math.abs

object SameMangaIdentityEvidencePolicy {

    val CURRENT_VERSION = IdentityDecisionVersion(1)

    private const val OBSERVATIONAL_SIMILARITY_FLOOR = 0.80
    private const val CHAPTER_EPSILON = 0.000_001
    private const val MAX_CHAPTER_SAMPLE = 64

    private val similarity = NormalizedLevenshtein()

    fun assess(
        origin: SameMangaIdentitySnapshot,
        candidate: SameMangaIdentitySnapshot,
    ): SameMangaIdentityAssessment {
        val reasons = linkedSetOf<SameMangaIdentityReason>()
        val sameRecord = origin.source == candidate.source &&
            origin.url.isNotBlank() &&
            origin.url == candidate.url
        if (sameRecord) reasons += SameMangaIdentityReason.RECORD_KEY_EQUAL

        val originTitles = SameMangaIdentityNormalizer.normalizeTitles(
            origin.displayTitle,
            origin.originalTitle,
            origin.aliases,
        )
        val candidateTitles = SameMangaIdentityNormalizer.normalizeTitles(
            candidate.displayTitle,
            candidate.originalTitle,
            candidate.aliases,
        )
        val titleEvidence = compareTitles(originTitles, candidateTitles)
        reasons += titleEvidence.reason

        val contributorEvidence = compareContributors(origin, candidate)
        reasons += contributorEvidence.reasons

        val markerEvidence = compareMarkers(originTitles, candidateTitles)
        reasons += markerEvidence.reason

        val originStatus = origin.status?.takeIf { it > 0 }
        val candidateStatus = candidate.status?.takeIf { it > 0 }
        reasons += if (originStatus != null && originStatus == candidateStatus) {
            SameMangaIdentityReason.STATUS_AGREEMENT
        } else {
            SameMangaIdentityReason.STATUS_UNKNOWN_OR_DIFFERENT
        }

        val sharedGenreCount = origin.genres.mapNotNull(SameMangaIdentityNormalizer::normalizeGenre).toSet()
            .intersect(candidate.genres.mapNotNull(SameMangaIdentityNormalizer::normalizeGenre).toSet())
            .size
        if (sharedGenreCount > 0) reasons += SameMangaIdentityReason.GENRE_SUPPORT

        val chapterEvidence = compareChapters(origin.chapterNumbers, candidate.chapterNumbers)
        reasons += chapterEvidence.reason

        when (candidate.userDecision) {
            SameMangaUserDecision.CONFIRMED -> reasons += SameMangaIdentityReason.USER_CONFIRMED
            SameMangaUserDecision.REJECTED -> reasons += SameMangaIdentityReason.USER_REJECTED
            SameMangaUserDecision.NONE -> Unit
        }

        val decision = when {
            candidate.userDecision == SameMangaUserDecision.REJECTED -> SameMangaIdentityDecision.REJECTED
            sameRecord || candidate.userDecision == SameMangaUserDecision.CONFIRMED -> SameMangaIdentityDecision.EXACT
            contributorEvidence.conflict || markerEvidence.conflict -> SameMangaIdentityDecision.CONFLICT
            titleEvidence.canonicalExact && contributorEvidence.sameRoleMatch -> SameMangaIdentityDecision.LIKELY
            else -> SameMangaIdentityDecision.UNCERTAIN
        }

        return SameMangaIdentityAssessment(
            version = CURRENT_VERSION,
            decision = decision,
            reasons = reasons.sortedBy(SameMangaIdentityReason::ordinal),
            titleSimilarity = titleEvidence.similarity.coerceIn(0.0, 1.0),
            sharedGenreCount = sharedGenreCount,
            chapterOffset = chapterEvidence.offset,
        )
    }

    private fun compareTitles(
        origin: List<SameMangaIdentityNormalizer.NormalizedTitle>,
        candidate: List<SameMangaIdentityNormalizer.NormalizedTitle>,
    ): TitleEvidence {
        if (origin.isEmpty() || candidate.isEmpty()) {
            return TitleEvidence(SameMangaIdentityReason.TITLE_MISSING, 0.0)
        }
        val originCanonical = origin.map { it.canonicalBase }.filter(String::isNotBlank).toSet()
        val candidateCanonical = candidate.map { it.canonicalBase }.filter(String::isNotBlank).toSet()
        val canonicalExact = originCanonical.intersect(candidateCanonical).isNotEmpty()

        val originCompatibility = origin.map { it.compatibilityBase }.filter(String::isNotBlank).toSet()
        val candidateCompatibility = candidate.map { it.compatibilityBase }.filter(String::isNotBlank).toSet()
        val compatibilityExact = originCompatibility.intersect(candidateCompatibility).isNotEmpty()

        val maximumSimilarity = originCanonical.maxOfOrNull { left ->
            candidateCanonical.maxOfOrNull { right -> similarity.similarity(left, right) } ?: 0.0
        } ?: 0.0

        val reason = when {
            canonicalExact -> SameMangaIdentityReason.TITLE_EXACT_CANONICAL
            compatibilityExact -> SameMangaIdentityReason.TITLE_EXACT_COMPATIBILITY
            maximumSimilarity >= OBSERVATIONAL_SIMILARITY_FLOOR -> SameMangaIdentityReason.TITLE_SIMILAR
            else -> SameMangaIdentityReason.TITLE_CONFLICT
        }
        return TitleEvidence(reason, maximumSimilarity, canonicalExact)
    }

    private fun compareContributors(
        origin: SameMangaIdentitySnapshot,
        candidate: SameMangaIdentitySnapshot,
    ): ContributorEvidence {
        val originAuthor = SameMangaIdentityNormalizer.normalizeContributor(origin.author)
        val originArtist = SameMangaIdentityNormalizer.normalizeContributor(origin.artist)
        val candidateAuthor = SameMangaIdentityNormalizer.normalizeContributor(candidate.author)
        val candidateArtist = SameMangaIdentityNormalizer.normalizeContributor(candidate.artist)
        val authorMatch = originAuthor != null && originAuthor == candidateAuthor
        val artistMatch = originArtist != null && originArtist == candidateArtist
        val originContributors = setOfNotNull(originAuthor, originArtist)
        val candidateContributors = setOfNotNull(candidateAuthor, candidateArtist)
        val conflict = originContributors.isNotEmpty() &&
            candidateContributors.isNotEmpty() &&
            originContributors.intersect(candidateContributors).isEmpty()

        val reasons = buildSet {
            if (authorMatch) add(SameMangaIdentityReason.CONTRIBUTOR_EXACT_AUTHOR)
            if (artistMatch) add(SameMangaIdentityReason.CONTRIBUTOR_EXACT_ARTIST)
            when {
                conflict -> add(SameMangaIdentityReason.CONTRIBUTOR_CONFLICT)
                !authorMatch && !artistMatch -> add(SameMangaIdentityReason.CONTRIBUTOR_MISSING)
            }
        }
        return ContributorEvidence(reasons, authorMatch || artistMatch, conflict)
    }

    private fun compareMarkers(
        origin: List<SameMangaIdentityNormalizer.NormalizedTitle>,
        candidate: List<SameMangaIdentityNormalizer.NormalizedTitle>,
    ): MarkerEvidence {
        val originMarkers = origin.flatMap { it.markers }.toSet()
        val candidateMarkers = candidate.flatMap { it.markers }.toSet()
        if (originMarkers.isEmpty() || candidateMarkers.isEmpty()) {
            return MarkerEvidence(SameMangaIdentityReason.PART_MARKER_MISSING, false)
        }
        val commonKinds = originMarkers.map { it.kind }.intersect(candidateMarkers.map { it.kind }.toSet())
        if (commonKinds.isEmpty()) {
            return MarkerEvidence(SameMangaIdentityReason.PART_MARKER_MISSING, false)
        }
        val conflict = commonKinds.any { kind ->
            originMarkers.filter { it.kind == kind }.map { it.ordinal }.toSet()
                .intersect(candidateMarkers.filter { it.kind == kind }.map { it.ordinal }.toSet())
                .isEmpty()
        }
        return if (conflict) {
            MarkerEvidence(SameMangaIdentityReason.PART_MARKER_CONFLICT, true)
        } else {
            MarkerEvidence(SameMangaIdentityReason.PART_MARKER_AGREEMENT, false)
        }
    }

    private fun compareChapters(origin: List<Double>, candidate: List<Double>): ChapterEvidence {
        val left = normalizeChapters(origin)
        val right = normalizeChapters(candidate)
        if (left.isEmpty() || right.isEmpty()) {
            return ChapterEvidence(SameMangaIdentityReason.CHAPTER_MISSING, null)
        }
        if (left == right) {
            return ChapterEvidence(SameMangaIdentityReason.CHAPTER_EXACT_ALIGNMENT, 0.0)
        }
        if (left.size == right.size && left.size >= 2) {
            val offsets = left.zip(right) { originNumber, candidateNumber -> candidateNumber - originNumber }
            val first = offsets.first()
            if (abs(first) > CHAPTER_EPSILON && offsets.all { abs(it - first) <= CHAPTER_EPSILON }) {
                return ChapterEvidence(SameMangaIdentityReason.CHAPTER_SHIFTED_ALIGNMENT, first)
            }
        }
        return ChapterEvidence(SameMangaIdentityReason.CHAPTER_CONFLICT, null)
    }

    private fun normalizeChapters(values: List<Double>): List<Double> {
        return values.asSequence()
            .filter { it.isFinite() && it >= 0.0 }
            .distinct()
            .sorted()
            .toList()
            .takeLast(MAX_CHAPTER_SAMPLE)
    }

    private data class TitleEvidence(
        val reason: SameMangaIdentityReason,
        val similarity: Double,
        val canonicalExact: Boolean = false,
    )

    private data class ContributorEvidence(
        val reasons: Set<SameMangaIdentityReason>,
        val sameRoleMatch: Boolean,
        val conflict: Boolean,
    )

    private data class MarkerEvidence(
        val reason: SameMangaIdentityReason,
        val conflict: Boolean,
    )

    private data class ChapterEvidence(
        val reason: SameMangaIdentityReason,
        val offset: Double?,
    )
}
// KMK <--
