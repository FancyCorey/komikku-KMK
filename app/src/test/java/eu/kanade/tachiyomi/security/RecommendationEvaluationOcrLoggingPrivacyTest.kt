package eu.kanade.tachiyomi.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class RecommendationEvaluationOcrLoggingPrivacyTest {
    @Test
    fun `recommendation evaluation OCR and setup diagnostics omit payloads`() {
        val sources = mapOf(
            "BrowsePersonalRecommendationsScreenModel.kt" to
                "src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt",
            "GetSourceEvaluationCandidates.kt" to
                "src/main/java/exh/recs/evaluation/GetSourceEvaluationCandidates.kt",
            "GetNonInstalledSourceSuggestions.kt" to
                "src/main/java/exh/recs/discovery/GetNonInstalledSourceSuggestions.kt",
            "SourceEvaluationJob.kt" to "src/main/java/exh/recs/evaluation/SourceEvaluationJob.kt",
            "SourceRecommendationQualityJob.kt" to
                "src/main/java/exh/recs/evaluation/SourceRecommendationQualityJob.kt",
            "OcrIndexService.kt" to "src/main/java/exh/ocr/OcrIndexService.kt",
            "ShizukuSetupHelper.kt" to "src/main/java/exh/recs/evaluation/ShizukuSetupHelper.kt",
        )

        val contents = sources.mapValues { (_, path) -> File(path).readText() }
        listOf(
            "logcat(LogPriority.WARN, e)",
            "logcat(LogPriority.ERROR, e)",
            "logcat(LogPriority.WARN, dbEx)",
            "mangaId=${'$'}{pageRef.manga.id}",
            "chapterId=${'$'}{pageRef.chapter.id}",
            "source=${'$'}{recSource.name}",
            "logUnexpectedError = { e ->",
        ).forEach { fragment ->
            contents.forEach { (file, source) ->
                assertFalse(source.contains(fragment), "Sensitive diagnostic fragment remains in $file: $fragment")
            }
        }

        listOf(
            "Cached-merge chapter-count lookup failed",
            "source_evaluation table unavailable",
            "KMK SourceEvaluationJob: unexpected error",
            "KMK SourceRecommendationQualityJob: unexpected error",
            "OCR: page processing failed",
            "Could not open Shizuku download page",
        ).forEach { marker ->
            assertTrue(contents.values.any { it.contains(marker) }, "Expected generic marker is missing: $marker")
        }
    }
}
