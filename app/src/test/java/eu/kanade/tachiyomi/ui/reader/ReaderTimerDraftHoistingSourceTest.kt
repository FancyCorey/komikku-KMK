package eu.kanade.tachiyomi.ui.reader

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Peirce's reader-timer/settings review found that an unstarted timer setup draft could be lost
 * when the schedule dialog replaced the timer dialog subtree, because the draft was owned by that
 * replaceable subtree. The fix hoists the draft into ReaderActivity's own composition (surviving
 * the subtree swap and, via rememberSaveable, process recreation) and makes ReaderTimerDialog take
 * it as parameters instead of owning it. This pins that contract at the source level -- a real
 * Compose recomposition/navigation test needs Robolectric, unavailable in this pure-JVM module.
 */
class ReaderTimerDraftHoistingSourceTest {

    private val activitySource = File("src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt").readText()
    private val dialogSource = File("src/main/java/eu/kanade/presentation/reader/ReaderTimerDialog.kt").readText()

    @Test
    fun `the timer setup draft is hoisted above the dialog subtree with a saveable state`() {
        assertTrue(activitySource.contains("var timerCustomMinutes by rememberSaveable"))
        assertTrue(activitySource.contains("var timerWarnMinutesSelected by rememberSaveable"))
        assertTrue(activitySource.contains("var timerFinishCurrentChapter by rememberSaveable"))
        assertTrue(activitySource.contains("var timerAllowExtraChapter by rememberSaveable"))
    }

    @Test
    fun `ReaderTimerDialog takes the draft as parameters instead of owning it internally`() {
        assertTrue(dialogSource.contains("customMinutes: String"))
        assertTrue(dialogSource.contains("warnMinutesSelected: Set<Int>"))
        assertTrue(dialogSource.contains("finishCurrentChapter: Boolean"))
        assertTrue(dialogSource.contains("allowExtraChapter: Boolean"))
        // No local `rememberSaveable`/`mutableStateOf` re-creation of the same fields inside the
        // dialog file -- that would silently reintroduce subtree-owned (and therefore
        // subtree-swap-losable) draft state alongside the hoisted one.
        assertFalse(dialogSource.contains("var customMinutes by"))
        assertFalse(dialogSource.contains("var warnMinutesSelected by"))
        assertFalse(dialogSource.contains("var finishCurrentChapter by"))
        assertFalse(dialogSource.contains("var allowExtraChapter by"))
        // Draft edits flow out through a callback rather than being mutated locally, so the hoisted
        // owner is the single source of truth for every field.
        assertTrue(dialogSource.contains("onDraftChange(new, warnMinutesSelected, finishCurrentChapter, allowExtraChapter)"))
        assertTrue(dialogSource.contains("onDraftChange(customMinutes, updated, finishCurrentChapter, allowExtraChapter)"))
        assertTrue(dialogSource.contains("onDraftChange(customMinutes, warnMinutesSelected, it, allowExtraChapter)"))
        assertTrue(dialogSource.contains("onDraftChange(customMinutes, warnMinutesSelected, finishCurrentChapter, it)"))
    }
}
