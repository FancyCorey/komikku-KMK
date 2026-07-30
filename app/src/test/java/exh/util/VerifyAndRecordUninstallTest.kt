package exh.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

// KMK Confirmed Blocker Remediation Phase 5 2026-07-29 -->
/**
 * Tests for [verifyAndRecordUninstall] -- the fix for the confirmed gap that
 * [exh.recs.evaluation.SourceEvaluationScreenModel.uninstallRuntimeHealthExtension] previously
 * called `extensionManager.uninstallExtension()` (fire-and-forget, no completion signal at all) and
 * recorded nothing, leaving extension uninstall entirely unrepresented in Action History. This
 * verifies removal via an observable package-name flow before ever recording a non-undoable event,
 * and never records anything on the mere fact that uninstall was requested.
 */
class VerifyAndRecordUninstallTest {

    @BeforeEach
    fun setUp() {
        NonUndoableEventJournal.clear()
    }

    @AfterEach
    fun tearDown() {
        NonUndoableEventJournal.clear()
    }

    @Test
    fun `package already absent when observed records the event immediately when Evaluation Mode is enabled`() = runTest {
        val installed = MutableStateFlow(listOf("com.other.package"))

        val removed = verifyAndRecordUninstall(
            installedPackageNames = installed,
            pkgName = "com.test.gone",
            isEvaluationModeEnabled = { true },
        )

        assertTrue(removed)
        assertEquals(1, NonUndoableEventJournal.snapshot().size)
        assertEquals(NonUndoableEventType.EXTENSION_UNINSTALLED, NonUndoableEventJournal.snapshot().first().eventType)
    }

    @Test
    fun `a still-installed package that never gets removed times out and records nothing`() = runTest {
        val installed = MutableStateFlow(listOf("com.test.stuck"))

        val removed = verifyAndRecordUninstall(
            installedPackageNames = installed,
            pkgName = "com.test.stuck",
            isEvaluationModeEnabled = { true },
            timeoutMillis = 50L,
        )

        assertFalse(removed, "a package that never disappears from the installed list must not be reported as removed")
        assertTrue(NonUndoableEventJournal.isEmpty(), "an unverified uninstall must never record an event")
    }

    @Test
    fun `Evaluation Mode disabled records nothing even when removal is verified`() = runTest {
        val installed = MutableStateFlow(emptyList<String>())

        val removed = verifyAndRecordUninstall(
            installedPackageNames = installed,
            pkgName = "com.test.gone",
            isEvaluationModeEnabled = { false },
        )

        assertTrue(removed, "the return value must still truthfully report removal regardless of Evaluation Mode")
        assertTrue(NonUndoableEventJournal.isEmpty(), "Evaluation Mode disabled must record nothing")
    }

    @Test
    fun `a package that transitions from present to absent is observed and recorded`() = runTest {
        val installed = MutableStateFlow(listOf("com.test.transitioning"))

        val job = launch { installed.value = emptyList() }
        val removed = verifyAndRecordUninstall(
            installedPackageNames = installed,
            pkgName = "com.test.transitioning",
            isEvaluationModeEnabled = { true },
        )
        job.join()

        assertTrue(removed)
        assertEquals(1, NonUndoableEventJournal.snapshot().size)
    }
}
// KMK <--
