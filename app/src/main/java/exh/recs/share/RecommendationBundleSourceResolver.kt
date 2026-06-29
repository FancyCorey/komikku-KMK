package exh.recs.share

import eu.kanade.tachiyomi.extension.model.Extension

// KMK -->

object RecommendationBundleSourceResolver {

    data class InstalledSourceSnapshot(
        val sourceId: Long,
        val pkgName: String,
        val name: String,
        val lang: String,
        val signatureHash: String,
    )

    sealed interface ResolvedSource {
        /** Exact match by sourceId. */
        data class FoundExact(val sourceId: Long) : ResolvedSource
        /** Fallback match by pkgName/name/lang or signatureHash. */
        data class FoundByMetadata(val sourceId: Long) : ResolvedSource
        /** Source not found in installed extensions. */
        data object Missing : ResolvedSource
    }

    fun resolveSource(
        item: RecommendationBundleItem,
        installedSources: List<InstalledSourceSnapshot>,
    ): ResolvedSource {
        // Step 1: exact sourceId match
        val exact = installedSources.firstOrNull { it.sourceId == item.sourceId }
        if (exact != null) return ResolvedSource.FoundExact(exact.sourceId)

        // Step 2: pkgName + name + lang (handles sourceId changes between forks)
        if (item.extensionPkgName != null && item.sourceName != null && item.sourceLang != null) {
            val byMeta = installedSources.firstOrNull { snap ->
                snap.pkgName == item.extensionPkgName &&
                    snap.name.equals(item.sourceName, ignoreCase = true) &&
                    snap.lang == item.sourceLang
            }
            if (byMeta != null) return ResolvedSource.FoundByMetadata(byMeta.sourceId)
        }

        // Step 3: signatureHash + name + lang (handles pkgName changes)
        if (item.extensionSignatureHash != null && item.sourceName != null && item.sourceLang != null) {
            val bySig = installedSources.firstOrNull { snap ->
                snap.signatureHash == item.extensionSignatureHash &&
                    snap.name.equals(item.sourceName, ignoreCase = true) &&
                    snap.lang == item.sourceLang
            }
            if (bySig != null) return ResolvedSource.FoundByMetadata(bySig.sourceId)
        }

        return ResolvedSource.Missing
    }

    sealed interface AvailableExtensionResolution {
        /** Exactly one available extension matches the item's package metadata. */
        data class Unambiguous(val ext: Extension.Available) : AvailableExtensionResolution
        /** Multiple available extensions could match — the user must choose which to install. */
        data class Ambiguous(val candidates: List<Extension.Available>) : AvailableExtensionResolution
        /** No available extension matches, or the item carries no pkgName. */
        data object NotFound : AvailableExtensionResolution
    }

    fun resolveAvailableExtension(
        item: RecommendationBundleItem,
        availableExtensions: List<Extension.Available>,
    ): AvailableExtensionResolution {
        val pkgName = item.extensionPkgName ?: return AvailableExtensionResolution.NotFound

        // Step 1: pkgName + sigHash (when sigHash is present in item)
        if (item.extensionSignatureHash != null) {
            val byExact = availableExtensions.filter { ext ->
                ext.pkgName == pkgName && ext.signatureHash == item.extensionSignatureHash
            }
            if (byExact.isNotEmpty()) {
                return if (byExact.size == 1) {
                    AvailableExtensionResolution.Unambiguous(byExact.first())
                } else {
                    AvailableExtensionResolution.Ambiguous(byExact)
                }
            }
        }

        // Step 2: pkgName-only fallback
        val byPkg = availableExtensions.filter { ext -> ext.pkgName == pkgName }
        return when {
            byPkg.isEmpty() -> AvailableExtensionResolution.NotFound
            byPkg.size == 1 -> AvailableExtensionResolution.Unambiguous(byPkg.first())
            else -> AvailableExtensionResolution.Ambiguous(byPkg)
        }
    }

    /** True when item and local manga share title + (author OR artist). Title alone is never enough. */
    fun isDuplicateByMetadata(
        item: RecommendationBundleItem,
        localTitle: String,
        localAuthor: String?,
        localArtist: String?,
    ): Boolean {
        if (normalizeTitle(item.title) != normalizeTitle(localTitle)) return false

        val itemAuthor = item.author?.trim()?.lowercase()
        val itemArtist = item.artist?.trim()?.lowercase()
        val localAuth = localAuthor?.trim()?.lowercase()
        val localArt = localArtist?.trim()?.lowercase()

        if (!itemAuthor.isNullOrBlank() && !localAuth.isNullOrBlank() && itemAuthor == localAuth) return true
        if (!itemArtist.isNullOrBlank() && !localArt.isNullOrBlank() && itemArtist == localArt) return true

        return false
    }

    fun normalizeTitle(title: String): String = title
        .lowercase()
        .replace(Regex("""\([^)]*\)|\[[^\]]*\]"""), "")
        .replace(Regex("[^a-z0-9]"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}

// KMK <--
