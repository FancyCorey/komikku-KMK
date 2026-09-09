package exh.recs.evaluation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.taste.model.SourceEvaluationVerdict
import java.util.concurrent.atomic.AtomicLong

// KMK -->
// Named modes for the
// Source Evaluation debug-only fixture, matching the deterministic terminal states already declared
// in the isolated source-evaluation fixture contract. "Off" is the
// default/production value and must never activate the fixture path -- see
// SourceEvaluationJob.selectSourceEvaluationRunner, which is the only call site that reads this
// value and only does so behind `BuildConfig.DEBUG`.
enum class SourceEvaluationDebugFixtureMode(val prefValue: String) {
    OFF("off"),
    CANDIDATE_LOAD_ERROR("candidate_load_error"),
    CONNECTIVITY_LOST("connectivity_lost"),
    PER_SOURCE_ERROR("per_source_error"),
    ;

    companion object {
        fun fromPrefValue(value: String): SourceEvaluationDebugFixtureMode =
            entries.find { it.prefValue == value } ?: OFF
    }
}

/**
 * Debug-only, deterministic Source Evaluation runner used to exercise the three terminal states
 * declared by the `source-evaluation-read-only-and-failure` host fixture without touching
 * [eu.kanade.tachiyomi.extension.ExtensionManager], the network, or any real source/extension
 * identity. Always compiled into every build type (there is no `app/src/debug/java` source set in
 * this project), but only ever constructed when both `BuildConfig.DEBUG` is true and the private
 * `evaluationFixtureFailureMode()` opt-in is not [SourceEvaluationDebugFixtureMode.OFF] -- see
 * `SourceEvaluationJob.selectSourceEvaluationRunner`. Every candidate/result label emitted here is a
 * synthetic "Fixture Source N" string; no real extension, package, or repository name is ever read
 * or produced by this class.
 */
class SourceEvaluationDebugFixtureRunner(
    private val mode: SourceEvaluationDebugFixtureMode,
) : SourceEvaluationRunnerContract {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var runJob: Job? = null
    private val runGeneration = AtomicLong(0L)

    private val _state = MutableStateFlow(SourceEvaluationQueueState())
    override val state: StateFlow<SourceEvaluationQueueState> = _state.asStateFlow()

    private val _completedCandidateKeys = mutableSetOf<String>()
    override val completedCandidateKeys: Set<String> get() = _completedCandidateKeys.toSet()

    override fun start(candidates: List<EvaluationCandidate>, options: SourceEvaluationOptions) {
        if (runJob?.isActive == true) return

        val generation = runGeneration.incrementAndGet()
        _completedCandidateKeys.clear()
        _state.value = SourceEvaluationQueueState(
            status = SourceEvaluationQueueState.Status.Running,
            totalCount = candidates.size,
            installerMode = options.installerMode,
            batchSize = options.batchSize,
        )

        runJob = scope.launch {
            try {
                // Deterministic, cancellable delay so SourceEvaluationJobCancellationTest-style
                // assertions can cancel mid-run and observe cooperative CancellationException
                // propagation, matching the real runner's contract.
                delay(50L)

                when (mode) {
                    SourceEvaluationDebugFixtureMode.OFF -> {
                        // Never reached in practice -- selectSourceEvaluationRunner never constructs
                        // this class for OFF -- but fail safe into a no-op completion rather than an
                        // undefined state if it ever is.
                        _state.update { it.copy(status = SourceEvaluationQueueState.Status.NoActionableWork) }
                    }
                    SourceEvaluationDebugFixtureMode.CANDIDATE_LOAD_ERROR -> {
                        _state.update {
                            it.copy(
                                status = SourceEvaluationQueueState.Status.Failed,
                                errorMessage = "fixture-candidate-load-error",
                            )
                        }
                    }
                    SourceEvaluationDebugFixtureMode.CONNECTIVITY_LOST -> {
                        _state.update { it.copy(status = SourceEvaluationQueueState.Status.ConnectivityLost) }
                    }
                    SourceEvaluationDebugFixtureMode.PER_SOURCE_ERROR -> {
                        val results = candidates.mapIndexed { index, candidate ->
                            // Corrective pass 2026-08-03: the completion key must match the real
                            // SourceEvaluationRunner's "${signatureHash}|${pkgName}" format (see
                            // SourceEvaluationRunner.kt and SourceEvaluationContinuationPolicy.kt) so
                            // SourceEvaluationContinuationPolicy can advance its cursor correctly if
                            // this fixture's completedCandidateKeys are ever consumed the same way as
                            // the real runner's. This internal key is never rendered in the fixture
                            // UI -- only the EvaluationResult fields below are user-visible, and those
                            // remain fully synthetic.
                            val key = "${candidate.extension.signatureHash}|${candidate.extension.pkgName}"
                            _completedCandidateKeys.add(key)
                            SourceEvaluationQueueState.EvaluationResult(
                                extensionName = "Fixture Source ${index + 1}",
                                sourceName = "Fixture Source ${index + 1}",
                                pkgName = "fixture.source.$index",
                                signatureHash = "fixture-signature-$index",
                                sourceId = index.toLong(),
                                verdict = SourceEvaluationVerdict.ERROR,
                                errorMessage = "fixture-per-source-error",
                            )
                        }
                        _state.update {
                            it.copy(
                                completedCount = candidates.size,
                                failedCount = candidates.size,
                                results = results,
                                status = SourceEvaluationRunCompletionPolicy.resolveStatus(
                                    candidatesCount = candidates.size,
                                    durablyHandledCount = _completedCandidateKeys.size,
                                ),
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                if (runGeneration.get() == generation) {
                    _state.update { it.copy(status = SourceEvaluationQueueState.Status.Cancelled) }
                }
            }
        }
    }

    override fun cancel() {
        val job = runJob
        if (job?.isActive != true) return

        _state.update { it.copy(status = SourceEvaluationQueueState.Status.Cancelling) }
        // Cancellation can race with coroutine startup. If the job is cancelled before its body
        // begins, the body's CancellationException handler never runs; complete the public state
        // from the job lifecycle as well so callers never remain stuck at Cancelling.
        val generation = runGeneration.get()
        job.invokeOnCompletion { cause ->
            if (cause is CancellationException && runGeneration.get() == generation) {
                _state.update { it.copy(status = SourceEvaluationQueueState.Status.Cancelled) }
            }
        }
        job.cancel()
    }
}
// KMK <--
