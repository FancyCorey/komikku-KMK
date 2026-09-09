package exh.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pairs a dispatcher with its explicit ownership action.
 *
 * Externally supplied dispatchers default to a no-op close. Owned handles close
 * at most once, even when a lifecycle callback is invoked defensively twice.
 */
class DispatcherHandle(
    val dispatcher: CoroutineDispatcher,
    close: () -> Unit = {},
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val closeAction = close

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            closeAction()
        }
    }
}

fun ownedFixedThreadPoolDispatcherHandle(threads: Int = 5): DispatcherHandle {
    require(threads > 0) { "threads must be positive" }
    val dispatcher = Executors.newFixedThreadPool(threads).asCoroutineDispatcher()
    return DispatcherHandle(dispatcher = dispatcher, close = dispatcher::close)
}
