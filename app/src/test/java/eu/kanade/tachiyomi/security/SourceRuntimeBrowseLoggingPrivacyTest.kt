package eu.kanade.tachiyomi.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class SourceRuntimeBrowseLoggingPrivacyTest {

    @Test
    fun `browse source diagnostics do not expose source identity or throwable`() {
        val source = File("src/main/java/eu/kanade/tachiyomi/ui/browse/source/browse/BrowseSourceScreenModel.kt").readText()

        assertFalse(source.contains("logcat(LogPriority.WARN, throwable)"))
        assertFalse(source.contains("BrowseSourceScreenModel[\${src.name}]"))
        assertFalse(source.contains("logcat(LogPriority.ERROR, e)"))
        assertTrue(source.contains("Browse source filter loading failed"))
        assertTrue(source.contains("Browse metadata update failed"))
    }

    @Test
    fun `source feed diagnostics do not expose source identity or throwable`() {
        val source = File("src/main/java/eu/kanade/tachiyomi/ui/browse/source/feed/SourceFeedScreenModel.kt").readText()

        assertFalse(source.contains("logcat(LogPriority.WARN, throwable)"))
        assertFalse(source.contains("SourceFeedScreenModel[\${src.name}]"))
        assertTrue(source.contains("Source feed filter loading failed"))
    }
}
