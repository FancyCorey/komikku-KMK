package tachiyomi.domain.taste.model

// KMK -->
/** Extension or source suspected of causing a fatal process kill during Source Evaluation. */
data class SourceEvaluationUnsafeSource(
    val unsafeKey: String,
    val evaluationKey: String?,
    val extensionPkgName: String,
    val signatureHash: String,
    val extensionName: String,
    val sourceId: Long?,
    val sourceName: String?,
    val lang: String?,
    val phase: String,
    val reason: String,
    val crashCount: Int,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val lastBatchId: String?,
) {
    val extensionKey: String get() = "$signatureHash|$extensionPkgName"
}

/**
 * Builds stable unsafe-source keys.
 *
 * Source-level key (preferred): signatureHash|pkgName|sourceId
 * Extension-level key (fallback): signatureHash|pkgName
 */
object SourceEvaluationUnsafeKeys {
    fun build(signatureHash: String, pkgName: String, sourceId: Long?): String =
        if (sourceId != null) "$signatureHash|$pkgName|$sourceId" else "$signatureHash|$pkgName"

    fun extensionKey(signatureHash: String, pkgName: String): String = "$signatureHash|$pkgName"
}
// KMK <--
