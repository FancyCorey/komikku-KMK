package exh.recs

import tachiyomi.domain.taste.model.normalizeTag

// KMK -->
/**
 * Applies a temporary For You focus to candidates that have already passed visibility and taste
 * scoring. This policy deliberately does not own candidate generation, hard exclusions, durable
 * taste, persistence, or source/network behavior.
 *
 * KMK v0.8.21-fix5: R4/AUG-14 completion -- direct product correction (2026-08-25). The prior
 * "rerank, never filter" implementation (every candidate preserved, just reordered) was rejected
 * by the product owner as untruthful: a user selecting a focus expects candidates that do not
 * match to actually disappear from the section, not merely sink to the bottom while still being
 * shown. This is now a real filter with an include/exclude criteria model:
 *  - [FocusCriteria.include]: a candidate must satisfy these under [FocusCriteria.matchAll]
 *    (match every included group) or, when false, Match Any (match at least one).
 *  - [FocusCriteria.exclude]: a candidate matching ANY excluded group is dropped, independent of
 *    the include Match All/Any setting.
 * [apply] returns both the filtered set and the original full set (see [Outcome.Filtered]) so the
 * presentation layer can offer a transient, non-mutating "Show Broader Results" view without
 * re-deriving candidates or touching this policy's own criteria/state.
 */
internal object RecommendationFocusPolicy {

    /**
     * An include/exclude focus selection. Both sets are pre-canonicalization inputs (raw user
     * selections); [apply] normalizes them internally via the shared [normalizeTag] + alias map,
     * exactly like candidate genres are normalized, so matching stays case/punctuation-insensitive
     * and alias-aware on both sides.
     *
     * [matchAll] governs only [include]: true (the default) requires every included group to
     * match; false requires at least one. [exclude] always behaves as "match none of these",
     * regardless of [matchAll].
     */
    data class FocusCriteria(
        val include: Set<String> = emptySet(),
        val exclude: Set<String> = emptySet(),
        val matchAll: Boolean = true,
    ) {
        val isActive: Boolean get() = include.isNotEmpty() || exclude.isNotEmpty()

        companion object {
            val EMPTY = FocusCriteria()
        }
    }

    /** A single filtered candidate, paired with which selected include groups it matched (possibly none). */
    data class FilteredCandidate(
        val recommendation: PersonalRecommendation,
        val matchedGroups: Set<String>,
    )

    sealed interface Outcome {
        data object Cleared : Outcome

        /**
         * [matched]: candidates that satisfy the full include/exclude/Match-All-or-Any contract,
         * in original relative order -- this is what every section shows by default once a focus
         * is active. [all]: every input candidate, in original relative order, untouched -- this
         * is exactly what "Show Broader Results" reveals, and is never itself filtered or
         * reordered by this policy.
         */
        data class Filtered(
            val matched: List<FilteredCandidate>,
            val all: List<PersonalRecommendation>,
        ) : Outcome
    }

    /**
     * Filters [candidates] against [criteria]. An inactive criteria (no include, no exclude) is a
     * cleared focus (pass-through). Otherwise every candidate is evaluated against the real
     * contract and non-matching candidates are genuinely absent from [Outcome.Filtered.matched].
     */
    fun apply(
        candidates: List<PersonalRecommendation>,
        criteria: FocusCriteria,
        aliasMap: Map<String, String>,
    ): Outcome {
        val include = criteria.include
            .map { canonicalGroup(it, aliasMap) }
            .filter { it.isNotBlank() }
            .toSet()
        val exclude = criteria.exclude
            .map { canonicalGroup(it, aliasMap) }
            .filter { it.isNotBlank() }
            .toSet()
        if (include.isEmpty() && exclude.isEmpty()) return Outcome.Cleared

        val matched = mutableListOf<FilteredCandidate>()
        for (recommendation in candidates) {
            val candidateGroups = recommendation.manga.genre.orEmpty()
                .asSequence()
                .map { canonicalFocusGroup(it, aliasMap) }
                .filter { it.isNotBlank() }
                .toSet()

            val excluded = exclude.isNotEmpty() && candidateGroups.any { it in exclude }
            if (excluded) continue

            val includeMatches = candidateGroups intersect include
            val passesInclude = when {
                include.isEmpty() -> true
                criteria.matchAll -> includeMatches.size == include.size
                else -> includeMatches.isNotEmpty()
            }
            if (!passesInclude) continue

            matched += FilteredCandidate(recommendation, includeMatches)
        }

        return Outcome.Filtered(matched = matched, all = candidates)
    }

    private fun canonicalGroup(raw: String, aliasMap: Map<String, String>): String {
        return canonicalFocusGroup(raw, aliasMap)
    }
}

/** Resolves a raw source/user label through the same normalized alias contract used for matching. */
internal fun canonicalFocusGroup(raw: String, aliasMap: Map<String, String>): String {
    val normalized = raw.normalizeTag()
    // Resolve at most one user-provided hop, then the built-in metadata aliases. User aliases
    // remain authoritative, while built-in aliases make equivalent format/demographic labels one
    // selectable group instead of leaking duplicates into the picker.
    val userCanonical = aliasMap[normalized]?.normalizeTag() ?: normalized
    return (aliasMap[userCanonical] ?: userCanonical).normalizeTag()
}

/**
 * Title-Case display label for a canonicalized (lowercase, space-separated) focus group key, e.g.
 * "slice of life" -> "Slice Of Life". Matching underneath always stays on the canonical/normalized
 * form via [normalizeTag] -- this is presentation-only.
 */
internal fun String.toFocusDisplayLabel(): String =
    split(" ")
        .filter { it.isNotEmpty() }
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }

/**
 * Narrows the focus catalog without changing selection state. A query may match the displayed
 * canonical group or any normalized alias already used by the focus policy.
 */
internal fun filterFocusGroups(
    groups: List<String>,
    query: String,
    aliasMap: Map<String, String> = emptyMap(),
): List<String> {
    val normalizedQuery = query.normalizeTag()
    if (normalizedQuery.isBlank()) return groups

    // Build the reverse index once for this query. The focus dialog can contain a large union of
    // source-filter groups, and scanning every alias for every group on each keystroke blocks the
    // Compose main thread on real profiles.
    val aliasesByCanonical = aliasMap.entries
        .asSequence()
        .mapNotNull { (alias, canonical) ->
            val normalizedAlias = alias.normalizeTag()
            val normalizedCanonical = canonical.normalizeTag()
            normalizedAlias.takeIf { it.isNotBlank() }?.let { it to normalizedCanonical }
        }
        .groupBy({ it.second }, { it.first })

    return groups.filter { group ->
        val normalizedGroup = group.normalizeTag()
        normalizedGroup.contains(normalizedQuery) ||
            aliasesByCanonical[normalizedGroup].orEmpty().any { alias -> alias.contains(normalizedQuery) }
    }
}

// KMK independent_codex_recheck_2026-08-26 -->
/**
 * Builds the alias map [RecommendationFocusPolicy.apply] needs (normalized alias -> canonical
 * group key) by merging two existing, already-shared owners -- never a second UI-only alias
 * system:
 * 1. [userAliasMap]: the user's own persisted tag-alias table, exactly [GetTagAliases
 *    .awaitAliasMap]'s own normalizedAlias -> groupKey shape (the same call
 *    [BrowsePersonalRecommendationsScreenModel.refresh] already makes for candidate-search alias
 *    expansion) -- not limited to whatever happens to be in the currently-loaded result set.
 * 2. [exh.recs.sources.GenreFilterMapper.BUILT_IN_SYNONYMS]: the small universal synonym list
 *    already used for source-filter genre matching (canonical -> variant labels), inverted here
 *    into the same normalized-alias -> canonical shape.
 *
 * User aliases always win a key conflict -- they are what the user explicitly curated; built-in
 * synonyms only fill gaps the user table doesn't cover.
 */
internal fun buildFocusAliasMap(userAliasMap: Map<String, String>): Map<String, String> {
    val merged = mutableMapOf<String, String>()
    KNOWN_FOCUS_ALIASES.forEach { (canonical, aliases) ->
        val canonicalKey = canonical.normalizeTag()
        aliases.forEach { alias ->
            val key = alias.normalizeTag()
            if (key.isNotBlank() && key != canonicalKey) merged.putIfAbsent(key, canonicalKey)
        }
    }
    exh.recs.sources.GenreFilterMapper.BUILT_IN_SYNONYMS.forEach { (canonical, variants) ->
        val canonicalKey = canonical.normalizeTag()
        variants.forEach { variant ->
            val key = variant.normalizeTag()
            if (key.isNotBlank()) merged.putIfAbsent(key, canonicalKey)
        }
    }
    userAliasMap.forEach { (normalizedAlias, groupKey) ->
        val alias = normalizedAlias.normalizeTag()
        val canonical = groupKey.normalizeTag()
        if (alias.isNotBlank() && canonical.isNotBlank()) merged[alias] = canonical
    }
    return merged
}

/**
 * The universe of focus criteria the dialog should offer, independent of what happens to be
 * loaded in the current For You result set (the confirmed gap this closes -- see
 * [RecommendationFocusPolicy]'s own doc). Union of three stable, non-load-dependent sources:
 * every canonical group the user's persisted tag-alias table knows about ([groupToAliases]'s keys,
 * from [GetTagAliases.awaitGroupToAliasesMap]), every built-in synonym group, and (KMK C3,
 * HR-2026-08-26-FOCUS-CRITERION-CATALOG-COMPLETENESS/E4.3) [sourceFilterGroups] -- genre-like
 * labels eligible sources themselves report supporting via their own [eu.kanade.tachiyomi.source
 * .model.FilterList] (see [exh.recs.sources.SourceGenreCatalogCache]), so the picker is a real
 * source-filter-style catalog and not merely alias/synonym-derived.
 */
internal fun buildFocusKnownGroups(
    groupToAliases: Map<String, List<String>>,
    sourceFilterGroups: Set<String> = emptySet(),
    aliasMap: Map<String, String> = emptyMap(),
): Set<String> =
    (groupToAliases.keys + exh.recs.sources.GenreFilterMapper.BUILT_IN_SYNONYMS.keys + sourceFilterGroups)
        .asSequence()
        .map { canonicalFocusGroup(it, aliasMap) }
        .filter { it.isNotBlank() }
        .toSet()
// KMK <--

// KMK --> EC-04 2026-09-01: typed focus dimensions.
/**
 * The focus dimensions classifiable today without a new [tachiyomi.domain.manga.model.Manga]
 * field or per-candidate network I/O: [GENRE] (the default for any canonical group not recognized
 * as another dimension), [DEMOGRAPHIC], [FORMAT], and [PRESENTATION_ACCESS]. These are small,
 * curated, universally-recognized manga metadata vocabularies. [DEMOGRAPHIC] is a small, curated,
 * universally-recognized manga demographic
 * vocabulary -- Shounen/Shoujo/Seinen/Josei/Kids -- classified against the exact same canonicalized
 * label already flowing through [RecommendationFocusPolicy], [exh.recs.sources.SourceGenreFilterExtractor],
 * and the user's alias table. No new source capability query, extraction pass, or matching logic is
 * required: this only classifies labels the existing pipeline already surfaces.
 *
 * Year and Chapter-count are deliberately NOT modeled here. [tachiyomi.domain.manga.model.Manga]
 * has no publication-year or chapter-count field; deriving either would require either unreliable
 * text-scraping of descriptions or new per-candidate chapter-list network I/O whose cost (requests,
 * latency, low-resource impact) is unmeasured -- exactly the kind of unmeasured default the
 * discovery-effort-policy finding already warns against. That remains a separate, explicitly
 * deferred requirement pending an architecture/product decision, not an oversight of this slice.
 */
enum class FocusDimension {
    FORMAT,
    DEMOGRAPHIC,
    PRESENTATION_ACCESS,
    GENRE,
}

/** Curated, canonicalized ([normalizeTag]'d) universal manga demographic vocabulary. */
internal val KNOWN_DEMOGRAPHIC_FOCUS_GROUPS: Set<String> = setOf(
    "shounen",
    "shonen",
    "shoujo",
    "shojo",
    "seinen",
    "josei",
    "kids",
    "children",
)
    .map { it.normalizeTag() }
    .toSet()

/** Publication/reading format labels are filterable metadata, not story genres. */
internal val KNOWN_FORMAT_FOCUS_GROUPS: Set<String> = setOf(
    "manga",
    "manhwa",
    "manhua",
    "webtoon",
    "webcomic",
    "long strip",
    "longstrip",
).map { it.normalizeTag() }.toSet()

/** Presentation/access labels are filterable metadata, not story genres. */
internal val KNOWN_PRESENTATION_ACCESS_FOCUS_GROUPS: Set<String> = setOf(
    "full color",
    "full colour",
    "color",
    "colour",
    "free",
).map { it.normalizeTag() }.toSet()

/** Built-in metadata aliases used only to canonicalize the focus catalog and its matcher. */
internal val KNOWN_FOCUS_ALIASES: Map<String, Set<String>> = mapOf(
    "shounen" to setOf("shonen"),
    "shoujo" to setOf("shojo"),
    "kids" to setOf("children"),
    "webtoon" to setOf("webcomic"),
    "long strip" to setOf("longstrip"),
    "full color" to setOf("full colour", "color", "colour"),
).mapKeys { it.key.normalizeTag() }

/**
 * Classifies an already-canonicalized ([normalizeTag]'d) focus group key. Capability-aware by
 * construction: a source/candidate/alias table that never contributes a demographic-like label
 * simply never has one classify as [FocusDimension.DEMOGRAPHIC], so a UI grouping by this
 * function naturally omits an empty Demographic section rather than disclosing a dimension the
 * current data can't actually support.
 */
fun classifyFocusDimension(canonicalGroup: String): FocusDimension =
    canonicalGroup.normalizeTag().let { normalized ->
        when {
            normalized in KNOWN_FORMAT_FOCUS_GROUPS -> FocusDimension.FORMAT
            normalized in KNOWN_DEMOGRAPHIC_FOCUS_GROUPS -> FocusDimension.DEMOGRAPHIC
            normalized in KNOWN_PRESENTATION_ACCESS_FOCUS_GROUPS -> FocusDimension.PRESENTATION_ACCESS
            else -> FocusDimension.GENRE
        }
    }
// KMK <--
