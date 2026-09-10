package exh.accessibility

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class KmkAccessibilityOwnershipSourceTest {

    private fun source(path: String): String = File(path).also {
        assertTrue(it.exists(), "expected source file at $path")
    }.readText()

    @Test
    fun `standalone icon actions have localized descriptions and platform targets`() {
        val bestVersion = source("src/main/java/exh/recs/bestversion/BestVersionCompareScreen.kt")
        val sourceEvaluation = source("src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt")
        val settings = source("src/main/java/exh/recs/settings/RecommendationSettingsSharedComponents.kt")
        val linkGroups = source("src/main/java/exh/recs/links/LinkGroupManagementScreen.kt")

        assertTrue(bestVersion.contains("KMR.strings.best_version_preview_fit_page"))
        assertTrue(bestVersion.contains("KMR.strings.best_version_preview_crop_page"))
        assertFalse(bestVersion.contains(".size(28.dp)"))
        assertTrue(sourceEvaluation.contains("KMR.strings.accessibility_expand_safety_diagnostics"))
        assertTrue(sourceEvaluation.contains("KMR.strings.accessibility_collapse_safety_diagnostics"))
        assertTrue(sourceEvaluation.contains(".minimumInteractiveComponentSize()"))
        assertTrue(sourceEvaluation.contains(".defaultMinSize(minHeight = 48.dp)"))
        assertTrue(settings.contains("KMR.strings.accessibility_delete_tag"))
        assertTrue(linkGroups.contains("KMR.strings.accessibility_expand_link_group"))
        assertTrue(linkGroups.contains("KMR.strings.accessibility_collapse_link_group"))
    }

    @Test
    fun `custom disclosure controls expose role action and state semantics`() {
        val sourceEvaluation = source("src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt")

        assertTrue(sourceEvaluation.contains("role = Role.Button"))
        assertTrue(sourceEvaluation.contains("onClickLabel = managementToggleAction"))
        assertTrue(sourceEvaluation.contains("stateDescription = managementExpandedState"))
        assertTrue(sourceEvaluation.contains("onClickLabel = toggleAction"))
        assertTrue(sourceEvaluation.contains("stateDescription = expandedState"))
        assertTrue(sourceEvaluation.contains(".toggleable(value = checked, role = Role.Switch"))
        assertTrue(sourceEvaluation.contains("Switch(checked = checked, onCheckedChange = null)"))
    }

    @Test
    fun `reader schedule binds labels to one radio or checkbox owner`() {
        val schedule = source("src/main/java/eu/kanade/presentation/reader/ReaderScheduleDialog.kt")

        assertTrue(schedule.contains(".selectable("))
        assertTrue(schedule.contains("role = Role.RadioButton"))
        assertTrue(schedule.contains("RadioButton(selected = mode == ReaderScheduleMode.RESTRICTED, onClick = null)"))
        assertTrue(schedule.contains("RadioButton(selected = mode == ReaderScheduleMode.ALLOWED, onClick = null)"))
        assertTrue(schedule.contains(".toggleable("))
        assertTrue(schedule.contains("role = Role.Checkbox"))
        assertTrue(schedule.contains("Checkbox(checked = wholeDay, onCheckedChange = null)"))
    }

    @Test
    fun `reader timer binds every checkbox label to one toggle owner`() {
        val timer = source("src/main/java/eu/kanade/presentation/reader/ReaderTimerDialog.kt")

        assertTrue(timer.contains("import androidx.compose.foundation.selection.toggleable"))
        assertTrue(timer.contains("toggleable(\n                        value = checked"))
        assertTrue(timer.contains("Checkbox(checked = checked, onCheckedChange = null)"))
        assertTrue(timer.contains("value = finishCurrentChapter"))
        assertTrue(timer.contains("role = Role.Checkbox"))
        assertTrue(timer.contains("enabled = finishCurrentChapter"))
    }

    @Test
    fun `reader schedule does not silently discard equal-time windows`() {
        val schedule = source("src/main/java/eu/kanade/presentation/reader/ReaderScheduleDialog.kt")

        assertTrue(schedule.contains("AddWindowStage.INVALID_TIME"))
        assertTrue(schedule.contains("KMR.strings.reading_schedule_equal_times"))
        assertFalse(schedule.contains("} else {\n                    onDone(null)\n                }"))
    }

    @Test
    fun `source ordering has evaluation-safe naming and non-drag actions`() {
        val shared = source("src/main/java/exh/recs/settings/RecommendationSettingsSharedComponents.kt")
        val priority = source("src/main/java/exh/recs/settings/RecommendationSourcePrioritySettingsScreen.kt")

        assertTrue(shared.contains("EvaluationModeFormatter.sourceLabel(source.id)"))
        assertTrue(shared.contains("KMR.strings.accessibility_reorder_source, sourceDisplayLabel"))
        assertTrue(shared.contains("CustomAccessibilityAction(moveUpLabel)"))
        assertTrue(shared.contains("CustomAccessibilityAction(moveDownLabel)"))
        assertTrue(shared.contains("customActions = reorderActions"))
        assertTrue(shared.contains("KMR.strings.accessibility_source_enabled, sourceDisplayLabel"))
        assertTrue(priority.contains("fun moveSource(fromIndex: Int, toIndex: Int)"))
        assertTrue(priority.contains("onMoveUp = if (index > 0)"))
        assertTrue(priority.contains("onMoveDown = if (index < sourcesState.lastIndex)"))
        assertTrue(priority.contains("screenModel.setSourceOrder(sourcesState.map { it.id })"))
    }

    @Test
    fun `action history controls remain labeled and decorative artwork stays silent`() {
        val history = source("src/main/java/exh/util/EvaluationModeActionHistoryScreen.kt")
        val emptyState = source("src/main/java/eu/kanade/presentation/components/KmkEmptyStateIllustration.kt")

        assertTrue(history.contains("contentDescription = stringResource(KMR.strings.eval_undo_clear_all)"))
        assertTrue(history.contains("contentDescription = stringResource(KMR.strings.eval_undo_diagnostic_details)"))
        assertTrue(emptyState.contains("contentDescription = null"))
    }
}
