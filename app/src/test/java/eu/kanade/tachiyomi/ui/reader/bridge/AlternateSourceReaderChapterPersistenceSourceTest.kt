package eu.kanade.tachiyomi.ui.reader.bridge

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/** Protects the live-chapter-to-reader route contract. */
class AlternateSourceReaderChapterPersistenceSourceTest {

    @Test
    fun `live chapter discovery synchronizes chapters before exposing them`() {
        val text = source("app/src/main/java/eu/kanade/tachiyomi/ui/reader/bridge/AlternateSourceReaderCandidateGateway.kt")

        assertTrue(
            text.contains("syncChaptersWithSource.await(chapters, manga, source, manualFetch = true)"),
            "selected live chapters must exist in the local database before route resolution",
        )
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
