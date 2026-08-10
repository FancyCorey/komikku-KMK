package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// KMK -->
class SourceEvaluationUpdatePolicyTest {

    private val sig = "abc123"
    private val pkg = "eu.kanade.test"

    private fun evalSnapshot(versionCode: Long?) =
        SourceEvaluationUpdatePolicy.EvaluationVersionSnapshot(
            extensionVersionCode = versionCode,
            signatureHash = sig,
            pkgName = pkg,
        )

    private fun availSnapshot(versionCode: Long) =
        SourceEvaluationUpdatePolicy.AvailableExtensionSnapshot(
            versionCode = versionCode,
            signatureHash = sig,
            pkgName = pkg,
        )

    @Test
    fun `same versionCode is NOT_UPDATED`() {
        val result = SourceEvaluationUpdatePolicy.detectUpdateStatus(
            evaluation = evalSnapshot(10),
            available = availSnapshot(10),
        )
        assertEquals(SourceEvaluationUpdatePolicy.UpdateStatus.NOT_UPDATED, result)
    }

    @Test
    fun `higher available versionCode is UPDATED`() {
        val result = SourceEvaluationUpdatePolicy.detectUpdateStatus(
            evaluation = evalSnapshot(10),
            available = availSnapshot(11),
        )
        assertEquals(SourceEvaluationUpdatePolicy.UpdateStatus.UPDATED, result)
    }

    @Test
    fun `lower available versionCode than stored is NOT_UPDATED`() {
        val result = SourceEvaluationUpdatePolicy.detectUpdateStatus(
            evaluation = evalSnapshot(15),
            available = availSnapshot(12),
        )
        assertEquals(SourceEvaluationUpdatePolicy.UpdateStatus.NOT_UPDATED, result)
    }

    @Test
    fun `null stored versionCode yields UPDATE_UNKNOWN`() {
        val result = SourceEvaluationUpdatePolicy.detectUpdateStatus(
            evaluation = evalSnapshot(null),
            available = availSnapshot(10),
        )
        assertEquals(SourceEvaluationUpdatePolicy.UpdateStatus.UPDATE_UNKNOWN, result)
    }

    @Test
    fun `empty evaluation list yields NEVER_EVALUATED`() {
        val result = SourceEvaluationUpdatePolicy.detectForPool(
            evaluations = emptyList(),
            available = availSnapshot(10),
        )
        assertEquals(SourceEvaluationUpdatePolicy.UpdateStatus.NEVER_EVALUATED, result)
    }

    @Test
    fun `single UPDATED evaluation in pool yields UPDATED`() {
        val result = SourceEvaluationUpdatePolicy.detectForPool(
            evaluations = listOf(evalSnapshot(5)),
            available = availSnapshot(7),
        )
        assertEquals(SourceEvaluationUpdatePolicy.UpdateStatus.UPDATED, result)
    }

    @Test
    fun `single NOT_UPDATED evaluation in pool yields NOT_UPDATED`() {
        val result = SourceEvaluationUpdatePolicy.detectForPool(
            evaluations = listOf(evalSnapshot(7)),
            available = availSnapshot(7),
        )
        assertEquals(SourceEvaluationUpdatePolicy.UpdateStatus.NOT_UPDATED, result)
    }

    @Test
    fun `pool uses best stored versionCode for comparison`() {
        val result = SourceEvaluationUpdatePolicy.detectForPool(
            evaluations = listOf(evalSnapshot(5), evalSnapshot(8)),
            available = availSnapshot(9),
        )
        assertEquals(SourceEvaluationUpdatePolicy.UpdateStatus.UPDATED, result)
    }

    @Test
    fun `pool with all null versionCodes yields UPDATE_UNKNOWN`() {
        val result = SourceEvaluationUpdatePolicy.detectForPool(
            evaluations = listOf(evalSnapshot(null), evalSnapshot(null)),
            available = availSnapshot(10),
        )
        assertEquals(SourceEvaluationUpdatePolicy.UpdateStatus.UPDATE_UNKNOWN, result)
    }
}
// KMK <--
