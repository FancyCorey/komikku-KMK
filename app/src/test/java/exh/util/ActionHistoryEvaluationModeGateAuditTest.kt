package exh.util

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Source-level guard for the Action History boundary. Evaluation Mode may redact presentation and
 * suppress developer diagnostics, but it must not silently remove ordinary-user history writers.
 */
class ActionHistoryEvaluationModeGateAuditTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun `local recovery writers do not gate capture on Evaluation Mode`() {
        val paths = listOf(
            "src/main/java/exh/util/PreferenceUndoRecorder.kt",
            "src/main/java/exh/util/ChapterUndoRecorder.kt",
            "src/main/java/exh/util/LibraryUndoRecorder.kt",
            "src/main/java/exh/util/GroupUndoRecorder.kt",
            "src/main/java/exh/util/CustomCoverUndoRecorder.kt",
            "src/main/java/eu/kanade/tachiyomi/ui/reader/schedule/ReaderSchedulePersistence.kt",
        )

        paths.forEach { path ->
            assertFalse(source(path).contains("evaluationMode().get()"), path)
        }
    }

    @Test
    fun `successful visibility receipts are not gated while failed paths remain success-bound`() {
        val source = source("src/main/java/exh/util/EvaluationModeInstallEventRecorder.kt")
        assertFalse(source.contains("if (isEvaluationModeEnabled())"))
        assertFalse(source.contains("removed && isEvaluationModeEnabled()"))
        assertTrue(source.contains("if (step == InstallStep.Installed && !recorded)"))
        assertTrue(source.contains("if (removed)"))
    }

    @Test
    fun `generic backup and Source Evaluation events are independent of Evaluation Mode`() {
        assertFalse(source("src/main/java/eu/kanade/tachiyomi/data/backup/restore/BackupRestoreOutcome.kt").contains("evaluationModeEnabled"))
        assertFalse(source("src/main/java/exh/recs/evaluation/SourceEvaluationHistoryPolicy.kt").contains("evaluationMode"))
    }

    @Test
    fun `successful Best Version migration history is independent of Evaluation Mode`() {
        val bestVersion = source("src/main/java/exh/recs/bestversion/BestVersionCompareScreenModel.kt")
        val successBlock = bestVersion
            .substringAfter("is mihon.domain.migration.usecases.MigrationOutcome.Success")
            .substringBefore("is mihon.domain.migration.usecases.MigrationOutcome.PartialFailure")
        assertFalse(successBlock.contains("evaluationMode().get()"))
        assertTrue(successBlock.contains("MigrationReceiptJournal.record"))
        assertTrue(successBlock.contains("NonUndoableEventType.MIGRATION_COMPLETED"))
    }

    @Test
    fun `recommendation preference writers do not gate ordinary history on Evaluation Mode`() {
        val recommendations = source("src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt")
        assertFalse(
            recommendations.substringAfter("fun toggleSource").substringBefore("// KMK -->")
                .contains("evaluationMode().get()"),
        )
        assertFalse(
            recommendations.substringAfter("private fun applySourcePreference")
                .substringBefore("// KMK <--")
                .contains("evaluationMode().get()"),
        )
        assertFalse(
            recommendations.substringAfter("private fun journalSourceQualityChange")
                .substringBefore("private fun applySourceQualityMark")
                .contains("evaluationMode().get()"),
        )

        val sourceEvaluation = source("src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt")
        assertFalse(
            sourceEvaluation.substringAfter("private fun journalSourceQualityChange")
                .substringBefore("private fun applySourceQualityMark")
                .contains("evaluationMode().get()"),
        )
    }

    @Test
    fun `taste and Not Interested writers use the shared ordinary history boundary`() {
        val recorder = source("src/main/java/exh/util/EvaluationModeJournalRecorder.kt")
        assertFalse(recorder.contains("evaluationMode().get()"))

        // KMK v0.8.21-fix3: R1 correction -- NotInterestedMangaScreen.kt is deleted (Not Interested
        // browsing now goes through the shared RatedMangaScreen, already covered by other source
        // audits); Not Interested writes live in the files listed below instead.
        listOf(
            "src/main/java/exh/recs/loved/LovedMangaScreenModel.kt",
            "src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreenModel.kt",
        ).forEach { path ->
            assertFalse(source(path).contains("evaluationMode().get()"), path)
        }

        val recommendations = source("src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt")
        assertFalse(
            recommendations.substringAfter("suspend fun markSelectedNotInterested")
                .substringBefore("// KMK v0.8.17-fix1: Clear Rating")
                .contains("evaluationMode().get()"),
        )
        assertFalse(
            recommendations.substringAfter("suspend fun clearSelectedRatings")
                .substringBefore("// KMK -->")
                .contains("evaluationMode().get()"),
        )
    }

    @Test
    fun `privacy and developer diagnostic gates remain present`() {
        assertTrue(source("src/main/java/exh/util/DeveloperOptionsGatePolicy.kt").contains("!evaluationModeEnabled"))
        assertTrue(source("src/main/java/exh/util/ActionHistoryScreenController.kt").contains("DeveloperOptionsGatePolicy.canExposeDiagnostics"))
        assertTrue(source("src/main/java/exh/util/EvaluationModeActionHistoryScreen.kt").contains("ActionHistoryRowPresentationPolicy.canExposeDiagnostics"))
    }

    @Test
    fun `normal settings route uses the user-facing history screen`() {
        assertTrue(
            source("src/main/java/eu/kanade/presentation/more/settings/screen/SettingsAdvancedScreen.kt")
                .contains("navigator.push(exh.util.ActionHistoryScreen())"),
        )
        assertTrue(
            source("src/main/java/exh/util/EvaluationModeActionHistoryScreen.kt")
                .contains("class ActionHistoryScreen"),
        )
    }
}
