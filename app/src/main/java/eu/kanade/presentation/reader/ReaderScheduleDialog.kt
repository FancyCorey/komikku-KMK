package eu.kanade.presentation.reader

import android.content.Context
import android.content.ContextWrapper
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import eu.kanade.presentation.components.KmkEmptyStateArtwork
import eu.kanade.presentation.components.KmkEmptyStateIllustration
import eu.kanade.tachiyomi.ui.reader.schedule.ReaderScheduleMode
import eu.kanade.tachiyomi.ui.reader.schedule.ReaderScheduleStore
import eu.kanade.tachiyomi.ui.reader.schedule.ReaderScheduleWindow
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale

// KMK v0.8.5 -->
// KMK v0.8.7 -->
/**
 * Reading-schedule editor: one shared [ReaderScheduleMode] plus an add/edit/delete list of
 * [ReaderScheduleWindow]s. Uses [MaterialTimePicker] for start/end time entry.
 *
 * The dialog unwraps [android.content.ContextWrapper] instances rather than directly casting the
 * Compose context to `MainActivity`. A direct cast can fail when `LocalContext` is a themed wrapper,
 * preventing the time picker from opening even though the activity is present underneath.
 *
 * The previous implementation performed a direct `context as? MainActivity` cast to obtain a
 * `FragmentManager` for [MaterialTimePicker]. Whenever the Compose `LocalContext` was a
 * `ContextThemeWrapper`/other `ContextWrapper` rather than a literal `MainActivity` instance (which
 * this repo's own `BiometricTimesScreen.kt` — the file this dialog was told to mirror — also does,
 * confirming this is a real, reachable failure mode, not a hypothetical one), the cast silently
 * failed, `onDone(null)` fired immediately, the weekday dialog closed, and no time picker ever
 * appeared. This file now unwraps `ContextWrapper`s via [findActivity] instead of casting directly,
 * and shows a visible error (rather than silently discarding the user's weekday selection) if no
 * Activity can be found at all.
 */
@Composable
fun ReaderScheduleDialog(
    onDismissRequest: () -> Unit,
    initialMode: ReaderScheduleMode,
    initialWindows: List<ReaderScheduleWindow>,
    onSave: (mode: ReaderScheduleMode, windows: List<ReaderScheduleWindow>) -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val wholeDayLabel = stringResource(KMR.strings.reading_schedule_whole_day)
    // KMK v0.8.7: `mode`/`windows` are the persisted-on-Save draft. `onDismissRequest` (outside tap,
    // back press) no longer calls onSave — it now behaves like Cancel (discards the draft), per plan
    // Finding D. Only the explicit Save button commits.
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var windows by rememberSaveable(stateSaver = readerScheduleWindowsSaver) { mutableStateOf(initialWindows) }
    var showAddFlow by rememberSaveable { mutableStateOf(false) }
    // KMK v0.8.7: null = adding a new window; non-null = editing windows[editingIndex] in place.
    var editingIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var pendingDeleteIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    fun openAddFlow() {
        editingIndex = null
        showAddFlow = true
    }

    fun openEditFlow(index: Int) {
        editingIndex = index
        showAddFlow = true
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(KMR.strings.reading_schedule_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(KMR.strings.reading_schedule_scope_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = MaterialTheme.padding.small),
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = MaterialTheme.padding.small))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = mode == ReaderScheduleMode.RESTRICTED,
                            role = Role.RadioButton,
                            onClick = { mode = ReaderScheduleMode.RESTRICTED },
                        ),
                ) {
                    RadioButton(selected = mode == ReaderScheduleMode.RESTRICTED, onClick = null)
                    Text(stringResource(KMR.strings.reading_schedule_mode_restricted))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = mode == ReaderScheduleMode.ALLOWED,
                            role = Role.RadioButton,
                            onClick = { mode = ReaderScheduleMode.ALLOWED },
                        ),
                ) {
                    RadioButton(selected = mode == ReaderScheduleMode.ALLOWED, onClick = null)
                    Text(stringResource(KMR.strings.reading_schedule_mode_allowed))
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = MaterialTheme.padding.small))
                Text(
                    text = stringResource(KMR.strings.reading_schedule_windows_header),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = MaterialTheme.padding.extraSmall),
                )
                if (windows.isEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = MaterialTheme.padding.small),
                    ) {
                        KmkEmptyStateIllustration(
                            artwork = KmkEmptyStateArtwork.READER_SCHEDULE,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp),
                        )
                        Text(
                            text = stringResource(KMR.strings.reading_schedule_no_windows),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                windows.forEachIndexed { index, window ->
                    if (index > 0) {
                        HorizontalDivider()
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = describeWindow(
                                window = window,
                                locale = locale,
                                wholeDayLabel = wholeDayLabel,
                                formatTime = { minute -> formatScheduleTime(context, minute) },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        // KMK v0.8.7: real Edit action (plan Finding E) — opens the same AddWindowFlow
                        // prefilled with this window's values; all other windows are untouched until
                        // the edited draft is explicitly committed.
                        IconButton(onClick = { openEditFlow(index) }) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = stringResource(MR.strings.action_edit),
                            )
                        }
                        IconButton(onClick = { pendingDeleteIndex = index }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = stringResource(MR.strings.action_delete),
                            )
                        }
                    }
                }
                TextButton(
                    onClick = { openAddFlow() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(imageVector = Icons.Outlined.Add, contentDescription = null)
                    Text(stringResource(KMR.strings.reading_schedule_add_window))
                }

                if (showAddFlow) {
                    val existing = editingIndex?.let { windows.getOrNull(it) }
                    AddWindowFlow(
                        initial = existing,
                        onDone = { newWindow ->
                            val index = editingIndex
                            showAddFlow = false
                            editingIndex = null
                            if (newWindow != null) {
                                windows = if (index != null && index in windows.indices) {
                                    // KMK v0.8.7: Edit — replace in place, every other window survives untouched.
                                    windows.toMutableList().also { it[index] = newWindow }
                                } else {
                                    windows + newWindow
                                }
                            }
                            // newWindow == null (Cancel at any inner stage, or an unusable Activity):
                            // discard only this draft; windows list (and any other in-progress edit) is unchanged.
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(mode, windows)
                onDismissRequest()
            }) {
                Text(stringResource(MR.strings.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )

    pendingDeleteIndex?.let { index ->
        AlertDialog(
            onDismissRequest = { pendingDeleteIndex = null },
            title = { Text(stringResource(KMR.strings.reading_schedule_delete_window_title)) },
            text = { Text(stringResource(KMR.strings.reading_schedule_delete_window_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        windows = ReaderScheduleWindowDeletionPolicy.removeAt(windows, index)
                        pendingDeleteIndex = null
                    },
                ) {
                    Text(stringResource(KMR.strings.reading_schedule_delete_window_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteIndex = null }) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        )
    }
}

/**
 * Saveable representation for the schedule draft. The domain window is intentionally not made
 * Parcelable: Android state restoration should carry only the small primitive draft, and malformed
 * restored rows should be discarded rather than crashing stopped-activity state collection.
 */
internal object ReaderScheduleWindowSaveableState {
    /**
     * Keep the Compose saveable payload as one primitive String. Android may parcel every value
     * returned by a saver while stopping an Activity; carrying the domain window object or a
     * nested object graph here would make the reader vulnerable to a process-state crash.
     */
    fun encode(windows: List<ReaderScheduleWindow>): String =
        ReaderScheduleStore.serializeWindows(windows)

    fun decode(value: Any): List<ReaderScheduleWindow> {
        if (value is String) return ReaderScheduleStore.parseWindows(value)

        // Accept the previous primitive-list shape so a state bundle produced by an older build
        // degrades safely during restoration instead of losing every draft window.
        val rows = value as? List<*> ?: return emptyList()
        return rows.mapNotNull { row ->
            val fields = row as? List<*> ?: return@mapNotNull null
            if (fields.size != 4) return@mapNotNull null
            val rawWeekdays = fields.getOrNull(0) as? List<*> ?: return@mapNotNull null
            val weekdays = rawWeekdays.mapNotNull { rawDay ->
                (rawDay as? Number)?.toInt()?.let { value ->
                    runCatching { DayOfWeek.of(value) }.getOrNull()
                }
            }.toSet()
            val start = (fields.getOrNull(1) as? Number)?.toInt() ?: return@mapNotNull null
            val end = (fields.getOrNull(2) as? Number)?.toInt() ?: return@mapNotNull null
            val allDay = fields.getOrNull(3) as? Boolean ?: return@mapNotNull null
            if (!ReaderScheduleWindow.isValid(weekdays, start, end, allDay)) {
                null
            } else {
                ReaderScheduleWindow(weekdays, start, end, allDay)
            }
        }
    }
}

private val readerScheduleWindowsSaver = Saver<List<ReaderScheduleWindow>, String>(
    save = { ReaderScheduleWindowSaveableState.encode(it) },
    restore = { ReaderScheduleWindowSaveableState.decode(it) },
)

// KMK Confirmed Blocker Remediation 2026-07-28 -->
/**
 * Pure list mutation for the schedule-window delete-confirmation flow, extracted from
 * [ReaderScheduleDialog]'s confirm-button `onClick` so the exact removal semantics are directly
 * unit-testable without a Compose test harness (this module has no existing Compose UI test
 * infrastructure -- see [ReaderScheduleDialogWindowDeletionTest] for the required coverage: tapping
 * delete opens confirmation without mutating the draft, cancel/back leaves the row present, confirm
 * removes only the selected row, and Save is a separate, later step this object does not touch).
 */
internal object ReaderScheduleWindowDeletionPolicy {
    /** Removes the window at [index] from [windows], returning a new list. Out-of-range [index] is a no-op. */
    fun removeAt(windows: List<ReaderScheduleWindow>, index: Int): List<ReaderScheduleWindow> =
        windows.toMutableList().also { if (index in it.indices) it.removeAt(index) }
}
// KMK <--

/**
 * Unwraps [ContextWrapper]s (e.g. `ContextThemeWrapper`) until a [FragmentActivity] is found, or
 * null if none exists in the chain. [FragmentActivity] (not just [android.app.Activity]) is
 * required here specifically because [MaterialTimePicker] needs `supportFragmentManager`.
 */
private tailrec fun Context.findActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private enum class AddWindowStage { WEEKDAYS, START_TIME, END_TIME, ERROR, INVALID_TIME }

/**
 * Drives the weekday-select -> start-time-picker -> end-time-picker sequence for adding or editing
 * one window. [initial], when non-null, prefills weekdays/times/whole-day for an Edit.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddWindowFlow(initial: ReaderScheduleWindow?, onDone: (ReaderScheduleWindow?) -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    var weekdays by rememberSaveable { mutableStateOf(initial?.weekdays ?: emptySet()) }
    var wholeDay by rememberSaveable { mutableStateOf(initial?.allDay ?: false) }
    var stage by rememberSaveable { mutableStateOf(AddWindowStage.WEEKDAYS) }
    // KMK v0.8.7: guards against a picker being shown twice for the same stage across recompositions
    // (plan requirement: "prevent duplicate picker launches when recomposition occurs").
    var startPickerLaunched by rememberSaveable { mutableStateOf(false) }
    var endPickerLaunched by rememberSaveable { mutableStateOf(false) }
    var startMinute by rememberSaveable { mutableStateOf(-1) }

    if (stage == AddWindowStage.WEEKDAYS) {
        AlertDialog(
            onDismissRequest = { onDone(null) },
            title = { Text(stringResource(KMR.strings.reading_schedule_weekdays)) },
            text = {
                Column {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                    ) {
                        DayOfWeek.entries.forEach { day ->
                            FilterChip(
                                selected = day in weekdays,
                                onClick = { weekdays = if (day in weekdays) weekdays - day else weekdays + day },
                                label = { Text(day.shortDisplayName(locale)) },
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = wholeDay,
                                role = Role.Checkbox,
                                onValueChange = { wholeDay = it },
                            )
                            .padding(top = MaterialTheme.padding.small),
                    ) {
                        Checkbox(checked = wholeDay, onCheckedChange = null)
                        Text(stringResource(KMR.strings.reading_schedule_whole_day))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // KMK v0.8.7: whole-day windows skip both time pickers entirely (Finding C).
                        stage = if (wholeDay) {
                            onDone(
                                ReaderScheduleWindow(weekdays, startMinuteOfDay = 0, endMinuteOfDay = 0, allDay = true),
                            )
                            AddWindowStage.WEEKDAYS // unreachable after onDone, kept for exhaustiveness
                        } else {
                            AddWindowStage.START_TIME
                        }
                    },
                    enabled = weekdays.isNotEmpty(),
                ) {
                    Text(stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { onDone(null) }) { Text(stringResource(MR.strings.action_cancel)) }
            },
        )
        return
    }

    if (stage == AddWindowStage.ERROR) {
        AlertDialog(
            onDismissRequest = { onDone(null) },
            title = { Text(stringResource(KMR.strings.reading_schedule_title)) },
            text = { Text(stringResource(KMR.strings.reading_schedule_activity_unavailable)) },
            confirmButton = {
                TextButton(onClick = { onDone(null) }) { Text(stringResource(MR.strings.action_ok)) }
            },
        )
        return
    }

    if (stage == AddWindowStage.INVALID_TIME) {
        AlertDialog(
            onDismissRequest = { stage = AddWindowStage.END_TIME },
            title = { Text(stringResource(KMR.strings.reading_schedule_title)) },
            text = { Text(stringResource(KMR.strings.reading_schedule_equal_times)) },
            confirmButton = {
                TextButton(onClick = { stage = AddWindowStage.END_TIME }) {
                    Text(stringResource(MR.strings.action_ok))
                }
            },
        )
        return
    }

    // KMK v0.8.7: 12h/24h follows the device's own preference (plan Finding B) instead of being
    // forced to CLOCK_24H. Persisted values remain minutes-since-midnight either way — only the
    // picker's on-screen presentation changes.
    val timeFormat = if (DateFormat.is24HourFormat(context)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H

    if (stage == AddWindowStage.START_TIME) {
        LaunchedEffect(startPickerLaunched) {
            if (startPickerLaunched) return@LaunchedEffect
            val activity = context.findActivity()
            if (activity == null) {
                stage = AddWindowStage.ERROR
                return@LaunchedEffect
            }
            startPickerLaunched = true
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(timeFormat)
                .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
                .apply {
                    if (initial != null && !initial.allDay) {
                        setHour(initial.startMinuteOfDay / 60)
                        setMinute(initial.startMinuteOfDay % 60)
                    }
                }
                .build()
            picker.addOnPositiveButtonClickListener {
                startMinute = picker.hour * 60 + picker.minute
                startPickerLaunched = false
                stage = AddWindowStage.END_TIME
            }
            picker.addOnCancelListener {
                startPickerLaunched = false
                onDone(null)
            }
            picker.addOnDismissListener { startPickerLaunched = false }
            picker.show(activity.supportFragmentManager, "reader_schedule_start_time")
        }
        return
    }

    if (stage == AddWindowStage.END_TIME) {
        LaunchedEffect(endPickerLaunched) {
            if (endPickerLaunched) return@LaunchedEffect
            val activity = context.findActivity()
            if (activity == null) {
                stage = AddWindowStage.ERROR
                return@LaunchedEffect
            }
            endPickerLaunched = true
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(timeFormat)
                .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
                .apply {
                    if (initial != null && !initial.allDay) {
                        setHour(initial.endMinuteOfDay / 60)
                        setMinute(initial.endMinuteOfDay % 60)
                    }
                }
                .build()
            picker.addOnPositiveButtonClickListener {
                val endMinute = picker.hour * 60 + picker.minute
                endPickerLaunched = false
                if (startMinute != endMinute) {
                    onDone(ReaderScheduleWindow(weekdays, startMinute, endMinute, allDay = false))
                } else {
                    stage = AddWindowStage.INVALID_TIME
                }
            }
            picker.addOnCancelListener {
                endPickerLaunched = false
                onDone(null)
            }
            picker.addOnDismissListener { endPickerLaunched = false }
            picker.show(activity.supportFragmentManager, "reader_schedule_end_time")
        }
        return
    }
}

internal fun DayOfWeek.shortDisplayName(locale: Locale): String = getDisplayName(TextStyle.SHORT, locale)

internal fun describeWindow(
    window: ReaderScheduleWindow,
    locale: Locale,
    wholeDayLabel: String,
    formatTime: (Int) -> String,
): String {
    val days = window.weekdays.sortedBy { it.value }.joinToString(", ") { it.shortDisplayName(locale) }
    if (window.allDay) return "$days ($wholeDayLabel)"
    return "$days ${formatTime(window.startMinuteOfDay)}-${formatTime(window.endMinuteOfDay)}"
}

private fun formatScheduleTime(context: Context, minuteOfDay: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        set(Calendar.MINUTE, minuteOfDay % 60)
    }
    return DateFormat.getTimeFormat(context).format(calendar.time)
}
// KMK <--
// KMK <--
