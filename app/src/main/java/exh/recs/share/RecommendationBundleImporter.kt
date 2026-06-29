package exh.recs.share

import android.content.Context
import android.net.Uri

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
                    RecommendationBundleValidator.ValidationResult.MalformedJson("Could not open file"),
                    0,
                )
        } catch (e: Exception) {
            return ImportReadResult(
                RecommendationBundleValidator.ValidationResult.MalformedJson(e.message ?: "IO error"),
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
