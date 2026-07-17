package exh.source

import eu.kanade.tachiyomi.extension.model.Extension
import mihon.domain.extension.model.ExtensionStore
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK -->
class ExplicitSourceClassifierTest {

    // --- isExplicitName ---

    @Test
    fun `isExplicitName matches hentai in name`() {
        assertTrue(ExplicitSourceClassifier.isExplicitName("HentaiFox"))
    }

    @Test
    fun `isExplicitName matches porn in name`() {
        assertTrue(ExplicitSourceClassifier.isExplicitName("PornComix"))
    }

    @Test
    fun `isExplicitName matches pururin`() {
        assertTrue(ExplicitSourceClassifier.isExplicitName("Pururin"))
    }

    @Test
    fun `isExplicitName matches tsumino`() {
        assertTrue(ExplicitSourceClassifier.isExplicitName("Tsumino"))
    }

    @Test
    fun `isExplicitName matches 8muses`() {
        assertTrue(ExplicitSourceClassifier.isExplicitName("8Muses"))
    }

    @Test
    fun `isExplicitName matches xxx`() {
        assertTrue(ExplicitSourceClassifier.isExplicitName("XXX Comics"))
    }

    @Test
    fun `isExplicitName does not match ecchi`() {
        assertFalse(ExplicitSourceClassifier.isExplicitName("EcchiSource"))
    }

    @Test
    fun `isExplicitName does not match nsfw`() {
        assertFalse(ExplicitSourceClassifier.isExplicitName("NSFWManga"))
    }

    @Test
    fun `isExplicitName does not match mature`() {
        assertFalse(ExplicitSourceClassifier.isExplicitName("MatureManga"))
    }

    @Test
    fun `isExplicitName does not match adult alone`() {
        assertFalse(ExplicitSourceClassifier.isExplicitName("AdultContent"))
    }

    @Test
    fun `isExplicitName matches adult manga`() {
        assertTrue(ExplicitSourceClassifier.isExplicitName("Best Adult Manga Site"))
    }

    @Test
    fun `isExplicitName is case insensitive`() {
        assertTrue(ExplicitSourceClassifier.isExplicitName("HENTAI Fox"))
    }

    @Test
    fun `isExplicitName does not match plain manga source`() {
        assertFalse(ExplicitSourceClassifier.isExplicitName("MangaDex"))
    }

    // --- isExplicitPackageName ---

    @Test
    fun `isExplicitPackageName matches hentai in package`() {
        assertTrue(ExplicitSourceClassifier.isExplicitPackageName("eu.kanade.tachiyomi.extension.all.hentaifox"))
    }

    @Test
    fun `isExplicitPackageName matches porn in package`() {
        assertTrue(ExplicitSourceClassifier.isExplicitPackageName("eu.kanade.tachiyomi.extension.en.porncomix"))
    }

    @Test
    fun `isExplicitPackageName does not match normal package`() {
        assertFalse(ExplicitSourceClassifier.isExplicitPackageName("eu.kanade.tachiyomi.extension.all.mangadex"))
    }

    // --- isExplicitSourceId ---

    @Test
    fun `isExplicitSourceId matches NHentai source ID`() {
        assertTrue(ExplicitSourceClassifier.isExplicitSourceId(NHENTAI_SOURCE_ID))
    }

    @Test
    fun `isExplicitSourceId matches Pururin source ID`() {
        assertTrue(ExplicitSourceClassifier.isExplicitSourceId(PURURIN_SOURCE_ID))
    }

    @Test
    fun `isExplicitSourceId matches EHentai source IDs`() {
        val firstEhId = EHENTAI_EXT_SOURCES.keys.first()
        assertTrue(ExplicitSourceClassifier.isExplicitSourceId(firstEhId))
    }

    @Test
    fun `isExplicitSourceId does not match random ID`() {
        assertFalse(ExplicitSourceClassifier.isExplicitSourceId(1234567890L))
    }

    // --- isExplicitExtension ---

    private fun ext(name: String, pkgName: String = "eu.test.${name.lowercase()}") = Extension.Available(
        name = name,
        pkgName = pkgName,
        versionName = "1.0",
        versionCode = 1L,
        libVersion = 1.4,
        lang = "en",
        isNsfw = true,
        signatureHash = "abc",
        storeName = "TestRepo",
        sources = emptyList(),
        apkUrl = "https://example.com/apk/$pkgName.apk",
        iconUrl = "",
        store = ExtensionStore(
            indexUrl = "https://example.com",
            name = "TestRepo",
            badgeLabel = "TestRepo",
            signingKey = "abc",
            contact = ExtensionStore.Contact(website = "", discord = null),
            isLegacy = false,
            extensionListUrl = null,
        ),
    )

    @Test
    fun `isExplicitExtension true for hentai name`() {
        assertTrue(ExplicitSourceClassifier.isExplicitExtension(ext("HentaiFox")))
    }

    @Test
    fun `isExplicitExtension true for porn package`() {
        assertTrue(ExplicitSourceClassifier.isExplicitExtension(ext("SomeSource", "eu.test.porncomix")))
    }

    @Test
    fun `isExplicitExtension false for ecchi-only source`() {
        assertFalse(ExplicitSourceClassifier.isExplicitExtension(ext("EcchiHub", "eu.test.ecchihub")))
    }

    @Test
    fun `isExplicitExtension false for normal manga source`() {
        assertFalse(ExplicitSourceClassifier.isExplicitExtension(ext("MangaDex", "eu.kanade.tachiyomi.extension.all.mangadex")))
    }
}
// KMK <--
