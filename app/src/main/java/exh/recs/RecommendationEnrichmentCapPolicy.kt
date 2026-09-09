package exh.recs

/**
 * Shared contract for the number of candidates enriched with extra source metadata per source.
 *
 * The default and existing upper envelope are preserved while allowing the same slider and exact
 * numeric entry used by the other recommendation budgets.
 */
object RecommendationEnrichmentCapPolicy {

    const val MIN = 1
    const val MAX = 20
    const val DEFAULT = 5

    /** Corrupt or out-of-range persisted values fall back to the stable default. */
    fun resolve(configuredValue: Int): Int = if (configuredValue in MIN..MAX) configuredValue else DEFAULT
}
