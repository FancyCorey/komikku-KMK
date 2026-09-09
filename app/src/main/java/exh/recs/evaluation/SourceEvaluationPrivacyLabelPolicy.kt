package exh.recs.evaluation

import exh.util.EvaluationModeFormatter
import tachiyomi.domain.taste.model.SourceEvaluationUnsafeSource
import tachiyomi.domain.taste.model.UnsafeExtensionPackage

/** Centralizes Evaluation Mode labels for Source Evaluation management dialogs. */
object SourceEvaluationPrivacyLabelPolicy {
    fun unsafeSourceLabel(unsafe: SourceEvaluationUnsafeSource, evaluationModeEnabled: Boolean): String =
        if (evaluationModeEnabled) {
            unsafe.sourceId?.let(EvaluationModeFormatter::sourceLabel)
                ?: EvaluationModeFormatter.sourceLabel(
                    unsafe.evaluationKey ?: "ext:${unsafe.extensionPkgName}",
                )
        } else {
            unsafe.extensionName
        }

    fun blockedPackageLabel(pkg: UnsafeExtensionPackage, evaluationModeEnabled: Boolean): String =
        if (evaluationModeEnabled) {
            EvaluationModeFormatter.sourceLabel("ext:${pkg.pkgName}")
        } else {
            pkg.extensionName ?: pkg.pkgName
        }
}
