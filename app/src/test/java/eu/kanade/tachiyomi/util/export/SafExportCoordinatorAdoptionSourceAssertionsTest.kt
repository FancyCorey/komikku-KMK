package eu.kanade.tachiyomi.util.export

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

// KMK -->
// KMK_CLAUDE_CORRECTIVE_COMPLETION_PLAN_2026-08-03 Phase 3: source-level assertions proving every
// `ActivityResultContracts.CreateDocument` writer in the app registers its picker `Uri` through
// `SafExportCoordinator` (or, for the two extension-export routes, an equivalent Uri-retained-on-
// every-outcome design already independently fixed and tested in ExtensionsTabBulkExportCleanupTest
// .kt / ExtensionDetailsScreenExportCleanupTest.kt). A full Compose UI test harness is not used here
// (per this project's standing preference for narrow JVM-only tests, and precedent in
// KmkEmptyStateIntegrationSourceAssertionsTest.kt); this is the source-level assertion alternative.
// These are deliberately coarse (substring checks on the raw file text) -- they exist to catch a
// future `CreateDocument` call site being added without wiring the shared lifecycle, not to fully
// verify runtime/Compose behavior.
class SafExportCoordinatorAdoptionSourceAssertionsTest {

    private fun readSource(path: String): String {
        val file = File(path)
        assertTrue(file.exists(), "expected source file at $path (relative to the app module directory) -- did it move?")
        return file.readText()
    }

    private fun assertUsesCreateDocumentAndCoordinator(path: String) {
        val source = readSource(path)
        assertTrue(
            source.contains("ActivityResultContracts.CreateDocument("),
            "$path was expected to still be a CreateDocument writer -- update this test if that changed",
        )
        assertTrue(
            source.contains("SafExportCoordinator()"),
            "$path must register its CreateDocument writes through SafExportCoordinator",
        )
        assertTrue(
            source.contains("SafArtifactCleanupDialog("),
            "$path must offer the shared Remove/Keep cleanup dialog",
        )
    }

    // KMK_CLAUDE_CORRECTIVE_COMPLETION_PLAN_2026-08-03 corrective re-pass (finding #5): screen and
    // coordinator now live in different files for these routes -- the coordinator was moved
    // from a Composable-`remember`ed instance into the route's existing ScreenModel
    // (screenModelScope-owned), so the picker/dialog wiring (CreateDocument + SafArtifactCleanupDialog)
    // stays in the screen file while `SafExportCoordinator()`'s construction moved to the model file.
    private fun assertUsesCreateDocumentAndModelOwnedCoordinator(
        screenPath: String,
        modelPath: String,
        expectedCoordinatorConstruction: String = "SafExportCoordinator()",
    ) {
        val screenSource = readSource(screenPath)
        assertTrue(
            screenSource.contains("ActivityResultContracts.CreateDocument("),
            "$screenPath was expected to still be a CreateDocument writer -- update this test if that changed",
        )
        assertTrue(
            screenSource.contains("SafArtifactCleanupDialog("),
            "$screenPath must offer the shared Remove/Keep cleanup dialog",
        )
        val modelSource = readSource(modelPath)
        assertTrue(
            modelSource.contains(expectedCoordinatorConstruction),
            "$modelPath must own (screenModelScope-scoped) the SafExportCoordinator for $screenPath",
        )
    }

    @Test
    fun `TopPicksScreen uses SafExportCoordinator`() {
        assertUsesCreateDocumentAndCoordinator("src/main/java/exh/recs/TopPicksScreen.kt")
    }

    @Test
    fun `RatedMangaScreen uses a SafExportCoordinator owned by LovedMangaScreenModel`() {
        assertUsesCreateDocumentAndModelOwnedCoordinator(
            screenPath = "src/main/java/exh/recs/loved/RatedMangaScreen.kt",
            modelPath = "src/main/java/exh/recs/loved/LovedMangaScreenModel.kt",
        )
    }

    @Test
    fun `BrowsePersonalRecommendationsTab uses a SafExportCoordinator owned by BrowsePersonalRecommendationsScreenModel`() {
        assertUsesCreateDocumentAndModelOwnedCoordinator(
            screenPath = "src/main/java/exh/recs/BrowsePersonalRecommendationsTab.kt",
            modelPath = "src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt",
        )
    }

    @Test
    fun `SettingsDataScreen CSV export uses a SafExportCoordinator owned by SettingsDataScreenModel, not a remember-scoped instance`() {
        // KMK_CLAUDE_FINAL_SAF_ACTION_HISTORY_RECONCILIATION_PLAN_2026-08-04 Phase 2: this route no
        // longer accepts the "stateless settings object" exception -- SettingsDataScreen is itself a
        // Voyager Screen (SearchableSettings : Screen), so rememberScreenModel correctly scopes
        // SettingsDataScreenModel's lifetime to it, the same guarantee every other CreateDocument
        // writer in the app has.
        val path = "src/main/java/eu/kanade/presentation/more/settings/screen/SettingsDataScreen.kt"
        assertUsesCreateDocumentAndCoordinator(path)
        val source = readSource(path)
        assertTrue(
            source.contains("private class SettingsDataScreenModel : ScreenModel"),
            "$path must define a real ScreenModel owning the CSV export coordinator",
        )
        assertTrue(
            source.contains("val screenModel = rememberScreenModel { SettingsDataScreenModel() }"),
            "$path must obtain the coordinator via rememberScreenModel(SettingsDataScreenModel), not a bare remember",
        )
        assertTrue(
            !source.contains("remember { SafExportCoordinator() }"),
            "$path must not own its own remember-scoped SafExportCoordinator anymore",
        )
    }

    @Test
    fun `extension bulk export uses a SafExportCoordinator owned by ExtensionsScreenModel`() {
        // KMK_CLAUDE_CORRECTIVE_COMPLETION_PLAN_2026-08-03 second corrective re-pass (findings #1
        // and #2): retrofitted onto the shared coordinator, owned by ExtensionsScreenModel
        // (screenModelScope-scoped), replacing the old standalone BulkExportArtifactKind design whose
        // Composable-remember-scoped cleanup dialog cleared its retained Uri even when deletion failed.
        assertUsesCreateDocumentAndModelOwnedCoordinator(
            screenPath = "src/main/java/eu/kanade/tachiyomi/ui/browse/extension/ExtensionsTab.kt",
            modelPath = "src/main/java/eu/kanade/tachiyomi/ui/browse/extension/ExtensionsScreenModel.kt",
            expectedCoordinatorConstruction = "SafExportCoordinator(allowSuccessfulRemoval = BuildConfig.DEBUG)",
        )
    }

    @Test
    fun `single extension export uses a SafExportCoordinator owned by ExtensionDetailsScreenModel`() {
        // KMK_CLAUDE_CORRECTIVE_COMPLETION_PLAN_2026-08-03 corrective re-pass (finding #2): retrofitted
        // onto the shared coordinator -- registers the picker Uri before checking whether the
        // pre-picker extension snapshot is still valid, so a stale/null extension no longer discards a
        // non-null Uri, and performWrite captures cancellation instead of the old "set uri/kind only
        // after the write attempt completes" pattern that silently skipped cleanup tracking on cancel.
        // KMK_CLAUDE_CORRECTIVE_COMPLETION_PLAN_2026-08-03 second corrective re-pass (finding #2): the
        // coordinator itself was further moved off this Composable's `remember` and onto
        // ExtensionDetailsScreenModel (screenModelScope-scoped).
        assertUsesCreateDocumentAndModelOwnedCoordinator(
            screenPath = "src/main/java/eu/kanade/tachiyomi/ui/browse/extension/details/ExtensionDetailsScreen.kt",
            modelPath = "src/main/java/eu/kanade/tachiyomi/ui/browse/extension/details/ExtensionDetailsScreenModel.kt",
            expectedCoordinatorConstruction = "SafExportCoordinator(allowSuccessfulRemoval = BuildConfig.DEBUG)",
        )
    }

    @Test
    fun `CreateBackupScreen registers through BackupCleanupRecoveryStore (application-scoped), and never labels the write as Undo`() {
        // KMK_CLAUDE_FINAL_SAF_ACTION_HISTORY_RECONCILIATION_PLAN_2026-08-04 Phase 3: a
        // screenModelScope-owned SafExportCoordinator was still not safe for backup creation -- the
        // WorkManager job it tracks is designed to outlive CreateBackupScreen, and navigating away
        // disposed the model (and cancelled its screenModelScope coroutine polling the job) before a
        // failed/cancelled backup could be offered cleanup. Ownership moved to
        // BackupCleanupRecoveryStore (a plain application-scoped singleton, not tied to any
        // Activity/Screen/ScreenModel), and the write itself now runs in
        // ProcessLifecycleOwner.lifecycleScope (cancelled only when the app process itself dies, not
        // when a screen is popped) instead of screenModelScope.
        val screenPath = "src/main/java/eu/kanade/presentation/more/settings/screen/data/CreateBackupScreen.kt"
        val screenSource = readSource(screenPath)
        assertTrue(
            screenSource.contains("ActivityResultContracts.CreateDocument("),
            "$screenPath was expected to still be a CreateDocument writer -- update this test if that changed",
        )
        assertTrue(
            screenSource.contains("BackupCleanupRecoveryStore.registerUri(operationId, uri)"),
            "$screenPath must register the picker Uri through BackupCleanupRecoveryStore, not a screen/model-scoped coordinator",
        )
        assertTrue(
            screenSource.contains("ProcessLifecycleOwner.get().lifecycleScope.launch"),
            "$screenPath must run the backup write in application-process scope, not screenModelScope, " +
                "so navigating away does not cancel the poll for the WorkManager job's terminal state",
        )
        assertTrue(
            !screenSource.contains("SafArtifactCleanupDialog("),
            "$screenPath must not render its own cleanup dialog anymore -- it is rendered once, at the " +
                "application composition root, so it stays reachable after this screen is popped",
        )
        assertTrue(!screenSource.contains("Undo", ignoreCase = false))

        val storePath = "src/main/java/eu/kanade/tachiyomi/util/export/BackupCleanupRecoveryStore.kt"
        val storeSource = readSource(storePath)
        assertTrue(
            storeSource.contains("object BackupCleanupRecoveryStore"),
            "$storePath must be a plain application-scoped singleton, not a screenModelScope-owned class",
        )

        val hostPath = "src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt"
        val hostSource = readSource(hostPath)
        assertTrue(
            hostSource.contains("BackupCleanupRecoveryDialog()"),
            "$hostPath must render the shared cleanup dialog at the application composition root, " +
                "so it remains reachable regardless of which screen is currently active",
        )
    }

    @Test
    fun `every CreateDocument route renders its cleanup dialog unconditionally on a non-null offer, with no extra gate that could suppress it`() {
        // KMK_CLAUDE_FINAL_SAF_ACTION_HISTORY_RECONCILIATION_PLAN_2026-08-04 Phase 4: this is the
        // actual mechanism that makes SafExportCoordinator.registerUri's overlap guard
        // defense-in-depth rather than the primary safeguard -- SafArtifactCleanupDialog is a modal
        // AlertDialog that appears the instant an offer becomes non-null (registerUri sets it
        // synchronously, before the async write even begins) and blocks touch input to the
        // underlying screen, including its own export-trigger control, until the user resolves it.
        // That guarantee only holds if every route renders the dialog unconditionally
        // (`offer?.let { ... }`, no extra `if` gate that could hide it while an offer is pending) --
        // proven here directly against each route's source.
        val offerLetPatterns = listOf(
            "src/main/java/exh/recs/TopPicksScreen.kt" to "cleanupOffer?.let { offer ->",
            "src/main/java/exh/recs/loved/RatedMangaScreen.kt" to "exportCleanupOffer?.let { offer ->",
            "src/main/java/exh/recs/BrowsePersonalRecommendationsTab.kt" to "exportCleanupOffer?.let { offer ->",
            "src/main/java/eu/kanade/tachiyomi/ui/browse/extension/details/ExtensionDetailsScreen.kt" to "exportCleanupOffer?.let { offer ->",
            "src/main/java/eu/kanade/tachiyomi/ui/browse/extension/ExtensionsTab.kt" to "bulkExportCleanupOffer?.let { offer ->",
            "src/main/java/eu/kanade/presentation/more/settings/screen/SettingsDataScreen.kt" to "exportCleanupOffer?.let { offer ->",
        )
        for ((path, pattern) in offerLetPatterns) {
            val source = readSource(path)
            assertTrue(
                source.contains(pattern),
                "$path must render SafArtifactCleanupDialog unconditionally via `$pattern` -- " +
                    "an extra gating condition here would break the modal-dialog overlap guard",
            )
        }
    }

    @Test
    fun `every CreateDocument route reserves an operation via beginOperation before launching the picker`() {
        // KMK_CLAUDE_SAF_EXPORT_LIFECYCLE_CORRECTIONS_2026-08-05 Finding 2: every route must reserve
        // an operationId before the picker launches, not merely register the returned Uri afterward --
        // otherwise a rejected/racing registration silently discards the document the picker created.
        val beginOperationPaths = listOf(
            "src/main/java/exh/recs/TopPicksScreen.kt",
            "src/main/java/exh/recs/loved/RatedMangaScreen.kt",
            "src/main/java/exh/recs/BrowsePersonalRecommendationsTab.kt",
            "src/main/java/eu/kanade/tachiyomi/ui/browse/extension/details/ExtensionDetailsScreen.kt",
            "src/main/java/eu/kanade/tachiyomi/ui/browse/extension/ExtensionsTab.kt",
            "src/main/java/eu/kanade/presentation/more/settings/screen/SettingsDataScreen.kt",
            "src/main/java/eu/kanade/presentation/more/settings/screen/data/CreateBackupScreen.kt",
        )
        for (path in beginOperationPaths) {
            val source = readSource(path)
            assertTrue(
                source.contains("beginOperation()"),
                "$path must reserve an operationId via beginOperation() before launching its CreateDocument picker",
            )
        }
    }

    @Test
    fun `SafArtifactCleanupDialog gates rendering on the IN_PROGRESS outcome`() {
        // KMK_CLAUDE_SAF_BACKUP_RECOVERY_ACTUAL_FINAL_PASS_2026-08-07: this now has a real behavioral
        // equivalent -- eu.kanade.presentation.components.safCleanupDialogActionFor, exercised
        // directly in SafArtifactCleanupDialogActionTest -- rather than only a source-text check.
        assertEquals(
            eu.kanade.presentation.components.SafCleanupDialogAction.NONE,
            eu.kanade.presentation.components.safCleanupDialogActionFor(SafArtifactOutcome.IN_PROGRESS),
            "IN_PROGRESS must refuse to render Remove/Keep",
        )
    }

    // --- KMK_CLAUDE_SAF_EXPORT_LIFECYCLE_CORRECTIONS_2026-08-06 corrective pass ---

    @Test
    fun `every route's handleUnregisterableUri fallback passes its owning coordinator, never discarding an unregisterable Uri`() {
        // Finding 1: a rejected registration must never silently drop the picker-created document --
        // every call site must pass its own coordinator (or, for backup, use the dedicated
        // handleUnregisterableBackupUri overload) so a failed deletion can still be adopted into that
        // route's normal Remove/Keep cleanup UI.
        val coordinatorCallSites = listOf(
            "src/main/java/exh/recs/TopPicksScreen.kt" to "screenModel.exportCoordinator,",
            "src/main/java/exh/recs/loved/RatedMangaScreen.kt" to "screenModel.exportCoordinator,",
            "src/main/java/exh/recs/BrowsePersonalRecommendationsTab.kt" to "screenModel.exportCoordinator,",
            "src/main/java/eu/kanade/tachiyomi/ui/browse/extension/details/ExtensionDetailsScreen.kt" to "screenModel.exportCoordinator,",
            "src/main/java/eu/kanade/tachiyomi/ui/browse/extension/ExtensionsTab.kt" to "extensionsScreenModel.bulkExportCoordinator,",
            "src/main/java/eu/kanade/presentation/more/settings/screen/SettingsDataScreen.kt" to "screenModel.exportCoordinator,",
        )
        for ((path, expectedCoordinatorArg) in coordinatorCallSites) {
            val source = readSource(path)
            assertTrue(
                source.contains("handleUnregisterableUri(") && source.contains(expectedCoordinatorArg),
                "$path must call handleUnregisterableUri with its own coordinator ($expectedCoordinatorArg)",
            )
        }
        val backupSource = readSource("src/main/java/eu/kanade/presentation/more/settings/screen/data/CreateBackupScreen.kt")
        assertTrue(
            backupSource.contains("handleUnregisterableBackupUri("),
            "CreateBackupScreen.kt must use the BackupCleanupRecoveryStore-backed fallback, not the coordinator-based one",
        )
    }

    @Test
    fun `SafExportCoordinator and BackupCleanupRecoveryStore both expose adoptUnregisterableUri`() {
        val coordinatorSource = readSource("src/main/java/eu/kanade/tachiyomi/util/export/SafExportCoordinator.kt")
        assertTrue(
            coordinatorSource.contains("fun adoptUnregisterableUri(uri: Uri): Boolean"),
            "SafExportCoordinator must expose adoptUnregisterableUri so a failed defensive-fallback deletion is never silently lost",
        )
        val storeSource = readSource("src/main/java/eu/kanade/tachiyomi/util/export/BackupCleanupRecoveryStore.kt")
        assertTrue(
            storeSource.contains("fun adoptUnregisterableUri(uri: Uri): Boolean"),
            "BackupCleanupRecoveryStore must expose the same fallback-adoption contract",
        )
    }

    @Test
    fun `startNow tags the manual WorkManager job with its destination uri so a later process can find it`() {
        // Finding 2: WorkInfo never exposes a job's input data (only id/state/tags/output survive a
        // query), so the destination uri must also be added as a queryable tag for
        // findManualJobIdForUri to be able to recover the real job id after a process death that
        // happened before attachWorkRequest persisted it durably. This is deliberately a source-level
        // assertion, not a runtime WorkManager query test: exercising a real WorkManager unique-work
        // KEEP-policy race (the "competing KEEP work" scenario) requires an actual WorkManager
        // scheduler, which is out of scope for these host-only unit tests (no ADB/emulator/device --
        // see this pass's governing instructions) and would otherwise require Robolectric, which this
        // project does not currently depend on.
        val path = "src/main/java/eu/kanade/tachiyomi/data/backup/create/BackupCreateJob.kt"
        val source = readSource(path)
        assertTrue(
            source.contains(".addTag(locationUriTag(uri))"),
            "$path's startNow must tag the enqueued job with a uri-derived tag",
        )
        assertTrue(
            source.contains("fun findManualJobIdForUri("),
            "$path must expose a way to recover a manual job's id by its destination uri after a process death",
        )
        assertTrue(
            source.contains("matches.firstOrNull { !it.state.isFinished } ?: matches.firstOrNull()"),
            "$path's findManualJobIdForUri must prefer a still-active match over a finished one when more than " +
                "one historical WorkInfo entry shares the unique work name -- this is exactly the safety property " +
                "that protects against a competing/dropped ExistingWorkPolicy.KEEP enqueue being mistaken for the " +
                "real active job",
        )
    }

    @Test
    fun `reconcileOnStartup recovers a missing work request id via findManualJobIdForUri instead of assuming resolution`() {
        // Finding 2: a record left IN_PROGRESS with no attached workRequestId must not be treated as
        // immediately resolved -- it must first try to recover the real job id from WorkManager itself.
        val path = "src/main/java/eu/kanade/tachiyomi/util/export/BackupCleanupRecoveryStore.kt"
        val source = readSource(path)
        assertTrue(
            source.contains("BackupCreateJob.findManualJobIdForUri(context, uri)"),
            "$path's reconcileOnStartup must attempt to recover a missing workRequestId via findManualJobIdForUri",
        )
    }

    @Test
    fun `loadRecord validates operationType, operationId, and uri shape, not only JSON syntax and schema version`() {
        // Finding 3.
        val path = "src/main/java/eu/kanade/tachiyomi/util/export/BackupCleanupRecoveryStore.kt"
        val source = readSource(path)
        assertTrue(source.contains("isValidOperationId(record.operationId)"), "$path must validate operationId format")
        assertTrue(source.contains("isValidSafDocumentUriString(record.uriString)"), "$path must validate the uri shape")
        assertTrue(
            source.contains("record.operationType != OPERATION_TYPE_BACKUP_CREATE"),
            "$path must reject any operationType other than the one known type",
        )
    }

    @Test
    fun `SafExportCoordinator documents that non-backup export process-death recovery is intentionally out of scope`() {
        // Finding 4: this is a documented, audited scope decision, not an oversight -- only backup
        // creation (a real WorkManager job that outlives the app process) gets durable persistence.
        // The six routes using SafExportCoordinator remain screen/process-lifetime-bounded by design.
        val path = "src/main/java/eu/kanade/tachiyomi/util/export/SafExportCoordinator.kt"
        val source = readSource(path)
        assertTrue(
            source.contains("Finding 4 -- deliberately bounded, not"),
            "$path must document the Finding 4 scope decision (non-backup export routes are intentionally not durable)",
        )
        assertTrue(
            !source.contains("PreferenceStore") && !source.contains("Injekt"),
            "$path must remain structurally in-memory-only (no PreferenceStore/Injekt persistence) -- " +
                "if this ever changes, the Finding 4 KDoc decision above must be revisited, not silently invalidated",
        )
        val storeSource = readSource("src/main/java/eu/kanade/tachiyomi/util/export/BackupCleanupRecoveryStore.kt")
        assertTrue(
            storeSource.contains("PreferenceStore"),
            "BackupCleanupRecoveryStore must remain the one durable exception, matching the documented scope decision",
        )
    }
}
// KMK <--
