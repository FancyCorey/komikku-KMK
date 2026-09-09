package exh.recs

/**
 * Resolves the aggregate resource envelope for one For You refresh.
 *
 * Recommendation source work is network and metadata heavy. Keep the normal device path at the
 * existing throughput, but reduce concurrent source work on constrained devices so a refresh does
 * not compete with the reader, image decoder, or the rest of the foreground application.
 */
internal object RecommendationEffectiveResourcePolicy {
    const val DEFAULT_SOURCE_CONCURRENCY = 5
    const val LOW_RAM_SOURCE_CONCURRENCY = 2
    const val MAX_SOURCE_CONCURRENCY = DEFAULT_SOURCE_CONCURRENCY
    const val DEFAULT_PREVIEW_CONCURRENCY = 4
    const val LOW_RAM_PREVIEW_CONCURRENCY = 2
    const val MAX_PREVIEW_CONCURRENCY = DEFAULT_PREVIEW_CONCURRENCY
    const val DEFAULT_PREVIEW_ENRICHMENT_CONCURRENCY = 8
    const val LOW_RAM_PREVIEW_ENRICHMENT_CONCURRENCY = 4

    fun sourceConcurrency(isLowRamDevice: Boolean): Int =
        if (isLowRamDevice) LOW_RAM_SOURCE_CONCURRENCY else DEFAULT_SOURCE_CONCURRENCY

    fun previewConcurrency(isLowRamDevice: Boolean): Int =
        if (isLowRamDevice) LOW_RAM_PREVIEW_CONCURRENCY else DEFAULT_PREVIEW_CONCURRENCY

    fun previewEnrichmentConcurrency(isLowRamDevice: Boolean): Int =
        if (isLowRamDevice) LOW_RAM_PREVIEW_ENRICHMENT_CONCURRENCY else DEFAULT_PREVIEW_ENRICHMENT_CONCURRENCY
}
