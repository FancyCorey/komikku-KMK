package exh.recs

import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.Source
import java.util.Locale

// KMK v0.8.12 -->
/**
 * Pure helper computing the full set of language chips the For You language selector should show.
 *
 * Root cause this exists for: [RecommendationSourceFilter.availableLanguages] only ever looked at
 * currently *installed and visible* sources. A user whose installed sources are mostly/only English
 * would see the language selector collapse to just "EN", even if they had previously selected a
 * non-English language, and even though non-English extensions are available to install. This
 * merges three inputs instead of one, so the chip list never loses a language the user actually
 * cares about.
 */
object RecommendationLanguageAvailabilityPolicy {

    /** Local Source has id 0 by convention in Komikku/Mihon -- never a real chip. */
    private fun isLocalSource(source: Source): Boolean = RecommendationSourceFilter.isLocalSource(source)

    /**
     * @param selectedLanguages the user's currently-selected recommendation languages -- always
     * included, even if nothing installed/available currently exposes that language.
     * @param installedVisibleSources from `sourceManager.getVisibleSources()`.
     * @param availableExtensions from `extensionManager.availableExtensionsFlow.value` -- both the
     * extension's own [Extension.Available.lang] and each of its [Extension.Available.sources]'
     * languages are included, so a not-yet-installed non-English extension still contributes its
     * language(s) to the chip list.
     * @return deterministic alphabetically-sorted language codes, falling back to
     * [RecommendationSourceFilter.DefaultLanguages] only when every input is empty/blank.
     */
    fun availableLanguages(
        selectedLanguages: Set<String>,
        installedVisibleSources: List<Source>,
        availableExtensions: List<Extension.Available>,
    ): List<String> {
        val languages = sortedSetOf<String>()

        selectedLanguages.forEach { lang ->
            val normalized = lang.trim().lowercase(Locale.ROOT)
            if (normalized.isNotEmpty()) languages += normalized
        }

        installedVisibleSources
            .filterNot(::isLocalSource)
            .forEach { source ->
                val normalized = source.lang.trim().lowercase(Locale.ROOT)
                if (normalized.isNotEmpty()) languages += normalized
            }

        availableExtensions.forEach { ext ->
            val extLang = ext.lang.trim().lowercase(Locale.ROOT)
            if (extLang.isNotEmpty()) languages += extLang
            ext.sources.forEach { source ->
                val normalized = source.lang.trim().lowercase(Locale.ROOT)
                if (normalized.isNotEmpty()) languages += normalized
            }
        }

        return languages.toList().ifEmpty { RecommendationSourceFilter.DefaultLanguages.toList() }
    }
}
// KMK <--
