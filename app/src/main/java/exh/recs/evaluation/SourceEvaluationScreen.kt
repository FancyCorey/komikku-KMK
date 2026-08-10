package exh.recs.evaluation

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.KmkEmptyStateArtwork
import eu.kanade.presentation.components.KmkEmptyStateIllustration
import eu.kanade.presentation.util.Screen
import exh.recs.settings.RecommendationSettingsQuickAccessDestination
import exh.recs.settings.RecommendationSettingsQuickAccessRow
import exh.recs.settings.toScreen
import exh.util.EvaluationModeFormatter
import exh.util.rememberEvaluationModeEnabled
import kotlinx.coroutines.launch
import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationMetadataConfidence
import tachiyomi.domain.taste.model.SourceEvaluationUnsafeSource
import tachiyomi.domain.taste.model.SourceEvaluationVerdict
import tachiyomi.domain.taste.model.SourceRecommendationFit
import tachiyomi.domain.taste.model.UnsafeExtensionPackage
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.roundToInt

// KMK -->
// KMK --> v0.6.20: threshold for reassessment recommendation
private const val REASSESSMENT_THRESHOLD = 100
// KMK <--

// KMK --> v0.7.18: map typed ScreenErrorKey to localized strings for display
@Composable
private fun ScreenErrorKey.toLocalString(): String = when (this) {
    is ScreenErrorKey.Offline -> stringResource(KMR.strings.source_evaluation_offline_error)
    is ScreenErrorKey.CandidateLoadFailed -> {
        val kind = SourceEvaluationProbeErrorKind.fromStorageKey(detail)
        if (kind == null) {
            stringResource(KMR.strings.source_evaluation_error_candidate_load_failed_unknown)
        } else {
            stringResource(
                when (kind) {
                    SourceEvaluationProbeErrorKind.NETWORK_UNAVAILABLE -> KMR.strings.source_evaluation_probe_error_network
                    SourceEvaluationProbeErrorKind.TIMEOUT -> KMR.strings.source_evaluation_probe_error_timeout
                    SourceEvaluationProbeErrorKind.UNSUPPORTED -> KMR.strings.source_evaluation_probe_error_unsupported
                    SourceEvaluationProbeErrorKind.EXTENSION_INCOMPATIBLE -> KMR.strings.source_evaluation_probe_error_extension_incompatible
                    SourceEvaluationProbeErrorKind.INTERNAL -> KMR.strings.source_evaluation_probe_error_internal
                },
            )
        }
    }
    is ScreenErrorKey.CrashRecovery -> {
        val display = SourceEvaluationErrorDisplayPolicy.crashRecoveryDisplay(
            extensionName = extensionName,
            phase = phase,
            evaluationModeEnabled = rememberEvaluationModeEnabled(),
        )
        stringResource(
            KMR.strings.source_evaluation_crash_recovery_marked_unsafe,
            display.extensionLabel,
            display.phase,
        )
    }
    // KMK --> v0.7.43
    is ScreenErrorKey.JobConflict -> when (activeJob) {
        ScreenErrorKey.ActiveJobKind.SOURCE_EVALUATION -> stringResource(
            KMR.strings.source_evaluation_job_conflict_evaluation_running,
        )
        ScreenErrorKey.ActiveJobKind.RECOMMENDATION_QUALITY -> stringResource(
            KMR.strings.source_evaluation_job_conflict_quality_running,
        )
    }
    // KMK <--
}
// KMK <--

class SourceEvaluationScreen(
    // KMK v0.8.11: optional stable item key to scroll to on open, set when this screen is opened
    // from a Recommendation Settings search result -- same contract as
    // exh.recs.settings.RecommendationForYouSettingsScreen.anchor.
    val anchor: String? = null,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel { SourceEvaluationScreenModel(context) }
        val state by screenModel.state.collectAsState()
        val queueState = state.queueState

        // KMK: this only gates the app-bar shortcut while the screen is open.
        val hideForYouTab = remember {
            Injekt.get<eu.kanade.domain.ui.UiPreferences>().hideForYouTab().get()
        }
        // KMK: Source Evaluation is root-pushed, so tab changes go through HomeScreen.openTab.
        val scope = rememberCoroutineScope()

        // KMK --> v0.6.12: refresh Shizuku state on screen resume (e.g. returning from Shizuku app)
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner.lifecycle) {
            val observer = object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    screenModel.refreshShizukuState()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        // KMK <--

        // KMK v0.8.10: clear a stale "Evaluation completed"/Cancelled/Failed/ConnectivityLost
        // summary when leaving the screen -- see SourceEvaluationCompletionLifecyclePolicy. A
        // still-running background job is untouched (the policy only clears terminal states).
        DisposableEffect(Unit) {
            onDispose { screenModel.clearCompletionOnLeave() }
        }
        // KMK <--

        // KMK --> v0.7.42-fix2: one `now` shared by the queue, sort, and row rendering below, so they
        // can never disagree about which fits are current vs. outdated within one composition pass.
        val now = remember(state.evaluations, state.recommendationFitsByEvalKey) { System.currentTimeMillis() }
        // KMK <--

        // KMK --> v0.6.15: sort evaluated results in memory (sanitization already done in ScreenModel)
        // KMK --> v0.7.6: sort from filteredEvaluations (display filter applied by screen model)
        val sortedEvaluations = remember(state.filteredEvaluations, state.recommendationFitsByEvalKey, state.resultSortMode, now) {
            SourceEvaluationResultList.sort(state.filteredEvaluations, state.recommendationFitsByEvalKey, state.resultSortMode, now)
        }
        // KMK <--
        // KMK <--
        // KMK --> v0.7.7: rec-quality queue computed outside LazyColumn (remember is @Composable)
        val recQualityQueue = remember(state.evaluations, state.recommendationFitsByEvalKey, now) {
            SourceRecommendationQualityQueue.compute(state.evaluations, state.recommendationFitsByEvalKey, now)
        }
        // KMK <--

        // KMK --> v0.7.11: pre-run consent dialog
        if (state.showConsentDialog) {
            AlertDialog(
                onDismissRequest = screenModel::dismissConsent,
                title = { Text(stringResource(KMR.strings.source_evaluation_consent_title)) },
                text = { Text(stringResource(KMR.strings.source_evaluation_consent_message)) },
                confirmButton = {
                    TextButton(onClick = screenModel::confirmConsent) {
                        Text(stringResource(KMR.strings.source_evaluation_consent_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = screenModel::dismissConsent) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }
        // KMK <--

        // KMK --> v0.6.13: prompt-heavy warning dialog
        if (state.showPromptHeavyWarningDialog) {
            AlertDialog(
                onDismissRequest = screenModel::dismissPromptWarningDialog,
                title = { Text(stringResource(KMR.strings.source_evaluation_prompt_warning_title)) },
                text = { Text(stringResource(KMR.strings.source_evaluation_prompt_warning_message)) },
                dismissButton = {
                    if (state.privateAvailable) {
                        TextButton(onClick = screenModel::switchToPrivateAndStart) {
                            Text(stringResource(KMR.strings.source_evaluation_use_private_instead))
                        }
                    }
                    TextButton(onClick = screenModel::dismissPromptWarningDialog) {
                        Text(stringResource(KMR.strings.source_evaluation_prompt_warning_cancel))
                    }
                },
                confirmButton = {
                    TextButton(onClick = screenModel::confirmAndStartWithPrompts) {
                        Text(stringResource(KMR.strings.source_evaluation_continue_with_prompts))
                    }
                },
            )
        }
        // KMK <--

        // KMK --> v0.6.15: clear evaluation results confirmation dialog
        if (state.showClearEvaluationsDialog) {
            AlertDialog(
                onDismissRequest = screenModel::dismissClearAllEvaluations,
                title = { Text(stringResource(KMR.strings.source_evaluation_clear_confirm_title)) },
                text = { Text(stringResource(KMR.strings.source_evaluation_clear_confirm_message)) },
                confirmButton = {
                    TextButton(onClick = screenModel::confirmClearAllEvaluations) {
                        Text(stringResource(KMR.strings.source_evaluation_clear_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = screenModel::dismissClearAllEvaluations) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }
        // KMK <--

        // KMK --> v0.6.16: clear unsafe quarantine confirmation dialog
        if (state.showClearUnsafeDialog) {
            AlertDialog(
                onDismissRequest = screenModel::dismissClearUnsafeDialog,
                title = { Text(stringResource(KMR.strings.source_evaluation_unsafe_clear_confirm_title)) },
                text = { Text(stringResource(KMR.strings.source_evaluation_unsafe_clear_confirm_message)) },
                confirmButton = {
                    TextButton(onClick = screenModel::confirmClearUnsafeSources) {
                        Text(stringResource(KMR.strings.source_evaluation_unsafe_clear_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = screenModel::dismissClearUnsafeDialog) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }
        // KMK <--

        // KMK --> v0.6.18: clear blocked packages confirmation dialog
        if (state.showClearBlockedPackagesDialog) {
            AlertDialog(
                onDismissRequest = screenModel::dismissClearBlockedPackagesDialog,
                title = { Text(stringResource(KMR.strings.source_evaluation_blocked_packages_clear_confirm_title)) },
                text = { Text(stringResource(KMR.strings.source_evaluation_blocked_packages_clear_confirm_message)) },
                confirmButton = {
                    TextButton(onClick = screenModel::confirmClearBlockedPackages) {
                        Text(stringResource(KMR.strings.source_evaluation_blocked_packages_clear_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = screenModel::dismissClearBlockedPackagesDialog) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }
        // KMK <--

        // KMK --> v0.6.18: blocked packages detail dialog
        if (state.showBlockedPackagesDialog) {
            AlertDialog(
                onDismissRequest = screenModel::dismissBlockedPackagesDialog,
                title = { Text(stringResource(KMR.strings.source_evaluation_blocked_packages_title)) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            text = stringResource(KMR.strings.source_evaluation_blocked_packages_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = MaterialTheme.padding.small),
                        )
                        state.blockedPackages.forEach { pkg ->
                            BlockedPackageRow(
                                pkg = pkg,
                                onAllow = { screenModel.allowBlockedPackage(pkg.pkgName) },
                                // KMK --> v0.7.18
                                hasNewerAvailable = pkg.pkgName in state.blockedPackagesWithNewerAvailable,
                                // KMK <--
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = screenModel::dismissBlockedPackagesDialog) {
                        Text(stringResource(MR.strings.action_close))
                    }
                },
                dismissButton = {
                    TextButton(onClick = screenModel::requestClearBlockedPackages) {
                        Text(stringResource(KMR.strings.source_evaluation_blocked_packages_clear))
                    }
                },
            )
        }
        // KMK <--

        // KMK --> v0.6.16: unsafe sources detail dialog
        if (state.showUnsafeSourcesDialog) {
            AlertDialog(
                onDismissRequest = screenModel::dismissUnsafeSourcesDialog,
                title = { Text(stringResource(KMR.strings.source_evaluation_unsafe_sources_title)) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            text = stringResource(KMR.strings.source_evaluation_unsafe_sources_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = MaterialTheme.padding.small),
                        )
                        state.unsafeSources.forEach { unsafe ->
                            UnsafeSourceRow(
                                unsafe = unsafe,
                                onRemove = { screenModel.deleteUnsafeSource(unsafe.unsafeKey) },
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = screenModel::dismissUnsafeSourcesDialog) {
                        Text(stringResource(MR.strings.action_close))
                    }
                },
                dismissButton = {
                    TextButton(onClick = screenModel::requestClearUnsafeSources) {
                        Text(stringResource(KMR.strings.source_evaluation_unsafe_sources_clear))
                    }
                },
            )
        }
        // KMK <--

        // KMK v0.8.10-fix5: source-runtime health diagnostics + recovery actions dialog
        if (state.showRuntimeHealthDialog) {
            var confirmDisablePkg by remember { mutableStateOf<String?>(null) }
            AlertDialog(
                onDismissRequest = screenModel::dismissRuntimeHealthDialog,
                title = { Text(stringResource(KMR.strings.source_evaluation_runtime_health_dialog_title)) },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        state.runtimeHealthIssues.forEach { issue ->
                            RuntimeHealthIssueRow(
                                issue = issue,
                                onRetry = { screenModel.retryRuntimeHealthSource(issue.sourceId) },
                                onUpdate = issue.packageName?.takeIf { issue.hasUpdate }
                                    ?.let { pkg -> { screenModel.updateRuntimeHealthExtension(pkg) } },
                                onReinstall = issue.packageName
                                    ?.takeIf { it in state.runtimeHealthReinstallablePackages }
                                    ?.let { pkg -> { screenModel.reinstallRuntimeHealthExtension(pkg) } },
                                onUninstall = issue.packageName
                                    ?.let { pkg -> { screenModel.uninstallRuntimeHealthExtension(pkg) } },
                                onDisable = issue.packageName
                                    ?.let { pkg -> { confirmDisablePkg = pkg } },
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = screenModel::dismissRuntimeHealthDialog) {
                        Text(stringResource(MR.strings.action_close))
                    }
                },
            )
            if (confirmDisablePkg != null) {
                val pkg = confirmDisablePkg!!
                val extName = state.runtimeHealthIssues.find { it.packageName == pkg }?.extensionName ?: pkg
                AlertDialog(
                    onDismissRequest = { confirmDisablePkg = null },
                    title = { Text(stringResource(KMR.strings.source_evaluation_runtime_health_disable_confirm_title)) },
                    text = { Text(stringResource(KMR.strings.source_evaluation_runtime_health_disable_confirm_body, extName)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                screenModel.disableRuntimeHealthExtension(pkg)
                                confirmDisablePkg = null
                            },
                        ) {
                            Text(stringResource(KMR.strings.source_evaluation_runtime_health_disable))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmDisablePkg = null }) {
                            Text(stringResource(MR.strings.action_cancel))
                        }
                    },
                )
            }
        }
        // KMK <--

        // KMK --> v0.6.20: management action confirmation dialog
        if (state.managementConfirmAction != null) {
            AlertDialog(
                onDismissRequest = screenModel::dismissManagementConfirm,
                title = { Text(stringResource(KMR.strings.source_evaluation_management_confirm_title)) },
                text = { Text(stringResource(KMR.strings.source_evaluation_management_confirm_body)) },
                confirmButton = {
                    TextButton(onClick = screenModel::confirmManagementAction) {
                        Text(stringResource(MR.strings.action_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = screenModel::dismissManagementConfirm) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }
        // KMK <--

        // KMK --> v0.6.20: reassessment prompt threshold (hoisted in v0.8.11 so the anchor key list
        // below and the items can share the exact same condition)
        val ratingsSinceBaseline = state.currentRatedCount - state.reassessmentBaselineCount
        // KMK <--

        // KMK v0.8.13-fix1: hoisted so the anchor key list and the item body share the exact same
        // computed state, instead of each calling SourceEvaluationStaleCompletionDisplayPolicy
        // separately with the risk of the two calls drifting.
        val staleCompletionState = SourceEvaluationStaleCompletionDisplayPolicy.evaluate(
            staleCandidatesEmpty = state.staleCandidates.isEmpty(),
            continuationCursorStaleIsSet = state.continuationCursorStale != null,
            isRunning = state.queueState.isRunning,
            hasUnreachableOutdated = state.outdatedReconciliation.hasUnreachableOutdated,
        )

        // KMK v0.8.11: scroll-to-anchor support for Recommendation Settings search results. The key
        // list mirrors the LazyColumn's item order below, including every conditional item that can
        // precede an anchor target -- see exh.recs.settings.ScrollToAnchorEffect. Items after the
        // last anchorable key (the past-evaluations list) are not needed for anchor resolution.
        val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
        val itemKeysInOrder = remember(state, queueState, recQualityQueue, ratingsSinceBaseline) {
            buildList {
                if (state.screenError != null) add("screen_error")
                if (state.leftoverPkgName != null) add("leftover_ext_warning")
                if (state.repoUnavailableWarning) add("repo_unavailable")
                if (queueState.isRunning) add("progress")
                if (!queueState.isRunning && queueState.status != SourceEvaluationQueueState.Status.Idle) add("summary")
                if (queueState.isIdle) {
                    add("run_header")
                    add("batch_size")
                    add("setup_options_toggle")
                    if (state.showSetupOptions) {
                        add("option_skip")
                        add("option_explicit")
                        add("candidate_diagnostics")
                    }
                    if (!state.tasteConfidence.isSufficientForPersonalizedEvaluation) add("taste_confidence_warning")
                    if (state.profileChangedSinceLastEval && state.evaluations.isNotEmpty()) add("profile_changed_prompt")
                    add("start_button")
                    if (state.canContinue && !state.queueState.isRunning) add("continue_button")
                    add("installer_header")
                    val installerReadyForAnchors = state.installerPolicy?.readiness ==
                        SourceEvaluationInstallerPolicy.InstallerReadiness.READY
                    if (installerReadyForAnchors) add("installer_details_toggle")
                    if (state.showInstallerDetails || !installerReadyForAnchors) add("installer_mode")
                    if (state.showShizukuSetup) add("shizuku_setup") else add("shizuku_setup_toggle")
                    if (state.installerPolicy?.requiresPromptWarning == true) add("prompt_warning")
                    if (state.installerPolicy?.readiness == SourceEvaluationInstallerPolicy.InstallerReadiness.UNAVAILABLE) {
                        add("unavailable_warning")
                    }
                    add("reassess_header")
                    if (ratingsSinceBaseline >= REASSESSMENT_THRESHOLD) add("reassessment_prompt")
                    add("reassess_button")
                    if (state.updatedEvaluatedExtensionCount > 0) {
                        add("updated_evaluated_notice")
                        add("reassess_updated_button")
                    }
                    if (staleCompletionState != SourceEvaluationStaleCompletionDisplayPolicy.DisplayState.Hidden) {
                        add("stale_reassess_complete")
                    }
                    // KMK v0.8.14-fix1: primary action before the excluded-sources disclosure -- see
                    // the LazyColumn items above for the reasoning.
                    if (state.staleCandidates.isNotEmpty() && !state.queueState.isRunning) add("stale_reassess_button")
                }
                if (recQualityQueue.totalPromising > 0) add("rec_quality_section")
                add("diagnostics_header")
            }
        }
        exh.recs.settings.ScrollToAnchorEffect(lazyListState, itemKeysInOrder, anchor)
        // KMK <--

        // KMK: settings screens do not host the collection quick-access panel.
        Scaffold(
            topBar = { scrollBehavior ->
                // KMK v0.8.18: the v0.8.15-fix1 top-right "Sources to try" app-bar shortcut was removed
                // -- it is now redundant with the Recommendation Settings quick-access row
                // (v0.8.17-fix1) already rendered below the app bar on this screen, which reaches the
                // same `RecommendationNonInstalledDiscoverySettingsScreen()` destination.
                // KMK: the For You shortcut returns to root and asks HomeScreen to select the tab.
                AppBar(
                    title = stringResource(KMR.strings.source_evaluation_title),
                    navigateUp = navigator::pop,
                    actions = {
                        if (!hideForYouTab) {
                            eu.kanade.presentation.components.AppBarActions(
                                kotlinx.collections.immutable.persistentListOf(
                                    eu.kanade.presentation.components.AppBar.Action(
                                        title = stringResource(KMR.strings.source_evaluation_go_to_for_you),
                                        icon = Icons.Filled.Home,
                                        onClick = {
                                            navigator.popUntilRoot()
                                            scope.launch {
                                                eu.kanade.tachiyomi.ui.home.HomeScreen.openTab(
                                                    eu.kanade.tachiyomi.ui.home.HomeScreen.Tab.Browse(toForYou = true),
                                                )
                                            }
                                        },
                                    ),
                                ),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            val layoutDirection = LocalLayoutDirection.current
            val listContentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                end = contentPadding.calculateEndPadding(layoutDirection),
                bottom = contentPadding.calculateBottomPadding(),
            )
            Column(modifier = Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
                RecommendationSettingsQuickAccessRow(
                    current = RecommendationSettingsQuickAccessDestination.SourceEvaluation,
                    onNavigate = { destination -> navigator.replace(destination.toScreen()) },
                )
                LazyColumn(
                    state = lazyListState,
                    contentPadding = listContentPadding,
                    modifier = Modifier.weight(1f),
                ) {
                    // KMK --> v0.6.16: screen error from crash recovery or candidate load failure
                    // KMK --> v0.7.18: typed ScreenErrorKey replaces hardcoded String
                    state.screenError?.let { errorKey ->
                        item(key = "screen_error") {
                            InfoCard(
                                message = errorKey.toLocalString(),
                                isError = true,
                                onDismiss = screenModel::clearScreenError,
                                modifier = Modifier.padding(MaterialTheme.padding.medium),
                            )
                        }
                    }
                    // KMK <--
                    // KMK <-- v0.7.18

                    // KMK --> SEC-01 v0.7.16: leftover extension warning (process-death survivor)
                    state.leftoverPkgName?.let { pkgName ->
                        item(key = "leftover_ext_warning") {
                            Column(modifier = Modifier.padding(MaterialTheme.padding.medium)) {
                                InfoCard(
                                    message = stringResource(KMR.strings.source_evaluation_leftover_warning, pkgName),
                                    isError = true,
                                )
                                TextButton(
                                    onClick = screenModel::dismissLeftoverExtension,
                                    modifier = Modifier.align(Alignment.End),
                                ) {
                                    Text(stringResource(KMR.strings.source_evaluation_leftover_uninstall))
                                }
                            }
                        }
                    }
                    // KMK <--

                    // KMK --> v0.6.19: unsafe_sources and blocked_packages cards demoted to safety diagnostics row below options.
                    // (Cards and dialogs remain; only placement changed.)
                    // KMK <--

                    // KMK --> v0.7.18: non-blocking repo-unavailable warning when extension list is empty
                    if (state.repoUnavailableWarning) {
                        item(key = "repo_unavailable") {
                            InfoCard(
                                message = stringResource(KMR.strings.source_evaluation_repo_unavailable_warning),
                                isError = false,
                                modifier = Modifier.padding(MaterialTheme.padding.medium),
                            )
                        }
                    }
                    // KMK <--

                    // Status / Progress section
                    if (queueState.isRunning) {
                        item(key = "progress") {
                            EvaluationProgressCard(
                                queueState = queueState,
                                onCancel = screenModel::cancelEvaluation,
                            )
                        }
                    }

                    if (!queueState.isRunning && queueState.status != SourceEvaluationQueueState.Status.Idle) {
                        item(key = "summary") {
                            EvaluationSummaryCard(
                                queueState = queueState,
                                onReset = screenModel::resetEvaluation,
                                // KMK --> SEC-02 v0.7.16
                                onCompleteCleanup = screenModel::cleanupPromptRequiredExtensions,
                                // KMK <--
                                // KMK --> v0.7.31: C2 — retry after connectivity loss
                                onRetry = screenModel::startEvaluation,
                                // KMK <--
                            )
                        }
                    }

                    // Options section (only when not running)
                    // KMK v0.8.11: reorganized into named sections -- Run evaluation, Installer and
                    // cleanup, Reassessment -- so the first screen reads as distinct groups instead of
                    // one long mixed options list. Every control is unchanged; only order and section
                    // headers moved. Keep the key list in `itemKeysInOrder` above in sync.
                    if (queueState.isIdle) {
                        item(key = "run_header") {
                            SectionHeader(stringResource(KMR.strings.source_evaluation_section_run))
                        }

                        item(key = "batch_size") {
                            BatchSizeSelector(
                                selected = state.options.batchSize,
                                onSelect = screenModel::setBatchSize,
                            )
                        }

                        // KMK v0.8.14: Phase E -- skip/explicit toggles and candidate diagnostics are
                        // secondary setup detail (not needed to decide whether to press Start), collapsed
                        // behind a disclosure by default. Neither is a search anchor, so hiding them by
                        // default doesn't affect ScrollToAnchorEffect.
                        item(key = "setup_options_toggle") {
                            DisclosureToggleRow(
                                label = stringResource(KMR.strings.source_evaluation_setup_options_toggle),
                                expanded = state.showSetupOptions,
                                onToggle = screenModel::toggleSetupOptions,
                            )
                        }
                        if (state.showSetupOptions) {
                            item(key = "option_skip") {
                                OptionToggleRow(
                                    label = stringResource(KMR.strings.source_evaluation_skip_evaluated),
                                    checked = state.options.skipAlreadyEvaluated,
                                    onToggle = { screenModel.setSkipAlreadyEvaluated(!state.options.skipAlreadyEvaluated) },
                                )
                            }

                            item(key = "option_explicit") {
                                OptionToggleRow(
                                    label = stringResource(KMR.strings.source_evaluation_include_explicit),
                                    checked = state.options.includeExplicitCandidates,
                                    onToggle = { screenModel.setIncludeExplicit(!state.options.includeExplicitCandidates) },
                                )
                            }

                            // KMK --> v0.6.12: candidate diagnostics
                            item(key = "candidate_diagnostics") {
                                CandidateDiagnosticsRow(
                                    diagnostics = state.candidateDiagnostics,
                                    isLoading = state.isLoadingCandidates,
                                    visibleCount = state.candidates.size,
                                    batchSize = state.options.batchSize,
                                    // KMK --> v0.6.19 follow-up
                                    skipAlreadyEvaluated = state.options.skipAlreadyEvaluated,
                                    // KMK <--
                                )
                            }
                            // KMK <--
                        }

                        // KMK v0.8.11: the safety-diagnostics and runtime-health rows moved from here
                        // to the "Diagnostics and recovery" section further down -- see diagnostics_header.

                        // KMK --> v0.6.19: low-confidence taste profile warning near start button
                        if (!state.tasteConfidence.isSufficientForPersonalizedEvaluation) {
                            item(key = "taste_confidence_warning") {
                                InfoCard(
                                    message = stringResource(KMR.strings.source_evaluation_low_confidence_warning),
                                    isError = false,
                                    modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                                )
                            }
                        }
                        // KMK <--

                        // KMK --> v0.7.31: C3 — profile changed since last eval run
                        if (state.profileChangedSinceLastEval && state.evaluations.isNotEmpty()) {
                            item(key = "profile_changed_prompt") {
                                InfoCard(
                                    message = stringResource(KMR.strings.source_evaluation_profile_changed_since_last_run),
                                    isError = false,
                                    modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                                )
                            }
                        }
                        // KMK <--

                        item(key = "start_button") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(MaterialTheme.padding.medium),
                                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                            ) {
                                val canStart = state.installerPolicy?.readiness ==
                                    SourceEvaluationInstallerPolicy.InstallerReadiness.READY &&
                                    state.candidates.isNotEmpty()
                                Button(
                                    onClick = screenModel::startEvaluation,
                                    enabled = canStart,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                                    Text(
                                        stringResource(
                                            KMR.strings.source_evaluation_start,
                                            state.candidates.size.coerceAtMost(state.options.batchSize),
                                        ),
                                        modifier = Modifier.padding(start = MaterialTheme.padding.extraSmall),
                                    )
                                }
                            }
                        }

                        // KMK --> v0.7.6: continue next batch button
                        if (state.canContinue && !state.queueState.isRunning) {
                            item(key = "continue_button") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = MaterialTheme.padding.medium),
                                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                                ) {
                                    val canContinue = state.installerPolicy?.readiness ==
                                        SourceEvaluationInstallerPolicy.InstallerReadiness.READY
                                    OutlinedButton(
                                        onClick = screenModel::continueEvaluation,
                                        enabled = canContinue,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(
                                            stringResource(
                                                KMR.strings.source_evaluation_continue_next_batch,
                                                state.remainingCandidateCount,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                        // KMK <--

                        // KMK v0.8.11: "Installer and cleanup" section -- installer mode, Shizuku setup,
                        // and installer-related warnings, grouped instead of interleaved with run options.
                        item(key = "installer_header") {
                            SectionHeader(stringResource(KMR.strings.source_evaluation_section_installer))
                        }

                        // KMK v0.8.14: Phase E -- the installer-mode selector is secondary setup detail once
                        // the installer is already ready; force it visible (not collapsible) whenever it
                        // isn't ready, since that's exactly the critical-setup case the plan says must stay
                        // visible. "installer_header" remains the search anchor either way.
                        val installerReady = state.installerPolicy?.readiness ==
                            SourceEvaluationInstallerPolicy.InstallerReadiness.READY
                        val showInstallerModeSelector = state.showInstallerDetails || !installerReady
                        if (installerReady) {
                            item(key = "installer_details_toggle") {
                                DisclosureToggleRow(
                                    label = stringResource(KMR.strings.source_evaluation_installer_details_toggle),
                                    expanded = state.showInstallerDetails,
                                    onToggle = screenModel::toggleInstallerDetails,
                                )
                            }
                        }
                        if (showInstallerModeSelector) {
                            item(key = "installer_mode") {
                                InstallerModeSelector(
                                    selected = state.options.installerMode,
                                    policy = state.installerPolicy,
                                    onSelect = screenModel::setInstallerMode,
                                )
                            }
                        }

                        // KMK --> v0.6.11: Shizuku setup card
                        // KMK --> v0.6.19: only show when Shizuku is selected/relevant or user expanded it
                        if (state.showShizukuSetup) {
                            item(key = "shizuku_setup") {
                                ShizukuSetupCard(
                                    shizukuState = state.shizukuState,
                                    installerMode = state.options.installerMode,
                                    isEvaluationRunning = state.queueState.isRunning,
                                    onInstallShizuku = screenModel::openShizukuSetup,
                                    onOpenShizuku = screenModel::openShizukuApp,
                                    onUseForRun = screenModel::useShizukuForEvaluation,
                                    onStopUsing = screenModel::stopUsingShizukuForEvaluation,
                                    onUninstall = screenModel::uninstallShizuku,
                                    onRefresh = screenModel::refreshShizukuState,
                                    // KMK --> v0.6.13: show "Use Private instead" when Shizuku is selected
                                    privateAvailable = state.privateAvailable,
                                    onUsePrivateInstead = screenModel::switchToPrivate,
                                    // KMK <--
                                )
                            }
                        } else {
                            item(key = "shizuku_setup_toggle") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = MaterialTheme.padding.medium),
                                ) {
                                    TextButton(onClick = screenModel::showShizukuSetup) {
                                        Text(
                                            stringResource(KMR.strings.source_evaluation_shizuku_setup_toggle),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        // KMK <-- v0.6.19
                        // KMK <-- v0.6.11

                        val currentPolicy = state.installerPolicy
                        if (currentPolicy?.requiresPromptWarning == true) {
                            item(key = "prompt_warning") {
                                InfoCard(
                                    message = currentPolicy.messageKey?.toLocalString()
                                        ?: stringResource(KMR.strings.source_evaluation_installer_warning),
                                    modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                                )
                            }
                        }

                        if (currentPolicy?.readiness == SourceEvaluationInstallerPolicy.InstallerReadiness.UNAVAILABLE) {
                            item(key = "unavailable_warning") {
                                InfoCard(
                                    message = currentPolicy.messageKey?.toLocalString()
                                        ?: stringResource(KMR.strings.source_evaluation_installer_unavailable),
                                    isError = true,
                                    modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                                )
                            }
                        }

                        // KMK v0.8.11: "Reassessment" section -- every reassess flavor (current tastes,
                        // updated extensions, stale/outdated) in one place.
                        item(key = "reassess_header") {
                            SectionHeader(stringResource(KMR.strings.source_evaluation_section_reassessment))
                        }

                        // KMK --> v0.6.20: reassessment prompt
                        if (ratingsSinceBaseline >= REASSESSMENT_THRESHOLD) {
                            item(key = "reassessment_prompt") {
                                InfoCard(
                                    message = stringResource(
                                        KMR.strings.source_evaluation_reassessment_recommended,
                                        ratingsSinceBaseline,
                                    ),
                                    isError = false,
                                    modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                                )
                            }
                        }

                        item(key = "reassess_button") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = MaterialTheme.padding.medium),
                                horizontalArrangement = Arrangement.Start,
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        screenModel.setSkipAlreadyEvaluated(false)
                                        screenModel.startEvaluation()
                                    },
                                    enabled = state.installerPolicy?.readiness ==
                                        SourceEvaluationInstallerPolicy.InstallerReadiness.READY &&
                                        !state.queueState.isRunning,
                                ) {
                                    Text(
                                        if (ratingsSinceBaseline >= REASSESSMENT_THRESHOLD) {
                                            stringResource(KMR.strings.source_evaluation_reassess_button)
                                        } else {
                                            stringResource(KMR.strings.source_evaluation_reassess_current_tastes)
                                        },
                                    )
                                }
                            }
                        }
                        // KMK <--

                        // KMK --> v0.7.4: updated evaluated extensions notice + reassess button
                        if (state.updatedEvaluatedExtensionCount > 0) {
                            item(key = "updated_evaluated_notice") {
                                InfoCard(
                                    message = stringResource(
                                        KMR.strings.source_evaluation_updated_extensions_notice,
                                        state.updatedEvaluatedExtensionCount,
                                    ),
                                    isError = false,
                                    modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                                )
                            }
                            item(key = "reassess_updated_button") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = MaterialTheme.padding.medium),
                                    horizontalArrangement = Arrangement.Start,
                                ) {
                                    OutlinedButton(
                                        onClick = screenModel::startReassessUpdated,
                                        enabled = state.installerPolicy?.readiness ==
                                            SourceEvaluationInstallerPolicy.InstallerReadiness.READY &&
                                            !state.queueState.isRunning,
                                    ) {
                                        Text(stringResource(KMR.strings.source_evaluation_reassess_updated_button))
                                    }
                                }
                            }
                        }
                        // KMK <--

                        // KMK v0.8.1-fix4: state-derived completion feedback -- shown only once a stale
                        // reassessment run has actually happened (continuationCursorStale != null) and
                        // nothing remains, so it naturally disappears again once new stale work appears.
                        if (staleCompletionState != SourceEvaluationStaleCompletionDisplayPolicy.DisplayState.Hidden) {
                            item(key = "stale_reassess_complete") {
                                InfoCard(
                                    // KMK v0.8.13-fix1: distinguish "everything actionable reassessed,
                                    // nothing excluded" from "everything actionable reassessed, but N
                                    // outdated rows remain excluded" -- a generic "complete" message next
                                    // to an "N of M outdated can't be reassessed" note previously read as
                                    // if the excluded rows had also been processed.
                                    message = if (staleCompletionState == SourceEvaluationStaleCompletionDisplayPolicy.DisplayState.CompletedWithUnreachableRemaining) {
                                        stringResource(
                                            KMR.strings.source_evaluation_stale_reassess_complete_partial,
                                            state.outdatedReconciliation.unreachableOutdatedCount,
                                        )
                                    } else {
                                        stringResource(KMR.strings.source_evaluation_stale_reassess_complete)
                                    },
                                    isError = false,
                                    modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                                )
                            }
                        }
                        // KMK <--

                        // KMK --> v0.8.1-fix3: stale/outdated reassessment queue — a first-class,
                        // independently continuable action, distinct from the unassessed queue's
                        // start/continue buttons above. Only shown when there is something actionable.
                        // KMK v0.8.14-fix1: the count here (state.staleCandidates.size) was already
                        // actionable-only -- "Reassess outdated (N)" has never included excluded/
                        // unreachable sources in its count. This pass moves the primary action ahead of
                        // the excluded-sources note below (previously the note appeared first and read as
                        // if it were blocking the action) and demotes that note to a collapsed disclosure.
                        if (state.staleCandidates.isNotEmpty() && !state.queueState.isRunning) {
                            item(key = "stale_reassess_button") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = MaterialTheme.padding.medium),
                                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                                ) {
                                    val ready = state.installerPolicy?.readiness ==
                                        SourceEvaluationInstallerPolicy.InstallerReadiness.READY
                                    OutlinedButton(
                                        onClick = screenModel::startOrContinueStaleReassessment,
                                        enabled = ready,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(
                                            if (state.canContinueStale) {
                                                stringResource(
                                                    KMR.strings.source_evaluation_stale_reassess_continue,
                                                    state.remainingStaleCandidateCount,
                                                )
                                            } else {
                                                stringResource(
                                                    KMR.strings.source_evaluation_stale_reassess_start,
                                                    state.staleCandidates.size,
                                                )
                                            },
                                        )
                                    }
                                    if (state.canContinueStale) {
                                        TextButton(onClick = screenModel::restartStaleReassessment, enabled = ready) {
                                            Text(
                                                stringResource(KMR.strings.source_evaluation_stale_reassess_restart),
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        // KMK <--

                        // KMK v0.8.20-fix1: the excluded-outdated disclosure (a collapsed "N skipped
                        // source(s)" toggle plus an explanatory "can't be reassessed here"/"none can be
                        // reassessed" note) was removed from this primary workflow. The reassessment
                        // queue and its button already use only actionable candidates
                        // (state.staleCandidates, itself built from the eligible pool -- see
                        // SourceEvaluationCandidateQueuePolicy.staleCandidates), so excluded sources never
                        // inflated the action count in the first place; this disclosure only ever
                        // described sources the action was never going to touch, which read as a warning
                        // or blocker even though nothing was actually blocked. The underlying
                        // eligibility/reconciliation logic (SourceEvaluationOutdatedReconciliation) is
                        // preserved -- it still powers the honest post-run completion message just below
                        // (which must keep distinguishing "complete" from "complete, N still excluded")
                        // and each row's neutral isActionableOutdated label -- only this pre-run warning
                        // surface was removed.
                        // KMK <--
                    }

                    // KMK v0.8.11: "For You search compatibility" section -- moved out of the
                    // past-evaluations block so it reads as its own concept, with an explainer that
                    // distinguishes it from catalogue Source Evaluation. Content/actions unchanged.
                    if (recQualityQueue.totalPromising > 0) {
                        item(key = "rec_quality_section") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.extraSmall),
                                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                            ) {
                                Text(
                                    text = stringResource(KMR.strings.source_evaluation_rec_quality_section_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                // KMK v0.8.11: plain-language boundary between this check and the
                                // catalogue evaluation above.
                                Text(
                                    text = stringResource(KMR.strings.source_evaluation_rec_quality_explainer),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                // KMK --> v0.7.45: public-safety cleanup fix — non-installed probes
                                // are refused (not silently temp-installed via Shizuku/Current) when
                                // Private is unavailable; make that visible instead of only showing
                                // per-source errors.
                                if (state.recQualityNonInstalledSkippedCount > 0) {
                                    Text(
                                        text = stringResource(
                                            KMR.strings.source_evaluation_rec_quality_private_required_message,
                                            state.recQualityNonInstalledSkippedCount,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                                // KMK <--
                                if (state.recQualityRunning) {
                                    // KMK --> v0.7.43: background job — keep running after leaving screen
                                    Text(
                                        text = stringResource(
                                            KMR.strings.source_evaluation_rec_quality_running,
                                            state.recQualityProgress,
                                            state.recQualityTotal,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    state.recQualityCurrentSourceName?.let { name ->
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    OutlinedButton(onClick = { screenModel.cancelRecommendationQualityCheck() }) {
                                        Text(stringResource(KMR.strings.source_evaluation_rec_quality_cancel))
                                    }
                                    // KMK <--
                                } else {
                                    // KMK --> v0.7.42-fix2: show missing and outdated counts separately
                                    if (recQualityQueue.missingCount > 0) {
                                        Text(
                                            text = stringResource(
                                                KMR.strings.source_evaluation_rec_quality_missing,
                                                recQualityQueue.missingCount,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (recQualityQueue.outdatedCount > 0) {
                                        Text(
                                            text = stringResource(
                                                KMR.strings.source_evaluation_rec_quality_outdated_count,
                                                recQualityQueue.outdatedCount,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    // KMK v0.7.44 Phase G.2: FlowRow so up to 3 buttons wrap on narrow phones
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                                    ) {
                                        if (recQualityQueue.missingCount > 0) {
                                            OutlinedButton(
                                                onClick = { screenModel.evaluateRecommendationQualityForPromising(reCheckAll = false) },
                                            ) {
                                                Text(stringResource(KMR.strings.source_evaluation_rec_quality_evaluate))
                                            }
                                        }
                                        if (recQualityQueue.outdatedCount > 0) {
                                            OutlinedButton(
                                                onClick = { screenModel.recheckOutdatedRecommendationQuality() },
                                            ) {
                                                Text(stringResource(KMR.strings.source_evaluation_rec_quality_recheck_outdated))
                                            }
                                        }
                                        if (recQualityQueue.checkedPromising.isNotEmpty()) {
                                            TextButton(
                                                onClick = { screenModel.evaluateRecommendationQualityForPromising(reCheckAll = true) },
                                            ) {
                                                Text(
                                                    stringResource(KMR.strings.source_evaluation_rec_quality_recheck_all),
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }
                                        }
                                    }
                                    // KMK <--
                                    // KMK --> v0.7.13: compact diagnostics summary after checks run
                                    val diag = state.recQualityDiagnostics
                                    if (diag != null && diag.hasAnyResults) {
                                        Text(
                                            text = stringResource(
                                                KMR.strings.source_evaluation_rec_quality_diagnostics,
                                                diag.checkedCount,
                                                diag.installLoadIssueCount,
                                                diag.searchErrorCount,
                                                diag.noResultsCount,
                                                diag.weakCount,
                                                diag.goodCount,
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    // KMK <--
                                }
                            }
                        }
                    }
                    // KMK <--

                    // KMK v0.8.11: "Diagnostics and recovery" section -- safety diagnostics, runtime
                    // health, copy-diagnostics/view-warning, and the management actions, gathered at the
                    // end instead of interleaved with run controls. Content/actions unchanged.
                    item(key = "diagnostics_header") {
                        SectionHeader(stringResource(KMR.strings.source_evaluation_section_diagnostics))
                    }

                    if (queueState.isIdle) {
                        // KMK --> v0.6.19: safety diagnostics row (demoted from top-of-screen cards)
                        val unsafeCount = state.unsafeSources.size
                        val blockedCount = state.blockedPackages.size
                        if (unsafeCount > 0 || blockedCount > 0) {
                            item(key = "safety_diagnostics") {
                                SafetyDiagnosticsRow(
                                    unsafeCount = unsafeCount,
                                    blockedCount = blockedCount,
                                    onViewUnsafe = screenModel::showUnsafeSourcesDialog,
                                    onViewBlocked = screenModel::showBlockedPackagesDialog,
                                    modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                                )
                            }
                        }
                        // KMK <--

                        // KMK v0.8.10-fix5: non-blocking source-runtime health warning
                        if (state.runtimeHealthIssues.isNotEmpty()) {
                            item(key = "runtime_health_warning") {
                                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.padding.medium)) {
                                    Row(
                                        modifier = Modifier.padding(MaterialTheme.padding.medium),
                                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Error,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                        Text(
                                            text = stringResource(KMR.strings.source_evaluation_runtime_health_warning),
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f),
                                        )
                                        TextButton(onClick = screenModel::showRuntimeHealthDialog) {
                                            Text(
                                                stringResource(KMR.strings.source_evaluation_runtime_health_details),
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        // KMK <--

                        // KMK --> v0.6.16: copy diagnostics button
                        item(key = "copy_diagnostics") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = MaterialTheme.padding.medium),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                // KMK --> v0.7.11: re-surface consent warning without starting evaluation
                                TextButton(onClick = screenModel::showConsentWarning) {
                                    Text(
                                        stringResource(KMR.strings.source_evaluation_view_warning),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                // KMK <--
                                TextButton(onClick = screenModel::copyDiagnosticsToClipboard) {
                                    Text(
                                        stringResource(KMR.strings.source_evaluation_diagnostics_copy),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                        // KMK <--
                    }

                    // KMK --> v0.6.20: management section
                    item(key = "management_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { screenModel.toggleManagementSection() }
                                .padding(
                                    horizontal = MaterialTheme.padding.medium,
                                    vertical = MaterialTheme.padding.small,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(KMR.strings.source_evaluation_management_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Icon(
                                if (state.showManagementSection) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = null,
                            )
                        }
                    }
                    if (state.showManagementSection) {
                        item(key = "management_reset_disliked") {
                            TextButton(
                                onClick = { screenModel.requestManagementAction(SourceEvaluationScreenModel.ManagementAction.RESET_DISLIKED_SOURCES) },
                                modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                            ) {
                                Text(stringResource(KMR.strings.source_evaluation_reset_disliked_sources))
                            }
                        }
                        item(key = "management_reset_baseline") {
                            TextButton(
                                onClick = { screenModel.requestManagementAction(SourceEvaluationScreenModel.ManagementAction.RESET_REASSESSMENT_BASELINE) },
                                modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                            ) {
                                Text(stringResource(KMR.strings.source_evaluation_reset_reassessment_baseline))
                            }
                        }
                        item(key = "management_clear_seen") {
                            TextButton(
                                onClick = { screenModel.requestManagementAction(SourceEvaluationScreenModel.ManagementAction.CLEAR_SEEN_MANGA) },
                                modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                            ) {
                                Text(stringResource(KMR.strings.source_evaluation_clear_seen_manga))
                            }
                        }
                        // KMK --> v0.7.18: hidden suggestion management
                        if (state.dismissedSuggestionCount > 0) {
                            item(key = "management_clear_dismissed") {
                                TextButton(
                                    onClick = { screenModel.requestManagementAction(SourceEvaluationScreenModel.ManagementAction.CLEAR_DISMISSED_SUGGESTIONS) },
                                    modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                                ) {
                                    Text(stringResource(KMR.strings.source_evaluation_clear_dismissed_suggestions, state.dismissedSuggestionCount))
                                }
                            }
                        }
                        if (state.dislikedSuggestionCount > 0) {
                            item(key = "management_clear_disliked_suggestions") {
                                TextButton(
                                    onClick = { screenModel.requestManagementAction(SourceEvaluationScreenModel.ManagementAction.CLEAR_DISLIKED_SUGGESTIONS) },
                                    modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                                ) {
                                    Text(stringResource(KMR.strings.source_evaluation_clear_disliked_suggestion_sources, state.dislikedSuggestionCount))
                                }
                            }
                        }
                        // KMK <--
                    }
                    // KMK <--

                    // Past evaluations
                    // The empty illustration belongs only to Past evaluations. Setup controls,
                    // diagnostics, warnings, and running/summary states remain visible elsewhere.
                    if (state.evaluations.isEmpty() && queueState.isIdle) {
                        item(key = "past_evaluations_empty") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(MaterialTheme.padding.medium),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                KmkEmptyStateIllustration(
                                    artwork = KmkEmptyStateArtwork.SOURCE_EVALUATION,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(96.dp),
                                )
                                Text(
                                    text = stringResource(KMR.strings.source_evaluation_empty),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = MaterialTheme.padding.small),
                                )
                            }
                        }
                    }

                    if (state.evaluations.isNotEmpty()) {
                        item(key = "eval_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = MaterialTheme.padding.medium,
                                        vertical = MaterialTheme.padding.small,
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = stringResource(KMR.strings.source_evaluation_past_results),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                // KMK --> v0.6.15: request confirmation before clearing
                                TextButton(onClick = screenModel::requestClearAllEvaluations) {
                                    Text(stringResource(KMR.strings.source_evaluation_clear_all))
                                }
                                // KMK <--
                            }
                        }

                        // KMK --> v0.7.6: show installed toggle + hidden count
                        // KMK --> v0.7.7: condition fixed — also show when showInstalled=true so user can toggle back
                        if (state.hiddenInstalledCount > 0 || state.showInstalled) {
                            item(key = "show_installed_row") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.extraSmall),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                                ) {
                                    FilterChip(
                                        selected = state.showInstalled,
                                        onClick = { screenModel.setShowInstalled(!state.showInstalled) },
                                        label = {
                                            Text(
                                                if (state.showInstalled) {
                                                    stringResource(KMR.strings.source_evaluation_hide_installed)
                                                } else {
                                                    stringResource(KMR.strings.source_evaluation_show_installed)
                                                },
                                            )
                                        },
                                    )
                                    if (state.hiddenInstalledCount > 0 && !state.showInstalled) {
                                        Text(
                                            text = stringResource(KMR.strings.source_evaluation_hidden_installed, state.hiddenInstalledCount),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        // KMK <-- v0.7.7
                        // KMK <-- v0.7.6

                        // KMK v0.8.1-fix4: show/hide source-quality-disliked rows toggle + hidden count
                        if (state.hiddenSourceQualityCount > 0 || state.showSourceQualityDisliked) {
                            item(key = "show_quality_disliked_row") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.extraSmall),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                                ) {
                                    FilterChip(
                                        selected = state.showSourceQualityDisliked,
                                        onClick = { screenModel.setShowSourceQualityDisliked(!state.showSourceQualityDisliked) },
                                        label = {
                                            Text(
                                                if (state.showSourceQualityDisliked) {
                                                    stringResource(KMR.strings.source_quality_hide_disliked_sources)
                                                } else {
                                                    stringResource(KMR.strings.source_quality_show_disliked_sources)
                                                },
                                            )
                                        },
                                    )
                                    if (state.hiddenSourceQualityCount > 0 && !state.showSourceQualityDisliked) {
                                        Text(
                                            text = stringResource(KMR.strings.source_quality_hidden_mark_count, state.hiddenSourceQualityCount),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        // KMK <--

                        // KMK v0.8.11: the recommendation-quality ("For You search compatibility")
                        // section moved out of this past-evaluations block -- see rec_quality_section
                        // above the diagnostics_header.

                        // KMK --> v0.6.15: sort controls for past evaluation results
                        item(key = "eval_sort") {
                            var sortExpanded by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.extraSmall),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                            ) {
                                Text(
                                    text = stringResource(KMR.strings.source_evaluation_sort_label),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Box {
                                    TextButton(onClick = { sortExpanded = true }) {
                                        Text(
                                            text = when (state.resultSortMode) {
                                                SourceEvaluationResultList.SortMode.BEST_FIT -> stringResource(KMR.strings.source_evaluation_sort_best_fit)
                                                SourceEvaluationResultList.SortMode.NEWEST -> stringResource(KMR.strings.source_evaluation_sort_newest)
                                                SourceEvaluationResultList.SortMode.SOURCE_NAME -> stringResource(KMR.strings.source_evaluation_sort_source_name)
                                                SourceEvaluationResultList.SortMode.EXTENSION_NAME -> stringResource(KMR.strings.source_evaluation_sort_extension_name)
                                                SourceEvaluationResultList.SortMode.FOR_YOU_COMPATIBILITY -> stringResource(KMR.strings.source_evaluation_sort_for_you_compatibility)
                                                SourceEvaluationResultList.SortMode.EXPLICIT_RISK -> stringResource(KMR.strings.source_evaluation_sort_explicit_risk)
                                            },
                                        )
                                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                                    }
                                    DropdownMenu(
                                        expanded = sortExpanded,
                                        onDismissRequest = { sortExpanded = false },
                                    ) {
                                        SourceEvaluationResultList.SortMode.entries.forEach { mode ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        when (mode) {
                                                            SourceEvaluationResultList.SortMode.BEST_FIT -> stringResource(KMR.strings.source_evaluation_sort_best_fit)
                                                            SourceEvaluationResultList.SortMode.NEWEST -> stringResource(KMR.strings.source_evaluation_sort_newest)
                                                            SourceEvaluationResultList.SortMode.SOURCE_NAME -> stringResource(KMR.strings.source_evaluation_sort_source_name)
                                                            SourceEvaluationResultList.SortMode.EXTENSION_NAME -> stringResource(KMR.strings.source_evaluation_sort_extension_name)
                                                            SourceEvaluationResultList.SortMode.FOR_YOU_COMPATIBILITY -> stringResource(KMR.strings.source_evaluation_sort_for_you_compatibility)
                                                            SourceEvaluationResultList.SortMode.EXPLICIT_RISK -> stringResource(KMR.strings.source_evaluation_sort_explicit_risk)
                                                        },
                                                    )
                                                },
                                                onClick = {
                                                    screenModel.setResultSortMode(mode)
                                                    sortExpanded = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        // KMK <--

                        // KMK --> v0.6.15: itemsIndexed with stable keys; no animateItem() (reorder stability)
                        itemsIndexed(
                            items = sortedEvaluations,
                            key = { index, evaluation -> SourceEvaluationResultList.stableUiKey(evaluation, index) },
                        ) { _, evaluation ->
                            // KMK --> v0.7.6: pass rec-quality fit for display
                            // KMK v0.8.1-fix4: source/library-quality key is extension-level ("a|sig|pkg")
                            val evalQualityKey = "a|${evaluation.extensionKey}"
                            // KMK v0.8.15-fix1: per-row Install eligibility -- see
                            // SourceEvaluationRowActionPolicy and SourceEvaluationScreenModel
                            // .installEvaluatedSource().
                            val isBlocked = evaluation.extensionKey in state.unsafeSources.map { "${it.signatureHash}|${it.extensionPkgName}" } ||
                                state.blockedPackages.any { it.pkgName == evaluation.extensionPkgName }
                            val installEligibility = SourceEvaluationRowActionPolicy.installEligibility(
                                isInstalled = evaluation.extensionKey in state.installedExtensionKeys,
                                isBlocked = isBlocked,
                                isAvailable = evaluation.extensionKey in state.availableExtensionKeys,
                            )
                            EvaluationResultRow(
                                evaluation = evaluation,
                                recFit = state.recommendationFitsByEvalKey[evaluation.evaluationKey],
                                // KMK --> v0.7.42-fix2: shared `now` so row labels agree with the queue/sort
                                now = now,
                                // KMK <--
                                isQualityDisliked = evalQualityKey in state.qualityDislikedSourceKeys,
                                isQualityExplicit = evalQualityKey in state.qualityExplicitSourceKeys,
                                onMarkQualityPoor = { screenModel.markSourceQualityPoor(evaluation) },
                                onMarkQualityExplicit = { screenModel.markSourceQualityExplicit(evaluation) },
                                onClearQualityMark = { screenModel.clearSourceQualityMark(evaluation) },
                                // KMK v0.8.14: row label policy -- only claim "reassess needed" for rows the
                                // reassessment queue can actually reach this run.
                                isActionableOutdated = evaluation.extensionKey in state.outdatedReconciliation.workableOutdatedExtensionKeys,
                                // KMK v0.8.15-fix1
                                installEligibility = installEligibility,
                                isInstalling = evaluation.extensionPkgName in state.installingPkgNames,
                                onInstall = { screenModel.installEvaluatedSource(evaluation.extensionPkgName, evaluation.signatureHash) },
                            )
                            // KMK <--
                        }
                        // KMK <--
                    }

                    item(key = "bottom_spacer") { Spacer(Modifier.height(MaterialTheme.padding.medium)) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
            horizontal = MaterialTheme.padding.medium,
            vertical = MaterialTheme.padding.small,
        ),
    )
}

@Composable
private fun EvaluationProgressCard(
    queueState: SourceEvaluationQueueState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val evaluationModeEnabled = rememberEvaluationModeEnabled()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(MaterialTheme.padding.medium),
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.padding.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                CircularProgressIndicator(modifier = Modifier.padding(end = MaterialTheme.padding.extraSmall))
                Text(
                    text = queueState.currentExtensionName?.let {
                        if (evaluationModeEnabled) EvaluationModeFormatter.sourceLabel("ext:$it") else it
                    } ?: stringResource(KMR.strings.source_evaluation_starting),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            queueState.currentSourceName?.let { sourceName ->
                Text(
                    text = if (evaluationModeEnabled) EvaluationModeFormatter.sourceLabel(sourceName) else sourceName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = "${queueState.currentPhase.name.replace(Regex("([A-Z])")) { " ${it.value}" }.trim()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val progress = if (queueState.totalCount > 0) {
                queueState.completedCount.toFloat() / queueState.totalCount.toFloat()
            } else {
                null
            }

            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "${queueState.completedCount} / ${queueState.totalCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(Icons.Outlined.Stop, contentDescription = null)
                Text(
                    stringResource(KMR.strings.source_evaluation_cancel),
                    modifier = Modifier.padding(start = MaterialTheme.padding.extraSmall),
                )
            }
        }
    }
}

@Composable
private fun EvaluationSummaryCard(
    queueState: SourceEvaluationQueueState,
    onReset: () -> Unit,
    // KMK --> SEC-02 v0.7.16
    onCompleteCleanup: () -> Unit = {},
    // KMK <--
    // KMK --> v0.7.31: C2 — one-tap retry after connectivity loss
    onRetry: (() -> Unit)? = null,
    // KMK <--
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(MaterialTheme.padding.medium),
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.padding.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            // KMK --> v0.7.18: ConnectivityLost also gets error icon
            // KMK v0.8.15: NoActionableWork is not an error (nothing crashed or failed to install) --
            // it's an honest "there was nothing this run could actually do" state, so it gets its own
            // informational icon rather than either the success or error icon.
            val icon = when (queueState.status) {
                SourceEvaluationQueueState.Status.Failed,
                SourceEvaluationQueueState.Status.ConnectivityLost,
                -> Icons.Outlined.Error
                SourceEvaluationQueueState.Status.NoActionableWork -> Icons.Outlined.Info
                else -> Icons.Outlined.CheckCircle
            }
            // KMK <--
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                Icon(icon, contentDescription = null)
                Text(
                    text = when (queueState.status) {
                        SourceEvaluationQueueState.Status.Completed ->
                            stringResource(KMR.strings.source_evaluation_completed)
                        SourceEvaluationQueueState.Status.Cancelled ->
                            stringResource(KMR.strings.source_evaluation_cancelled)
                        SourceEvaluationQueueState.Status.Failed ->
                            stringResource(KMR.strings.source_evaluation_failed)
                        // KMK --> v0.7.18
                        SourceEvaluationQueueState.Status.ConnectivityLost ->
                            stringResource(KMR.strings.source_evaluation_connectivity_lost)
                        // KMK <--
                        // KMK v0.8.15
                        SourceEvaluationQueueState.Status.NoActionableWork ->
                            stringResource(KMR.strings.source_evaluation_no_actionable_work)
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (queueState.strongFitCount > 0) {
                Text(stringResource(KMR.strings.source_evaluation_strong_fit_count, queueState.strongFitCount))
            }
            if (queueState.worthTryingCount > 0) {
                Text(stringResource(KMR.strings.source_evaluation_worth_trying_count, queueState.worthTryingCount))
            }
            if (queueState.explicitHeavyCount > 0) {
                Text(stringResource(KMR.strings.source_evaluation_explicit_count, queueState.explicitHeavyCount))
            }
            if (queueState.ecchiHeavyCount > 0) {
                Text(stringResource(KMR.strings.source_evaluation_ecchi_count, queueState.ecchiHeavyCount))
            }
            if (queueState.errorCount > 0) {
                Text(
                    text = stringResource(KMR.strings.source_evaluation_error_count, queueState.errorCount),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // KMK --> v0.7.11: cleanup outcome warnings
            if (queueState.promptRequiredCleanupCount > 0) {
                Text(
                    text = stringResource(
                        KMR.strings.source_evaluation_cleanup_prompt_required_warning,
                        queueState.promptRequiredCleanupCount,
                    ),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                // KMK --> SEC-02 v0.7.16: one-tap button to trigger system uninstall prompts
                TextButton(onClick = onCompleteCleanup) {
                    Text(
                        stringResource(
                            KMR.strings.source_evaluation_complete_cleanup,
                            queueState.promptRequiredCleanupCount,
                        ),
                    )
                }
                // KMK <--
            }
            if (queueState.cleanupFailedCount > 0) {
                Text(
                    text = stringResource(
                        KMR.strings.source_evaluation_cleanup_failed_warning,
                        queueState.cleanupFailedCount,
                    ),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            // KMK <--
            // KMK v0.8.15-fix1: honest non-fatal note when the stale-row delete-before-upsert step
            // itself failed for one or more sources -- see recordExtensionError().
            if (queueState.reconciliationFailedCount > 0) {
                Text(
                    text = stringResource(
                        KMR.strings.source_evaluation_reconciliation_failed_warning,
                        queueState.reconciliationFailedCount,
                    ),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // KMK --> v0.7.31: C2 — retry button after connectivity loss
            if (queueState.status == SourceEvaluationQueueState.Status.ConnectivityLost && onRetry != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onReset) {
                        Text(stringResource(KMR.strings.source_evaluation_reset))
                    }
                    TextButton(onClick = onRetry) {
                        Text(stringResource(KMR.strings.source_evaluation_retry_connectivity))
                    }
                }
            } else {
                TextButton(onClick = onReset, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(KMR.strings.source_evaluation_reset))
                }
            }
            // KMK <--
        }
    }
}

@Composable
private fun BatchSizeSelector(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = MaterialTheme.padding.medium)) {
        Text(
            text = stringResource(KMR.strings.source_evaluation_batch_size),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            modifier = Modifier.padding(top = MaterialTheme.padding.extraSmall),
        ) {
            listOf(10, 25, 50, 100).forEach { size ->
                FilterChip(
                    selected = selected == size,
                    onClick = {
                        onSelect(size)
                    },
                    label = {
                        Text(
                            if (size == 100) "$size ⚠" else "$size",
                        )
                    },
                )
            }
        }
        if (selected == 100) {
            Text(
                text = stringResource(KMR.strings.source_evaluation_large_batch_warning),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = MaterialTheme.padding.extraSmall),
            )
        }
    }
}

@Composable
private fun SourceEvaluationInstallerPolicy.InstallerPolicyMessage.toLocalString(): String = when (this) {
    SourceEvaluationInstallerPolicy.InstallerPolicyMessage.PRIVATE_READY ->
        stringResource(KMR.strings.source_evaluation_installer_private_ready)
    SourceEvaluationInstallerPolicy.InstallerPolicyMessage.PRIVATE_UNAVAILABLE ->
        stringResource(KMR.strings.source_evaluation_installer_private_unavailable)
    SourceEvaluationInstallerPolicy.InstallerPolicyMessage.SHIZUKU_READY ->
        stringResource(KMR.strings.source_evaluation_installer_shizuku_ready)
    SourceEvaluationInstallerPolicy.InstallerPolicyMessage.SHIZUKU_NOT_INSTALLED ->
        stringResource(KMR.strings.source_evaluation_installer_shizuku_not_installed)
    SourceEvaluationInstallerPolicy.InstallerPolicyMessage.SHIZUKU_NOT_RUNNING ->
        stringResource(KMR.strings.source_evaluation_installer_shizuku_not_running)
    SourceEvaluationInstallerPolicy.InstallerPolicyMessage.SHIZUKU_NEEDS_PERMISSION ->
        stringResource(KMR.strings.source_evaluation_installer_shizuku_needs_permission)
    SourceEvaluationInstallerPolicy.InstallerPolicyMessage.CURRENT_SHIZUKU_UNAVAILABLE ->
        stringResource(KMR.strings.source_evaluation_installer_current_shizuku_unavailable)
    SourceEvaluationInstallerPolicy.InstallerPolicyMessage.CURRENT_PROMPT_HEAVY ->
        stringResource(KMR.strings.source_evaluation_installer_current_prompt_heavy)
}

@Composable
private fun InstallerModeSelector(
    selected: SourceEvaluationInstallerPolicy.InstallerMode,
    policy: SourceEvaluationInstallerPolicy.PolicyResult?,
    onSelect: (SourceEvaluationInstallerPolicy.InstallerMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = MaterialTheme.padding.medium)) {
        Text(
            text = stringResource(KMR.strings.source_evaluation_installer_mode),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            modifier = Modifier.padding(top = MaterialTheme.padding.extraSmall),
        ) {
            SourceEvaluationInstallerPolicy.InstallerMode.entries.forEach { mode ->
                FilterChip(
                    selected = selected == mode,
                    onClick = { onSelect(mode) },
                    // KMK --> v0.6.13: label Private as recommended
                    label = {
                        Text(
                            if (mode == SourceEvaluationInstallerPolicy.InstallerMode.PRIVATE) {
                                stringResource(KMR.strings.source_evaluation_installer_private_label)
                            } else {
                                mode.name.lowercase().replaceFirstChar { it.uppercase() }
                            },
                        )
                    },
                    // KMK <--
                )
            }
        }
        policy?.messageKey?.let { key ->
            Text(
                text = key.toLocalString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (policy.readiness == SourceEvaluationInstallerPolicy.InstallerReadiness.UNAVAILABLE ||
                    policy.readiness == SourceEvaluationInstallerPolicy.InstallerReadiness.NEEDS_PERMISSION
                ) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(top = MaterialTheme.padding.extraSmall),
            )
        }
    }
}

@Composable
private fun OptionToggleRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

// KMK v0.8.14: Phase E -- shared row for the "More setup options"/"Installer details" disclosures,
// same expand/collapse affordance as the existing "Source management" section header.
@Composable
private fun DisclosureToggleRow(
    label: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Icon(
            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null,
        )
    }
}

@Composable
private fun InfoCard(
    message: String,
    isError: Boolean = false,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(MaterialTheme.padding.medium),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = if (isError) Icons.Outlined.Error else Icons.Outlined.Info,
                contentDescription = null,
                tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (onDismiss != null) {
                TextButton(onClick = onDismiss) {
                    Text(
                        stringResource(KMR.strings.source_evaluation_screen_error_dismiss),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

// KMK --> v0.6.16: quarantine card and row composables
@Composable
private fun UnsafeSourcesCard(
    unsafeCount: Int,
    unsafeHiddenCount: Int,
    onView: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(MaterialTheme.padding.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    text = stringResource(KMR.strings.source_evaluation_unsafe_sources_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (unsafeHiddenCount > 0) {
                Text(
                    text = stringResource(KMR.strings.source_evaluation_unsafe_sources_count, unsafeHiddenCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                TextButton(onClick = onView) {
                    Text(stringResource(KMR.strings.source_evaluation_unsafe_sources_view))
                }
                TextButton(onClick = onClear) {
                    Text(stringResource(KMR.strings.source_evaluation_unsafe_sources_clear))
                }
            }
        }
    }
}

@Composable
private fun UnsafeSourceRow(
    unsafe: SourceEvaluationUnsafeSource,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = MaterialTheme.padding.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = unsafe.extensionName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${unsafe.phase} • ×${unsafe.crashCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onRemove) {
            Text(
                stringResource(KMR.strings.source_evaluation_unsafe_remove_one),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

// KMK v0.8.10-fix5: one source-runtime health issue, with only the recovery actions that are
// actually possible (each callback is null when not valid for this issue).
@Composable
private fun RuntimeHealthIssueRow(
    issue: eu.kanade.tachiyomi.source.SourceRuntimeHealthIssue,
    onRetry: () -> Unit,
    onUpdate: (() -> Unit)?,
    onReinstall: (() -> Unit)?,
    onUninstall: (() -> Unit)?,
    onDisable: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val evaluationModeEnabled = rememberEvaluationModeEnabled()
    val displayLabel = if (evaluationModeEnabled) {
        EvaluationModeFormatter.sourceLabel(issue.sourceId)
    } else {
        issue.extensionName ?: issue.sourceName
    }
    Column(modifier = modifier.fillMaxWidth().padding(vertical = MaterialTheme.padding.extraSmall)) {
        Text(
            text = displayLabel,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = stringResource(
                KMR.strings.source_evaluation_runtime_health_issue_summary,
                displayLabel,
                issue.sourceLang,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(
                KMR.strings.source_evaluation_runtime_health_count,
                issue.count,
                java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(
                    System.currentTimeMillis() - issue.lastFailureAt,
                ).let { "${it}m" },
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            TextButton(onClick = onRetry) {
                Text(stringResource(KMR.strings.source_evaluation_runtime_health_retry), style = MaterialTheme.typography.labelSmall)
            }
            if (onUpdate != null) {
                TextButton(onClick = onUpdate) {
                    Text(stringResource(KMR.strings.source_evaluation_runtime_health_update), style = MaterialTheme.typography.labelSmall)
                }
            }
            if (onReinstall != null) {
                TextButton(onClick = onReinstall) {
                    Text(stringResource(KMR.strings.source_evaluation_runtime_health_reinstall), style = MaterialTheme.typography.labelSmall)
                }
            }
            if (onUninstall != null) {
                TextButton(onClick = onUninstall) {
                    Text(stringResource(KMR.strings.source_evaluation_runtime_health_uninstall), style = MaterialTheme.typography.labelSmall)
                }
            }
            if (onDisable != null) {
                TextButton(onClick = onDisable) {
                    Text(
                        stringResource(KMR.strings.source_evaluation_runtime_health_disable),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        HorizontalDivider()
    }
}

// KMK --> v0.6.19: compact safety diagnostics row shown below options when quarantine/blocked counts are nonzero
@Composable
private fun SafetyDiagnosticsRow(
    unsafeCount: Int,
    blockedCount: Int,
    onViewUnsafe: () -> Unit,
    onViewBlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // KMK --> v0.7.29: expand/collapse toggle for quarantine section
    // KMK v0.8.13-fix1: default changed to collapsed -- this is diagnostic detail, not a control the
    // user needs on first view of the screen (plan Phase E: "quarantine/blocked counts collapsed by
    // default with detail dialogs"). The counts themselves remain visible in the row header either way.
    var expanded by rememberSaveable { mutableStateOf(false) }
    // KMK <--
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(KMR.strings.source_evaluation_safety_diagnostics_title),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // KMK --> v0.7.29: chevron icon to collapse/expand the button list
            androidx.compose.material3.IconButton(
                onClick = { expanded = !expanded },
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // KMK <--
        }
        // KMK --> v0.7.29
        if (expanded) {
            // KMK <--
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (unsafeCount > 0) {
                    TextButton(onClick = onViewUnsafe) {
                        Text(
                            stringResource(KMR.strings.source_evaluation_safety_quarantined_count, unsafeCount),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (blockedCount > 0) {
                    TextButton(onClick = onViewBlocked) {
                        Text(
                            stringResource(KMR.strings.source_evaluation_safety_blocked_count, blockedCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            // KMK --> v0.7.29
        }
        // KMK <--
    }
}
// KMK <--

// KMK --> v0.6.18: blocked extension packages card and row composables
@Composable
private fun BlockedPackagesCard(
    blockedCount: Int,
    onView: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(MaterialTheme.padding.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = stringResource(KMR.strings.source_evaluation_blocked_packages_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = stringResource(KMR.strings.source_evaluation_blocked_packages_count, blockedCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall)) {
                TextButton(onClick = onView) {
                    Text(stringResource(KMR.strings.source_evaluation_blocked_packages_view))
                }
                TextButton(onClick = onClear) {
                    Text(stringResource(KMR.strings.source_evaluation_blocked_packages_clear))
                }
            }
        }
    }
}

@Composable
private fun BlockedPackageRow(
    pkg: UnsafeExtensionPackage,
    onAllow: () -> Unit,
    // KMK --> v0.7.18: version-aware quarantine label
    hasNewerAvailable: Boolean = false,
    // KMK <--
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = MaterialTheme.padding.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pkg.extensionName ?: pkg.pkgName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pkg.pkgName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // KMK --> v0.7.18: newer-version hint
            if (hasNewerAvailable) {
                Text(
                    text = stringResource(KMR.strings.source_evaluation_blocked_package_newer_available),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            // KMK <--
        }
        if (pkg.removable) {
            TextButton(onClick = onAllow) {
                Text(
                    stringResource(KMR.strings.source_evaluation_blocked_package_allow),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
// KMK <--

// KMK --> v0.6.20: evidence strength classifier (pure, testable)
// KMK --> v0.7.15: replaced String-returning label function with enum classifier
private enum class EvidenceStrength { STRONG, MODERATE, WEAK, LOW_CONFIDENCE }

private fun evidenceStrength(evaluation: SourceEvaluation): EvidenceStrength {
    val strongSignals = evaluation.preferredTagMatchCount + evaluation.likedTitleMatchCount
    val hasGoodSearch = evaluation.searchCount >= 2 && evaluation.searchSuccessCount >= 1
    val hasGoodSample = evaluation.sampleCount >= 5
    return when {
        strongSignals >= 3 && hasGoodSearch && hasGoodSample -> EvidenceStrength.STRONG
        strongSignals >= 1 && (hasGoodSearch || hasGoodSample) -> EvidenceStrength.MODERATE
        evaluation.sampleCount >= 2 -> EvidenceStrength.WEAK
        else -> EvidenceStrength.LOW_CONFIDENCE
    }
}

private fun lastEvaluatedDaysAgo(evaluatedAt: Long): Int {
    val nowMs = System.currentTimeMillis()
    return ((nowMs - evaluatedAt) / (1000L * 60 * 60 * 24)).toInt()
}
// KMK <--
// KMK <-- v0.7.15

// KMK v0.8.17-fix1: shared Details/Errors/Install row-action item -- see the call sites below for the
// alignment complaint this fixes. Every instance gets the same icon size, label style, internal
// spacing, minimum tap height, and vertical centering, whether it is a disclosure toggle (Details/
// Errors) or a real action (Install), rather than mixing a plain `clickable` Row with a Material
// `TextButton`.
@Composable
private fun SourceEvaluationRowAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .defaultMinSize(minHeight = 32.dp)
            .padding(horizontal = MaterialTheme.padding.extraSmall, vertical = MaterialTheme.padding.extraSmall),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = contentColor,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = contentColor,
            modifier = Modifier.padding(start = MaterialTheme.padding.extraSmall),
        )
    }
}

// KMK --> v0.6.15: defensive display values, compact score subtitle, truncated error
// KMK --> v0.7.6: accept optional rec-quality fit for second-line label
@Composable
private fun EvaluationResultRow(
    evaluation: SourceEvaluation,
    recFit: SourceRecommendationFit? = null,
    // KMK --> v0.7.42-fix2: shared `now` — see call site
    now: Long = System.currentTimeMillis(),
    // KMK <--
    // KMK v0.8.1-fix4: source/library-quality axis
    isQualityDisliked: Boolean = false,
    isQualityExplicit: Boolean = false,
    onMarkQualityPoor: () -> Unit = {},
    onMarkQualityExplicit: () -> Unit = {},
    onClearQualityMark: () -> Unit = {},
    // KMK v0.8.14: whether this row's extension is in the current reassessment queue's eligible pool
    // (SourceEvaluationOutdatedReconciliation.Result.workableOutdatedExtensionKeys) -- distinguishes
    // "Outdated - reassess needed" (actionable now) from "Outdated - not included in this run"
    // (installed/language-filtered/disliked/quarantined; reassess it from elsewhere instead).
    isActionableOutdated: Boolean = true,
    // KMK v0.8.15-fix1: clarified per-row action model -- Details/Errors/Install. Install is only
    // ever rendered active when eligibility == ELIGIBLE (see SourceEvaluationRowActionPolicy); for
    // every other reason (already installed, blocked/quarantined, no longer available) the row shows
    // no Install action at all rather than a misleading disabled one.
    installEligibility: SourceEvaluationRowActionPolicy.InstallEligibility = SourceEvaluationRowActionPolicy.InstallEligibility.UNAVAILABLE,
    isInstalling: Boolean = false,
    onInstall: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // KMK --> v0.8.19: evaluation mode source/extension-name obfuscation
    val evaluationModeEnabled = rememberEvaluationModeEnabled()
    val obfuscatedLabel = if (evaluationModeEnabled) EvaluationModeFormatter.sourceLabel(evaluation.evaluationKey) else null
    val displaySource = obfuscatedLabel
        ?: evaluation.sourceName.ifBlank { stringResource(KMR.strings.source_evaluation_unknown_source) }
    val displayExt = obfuscatedLabel
        ?: evaluation.extensionName.ifBlank { stringResource(KMR.strings.source_evaluation_unknown_extension) }
    // KMK <--
    val isError = evaluation.verdict == SourceEvaluationVerdict.ERROR
    // KMK v0.8.15: hoisted so both the compact subtitle and the expanded evidence details (which
    // repeats it alongside the raw facts the subtitle used to inline) can share one computation.
    val daysAgo = lastEvaluatedDaysAgo(evaluation.evaluatedAt)
    val lastEvalStr = if (daysAgo <= 0) {
        stringResource(KMR.strings.source_evaluation_last_evaluated_today)
    } else {
        stringResource(KMR.strings.source_evaluation_last_evaluated_days, daysAgo)
    }
    // KMK --> v0.7.47: a row scored under an older SourceEvaluationKeys.CURRENT_VERSION (or expired)
    // must never read as a current verdict — it shows "Outdated - reassess needed" instead of its
    // stored fit/confidence numbers, which were computed under different scoring rules.
    val catalogueDisplayState = SourceEvaluationDisplayPolicy.state(evaluation, now)
    val isOutdatedCatalogueRow = catalogueDisplayState == SourceEvaluationDisplayPolicy.SourceEvaluationDisplayState.OUTDATED_VERSION ||
        catalogueDisplayState == SourceEvaluationDisplayPolicy.SourceEvaluationDisplayState.EXPIRED
    // KMK v0.8.15-fix1: hoisted so both the compact subtitle and the "Errors" expand block below can
    // share this classified message instead of recomputing it.
    val classifiedCatalogueErrorMessage = if (isError && !evaluation.errorMessage.isNullOrBlank()) {
        // KMK v0.7.45: errorMessage is a classified storage key for rows written after this fix
        // (SourceEvaluationProbeErrorClassifier); older rows may still hold pre-v0.7.45 raw text.
        val kind = SourceEvaluationProbeErrorKind.fromStorageKey(evaluation.errorMessage)
        if (kind != null) {
            stringResource(
                when (kind) {
                    SourceEvaluationProbeErrorKind.NETWORK_UNAVAILABLE -> KMR.strings.source_evaluation_probe_error_network
                    SourceEvaluationProbeErrorKind.TIMEOUT -> KMR.strings.source_evaluation_probe_error_timeout
                    SourceEvaluationProbeErrorKind.UNSUPPORTED -> KMR.strings.source_evaluation_probe_error_unsupported
                    SourceEvaluationProbeErrorKind.EXTENSION_INCOMPATIBLE -> KMR.strings.source_evaluation_probe_error_extension_incompatible
                    SourceEvaluationProbeErrorKind.INTERNAL -> KMR.strings.source_evaluation_probe_error_internal
                },
            )
        } else {
            // Legacy rows may contain the pre-classification value. Never re-render that stored
            // exception text; older data is treated as an internal failure category.
            stringResource(KMR.strings.source_evaluation_probe_error_internal)
        }
    } else {
        null
    }
    val subtitle = if (isOutdatedCatalogueRow) {
        if (isActionableOutdated) {
            stringResource(KMR.strings.source_evaluation_outdated_reassess_needed, displayExt)
        } else {
            stringResource(KMR.strings.source_evaluation_outdated_excluded_here, displayExt)
        }
    } else if (classifiedCatalogueErrorMessage != null) {
        stringResource(KMR.strings.source_evaluation_row_error_prefix, displayExt, classifiedCatalogueErrorMessage)
    } else {
        // KMK v0.8.15: compact main-row subtitle -- was a single dense line packing extension name,
        // language, catalogue fit percent, metadata confidence, evidence strength, and last-evaluated
        // date all at once ("ExtName • EN • catalogue fit 85% • metadata High • Moderate evidence •
        // Last evaluated today"). The verdict chip already shows the fit/status label, so this line
        // now only adds the one thing the chip doesn't: when it was last checked. The dropped facts
        // (fit percent, metadata confidence, evidence strength, language) moved into the expandable
        // evidence details below -- see the `evidenceExpanded` block.
        val (verdictLabelRes, _) = verdictLabelAndColor(evaluation.verdict, MaterialTheme.colorScheme)
        stringResource(KMR.strings.source_evaluation_row_compact_subtitle, stringResource(verdictLabelRes), lastEvalStr)
    }
    // KMK --> v0.7.6: map verdict to display label
    // KMK --> v0.7.7: also show "Not checked" for promising rows with no result yet
    // KMK --> v0.7.42-fix2: resolved through the shared SourceRecommendationFitDisplayPolicy instead
    // of a local STRONG_FIT/WORTH_TRYING check + bare `recFit != null` — a stale/expired fit can no
    // longer display as a current Great/Good/Mixed/Weak/No matches/Error result; it now truthfully
    // shows "Outdated - recheck". Ineligible rows show no compatibility label at all.
    val compatState = SourceRecommendationFitDisplayPolicy.resolve(evaluation, recFit, now)
    val recQualityLabel = when (compatState) {
        CompatibilityDisplayState.INELIGIBLE -> null
        CompatibilityDisplayState.NOT_CHECKED -> stringResource(
            KMR.strings.source_evaluation_rec_quality_label,
            stringResource(KMR.strings.source_evaluation_rec_quality_not_checked),
        )
        CompatibilityDisplayState.OUTDATED -> stringResource(
            KMR.strings.source_evaluation_rec_quality_label,
            stringResource(KMR.strings.source_evaluation_rec_quality_outdated),
        )
        else -> stringResource(
            KMR.strings.source_evaluation_rec_quality_label,
            stringResource(
                when (compatState) {
                    CompatibilityDisplayState.GREAT -> KMR.strings.source_evaluation_rec_quality_great
                    CompatibilityDisplayState.GOOD -> KMR.strings.source_evaluation_rec_quality_good
                    CompatibilityDisplayState.MIXED -> KMR.strings.source_evaluation_rec_quality_mixed
                    CompatibilityDisplayState.WEAK -> KMR.strings.source_evaluation_rec_quality_weak
                    CompatibilityDisplayState.NO_MATCHES -> KMR.strings.source_evaluation_rec_quality_no_matches
                    CompatibilityDisplayState.ERROR -> KMR.strings.source_evaluation_rec_quality_error
                    CompatibilityDisplayState.NOT_CHECKED,
                    CompatibilityDisplayState.OUTDATED,
                    CompatibilityDisplayState.INELIGIBLE,
                    -> KMR.strings.source_evaluation_rec_quality_too_little_evidence // unreachable: handled by the branches above
                },
            ),
        )
    }
    // KMK <-- v0.7.7
    // KMK <-- v0.7.6
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displaySource,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // KMK v0.8.16-fix1: bumped from bodySmall to bodyMedium -- this is the row's user-facing
            // status line (plan: "Second line: user-facing summary"), not a secondary diagnostic, and
            // the audit found it read as too small even on tablet.
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // KMK --> v0.7.6: rec-quality second line
            if (recQualityLabel != null) {
                Text(
                    text = recQualityLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // KMK --> v0.7.31: C1 — error category badge for rec-quality ERROR verdict
            // KMK --> v0.7.42-fix2: gated on the current-outcome ERROR state, not a bare recFit check
            // — a stale ERROR fit displays as Outdated above and must not also show error-kind detail.
            // KMK --> v0.7.44 Phase G.1: kindLabel now resolves to a KMR string (was hardcoded
            // English); the badge + reason-hint block below are collapsed by default on a per-row
            // basis so a screen full of error rows doesn't turn into raw exception spam.
            val failureKind = if (compatState == CompatibilityDisplayState.ERROR && recFit != null) {
                SourceRecommendationFitFailureClassifier.classify(recFit.errorMessage)
            } else {
                null
            }
            val kindLabel = failureKindLabel(failureKind)
            val recFitErrorMessage = recFit?.errorMessage
            val hasReasonHint = compatState.isCurrentOutcome && !recFitErrorMessage.isNullOrBlank()
            // KMK v0.8.15-fix1: the "Errors" action (plan-clarified name for this toggle -- see
            // source_evaluation_row_show_details/hide_details) must be visible/enabled whenever there
            // is meaningful error information, not only For You search-compatibility failures. Catalogue
            // evaluation errors (isError) now also open this section, showing the classified error
            // message instead of only the compact subtitle line above.
            val hasCatalogueError = isError && !evaluation.errorMessage.isNullOrBlank()
            val hasDetails = SourceEvaluationRowActionPolicy.hasErrorInfo(
                isCatalogueError = hasCatalogueError,
                hasSearchFailureKind = kindLabel != null,
                hasSearchReasonHint = hasReasonHint,
            )
            // KMK v0.8.16-fix1: Details/Errors/Install were three separate always-vertically-stacked
            // rows. Their toggle/action headers now share one FlowRow so they sit side by side and
            // wrap onto a new line on narrow width instead of each always claiming its own line --
            // the expanded content for each still renders below, driven by the same hoisted state.
            var expanded by rememberSaveable(evaluation.evaluationKey) { mutableStateOf(false) }
            val evidence = SourceEvaluationEvidenceSummaryPolicy.evidenceFor(evaluation, catalogueDisplayState)
            var evidenceExpanded by rememberSaveable(evaluation.evaluationKey) { mutableStateOf(false) }
            val canInstall = SourceEvaluationRowActionPolicy.canOfferInstall(installEligibility)
            // KMK v0.8.17-fix1: Details/Errors/Install previously mixed a plain `clickable` Row (no
            // horizontal padding, no defined minimum height) with a Material `TextButton` (its own
            // internal min-height/ripple/content padding) -- the live-device audit found Install
            // visibly misaligned from Details/Errors as a result. All three now render through one
            // shared `SourceEvaluationRowAction` composable so icon size, label style, spacing,
            // minimum tap height, and vertical alignment are identical regardless of which action it
            // is. Install eligibility/Details/Errors disclosure behavior is unchanged -- this is a
            // layout-only fix.
            if (hasDetails || evidence != null || canInstall) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    if (evidence != null) {
                        SourceEvaluationRowAction(
                            icon = if (evidenceExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            label = stringResource(
                                if (evidenceExpanded) {
                                    KMR.strings.source_evaluation_details_hide
                                } else {
                                    KMR.strings.source_evaluation_details_toggle
                                },
                            ),
                            onClick = { evidenceExpanded = !evidenceExpanded },
                        )
                    }
                    if (hasDetails) {
                        SourceEvaluationRowAction(
                            icon = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            label = stringResource(
                                if (expanded) {
                                    KMR.strings.source_evaluation_row_hide_details
                                } else {
                                    KMR.strings.source_evaluation_row_show_details
                                },
                            ),
                            onClick = { expanded = !expanded },
                        )
                    }
                    // KMK v0.8.15-fix1: Install action -- only rendered when this row's extension is
                    // actually safe to install directly (not installed, not blocked/quarantined, still
                    // available). See SourceEvaluationRowActionPolicy.canOfferInstall and
                    // SourceEvaluationScreenModel.installEvaluatedSource(); reuses the same
                    // extensionManager.installExtension(...) path and installer safety/prompt behavior
                    // used everywhere else in the app.
                    if (canInstall) {
                        SourceEvaluationRowAction(
                            icon = Icons.Outlined.GetApp,
                            label = stringResource(
                                if (isInstalling) KMR.strings.source_evaluation_row_installing else KMR.strings.source_evaluation_row_install,
                            ),
                            onClick = onInstall,
                            enabled = !isInstalling,
                        )
                    }
                }
            }
            // KMK v0.8.1-fix1 -->
            if (hasDetails && expanded) {
                if (classifiedCatalogueErrorMessage != null) {
                    Text(
                        text = classifiedCatalogueErrorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (kindLabel != null) {
                    Text(
                        text = stringResource(KMR.strings.source_evaluation_rec_error_kind, kindLabel),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // KMK --> v0.7.12: show error/reason detail below the quality label
                // KMK --> v0.7.13: also show subdued reason text for NO_MATCHES and WEAK verdicts
                if (hasReasonHint) {
                    val isError = compatState == CompatibilityDisplayState.ERROR
                    Text(
                        text = stringResource(KMR.strings.source_evaluation_rec_quality_error_hint, recFitErrorMessage.orEmpty()),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // KMK <--
            }
            // KMK <--

            // KMK --> v0.8.1-fix1: compact expandable catalogue-evidence details, separate from the
            // rec-quality (For You search) details block above — this one explains the *catalogue
            // fit* verdict (enrichment/positive/negative/blocked/adult-risk counts), which v0.7.47
            // computed and persisted but never surfaced. Hidden entirely for outdated/error/
            // zero-sample rows via SourceEvaluationEvidenceSummaryPolicy (their counters are stale
            // or nonexistent, not current evidence).
            if (evidence != null) {
                if (evidenceExpanded) {
                    // KMK v0.8.15: catalogue fit percent, metadata confidence, evidence strength, and
                    // language moved here from the main row subtitle -- see the compact-subtitle
                    // comment above in this function.
                    val fitPercent = (evaluation.recommendationFitScore.coerceIn(0.0, 1.0) * 100).roundToInt()
                    val confidenceLabel = stringResource(
                        when (evaluation.catalogueMetadataConfidence) {
                            SourceEvaluationMetadataConfidence.HIGH -> KMR.strings.source_evaluation_metadata_confidence_high
                            SourceEvaluationMetadataConfidence.MODERATE -> KMR.strings.source_evaluation_metadata_confidence_moderate
                            SourceEvaluationMetadataConfidence.LOW -> KMR.strings.source_evaluation_metadata_confidence_low
                            SourceEvaluationMetadataConfidence.UNKNOWN -> KMR.strings.source_evaluation_metadata_confidence_unknown
                        },
                    )
                    val evidenceLabel = stringResource(
                        when (evidenceStrength(evaluation)) {
                            EvidenceStrength.STRONG -> KMR.strings.source_evaluation_evidence_strong
                            EvidenceStrength.MODERATE -> KMR.strings.source_evaluation_evidence_moderate
                            EvidenceStrength.WEAK -> KMR.strings.source_evaluation_evidence_weak
                            EvidenceStrength.LOW_CONFIDENCE -> KMR.strings.source_evaluation_evidence_low_confidence
                        },
                    )
                    Text(
                        text = stringResource(
                            KMR.strings.source_evaluation_catalogue_row_subtitle,
                            displayExt,
                            evaluation.lang.uppercase(),
                            fitPercent,
                            confidenceLabel,
                            evidenceLabel,
                            lastEvalStr,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(
                            KMR.strings.source_evaluation_detail_enriched_count,
                            evidence.detailEnrichmentSuccessCount,
                            evidence.detailEnrichmentAttemptCount,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (evidence.detailEnrichmentFailedCount > 0) {
                        Text(
                            text = stringResource(
                                KMR.strings.source_evaluation_detail_enrichment_failed_count,
                                evidence.detailEnrichmentFailedCount,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = stringResource(
                            KMR.strings.source_evaluation_metadata_sample_count,
                            evidence.metadataCandidateCount,
                            evidence.sampleCount,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(
                            KMR.strings.source_evaluation_positive_negative_summary,
                            evidence.positiveCandidateCount,
                            evidence.negativeCandidateCount,
                            evidence.blockedCandidateCount,
                            evidence.adultSignalCandidateCount,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (evidence.isManualReview) {
                        Text(
                            text = stringResource(KMR.strings.source_evaluation_verdict_review_explanation),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            // KMK <--
            // KMK v0.8.1-fix4: source/library-quality badge -- shown only when the row is currently
            // marked, so it does not clutter the common case.
            if (isQualityDisliked) {
                Text(
                    text = stringResource(
                        if (isQualityExplicit) KMR.strings.source_quality_badge_explicit else KMR.strings.source_quality_badge_poor,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            // KMK <--
        }
        VerdictBadge(verdict = evaluation.verdict)
        // KMK v0.8.1-fix4: source/library-quality overflow menu -- kept out of the always-visible
        // row content so phone UI stays compact.
        var showQualityMenu by rememberSaveable(evaluation.evaluationKey) { mutableStateOf(false) }
        Box {
            // KMK v0.8.14-fix1: was a 28dp IconButton, under Material's 48dp minimum touch target --
            // default IconButton size (no explicit override) so the tap target is normal size on
            // phone while the icon glyph itself stays compact.
            IconButton(onClick = { showQualityMenu = true }) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = stringResource(KMR.strings.source_quality_mark_poor),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = showQualityMenu, onDismissRequest = { showQualityMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(KMR.strings.source_quality_mark_poor)) },
                    onClick = {
                        onMarkQualityPoor()
                        showQualityMenu = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(KMR.strings.source_quality_mark_explicit)) },
                    onClick = {
                        onMarkQualityExplicit()
                        showQualityMenu = false
                    },
                )
                if (isQualityDisliked) {
                    DropdownMenuItem(
                        text = { Text(stringResource(KMR.strings.source_quality_clear_mark)) },
                        onClick = {
                            onClearQualityMark()
                            showQualityMenu = false
                        },
                    )
                }
            }
        }
        // KMK <--
    }
}

// KMK --> v0.7.44 Phase G.1: KMR-backed failure-kind label, replacing the hardcoded English map
// that used to live inline in EvaluationResultRow.
@Composable
private fun failureKindLabel(failureKind: SourceRecommendationProbeFailureKind?): String? {
    val resource = when (failureKind) {
        null, SourceRecommendationProbeFailureKind.NONE, SourceRecommendationProbeFailureKind.UNKNOWN -> null
        SourceRecommendationProbeFailureKind.NO_TASTE_EVIDENCE -> KMR.strings.source_evaluation_rec_error_kind_no_taste_evidence
        SourceRecommendationProbeFailureKind.AVAILABLE_EXTENSION_LIST_EMPTY -> KMR.strings.source_evaluation_rec_error_kind_ext_list_unavailable
        SourceRecommendationProbeFailureKind.EXTENSION_NOT_FOUND -> KMR.strings.source_evaluation_rec_error_kind_ext_not_found
        SourceRecommendationProbeFailureKind.EXTENSION_MATCH_AMBIGUOUS -> KMR.strings.source_evaluation_rec_error_kind_ext_ambiguous
        SourceRecommendationProbeFailureKind.INSTALL_FAILED_OR_TIMED_OUT -> KMR.strings.source_evaluation_rec_error_kind_install_failed
        SourceRecommendationProbeFailureKind.INSTALLED_EXTENSION_DID_NOT_LOAD -> KMR.strings.source_evaluation_rec_error_kind_ext_did_not_load
        SourceRecommendationProbeFailureKind.SOURCE_NOT_FOUND -> KMR.strings.source_evaluation_rec_error_kind_source_not_found
        SourceRecommendationProbeFailureKind.SOURCE_MATCH_AMBIGUOUS -> KMR.strings.source_evaluation_rec_error_kind_source_ambiguous
        SourceRecommendationProbeFailureKind.SEARCH_ERROR -> KMR.strings.source_evaluation_rec_error_kind_search_error
        SourceRecommendationProbeFailureKind.SEARCH_TIMED_OUT -> KMR.strings.source_evaluation_rec_error_kind_search_timed_out
        SourceRecommendationProbeFailureKind.RAW_RESULTS_EMPTY -> KMR.strings.source_evaluation_rec_error_kind_no_results
        SourceRecommendationProbeFailureKind.RESULTS_NO_METADATA -> KMR.strings.source_evaluation_rec_error_kind_no_genre_metadata
        SourceRecommendationProbeFailureKind.ALL_RESULTS_BLOCKED -> KMR.strings.source_evaluation_rec_error_kind_all_results_blocked
        SourceRecommendationProbeFailureKind.PRIVATE_INSTALLER_REQUIRED -> KMR.strings.source_evaluation_rec_error_kind_private_required
    }
    return resource?.let { stringResource(it) }
}
// KMK <--
// KMK <-- v0.7.6 EvaluationResultRow

// KMK --> v0.6.11: Shizuku setup card
@Composable
private fun ShizukuSetupCard(
    shizukuState: ShizukuSetupHelper.State,
    installerMode: SourceEvaluationInstallerPolicy.InstallerMode,
    isEvaluationRunning: Boolean,
    onInstallShizuku: () -> Unit,
    onOpenShizuku: () -> Unit,
    onUseForRun: () -> Unit,
    onStopUsing: () -> Unit,
    onUninstall: () -> Unit,
    // KMK --> v0.6.12: manual refresh
    onRefresh: () -> Unit,
    // KMK <--
    // KMK --> v0.6.13: "Use Private instead" action when Shizuku is selected
    privateAvailable: Boolean = false,
    onUsePrivateInstead: (() -> Unit)? = null,
    // KMK <--
    modifier: Modifier = Modifier,
) {
    val isUsingShizuku = installerMode == SourceEvaluationInstallerPolicy.InstallerMode.SHIZUKU

    // KMK --> v0.6.12: clearer status strings, distinguish selected-but-not-ready
    // KMK --> v0.6.13: when Shizuku selected+ready, mention Private is recommended
    val statusText = when {
        !shizukuState.installed -> stringResource(KMR.strings.shizuku_status_not_installed)
        isUsingShizuku && !shizukuState.binderAlive -> stringResource(KMR.strings.shizuku_status_selected_not_ready)
        isUsingShizuku && !shizukuState.permissionGranted -> stringResource(KMR.strings.shizuku_status_selected_not_ready)
        !shizukuState.binderAlive -> stringResource(KMR.strings.shizuku_status_not_running)
        !shizukuState.permissionGranted -> stringResource(KMR.strings.shizuku_status_needs_permission)
        isUsingShizuku && privateAvailable -> stringResource(KMR.strings.shizuku_status_selected_ready_private_recommended)
        isUsingShizuku -> stringResource(KMR.strings.shizuku_status_selected_ready)
        else -> stringResource(KMR.strings.shizuku_status_ready_not_selected)
    }
    // KMK <-- v0.6.13
    // KMK <-- v0.6.12

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.extraSmall),
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.padding.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Text(
                text = stringResource(KMR.strings.shizuku_setup_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // KMK --> v0.7.44 Phase G.2: FlowRow instead of Row so this can grow to 5 TextButtons
            // (Open, Stop using/Use for run, Use Private, Uninstall, Refresh) without overflowing
            // or crowding on a narrow phone — buttons wrap to the next line instead.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (!shizukuState.installed) {
                    TextButton(onClick = onInstallShizuku) {
                        Text(stringResource(KMR.strings.shizuku_action_install))
                    }
                } else {
                    TextButton(onClick = onOpenShizuku) {
                        Text(stringResource(KMR.strings.shizuku_action_open))
                    }

                    if (isUsingShizuku) {
                        TextButton(onClick = onStopUsing) {
                            Text(stringResource(KMR.strings.shizuku_action_stop_using))
                        }
                        // KMK --> v0.6.13: offer to switch to Private when Shizuku is selected
                        if (privateAvailable && onUsePrivateInstead != null) {
                            TextButton(onClick = onUsePrivateInstead) {
                                Text(stringResource(KMR.strings.source_evaluation_use_private_instead))
                            }
                        }
                        // KMK <--
                    } else {
                        TextButton(
                            onClick = onUseForRun,
                            enabled = shizukuState.binderAlive && shizukuState.permissionGranted,
                        ) {
                            Text(stringResource(KMR.strings.shizuku_action_use_for_run))
                        }
                    }

                    TextButton(
                        onClick = onUninstall,
                        enabled = !isEvaluationRunning,
                    ) {
                        Text(stringResource(KMR.strings.shizuku_action_uninstall))
                    }
                }
                // KMK --> v0.6.12: manual refresh button
                TextButton(onClick = onRefresh) {
                    Text(stringResource(KMR.strings.shizuku_action_refresh_status))
                }
                // KMK <--
            }
            // KMK <--

            Text(
                text = stringResource(KMR.strings.shizuku_safety_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
// KMK <--

// KMK --> v0.6.12: candidate pool diagnostics
@Composable
private fun CandidateDiagnosticsRow(
    diagnostics: SourceEvaluationScreenModel.CandidateDiagnostics,
    isLoading: Boolean,
    visibleCount: Int,
    batchSize: Int,
    // KMK --> v0.6.19 follow-up: show "unassessed remaining" wording when skip-evaluated is on
    skipAlreadyEvaluated: Boolean = false,
    // KMK <--
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.extraSmall),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        if (isLoading) {
            Text(
                text = stringResource(KMR.strings.source_evaluation_candidates_loading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // KMK --> v0.6.19 follow-up: when skipAlreadyEvaluated is on, the visible count is
            // the unassessed remaining count — use more precise wording in that case
            val availableText = if (skipAlreadyEvaluated) {
                stringResource(KMR.strings.source_evaluation_candidates_unassessed_remaining, visibleCount)
            } else {
                stringResource(KMR.strings.source_evaluation_candidates_available, visibleCount)
            }
            Text(
                text = availableText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // KMK <--
            if (diagnostics.evaluatedHiddenCount > 0) {
                Text(
                    text = stringResource(
                        KMR.strings.source_evaluation_candidates_evaluated_hidden,
                        diagnostics.evaluatedHiddenCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (diagnostics.explicitHiddenCount > 0) {
                Text(
                    text = stringResource(
                        KMR.strings.source_evaluation_candidates_explicit_hidden,
                        diagnostics.explicitHiddenCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // KMK --> v0.6.16: show quarantined hidden count
            if (diagnostics.unsafeHiddenCount > 0) {
                Text(
                    text = stringResource(
                        KMR.strings.source_evaluation_candidates_unsafe_hidden,
                        diagnostics.unsafeHiddenCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            // KMK <--
        }
    }
}
// KMK <--

// KMK --> v0.7.15: use KMR strings for verdict labels instead of hardcoded English
// KMK v0.8.15: extracted the verdict->(label, color) mapping out of VerdictBadge so the compact
// row subtitle (see EvaluationResultRow) can reuse the same short label instead of duplicating it.
private fun verdictLabelAndColor(
    verdict: SourceEvaluationVerdict,
    colors: androidx.compose.material3.ColorScheme,
): Pair<dev.icerock.moko.resources.StringResource, Color> = when (verdict) {
    SourceEvaluationVerdict.STRONG_FIT -> KMR.strings.source_evaluation_verdict_strong_fit to colors.primary
    SourceEvaluationVerdict.WORTH_TRYING -> KMR.strings.source_evaluation_verdict_worth_trying to colors.secondary
    SourceEvaluationVerdict.NEUTRAL -> KMR.strings.source_evaluation_verdict_neutral to colors.onSurfaceVariant
    SourceEvaluationVerdict.WEAK -> KMR.strings.source_evaluation_verdict_weak to colors.onSurfaceVariant
    SourceEvaluationVerdict.POOR_SEARCH -> KMR.strings.source_evaluation_verdict_poor_search to colors.onSurfaceVariant
    SourceEvaluationVerdict.EXPLICIT_HEAVY -> KMR.strings.source_evaluation_verdict_explicit to colors.error
    SourceEvaluationVerdict.ECCHI_HEAVY -> KMR.strings.source_evaluation_verdict_ecchi to colors.tertiary
    SourceEvaluationVerdict.REJECTED -> KMR.strings.source_evaluation_verdict_rejected to colors.error
    SourceEvaluationVerdict.ERROR -> KMR.strings.source_evaluation_verdict_error to colors.error
    SourceEvaluationVerdict.NEEDS_MANUAL_REVIEW -> KMR.strings.source_evaluation_verdict_review to colors.secondary
}

@Composable
private fun VerdictBadge(verdict: SourceEvaluationVerdict) {
    val (labelRes, color) = verdictLabelAndColor(verdict, MaterialTheme.colorScheme)
    Badge(containerColor = color.copy(alpha = 0.15f)) {
        Text(
            text = stringResource(labelRes),
            color = color,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
// KMK <-- v0.7.15
// KMK <--
