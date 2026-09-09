package eu.kanade.tachiyomi.ui.reader.bridge

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class AlternateSourceReaderPresentationAdoptionSourceTest {

    @Test
    fun `pager and webtoon use the same guarded gap entry intent`() {
        listOf(
            source("ui/reader/viewer/pager/PagerTransitionHolder.kt"),
            source("ui/reader/viewer/webtoon/WebtoonTransitionHolder.kt"),
        ).forEach { holder ->
            assertTrue(holder.contains("canOfferAlternateSourceGapAction()"))
            assertTrue(holder.contains("openAlternateSourceChooser(precedingId, followingId)"))
            assertTrue(holder.contains("else {\n                null\n            }"))
        }

        val transitionView = source("ui/reader/viewer/ReaderTransitionView.kt")
        assertTrue(transitionView.contains("transition is ChapterTransition.Next"))
        // KMK -->
        // Corrected 2026-08-30: this used to assert "precedingId != null && followingId != null",
        // which stopped matching the source once the end-of-source PRIMARY_MISSING fix made
        // followingId nullable here -- the gap-entry callback must still be offered with only a
        // preceding anchor, not just when both anchors are known. See
        // AlternateSourceContinuityPresentationEndOfSourceTest for the presentation-layer coverage
        // this enabled (ChapterTransition.kt actually rendering the action with no next chapter).
        assertTrue(transitionView.contains("precedingId != null && onAlternateSourceGap != null"))
        // KMK <--
        assertTrue(transitionView.contains("{ onAlternateSourceGap(precedingId, followingId) }"))
    }

    @Test
    fun `view model revalidates anchors and suppresses blocked or active sessions`() {
        val viewModel = source("ui/reader/ReaderViewModel.kt")
        assertTrue(viewModel.contains("if (!canOfferAlternateSourceGapAction()) return"))
        assertTrue(viewModel.contains("machineState.phase == AlternateSourceReaderPhase.PRIMARY"))
        assertTrue(viewModel.contains("machineState.session == null"))
        assertTrue(viewModel.contains("route.viewerChapters?.currChapter !== preceding"))
        assertTrue(viewModel.contains("precedingOwner.id != followingOwner.id"))
        assertTrue(viewModel.contains("invalidateAlternateSourceCandidateStateForRouteChange()"))
        assertTrue(viewModel.contains("if (alternateSourceCommandJob?.isActive == true) return"))
    }

    @Test
    fun `reader chooser preserves candidate identity while applying evaluation source privacy`() {
        val viewModel = source("ui/reader/ReaderViewModel.kt")
        assertTrue(viewModel.contains("AlternateSourceReaderPresentationPolicy.mangaCandidateRow"))
        assertTrue(viewModel.contains("AlternateSourceReaderPresentationPolicy.chapterCandidateRow"))
        assertTrue(viewModel.contains("EvaluationModeFormatter.sourceLabel(manga.source)"))
        assertFalse(viewModel.contains("genericLabel = AlternateSourceReaderGenericLabel"))
    }

    @Test
    fun `activity collection and schedule overlay keep lifecycle and blocking authority`() {
        val activity = source("ui/reader/ReaderActivity.kt")
        assertTrue(
            activity.contains(
                "viewModel.alternateSourcePresentation.collectAsStateWithLifecycle()",
            ),
        )
        assertTrue(activity.contains("if (!isBlockedBySchedule) {\n                AlternateSourceReaderDialog("))
        assertTrue(activity.contains("onDismiss = viewModel::dismissAlternateSourcePresentation"))
        assertTrue(activity.contains("onConfirmProvisional = viewModel::confirmProvisionalAlternateSourceSelection"))
    }

    @Test
    fun `initial reader chooser does not spend the explicit installed-source search`() {
        val viewModel = source("ui/reader/ReaderViewModel.kt")
        val entryPoint = viewModel.substringAfter("fun openAlternateSourceChooserForCurrentChapter()")
            .substringBefore("fun canOfferAlternateSourceChooser()")
        assertTrue(entryPoint.contains("includeLiveSearch = false"))
        assertTrue(viewModel.contains("fun searchAlternateSourceCandidates()"))
        assertTrue(
            viewModel.substringAfter("fun searchAlternateSourceCandidates()")
                .substringBefore("fun acceptAlternateSourceSearchResult")
                .contains("includeLiveSearch = true"),
        )
    }

    @Test
    fun `chapter discovery retry retains the selected source candidate`() {
        val viewModel = source("ui/reader/ReaderViewModel.kt")
        val candidateSelection = viewModel.substringAfter("private fun selectAlternateSourceCandidate")
            .substringBefore("fun selectAlternateSourceChapter")
        val retry = viewModel.substringAfter("fun retryAlternateSourcePresentation()")
            .substringBefore("fun acknowledgeAlternateSourceUncertainAlignment")

        assertTrue(candidateSelection.contains("retainedAlternateSourceMangaSelection = selection"))
        assertTrue(retry.contains("retainedAlternateSourceMangaSelection?.let"))
        assertTrue(retry.contains("selectAlternateSourceCandidate(it)"))
    }

    @Test
    fun `global search return reuses a visible candidate token`() {
        val viewModel = source("ui/reader/ReaderViewModel.kt")
        val searchReturn = viewModel.substringAfter("fun acceptAlternateSourceSearchResult")
            .substringBefore("fun toggleAlternateSourceCandidate")
        assertTrue(searchReturn.contains("existingToken"))
        assertTrue(searchReturn.contains("existing.any { row -> row.token == entry.key }"))
        assertTrue(searchReturn.contains("selectedToken = existingToken"))
        assertTrue(searchReturn.contains("val token = newAlternateSourceToken()"))
    }

    @Test
    fun `dialog is scrollable resource backed and uses accessible selection targets`() {
        val dialog = presentation("AlternateSourceReaderDialog.kt")
        assertTrue(dialog.contains("verticalScroll(rememberScrollState())"))
        assertTrue(dialog.contains("heightIn(max = 420.dp)"))
        assertTrue(dialog.contains("defaultMinSize(minHeight = 48.dp)"))
        assertTrue(dialog.contains("role = Role.RadioButton"))
        assertTrue(dialog.contains("stateDescription = label"))
        assertTrue(dialog.contains("stringResource(KMR.strings."))
        assertFalse(dialog.contains("Text(\""))
    }

    @Test
    fun `top bar exposes only contextual resource backed bridge actions`() {
        val topBar = presentation("appbars/ReaderTopBar.kt")
        listOf(
            "alternate_source_reader_return",
            "alternate_source_reader_correct",
            "alternate_source_reader_skip",
        ).forEach { resource -> assertTrue(topBar.contains(resource)) }
        assertTrue(topBar.contains("onReturnToPrimarySource?.let"))
        assertTrue(topBar.contains("onCorrectAlternateSourceMapping?.let"))
        assertTrue(topBar.contains("onSkipAlternateSourceChapter?.let"))
    }

    private fun source(path: String) = File("src/main/java/eu/kanade/tachiyomi/$path").readText()

    private fun presentation(path: String) = File("src/main/java/eu/kanade/presentation/reader/$path").readText()
}
