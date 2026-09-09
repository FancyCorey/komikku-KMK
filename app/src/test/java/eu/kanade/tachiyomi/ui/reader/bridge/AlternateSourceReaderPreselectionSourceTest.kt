package eu.kanade.tachiyomi.ui.reader.bridge

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/** Keeps reader alternate-source defaults on the shared configurable matching policy. */
class AlternateSourceReaderPreselectionSourceTest {

    @Test
    fun `reader chooser resolves configured exact all or none mode`() {
        val source = source("app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt")

        assertTrue(
            source.contains("SameMangaPreselectionMode.resolve("),
            "reader chooser must resolve the shared persisted preselection mode",
        )
        assertTrue(
            source.contains("sourcePreferencesForRatingPrompt.sameMangaMatchPreselectionMode().get()"),
            "reader chooser must read the shared preselection mode preference",
        )
        assertTrue(
            source.contains("SameMangaPreselectionPolicy.shouldSelect(\n                            preselectionMode,"),
            "reader chooser must apply the resolved mode rather than a local default",
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
