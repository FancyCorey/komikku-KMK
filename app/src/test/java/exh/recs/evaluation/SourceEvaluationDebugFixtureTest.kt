package exh.recs.evaluation

import eu.kanade.tachiyomi.extension.model.Extension
import mihon.domain.extension.model.ExtensionStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SourceEvaluationDebugFixtureTest {
    @Test
    fun `release builds always resolve fixture preference to off`() {
        assertEquals(
            SourceEvaluationDebugFixtureMode.OFF,
            SourceEvaluationDebugFixture.resolveMode(
                isDebugBuild = false,
                preferenceValue = SourceEvaluationDebugFixtureMode.PER_SOURCE_ERROR.prefValue,
            ),
        )
    }

    @Test
    fun `off mode preserves the exact real candidate pool`() {
        val realPool = pool(candidate("private.real.source"))

        val result = SourceEvaluationDebugFixture.candidatePool(SourceEvaluationDebugFixtureMode.OFF, realPool)

        assertSame(realPool, result)
        assertTrue(SourceEvaluationDebugFixture.requiresNetwork(SourceEvaluationDebugFixtureMode.OFF))
        assertTrue(SourceEvaluationDebugFixture.shouldPersistRunBaseline(SourceEvaluationDebugFixtureMode.OFF))
    }

    @Test
    fun `active mode replaces real identities with one synthetic candidate`() {
        val result = SourceEvaluationDebugFixture.candidatePool(
            SourceEvaluationDebugFixtureMode.PER_SOURCE_ERROR,
            pool(candidate("private.real.source")),
        )

        assertEquals(1, result.allEligible.size)
        val extension = result.allEligible.single().extension
        assertEquals("Fixture Source", extension.name)
        assertEquals("fixture.source.evaluation", extension.pkgName)
        assertFalse(extension.pkgName.contains("private.real.source"))
        assertTrue(result.evaluationsByExtensionKey.isEmpty())
        assertTrue(result.explicitExtensionKeys.isEmpty())
    }

    @Test
    fun `active fixture requires neither network nor run-baseline persistence`() {
        SourceEvaluationDebugFixtureMode.entries
            .filterNot { it == SourceEvaluationDebugFixtureMode.OFF }
            .forEach { mode ->
                assertFalse(SourceEvaluationDebugFixture.requiresNetwork(mode), mode.name)
                assertFalse(SourceEvaluationDebugFixture.shouldPersistRunBaseline(mode), mode.name)
            }
    }

    private fun pool(candidate: EvaluationCandidate) = SourceEvaluationCandidateFilter.CandidatePoolResult(
        allEligible = listOf(candidate),
        evaluationsByExtensionKey = emptyMap(),
        explicitExtensionKeys = emptySet(),
        dislikedHiddenCount = 0,
        blockExplicit = false,
    )

    private fun candidate(pkgName: String) = EvaluationCandidate(
        extension = Extension.Available(
            name = "Private Real Source",
            pkgName = pkgName,
            versionName = "1",
            versionCode = 1,
            libVersion = 1.5,
            lang = "en",
            isNsfw = false,
            signatureHash = "private-signature",
            storeName = "Private Store",
            sources = emptyList(),
            apkUrl = "https://private.invalid/source.apk",
            iconUrl = "",
            store = ExtensionStore(
                indexUrl = "https://private.invalid/index.json",
                name = "Private Store",
                badgeLabel = "Private",
                signingKey = "private-signature",
                contact = ExtensionStore.Contact(website = "", discord = null),
                isLegacy = false,
                extensionListUrl = null,
            ),
        ),
        priorityRank = 0,
    )
}
