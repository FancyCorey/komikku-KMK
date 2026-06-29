package exh.source

import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.CatalogueSource

// KMK -->
/**
 * Conservative classifier for clearly explicit porn/hentai sources.
 *
 * Triggers on: strong explicit keywords in name/pkgName, or known explicit source IDs.
 * Does NOT trigger on: ecchi, nsfw, mature, lewd, adult alone.
 *
 * Separate from the broad `isNsfw` flag — that flag covers all adult content including
 * ecchi, so `isNsfw == true` does not imply this classifier returns true.
 */
object ExplicitSourceClassifier {

    private val EXPLICIT_NAME_SUBSTRINGS = listOf(
        "hentai", "porn", "porno", "pururin", "tsumino", "8muses", "hbrowse",
        "luscious", "doujins", "multporn", "xxx", "erotic", "smut",
        "adult comic", "adult manga", "adult manhwa", "adult manhua",
    )

    private val EXPLICIT_SOURCE_IDS: Set<Long> by lazy {
        setOf(
            NHENTAI_SOURCE_ID,
            PURURIN_SOURCE_ID,
            TSUMINO_SOURCE_ID,
            EIGHTMUSES_SOURCE_ID,
            HBROWSE_SOURCE_ID,
        ) + EHENTAI_EXT_SOURCES.keys + EXHENTAI_EXT_SOURCES.keys
    }

    fun isExplicitName(name: String): Boolean {
        val lower = name.lowercase()
        return EXPLICIT_NAME_SUBSTRINGS.any { lower.contains(it) }
    }

    fun isExplicitPackageName(pkgName: String): Boolean {
        val lower = pkgName.lowercase()
        return lower.contains("hentai") || lower.contains("porn")
    }

    fun isExplicitSourceId(sourceId: Long): Boolean = sourceId in EXPLICIT_SOURCE_IDS

    fun isExplicitExtension(extension: Extension): Boolean =
        isExplicitName(extension.name) || isExplicitPackageName(extension.pkgName)

    fun isExplicitCatalogueSource(source: CatalogueSource): Boolean =
        isExplicitSourceId(source.id) || isExplicitName(source.name)
}
// KMK <--
