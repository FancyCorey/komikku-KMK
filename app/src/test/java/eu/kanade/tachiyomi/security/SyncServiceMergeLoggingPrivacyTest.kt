package eu.kanade.tachiyomi.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class SyncServiceMergeLoggingPrivacyTest {

    @Test
    fun `sync merge diagnostics do not expose titles urls names or keys`() {
        val source = File("src/main/java/eu/kanade/tachiyomi/data/sync/service/SyncService.kt").readText()

        val forbiddenLogFragments = listOf(
            "Keeping local version of \${local.title}",
            "Keeping remote version of \${remote.title}",
            "Processing chapter key: \$compositeKey",
            "\${localChapter.name}",
            "\${remoteChapter.name}",
            "Processing source ID: \$sourceId",
            "\${localSource.name}",
            "\${remoteSource.name}",
            "Processing preference key: \$key",
            "\${localPreference.key}",
            "\${remotePreference.key}",
            "Processing source preference key: \$sourceKey",
            "\${localSourcePreference.sourceKey}",
            "\${remoteSourcePreference.sourceKey}",
            "Processing saved search key: \$compositeKey",
            "\${localSearch.name}",
            "\${remoteSearch.name}",
        )

        forbiddenLogFragments.forEach { fragment ->
            assertFalse(source.contains(fragment), "Sensitive sync log fragment remains: $fragment")
        }
        assertTrue(source.contains("Chapter merge completed"))
        assertTrue(source.contains("Source merge completed"))
        assertTrue(source.contains("Saved searches merge completed"))
    }
}
