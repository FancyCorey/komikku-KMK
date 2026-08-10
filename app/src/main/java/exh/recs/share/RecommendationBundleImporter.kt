package exh.recs.share

import android.content.Context
import android.net.Uri
import exh.recs.RecommendationErrorClassifier
import exh.recs.recommendationErrorMessageRes
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.kmk.KMR

// KMK -->

object RecommendationBundleImporter {

    data class ImportReadResult(
        val validation: RecommendationBundleValidator.ValidationResult,
        val fileSizeBytes: Int,
    )

    fun readFromUri(context: Context, uri: Uri): ImportReadResult {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return ImportReadResult(
                    // KMK v0.7.46: already a KMR string, not raw text
                    RecommendationBundleValidator.ValidationResult.MalformedJson(
                        context.stringResource(KMR.strings.rec_bundle_load_error_file_open_failed),
                    ),
                    0,
                )
        } catch (e: Exception) {
            // KMK v0.7.46: classified + localized, not raw exception text
            return ImportReadResult(
                RecommendationBundleValidator.ValidationResult.MalformedJson(
                    context.stringResource(recommendationErrorMessageRes(RecommendationErrorClassifier.classify(e))),
                ),
                0,
            )
        }
        val content = bytes.toString(Charsets.UTF_8)
        return ImportReadResult(
            validation = RecommendationBundleValidator.validate(content, bytes.size),
            fileSizeBytes = bytes.size,
        )
    }
}

// KMK <--
