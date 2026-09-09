package eu.kanade.tachiyomi.ui.reader.bridge

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/** Keeps failed alternate-source journeys actionable at both chooser levels. */
class AlternateSourceReaderRecoverySourceTest {

    @Test
    fun `chapter chooser exposes retry for a failed chapter discovery`() {
        val dialog = source("app/src/main/java/eu/kanade/presentation/reader/AlternateSourceReaderDialog.kt")
        val chapter = dialog.substringAfter("is AlternateSourceReaderPresentation.ChoosingChapter")
            .substringBefore("is AlternateSourceReaderPresentation.ConfirmPair")

        assertTrue(chapter.contains("retryAction = onRetry"))
        assertTrue(dialog.contains("if (content is AlternateSourceReaderLoadState.Failed)"))
        assertTrue(dialog.contains("MR.strings.action_retry"))
    }

    @Test
    fun `chapter chooser requires explicit confirmation after selection`() {
        val dialog = source("app/src/main/java/eu/kanade/presentation/reader/AlternateSourceReaderDialog.kt")
        val activity = source("app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt")
        val viewModel = source("app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt")

        assertTrue(dialog.contains("selectedToken = presentation.selectedToken"))
        assertTrue(dialog.contains("onConfirm = onConfirmChapter"))
        assertTrue(activity.contains("onConfirmChapter = viewModel::confirmAlternateSourceChapterSelection"))
        assertTrue(viewModel.contains("fun confirmAlternateSourceChapterSelection()"))
    }

    @Test
    fun `invalid global search returns become recoverable instead of silent no-op`() {
        val viewModel = source("app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt")
        val searchReturn = viewModel.substringAfter("fun acceptAlternateSourceSearchResult")
            .substringBefore("fun toggleAlternateSourceCandidate")

        assertTrue(searchReturn.contains("val manga = getManga.await(mangaId)"))
        assertTrue(searchReturn.contains("AlternateSourceReaderPresentation.RecoverableFailure"))
        assertTrue(searchReturn.contains("canRetry = true"))
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
