package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.online.all.Lanraragi
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.source.online.all.NHentai
import eu.kanade.tachiyomi.source.online.all.Pururin
import eu.kanade.tachiyomi.source.online.english.EightMuses
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

// KMK v0.8.17 (Komikku v1.14.1 reconciliation) -->
/**
 * Regression coverage for [AndroidSourceManager.DELEGATED_SOURCES] and the delegate-matching predicate
 * in [AndroidSourceManager.toInternalSource] (private, so the exact same predicate is replicated here
 * against the real public [AndroidSourceManager.DELEGATED_SOURCES] data -- constructing a full
 * [AndroidSourceManager] needs Android `Context`/`ExtensionManager` and is out of scope for a pure unit
 * test). Upstream v1.14.1 fixed delegated-source loading (PR #1797) by replacing a
 * `Map<String, DelegatedSource>` keyed by qualified class name (plus a separate `factory`-flag
 * prefix-match pass) with a plain `List<DelegatedSource>` matched inline by exact class name or, only
 * when `factory = true`, by class-name prefix. This test proves that migration was reconciled
 * correctly into the KMK tree, including the Pururin source-id/package move
 * (`eu.kanade.tachiyomi.extension.en.pururin.Pururin` -> `eu.kanade.tachiyomi.extension.all.pururin.Pururin`,
 * `PURURIN_SOURCE_ID` -> `fillInSourceId`) and that no entry retains the pre-fix `factory = true` flag.
 */
class DelegatedSourceResolutionTest {

    /** Mirrors the exact predicate in [AndroidSourceManager.toInternalSource]. */
    private fun resolve(sourceQName: String) = AndroidSourceManager.DELEGATED_SOURCES.firstOrNull { delegated ->
        sourceQName == delegated.originalSourceQualifiedClassName ||
            (delegated.factory && sourceQName.startsWith(delegated.originalSourceQualifiedClassName))
    }

    @Test
    fun `Pururin delegate resolves by its new all-package qualified class name`() {
        val delegate = resolve("eu.kanade.tachiyomi.extension.all.pururin.Pururin")
        assertEquals(Pururin::class, delegate?.newSourceClass)
    }

    @Test
    fun `Pururin no longer resolves by its old english-package qualified class name`() {
        assertNull(resolve("eu.kanade.tachiyomi.extension.en.pururin.Pururin"))
    }

    @Test
    fun `MangaDex delegate resolves by its exact qualified class name`() {
        val delegate = resolve("eu.kanade.tachiyomi.extension.all.mangadex.MangaDex")
        assertEquals(MangaDex::class, delegate?.newSourceClass)
    }

    @Test
    fun `MangaDex no longer resolves by bare package-prefix match now that factory is false`() {
        // Pre-fix behavior treated MangaDex as a "factory" entry, matching any subclass under the
        // package prefix. Post-fix, MangaDex is an exact-match entry like every other delegate.
        assertNull(resolve("eu.kanade.tachiyomi.extension.all.mangadex.SomeMangaDexVariant"))
    }

    @Test
    fun `NHentai delegate resolves by its exact qualified class name`() {
        val delegate = resolve("eu.kanade.tachiyomi.extension.all.nhentai.NHentai")
        assertEquals(NHentai::class, delegate?.newSourceClass)
    }

    @Test
    fun `LANraragi delegate resolves by its exact qualified class name`() {
        val delegate = resolve("eu.kanade.tachiyomi.extension.all.lanraragi.LANraragi")
        assertEquals(Lanraragi::class, delegate?.newSourceClass)
    }

    @Test
    fun `8Muses delegate resolves by its exact qualified class name`() {
        val delegate = resolve("eu.kanade.tachiyomi.extension.en.eightmuses.EightMuses")
        assertEquals(EightMuses::class, delegate?.newSourceClass)
    }

    @Test
    fun `an unrelated source class name resolves to no delegate`() {
        assertNull(resolve("eu.kanade.tachiyomi.extension.en.somerandomsource.SomeRandomSource"))
    }

    @Test
    fun `no DELEGATED_SOURCES entry retains the pre-fix factory flag`() {
        val stillFactory = AndroidSourceManager.DELEGATED_SOURCES.filter { it.factory }
        assertEquals(true, stillFactory.isEmpty())
    }
}
// KMK <--
