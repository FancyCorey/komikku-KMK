package exh.recs.bestversion.fixture

import mihon.domain.migration.models.MigrationFlag
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BestVersionPairedFixtureContractTest {
    private val signer = "a".repeat(64)

    private fun activation(
        debug: Boolean = true,
        mode: BestVersionPairedFixtureMode = BestVersionPairedFixtureMode.PAIRED_RECORDS,
        evaluationMode: Boolean = true,
        profile: String = BestVersionPairedFixtureGate.ISOLATED_PROFILE,
        signerValue: String = signer,
        installed: Set<BestVersionPairedFixtureSourceIdentity> = setOf(
            BestVersionPairedFixtureSourceIdentity(
                BestVersionPairedFixtureGate.ALPHA_PACKAGE,
                BestVersionPairedFixtureGate.ALPHA_SOURCE_ID,
                signer,
                "Fixture Source Alpha",
                "Fixture Source",
                "1.6.0",
                1L,
            ),
            BestVersionPairedFixtureSourceIdentity(
                BestVersionPairedFixtureGate.BETA_PACKAGE,
                BestVersionPairedFixtureGate.BETA_SOURCE_ID,
                signer,
                "Fixture Source Beta",
                "Fixture Source",
                "1.6.0",
                1L,
            ),
        ),
    ) = BestVersionPairedFixtureActivation(debug, mode, evaluationMode, profile, signerValue, installed)

    @Test
    fun `exact isolated activation is allowed`() {
        assertTrue(BestVersionPairedFixtureGate.isAllowed(activation()))
        assertTrue(BestVersionPairedFixtureGate.isAllowed(activation(signerValue = signer.uppercase())))
    }

    @Test
    fun `every activation gate fails closed`() {
        assertFalse(BestVersionPairedFixtureGate.isAllowed(activation(debug = false)))
        assertFalse(BestVersionPairedFixtureGate.isAllowed(activation(mode = BestVersionPairedFixtureMode.OFF)))
        assertFalse(BestVersionPairedFixtureGate.isAllowed(activation(evaluationMode = false)))
        assertFalse(BestVersionPairedFixtureGate.isAllowed(activation(profile = "physical-tablet")))
        assertFalse(BestVersionPairedFixtureGate.isAllowed(activation(signerValue = "bad")))
        assertFalse(BestVersionPairedFixtureGate.isAllowed(activation(installed = emptySet())))
        assertFalse(
            BestVersionPairedFixtureGate.isAllowed(
                activation(
                    installed = activation().installedSources +
                        BestVersionPairedFixtureSourceIdentity("extra", 99L, signer, "Extra", "Extra", "1", 1L),
                ),
            ),
        )
    }

    @Test
    fun `fixture flags cannot include external effects`() {
        val spec = BestVersionPairedFixtureSpec()
        assertTrue(spec.allowedMigrationFlags == setOf(MigrationFlag.CHAPTER, MigrationFlag.CATEGORY))
        assertFalse(MigrationFlag.TRACK in spec.allowedMigrationFlags)
        assertFalse(MigrationFlag.REMOVE_DOWNLOAD in spec.allowedMigrationFlags)
        assertFalse(MigrationFlag.CUSTOM_COVER in spec.allowedMigrationFlags)
    }

    @Test
    fun `observed state hash is stable across row ordering`() {
        val first = BestVersionPairedFixtureObservedState(mangaRows = listOf("b", "a"), chapterRows = listOf("2", "1"))
        val second = BestVersionPairedFixtureObservedState(mangaRows = listOf("a", "b"), chapterRows = listOf("1", "2"))
        assertTrue(first.sha256() == second.sha256())
        assertFalse(first.isAbsent)
        assertTrue(BestVersionPairedFixtureObservedState().isAbsent)
    }
}
