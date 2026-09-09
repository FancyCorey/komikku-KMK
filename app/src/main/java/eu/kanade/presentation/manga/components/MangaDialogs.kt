package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.track.model.LocalTrackingActionPolicy
import eu.kanade.presentation.track.TrackDateSelector
import eu.kanade.tachiyomi.util.system.isReleaseBuildType
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.domain.manga.interactor.FetchInterval
import tachiyomi.domain.tracker.model.LocalTrackedWork
import tachiyomi.domain.tracker.model.LocalTrackedWorkStatus
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.WheelTextPicker
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue

@Composable
fun DeleteChaptersDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    onConfirm()
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.are_you_sure))
        },
        text = {
            Text(text = stringResource(MR.strings.confirm_delete_chapters))
        },
    )
}

// KMK -->
@Composable
fun ClearMangaDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (Boolean, Boolean) -> Unit,
) {
    var list by remember {
        mutableStateOf(
            buildList<CheckboxState.State<StringResource>> {
                add(CheckboxState.State.None(KMR.strings.downloaded_data))
                add(CheckboxState.State.None(KMR.strings.chapters_from_database))
            },
        )
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(
                enabled = list.any { it.isChecked },
                onClick = {
                    onDismissRequest()
                    onConfirm(
                        list[0].isChecked,
                        list[1].isChecked,
                    )
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.action_remove))
        },
        text = {
            Column {
                list.forEachIndexed { index, state ->
                    LabeledCheckbox(
                        label = stringResource(state.value),
                        checked = state.isChecked,
                        onCheckedChange = {
                            val mutableList = list.toMutableList()
                            mutableList[index] = state.next() as CheckboxState.State<StringResource>
                            list = mutableList.toList()
                        },
                    )
                }
            }
        },
    )
}

/**
 * The local-tracking status/list workflow: lets the user choose a status (Reading/Plan to
 * read/On hold/Completed/Dropped) or remove local tracking entirely, independent of any external
 * tracker. Selecting a status stages the change until the user confirms with OK; Remove remains a
 * separate explicit action, matching the confirmation behavior used by external trackers.
 *
 * KMK v0.8.21-fix2: [currentStatus] is now nullable so this same dialog serves both the "not yet
 * locally tracked" first-time entry (no radio pre-selected, no Remove action -- there is nothing
 * to remove yet) and the "already tracked" status-change case, instead of the caller writing a
 * status immediately on first tap. This is what makes Local a true peer of an external tracker in
 * [eu.kanade.presentation.track.TrackInfoDialogHome]: tapping it always opens this dialog, it never
 * silently writes a status on the first tap.
 */
@Composable
fun LocalTrackStatusDialog(
    currentStatus: LocalTrackedWorkStatus?,
    onStatusSelected: (LocalTrackedWorkStatus) -> Unit,
    onRemove: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    var selectedStatus by remember(currentStatus) { mutableStateOf(currentStatus) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(KMR.strings.local_tracking_status_dialog_title)) },
        text = {
            Column {
                LocalTrackingActionPolicy.statusOrder.forEach { status ->
                    val isSelected = status == selectedStatus
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .selectable(
                                selected = isSelected,
                                onClick = { selectedStatus = status },
                            )
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize(),
                    ) {
                        RadioButton(selected = isSelected, onClick = null)
                        Text(
                            text = stringResource(LocalTrackingActionPolicy.statusLabel(status)),
                            style = MaterialTheme.typography.bodyLarge.merge(),
                            modifier = Modifier.padding(start = 24.dp),
                        )
                    }
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (currentStatus != null) {
                    TextButton(onClick = onRemove) {
                        Text(text = stringResource(KMR.strings.remove_local_tracking))
                    }
                }
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedStatus?.let(onStatusSelected) ?: onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalTrackDetailsDialog(
    work: LocalTrackedWork,
    dateFormat: DateTimeFormatter,
    onSave: (Double?, Long?, Long?) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var scoreText by rememberSaveable { mutableStateOf(work.score?.toString().orEmpty()) }
    var startDate by rememberSaveable { mutableStateOf(work.startDate) }
    var finishDate by rememberSaveable { mutableStateOf(work.finishDate) }
    var dateField by remember { mutableStateOf<LocalDateField?>(null) }
    var validationMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val scoreError = stringResource(KMR.strings.local_tracking_score_error)
    fun save() {
        val score = scoreText.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        if ((score == null && scoreText.isNotBlank()) || (score != null && (score < 1.0 || score > 100.0))) {
            validationMessage = scoreError
            return
        }
        validationMessage = null
        onSave(score, startDate, finishDate)
    }
    if (dateField != null) {
        val field = dateField!!
        TrackDateSelector(
            title = stringResource(
                when (field) {
                    LocalDateField.START -> MR.strings.track_started_reading_date
                    LocalDateField.FINISH -> MR.strings.track_finished_reading_date
                },
            ),
            initialSelectedDateMillis = when (field) {
                LocalDateField.START -> startDate
                LocalDateField.FINISH -> finishDate
            } ?: System.currentTimeMillis(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    if (date > java.time.LocalDate.now(ZoneOffset.UTC)) return false
                    return when (field) {
                        LocalDateField.START -> finishDate == null || date <= Instant.ofEpochMilli(finishDate!!).atZone(ZoneOffset.UTC).toLocalDate()
                        LocalDateField.FINISH -> startDate == null || date >= Instant.ofEpochMilli(startDate!!).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                }

                override fun isSelectableYear(year: Int): Boolean = year <= java.time.LocalDate.now(ZoneOffset.UTC).year
            },
            onConfirm = { millis ->
                when (field) {
                    LocalDateField.START -> startDate = millis
                    LocalDateField.FINISH -> finishDate = millis
                }
                dateField = null
            },
            onRemove = {
                when (field) {
                    LocalDateField.START -> startDate = null
                    LocalDateField.FINISH -> finishDate = null
                }
                dateField = null
            },
            onDismissRequest = { dateField = null },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = stringResource(KMR.strings.local_tracking_details_dialog_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = scoreText,
                        onValueChange = { scoreText = it.filter { char -> char.isDigit() || char == '.' } },
                        label = { Text(stringResource(MR.strings.score)) },
                        isError = validationMessage != null,
                        supportingText = validationMessage?.let { message -> { Text(message) } },
                        singleLine = true,
                    )
                    Text(
                        text = listOfNotNull(
                            startDate?.let { dateFormat.format(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) },
                            finishDate?.let { dateFormat.format(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) },
                        ).joinToString(" • ").ifBlank { stringResource(KMR.strings.local_tracking_dates_pending) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { dateField = LocalDateField.START }) {
                        Text(stringResource(MR.strings.track_started_reading_date))
                    }
                    TextButton(onClick = { dateField = LocalDateField.FINISH }) {
                        Text(stringResource(MR.strings.track_finished_reading_date))
                    }
                }
            },
            dismissButton = { TextButton(onClick = onDismissRequest) { Text(stringResource(MR.strings.action_cancel)) } },
            confirmButton = { TextButton(onClick = ::save) { Text(stringResource(MR.strings.action_save)) } },
        )
    }
}

private enum class LocalDateField {
    START,
    FINISH,
}
// KMK <--

@Composable
fun SetIntervalDialog(
    interval: Int,
    nextUpdate: Instant?,
    onDismissRequest: () -> Unit,
    onValueChanged: ((Int) -> Unit)? = null,
) {
    var selectedInterval by rememberSaveable { mutableIntStateOf(if (interval < 0) -interval else 0) }

    val nextUpdateDays = remember(nextUpdate) {
        return@remember if (nextUpdate != null) {
            val now = Instant.now()
            now.until(nextUpdate, ChronoUnit.DAYS).toInt().coerceAtLeast(0)
        } else {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(MR.strings.pref_library_update_smart_update)) },
        text = {
            Column {
                if (nextUpdateDays != null && nextUpdateDays >= 0 && interval >= 0) {
                    Text(
                        stringResource(
                            MR.strings.manga_interval_expected_update,
                            pluralStringResource(
                                MR.plurals.day,
                                count = nextUpdateDays,
                                nextUpdateDays,
                            ),
                            pluralStringResource(
                                MR.plurals.day,
                                count = interval.absoluteValue,
                                interval.absoluteValue,
                            ),
                        ),
                    )
                } else {
                    Text(
                        stringResource(MR.strings.manga_interval_expected_update_null),
                    )
                }
                Spacer(Modifier.height(MaterialTheme.padding.small))

                if (onValueChanged != null && (!isReleaseBuildType)) {
                    Text(stringResource(MR.strings.manga_interval_custom_amount))

                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        val size = DpSize(width = maxWidth / 2, height = 128.dp)
                        val items =
                            // KMK -->
                            (
                                listOf(stringResource(MR.strings.action_disable)) +
                                    // KMK <--
                                    (0..FetchInterval.MAX_INTERVAL)
                                        .map {
                                            if (it == 0) {
                                                stringResource(MR.strings.label_default)
                                            } else {
                                                it.toString()
                                            }
                                        }
                                )
                                .toImmutableList()
                        WheelTextPicker(
                            items = items,
                            size = size,
                            startIndex = (
                                selectedInterval +
                                    // KMK -->
                                    1
                                ).takeIf { selectedInterval != FetchInterval.MANUAL_DISABLE } ?: 0,
                            // KMK <--
                            onSelectionChanged = { idx ->
                                selectedInterval = (
                                    idx -
                                        // KMK -->
                                        1
                                    ).takeIf { idx != 0 } ?: FetchInterval.MANUAL_DISABLE
                                // KMK <--
                            },
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onValueChanged?.invoke(selectedInterval)
                onDismissRequest()
            }) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
    )
}
