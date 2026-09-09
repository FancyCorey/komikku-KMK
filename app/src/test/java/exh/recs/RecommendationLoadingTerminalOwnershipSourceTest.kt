package exh.recs

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class RecommendationLoadingTerminalOwnershipSourceTest {
    @Test
    fun `recommendation loader terminates offline and empty profile states`() {
        val model = source("src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt")
        assertTrue(model.contains("isLoading = retryOnly == null"))
        assertTrue(model.contains("state.copy(isLoading = false)"))
        assertTrue(model.contains("profileIsEmpty = false"))
        assertTrue(model.contains("isOffline = false"))
        assertTrue(model.contains("State(isLoading = false, isOffline = true, resultGeneration = refreshGeneration)"))
        assertTrue(model.contains("it.copy(isLoading = false, profileIsEmpty = true)"))
    }

    @Test
    fun `recommendation renderer distinguishes loading offline empty and no results`() {
        val screen = source("src/main/java/exh/recs/BrowsePersonalRecommendationsTab.kt")
        assertTrue(screen.contains("state.isOffline"))
        assertTrue(screen.contains("state.isLoading"))
        assertTrue(screen.contains("state.isLoading && state.items.isEmpty()"))
        assertTrue(screen.contains("state.profileIsEmpty"))
        assertTrue(screen.contains("val hasNoResults = allDone && !hasCombined"))
        assertTrue(screen.contains("stringResource(KMR.strings.rec_for_you_offline)"))
    }

    @Test
    fun `visible exposure is guarded by generation and settled result policy`() {
        val model = source("src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt")
        val tab = source("src/main/java/exh/recs/BrowsePersonalRecommendationsTab.kt")
        assertTrue(model.contains("lastExposureRecordedGeneration"))
        assertTrue(model.contains("RecommendationExposureCapturePolicy.shouldRecord"))
        assertTrue(tab.contains("LaunchedEffect(state.resultGeneration, allDone)"))
        assertTrue(tab.contains("if (allDone) onVisibleResultsRendered()"))
    }

    private fun source(path: String): String = File(path).also {
        assertTrue(it.isFile, "expected source file at $path")
    }.readText()
}
