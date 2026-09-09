package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

// KMK -->
// Direct coverage for
// the release-gating decision consumed by SourceEvaluationJob.doWork(). The fixture path must only
// activate when BOTH isDebugBuild is true AND the fixture mode is not OFF -- neither condition
// alone is sufficient, so every other combination (including "release build with the preference
// somehow left on") must resolve to the real runner provider.
class SelectSourceEvaluationRunnerTest {

    private class FakeRunner : SourceEvaluationRunnerContract {
        override val state = kotlinx.coroutines.flow.MutableStateFlow(SourceEvaluationQueueState())
        override val completedCandidateKeys: Set<String> = emptySet()
        override fun start(candidates: List<EvaluationCandidate>, options: SourceEvaluationOptions) {}
        override fun cancel() {}
    }

    @Test
    fun `debug build with a non-off fixture mode selects the fixture runner`() {
        val real = FakeRunner()
        val fixture = FakeRunner()

        val selected = selectSourceEvaluationRunner(
            isDebugBuild = true,
            fixtureMode = SourceEvaluationDebugFixtureMode.CANDIDATE_LOAD_ERROR,
            realRunnerProvider = { real },
            fixtureRunnerProvider = { fixture },
        )

        assertSame(fixture, selected)
    }

    @Test
    fun `debug build with fixture mode OFF selects the real runner`() {
        val real = FakeRunner()
        val fixture = FakeRunner()

        val selected = selectSourceEvaluationRunner(
            isDebugBuild = true,
            fixtureMode = SourceEvaluationDebugFixtureMode.OFF,
            realRunnerProvider = { real },
            fixtureRunnerProvider = { fixture },
        )

        assertSame(real, selected)
    }

    @Test
    fun `non-debug build with a non-off fixture mode still selects the real runner`() {
        val real = FakeRunner()
        val fixture = FakeRunner()

        val selected = selectSourceEvaluationRunner(
            isDebugBuild = false,
            fixtureMode = SourceEvaluationDebugFixtureMode.PER_SOURCE_ERROR,
            realRunnerProvider = { real },
            fixtureRunnerProvider = { fixture },
        )

        assertSame(real, selected)
    }

    @Test
    fun `non-debug build with fixture mode OFF selects the real runner`() {
        val real = FakeRunner()
        val fixture = FakeRunner()

        val selected = selectSourceEvaluationRunner(
            isDebugBuild = false,
            fixtureMode = SourceEvaluationDebugFixtureMode.OFF,
            realRunnerProvider = { real },
            fixtureRunnerProvider = { fixture },
        )

        assertSame(real, selected)
    }

    @Test
    fun `fromPrefValue falls back to OFF for an unrecognized value`() {
        assertSame(SourceEvaluationDebugFixtureMode.OFF, SourceEvaluationDebugFixtureMode.fromPrefValue("garbage"))
    }
}
// KMK <--
