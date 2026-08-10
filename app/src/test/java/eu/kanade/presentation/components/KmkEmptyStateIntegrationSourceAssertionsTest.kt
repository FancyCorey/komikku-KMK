package eu.kanade.presentation.components

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

// KMK -->
/**
 * Source-level assertions proving each `KmkEmptyStateIllustration(...)` call site sits inside its
 * intended semantic branch and nowhere else -- specifically that it is textually absent from the
 * loading/success branches of the same file. A full Compose UI test harness (Robolectric or device
 * instrumentation) is not used here, per this project's standing preference for narrow JVM-only tests;
 * this is the source-level assertion alternative used here to prove each illustration appears only in
 * its intended branch.
 *
 * These are deliberately coarse (line-range / substring checks on the raw file text, not an AST parse)
 * -- they exist to catch a call site being added to (or accidentally left in) the wrong branch during a
 * future edit, not to fully verify runtime behavior. Reads relative to the `app` module directory,
 * which is Gradle's default working directory for `:app:testDebugUnitTest`.
 */
class KmkEmptyStateIntegrationSourceAssertionsTest {

    private fun readSource(path: String): String {
        val file = File(path)
        assertTrue(file.exists(), "expected source file at $path (relative to the app module directory) -- did it move?")
        return file.readText()
    }

    /** The exact substring every wired call site uses -- makes intent obvious in failure messages. */
    private fun assertIllustrationOnlyBetween(source: String, artwork: String, mustContainMarker: String, mustNotContain: List<String>) {
        val callSiteCount = Regex("KmkEmptyStateIllustration\\(").findAll(source).count()
        assertTrue(callSiteCount >= 1, "expected at least one KmkEmptyStateIllustration(...) call for $artwork")

        val markerIndex = source.indexOf(mustContainMarker)
        assertTrue(markerIndex >= 0, "expected to find the anchor '$mustContainMarker' near the $artwork call site")

        mustNotContain.forEach { forbidden ->
            // The forbidden branch text and the illustration call must never appear in the same
            // conditional block -- approximated here by requiring the forbidden branch's own source
            // slice (found independently) not to itself contain "KmkEmptyStateIllustration(".
            val forbiddenIndex = source.indexOf(forbidden)
            if (forbiddenIndex >= 0) {
                val nextBranchBoundary = source.indexOf("\n        state.", forbiddenIndex + forbidden.length)
                    .takeIf { it > 0 } ?: (forbiddenIndex + 400).coerceAtMost(source.length)
                val forbiddenSlice = source.substring(forbiddenIndex, nextBranchBoundary.coerceAtMost(source.length))
                assertFalse(
                    forbiddenSlice.contains("KmkEmptyStateIllustration("),
                    "did not expect $artwork's illustration inside the '$forbidden' branch",
                )
            }
        }
    }

    @Test
    fun `For You illustration is wired only into profileIsEmpty and hasNoResults, never loading or offline`() {
        val source = readSource("src/main/java/exh/recs/BrowsePersonalRecommendationsTab.kt")

        assertTrue(source.contains("state.profileIsEmpty -> {"))
        assertTrue(source.contains("if (hasNoResults) {"))
        assertIllustrationOnlyBetween(
            source,
            artwork = "FOR_YOU",
            mustContainMarker = "KmkEmptyStateArtwork.FOR_YOU",
            mustNotContain = listOf("state.isLoading -> {", "state.isOffline -> {"),
        )
        assertTrue(source.contains("state.isLoading -> {"), "the loading branch must still exist, untouched")
        assertTrue(source.contains("state.isOffline -> {"), "the offline branch must still exist, untouched")
    }

    @Test
    fun `Action History illustration is wired only into the rows-isEmpty branch, history rows are preserved`() {
        val source = readSource("src/main/java/exh/util/EvaluationModeActionHistoryScreen.kt")

        assertTrue(source.contains("if (rows.isEmpty()) {"))
        assertTrue(source.contains("KmkEmptyStateArtwork.ACTION_HISTORY"))
        assertTrue(source.contains("} else {"), "the non-empty rows-rendering branch must still exist")
        // The illustration call must appear before the "} else {" that renders the actual rows.
        val emptyBranchIndex = source.indexOf("if (rows.isEmpty()) {")
        val illustrationIndex = source.indexOf("KmkEmptyStateArtwork.ACTION_HISTORY")
        val elseBranchIndex = source.indexOf("} else {", emptyBranchIndex)
        assertTrue(emptyBranchIndex < illustrationIndex && illustrationIndex < elseBranchIndex)
    }

    @Test
    fun `Source Evaluation illustration is wired only into the past-evaluations-empty branch, setup and diagnostics untouched`() {
        val source = readSource("src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt")

        assertTrue(source.contains("state.evaluations.isEmpty() && queueState.isIdle"))
        assertTrue(source.contains("KmkEmptyStateArtwork.SOURCE_EVALUATION"))
        // Setup/diagnostics sections must still be present, unreplaced.
        assertTrue(source.contains("run_header"))
        assertTrue(source.contains("candidate_diagnostics"))
        assertTrue(source.contains("repo_unavailable"))
        // The illustration must not appear inside the running-evaluation progress card branch.
        assertIllustrationOnlyBetween(
            source,
            artwork = "SOURCE_EVALUATION",
            mustContainMarker = "KmkEmptyStateArtwork.SOURCE_EVALUATION",
            mustNotContain = listOf("if (queueState.isRunning) {"),
        )
    }

    @Test
    fun `Find Best Version illustration is wired only into the allCandidates-isEmpty branch`() {
        val source = readSource("src/main/java/exh/recs/bestversion/BestVersionCompareScreen.kt")

        assertTrue(source.contains("if (allCandidates.isEmpty()) {"))
        assertTrue(source.contains("KmkEmptyStateArtwork.FIND_BEST_VERSION_UNAVAILABLE"))
        assertTrue(source.contains("} else {"), "the non-empty candidates-rendering branch must still exist")
        val emptyBranchIndex = source.indexOf("if (allCandidates.isEmpty()) {")
        val illustrationIndex = source.indexOf("KmkEmptyStateArtwork.FIND_BEST_VERSION_UNAVAILABLE")
        val elseBranchIndex = source.indexOf("} else {", emptyBranchIndex)
        assertTrue(emptyBranchIndex < illustrationIndex && illustrationIndex < elseBranchIndex)
    }

    @Test
    fun `Reader Schedule illustration is wired only into windows-isEmpty, add-window action stays present`() {
        val source = readSource("src/main/java/eu/kanade/presentation/reader/ReaderScheduleDialog.kt")

        assertTrue(source.contains("if (windows.isEmpty()) {"))
        assertTrue(source.contains("KmkEmptyStateArtwork.READER_SCHEDULE"))
        assertTrue(source.contains("openAddFlow() }"), "the add-window action must remain present and reachable")
        val emptyBranchIndex = source.indexOf("if (windows.isEmpty()) {")
        val illustrationIndex = source.indexOf("KmkEmptyStateArtwork.READER_SCHEDULE")
        val addButtonIndex = source.indexOf("openAddFlow() }")
        assertTrue(emptyBranchIndex < illustrationIndex, "illustration must be inside the isEmpty() branch")
        assertTrue(illustrationIndex < addButtonIndex, "the add-window action must remain immediately after, not buried below a large illustration")
    }

    @Test
    fun `EmptyScreen preserves the random error face fallback when no illustration is supplied`() {
        val source = readSource("../presentation-core/src/main/java/tachiyomi/presentation/core/screens/EmptyScreen.kt")

        assertTrue(source.contains("illustration: (@Composable () -> Unit)? = null"))
        assertTrue(source.contains("if (illustration != null) {"))
        assertTrue(source.contains("getRandomErrorFace()"), "the fallback face generator must be unchanged")
    }
}
// KMK <--
