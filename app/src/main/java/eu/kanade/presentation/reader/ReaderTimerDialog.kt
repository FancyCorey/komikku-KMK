package eu.kanade.presentation.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import eu.kanade.tachiyomi.ui.reader.timer.ReaderTimerGracePolicy
import eu.kanade.tachiyomi.ui.reader.timer.ReaderTimerPhase
import eu.kanade.tachiyomi.ui.reader.timer.ReaderTimerSession
import eu.kanade.tachiyomi.ui.reader.timer.ReaderTimerWarningPolicy
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

// KMK v0.8.4 -->
/**
 * Compact active-reading timer dialog. Presets (15/30/60 min) or a bounded custom duration to
 * start; warning-threshold checkboxes and post-expiry behavior are chosen at Start time, since they
 * are session parameters, not persistent global settings (see the implementation report's "Known
 * limitations" section for the deliberate decision not to remember the last-used configuration).
 */
@Composable
fun ReaderTimerDialog(
    onDismissRequest: () -> Unit,
    session: ReaderTimerSession,
    onStart: (durationMs: Long, warningPolicy: ReaderTimerWarningPolicy, gracePolicy: ReaderTimerGracePolicy) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onStop: () -> Unit,
    onConfigureSchedule: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(KMR.strings.reading_timer_title)) },
        text = {
            Column {
                if (session.phase == ReaderTimerPhase.IDLE || session.phase == ReaderTimerPhase.EXPIRED) {
                    ReaderTimerSetupContent(onStart = onStart)
                } else {
                    ReaderTimerRunningContent(session = session, onPause = onPause, onResume = onResume, onReset = onReset)
                }
                TextButton(
                    onClick = onConfigureSchedule,
                    modifier = Modifier.fillMaxWidth().padding(top = MaterialTheme.padding.small),
                ) {
                    Text(stringResource(KMR.strings.reading_schedule_configure))
                }
            }
        },
        confirmButton = {
            if (session.phase != ReaderTimerPhase.IDLE) {
                TextButton(onClick = {
                    onStop()
                    onDismissRequest()
                }) {
                    Text(stringResource(KMR.strings.reading_timer_stop))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(tachiyomi.i18n.MR.strings.action_close))
            }
        },
    )
}

@Composable
private fun ReaderTimerSetupContent(
    onStart: (durationMs: Long, warningPolicy: ReaderTimerWarningPolicy, gracePolicy: ReaderTimerGracePolicy) -> Unit,
) {
    var customMinutes by rememberSaveable { mutableStateOf("") }
    var warnMinutesSelected by rememberSaveable { mutableStateOf(setOf<Int>()) }
    var finishCurrentChapter by rememberSaveable { mutableStateOf(true) }
    var allowExtraChapter by rememberSaveable { mutableStateOf(false) }

    fun start(minutes: Int) {
        val bounded = minutes.coerceIn(1, MAX_CUSTOM_MINUTES)
        onStart(
            bounded * 60_000L,
            ReaderTimerWarningPolicy(warnMinutesSelected),
            ReaderTimerGracePolicy(finishCurrentChapter = finishCurrentChapter, allowExtraChapter = allowExtraChapter),
        )
    }

    Column {
        Text(
            text = stringResource(KMR.strings.reading_timer_pause_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = MaterialTheme.padding.small),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
            listOf(15, 30, 60).forEach { minutes ->
                OutlinedButton(onClick = { start(minutes) }) {
                    Text(stringResource(KMR.strings.reading_timer_preset_minutes, minutes))
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = MaterialTheme.padding.medium),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            OutlinedTextField(
                value = customMinutes,
                onValueChange = { new -> if (new.length <= 3 && new.all(Char::isDigit)) customMinutes = new },
                label = { Text(stringResource(KMR.strings.reading_timer_custom_minutes)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = { customMinutes.toIntOrNull()?.let(::start) },
                enabled = customMinutes.toIntOrNull()?.let { it in 1..MAX_CUSTOM_MINUTES } == true,
            ) {
                Text(stringResource(KMR.strings.reading_timer_start))
            }
        }

        Text(
            text = stringResource(KMR.strings.reading_timer_warnings_header),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = MaterialTheme.padding.medium),
        )
        ReaderTimerWarningPolicy.SUPPORTED_MINUTES.sortedDescending().forEach { minute ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = minute in warnMinutesSelected,
                    onCheckedChange = { checked ->
                        warnMinutesSelected = if (checked) warnMinutesSelected + minute else warnMinutesSelected - minute
                    },
                )
                Text(stringResource(KMR.strings.reading_timer_warning_minutes_option, minute))
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = MaterialTheme.padding.small)) {
            Checkbox(checked = finishCurrentChapter, onCheckedChange = { finishCurrentChapter = it })
            Text(stringResource(KMR.strings.reading_timer_finish_chapter))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = allowExtraChapter,
                onCheckedChange = { allowExtraChapter = it },
                enabled = finishCurrentChapter,
            )
            Text(stringResource(KMR.strings.reading_timer_allow_extra_chapter))
        }
    }
}

@Composable
private fun ReaderTimerRunningContent(
    session: ReaderTimerSession,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
) {
    val phaseLabel = when (session.phase) {
        ReaderTimerPhase.RUNNING -> stringResource(KMR.strings.reading_timer_status_running)
        ReaderTimerPhase.PAUSED -> stringResource(KMR.strings.reading_timer_status_paused)
        ReaderTimerPhase.CHAPTER_GRACE -> stringResource(KMR.strings.reading_timer_status_chapter_grace)
        ReaderTimerPhase.EXTRA_CHAPTER_GRACE -> stringResource(KMR.strings.reading_timer_status_extra_chapter_grace)
        ReaderTimerPhase.EXPIRED -> stringResource(KMR.strings.reading_timer_status_expired)
        ReaderTimerPhase.IDLE -> ""
    }
    Column {
        Text(phaseLabel, style = MaterialTheme.typography.bodyMedium)
        if (session.phase == ReaderTimerPhase.RUNNING || session.phase == ReaderTimerPhase.PAUSED) {
            val remainingSeconds = ((session.totalDurationMs - session.elapsedActiveMs).coerceAtLeast(0L) / 1000L)
            Text(
                text = stringResource(
                    KMR.strings.reading_timer_remaining,
                    "%d:%02d".format(remainingSeconds / 60, remainingSeconds % 60),
                ),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = MaterialTheme.padding.small),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            modifier = Modifier.padding(top = MaterialTheme.padding.medium),
        ) {
            if (session.phase == ReaderTimerPhase.PAUSED) {
                OutlinedButton(onClick = onResume) { Text(stringResource(KMR.strings.reading_timer_resume)) }
            } else if (session.isActivelyCounting) {
                OutlinedButton(onClick = onPause) { Text(stringResource(KMR.strings.reading_timer_pause)) }
            }
            OutlinedButton(onClick = onReset) { Text(stringResource(KMR.strings.reading_timer_reset)) }
        }
    }
}

private const val MAX_CUSTOM_MINUTES = 300
// KMK <--
