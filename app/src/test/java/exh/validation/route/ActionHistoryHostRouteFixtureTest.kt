package exh.validation.route

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import exh.util.ActionHistoryCommandController
import exh.util.ActionHistoryCommandResult
import exh.util.ActionHistoryFollowUpResult
import exh.util.ActionHistoryRegistry
import exh.util.ActionHistoryRowIntent
import exh.util.ActionHistoryRowIntentPolicy
import exh.util.ActionHistoryRowTarget
import exh.util.EvaluationJournalActionType
import exh.util.EvaluationJournalEntry
import exh.util.EvaluationModeUndoJournal
import exh.util.FakePreferenceStore
import exh.util.GroupUndoResult
import exh.util.NonUndoableEvent
import exh.util.NonUndoableEventJournal
import exh.util.NonUndoableEventType
import exh.util.PackageOperationJournal
import exh.util.PackageOperationKind
import exh.util.PackageOperationReceipt
import exh.util.PreferenceJournalActionType
import exh.util.PreferenceUndoEntry
import exh.util.PreferenceUndoJournal
import exh.util.actionHistoryFollowUpExtensionManagerProvider
import exh.util.actionHistoryFollowUpSourcePreferencesProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ActionHistoryHostRouteFixtureTest {
    @BeforeEach
    fun clearRouteState() {
        ActionHistoryRegistry.clearAll()
        PackageOperationJournal.clear()
    }

    @AfterEach
    fun restoreRouteState() {
        ActionHistoryRegistry.clearAll()
        PackageOperationJournal.clear()
        actionHistoryFollowUpExtensionManagerProvider = { Injekt.get() }
        actionHistoryFollowUpSourcePreferencesProvider = { Injekt.get() }
    }

    @Test
    fun `empty route remains empty`() {
        assertTrue(ActionHistoryCommandController().rows().isEmpty())
    }

    @Test
    fun `contextual manga row opens only a live journal-owned id`() {
        seedTaste(mangaId = 42L)
        val row = ActionHistoryCommandController().rows().single()

        HostRouteTestEnvironment().use { environment ->
            val open = environment.execute {
                ActionHistoryRowIntentPolicy.resolve(row, ActionHistoryRowTarget.SUMMARY) { it == 42L }
            }
            val deleted = environment.execute {
                ActionHistoryRowIntentPolicy.resolve(row, ActionHistoryRowTarget.SUMMARY) { false }
            }

            assertEquals(ActionHistoryRowIntent.OpenManga(42L), open)
            assertEquals(ActionHistoryRowIntent.ContextUnavailable, deleted)
        }
    }

    @Test
    fun `non-contextual preference Undo restores and removes the row`() {
        var current = 2
        seedPreference(
            id = "restore",
            readCurrent = { current },
            restore = { current = it },
        )
        val controller = ActionHistoryCommandController()
        val row = controller.rows().single()

        HostRouteTestEnvironment().use { environment ->
            val result = environment.execute { controller.executeUndo(row) }

            val completed = assertInstanceOf(ActionHistoryCommandResult.UndoCompleted::class.java, result)
            assertEquals(GroupUndoResult.RESTORED, (completed.outcome as exh.util.ActionHistoryUndoResult.Simple).result)
            assertEquals(1, current)
            assertTrue(completed.rows.isEmpty())
            assertNull(row.contextMangaId)
        }
    }

    @Test
    fun `conflicted preference Undo retains the row without writing`() {
        var current = 3
        var writes = 0
        seedPreference(
            id = "conflict",
            readCurrent = { current },
            restore = {
                writes += 1
                current = it
            },
        )
        val controller = ActionHistoryCommandController()
        val row = controller.rows().single()

        HostRouteTestEnvironment().use { environment ->
            val result = environment.execute { controller.executeUndo(row) }
            val completed = assertInstanceOf(ActionHistoryCommandResult.UndoCompleted::class.java, result)

            assertEquals(GroupUndoResult.CONFLICT, (completed.outcome as exh.util.ActionHistoryUndoResult.Simple).result)
            assertEquals(0, writes)
            assertEquals(3, current)
            assertEquals(listOf(row.id), completed.rows.map { it.id })
        }
    }

    @Test
    fun `view-only external event has no Undo or follow-up`() {
        NonUndoableEventJournal.record(
            NonUndoableEvent(
                id = "backup",
                timestamp = 10L,
                eventType = NonUndoableEventType.BACKUP_RESTORED,
            ),
        )

        val row = ActionHistoryCommandController().rows().single()

        assertNull(row.undo)
        assertNull(row.followUp)
        assertNull(row.contextMangaId)
    }

    @Test
    fun `eligible package follow-up executes as a forward action while changed package is refused`() {
        val extensionManager = mockk<ExtensionManager>(relaxed = true)
        val sourcePreferences = SourcePreferences(FakePreferenceStore()).apply { evaluationMode().set(true) }
        val installed = installedExtension()
        val installedFlow = MutableStateFlow(listOf(installed))
        every { extensionManager.installedExtensionsFlow } returns installedFlow
        every { extensionManager.availableExtensionsFlow } returns MutableStateFlow(emptyList())
        every { extensionManager.uninstallExtension(installed) } answers { installedFlow.value = emptyList() }
        actionHistoryFollowUpExtensionManagerProvider = { extensionManager }
        actionHistoryFollowUpSourcePreferencesProvider = { sourcePreferences }
        seedPackageInstall("eligible")

        val controller = ActionHistoryCommandController()
        val eligible = controller.rows().single()
        assertNull(eligible.undo)
        assertTrue(eligible.followUp != null)

        HostRouteTestEnvironment().use { environment ->
            val result = environment.execute { controller.executeFollowUp(eligible) }
            val completed = assertInstanceOf(ActionHistoryCommandResult.FollowUpCompleted::class.java, result)
            assertEquals(ActionHistoryFollowUpResult.Started, completed.outcome)
        }

        ActionHistoryRegistry.clearAll()
        PackageOperationJournal.clear()
        seedPackageInstall("refused")
        val refused = ActionHistoryCommandController().rows().single()
        assertNull(refused.followUp)
        assertNull(refused.undo)
    }

    @Test
    fun `Undo cancellation propagates and leaves the journal row`() {
        seedPreference(
            id = "cancel",
            readCurrent = { 2 },
            restore = { throw CancellationException("synthetic-cancel") },
        )
        val controller = ActionHistoryCommandController()
        val row = controller.rows().single()

        assertThrows(CancellationException::class.java) {
            HostRouteTestEnvironment().use { environment ->
                environment.execute { controller.executeUndo(row) }
            }
        }
        assertEquals(listOf(row.id), controller.rows().map { it.id })
    }

    @Test
    fun `clear-all requires confirmation and clears every seeded family`() {
        seedTaste(mangaId = 7L)
        seedPreference("clear", readCurrent = { 2 }, restore = {})
        NonUndoableEventJournal.record(
            NonUndoableEvent("clear-event", 30L, NonUndoableEventType.BACKUP_RESTORED),
        )
        val controller = ActionHistoryCommandController()

        val cancelled = controller.clear(confirmed = false)
        assertInstanceOf(ActionHistoryCommandResult.ClearConfirmationRequired::class.java, cancelled)
        assertEquals(3, cancelled.rows.size)

        val cleared = controller.clear(confirmed = true)
        assertInstanceOf(ActionHistoryCommandResult.Cleared::class.java, cleared)
        assertTrue(cleared.rows.isEmpty())
        assertTrue(EvaluationModeUndoJournal.isEmpty())
        assertTrue(PreferenceUndoJournal.isEmpty())
        assertTrue(NonUndoableEventJournal.isEmpty())
    }

    @Test
    fun `journal state survives route-controller leave and return`() {
        seedPreference("return", readCurrent = { 2 }, restore = {})
        val firstRouteRows = ActionHistoryCommandController().rows()
        val returnedRouteRows = ActionHistoryCommandController().rows()

        assertEquals(firstRouteRows.map { it.id }, returnedRouteRows.map { it.id })
        assertEquals(1, returnedRouteRows.size)
    }

    private fun seedTaste(mangaId: Long) {
        EvaluationModeUndoJournal.record(
            EvaluationJournalEntry(
                id = "taste-$mangaId",
                timestamp = 1L,
                actionType = EvaluationJournalActionType.RATE_LIKE,
                mangaId = mangaId,
                source = 10L,
                url = "/synthetic/$mangaId",
                previousRating = null,
                newRating = 1,
                isBulk = false,
                bulkOperationId = null,
                changedFields = setOf(EvaluationJournalEntry.FIELD_RATING),
            ),
        )
    }

    private fun seedPreference(
        id: String,
        readCurrent: suspend () -> Int,
        restore: suspend (Int) -> Unit,
    ) {
        PreferenceUndoJournal.record(
            PreferenceUndoEntry(
                id = id,
                timestamp = 2L,
                actionType = PreferenceJournalActionType.RESULT_BUDGET,
                identityKey = "synthetic-result-budget",
                previousValue = 1,
                expectedPostValue = 2,
                readCurrent = readCurrent,
                restore = restore,
            ),
        )
    }

    private fun seedPackageInstall(id: String) {
        NonUndoableEventJournal.record(
            NonUndoableEvent(id, 20L, NonUndoableEventType.EXTENSION_INSTALLED),
        )
        PackageOperationJournal.record(
            PackageOperationReceipt(
                id = id,
                timestamp = 20L,
                kind = PackageOperationKind.INSTALL,
                packageName = "invalid.synthetic.extension",
                signatureHash = "synthetic-signature",
                versionCode = 1L,
                artifactUri = "https://invalid.example/extension.apk",
            ),
        )
    }

    private fun installedExtension() = Extension.Installed(
        name = "Synthetic",
        pkgName = "invalid.synthetic.extension",
        versionName = "1.0",
        versionCode = 1L,
        libVersion = 1.5,
        lang = "en",
        isNsfw = false,
        signatureHash = "synthetic-signature",
        storeName = null,
        pkgFactory = null,
        sources = emptyList(),
        icon = null,
        isShared = false,
    )

    private fun <T> HostRouteTestEnvironment.execute(block: suspend () -> T): T {
        var result: Result<T>? = null
        scope.launch { result = runCatching { block() } }
        advanceUntilIdle()
        return checkNotNull(result).getOrThrow()
    }
}
