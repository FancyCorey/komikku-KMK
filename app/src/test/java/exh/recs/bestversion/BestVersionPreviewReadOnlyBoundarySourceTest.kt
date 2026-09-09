package exh.recs.bestversion

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

// KMK R2-AUG-05-ACTUAL-BEST-VERSION-FAILURE-PATH -->
/**
 * Source-guard test proving the Best Version candidate preview's read/write boundary is structural,
 * not a runtime `if (isPreview) skip-write` flag. Follows the same source-text-substring convention as
 * [exh.recs.matching.SameMangaSearchOwnershipSourceTest] -- reads the real `.kt` files as text and
 * asserts specific write-capable identifiers are absent, rather than instantiating the whole screen
 * (which also legitimately depends on some of these types for the UNRELATED migrate/copy flow -- see
 * below for how this test avoids a false positive there).
 *
 * The preview pipeline (`BestVersionCompareScreenModel.previewOneCandidate`/`startPreview`/
 * `retryCandidate`, and `BestVersionCompareScreen`'s `FullscreenCandidatePreviewDialog`/
 * `PreviewPageContent`) must never hold a constructor/property reference reachable from opening a
 * preview to: [eu.kanade.tachiyomi.data.download.DownloadManager] (downloads), history/chapter
 * read-state writers, any tracker sync call, the Alternate Source Reading Bridge's mutator
 * ([eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderCandidateGateway]), or Chapter Line
 * Continuity's writer ([eu.kanade.tachiyomi.util.chapter.ChapterLineContinuityPolicy]). Opening a
 * preview must never create or alter a durable bridge relationship, and must never write reading
 * history, progress, downloads, or tracking -- see the canonical
 * R2-AUG-05-ACTUAL-BEST-VERSION-FAILURE-PATH contract's `side_effect_boundary` clause.
 *
 * Migration ([BestVersionCompareScreenModel.confirmMigration]) is a deliberately different, legitimate
 * write path in the SAME file -- it is expected to (and does) reference write-capable collaborators
 * like `MigrateMangaUseCase`/`UpsertMangaSourceQualitySignal`. This test does not assert those are
 * absent from the whole file; it only asserts the specific forbidden bridge/continuity/download/tracker
 * identifiers this feature must never reach at all, from ANY code path -- migration included -- because
 * none of the systems above are ones Best Version is allowed to touch, ever.
 */
class BestVersionPreviewReadOnlyBoundarySourceTest {

    private val forbiddenIdentifiers = listOf(
        "DownloadManager",
        "UpdateChapter",
        "TrackerManager",
        "AlternateSourceReaderCandidateGateway",
        "ChapterLineContinuityPolicy",
        "InsertHistory",
        "UpdateHistory",
        "SetReadStatus",
        "syncChapterReadStatus",
    )

    @Test
    fun `the screen model never references any write-capable reader-owner, tracker, or bridge type`() {
        val text = stripComments(source("app/src/main/java/exh/recs/bestversion/BestVersionCompareScreenModel.kt"))
        forbiddenIdentifiers.forEach { identifier ->
            assertFalse(
                text.contains(identifier),
                "BestVersionCompareScreenModel.kt must never reference $identifier in actual code -- " +
                    "Best Version (including its preview) must never write history, downloads, " +
                    "tracking, or mutate the Alternate Source Reading Bridge / Chapter Line " +
                    "Continuity state.",
            )
        }
    }

    @Test
    fun `the compare screen (including the candidate preview dialog) never references any write-capable type`() {
        val text = stripComments(source("app/src/main/java/exh/recs/bestversion/BestVersionCompareScreen.kt"))
        forbiddenIdentifiers.forEach { identifier ->
            assertFalse(
                text.contains(identifier),
                "BestVersionCompareScreen.kt must never reference $identifier in actual code.",
            )
        }
    }

    @Test
    fun `the reader-preview preference policy stays a pure value mapping with no reader lifecycle coupling`() {
        val text = stripComments(source("app/src/main/java/exh/recs/bestversion/BestVersionReaderPreviewPolicy.kt"))
        assertFalse(text.contains("ReaderViewModel"))
        assertFalse(text.contains("ReaderActivity"))
        forbiddenIdentifiers.forEach { identifier -> assertFalse(text.contains(identifier)) }
    }

    // KMK R2-AUG-05: strips `//` line comments and `/* ... */` (incl. KDoc `/** ... */`) block
    // comments before scanning for forbidden identifiers -- several doc comments in this feature
    // deliberately EXPLAIN, in prose, which write-capable types are NOT referenced (e.g.
    // "Deliberately does not touch DownloadManager/UpdateChapter"), which would otherwise make this
    // guard trip on its own documentation. Only identifiers reachable from actual compiled code count.
    private fun stripComments(text: String): String {
        val noBlockComments = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL).replace(text, " ")
        return noBlockComments.lineSequence().joinToString(separator = "\n") { line ->
            val idx = line.indexOf("//")
            if (idx >= 0) line.substring(0, idx) else line
        }
    }

    @Test
    fun `previewOneCandidate only ever calls read-only SourceRuntime operations`() {
        // KMK R2-AUG-05: the preview pipeline may only ever call PageList/ImageUrl (both read-only
        // source fetches) -- never a write-shaped SourceRuntimeOperation.
        val text = source("app/src/main/java/exh/recs/bestversion/BestVersionCompareScreenModel.kt")
        val startIndex = text.indexOf("private suspend fun previewOneCandidate(")
        assertTrue(startIndex >= 0, "previewOneCandidate() not found")
        val endIndex = text.indexOf("\n    companion object", startIndex)
        assertTrue(endIndex > startIndex, "could not bound previewOneCandidate()'s body")
        val body = text.substring(startIndex, endIndex)
        val operations = Regex("SourceRuntimeOperation\\.(\\w+)").findAll(body).map { it.groupValues[1] }.toSet()
        assertOperationsWithin(setOf("PageList", "ImageUrl"), operations)
    }

    // KMK independent_codex_recheck_2026-08-26: the reader-quality fullscreen preview's own
    // page-list fetch (openFullscreenCandidate) and per-page lazy image resolution
    // (resolveFullscreenPageImage) are new functions this recheck introduced -- neither is covered
    // by previewOneCandidate's own bounded test above, so each gets the identical read-only-
    // operations proof.
    @Test
    fun `openFullscreenCandidate only ever calls read-only SourceRuntime operations`() {
        val text = source("app/src/main/java/exh/recs/bestversion/BestVersionCompareScreenModel.kt")
        val startIndex = text.indexOf("fun openFullscreenCandidate(")
        assertTrue(startIndex >= 0, "openFullscreenCandidate() not found")
        val endIndex = text.indexOf("\n    fun closeFullscreenCandidate", startIndex)
        assertTrue(endIndex > startIndex, "could not bound openFullscreenCandidate()'s body")
        val body = text.substring(startIndex, endIndex)
        val operations = Regex("SourceRuntimeOperation\\.(\\w+)").findAll(body).map { it.groupValues[1] }.toSet()
        assertOperationsWithin(setOf("PageList"), operations, "openFullscreenCandidate()")
    }

    @Test
    fun `resolveFullscreenPageImage only ever calls read-only SourceRuntime operations`() {
        val text = source("app/src/main/java/exh/recs/bestversion/BestVersionCompareScreenModel.kt")
        val startIndex = text.indexOf("private fun resolveFullscreenPageImage(")
        assertTrue(startIndex >= 0, "resolveFullscreenPageImage() not found")
        val endIndex = text.indexOf("\n    // KMK v0.8.18: iterates the full comparison set", startIndex)
        assertTrue(endIndex > startIndex, "could not bound resolveFullscreenPageImage()'s body")
        val body = text.substring(startIndex, endIndex)
        val operations = Regex("SourceRuntimeOperation\\.(\\w+)").findAll(body).map { it.groupValues[1] }.toSet()
        // KMK C2 (E4.1): a forced non-EH retry now also refetches the page list (both read-only
        // fetches, never a write-shaped operation) to obtain a genuinely fresh URL instead of
        // reusing a possibly-expired one -- see resolveFullscreenPageImage's own doc comment.
        assertOperationsWithin(setOf("PageList", "ImageUrl"), operations, "resolveFullscreenPageImage()")
    }

    private fun assertOperationsWithin(expected: Set<String>, actual: Set<String>, label: String = "previewOneCandidate()") {
        assertTrue(actual.isNotEmpty(), "expected at least one SourceRuntimeOperation call in $label")
        assertTrue(expected.containsAll(actual), "$label called an unexpected SourceRuntimeOperation: $actual (allowed: $expected)")
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
