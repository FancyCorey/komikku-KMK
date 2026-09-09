package exh.recs

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class BrowsePersonalRecommendationsRefreshOwnershipTest {
    @Test
    fun `refresh requests cancel the whole prior load before starting another`() {
        val source = source()

        assertTrue(source.contains("private var loadJob: Job? = null"))
        assertTrue(source.contains("private val loadLaunchLock = Any()"))
        assertTrue(source.contains("loadJob?.cancel()"))
        assertTrue(source.contains("searchJob?.cancel()"))
        assertTrue(source.contains("currentCoroutineContext().ensureActive()"))
        assertTrue(source.contains("synchronized(loadLaunchLock)"))
    }

    @Test
    fun `refresh reserves a generation before launching cancellable work`() {
        val source = source()
        val reservation = source.indexOf("val refreshGeneration = mutableState.updateAndGet")
        val launch = source.indexOf("loadJob = screenModelScope.launch")

        assertTrue(reservation >= 0)
        assertTrue(launch > reservation)
        assertTrue(source.substring(reservation, launch).contains("resultGeneration + 1"))
        assertTrue(source.contains("refreshGeneration: Long"))
    }

    @Test
    fun `cancelled refresh cannot persist partial run summaries`() {
        val source = source()
        val cancellationGuard = source.indexOf("if (!isActive) return@launch")
        val persistenceStart = source.indexOf("// Persist strategy map and source run statuses")

        assertTrue(cancellationGuard >= 0)
        assertTrue(persistenceStart > cancellationGuard)
        assertTrue(
            source.substring(cancellationGuard, persistenceStart).contains("isActive"),
            "the cancellation guard must protect durable run-summary persistence",
        )
    }

    private fun source(): String {
        val relative = Path.of("app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt")
        val path = listOf(relative, Path.of("..").resolve(relative))
            .map(Path::toAbsolutePath)
            .firstOrNull(Files::isRegularFile)
            ?: error("Unable to resolve recommendation screen model")
        return Files.readAllBytes(path).toString(Charsets.UTF_8)
    }
}
