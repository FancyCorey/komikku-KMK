package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.SourceEvaluationProbeMarker

// KMK -->
// Regression coverage for the Source Evaluation clipboard/diagnostics privacy fix
// (KMK Security and Degraded-Environment Hardening 2026-07-30, confirmed clipboard leak).
// Every assertion below verifies that no raw source name, extension name, package name,
// signature hash, or raw exception text is ever emitted when Evaluation Mode is enabled,
// and that raw error text is never emitted in either Evaluation Mode state.
class SourceEvaluationDiagnosticsPolicyTest {

    private fun marker(
        extensionName: String = "Real Extension Name",
        extensionPkgName: String = "com.example.realext",
        signatureHash: String = "deadbeef",
        sourceId: Long? = 42L,
        sourceName: String? = "Real Source Name",
        phase: String = "Probing",
        updatedAt: Long = 1000L,
    ) = SourceEvaluationProbeMarker(
        evaluationKey = "key",
        extensionPkgName = extensionPkgName,
        signatureHash = signatureHash,
        extensionName = extensionName,
        sourceId = sourceId,
        sourceName = sourceName,
        lang = "en",
        phase = phase,
        startedAt = 500L,
        updatedAt = updatedAt,
        batchId = null,
    )

    @Test
    fun `evaluation mode off preserves real extension and source names`() {
        val result = SourceEvaluationDiagnosticsPolicy.sanitize(
            evaluationModeEnabled = false,
            currentExtensionName = "Real Extension Name",
            currentSourceName = "Real Source Name",
            lastError = null,
            lastProbeMarker = null,
        )
        assertEquals("Real Extension Name", result.currentExtensionLabel)
        assertEquals("Real Source Name", result.currentSourceLabel)
    }

    @Test
    fun `evaluation mode on never returns the raw extension or source name`() {
        val result = SourceEvaluationDiagnosticsPolicy.sanitize(
            evaluationModeEnabled = true,
            currentExtensionName = "Real Extension Name",
            currentSourceName = "Real Source Name",
            lastError = null,
            lastProbeMarker = null,
        )
        assertTrue(result.currentExtensionLabel != "Real Extension Name")
        assertTrue(result.currentSourceLabel != "Real Source Name")
        assertTrue(result.currentExtensionLabel!!.startsWith("Source "))
        assertTrue(result.currentSourceLabel!!.startsWith("Source "))
    }

    @Test
    fun `null extension and source fields stay null in both modes`() {
        val off = SourceEvaluationDiagnosticsPolicy.sanitize(false, null, null, null, null)
        val on = SourceEvaluationDiagnosticsPolicy.sanitize(true, null, null, null, null)
        assertNull(off.currentExtensionLabel)
        assertNull(off.currentSourceLabel)
        assertNull(on.currentExtensionLabel)
        assertNull(on.currentSourceLabel)
    }

    @Test
    fun `raw error text is replaced with a finite category in both evaluation mode states`() {
        val rawError = "java.net.SocketTimeoutException: connect timed out to https://secret-host.example/api?token=abc123"
        val off = SourceEvaluationDiagnosticsPolicy.sanitize(false, null, null, rawError, null)
        val on = SourceEvaluationDiagnosticsPolicy.sanitize(true, null, null, rawError, null)
        assertTrue(off.errorCategory != null && !off.errorCategory.contains(rawError))
        assertTrue(on.errorCategory != null && !on.errorCategory.contains(rawError))
        assertTrue(off.errorCategory?.contains("secret-host") != true)
        assertTrue(off.errorCategory?.contains("token=abc123") != true)
    }

    @Test
    fun `null error yields null category`() {
        val result = SourceEvaluationDiagnosticsPolicy.sanitize(false, null, null, null, null)
        assertNull(result.errorCategory)
    }

    @Test
    fun `probe marker package name and signature hash never appear in either mode`() {
        val m = marker()
        val off = SourceEvaluationDiagnosticsPolicy.sanitize(false, null, null, null, m)
        val on = SourceEvaluationDiagnosticsPolicy.sanitize(true, null, null, null, m)
        for (result in listOf(off, on)) {
            val marker = result.probeMarker!!
            assertTrue(!marker.extensionLabel.contains(m.extensionPkgName))
            assertTrue(!marker.extensionLabel.contains(m.signatureHash))
            val sourceLabel = marker.sourceLabel
            assertTrue(sourceLabel == null || !sourceLabel.contains(m.signatureHash))
        }
    }

    @Test
    fun `probe marker names are generic labels when evaluation mode is on`() {
        val m = marker()
        val result = SourceEvaluationDiagnosticsPolicy.sanitize(true, null, null, null, m)
        val marker = result.probeMarker!!
        assertTrue(marker.extensionLabel != m.extensionName)
        assertTrue(marker.sourceLabel != m.sourceName)
    }

    @Test
    fun `probe marker names are the real display names when evaluation mode is off`() {
        val m = marker()
        val result = SourceEvaluationDiagnosticsPolicy.sanitize(false, null, null, null, m)
        val marker = result.probeMarker!!
        assertEquals(m.extensionName, marker.extensionLabel)
        assertEquals(m.sourceName, marker.sourceLabel)
    }

    @Test
    fun `probe marker phase and updatedAt pass through unchanged in both modes`() {
        val m = marker(phase = "Installing", updatedAt = 9999L)
        val off = SourceEvaluationDiagnosticsPolicy.sanitize(false, null, null, null, m)
        val on = SourceEvaluationDiagnosticsPolicy.sanitize(true, null, null, null, m)
        val offMarker = off.probeMarker!!
        val onMarker = on.probeMarker!!
        assertEquals("Installing", offMarker.phase)
        assertEquals("Installing", onMarker.phase)
        assertEquals(9999L, offMarker.updatedAt)
        assertEquals(9999L, onMarker.updatedAt)
    }

    @Test
    fun `probe marker with null source name and id yields a null source label in both modes`() {
        val m = marker(sourceId = null, sourceName = null)
        val off = SourceEvaluationDiagnosticsPolicy.sanitize(false, null, null, null, m)
        val on = SourceEvaluationDiagnosticsPolicy.sanitize(true, null, null, null, m)
        assertNull(off.probeMarker!!.sourceLabel)
        assertNull(on.probeMarker!!.sourceLabel)
    }

    @Test
    fun `null probe marker yields null sanitized probe marker`() {
        val result = SourceEvaluationDiagnosticsPolicy.sanitize(true, null, null, null, null)
        assertNull(result.probeMarker)
    }

    @Test
    fun `same source name produces a stable label across calls within evaluation mode`() {
        val first = SourceEvaluationDiagnosticsPolicy.sanitize(true, null, "Stable Source", null, null)
        val second = SourceEvaluationDiagnosticsPolicy.sanitize(true, null, "Stable Source", null, null)
        assertEquals(first.currentSourceLabel, second.currentSourceLabel)
    }
}
// KMK <--
