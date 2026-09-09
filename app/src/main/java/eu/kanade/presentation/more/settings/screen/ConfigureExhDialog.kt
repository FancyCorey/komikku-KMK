package eu.kanade.presentation.more.settings.screen

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import eu.kanade.tachiyomi.util.system.toast
import exh.source.ExhPreferences
import exh.uconfig.EHConfigurationCoordinator
import exh.uconfig.EHConfigurationState
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun ConfigureExhDialog(run: Boolean, onRunning: () -> Unit) {
    val exhPreferences = remember {
        Injekt.get<ExhPreferences>()
    }
    val coordinator = remember { Injekt.get<EHConfigurationCoordinator>() }
    val state by coordinator.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(run) {
        if (run) {
            coordinator.request(exhPreferences.exhShowSettingsUploadWarning().get())
            onRunning()
        }
    }

    if (state == EHConfigurationState.AwaitingConfirmation) {
        AlertDialog(
            onDismissRequest = coordinator::dismissConfirmation,
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
            confirmButton = {
                TextButton(
                    onClick = {
                        exhPreferences.exhShowSettingsUploadWarning().set(false)
                        coordinator.confirm()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            },
            title = {
                Text(text = stringResource(SYMR.strings.settings_profile_note))
            },
            text = {
                Text(text = stringResource(SYMR.strings.settings_profile_note_message))
            },
        )
    }
    if (state == EHConfigurationState.Running) {
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
            confirmButton = {},
            title = {
                Text(text = stringResource(SYMR.strings.eh_settings_uploading_to_server))
            },
            text = {
                Text(text = stringResource(SYMR.strings.eh_settings_uploading_to_server_message))
            },
        )
    }
    if (state == EHConfigurationState.Failed) {
        AlertDialog(
            onDismissRequest = coordinator::dismissFailure,
            confirmButton = {
                TextButton(onClick = { coordinator.retry() }) {
                    Text(text = stringResource(MR.strings.action_retry))
                }
            },
            dismissButton = {
                TextButton(onClick = { coordinator.dismissFailure() }) {
                    Text(text = stringResource(MR.strings.action_close))
                }
            },
            title = {
                Text(text = stringResource(SYMR.strings.eh_settings_configuration_failed))
            },
            text = {
                Text(text = stringResource(SYMR.strings.eh_settings_configuration_failed_message_safe))
            },
        )
    }

    LaunchedEffect(state) {
        if (state == EHConfigurationState.Succeeded) {
            context.toast(SYMR.strings.eh_settings_successfully_uploaded)
            coordinator.consumeSuccess()
        }
    }
}

/** Dispatches a request to the application-level [ConfigureExhDialog] host without rendering a second dialog. */
@Composable
fun RequestExhConfiguration(run: Boolean, onRunning: () -> Unit) {
    val exhPreferences = remember { Injekt.get<ExhPreferences>() }
    val coordinator = remember { Injekt.get<EHConfigurationCoordinator>() }
    LaunchedEffect(run) {
        if (run) {
            coordinator.request(exhPreferences.exhShowSettingsUploadWarning().get())
            onRunning()
        }
    }
}
