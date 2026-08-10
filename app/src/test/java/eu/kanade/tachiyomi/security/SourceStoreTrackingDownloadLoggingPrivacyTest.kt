package eu.kanade.tachiyomi.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class SourceStoreTrackingDownloadLoggingPrivacyTest {
    @Test
    fun `source store tracking and download diagnostics use generic markers`() {
        val sources = mapOf(
            "TrustExtensionRepositoryMigration.kt" to
                "src/main/java/mihon/core/migration/migrations/TrustExtensionRepositoryMigration.kt",
            "DelayedTrackingStore.kt" to
                "src/main/java/eu/kanade/domain/track/store/DelayedTrackingStore.kt",
            "PreferenceRestorer.kt" to
                "src/main/java/eu/kanade/tachiyomi/data/backup/restore/restorers/PreferenceRestorer.kt",
            "ExtensionStoreService.kt" to "../data/src/main/java/mihon/data/extension/service/ExtensionStoreService.kt",
            "SourcePagingSource.kt" to "../data/src/main/java/tachiyomi/data/source/SourcePagingSource.kt",
            "RecommendationPagingSource.kt" to
                "src/main/java/exh/recs/sources/RecommendationPagingSource.kt",
            "Downloader.kt" to "src/main/java/eu/kanade/tachiyomi/data/download/Downloader.kt",
        )

        val contents = sources.mapValues { (_, path) -> File(path).readText() }
        listOf(
            "baseUrl: ${'$'}source",
            "${'$'}trackId",
            "${'$'}lastChapterRead",
            "Failed to restore preference <${'$'}key>",
            "${'$'}updatedIndexUrl",
            "${'$'}{this::class.simpleName}: Failed to load paging source",
            "${'$'}{this::class.simpleName}: Failed to load paging source (extension linkage failure)",
            "logcat { name +",
            "logcat(LogPriority.ERROR, e)",
        ).forEach { fragment ->
            contents.forEach { (file, source) ->
                assertFalse(source.contains(fragment), "Sensitive diagnostic fragment remains in $file: $fragment")
            }
        }

        listOf(
            "Extension repository migration failed",
            "Tracking item queued",
            "Preference restore failed",
            "Extension store fetch failed",
            "Source paging load failed",
            "Source paging linkage failure",
            "Recommendation results loaded count=",
            "Recommendation results failed",
            "Download job failed",
            "Downloaded image split failed",
        ).forEach { marker ->
            assertTrue(contents.values.any { it.contains(marker) }, "Expected generic marker is missing: $marker")
        }
    }
}
