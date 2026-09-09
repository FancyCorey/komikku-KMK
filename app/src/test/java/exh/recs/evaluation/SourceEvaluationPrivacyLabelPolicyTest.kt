package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.SourceEvaluationUnsafeSource
import tachiyomi.domain.taste.model.UnsafeExtensionPackage

class SourceEvaluationPrivacyLabelPolicyTest {
    private fun unsafe(sourceId: Long? = 42L, evaluationKey: String? = "evaluation-key") =
        SourceEvaluationUnsafeSource(
            unsafeKey = "sig|pkg|42",
            evaluationKey = evaluationKey,
            extensionPkgName = "com.example.secret",
            signatureHash = "secret-signature",
            extensionName = "Private Extension",
            sourceId = sourceId,
            sourceName = "Private Source",
            lang = "en",
            phase = "Probe",
            reason = "crash",
            crashCount = 1,
            firstSeenAt = 1L,
            lastSeenAt = 1L,
            lastBatchId = null,
        )

    private val blocked = UnsafeExtensionPackage(
        pkgName = "com.example.secret",
        extensionName = "Private Extension",
        reason = "crash",
        source = "probe_recovery",
        removable = true,
        createdAt = 1L,
        updatedAt = 1L,
    )

    @Test
    fun `ordinary mode preserves user-facing labels`() {
        assertEquals("Private Extension", SourceEvaluationPrivacyLabelPolicy.unsafeSourceLabel(unsafe(), false))
        assertEquals("Private Extension", SourceEvaluationPrivacyLabelPolicy.blockedPackageLabel(blocked, false))
    }

    @Test
    fun `Evaluation Mode redacts unsafe source and blocked package identities`() {
        val unsafeLabel = SourceEvaluationPrivacyLabelPolicy.unsafeSourceLabel(unsafe(), true)
        val packageLabel = SourceEvaluationPrivacyLabelPolicy.blockedPackageLabel(blocked, true)
        assertFalse(unsafeLabel.contains("Private"))
        assertFalse(unsafeLabel.contains("secret"))
        assertFalse(packageLabel.contains("Private"))
        assertFalse(packageLabel.contains("com.example.secret"))
    }

    @Test
    fun `unsafe source fallback remains generic when source identity is absent`() {
        val label = SourceEvaluationPrivacyLabelPolicy.unsafeSourceLabel(unsafe(null, null), true)
        assertFalse(label.contains("Private"))
        assertFalse(label.contains("com.example.secret"))
    }
}
