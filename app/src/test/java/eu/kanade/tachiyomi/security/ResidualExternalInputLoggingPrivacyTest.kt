package eu.kanade.tachiyomi.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ResidualExternalInputLoggingPrivacyTest {
    @Test
    fun `residual external input diagnostics use generic markers`() {
        val sources = mapOf(
            "ShizukuInstaller.kt" to "src/main/java/eu/kanade/tachiyomi/extension/installer/ShizukuInstaller.kt",
            "EhLoginActivity.kt" to "src/main/java/exh/ui/login/EhLoginActivity.kt",
            "EHentai.kt" to "src/main/java/eu/kanade/tachiyomi/source/online/all/EHentai.kt",
            "SourceRecommendationQualityRunner.kt" to "src/main/java/exh/recs/evaluation/SourceRecommendationQualityRunner.kt",
            "LibraryUpdateJob.kt" to "src/main/java/eu/kanade/tachiyomi/data/library/LibraryUpdateJob.kt",
            "LibraryScreenModel.kt" to "src/main/java/eu/kanade/tachiyomi/ui/library/LibraryScreenModel.kt",
            "MigrationListScreenModel.kt" to "src/main/java/mihon/feature/migration/list/MigrationListScreenModel.kt",
            "ExtensionManager.kt" to "src/main/java/eu/kanade/tachiyomi/extension/ExtensionManager.kt",
            "AndroidSourceManager.kt" to "src/main/java/eu/kanade/tachiyomi/source/AndroidSourceManager.kt",
            "RecommendsScreenModel.kt" to "src/main/java/exh/recs/RecommendsScreenModel.kt",
        )

        val contents = sources.mapValues { (_, path) -> File(path).readText() }
        val forbidden = listOf(
            "Overwritten Cookie:",
            "xLogD(url)",
            "${'$'}{entry.downloadId} ${'$'}{entry.uri}",
            "Failed to install extension ${'$'}packageName: ${'$'}message",
            "${'$'}{evaluation.sourceName}",
            "${'$'}{ext.name}",
            "${'$'}{manga.title}",
            "Migration did not complete for manga",
            "${'$'}{manga.manga.id}: ${'$'}outcome",
            "source=${'$'}{recSource.name}",
            "Removing blacklisted extension: (name:",
            "Delegating source: %s -> %s!",
        )

        contents.forEach { (file, source) ->
            forbidden.forEach { fragment ->
                assertFalse(source.contains(fragment), "Sensitive diagnostic fragment remains in $file: $fragment")
            }
        }

        listOf(
            "Shizuku extension installation failed",
            "EH login page processing started",
            "EHentai cookie header rebuilt",
            "KMK SourceRecommendationQualityRunner: probe failed",
            "Library initial track update failed",
            "MangaDex status sync failed",
            "Migration did not complete",
            "Removing blacklisted extension",
            "Delegating source",
            "GROUP_PREVIEW error elapsedMs=",
        ).forEach { marker ->
            assertTrue(contents.values.any { it.contains(marker) }, "Expected generic marker is missing: $marker")
        }
    }
}
