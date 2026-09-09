package eu.kanade.tachiyomi.presentation.more.settings.screen.data

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class RestoreBackupDocumentContractSourceTest {
    @Test
    fun `restore backup uses open document contract for an existing user file`() {
        val source = Files.readAllBytes(
            Path.of("src/main/java/eu/kanade/presentation/more/settings/screen/SettingsDataScreen.kt"),
        ).toString(Charsets.UTF_8)

        assertTrue(source.contains("object : ActivityResultContracts.OpenDocument()"))
        assertTrue(source.contains("chooseBackup.launch(arrayOf(\"*/*\"))"))
        assertTrue(source.contains("RestoreBackupScreen(it.toString())"))
    }
}
