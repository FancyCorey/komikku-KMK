package eu.kanade.presentation.track

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.kanade.tachiyomi.ui.manga.track.TrackItem
import eu.kanade.tachiyomi.ui.manga.track.TrackerEntry
import eu.kanade.test.DummyTracker
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tachiyomi.domain.track.model.Track
import tachiyomi.domain.tracker.model.LocalTrackedWork
import tachiyomi.domain.tracker.model.LocalTrackedWorkStatus
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * R5 correction: genuine Compose semantics coverage for [TrackInfoDialogHome]'s [TrackerEntry.Local]
 * row, per the R5 exit gate's explicit "source analogy alone is insufficient." Renders the real
 * production composable (built through the real [TrackerEntry.build] production owner) and queries
 * the actual semantics tree via [androidx.compose.ui.test], not a source-text guard.
 */
@RunWith(AndroidJUnit4::class)
class TrackInfoDialogHomeSemanticsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    private fun trackItem(id: Long, name: String) = TrackItem(
        track = null,
        tracker = DummyTracker(id = id, name = name),
    )

    private fun localWork(status: LocalTrackedWorkStatus) = LocalTrackedWork(
        id = "work-1",
        title = "Example",
        normalizedTitle = "example",
        status = status,
        lastChapterSource = null,
        lastChapterNumber = null,
        lastChapterUrl = null,
        lastChapterLabel = null,
        lastProgressAt = null,
        createdAt = 1000L,
        updatedAt = 1000L,
    )

    private fun setContent(
        entries: List<TrackerEntry>,
        onLocalClick: () -> Unit = {},
        onLocalOtherVersionsClick: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            TrackInfoDialogHome(
                entries = entries,
                dateFormat = dateFormat,
                onStatusClick = {},
                onChapterClick = {},
                onScoreClick = {},
                onStartDateEdit = {},
                onEndDateEdit = {},
                onNewSearch = {},
                onOpenInBrowser = {},
                onRemoved = {},
                onCopyLink = {},
                onTogglePrivate = {},
                onLocalClick = onLocalClick,
                onLocalOtherVersionsClick = onLocalOtherVersionsClick,
            )
        }
    }

    @Test
    fun localRowIsVisibleAlongsideExternalRowsInTheSameComposition() {
        val entries = TrackerEntry.build(
            trackItems = listOf(trackItem(1L, "AniList"), trackItem(2L, "MyAnimeList")),
            localWork = null,
        )

        setContent(entries)

        // Both External rows (unconfigured -> "Add tracking" per TrackInfoItemEmpty) and the Local
        // row ("Track locally", work == null) must coexist in the same rendered semantics tree --
        // not merely in the entries list, which the production-bound coexistence test already
        // covers at the data level.
        composeTestRule.onNodeWithText("Track locally").assertExists()
        composeTestRule.onAllNodesWithText("Add tracking").assertCountEquals(2)
    }

    @Test
    fun localRowAccessibleNameReflectsCurrentTrackedStatus() {
        val entries = TrackerEntry.build(
            trackItems = listOf(trackItem(1L, "AniList")),
            localWork = localWork(LocalTrackedWorkStatus.READING),
        )

        setContent(entries)

        // "Local tracking: %1$s" with the Reading status label -- proves the Local row's own
        // accessible text (what a screen reader would announce) reflects real tracked state, not a
        // generic label, while an External row is simultaneously present.
        composeTestRule.onNodeWithText("Local tracking: Reading").assertExists()
        composeTestRule.onNodeWithText("Add tracking").assertExists()
    }

    @Test
    fun localRowHasAClickActionAndInvokesOnlyItsOwnCallback() {
        var localClicks = 0
        val entries = TrackerEntry.build(
            trackItems = listOf(trackItem(1L, "AniList")),
            localWork = null,
        )

        setContent(entries, onLocalClick = { localClicks++ })

        val localNode = composeTestRule.onNodeWithText("Track locally")
        localNode.assertHasClickAction()
        localNode.performClick()

        assert(localClicks == 1) { "expected exactly one onLocalClick invocation, got $localClicks" }
    }

    @Test
    fun trackedLocalRowExposesOtherVersionsAction() {
        var clicks = 0
        setContent(
            entries = TrackerEntry.build(
                trackItems = emptyList(),
                localWork = localWork(LocalTrackedWorkStatus.READING),
            ),
            onLocalOtherVersionsClick = { clicks++ },
        )

        composeTestRule.onNodeWithText("Track other versions").assertHasClickAction().performClick()

        assert(clicks == 1) { "expected the other-versions action to invoke its callback once" }
    }

    @Test
    fun clickingAnExternalRowNeverInvokesTheLocalCallback() {
        var localClicks = 0
        val entries = TrackerEntry.build(
            trackItems = listOf(trackItem(1L, "AniList")),
            localWork = null,
        )

        setContent(entries, onLocalClick = { localClicks++ })

        composeTestRule.onNodeWithText("Add tracking").performClick()

        assert(localClicks == 0) { "clicking the External row must never trigger the Local row's own callback" }
    }

    @Test
    fun localRowRemainsPresentAndClickableWhenNoExternalTrackersExist() {
        val entries = TrackerEntry.build(trackItems = emptyList(), localWork = null)

        setContent(entries)

        composeTestRule.onNodeWithText("Track locally").assertHasClickAction()
    }
}
