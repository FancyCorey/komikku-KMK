package exh.validation.route

import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.ScreenModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import java.util.concurrent.atomic.AtomicLong

/** Owns deterministic coroutine time and reverse-order cleanup for one host route test. */
@OptIn(InternalVoyagerApi::class)
class HostRouteTestEnvironment : AutoCloseable {
    val scheduler = TestCoroutineScheduler()
    val dispatcher = StandardTestDispatcher(scheduler)
    val scope = TestScope(dispatcher)
    val namespace = "host-route-${nextNamespace.incrementAndGet()}"

    private val cleanup = ArrayDeque<() -> Unit>()
    private var closed = false

    init {
        Dispatchers.setMain(dispatcher)
    }

    fun registerCleanup(action: () -> Unit) {
        check(!closed) { "host-route-environment-closed" }
        cleanup.addFirst(action)
    }

    inline fun <reified T : ScreenModel> ownScreenModel(model: T, tag: String? = null): T {
        val registered = ScreenModelStore.getOrPut<T>(namespace, tag) { model }
        check(registered === model) { "host-route-screen-model-key-collision" }
        return registered
    }

    inline fun <reified T : ScreenModel> createScreenModel(
        tag: String? = null,
        noinline factory: () -> T,
    ): T = ScreenModelStore.getOrPut(namespace, tag, factory)

    fun disposeScreenModels() {
        ScreenModelStore.onDisposeNavigator(namespace)
    }

    fun advanceUntilIdle() {
        scope.advanceUntilIdle()
    }

    fun runCurrent() {
        scope.runCurrent()
    }

    override fun close() {
        if (closed) return
        closed = true
        var failure: Throwable? = null
        cleanup.forEach { action ->
            try {
                action()
            } catch (error: Throwable) {
                failure = failure ?: error
            }
        }
        scope.cancel()
        scheduler.advanceUntilIdle()
        val activeJobs = scope.coroutineContext[Job]?.children?.count { it.isActive } ?: 0
        try {
            check(activeJobs == 0) { "host-route-active-jobs:$activeJobs" }
        } catch (error: Throwable) {
            failure = failure ?: error
        } finally {
            Dispatchers.resetMain()
        }
        failure?.let { throw it }
    }

    private companion object {
        val nextNamespace = AtomicLong()
    }
}
