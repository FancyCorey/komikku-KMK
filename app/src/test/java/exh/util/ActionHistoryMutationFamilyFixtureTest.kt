package exh.util

import exh.recs.bridge.AlternateSourceBridgeMutation
import exh.recs.matching.CrossSourceIdentityMutation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ActionHistoryMutationFamilyFixtureTest {

    private enum class RestoreMode {
        UNDO,
        COMPENSATING_FOLLOW_UP,
        EVENT_ONLY,
    }

    private enum class PrivacyClass {
        TYPED_LOCAL_INVERSE,
        GENERIC_EVENT_PRIVATE_RECEIPT,
        GENERIC_EVENT_ONLY,
    }

    private data class MutationFamilyFixture(
        val id: String,
        val actionName: String,
        val recorderOwner: String,
        val rowFamily: String,
        val restoreMode: RestoreMode,
        val verifiedSuccessBoundary: String,
        val privacyClass: PrivacyClass,
        val publicDisplayFields: Set<String>,
        val routeTestOwner: String,
    )

    private data class JournalOwnershipFixture(
        val familyId: String,
        val objectName: String,
        val sourcePath: String,
    )

    private val genericEventFields = setOf("eventType", "timestamp")

    private val fixtures = buildList {
        EvaluationJournalActionType.entries.forEach { action ->
            add(
                reversible(
                    family = "taste",
                    action = action.name,
                    recorder = "app/src/main/java/exh/util/EvaluationModeJournalRecorder.kt",
                    boundary = "build-before-write; commit-after-success",
                    test = "app/src/test/java/exh/util/EvaluationModeUndoServiceRestoreTest.kt",
                ),
            )
        }
        GroupJournalActionType.entries.forEach { action ->
            add(
                reversible(
                    family = "group",
                    action = action.name,
                    recorder = "app/src/main/java/exh/util/GroupUndoRecorder.kt",
                    boundary = "snapshot-before-write; record-after-success",
                    test = "app/src/test/java/exh/util/GroupUndoServiceRestoreTest.kt",
                ),
            )
        }
        CrossSourceIdentityMutation.entries.forEach { action ->
            add(
                reversible(
                    family = "cross_source_identity",
                    action = action.name,
                    recorder = "app/src/main/java/exh/util/CrossSourceIdentityUndoJournal.kt",
                    boundary = "snapshot-before-write; compare-and-swap; record-after-success",
                    test = "app/src/test/java/exh/recs/matching/CrossSourceIdentityDecisionControllerTest.kt",
                ),
            )
        }
        AlternateSourceBridgeMutation.entries.forEach { action ->
            add(
                reversible(
                    family = "alternate_source_bridge",
                    action = action.name,
                    recorder = "app/src/main/java/exh/util/AlternateSourceBridgeUndoJournal.kt",
                    boundary = "snapshot-before-write; compare-and-swap; record-after-success",
                    test = "app/src/test/java/exh/recs/bridge/AlternateSourceBridgeControllerTest.kt",
                ),
            )
        }
        LibraryJournalActionType.entries.forEach { action ->
            add(
                reversible(
                    family = "library",
                    action = action.name,
                    recorder = "app/src/main/java/exh/util/LibraryUndoRecorder.kt",
                    boundary = "snapshot-before-write; record-after-success",
                    test = "app/src/test/java/exh/util/LibraryUndoServiceRestoreTest.kt",
                ),
            )
        }
        PreferenceJournalActionType.entries.forEach { action ->
            add(
                reversible(
                    family = "preference",
                    action = action.name,
                    recorder = "app/src/main/java/exh/util/PreferenceUndoRecorder.kt",
                    boundary = "build-before-write; record-after-success",
                    test = "app/src/test/java/exh/util/PreferenceUndoRecorderTest.kt",
                ),
            )
        }
        ChapterJournalActionType.entries.forEach { action ->
            add(
                reversible(
                    family = "chapter",
                    action = action.name,
                    recorder = "app/src/main/java/exh/util/ChapterUndoRecorder.kt",
                    boundary = "snapshot-before-write; record-after-success",
                    test = "app/src/test/java/exh/util/ChapterUndoServiceRestoreTest.kt",
                ),
            )
        }
        add(
            reversible(
                family = "cover",
                action = "CUSTOM_COVER",
                recorder = "app/src/main/java/exh/util/CustomCoverUndoRecorder.kt",
                boundary = "digest-before-write; record-after-success",
                test = "app/src/test/java/exh/util/CustomCoverUndoJournalTest.kt",
            ),
        )
        NonUndoableEventType.entries.forEach { event ->
            val hasReceiptBackedFollowUp = event in setOf(
                NonUndoableEventType.MIGRATION_COMPLETED,
                NonUndoableEventType.EXTENSION_INSTALLED,
                NonUndoableEventType.EXTENSION_UPDATED,
                NonUndoableEventType.EXTENSION_UNINSTALLED,
                NonUndoableEventType.DOWNLOAD_DELETED,
                NonUndoableEventType.TRACKER_WRITE_COMPLETED,
                NonUndoableEventType.TRACKER_BOUND,
            )
            add(
                MutationFamilyFixture(
                    id = "nonundoable.${event.name}",
                    actionName = event.name,
                    recorderOwner = "app/src/main/java/exh/util/NonUndoableEventJournal.kt",
                    rowFamily = "nonundoable",
                    restoreMode = if (hasReceiptBackedFollowUp) {
                        RestoreMode.COMPENSATING_FOLLOW_UP
                    } else {
                        RestoreMode.EVENT_ONLY
                    },
                    verifiedSuccessBoundary = "record only after the owning operation verifies completion",
                    privacyClass = if (hasReceiptBackedFollowUp) {
                        PrivacyClass.GENERIC_EVENT_PRIVATE_RECEIPT
                    } else {
                        PrivacyClass.GENERIC_EVENT_ONLY
                    },
                    publicDisplayFields = genericEventFields,
                    routeTestOwner = "app/src/test/java/exh/util/ActionHistoryRegistryTest.kt",
                ),
            )
        }
    }

    private val journalOwners = listOf(
        JournalOwnershipFixture("taste", "EvaluationModeUndoJournal", "app/src/main/java/exh/util/EvaluationModeUndoJournal.kt"),
        JournalOwnershipFixture("group", "GroupUndoJournal", "app/src/main/java/exh/util/GroupUndoJournal.kt"),
        JournalOwnershipFixture(
            "cross_source_identity",
            "CrossSourceIdentityUndoJournal",
            "app/src/main/java/exh/util/CrossSourceIdentityUndoJournal.kt",
        ),
        JournalOwnershipFixture(
            "alternate_source_bridge",
            "AlternateSourceBridgeUndoJournal",
            "app/src/main/java/exh/util/AlternateSourceBridgeUndoJournal.kt",
        ),
        JournalOwnershipFixture("library", "LibraryUndoJournal", "app/src/main/java/exh/util/LibraryUndoJournal.kt"),
        JournalOwnershipFixture("preference", "PreferenceUndoJournal", "app/src/main/java/exh/util/PreferenceUndoJournal.kt"),
        JournalOwnershipFixture("chapter", "ChapterUndoJournal", "app/src/main/java/exh/util/ChapterUndoJournal.kt"),
        JournalOwnershipFixture("cover", "CustomCoverUndoJournal", "app/src/main/java/exh/util/CustomCoverUndoJournal.kt"),
        JournalOwnershipFixture("nonundoable", "NonUndoableEventJournal", "app/src/main/java/exh/util/NonUndoableEventJournal.kt"),
    )

    @Test
    fun `closed mutation register has unique ids and exactly the production registry families`() {
        assertEquals(fixtures.size, fixtures.map { it.id }.distinct().size, "mutation ids must be unique")
        assertEquals(
            ActionHistoryRegistry.sources.map { it.familyId }.toSet(),
            fixtures.map { it.rowFamily }.toSet(),
            "every production row family must have mutation ownership and no fixture-only family may exist",
        )
        assertEquals(
            ActionHistoryRegistry.sources.map { it.familyId }.toSet(),
            journalOwners.map { it.familyId }.toSet(),
            "every registered family must have one process-lifetime ownership guard",
        )
        assertEquals(journalOwners.size, journalOwners.map { it.familyId }.distinct().size)
    }

    @Test
    fun `closed mutation register covers every current production action enum value`() {
        assertActionCoverage("taste", EvaluationJournalActionType.entries.map { it.name })
        assertActionCoverage("group", GroupJournalActionType.entries.map { it.name })
        assertActionCoverage("cross_source_identity", CrossSourceIdentityMutation.entries.map { it.name })
        assertActionCoverage("alternate_source_bridge", AlternateSourceBridgeMutation.entries.map { it.name })
        assertActionCoverage("library", LibraryJournalActionType.entries.map { it.name })
        assertActionCoverage("preference", PreferenceJournalActionType.entries.map { it.name })
        assertActionCoverage("chapter", ChapterJournalActionType.entries.map { it.name })
        assertActionCoverage("nonundoable", NonUndoableEventType.entries.map { it.name })
        assertEquals(setOf("CUSTOM_COVER"), fixtures.filter { it.rowFamily == "cover" }.map { it.actionName }.toSet())
    }

    @Test
    fun `compensating and event-only rows can never be classified as Undo`() {
        fixtures.filter { it.privacyClass != PrivacyClass.TYPED_LOCAL_INVERSE }.forEach { fixture ->
            assertFalse(
                fixture.restoreMode == RestoreMode.UNDO,
                "${fixture.id} is an external/event operation and must never be labeled Undo",
            )
        }
        fixtures.filter { it.restoreMode == RestoreMode.UNDO }.forEach { fixture ->
            assertEquals(PrivacyClass.TYPED_LOCAL_INVERSE, fixture.privacyClass)
        }
    }

    @Test
    fun `event rows expose generic classification fields only`() {
        fixtures.filter { it.rowFamily == "nonundoable" }.forEach { fixture ->
            assertTrue(
                fixture.publicDisplayFields.all(genericEventFields::contains),
                "${fixture.id} exposes a user-data display field: ${fixture.publicDisplayFields - genericEventFields}",
            )
        }
    }

    @Test
    fun `every fixture names an existing recorder and route test owner`() {
        fixtures.forEach { fixture ->
            assertTrue(repoFile(fixture.recorderOwner).isFile, "${fixture.id} recorder owner is missing")
            assertTrue(fixture.verifiedSuccessBoundary.isNotBlank(), "${fixture.id} has no success boundary")
            assertTrue(repoFile(fixture.routeTestOwner).isFile, "${fixture.id} route test owner is missing")
        }
    }

    @Test
    fun `registered journals and diagnostic trace remain process-lifetime only`() {
        val forbiddenProductionTokens = listOf(
            "PreferenceStore",
            "SharedPreferences",
            "DataStore",
            "SavedStateHandle",
            "SavedStateRegistry",
            "kotlinx.serialization",
            "java.io.File",
            "DatabaseHandler",
            "DatabaseQueries",
            "BackupManager",
            "SyncManager",
        )

        journalOwners.forEach { owner ->
            val source = sourceWithoutComments(owner.sourcePath)
            assertTrue(source.contains("object ${owner.objectName}"), "${owner.objectName} ownership is missing")
            forbiddenProductionTokens.forEach { token ->
                assertFalse(source.contains(token), "${owner.objectName} must not reference persistent owner `$token`")
            }
        }

        val diagnostics = sourceWithoutComments("app/src/main/java/exh/util/ActionHistoryDiagnosticTrace.kt")
        forbiddenProductionTokens.forEach { token ->
            assertFalse(diagnostics.contains(token), "ActionHistoryDiagnosticTrace must not reference `$token`")
        }
        assertTrue(diagnostics.contains("ArrayDeque<DiagnosticTraceEvent>()"))
    }

    private fun reversible(
        family: String,
        action: String,
        recorder: String,
        boundary: String,
        test: String,
    ) = MutationFamilyFixture(
        id = "$family.$action",
        actionName = action,
        recorderOwner = recorder,
        rowFamily = family,
        restoreMode = RestoreMode.UNDO,
        verifiedSuccessBoundary = boundary,
        privacyClass = PrivacyClass.TYPED_LOCAL_INVERSE,
        publicDisplayFields = emptySet(),
        routeTestOwner = test,
    )

    private fun assertActionCoverage(family: String, expected: List<String>) {
        assertEquals(expected.toSet(), fixtures.filter { it.rowFamily == family }.map { it.actionName }.toSet())
    }

    private fun sourceWithoutComments(path: String): String {
        val source = repoFile(path).readText()
        return source
            .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
            .lineSequence()
            .map { it.substringBefore("//") }
            .joinToString("\n")
    }

    private fun repoFile(path: String): File {
        val candidates = listOf(
            File(path),
            File(path.removePrefix("app/")),
            File("..", path),
        )
        return candidates.firstOrNull(File::exists) ?: candidates.first()
    }
}
