package exh.recs.group

// KMK -->
/**
 * A single weighted tag contribution from the group recommendation seed.
 *
 * [name] is the normalized tag string (lowercase, trimmed).
 * [weight] is in (0.0, 1.0] where 1.0 means every confirmed linked member contributed this tag.
 * [memberCount] is how many confirmed linked members contributed this tag.
 */
data class GroupSeedTag(
    val name: String,
    val weight: Double,
    val memberCount: Int,
)
// KMK <--
