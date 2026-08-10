package eu.kanade.tachiyomi.presentation.more.settings.screen.data

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.io.File

class RestoreBackupScreenPrivacyTest {

    @Test
    fun `restore error state does not retain or render raw uri`() {
        val source = File("src/main/java/eu/kanade/presentation/more/settings/screen/data/RestoreBackupScreen.kt").readText()

        assertFalse(source.contains("appendLine(error.uri.toString())"))
        assertFalse(source.contains("InvalidRestore(uri"))
        assertFalse(source.contains("MissingRestoreComponents(uri,"))
        assertFalse(source.contains("val uri: Uri?"))
    }
}
