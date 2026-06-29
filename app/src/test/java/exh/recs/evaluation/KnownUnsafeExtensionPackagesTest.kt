package exh.recs.evaluation

import eu.kanade.tachiyomi.extension.util.KnownUnsafeExtensionPackages
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK -->
class KnownUnsafeExtensionPackagesTest {

    @Test
    fun `ALL contains DigitalComicMuseum`() {
        assertTrue(KnownUnsafeExtensionPackages.ALL.contains(KnownUnsafeExtensionPackages.DIGITAL_COMIC_MUSEUM))
    }

    @Test
    fun `DigitalComicMuseum has correct package name`() {
        assertEquals(
            "eu.kanade.tachiyomi.extension.en.digitalcomicmuseum",
            KnownUnsafeExtensionPackages.DIGITAL_COMIC_MUSEUM.pkgName,
        )
    }

    @Test
    fun `DigitalComicMuseum extension name is non-empty`() {
        assertFalse(KnownUnsafeExtensionPackages.DIGITAL_COMIC_MUSEUM.extensionName.isBlank())
    }

    @Test
    fun `DigitalComicMuseum reason mentions crash`() {
        val reason = KnownUnsafeExtensionPackages.DIGITAL_COMIC_MUSEUM.reason.lowercase()
        assertTrue(reason.contains("crash") || reason.contains("sigsegv") || reason.contains("overflow")) {
            "Reason should mention native crash: $reason"
        }
    }

    @Test
    fun `isKnownUnsafe returns true for DigitalComicMuseum package`() {
        assertTrue(KnownUnsafeExtensionPackages.isKnownUnsafe("eu.kanade.tachiyomi.extension.en.digitalcomicmuseum"))
    }

    @Test
    fun `isKnownUnsafe returns false for unknown package`() {
        assertFalse(KnownUnsafeExtensionPackages.isKnownUnsafe("eu.kanade.tachiyomi.extension.en.mangadex"))
    }

    @Test
    fun `isKnownUnsafe returns false for empty string`() {
        assertFalse(KnownUnsafeExtensionPackages.isKnownUnsafe(""))
    }
}
// KMK <--
