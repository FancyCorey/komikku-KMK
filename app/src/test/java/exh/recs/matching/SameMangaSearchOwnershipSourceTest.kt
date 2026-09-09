package exh.recs.matching

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class SameMangaSearchOwnershipSourceTest {

    @Test
    fun `only the shared searcher owns same-manga SourceRuntime search`() {
        val searcher = source("app/src/main/java/exh/recs/matching/SameMangaCandidateSearcher.kt")
        val crossExtension = source("app/src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt")
        val bestVersion = source("app/src/main/java/exh/recs/bestversion/BestVersionCompareScreenModel.kt")
        val bestVersionGateway = source("app/src/main/java/exh/recs/bestversion/BestVersionCandidateSearchGateway.kt")

        assertEquals(1, Regex("SourceRuntimeOperation\\.Search").findAll(searcher).count())
        assertFalse(crossExtension.contains("SourceRuntimeOperation.Search"))
        assertFalse(bestVersion.contains("SourceRuntimeOperation.Search"))
        assertFalse(bestVersionGateway.contains("SourceRuntimeOperation.Search"))
        assertTrue(crossExtension.contains("SameMangaCandidateSearcher("))
        assertTrue(bestVersion.contains("SameMangaBestVersionCandidateSearchGateway("))
        assertTrue(bestVersionGateway.contains("SameMangaCandidateSearcher("))
    }

    @Test
    fun `screen models and searcher create no executor`() {
        val paths = listOf(
            "app/src/main/java/exh/recs/matching/SameMangaCandidateSearcher.kt",
            "app/src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt",
            "app/src/main/java/exh/recs/bestversion/BestVersionCompareScreenModel.kt",
            "app/src/main/java/exh/recs/bestversion/BestVersionCandidateSearchGateway.kt",
        )

        paths.forEach { path ->
            val text = source(path)
            assertFalse(text.contains("newFixedThreadPool"))
            assertFalse(text.contains("Executors."))
        }
    }

    @Test
    fun `both screen owners cancel search and close their handle`() {
        val crossExtension = source("app/src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt")
        val bestVersion = source("app/src/main/java/exh/recs/bestversion/BestVersionCompareScreenModel.kt")

        listOf(crossExtension, bestVersion).forEach { text ->
            assertTrue(text.contains("override fun onDispose()"))
            assertTrue(text.contains("searchJob?.cancel()"))
            assertTrue(text.contains("dispatcherHandle.close()"))
        }
    }

    private fun source(relativePath: String): String {
        val direct = Path.of(relativePath)
        val fromParent = Path.of("..").resolve(relativePath)
        val path = listOf(direct, fromParent)
            .map(Path::toAbsolutePath)
            .firstOrNull(Files::isRegularFile)
            ?: error("Unable to resolve source file: $relativePath")
        return String(Files.readAllBytes(path), Charsets.UTF_8)
    }
}
