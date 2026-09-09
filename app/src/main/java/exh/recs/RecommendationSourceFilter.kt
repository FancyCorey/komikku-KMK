package exh.recs

// KMK -->
import eu.kanade.tachiyomi.source.Source
import java.util.Locale

internal object RecommendationSourceFilter {

    val DefaultLanguages: Set<String> = setOf("en")

    /** Local Source has id 0 by convention in Komikku/Mihon. */
    private const val LOCAL_SOURCE_ID = 0L

    /** Returns languages normalized to lowercase with blanks removed, falling back to [DefaultLanguages]. */
    fun normalizeLanguages(languages: Set<String>): Set<String> {
        val normalized = languages.map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }.toSet()
        return normalized.ifEmpty { DefaultLanguages }
    }

    fun isLocalSource(source: Source): Boolean = source.id == LOCAL_SOURCE_ID

    fun isAllowedLanguage(source: Source, normalizedLanguages: Set<String>): Boolean =
        source.lang.lowercase(Locale.ROOT) in normalizedLanguages

    /**
     * Returns sources from [sources] that match [languages] and are not Local Source.
     * [includeLocal] can override the Local Source exclusion.
     */
    fun filterForRecommendations(
        sources: List<Source>,
        languages: Set<String>,
        includeLocal: Boolean = false,
    ): List<Source> {
        val langs = normalizeLanguages(languages)
        return sources.filter { source ->
            if (isLocalSource(source) && !includeLocal) return@filter false
            isAllowedLanguage(source, langs)
        }
    }

    /**
     * Returns the distinct sorted list of language codes from [sources], excluding Local Source lang.
     * Used to populate the available-languages chip list in settings.
     */
    fun availableLanguages(sources: List<Source>): List<String> =
        sources
            .filter { !isLocalSource(it) }
            .map { it.lang.lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
}
// KMK <--
