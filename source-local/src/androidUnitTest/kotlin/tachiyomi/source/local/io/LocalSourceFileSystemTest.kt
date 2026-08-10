package tachiyomi.source.local.io

import com.hippo.unifile.UniFile
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.storage.service.StorageManager
import java.io.File

// KMK -->
// This module previously had no
// unit test source set previously (see source-local/build.gradle.kts's new androidUnitTest
// block). [UniFile.fromFile] wraps a plain java.io.File without needing a real Android Context, so
// the local-source directory-enumeration contract can be exercised directly on a real temp
// directory tree -- no emulator required. [StorageManager] itself is mocked since it needs a real
// Context to resolve a SAF root; only its already-narrow [LocalSourceFileSystem] consumption
// surface (getLocalSourceDirectory()) is stubbed.
//
// This directly documents and verifies the exact on-disk shape LocalSource.getSearchManga()
// requires: each manga must be its own SUBFOLDER directly under the local-source root -- a chapter
// archive staged loose at the root (not inside a manga folder) is enumerated here but is never
// treated as a manga by the caller (LocalSource filters non-directories out before building the
// popular/search page). This is the most concrete lead found for the repeated device "loading-only,
// no manga row" symptom recorded in
// Reader fixture contract: a nested local-source archive remains discoverable and reversible.
// Fixture Retry note -- not confirmed without a device repro, but the on-disk shape requirement is
// now explicit and test-proven rather than assumed.
class LocalSourceFileSystemTest {

    private lateinit var storageManager: StorageManager

    @BeforeEach
    fun setUp() {
        storageManager = mockk()
    }

    @AfterEach
    fun tearDown() {
        // no-op: TempDir is cleaned up by JUnit
    }

    private fun fileSystem(root: File): LocalSourceFileSystem {
        every { storageManager.getLocalSourceDirectory() } returns UniFile.fromFile(root)
        return LocalSourceFileSystem(storageManager)
    }

    @Test
    fun `getBaseDirectory resolves to the storage manager's local-source root`(@TempDir root: File) {
        val fs = fileSystem(root)
        assertEquals(root.name, fs.getBaseDirectory()?.name)
    }

    @Test
    fun `getBaseDirectory is null when the storage manager has no accessible root`() {
        every { storageManager.getLocalSourceDirectory() } returns null
        val fs = LocalSourceFileSystem(storageManager)

        assertNull(fs.getBaseDirectory())
    }

    @Test
    fun `a manga folder is enumerated by getFilesInBaseDirectory`(@TempDir root: File) {
        File(root, "My Manga").mkdir()
        val fs = fileSystem(root)

        val names = fs.getFilesInBaseDirectory().map { it.name }
        assertTrue("My Manga" in names, "the manga folder must be enumerated at the root")
    }

    @Test
    fun `a chapter archive staged loose at the root is enumerated but is not a directory`(@TempDir root: File) {
        // This is the exact shape mismatch this test class exists to make explicit: staging a CBZ
        // directly in the local-source root (not inside a manga subfolder) is a structurally
        // different layout from what LocalSource.getSearchManga() expects (it additionally filters
        // for `it.isDirectory`, which this entry fails).
        File(root, "chapter-1.cbz").writeText("not a real archive, just a placeholder for shape testing")
        val fs = fileSystem(root)

        val entry = fs.getFilesInBaseDirectory().single { it.name == "chapter-1.cbz" }
        assertTrue(!entry.isDirectory, "a loose chapter file at the root must never be mistaken for a manga folder")
    }

    @Test
    fun `getMangaDirectory resolves an existing manga folder`(@TempDir root: File) {
        File(root, "My Manga").mkdir()
        val fs = fileSystem(root)

        val dir = fs.getMangaDirectory("My Manga")
        assertTrue(dir != null && dir.isDirectory)
    }

    @Test
    fun `getMangaDirectory returns null for a name that does not exist`(@TempDir root: File) {
        val fs = fileSystem(root)

        assertNull(fs.getMangaDirectory("Does Not Exist"))
    }

    @Test
    fun `getMangaDirectory returns null when the name resolves to a file, not a directory`(@TempDir root: File) {
        File(root, "not-a-folder.cbz").writeText("placeholder")
        val fs = fileSystem(root)

        assertNull(fs.getMangaDirectory("not-a-folder.cbz"), "a same-named file must never be treated as the manga directory")
    }

    @Test
    fun `getFilesInMangaDirectory lists chapter entries inside a valid manga folder`(@TempDir root: File) {
        val mangaDir = File(root, "My Manga").apply { mkdir() }
        File(mangaDir, "chapter-1.cbz").writeText("placeholder")
        File(mangaDir, "chapter-2.cbz").writeText("placeholder")
        val fs = fileSystem(root)

        val names = fs.getFilesInMangaDirectory("My Manga").map { it.name }.toSet()
        assertEquals(setOf("chapter-1.cbz", "chapter-2.cbz"), names)
    }

    @Test
    fun `getFilesInMangaDirectory is empty for a manga folder that does not exist`(@TempDir root: File) {
        val fs = fileSystem(root)

        assertTrue(fs.getFilesInMangaDirectory("Does Not Exist").isEmpty())
    }
}
// KMK <--
