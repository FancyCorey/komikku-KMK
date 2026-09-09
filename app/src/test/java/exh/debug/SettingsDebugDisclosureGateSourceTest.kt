package exh.debug

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class SettingsDebugDisclosureGateSourceTest {
    private fun source(path: String): String = Files.readAllBytes(Path.of(path)).toString(Charsets.UTF_8)

    @Test
    fun `advanced settings exposes debug navigation only through shared gate`() {
        val settings = source(
            "src/main/java/eu/kanade/presentation/more/settings/screen/SettingsAdvancedScreen.kt",
        )
        val navigationIndex = settings.indexOf("navigator.push(SettingsDebugScreen())")
        val gateIndex = settings.lastIndexOf("DeveloperOptionsGatePolicy.canExposeDiagnostics", navigationIndex)

        assertTrue(navigationIndex > 0)
        assertTrue(gateIndex > 0)
        assertTrue(gateIndex < navigationIndex)
        assertTrue("developerOptionsEnabled = developerOptionsEnabled" in settings)
        assertTrue("evaluationModeEnabled = evaluationModeEnabled" in settings)
    }

    @Test
    fun `debug destination rechecks gate and exits when disclosure is forbidden`() {
        val screen = source("src/main/java/exh/debug/SettingsDebugScreen.kt")

        assertTrue("DeveloperOptionsGatePolicy.canExposeDiagnostics" in screen)
        assertTrue("if (!diagnosticsAllowed) navigator.pop()" in screen)
        assertTrue("if (!diagnosticsAllowed)" in screen)
        assertFalse("SelectionContainer" in screen)
    }

    @Test
    fun `debug destination has exactly one production navigation entry`() {
        val references = Files.walk(Path.of("src/main/java")).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .mapToInt { path ->
                    "SettingsDebugScreen\\(\\)".toRegex()
                        .findAll(Files.readAllBytes(path).toString(Charsets.UTF_8))
                        .count()
                }
                .sum()
        }

        assertTrue(references == 1)
    }
}
