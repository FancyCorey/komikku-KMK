package exh.recs.evaluation

import eu.kanade.tachiyomi.extension.model.Extension
import tachiyomi.domain.taste.model.SourceEvaluationVerdict

// KMK -->
data class SourceEvaluationQueueState(
    val status: Status = Status.Idle,
    val totalCount: Int = 0,
    val completedCount: Int = 0,
    val failedCount: Int = 0,
    val skippedCount: Int = 0,
    val currentExtensionName: String? = null,
    val currentSourceName: String? = null,
    val currentPhase: Phase = Phase.Idle,
    val results: List<EvaluationResult> = emptyList(),
    val installerMode: SourceEvaluationInstallerPolicy.InstallerMode = SourceEvaluationInstallerPolicy.InstallerMode.PRIVATE,
    val batchSize: Int = 10,
    val errorMessage: String? = null,
) {
    // KMK --> v0.7.18
    enum class Status { Idle, Running, Cancelling, Completed, Cancelled, Failed, ConnectivityLost }
    // KMK <--

    enum class Phase {
        Idle,
        Downloading,
        Installing,
        LoadingSources,
        ProbingPopular,
        ProbingLatest,
        ProbingSearch,
        Scoring,
        Cleanup,
    }

    // KMK --> v0.6.13: cleanup status for each evaluated extension
    enum class CleanupStatus {
        NotNeeded,
        PrivateRemoved,
        SkippedPreExisting,
        PromptRequired,
        Failed,
    }
    // KMK <--

    data class EvaluationResult(
        val extensionName: String,
        val sourceName: String,
        val pkgName: String,
        val signatureHash: String,
        val sourceId: Long?,
        val verdict: SourceEvaluationVerdict,
        val errorMessage: String? = null,
        // KMK --> v0.6.13
        val cleanupStatus: CleanupStatus = CleanupStatus.NotNeeded,
        // KMK <--
    )

    // KMK --> v0.7.18: ConnectivityLost added to idle+terminal so options section re-appears and summary card shows
    val isIdle: Boolean get() = status == Status.Idle || status == Status.Completed || status == Status.Cancelled || status == Status.Failed || status == Status.ConnectivityLost
    val isRunning: Boolean get() = status == Status.Running
    // KMK --> v0.6.19
    val isTerminal: Boolean get() = status == Status.Completed || status == Status.Cancelled || status == Status.Failed || status == Status.ConnectivityLost
    // KMK <--
    // KMK <-- v0.7.18
    // KMK --> v0.6.13
    val promptRequiredCleanupCount: Int get() = results.count { it.cleanupStatus == CleanupStatus.PromptRequired }
    // KMK <--
    // KMK --> v0.7.11
    val cleanupFailedCount: Int get() = results.count { it.cleanupStatus == CleanupStatus.Failed }
    // KMK <--
    val strongFitCount: Int get() = results.count { it.verdict == SourceEvaluationVerdict.STRONG_FIT }
    val worthTryingCount: Int get() = results.count { it.verdict == SourceEvaluationVerdict.WORTH_TRYING }
    val explicitHeavyCount: Int get() = results.count { it.verdict == SourceEvaluationVerdict.EXPLICIT_HEAVY }
    val ecchiHeavyCount: Int get() = results.count { it.verdict == SourceEvaluationVerdict.ECCHI_HEAVY }
    val errorCount: Int get() = results.count { it.verdict == SourceEvaluationVerdict.ERROR }
}

/**
 * Options that configure a single evaluation run.
 */
data class SourceEvaluationOptions(
    val installerMode: SourceEvaluationInstallerPolicy.InstallerMode = SourceEvaluationInstallerPolicy.InstallerMode.PRIVATE,
    val batchSize: Int = 10,
    val skipAlreadyEvaluated: Boolean = true,
    val includeExplicitCandidates: Boolean = false,
    val reEvaluateStale: Boolean = false,
    // KMK --> v0.6.13: allow Android uninstall prompts during cleanup (default: skip shared-ext cleanup silently)
    val promptHeavyCleanupAllowed: Boolean = false,
    // KMK <--
    // KMK --> v0.7.4: restrict evaluation run to extensions updated since their last evaluation
    val onlyUpdatedEvaluated: Boolean = false,
    // KMK <--
)

/**
 * Minimal data about a candidate extension for evaluation.
 */
data class EvaluationCandidate(
    val extension: Extension.Available,
    val priorityRank: Int,
)
// KMK <--
