package exh.recs.group

// KMK -->
/**
 * Pure data class representing a seed for group-seeded recommendations.
 *
 * Built from a rated manga entry plus its confirmed cross-source link group members.
 * Used to boost TasteProfile tag weights for the seed manga's genres and to exclude
 * seed members from the result list (they are already known/rated).
 *
 * v0.7.35: first implementation — no DB caching for seed results.
 * v0.7.38: added [seedTags] for weighted multi-member tag contributions;
 *          added [metadataMemberCount] and [enrichedMemberCount] for transparency.
 */
data class GroupRecommendationSeed(
    val primaryTitle: String,
    val titles: List<String>,
    /** Flat tag list for query building — top tags by weight, already normalized. */
    val tags: List<String>,
    /** Weighted tag list built from all confirmed linked members. Empty if seed has no metadata. */
    val seedTags: List<GroupSeedTag>,
    val sourceIds: Set<Long>,
    val memberKeys: Set<Pair<Long, String>>,
    val groupId: String?,
    /** How many group members had usable genre metadata (local or enriched). */
    val metadataMemberCount: Int = 0,
    /** How many group members were detail-enriched via source API call in this build. */
    val enrichedMemberCount: Int = 0,
)
// KMK <--
