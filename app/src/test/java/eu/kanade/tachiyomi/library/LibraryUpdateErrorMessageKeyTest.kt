package eu.kanade.tachiyomi.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class LibraryUpdateErrorMessageKeyTest {

    @Test
    fun `stable storage values round trip`() {
        LibraryUpdateErrorMessageKey.entries.forEach { key ->
            assertEquals(key, LibraryUpdateErrorMessageKey.fromStorageValue(key.storageValue))
        }
    }

    @Test
    fun `legacy raw exception text resolves to generic error`() {
        assertEquals(
            LibraryUpdateErrorMessageKey.Unknown,
            LibraryUpdateErrorMessageKey.fromStorageValue(
                "java.io.FileNotFoundException: /storage/emulated/0/private-name (Permission denied)",
            ),
        )
    }

    @Test
    fun `blank and missing legacy values resolve to generic error`() {
        assertEquals(LibraryUpdateErrorMessageKey.Unknown, LibraryUpdateErrorMessageKey.fromStorageValue(""))
        assertEquals(LibraryUpdateErrorMessageKey.Unknown, LibraryUpdateErrorMessageKey.fromStorageValue(null))
    }

    @Test
    fun `writers persist stable keys and presentation never carries raw stored text`() {
        val job = source("src/main/java/eu/kanade/tachiyomi/data/library/LibraryUpdateJob.kt")
        val manga = source("src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreenModel.kt")
        val screenModel = source(
            "src/main/java/eu/kanade/tachiyomi/ui/libraryUpdateError/LibraryUpdateErrorScreenModel.kt",
        )
        val presentation = source(
            "src/main/java/eu/kanade/presentation/libraryUpdateError/components/LibraryUpdateErrorUiItem.kt",
        )

        assertFalse("else -> e.message" in job)
        assertFalse("writeErrorToDB(state.manga to with(context) { e.formattedMessage })" in manga)
        assertTrue("LibraryUpdateErrorMessageKey.fromStorageValue(message?.message)" in screenModel)
        assertTrue("data class Header(val errorKey: LibraryUpdateErrorMessageKey" in presentation)
        assertFalse("data class Header(val errorMessage: String" in presentation)
    }

    private fun source(path: String): String = Files.readAllBytes(Path.of(path)).toString(Charsets.UTF_8)
}
