package exh.recs.discovery

import eu.kanade.tachiyomi.extension.model.Extension

// KMK: debug-only Sources To Try fixture. It selects an already-catalogued, not-installed
// extension so the real install, receipt, dismiss, quality, and rollback paths remain under test.
// It never invents a package or APK and is unreachable when isDebugBuild is false.
enum class SourcesToTryDebugFixtureMode(val prefValue: String) {
    OFF("off"),
    SUGGESTION_VISIBLE("suggestion_visible"),
    ;

    companion object {
        fun fromPrefValue(value: String): SourcesToTryDebugFixtureMode =
            entries.firstOrNull { it.prefValue == value } ?: OFF
    }
}

object SourcesToTryDebugFixture {
    fun appendSuggestion(
        mode: SourcesToTryDebugFixtureMode,
        isDebugBuild: Boolean,
        available: List<Extension.Available>,
        installed: Set<String>,
        untrusted: Set<String>,
        enabledLanguages: Set<String>,
        showNsfw: Boolean,
        blockExplicit: Boolean,
        existing: List<NonInstalledSourceSuggestion>,
    ): List<NonInstalledSourceSuggestion> {
        if (!isDebugBuild || mode == SourcesToTryDebugFixtureMode.OFF) return existing

        val extension = available
            .asSequence()
            .sortedBy { it.pkgName }
            .filter { it.signatureHash + "|" + it.pkgName !in installed }
            .filter { it.signatureHash + "|" + it.pkgName !in untrusted }
            .filter { showNsfw || !it.isNsfw }
            .filter { !blockExplicit || !exh.source.ExplicitSourceClassifier.isExplicitExtension(it) }
            .filter { it.lang.lowercase() in enabledLanguages }
            .firstOrNull() ?: return existing

        val source = extension.sources.minByOrNull { it.id }
        val key = buildDismissalKey(extension.signatureHash, extension.pkgName, source?.id)
        if (existing.any { it.dismissalKey == key }) return existing

        return existing + NonInstalledSourceSuggestion(
            extension = extension,
            source = source,
            score = 0.55,
            confidence = SuggestionConfidence.MEDIUM,
            reasons = listOf(NonInstalledSuggestionReason.NeedsTesting),
        )
    }
}
