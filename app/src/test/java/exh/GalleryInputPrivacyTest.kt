package exh

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class GalleryInputPrivacyTest {
    @Test
    fun `intercept activity handles missing view data without force unwrap`() {
        val source = File("src/main/java/exh/ui/intercept/InterceptActivity.kt").readText()

        assertFalse(source.contains("intent.dataString!!"))
        assertTrue(source.contains("val gallery = intent.dataString ?: run"))
    }

    @Test
    fun `gallery diagnostics do not include incoming url source text or throwables`() {
        val source = File("src/main/java/exh/GalleryAdder.kt").readText()

        assertFalse(source.contains("gallery_adder_importing_gallery, url"))
        assertFalse(source.contains("forceSource?.toString()"))
        assertFalse(source.contains("logger()?.w(context.stringResource(SYMR.strings.gallery_adder_could_not_add_gallery, url), e)"))
        assertFalse(source.contains("logger()?.w(\"Gallery import failed\", e)"))
        assertFalse(source.contains("logger()?.e(context.stringResource(SYMR.strings.gallery_adder_source_uri_must_match), e)"))
    }
}
