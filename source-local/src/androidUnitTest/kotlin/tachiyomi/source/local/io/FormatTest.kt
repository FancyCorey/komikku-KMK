package tachiyomi.source.local.io

import com.hippo.unifile.UniFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

// KMK -->
// KMK_CLAUDE_REMAINING_FIXTURE_BLOCKER_IMPLEMENTATION_PLAN_2026-08-03 Phase 4: direct coverage for
// Format.valueOf(), the pure classification LocalSource uses to decide how to read a chapter entry
// (directory of loose images, a supported archive, or an Epub) -- and, for a genuinely unsupported
// extension, to throw Format.UnknownFormatException rather than silently misreading a chapter.
// Content bytes are irrelevant to this classification (it only inspects isDirectory/extension), so
// these use plain placeholder text files -- this deliberately does not attempt real archive-format
// validation (that is Archive/ArchiveReader's own concern, exercised by opening the file, not by
// Format.valueOf's classification step tested here).
class FormatTest {

    @Test
    fun `a directory is classified as Format Directory`(@TempDir root: File) {
        val dir = File(root, "Chapter 1").apply { mkdir() }

        val format = Format.valueOf(UniFile.fromFile(dir)!!)

        assertEquals(Format.Directory::class, format::class)
    }

    @Test
    fun `every supported archive extension is classified as Format Archive`(@TempDir root: File) {
        listOf("zip", "cbz", "rar", "cbr", "7z", "cb7", "tar", "cbt", "ZIP", "CbZ").forEachIndexed { index, extension ->
            val file = File(root, "chapter-$index.$extension").apply { writeText("placeholder") }

            val format = Format.valueOf(UniFile.fromFile(file)!!)

            assertEquals(Format.Archive::class, format::class, "extension '$extension' must classify as Archive")
        }
    }

    @Test
    fun `an epub file is classified as Format Epub, not Archive, even though epub is zip-based`(@TempDir root: File) {
        val file = File(root, "chapter-1.epub").apply { writeText("placeholder") }

        val format = Format.valueOf(UniFile.fromFile(file)!!)

        assertEquals(Format.Epub::class, format::class)
    }

    @Test
    fun `an unsupported extension throws UnknownFormatException instead of being silently misread`(@TempDir root: File) {
        listOf("txt", "pdf", "jpg", "mp4", "").forEachIndexed { index, extension ->
            val name = if (extension.isEmpty()) "chapter-$index" else "chapter-$index.$extension"
            val file = File(root, name).apply { writeText("placeholder") }

            assertThrows(Format.UnknownFormatException::class.java) {
                Format.valueOf(UniFile.fromFile(file)!!)
            }
        }
    }

    @Test
    fun `a malformed archive (wrong extension for its actual content) is still classified by extension alone`(@TempDir root: File) {
        // Format.valueOf never inspects file content -- only isDirectory/extension. A file with a
        // .cbz extension but corrupt/non-archive bytes is still classified as Format.Archive here;
        // the actual malformed-content failure surfaces later, when something tries to open it as
        // an archive (ArchiveReader), not at this classification step. This test exists to make
        // that boundary explicit rather than assumed.
        val file = File(root, "corrupt.cbz").apply { writeBytes(byteArrayOf(0x00, 0x01, 0x02)) }

        val format = Format.valueOf(UniFile.fromFile(file)!!)

        assertEquals(Format.Archive::class, format::class)
    }
}
// KMK <--
