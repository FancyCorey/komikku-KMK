package exh.recs.evaluation

import eu.kanade.tachiyomi.extension.model.Extension
import tachiyomi.domain.taste.model.SourceEvaluation

// KMK --> v0.7.10
/**
 * Resolves an [Extension.Installed] for a given [SourceEvaluation].
 * Used by the on-demand rec-quality probe to locate an already-installed extension
 * without requiring an exact sig+pkg match (which can fail after signing key rotation).
 *
 * Resolution steps (in order, stops at first match):
 *  1. Exact signatureHash + pkgName
 *  2. pkgName only (sig may differ after a signing key rotation or update)
 *  3. signatureHash + extension name
 *  4. Extension name + lang (only when unambiguous)
 *
 * Returns [ResolveResult.Ambiguous] at any step where more than one extension matches,
 * so the caller knows not to guess.
 */
object SourceRecommendationQualityInstalledResolver {

    sealed interface ResolveResult {
        data class Found(val extension: Extension.Installed) : ResolveResult
        data class Ambiguous(val matchCount: Int, val reason: String) : ResolveResult
        data object NotFound : ResolveResult
    }

    fun resolve(
        evaluation: SourceEvaluation,
        installed: List<Extension.Installed>,
    ): ResolveResult {
        // Step 1: exact sig + pkg
        val exactMatches = installed.filter {
            it.signatureHash == evaluation.signatureHash && it.pkgName == evaluation.extensionPkgName
        }
        if (exactMatches.size == 1) return ResolveResult.Found(exactMatches.first())
        if (exactMatches.size > 1) {
            return ResolveResult.Ambiguous(
                exactMatches.size,
                "Multiple installed extensions with same sig+pkg",
            )
        }

        // Step 2: pkgName only
        val pkgMatches = installed.filter { it.pkgName == evaluation.extensionPkgName }
        if (pkgMatches.size == 1) return ResolveResult.Found(pkgMatches.first())
        if (pkgMatches.size > 1) {
            return ResolveResult.Ambiguous(
                pkgMatches.size,
                "Multiple installed extensions match pkgName ${evaluation.extensionPkgName}",
            )
        }

        // Step 3: sig + name
        val sigNameMatches = installed.filter {
            it.signatureHash == evaluation.signatureHash && it.name == evaluation.extensionName
        }
        if (sigNameMatches.size == 1) return ResolveResult.Found(sigNameMatches.first())
        if (sigNameMatches.size > 1) {
            return ResolveResult.Ambiguous(
                sigNameMatches.size,
                "Multiple installed extensions match sig+name ${evaluation.extensionName}",
            )
        }

        // Step 4: name + lang (unambiguous only)
        val nameLangMatches = installed.filter {
            it.name == evaluation.extensionName && it.lang == evaluation.lang
        }
        if (nameLangMatches.size == 1) return ResolveResult.Found(nameLangMatches.first())
        if (nameLangMatches.size > 1) {
            return ResolveResult.Ambiguous(
                nameLangMatches.size,
                "Multiple installed extensions match name+lang ${evaluation.extensionName}/${evaluation.lang}",
            )
        }

        return ResolveResult.NotFound
    }
}
// KMK <--
