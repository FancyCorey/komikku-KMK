package exh.recs.discovery

import eu.kanade.tachiyomi.extension.model.Extension
import exh.recs.sourceprefs.RecommendationSourcePreferenceStore
import tachiyomi.domain.taste.model.SourceEvaluationVerdict
import java.net.URI
import java.util.Locale

enum class SourcesToTryDebugFixtureMode(val prefValue: String) {
    OFF("off"),
    SUGGESTION_VISIBLE("suggestion_visible"),
    EMPTY("empty"),
    ;

    companion object {
        fun fromPrefValue(value: String): SourcesToTryDebugFixtureMode =
            entries.firstOrNull { it.prefValue == value } ?: OFF
    }
}

object SourcesToTryDebugFixture {
    internal const val STORE_NAME = "KMK Local Fixture Store"
    internal const val STORE_BADGE = "Fixture"

    private data class FixtureIdentity(
        val sourceId: Long,
        val sourceName: String,
    )

    private val fixturePackages = mapOf(
        "app.komikku.fixture.sources.alpha" to FixtureIdentity(
            sourceId = 910000000000000001L,
            sourceName = "Fixture Source Alpha",
        ),
        "app.komikku.fixture.sources.beta" to FixtureIdentity(
            sourceId = 910000000000000002L,
            sourceName = "Fixture Source Beta",
        ),
    )

    fun appendSuggestions(
        mode: SourcesToTryDebugFixtureMode,
        isDebugBuild: Boolean,
        expectedSignerSha256: String,
        available: List<Extension.Available>,
        installedPackages: Set<String>,
        untrustedPackages: Set<String>,
        enabledLanguages: Set<String>,
        showNsfw: Boolean,
        blockExplicit: Boolean,
        dismissed: Set<String>,
        dislikedKeys: Set<String>,
        qualityDislikedKeys: Set<String>,
        evaluations: Map<String, SourceEvaluationVerdict>,
        existing: List<NonInstalledSourceSuggestion>,
    ): List<NonInstalledSourceSuggestion> {
        if (!isDebugBuild || mode == SourcesToTryDebugFixtureMode.OFF) return existing
        if (mode == SourcesToTryDebugFixtureMode.EMPTY) return emptyList()

        val expectedSigner = expectedSignerSha256.lowercase(Locale.ROOT)
        if (!expectedSigner.matches(Regex("[0-9a-f]{64}"))) return existing
        val normalizedLanguages = enabledLanguages.map { it.lowercase(Locale.ROOT) }.toSet()
        val existingKeys = existing.mapTo(mutableSetOf()) { it.dismissalKey }

        val additions = available
            .asSequence()
            .sortedBy { it.pkgName }
            .mapNotNull { extension ->
                val identity = fixturePackages[extension.pkgName] ?: return@mapNotNull null
                val source = extension.sources.singleOrNull() ?: return@mapNotNull null
                if (!extension.matchesFixtureContract(identity, source, expectedSigner)) return@mapNotNull null
                if (extension.pkgName in installedPackages || extension.pkgName in untrustedPackages) return@mapNotNull null
                if (!showNsfw && extension.isNsfw) return@mapNotNull null
                if (blockExplicit && exh.source.ExplicitSourceClassifier.isExplicitExtension(extension)) return@mapNotNull null
                if (source.lang.lowercase(Locale.ROOT) !in normalizedLanguages) return@mapNotNull null

                val dismissalKey = buildDismissalKey(extension.signatureHash, extension.pkgName, source.id)
                val preferenceKey = RecommendationSourcePreferenceStore.availableKey(
                    extension.signatureHash,
                    extension.pkgName,
                    source.id,
                )
                val verdict = evaluations[dismissalKey]
                if (dismissalKey in dismissed || preferenceKey in dislikedKeys || preferenceKey in qualityDislikedKeys) {
                    return@mapNotNull null
                }
                if (verdict == SourceEvaluationVerdict.REJECTED) return@mapNotNull null
                if (blockExplicit && verdict == SourceEvaluationVerdict.EXPLICIT_HEAVY) return@mapNotNull null
                if (!existingKeys.add(dismissalKey)) return@mapNotNull null

                NonInstalledSourceSuggestion(
                    extension = extension,
                    source = source,
                    score = 0.55,
                    confidence = SuggestionConfidence.MEDIUM,
                    reasons = listOf(NonInstalledSuggestionReason.NeedsTesting),
                )
            }
            .toList()

        return existing + additions
    }

    private fun Extension.Available.matchesFixtureContract(
        identity: FixtureIdentity,
        source: Extension.Available.Source,
        expectedSigner: String,
    ): Boolean {
        if (signatureHash.lowercase(Locale.ROOT) != expectedSigner) return false
        if (store.signingKey.lowercase(Locale.ROOT) != expectedSigner) return false
        if (store.name != STORE_NAME || storeName != STORE_NAME || store.badgeLabel != STORE_BADGE) return false
        if (store.isLegacy || store.extensionListUrl != null) return false
        if (
            name != identity.sourceName ||
            versionName != "1.6.0" ||
            versionCode != 1L ||
            lang != "en" ||
            isNsfw ||
            libVersion != 1.6
        ) {
            return false
        }
        if (source.id != identity.sourceId || source.name != identity.sourceName || source.lang != "en") return false
        if (source.baseUrl.isNotEmpty()) return false

        val index = fixtureIndex(store.indexUrl) ?: return false
        val baseUrl = "http://127.0.0.1:${index.port}"
        if (store.contact.website != baseUrl || store.contact.discord != null) return false
        if (apkUrl != "$baseUrl/apks/$pkgName.apk") return false
        if (iconUrl != "$baseUrl/icons/$pkgName.png") return false
        return true
    }

    private fun fixtureIndex(value: String): URI? = runCatching { URI(value) }
        .getOrNull()
        ?.takeIf { uri ->
            uri.scheme == "http" &&
                uri.host == "127.0.0.1" &&
                uri.port in 1..65535 &&
                uri.path == "/index.json" &&
                uri.userInfo == null &&
                uri.query == null &&
                uri.fragment == null
        }
}
