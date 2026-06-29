package exh.recs.evaluation

import tachiyomi.domain.taste.model.SourceEvaluationProbeMarker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// KMK -->
object SourceEvaluationDiagnosticsBuilder {

    fun build(
        kmkVersion: String,
        installerMode: String,
        batchSize: Int,
        skipAlreadyEvaluated: Boolean,
        includeExplicit: Boolean,
        candidateCount: Int,
        evalResultCount: Int,
        unsafeCount: Int,
        unsafeHiddenCount: Int,
        // KMK --> v0.6.18: package-level load quarantine diagnostics
        blockedPackageCount: Int = 0,
        dcmIsStaticallyBlocked: Boolean = true,
        // KMK <--
        queuePhase: String,
        currentExtension: String?,
        currentSource: String?,
        shizukuInstalled: Boolean,
        shizukuBinderAlive: Boolean,
        shizukuPermGranted: Boolean,
        lastError: String?,
        lastProbeMarker: SourceEvaluationProbeMarker?,
        now: Long = System.currentTimeMillis(),
    ): String {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(now))
        return buildString {
            appendLine("=== Source Evaluation Diagnostics ===")
            appendLine("KMK-Recs: $kmkVersion")
            appendLine("Timestamp: $ts")
            appendLine()
            appendLine("--- Options ---")
            appendLine("Installer: $installerMode  BatchSize: $batchSize")
            appendLine("SkipEvaluated: $skipAlreadyEvaluated  IncludeExplicit: $includeExplicit")
            appendLine()
            appendLine("--- Counts ---")
            appendLine("Candidates: $candidateCount  EvalResults: $evalResultCount")
            appendLine("UnsafeSources: $unsafeCount  UnsafeHidden: $unsafeHiddenCount")
            // KMK --> v0.6.18
            appendLine("BlockedPackages: $blockedPackageCount  DCMStaticBlock: $dcmIsStaticallyBlocked")
            // KMK <--
            appendLine()
            appendLine("--- Queue ---")
            appendLine("Phase: $queuePhase")
            if (currentExtension != null) appendLine("Extension: $currentExtension")
            if (currentSource != null) appendLine("Source: $currentSource")
            appendLine()
            appendLine("--- Shizuku ---")
            appendLine("Installed: $shizukuInstalled  BinderAlive: $shizukuBinderAlive  PermGranted: $shizukuPermGranted")
            if (lastError != null) {
                appendLine()
                appendLine("--- Last Error ---")
                appendLine(lastError)
            }
            if (lastProbeMarker != null) {
                appendLine()
                appendLine("--- Last Probe Marker ---")
                appendLine("Ext: ${lastProbeMarker.extensionName} (${lastProbeMarker.extensionPkgName})")
                appendLine("Source: ${lastProbeMarker.sourceName ?: "n/a"} (id=${lastProbeMarker.sourceId ?: "n/a"})")
                appendLine("Phase: ${lastProbeMarker.phase}")
                val markerTs = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(lastProbeMarker.updatedAt))
                appendLine("UpdatedAt: $markerTs")
            }
        }.trimEnd()
    }
}
// KMK <--
