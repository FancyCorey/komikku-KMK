package exh.recs.evaluation

import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.Source
import tachiyomi.domain.taste.model.SourceEvaluation
import java.util.Locale

// KMK --> v0.7.10
/**
 * Resolves a [Source] within an installed extension for a given [SourceEvaluation].
 * Used after an extension is confirmed installed to locate the intended source to probe.
 *
 * Resolution steps (in order, stops at first match):
 *  1. Exact source id
 *  2. Exact source name + evaluation lang
 *  3. Exact source name (only when unambiguous)
 *  4. Normalized source name + lang (only when unambiguous)
 *  5. Normalized source name (only when unambiguous)
 *
 * Returns [ResolveResult.Ambiguous] when multiple sources match at the same step,
 * so the caller knows not to guess.
 */
object SourceRecommendationQualitySourceResolver {

    sealed interface ResolveResult {
        data class Found(val source: Source) : ResolveResult
        data class Ambiguous(val reason: String) : ResolveResult
        data object NotFound : ResolveResult
    }

    fun resolve(
        installedExt: Extension.Installed,
        evaluation: SourceEvaluation,
    ): ResolveResult {
        val sources = installedExt.sources.filterIsInstance<Source>()

        // Step 1: exact source id
        sources.find { it.id == evaluation.sourceId }?.let { return ResolveResult.Found(it) }

        // Step 2: exact name + lang
        val byNameLang = sources.filter { it.name == evaluation.sourceName && it.lang == evaluation.lang }
        if (byNameLang.size == 1) return ResolveResult.Found(byNameLang.first())
        if (byNameLang.size > 1) {
            return ResolveResult.Ambiguous(
                "${byNameLang.size} sources match name+lang ${evaluation.sourceName}/${evaluation.lang}",
            )
        }

        // Step 3: exact name only (unambiguous)
        val byName = sources.filter { it.name == evaluation.sourceName }
        if (byName.size == 1) return ResolveResult.Found(byName.first())
        if (byName.size > 1) {
            return ResolveResult.Ambiguous(
                "${byName.size} sources match name ${evaluation.sourceName}",
            )
        }

        // Step 4: normalized name + lang (unambiguous)
        val normalizedQuery = evaluation.sourceName.lowercase(Locale.ROOT).trim()
        val byNormLang = sources.filter { it.name.lowercase(Locale.ROOT).trim() == normalizedQuery && it.lang == evaluation.lang }
        if (byNormLang.size == 1) return ResolveResult.Found(byNormLang.first())
        if (byNormLang.size > 1) {
            return ResolveResult.Ambiguous(
                "${byNormLang.size} sources match normalized name+lang",
            )
        }

        // Step 5: normalized name only (unambiguous)
        val byNorm = sources.filter { it.name.lowercase(Locale.ROOT).trim() == normalizedQuery }
        if (byNorm.size == 1) return ResolveResult.Found(byNorm.first())
        if (byNorm.size > 1) {
            return ResolveResult.Ambiguous(
                "${byNorm.size} sources match normalized name ${evaluation.sourceName}",
            )
        }

        return ResolveResult.NotFound
    }
}
// KMK <--
