package exh.recs.bridge.fixture

import exh.recs.bestversion.fixture.BestVersionPairedFixtureGate
import exh.recs.bestversion.fixture.BestVersionPairedFixtureSourceIdentity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class AlternateSourceReaderFixtureContractTest {
    @Test
    fun `scenario parser is closed and defaults unknown values to off`() {
        AlternateSourceReaderFixtureScenario.entries.forEach { scenario ->
            assertEquals(scenario, AlternateSourceReaderFixtureScenario.fromPrefValue(scenario.prefValue))
        }
        assertEquals(AlternateSourceReaderFixtureScenario.OFF, AlternateSourceReaderFixtureScenario.fromPrefValue("future"))
        assertEquals(AlternateSourceReaderFixtureScenario.OFF, AlternateSourceReaderFixtureScenario.fromPrefValue(""))
    }

    @Test
    fun `gate accepts only the exact bridge and paired fixture activation`() {
        assertTrue(AlternateSourceReaderFixtureGate.isAllowed(activation()))
        assertFalse(AlternateSourceReaderFixtureGate.isAllowed(activation().copy(isDebugBuild = false)))
        assertFalse(AlternateSourceReaderFixtureGate.isAllowed(activation().copy(evaluationModeEnabled = false)))
        assertFalse(AlternateSourceReaderFixtureGate.isAllowed(activation().copy(scenario = AlternateSourceReaderFixtureScenario.OFF)))
        assertFalse(AlternateSourceReaderFixtureGate.isAllowed(activation().copy(fixtureProfile = "future")))
        assertFalse(AlternateSourceReaderFixtureGate.isAllowed(activation().copy(pairedFixtureProfile = "future")))
        assertFalse(AlternateSourceReaderFixtureGate.isAllowed(activation().copy(expectedSignerSha256 = "invalid")))
        assertFalse(AlternateSourceReaderFixtureGate.isAllowed(activation().copy(installedSources = emptySet())))
    }

    @Test
    fun `gate rejects extra duplicate and identity-mismatched sources`() {
        val valid = activation()
        val alpha = valid.installedSources.first { it.packageName == BestVersionPairedFixtureGate.ALPHA_PACKAGE }
        assertFalse(
            AlternateSourceReaderFixtureGate.isAllowed(
                valid.copy(installedSources = valid.installedSources + alpha.copy(versionCode = 2L)),
            ),
        )
        assertFalse(
            AlternateSourceReaderFixtureGate.isAllowed(
                valid.copy(installedSources = valid.installedSources.map { it.copy(sourceName = "Changed") }.toSet()),
            ),
        )
    }

    @Test
    fun `manifest accepts a complete exact sequential record`() {
        val now = 1_000_000L
        assertTrue(completeManifest(now).isStructurallyValid(now))
    }

    @Test
    fun `manifest rejects unsupported identity scenario hashes and times`() {
        val now = 100_000_000L
        val valid = completeManifest(now)
        assertFalse(valid.copy(schemaVersion = 2).isStructurallyValid(now))
        assertFalse(valid.copy(revision = "future").isStructurallyValid(now))
        assertFalse(valid.copy(operationId = "invalid").isStructurallyValid(now))
        assertFalse(valid.copy(pairedFixtureOperationId = valid.operationId).isStructurallyValid(now))
        assertFalse(valid.copy(scenario = AlternateSourceReaderFixtureScenario.OFF).isStructurallyValid(now))
        assertFalse(valid.copy(primarySourceId = 7L).isStructurallyValid(now))
        assertFalse(valid.copy(primaryMangaId = valid.alternateMangaId).isStructurallyValid(now))
        assertFalse(valid.copy(primaryMangaUrl = "/other").isStructurallyValid(now))
        assertFalse(valid.copy(bridgeBaselineHash = "a".repeat(64)).isStructurallyValid(now))
        assertFalse(valid.copy(createdAt = now + AlternateSourceReaderFixtureManifest.MAX_FUTURE_SKEW_MS + 1L).isStructurallyValid(now))
        assertFalse(valid.copy(createdAt = now - AlternateSourceReaderFixtureManifest.MAX_AGE_MS - 1L).isStructurallyValid(now))
    }

    @Test
    fun `manifest enforces sequential completed and pending steps`() {
        val now = 1_000_000L
        val valid = completeManifest(now)
        assertFalse(
            valid.copy(
                completedSteps = setOf(
                    AlternateSourceReaderFixtureStep.PAIRED_GRAPH,
                    AlternateSourceReaderFixtureStep.PRIMARY_GAP,
                ),
                pendingStep = null,
            ).isStructurallyValid(now),
        )
        assertFalse(
            valid.copy(
                completedSteps = setOf(AlternateSourceReaderFixtureStep.PAIRED_GRAPH),
                pendingStep = AlternateSourceReaderFixtureStep.BRIDGE,
            ).isStructurallyValid(now),
        )
        assertFalse(
            valid.copy(
                completedSteps = setOf(AlternateSourceReaderFixtureStep.PAIRED_GRAPH),
                pendingStep = AlternateSourceReaderFixtureStep.PAIRED_GRAPH,
            ).isStructurallyValid(now),
        )
    }

    @Test
    fun `manifest requires a valid chapter snapshot once the gap step is complete`() {
        val now = 1_000_000L
        val valid = completeManifest(now)
        assertFalse(valid.copy(primaryGapChapter = null).isStructurallyValid(now))
        assertFalse(
            valid.copy(primaryGapChapter = valid.primaryGapChapter?.copy(url = "/wrong"))
                .isStructurallyValid(now),
        )
        assertFalse(
            valid.copy(primaryGapChapter = valid.primaryGapChapter?.copy(chapterNumber = Float.NaN))
                .isStructurallyValid(now),
        )
    }

    @Test
    fun `manifest bounds and validates action history baseline ids`() {
        val now = 1_000_000L
        val valid = completeManifest(now)
        assertFalse(valid.copy(actionHistoryBaselineIds = setOf("invalid")).isStructurallyValid(now))
        assertFalse(
            valid.copy(
                actionHistoryBaselineIds = (0..AlternateSourceReaderFixtureManifest.MAX_ACTION_HISTORY_BASELINE)
                    .map { UUID.randomUUID().toString() }
                    .toSet(),
            ).isStructurallyValid(now),
        )
    }

    private fun activation(): AlternateSourceReaderFixtureActivation {
        val signer = "a".repeat(64)
        return AlternateSourceReaderFixtureActivation(
            isDebugBuild = true,
            scenario = AlternateSourceReaderFixtureScenario.EXACT_GAP,
            evaluationModeEnabled = true,
            fixtureProfile = AlternateSourceReaderFixtureGate.ISOLATED_PROFILE,
            pairedFixtureProfile = BestVersionPairedFixtureGate.ISOLATED_PROFILE,
            expectedSignerSha256 = signer,
            installedSources = setOf(
                sourceIdentity(
                    BestVersionPairedFixtureGate.ALPHA_PACKAGE,
                    BestVersionPairedFixtureGate.ALPHA_SOURCE_ID,
                    "Fixture Source Alpha",
                    signer,
                ),
                sourceIdentity(
                    BestVersionPairedFixtureGate.BETA_PACKAGE,
                    BestVersionPairedFixtureGate.BETA_SOURCE_ID,
                    "Fixture Source Beta",
                    signer,
                ),
            ),
        )
    }

    private fun sourceIdentity(packageName: String, sourceId: Long, extensionName: String, signer: String) =
        BestVersionPairedFixtureSourceIdentity(
            packageName = packageName,
            sourceId = sourceId,
            signerSha256 = signer,
            extensionName = extensionName,
            sourceName = "Fixture Source",
            versionName = "1.6.0",
            versionCode = 1L,
        )

    private fun completeManifest(now: Long) = AlternateSourceReaderFixtureManifest(
        operationId = UUID.randomUUID().toString(),
        pairedFixtureOperationId = UUID.randomUUID().toString(),
        scenario = AlternateSourceReaderFixtureScenario.EXACT_GAP,
        createdAt = now,
        completedSteps = AlternateSourceReaderFixtureStep.entries.toSet(),
        primaryMangaId = 11L,
        alternateMangaId = 12L,
        primaryGapChapter = AlternateSourceReaderFixtureChapterSnapshot(
            id = 21L,
            mangaId = 11L,
            url = "/kmk-fixture/f2/origin/chapter-2",
            name = "Chapter 2",
            chapterNumber = 2F,
            read = false,
            bookmark = false,
            lastPageRead = 0L,
            dateFetch = now,
            dateUpload = now,
            sourceOrder = 1L,
        ),
        bridgeBaselineHash = "A".repeat(64),
        identityBaselineHash = "B".repeat(64),
        actionHistoryBaselineIds = setOf(UUID.randomUUID().toString()),
        seededOverlayHash = "C".repeat(64),
    )
}
