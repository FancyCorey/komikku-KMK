package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.extension.ExtensionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

// KMK v0.8.10-fix5 -->
/**
 * A single [SourceRuntimeFailureRegistry] entry, enriched with installed-extension identity for
 * diagnostics/UI. Process-lifetime only — never persists raw exception text or anything beyond what
 * [SourceRuntimeFailureRegistry.Entry] already tracks.
 */
data class SourceRuntimeHealthIssue(
    val sourceId: Long,
    val sourceName: String,
    val sourceLang: String,
    val packageName: String?,
    val extensionName: String?,
    val hasUpdate: Boolean,
    val operation: SourceRuntimeOperation,
    val kind: SourceRuntimeFailureKind,
    val firstFailureAt: Long,
    val lastFailureAt: Long,
    val count: Int,
)

/**
 * Maps [SourceRuntimeFailureRegistry] entries to installed-extension information for diagnostics/UI.
 *
 * Deliberately does not put [ExtensionManager] into [SourceRuntimeFailureRegistry] itself, keeping
 * that registry dependency-free — this reporter is the one place that joins the two.
 */
object SourceRuntimeHealthReporter {

    fun issuesFlow(extensionManager: ExtensionManager): Flow<List<SourceRuntimeHealthIssue>> {
        return combine(
            SourceRuntimeFailureRegistry.failures,
            extensionManager.installedExtensionsFlow,
        ) { failures, installedExtensions ->
            failures.map { entry ->
                val extension = installedExtensions.find { extension ->
                    extension.sources.any { it.id == entry.sourceId }
                }
                SourceRuntimeHealthIssue(
                    sourceId = entry.sourceId,
                    sourceName = entry.sourceName,
                    sourceLang = entry.sourceLang,
                    packageName = extension?.pkgName,
                    extensionName = extension?.name,
                    hasUpdate = extension?.hasUpdate ?: false,
                    operation = entry.operation,
                    kind = entry.kind,
                    firstFailureAt = entry.firstFailureAt,
                    lastFailureAt = entry.lastFailureAt,
                    count = entry.count,
                )
            }
        }
    }
}
// KMK <--
