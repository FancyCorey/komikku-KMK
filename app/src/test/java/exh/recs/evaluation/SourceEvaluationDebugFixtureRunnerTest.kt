package exh.recs.evaluation

import eu.kanade.tachiyomi.extension.model.Extension
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mihon.domain.extension.model.ExtensionStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK -->
// Direct coverage for
// the debug-only, deterministic Source Evaluation fixture runner. Proves each declared mode reaches
// exactly the terminal state described by
// the isolated source-evaluation fixture contract, that cancellation
// is cooperative (matching SourceEvaluationRunner's own contract), and that no result ever contains
// a real source/extension/package/repository identity -- every label is a synthetic "Fixture Source
// N" string.
class SourceEvaluationDebugFixtureRunnerTest {

    private val candidates = listOf(
        EvaluationCandidate(
            extension = fakeExtension("pkg.one", 1L),
            priorityRank = 0,
        ),
        EvaluationCandidate(
            extension = fakeExtension("pkg.two", 2L),
            priorityRank = 1,
        ),
    )

    @Test
    fun `candidate load error mode fails immediately with a synthetic error key`() = runBlocking {
        val runner = SourceEvaluationDebugFixtureRunner(SourceEvaluationDebugFixtureMode.CANDIDATE_LOAD_ERROR)
        runner.start(candidates, SourceEvaluationOptions())

        val finalState = awaitTerminal(runner)

        assertEquals(SourceEvaluationQueueState.Status.Failed, finalState.status)
        assertEquals("fixture-candidate-load-error", finalState.errorMessage)
        assertTrue(runner.completedCandidateKeys.isEmpty())
        assertNoRealIdentity(finalState)
    }

    @Test
    fun `connectivity lost mode reaches ConnectivityLost`() = runBlocking {
        val runner = SourceEvaluationDebugFixtureRunner(SourceEvaluationDebugFixtureMode.CONNECTIVITY_LOST)
        runner.start(candidates, SourceEvaluationOptions())

        val finalState = awaitTerminal(runner)

        assertEquals(SourceEvaluationQueueState.Status.ConnectivityLost, finalState.status)
        assertTrue(runner.completedCandidateKeys.isEmpty())
        assertNoRealIdentity(finalState)
    }

    @Test
    fun `per-source error mode completes with an ERROR verdict recorded for every candidate`() = runBlocking {
        val runner = SourceEvaluationDebugFixtureRunner(SourceEvaluationDebugFixtureMode.PER_SOURCE_ERROR)
        runner.start(candidates, SourceEvaluationOptions())

        val finalState = awaitTerminal(runner)

        assertEquals(SourceEvaluationQueueState.Status.Completed, finalState.status)
        assertEquals(candidates.size, finalState.results.size)
        assertTrue(finalState.results.all { it.verdict == tachiyomi.domain.taste.model.SourceEvaluationVerdict.ERROR })
        assertEquals(candidates.size, runner.completedCandidateKeys.size)
        assertNoRealIdentity(finalState)
    }

    @Test
    fun `per-source error mode uses the real runner's signatureHash-pkgName completion key format`() = runBlocking {
        // KMK: completedCandidateKeys must be cursor-compatible with
        // SourceEvaluationContinuationPolicy, which persists/advances a cursor keyed by
        // "${signatureHash}|${pkgName}" (SourceEvaluationContinuationPolicy.candidateKey), the same
        // format SourceEvaluationRunner uses. The fixture previously used an unrelated
        // "fixture|priorityRank|index" key that would have silently broken cursor advancement if the
        // fixture's completedCandidateKeys were ever consumed the same way as the real runner's.
        val runner = SourceEvaluationDebugFixtureRunner(SourceEvaluationDebugFixtureMode.PER_SOURCE_ERROR)
        runner.start(candidates, SourceEvaluationOptions())
        awaitTerminal(runner)

        val expectedKeys = candidates.map(SourceEvaluationContinuationPolicy::candidateKey).toSet()
        assertEquals(expectedKeys, runner.completedCandidateKeys)
    }

    @Test
    fun `cancel() cooperatively cancels a running fixture batch`() = runBlocking {
        val runner = SourceEvaluationDebugFixtureRunner(SourceEvaluationDebugFixtureMode.PER_SOURCE_ERROR)
        runner.start(candidates, SourceEvaluationOptions())
        runner.cancel()

        val finalState = awaitTerminal(runner)

        assertEquals(SourceEvaluationQueueState.Status.Cancelled, finalState.status)
    }

    @Test
    fun `a cancelled run cannot overwrite a newer run`() = runBlocking {
        val runner = SourceEvaluationDebugFixtureRunner(SourceEvaluationDebugFixtureMode.PER_SOURCE_ERROR)
        runner.start(candidates, SourceEvaluationOptions())
        runner.cancel()
        runner.start(candidates, SourceEvaluationOptions())

        val finalState = awaitTerminal(runner)

        assertEquals(SourceEvaluationQueueState.Status.Completed, finalState.status)
        assertEquals(candidates.size, finalState.results.size)
    }

    private fun assertNoRealIdentity(state: SourceEvaluationQueueState) {
        val forbidden = listOf("pkg.one", "pkg.two")
        state.results.forEach { result ->
            forbidden.forEach { needle ->
                assertFalse(result.extensionName.contains(needle))
                assertFalse(result.pkgName.contains(needle))
                assertFalse(result.sourceName.contains(needle))
            }
        }
    }

    private suspend fun awaitTerminal(runner: SourceEvaluationDebugFixtureRunner): SourceEvaluationQueueState {
        var state = runner.state.value
        var guard = 0
        while (!state.isTerminal && guard < 200) {
            delay(5L)
            state = runner.state.value
            guard++
        }
        return state
    }

    private fun fakeExtension(pkgName: String, sig: Long): Extension.Available =
        Extension.Available(
            name = "unused",
            pkgName = pkgName,
            versionName = "1.0",
            versionCode = 1L,
            libVersion = 1.0,
            lang = "en",
            isNsfw = false,
            signatureHash = sig.toString(),
            storeName = "unused-repo",
            sources = emptyList(),
            apkUrl = "https://test-repo.example.com/apk/$pkgName.apk",
            iconUrl = "",
            store = ExtensionStore(
                indexUrl = "",
                name = "unused-repo",
                badgeLabel = "unused-repo",
                signingKey = sig.toString(),
                contact = ExtensionStore.Contact(website = "", discord = null),
                isLegacy = false,
                extensionListUrl = null,
            ),
        )
}
// KMK <--
