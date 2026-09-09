package exh.uconfig

import android.content.Context
import exh.log.xLogE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface EHConfigurationState {
    data object Idle : EHConfigurationState
    data object AwaitingConfirmation : EHConfigurationState
    data object Running : EHConfigurationState
    data object Succeeded : EHConfigurationState
    data object Failed : EHConfigurationState
}

internal sealed interface EHConfigurationOutcome {
    data object Success : EHConfigurationOutcome
    data class Failure(val exception: Exception) : EHConfigurationOutcome
}

internal suspend fun runEHConfiguration(configure: suspend () -> Unit): EHConfigurationOutcome =
    try {
        configure()
        EHConfigurationOutcome.Success
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        EHConfigurationOutcome.Failure(e)
    }

/**
 * Process-scoped owner for the remote profile configuration operation.
 *
 * The settings screen can disappear while this operation is running, so its state cannot be owned by a
 * Composable. Only the application-level dialog host renders this state. The coordinator never retains or emits
 * an exception message; technical details go to the internal logger and ordinary UI receives [Failed].
 */
class EHConfigurationCoordinator(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val configure: suspend () -> Unit = {
        EHConfigurator(context.applicationContext).configureAll()
    },
    private val onFailure: (Exception) -> Unit = { exception ->
        exception.xLogE("Configuration error!", exception)
    },
) {
    private val mutableState = MutableStateFlow<EHConfigurationState>(EHConfigurationState.Idle)
    val state: StateFlow<EHConfigurationState> = mutableState.asStateFlow()

    @Synchronized
    fun request(needsConfirmation: Boolean): Boolean {
        if (mutableState.value != EHConfigurationState.Idle) return false

        if (needsConfirmation) {
            mutableState.value = EHConfigurationState.AwaitingConfirmation
        } else {
            startLocked()
        }
        return true
    }

    @Synchronized
    fun confirm(): Boolean {
        if (mutableState.value != EHConfigurationState.AwaitingConfirmation) return false
        startLocked()
        return true
    }

    @Synchronized
    fun dismissConfirmation(): Boolean = transitionToIdle(EHConfigurationState.AwaitingConfirmation)

    @Synchronized
    fun retry(): Boolean {
        if (mutableState.value != EHConfigurationState.Failed) return false
        startLocked()
        return true
    }

    @Synchronized
    fun dismissFailure(): Boolean = transitionToIdle(EHConfigurationState.Failed)

    @Synchronized
    fun consumeSuccess(): Boolean = transitionToIdle(EHConfigurationState.Succeeded)

    private fun transitionToIdle(expected: EHConfigurationState): Boolean {
        if (mutableState.value != expected) return false
        mutableState.value = EHConfigurationState.Idle
        return true
    }

    private fun startLocked() {
        mutableState.value = EHConfigurationState.Running
        scope.launch {
            val outcome = try {
                runEHConfiguration(configure)
            } catch (e: CancellationException) {
                synchronized(this@EHConfigurationCoordinator) {
                    transitionToIdle(EHConfigurationState.Running)
                }
                throw e
            }

            synchronized(this@EHConfigurationCoordinator) {
                if (mutableState.value != EHConfigurationState.Running) return@synchronized
                when (outcome) {
                    EHConfigurationOutcome.Success -> mutableState.value = EHConfigurationState.Succeeded
                    is EHConfigurationOutcome.Failure -> {
                        onFailure(outcome.exception)
                        mutableState.value = EHConfigurationState.Failed
                    }
                }
            }
        }
    }
}
