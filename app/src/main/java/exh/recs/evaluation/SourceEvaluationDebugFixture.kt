package exh.recs.evaluation

import eu.kanade.tachiyomi.extension.model.Extension
import mihon.domain.extension.model.ExtensionStore

/**
 * Application-visible input for the debug-only Source Evaluation fixture.
 *
 * The fixture must not depend on an installed extension, a configured repository, network
 * availability, or any user source identity. Release builds always resolve to [SourceEvaluationDebugFixtureMode.OFF].
 */
internal object SourceEvaluationDebugFixture {
    fun resolveMode(isDebugBuild: Boolean, preferenceValue: String): SourceEvaluationDebugFixtureMode =
        if (isDebugBuild) {
            SourceEvaluationDebugFixtureMode.fromPrefValue(preferenceValue)
        } else {
            SourceEvaluationDebugFixtureMode.OFF
        }

    fun candidatePool(
        mode: SourceEvaluationDebugFixtureMode,
        realPool: SourceEvaluationCandidateFilter.CandidatePoolResult,
    ): SourceEvaluationCandidateFilter.CandidatePoolResult =
        if (mode == SourceEvaluationDebugFixtureMode.OFF) {
            realPool
        } else {
            SourceEvaluationCandidateFilter.CandidatePoolResult(
                allEligible = listOf(EvaluationCandidate(extension = syntheticExtension(), priorityRank = 0)),
                evaluationsByExtensionKey = emptyMap(),
                explicitExtensionKeys = emptySet(),
                dislikedHiddenCount = 0,
                blockExplicit = false,
            )
        }

    fun requiresNetwork(mode: SourceEvaluationDebugFixtureMode): Boolean =
        mode == SourceEvaluationDebugFixtureMode.OFF

    fun shouldPersistRunBaseline(mode: SourceEvaluationDebugFixtureMode): Boolean =
        mode == SourceEvaluationDebugFixtureMode.OFF

    private fun syntheticExtension() = Extension.Available(
        name = "Fixture Source",
        pkgName = "fixture.source.evaluation",
        versionName = "1",
        versionCode = 1,
        libVersion = 1.5,
        lang = "en",
        isNsfw = false,
        signatureHash = "fixture-evaluation-signature",
        storeName = "Fixture Store",
        sources = emptyList(),
        apkUrl = "https://fixture.invalid/source-evaluation.apk",
        iconUrl = "",
        store = ExtensionStore(
            indexUrl = "https://fixture.invalid/index.json",
            name = "Fixture Store",
            badgeLabel = "Fixture",
            signingKey = "fixture-evaluation-signature",
            contact = ExtensionStore.Contact(website = "", discord = null),
            isLegacy = false,
            extensionListUrl = null,
        ),
    )
}
