package eu.kanade.tachiyomi.ui.reader.bridge

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

// KMK -->
/**
 * EC-02 (KFC-V0.8.21-FIX2-CORRECTIVE-RECHECK-AND-RELEASE-PROGRAM): confirmed device-level defect,
 * 2026-08-30. [ReaderViewModel.alternateSourceEntryRequest] and [ReaderTransitionView.bind] already
 * accepted a nullable `followingChapterId` (the domain-level `PRIMARY_MISSING`-with-only-a-preceding-
 * -anchor fix), but the PRESENTATION layer -- `ChapterTransition.kt`'s `TransitionText` -- never
 * actually rendered the gap-entry action when there was no next chapter at all: its `else` branch
 * (reached whenever `bottomChapter == null`, i.e. genuine end-of-source, not merely a numbered gap
 * between two known chapters -- `calculateChapterGap` returns 0 whenever either chapter is null, so
 * the `chapterGap > 0` branch above it is structurally unreachable here) rendered only a plain
 * [NoChapterNotification] with no action, regardless of whether the caller supplied
 * `onChapterGapAction`. Confirmed live on `KFC_PerfClone_API30` using the Fixture Pair A "Exact
 * missing section" scenario: reading to the end of the last primary-source chapter showed "There's
 * no next chapter" with no continuity action.
 *
 * This is a source-text regression test in the same convention as
 * [AlternateSourceReaderPresentationAdoptionSourceTest] (a Compose function in this module has no
 * lightweight behavioral test harness here) -- it asserts the exact fixed code shape is present,
 * not merely that some string exists nearby, so a future edit that reintroduces the unconditional
 * `NoChapterNotification(text = fallbackLabel, modifier = ...)` call (dropping the action) fails
 * this test.
 */
class AlternateSourceContinuityPresentationEndOfSourceTest {

    private val chapterTransitionSource by lazy {
        File("src/main/java/eu/kanade/presentation/reader/ChapterTransition.kt").readText()
    }

    @Test
    fun `end-of-source branch forwards the gap action into NoChapterNotification`() {
        assertTrue(
            chapterTransitionSource.contains(
                "NoChapterNotification(\n" +
                    "                text = fallbackLabel,\n" +
                    "                onAlternateSourceGap = onChapterGapAction,",
            ),
        )
    }

    @Test
    fun `NoChapterNotification renders the same gap action button as the mid-list warning`() {
        // Both call sites must resolve to the SAME button copy (alternate_source_reader_gap_action)
        // so a user sees consistent wording whether the gap is a numbered mid-list gap or a genuine
        // end-of-source PRIMARY_MISSING mapping.
        val occurrences = Regex("stringResource\\(KMR\\.strings\\.alternate_source_reader_gap_action\\)")
            .findAll(chapterTransitionSource)
            .count()
        assertTrue(
            occurrences == 2,
            "expected exactly 2 usages (ChapterGapWarning + NoChapterNotification), found $occurrences",
        )
    }

    @Test
    fun `NoChapterNotification signature accepts an optional alternate-source action`() {
        assertTrue(
            chapterTransitionSource.contains("onAlternateSourceGap: (() -> Unit)? = null,"),
        )
    }
}
// KMK <--
