package eu.kanade.presentation.more.settings.screen

import android.net.Uri
import eu.kanade.tachiyomi.util.export.SafArtifactOutcome
import eu.kanade.tachiyomi.util.export.SafExportCoordinator
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

// KMK -->
// KMK: direct coverage for
// the CSV export route's SafExportCoordinator usage, now owned by SettingsDataScreenModel
// (screenModelScope-scoped) instead of a Composable-remember-owned instance. Mirrors
// ExtensionDetailsScreenExportCleanupTest.kt / ExtensionsTabBulkExportCleanupTest.kt: the picker Uri
// must be retained even when the favorite-list snapshot captured before the picker opened turns out
// to be empty/stale by the time the write runs, and cancellation must downgrade the offer to
// CANCELLED rather than bypassing cleanup tracking. SettingsDataScreenModel is `private`, so these
// tests exercise the same SafExportCoordinator contract it drives, plus source-level assertions that
// the model exists and owns the coordinator (screenModelScope-scoped, not Composable-remember-owned).
class SettingsDataScreenCsvExportCleanupTest {

    @Test
    fun `an empty favorites snapshot still registers the Uri and reports partial-or-empty, never discards it`() {
        val coordinator = SafExportCoordinator()
        val id = coordinator.beginOperation()!!
        val uri = mockk<Uri>()
        coordinator.registerUri(id, uri)

        val favoritesSnapshot = emptyList<Any>()
        val outcome = runBlocking {
            coordinator.performWrite(id) {
                if (favoritesSnapshot.isEmpty()) SafArtifactOutcome.PARTIAL_OR_EMPTY else error("unreachable: favorites was non-empty")
            }
        }

        assertEquals(SafArtifactOutcome.PARTIAL_OR_EMPTY, outcome)
        assertEquals(uri, coordinator.cleanupOffer.value?.uri)
        assertEquals(SafArtifactOutcome.PARTIAL_OR_EMPTY, coordinator.cleanupOffer.value?.outcome)
    }

    @Test
    fun `a successful CSV write is reflected as SUCCESS on the retained offer`() {
        val coordinator = SafExportCoordinator()
        val id = coordinator.beginOperation()!!
        val uri = mockk<Uri>()
        coordinator.registerUri(id, uri)

        runBlocking {
            coordinator.performWrite(id) { SafArtifactOutcome.SUCCESS }
        }

        assertEquals(SafArtifactOutcome.SUCCESS, coordinator.cleanupOffer.value?.outcome)
    }

    @Test
    fun `a destination-open or write failure is reflected as FAILED on the retained offer`() {
        val coordinator = SafExportCoordinator()
        val id = coordinator.beginOperation()!!
        val uri = mockk<Uri>()
        coordinator.registerUri(id, uri)

        runBlocking {
            coordinator.performWrite(id) { SafArtifactOutcome.FAILED }
        }

        assertEquals(SafArtifactOutcome.FAILED, coordinator.cleanupOffer.value?.outcome)
    }

    @Test
    fun `cancellation after picker return downgrades the offer to CANCELLED instead of bypassing cleanup tracking`() {
        val coordinator = SafExportCoordinator()
        val id = coordinator.beginOperation()!!
        val uri = mockk<Uri>()
        coordinator.registerUri(id, uri)

        var thrown: kotlinx.coroutines.CancellationException? = null
        try {
            runBlocking {
                coordinator.performWrite(id) { throw kotlinx.coroutines.CancellationException("cancelled") }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            thrown = e
        }

        assertTrue(thrown != null, "cancellation must propagate, not be swallowed as an ordinary outcome")
        assertEquals(SafArtifactOutcome.CANCELLED, coordinator.cleanupOffer.value?.outcome)
        assertEquals(uri, coordinator.cleanupOffer.value?.uri)
    }

    @Test
    fun `SettingsDataScreenModel owns a screenModelScope-scoped SafExportCoordinator, not a Composable-remember one`() {
        val file = File("src/main/java/eu/kanade/presentation/more/settings/screen/SettingsDataScreen.kt")
        assertTrue(file.exists(), "expected source file at ${file.path} (relative to the app module directory) -- did it move?")
        val source = file.readText()

        assertTrue(
            source.contains("private class SettingsDataScreenModel : ScreenModel"),
            "SettingsDataScreenModel must exist as a real ScreenModel -- the CSV route must not remain a documented exception",
        )
        assertTrue(
            source.contains("val exportCoordinator = SafExportCoordinator()"),
            "SettingsDataScreenModel must own the coordinator (screenModelScope-scoped)",
        )
        assertTrue(
            source.contains("val screenModel = rememberScreenModel { SettingsDataScreenModel() }"),
            "getExportGroup() must obtain the coordinator via rememberScreenModel, not remember { SafExportCoordinator() }",
        )
        assertTrue(
            !source.contains("remember { SafExportCoordinator() }"),
            "the CSV route must no longer own its own remember-scoped SafExportCoordinator",
        )
        assertTrue(
            source.contains("exportSnapshot = favorites to options"),
            "the favorites/options snapshot must be captured at the confirm click, before the picker launches",
        )
    }
}
// KMK <--
