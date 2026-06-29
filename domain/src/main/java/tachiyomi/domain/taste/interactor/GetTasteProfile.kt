package tachiyomi.domain.taste.interactor

import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.TagPreference
import tachiyomi.domain.taste.model.TasteProfile
import tachiyomi.domain.taste.model.normalizeTag
import tachiyomi.domain.taste.repository.TasteRepository

// KMK -->
class GetTasteProfile(
    private val tasteRepository: TasteRepository,
    private val mangaRepository: MangaRepository,
) {
    /**
     * Builds a [TasteProfile] from:
     * - all manga taste ratings + their stored genres;
     * - all explicit tag preferences;
     * - all tag aliases (for group-key resolution).
     *
     * Learned tag weights are capped to [-10, 10] per group key to prevent
     * a single large-genre manga from dominating the profile forever.
     */
    suspend fun await(): TasteProfile {
        val tastes = tasteRepository.getAllMangaTastes()
        val tagTastes = tasteRepository.getAllTagTastes()
        val aliases = tasteRepository.getAllTagAliases()

        // normalizedAlias -> groupKey fast lookup
        val aliasToGroup: Map<String, String> = aliases.associate { it.normalizedAlias to it.groupKey }

        fun String.toGroupKey(): String {
            val normalized = normalizeTag()
            return aliasToGroup[normalized] ?: normalized
        }

        // Accumulate learned weights from manga ratings
        val rawWeights = mutableMapOf<String, Double>()
        val sourcePositive = mutableMapOf<Long, Int>()
        val sourceNegative = mutableMapOf<Long, Int>()

        for (taste in tastes) {
            val weight = when (taste.rating) {
                MangaRating.LOVE.value -> 2.0
                MangaRating.LIKE.value -> 1.0
                MangaRating.DISLIKE.value -> -2.0
                else -> continue
            }

            if (weight > 0) {
                sourcePositive[taste.source] = (sourcePositive[taste.source] ?: 0) + 1
            } else {
                sourceNegative[taste.source] = (sourceNegative[taste.source] ?: 0) + 1
            }

            // Fetch manga genres; skip if manga row was purged
            val genres = runCatching {
                mangaRepository.getMangaById(taste.mangaId).genre
            }.getOrNull() ?: continue

            for (genre in genres) {
                val groupKey = genre.toGroupKey()
                rawWeights[groupKey] = (rawWeights[groupKey] ?: 0.0) + weight
            }
        }

        val learnedTagWeights = rawWeights.mapValues { (_, v) -> v.coerceIn(-10.0, 10.0) }

        // Explicit tag preferences (from tag_taste table)
        val explicitTagPreferences: Map<String, Int> = tagTastes.associate { tt ->
            tt.normalizedTag.toGroupKey() to tt.preference
        }

        // Source affinity: weak signal (+0.1 per net positive rating)
        val allSourceIds = (sourcePositive.keys + sourceNegative.keys)
        val sourceAffinity: Map<Long, Double> = allSourceIds.associateWith { sid ->
            ((sourcePositive[sid] ?: 0) - (sourceNegative[sid] ?: 0)).toDouble() * 0.1
        }

        // Blocked groups: explicit tag preference == BLOCK (-2)
        val blockedGroups: Set<String> = tagTastes
            .filter { it.preference == TagPreference.BLOCK.value }
            .map { it.normalizedTag.toGroupKey() }
            .toSet()

        return TasteProfile(
            learnedTagWeights = learnedTagWeights,
            explicitTagPreferences = explicitTagPreferences,
            sourceAffinity = sourceAffinity,
            blockedGroups = blockedGroups,
        )
    }
}
// KMK <--
