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
        evaluationModeEnabled: Boolean,
        now: Long = System.currentTimeMillis(),
    ): String {
        val sanitized = SourceEvaluationDiagnosticsPolicy.sanitize(
            evaluationModeEnabled = evaluationModeEnabled,
            currentExtensionName = currentExtension,
            currentSourceName = currentSource,
            lastError = lastError,
            lastProbeMarker = lastProbeMarker,
        )
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
            if (sanitized.currentExtensionLabel != null) appendLine("Extension: ${sanitized.currentExtensionLabel}")
            if (sanitized.currentSourceLabel != null) appendLine("Source: ${sanitized.currentSourceLabel}")
            appendLine()
            appendLine("--- Shizuku ---")
            appendLine("Installed: $shizukuInstalled  BinderAlive: $shizukuBinderAlive  PermGranted: $shizukuPermGranted")
            if (sanitized.errorCategory != null) {
                appendLine()
                appendLine("--- Last Error ---")
                appendLine(sanitized.errorCategory)
            }
            if (sanitized.probeMarker != null) {
                appendLine()
                appendLine("--- Last Probe Marker ---")
                appendLine("Ext: ${sanitized.probeMarker.extensionLabel}")
                appendLine("Source: ${sanitized.probeMarker.sourceLabel ?: "n/a"}")
                appendLine("Phase: ${sanitized.probeMarker.phase}")
                val markerTs = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(sanitized.probeMarker.updatedAt))
                appendLine("UpdatedAt: $markerTs")
            }
        }.trimEnd()
    }
}
// KMK <--
