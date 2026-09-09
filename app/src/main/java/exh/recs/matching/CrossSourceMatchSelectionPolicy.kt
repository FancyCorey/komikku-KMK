package exh.recs.matching

import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.model.CrossSourceIdentityDecision
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionPolicy

object CrossSourceMatchSelectionPolicy {
    fun automaticSelections(
        candidates: List<Manga>,
        origin: Manga?,
        preselectionMode: SameMangaPreselectionMode,
        decisions: Map<MangaIdentityKey, CrossSourceIdentityDecision?>,
        manuallyDeselected: Set<MangaIdentityKey>,
    ): Set<MangaIdentityKey> {
        if (origin == null) return emptySet()
        return candidates.mapNotNull { candidate ->
            val key = MangaIdentityKey(candidate.source, candidate.url)
            val isOrigin = key.source == origin.source && key.url == origin.url
            val isExplicitlyConfirmed = CrossSourceIdentityDecisionPolicy.isAuthoritativeConfirmation(decisions[key])
            if (
                !isOrigin &&
                key !in manuallyDeselected &&
                (SameMangaPreselectionPolicy.shouldSelect(preselectionMode, origin, candidate) || isExplicitlyConfirmed)
            ) {
                key
            } else {
                null
            }
        }.toSet()
    }

    fun automaticSelections(
        candidates: Set<MangaIdentityKey>,
        decisions: Map<MangaIdentityKey, CrossSourceIdentityDecision?>,
        enabled: Boolean,
        origin: MangaIdentityKey?,
        manuallyDeselected: Set<MangaIdentityKey>,
    ): Set<MangaIdentityKey> {
        if (!enabled) return emptySet()
        return candidates.filterTo(mutableSetOf()) { key ->
            key != origin &&
                key !in manuallyDeselected &&
                CrossSourceIdentityDecisionPolicy.isAuthoritativeConfirmation(decisions[key])
        }
    }
}
