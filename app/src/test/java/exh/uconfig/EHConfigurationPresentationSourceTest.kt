package exh.uconfig

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class EHConfigurationPresentationSourceTest {

    @Test
    fun `dialog uses process coordinator and never renders exception message`() {
        val source = Files.readAllBytes(
            Path.of("src/main/java/eu/kanade/presentation/more/settings/screen/ConfigureExhDialog.kt"),
        ).toString(Charsets.UTF_8)

        assertTrue(source.contains("EHConfigurationCoordinator"))
        assertTrue(source.contains("eh_settings_configuration_failed_message_safe"))
        assertTrue(source.contains("MR.strings.action_retry"))
        assertTrue(source.contains("MR.strings.action_close"))
        assertFalse(source.contains("Exception?"))
        assertFalse(source.contains(".message.orEmpty()"))
        assertFalse(source.contains("NonCancellable"))
    }

    @Test
    fun `settings screen dispatches to the sole application dialog host`() {
        val settingsSource = Files.readAllBytes(
            Path.of("src/main/java/eu/kanade/presentation/more/settings/screen/SettingsEhScreen.kt"),
        ).toString(Charsets.UTF_8)
        val activitySource = Files.readAllBytes(
            Path.of("src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt"),
        ).toString(Charsets.UTF_8)

        assertTrue(settingsSource.contains("RequestExhConfiguration("))
        assertFalse(settingsSource.contains("ConfigureExhDialog(run"))
        assertTrue(activitySource.contains("ConfigureExhDialog(run"))
    }
}
