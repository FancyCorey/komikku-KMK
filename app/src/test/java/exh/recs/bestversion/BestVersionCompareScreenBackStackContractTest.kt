package exh.recs.bestversion

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

// KMK C2 (HR-2026-08-26-RATED-COLLECTIONS-AND-BEST-VERSION-CORRECTIONS, E3) -->
/**
 * Source-guard proof for the E3 re-audit's Back-stack requirement: "Evaluate the back stack from
 * candidate selection, chapter selection, comparison, and fullscreen preview. The user wants to
 * inspect candidates/pages individually without unexpectedly leaving the entire Best Version
 * workflow."
 *
 * Reading the real BestVersionCompareScreen.kt confirms the contract already holds structurally,
 * not by convention:
 * - The three candidate/page-inspection surfaces (the reader-quality full-chapter fullscreen
 *   preview, the earlier bounded-sample fullscreen page preview, and the manual chapter picker)
 *   are each rendered via Compose's own [androidx.compose.ui.window.Dialog], whose platform
 *   Android Dialog intercepts the system Back button BEFORE it ever reaches this screen's Voyager
 *   Navigator (dismissOnBackPress defaults to true) -- Back while any of these is open closes only
 *   that dialog and calls its own `onDismiss`/`onDismissRequest`, never the screen's own
 *   `navigator.pop()`.
 * - Each of those three dialogs' dismiss callbacks only clears local Compose state (and, for the
 *   candidate comparison preview, calls the screen model's own `closeFullscreenCandidate()`) --
 *   never `navigator.pop()` or `navigator.replace()`. This is what actually prevents "candidate
 *   inspection" (opening a page/candidate preview) from unexpectedly exiting the whole workflow.
 * - `navigator.pop()`/`navigator.replace()` appear only at the three genuine, intentional
 *   workflow-exit points: the top app bar's Up action, the terminal Error step's explicit "back"
 *   button, and the terminal Done step's "Open"/fallback action -- never inside a dialog dismiss
 *   callback, and never at the top-level candidate-selection/chapter-selection/comparison steps
 *   themselves (which have no back-stack of their own -- Back there falls through to the app bar's
 *   Up action, the same as any other single-screen step flow, which is the expected/unsurprising
 *   behavior this audit was not asked to change).
 *
 * This is a source-guard, not a live Espresso/Compose UI test (this codebase's established
 * convention for this kind of structural contract -- see [BestVersionPreviewReadOnlyBoundarySourceTest]
 * for the identical pattern) -- it fails loudly if a future change wires a dialog's dismiss to
 * `navigator.pop()`/`navigator.replace()`, which would reintroduce exactly the defect this audit
 * confirms does not currently exist.
 */
class BestVersionCompareScreenBackStackContractTest {

    private val screenText: String by lazy {
        stripComments(source("app/src/main/java/exh/recs/bestversion/BestVersionCompareScreen.kt"))
    }

    @Test
    fun `the fullscreen page preview dialog is a real Dialog, not a full-screen Composable outside the back stack`() {
        val body = dialogInvocationBody("FullscreenPagePreviewDialog")
        assertTrue(body.contains("Dialog("), "FullscreenPagePreviewDialog must be implemented via androidx.compose.ui.window.Dialog so Back closes it, not the whole screen")
    }

    @Test
    fun `the fullscreen candidate comparison preview dialog is a real Dialog`() {
        val body = dialogInvocationBody("FullscreenCandidatePreviewDialog")
        assertTrue(body.contains("Dialog("), "FullscreenCandidatePreviewDialog must be implemented via androidx.compose.ui.window.Dialog so Back closes it, not the whole screen")
    }

    @Test
    fun `the manual chapter picker dialog is a real Dialog`() {
        val body = dialogInvocationBody("ManualChapterPickerDialog")
        assertTrue(body.contains("Dialog("), "ManualChapterPickerDialog must be implemented via androidx.compose.ui.window.Dialog so Back closes it, not the whole screen")
    }

    @Test
    fun `fullscreen candidate inspection keeps pinch and pan enabled in continuous mode`() {
        val body = dialogInvocationBody("FullscreenCandidatePreviewDialog")
        assertTrue(body.contains("zoomable = true"), "fullscreen candidate pages must support pinch and pan")
        assertFalse(body.contains("zoomable = false"), "continuous/webtoon fullscreen pages must not disable pinch and pan")
    }

    @Test
    fun `dismissing the fullscreen page preview never pops or replaces the navigator`() {
        val callSite = callSiteBody("FullscreenPagePreviewDialog(")
        assertOnDismissNeverNavigates(callSite, "FullscreenPagePreviewDialog")
    }

    @Test
    fun `dismissing the fullscreen candidate comparison preview never pops or replaces the navigator`() {
        val callSite = callSiteBody("FullscreenCandidatePreviewDialog(")
        assertOnDismissNeverNavigates(callSite, "FullscreenCandidatePreviewDialog")
    }

    @Test
    fun `dismissing the manual chapter picker never pops or replaces the navigator`() {
        val callSite = callSiteBody("ManualChapterPickerDialog(")
        assertOnDismissNeverNavigates(callSite, "ManualChapterPickerDialog")
    }

    @Test
    fun `dismissing the migration confirm dialog never pops or replaces the navigator`() {
        val callSite = callSiteBody("MigrationConfirmDialog(")
        assertOnDismissNeverNavigates(callSite, "MigrationConfirmDialog")
    }

    // KMK: bounds the blast radius the other way -- proves every real workflow-exit call site is one
    // of the three intentional ones (app bar Up, terminal Error's back button, terminal Done's
    // Open/fallback action), so a stray navigator.pop()/replace() added to some new step later would
    // fail this count instead of silently slipping in unexamined.
    @Test
    fun `navigator pop or replace appears only at the four intentional terminal exit points`() {
        // KMK: the app bar's Up action is a method reference (navigator::pop, double-colon), not a
        // call (navigator.pop(), dot) -- matched separately since the two use different separators.
        val exitCallSites = Regex("navigator\\.(pop\\(|replace\\()|navigator::pop\\b").findAll(screenText).count()
        assertTrue(
            exitCallSites == 4,
            "expected exactly 4 navigator exit references (app bar Up via navigator::pop, the Done " +
                "step's navigator.replace()/pop() fallback, and the Error step's navigator.pop()) but " +
                "found $exitCallSites -- a new one may have been added inside a dialog dismiss path",
        )
    }

    private fun assertOnDismissNeverNavigates(callSiteBody: String, dialogName: String) {
        assertFalse(
            callSiteBody.contains("navigator.pop") || callSiteBody.contains("navigator.replace"),
            "$dialogName's onDismiss/onDismissRequest callback (as passed at its call site) must never call navigator.pop()/replace() -- " +
                "closing a candidate/page inspection dialog must never exit the whole Best Version workflow.",
        )
    }

    /** Extracts the body of the named dialog's own `@Composable private fun` definition. */
    private fun dialogInvocationBody(functionName: String): String {
        val defIndex = screenText.indexOf("private fun $functionName(")
        assertTrue(defIndex >= 0, "$functionName definition not found")
        val nextFunIndex = screenText.indexOf("\nprivate fun ", defIndex + 1)
            .let { if (it < 0) screenText.indexOf("\n@Composable", defIndex + 1) else it }
        val endIndex = if (nextFunIndex > defIndex) nextFunIndex else screenText.length
        return screenText.substring(defIndex, endIndex)
    }

    /** Extracts a bounded window of text around where [functionName] is CALLED (not defined). */
    private fun callSiteBody(callPrefix: String): String {
        val callIndex = screenText.indexOf(callPrefix)
        assertTrue(callIndex >= 0, "no call site found for $callPrefix")
        // KMK: the call site's own argument list (onDismiss = { ... }) is always well within a few
        // hundred characters of the call -- bounding the window keeps this from accidentally scanning
        // into unrelated code below it while still covering the full lambda body.
        val endIndex = minOf(callIndex + 600, screenText.length)
        return screenText.substring(callIndex, endIndex)
    }

    // KMK: identical convention to BestVersionPreviewReadOnlyBoundarySourceTest.stripComments --
    // strips `//` line comments and `/* ... */` block comments so doc-comment prose that mentions
    // "navigator.pop()" in passing (as this very test class's own KDoc does) never falsely trips a
    // guard scanning compiled code.
    private fun stripComments(text: String): String {
        val noBlockComments = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL).replace(text, " ")
        return noBlockComments.lineSequence().joinToString(separator = "\n") { line ->
            val idx = line.indexOf("//")
            if (idx >= 0) line.substring(0, idx) else line
        }
    }

    private fun source(relativePath: String): String {
        val direct = Path.of(relativePath)
        val fromParent = Path.of("..").resolve(relativePath)
        val path = listOf(direct, fromParent)
            .map(Path::toAbsolutePath)
            .firstOrNull(Files::isRegularFile)
            ?: error("Unable to resolve source file: $relativePath")
        return String(Files.readAllBytes(path), Charsets.UTF_8)
    }
}
// KMK <--
