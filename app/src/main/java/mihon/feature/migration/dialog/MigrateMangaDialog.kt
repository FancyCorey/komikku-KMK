package mihon.feature.migration.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastForEach
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import kotlinx.coroutines.flow.update
import mihon.domain.migration.models.MigrationFlag
import mihon.domain.migration.usecases.MigrateMangaUseCase
import mihon.domain.migration.usecases.MigrationOutcome
import mihon.feature.common.utils.getLabel
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK Confirmed Blocker Remediation follow-up Phase 1 2026-07-29: which localized message to show
// for a non-Success MigrationOutcome -- kept as a plain enum (not the message string itself) so the
// screen model stays UI-toolkit-agnostic and directly unit-testable.
internal enum class MigrationDialogErrorKey { PARTIAL_FAILURE, NOT_STARTED }

@Composable
internal fun Screen.MigrateMangaDialog(
    current: Manga,
    target: Manga,
    onClickTitle: () -> Unit,
    onDismissRequest: () -> Unit,
    onComplete: () -> Unit = onDismissRequest,
) {
    val scope = rememberCoroutineScope()

    val screenModel = rememberScreenModel { MigrateDialogScreenModel() }
    LaunchedEffect(current, target) {
        screenModel.init(current, target)
    }
    val state by screenModel.state.collectAsState()

    if (state.isMigrated) return

    if (state.isMigrating) {
        LoadingScreen(
            modifier = Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)),
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(MR.strings.migration_dialog_what_to_include))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                // KMK Confirmed Blocker Remediation Phase 1 2026-07-29: surface a truthful failure
                // state instead of silently closing the dialog as if the migration had succeeded --
                // see MigrateDialogScreenModel.migrateManga's MigrationOutcome handling below.
                state.errorMessage?.let { key ->
                    val messageRes = when (key) {
                        MigrationDialogErrorKey.PARTIAL_FAILURE -> KMR.strings.migration_dialog_partial_failure
                        MigrationDialogErrorKey.NOT_STARTED -> KMR.strings.migration_dialog_not_started
                    }
                    Text(
                        text = stringResource(messageRes),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = MaterialTheme.padding.small),
                    )
                }
                state.applicableFlags.fastForEach { flag ->
                    LabeledCheckbox(
                        label = stringResource(flag.getLabel()),
                        checked = flag in state.selectedFlags,
                        onCheckedChange = { screenModel.toggleSelection(flag) },
                    )
                }
            }
        },
        confirmButton = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            ) {
                TextButton(
                    onClick = {
                        // KMK -->
                        // Allow `migrate` mangas when using `Bulk-favorite`
                        // onDismissRequest()
                        // KMK <--
                        onClickTitle()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_show_manga))
                }

                Spacer(modifier = Modifier.weight(1f))

                TextButton(
                    onClick = {
                        scope.launchIO {
                            val migrated = screenModel.migrateManga(replace = false)
                            if (migrated) withUIContext { onComplete() }
                        }
                    },
                ) {
                    Text(text = stringResource(MR.strings.copy))
                }
                TextButton(
                    onClick = {
                        scope.launchIO {
                            val migrated = screenModel.migrateManga(replace = true)
                            if (migrated) withUIContext { onComplete() }
                        }
                    },
                ) {
                    Text(text = stringResource(MR.strings.migrate))
                }
            }
        },
    )
}

// KMK Confirmed Blocker Remediation follow-up Phase 1: internal (not private) so
// MigrateDialogScreenModelMigrationOutcomeTest can construct it directly with a mocked
// MigrateMangaUseCase, without needing the use case's full platform-dependency graph.
internal class MigrateDialogScreenModel(
    val sourcePreference: SourcePreferences = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val migrateManga: MigrateMangaUseCase = Injekt.get(),
) : StateScreenModel<MigrateDialogScreenModel.State>(State()) {

    fun init(current: Manga, target: Manga) {
        val applicableFlags = buildList {
            MigrationFlag.entries.forEach {
                val applicable = when (it) {
                    MigrationFlag.CHAPTER -> true
                    MigrationFlag.CATEGORY -> true
                    // KMK -->
                    MigrationFlag.TRACK -> true
                    // KMK <--
                    MigrationFlag.CUSTOM_COVER -> current.hasCustomCover(coverCache)
                    MigrationFlag.NOTES -> current.notes.isNotBlank()
                    MigrationFlag.REMOVE_DOWNLOAD -> downloadManager.getDownloadCount(current) > 0
                    // KMK -->
                    MigrationFlag.EXTRA -> true
                    // KMK <--
                }
                if (applicable) add(it)
            }
        }
        val selectedFlags = sourcePreference.migrationFlags().get()
        mutableState.update {
            State(
                current = current,
                target = target,
                applicableFlags = applicableFlags,
                selectedFlags = selectedFlags,
            )
        }
    }

    fun toggleSelection(flag: MigrationFlag) {
        mutableState.update {
            val selectedFlags = it.selectedFlags.toMutableSet()
                .apply { if (contains(flag)) remove(flag) else add(flag) }
                .toSet()
            it.copy(selectedFlags = selectedFlags)
        }
    }

    // KMK Confirmed Blocker Remediation follow-up Phase 1 2026-07-29: previously discarded the
    // MigrationOutcome entirely and always set isMigrated = true, so a PartialFailure/NotStarted
    // outcome (migrateManga's non-fatal-exception path) closed this dialog exactly as if the
    // migration had fully succeeded. migrateManga(...) itself already catches every non-fatal
    // exception internally and only lets CancellationException/fatal errors propagate (see
    // MigrateMangaUseCase's own catch block) -- so this call intentionally has no try/catch of its
    // own; a thrown exception here is meant to propagate to the caller's coroutine scope, not be
    // swallowed. Returns true only for a verified Success, so the caller only calls onComplete()
    // then.
    suspend fun migrateManga(replace: Boolean): Boolean {
        val state = state.value
        val current = state.current ?: return false
        val target = state.target ?: return false
        // KMK -->
        // sourcePreference.migrationFlags().set(state.selectedFlags)
        // KMK <--
        mutableState.update { it.copy(isMigrating = true, errorMessage = null) }
        return when (migrateManga(current, target, replace, /* KMK --> */ state.selectedFlags /* KMK <-- */)) {
            is MigrationOutcome.Success -> {
                mutableState.update { it.copy(isMigrating = false, isMigrated = true) }
                true
            }
            is MigrationOutcome.PartialFailure -> {
                mutableState.update {
                    it.copy(isMigrating = false, isMigrated = false, errorMessage = MigrationDialogErrorKey.PARTIAL_FAILURE)
                }
                false
            }
            is MigrationOutcome.NotStarted -> {
                mutableState.update {
                    it.copy(isMigrating = false, isMigrated = false, errorMessage = MigrationDialogErrorKey.NOT_STARTED)
                }
                false
            }
        }
    }

    data class State(
        val current: Manga? = null,
        val target: Manga? = null,
        val applicableFlags: List<MigrationFlag> = emptyList(),
        val selectedFlags: Set<MigrationFlag> = emptySet(),
        val isMigrating: Boolean = false,
        val isMigrated: Boolean = false,
        // KMK Confirmed Blocker Remediation follow-up Phase 1: non-null only after a verified
        // PartialFailure/NotStarted MigrationOutcome; resolved to localized text in the Composable.
        val errorMessage: MigrationDialogErrorKey? = null,
    )
}
