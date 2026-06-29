package tachiyomi.domain.taste.model

// KMK -->
/** Single-row record of the extension/source currently being probed by Source Evaluation. */
data class SourceEvaluationProbeMarker(
    val evaluationKey: String?,
    val extensionPkgName: String,
    val signatureHash: String,
    val extensionName: String,
    val sourceId: Long?,
    val sourceName: String?,
    val lang: String?,
    val phase: String,
    val startedAt: Long,
    val updatedAt: Long,
    val batchId: String?,
) {
    /** Extension-level key (no source id) matching SourceEvaluation.extensionKey format. */
    val extensionKey: String get() = "$signatureHash|$extensionPkgName"
}
// KMK <--
