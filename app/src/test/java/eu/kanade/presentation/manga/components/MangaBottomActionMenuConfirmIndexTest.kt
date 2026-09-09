package eu.kanade.presentation.manga.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

// KMK --> EC-04 2026-09-01: inherited-diff audit finding. MangaBottomActionMenu() previously had a
// real bug: the two new chapter-line preference buttons (`onSetChapterLinePreferenceClicked`/
// `onResetChapterLinePreferenceClicked`) were both wired to `confirm[4]`, the SAME slot already used
// by "mark previous as read". `onLongClickItem()` clears every OTHER index when arming one slot, so
// all three buttons shared one armed/confirm state: arming any one of them and then tapping a
// DIFFERENT one of the three would fire that other action without the user ever having long-pressed
// it specifically. Fixed by giving every button in this composable's `confirm` list its own unique
// index (0..8, one per button) and sizing the list to match. `MangaBottomActionMenu` can't be
// exercised directly in this pure-JVM test module (no Robolectric -- it needs a real Android
// Context), matching the same constraint documented by
// eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderPresentationAdoptionSourceTest, so this
// is a source-text regression test: it parses every `confirm[N]` reference in the function and
// asserts each index appears at most once, so a future edit that reintroduces a collision fails
// this test.
class MangaBottomActionMenuConfirmIndexTest {

    private val source by lazy {
        File("src/main/java/eu/kanade/presentation/manga/components/MangaBottomActionMenu.kt").readText()
    }

    /** Isolates just the `MangaBottomActionMenu` function body, not the unrelated `LibraryBottomActionMenu` below it. */
    private fun mangaBottomActionMenuBody(): String {
        val start = source.indexOf("fun MangaBottomActionMenu(")
        check(start >= 0) { "MangaBottomActionMenu function not found" }
        val end = source.indexOf("fun LibraryBottomActionMenu(", start)
        check(end > start) { "LibraryBottomActionMenu function not found after MangaBottomActionMenu" }
        return source.substring(start, end)
    }

    @Test
    fun `every confirm slot index used in MangaBottomActionMenu is unique`() {
        val body = mangaBottomActionMenuBody()
        val indices = Regex("""toConfirm = confirm\[(\d+)]""")
            .findAll(body)
            .map { it.groupValues[1].toInt() }
            .toList()

        assertEquals(
            indices.size,
            indices.toSet().size,
            "duplicate confirm[] index found in MangaBottomActionMenu -- two buttons sharing a slot " +
                "means arming one via long-press also arms the other, letting a single tap fire an " +
                "action that was never itself confirmed. Indices seen: $indices",
        )
    }

    @Test
    fun `the confirm state list is sized to cover every button's index`() {
        val body = mangaBottomActionMenuBody()
        val declaredSize = Regex("""mutableStateListOf\((false(?:, false)*)\)""")
            .find(body)
            ?.groupValues
            ?.get(1)
            ?.split(",")
            ?.size
            ?: error("confirm state list declaration not found")
        val highestIndex = Regex("""toConfirm = confirm\[(\d+)]""")
            .findAll(body)
            .map { it.groupValues[1].toInt() }
            .maxOrNull()
            ?: error("no confirm[] usage found")

        assertEquals(
            highestIndex + 1,
            declaredSize,
            "the confirm mutableStateListOf(...) size must be exactly (highest button index + 1) -- " +
                "found declared size $declaredSize but the highest used index is $highestIndex",
        )
    }
}
// KMK <--
