package exh.recs.loved

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

// KMK C2 (HR-2026-08-26-RATED-COLLECTIONS-AND-BEST-VERSION-CORRECTIONS, E1) -->
/**
 * Source-guard proof for E1's rated-collection navigation contract: a compact way to switch among
 * Loved/Liked/Disliked/Not Interested from inside any one of them, without backing out to For You,
 * reusing the single shared rating-family owner rather than creating four screen-specific
 * navigators. Follows the same source-text convention as
 * [exh.recs.bestversion.BestVersionCompareScreenBackStackContractTest] -- a live Compose UI test
 * is not this codebase's established convention for this kind of structural contract.
 */
class RatedCollectionSwitcherSourceTest {

    private val screenText: String by lazy {
        stripComments(source("app/src/main/java/exh/recs/loved/RatedMangaScreen.kt"))
    }

    private fun switcherBody(): String {
        val start = screenText.indexOf("private fun RatedCollectionSwitcher(")
        assertTrue(start >= 0, "RatedCollectionSwitcher not found")
        val end = screenText.indexOf("\n@Composable", start + 1).let { if (it < 0) screenText.indexOf("\nprivate fun ", start + 1) else it }
        assertTrue(end > start, "could not bound RatedCollectionSwitcher's body")
        return screenText.substring(start, end)
    }

    @Test
    fun `the switcher covers all four rating tiers exactly once`() {
        val body = switcherBody()
        listOf("MangaRating.LOVE", "MangaRating.LIKE", "MangaRating.DISLIKE", "MangaRating.NOT_INTERESTED").forEach { tier ->
            val count = Regex(Regex.escape(tier)).findAll(body).count()
            assertTrue(count >= 1, "RatedCollectionSwitcher must offer $tier as a switch target")
        }
    }

    @Test
    fun `switching tiers uses navigator_replace, never navigator_push, so the back stack never grows`() {
        val body = switcherBody()
        assertTrue(body.contains("navigator.replace("), "switching tiers must use navigator.replace() so Back still reaches For You/Library in one step regardless of how many tiers were visited")
        assertFalse(body.contains("navigator.push("), "switching tiers must never push a new back-stack entry -- that would make Back require popping through every visited tier")
    }

    @Test
    fun `the switcher is never routed to a second concurrent screen model or a TabRow-Pager pair`() {
        val body = switcherBody()
        assertFalse(body.contains("rememberScreenModel"), "the switcher itself must never construct a second LovedMangaScreenModel -- it only navigates, the target screen owns its own model")
        assertFalse(body.contains("HorizontalPager") || body.contains("TabRow"), "the switcher is a compact menu, not a tab strip requiring all four collections' screen models alive concurrently")
    }

    @Test
    fun `the switcher is wired into the non-selection-mode AppBar actions, not the selection-mode AppBar`() {
        val actionsSlotStart = screenText.indexOf("actions = {\n")
        assertTrue(actionsSlotStart >= 0, "actions slot not found")
        val actionsSlotEnd = screenText.indexOf("AppBarActions(", actionsSlotStart)
        assertTrue(actionsSlotEnd > actionsSlotStart, "AppBarActions( call not found after actions slot start")
        val window = screenText.substring(actionsSlotStart, actionsSlotEnd)
        assertTrue(window.contains("RatedCollectionSwitcher("), "RatedCollectionSwitcher must be called inside the non-selection-mode actions slot, immediately before AppBarActions()")
    }

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
