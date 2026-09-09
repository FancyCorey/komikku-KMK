package exh.validation.route

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class HostRouteTestEnvironmentTest {
    @Test
    fun `environment owns deterministic scheduling and reverse cleanup`() {
        val events = mutableListOf<String>()

        HostRouteTestEnvironment().use { environment ->
            environment.registerCleanup { events += "first" }
            environment.registerCleanup { events += "second" }
            environment.scope.launch {
                delay(500L)
                events += "work"
            }

            environment.advanceUntilIdle()
            assertEquals(listOf("work"), events)
        }

        assertEquals(listOf("work", "second", "first"), events)
    }

    @Test
    fun `each environment has a unique synthetic namespace`() {
        val first = HostRouteTestEnvironment()
        val second = HostRouteTestEnvironment()
        try {
            assertNotEquals(first.namespace, second.namespace)
        } finally {
            second.close()
            first.close()
        }
    }

    @Test
    fun `disposing one owned ScreenModel does not poison the next environment scope`() {
        HostRouteTestEnvironment().use { first ->
            val firstModel = first.ownScreenModel(FixtureScreenModel())
            val firstScope = firstModel.screenModelScope
            assertEquals(true, firstScope.coroutineContext[Job]?.isActive)
            first.disposeScreenModels()
            assertEquals(false, firstScope.coroutineContext[Job]?.isActive)
        }

        HostRouteTestEnvironment().use { second ->
            val secondModel = second.ownScreenModel(FixtureScreenModel())
            assertEquals(true, secondModel.screenModelScope.coroutineContext[Job]?.isActive)
            second.disposeScreenModels()
        }
    }

    private class FixtureScreenModel : ScreenModel
}
