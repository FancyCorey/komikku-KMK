package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK v0.8.15-fix1 -->
class SourceEvaluationRowActionPolicyTest {

    @Test
    fun `not installed, not blocked, available is eligible for Install`() {
        val result = SourceEvaluationRowActionPolicy.installEligibility(
            isInstalled = false,
            isBlocked = false,
            isAvailable = true,
        )
        assertEquals(SourceEvaluationRowActionPolicy.InstallEligibility.ELIGIBLE, result)
        assertTrue(SourceEvaluationRowActionPolicy.canOfferInstall(result))
    }

    @Test
    fun `an installed source does not expose a misleading Install action`() {
        val result = SourceEvaluationRowActionPolicy.installEligibility(
            isInstalled = true,
            isBlocked = false,
            isAvailable = true,
        )
        assertEquals(SourceEvaluationRowActionPolicy.InstallEligibility.ALREADY_INSTALLED, result)
        assertFalse(SourceEvaluationRowActionPolicy.canOfferInstall(result))
    }

    @Test
    fun `a blocked or quarantined source does not expose a misleading Install action`() {
        val result = SourceEvaluationRowActionPolicy.installEligibility(
            isInstalled = false,
            isBlocked = true,
            isAvailable = true,
        )
        assertEquals(SourceEvaluationRowActionPolicy.InstallEligibility.BLOCKED, result)
        assertFalse(SourceEvaluationRowActionPolicy.canOfferInstall(result))
    }

    @Test
    fun `a no-longer-available source does not expose a misleading Install action`() {
        val result = SourceEvaluationRowActionPolicy.installEligibility(
            isInstalled = false,
            isBlocked = false,
            isAvailable = false,
        )
        assertEquals(SourceEvaluationRowActionPolicy.InstallEligibility.UNAVAILABLE, result)
        assertFalse(SourceEvaluationRowActionPolicy.canOfferInstall(result))
    }

    @Test
    fun `installed takes priority over blocked when both are somehow true`() {
        val result = SourceEvaluationRowActionPolicy.installEligibility(
            isInstalled = true,
            isBlocked = true,
            isAvailable = true,
        )
        assertEquals(SourceEvaluationRowActionPolicy.InstallEligibility.ALREADY_INSTALLED, result)
    }

    @Test
    fun `a source with no error has no Errors action`() {
        assertFalse(
            SourceEvaluationRowActionPolicy.hasErrorInfo(
                isCatalogueError = false,
                hasSearchFailureKind = false,
                hasSearchReasonHint = false,
            ),
        )
    }

    @Test
    fun `a source with a catalogue evaluation error has an Errors action`() {
        assertTrue(
            SourceEvaluationRowActionPolicy.hasErrorInfo(
                isCatalogueError = true,
                hasSearchFailureKind = false,
                hasSearchReasonHint = false,
            ),
        )
    }

    @Test
    fun `a source with only a search-compatibility failure kind has an Errors action`() {
        assertTrue(
            SourceEvaluationRowActionPolicy.hasErrorInfo(
                isCatalogueError = false,
                hasSearchFailureKind = true,
                hasSearchReasonHint = false,
            ),
        )
    }

    @Test
    fun `a source with only a search-compatibility reason hint has an Errors action`() {
        assertTrue(
            SourceEvaluationRowActionPolicy.hasErrorInfo(
                isCatalogueError = false,
                hasSearchFailureKind = false,
                hasSearchReasonHint = true,
            ),
        )
    }
}
// KMK <--
