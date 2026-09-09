package exh.recs.bridge.fixture

import exh.recs.bestversion.fixture.BestVersionPairedFixtureActivation
import exh.recs.bestversion.fixture.BestVersionPairedFixtureGate
import exh.recs.bestversion.fixture.BestVersionPairedFixtureMode
import exh.recs.bestversion.fixture.BestVersionPairedFixtureSourceIdentity
import kotlinx.serialization.Serializable
import java.util.UUID

enum class AlternateSourceReaderFixtureScenario(val prefValue: String) {
    OFF("off"),
    EXACT_GAP("exact_gap"),
    OFFSET_PROVISIONAL("offset_provisional"),
    MISSING("missing"),
    DUPLICATE_CONFLICT("duplicate_conflict"),
    STALE("stale"),
    PROCESS_RECREATED("process_recreated"),
    ;

    companion object {
        fun fromPrefValue(value: String): AlternateSourceReaderFixtureScenario =
            entries.firstOrNull { it.prefValue == value } ?: OFF
    }
}

data class AlternateSourceReaderFixtureActivation(
    val isDebugBuild: Boolean,
    val scenario: AlternateSourceReaderFixtureScenario,
    val evaluationModeEnabled: Boolean,
    val fixtureProfile: String,
    val pairedFixtureProfile: String,
    val expectedSignerSha256: String,
    val installedSources: Set<BestVersionPairedFixtureSourceIdentity>,
)

object AlternateSourceReaderFixtureGate {
    const val ISOLATED_PROFILE = "isolated-emulator-bridge"

    fun isAllowed(input: AlternateSourceReaderFixtureActivation): Boolean {
        if (input.scenario == AlternateSourceReaderFixtureScenario.OFF) return false
        if (input.fixtureProfile != ISOLATED_PROFILE) return false
        return BestVersionPairedFixtureGate.isAllowed(
            BestVersionPairedFixtureActivation(
                isDebugBuild = input.isDebugBuild,
                mode = BestVersionPairedFixtureMode.PAIRED_RECORDS,
                evaluationModeEnabled = input.evaluationModeEnabled,
                fixtureProfile = input.pairedFixtureProfile,
                expectedSignerSha256 = input.expectedSignerSha256,
                installedSources = input.installedSources,
            ),
        )
    }
}

@Serializable
enum class AlternateSourceReaderFixtureStep {
    PAIRED_GRAPH,
    BASELINES,
    PRIMARY_GAP,
    IDENTITY,
    BRIDGE,
    SESSION,
    ROUTE,
}

@Serializable
data class AlternateSourceReaderFixtureChapterSnapshot(
    val id: Long,
    val mangaId: Long,
    val url: String,
    val name: String,
    val chapterNumber: Float,
    val scanlator: String? = null,
    val read: Boolean,
    val bookmark: Boolean,
    val lastPageRead: Long,
    val dateFetch: Long,
    val dateUpload: Long,
    val sourceOrder: Long,
) {
    fun isStructurallyValid(): Boolean =
        id > 0L &&
            mangaId > 0L &&
            url == PRIMARY_GAP_CHAPTER_URL &&
            name.isNotBlank() &&
            name.length <= MAX_TEXT_LENGTH &&
            chapterNumber.isFinite() &&
            scanlator.orEmpty().length <= MAX_TEXT_LENGTH &&
            lastPageRead >= 0L &&
            dateFetch >= 0L &&
            dateUpload >= 0L &&
            sourceOrder >= 0L

    private companion object {
        const val PRIMARY_GAP_CHAPTER_URL = "/kmk-fixture/f2/origin/chapter-2"
        const val MAX_TEXT_LENGTH = 256
    }
}

@Serializable
data class AlternateSourceReaderFixtureManifest(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val revision: String = REVISION,
    val operationId: String,
    val pairedFixtureOperationId: String,
    val scenario: AlternateSourceReaderFixtureScenario,
    val createdAt: Long,
    val pendingStep: AlternateSourceReaderFixtureStep? = null,
    val completedSteps: Set<AlternateSourceReaderFixtureStep> = emptySet(),
    val primarySourceId: Long = BestVersionPairedFixtureGate.ALPHA_SOURCE_ID,
    val alternateSourceId: Long = BestVersionPairedFixtureGate.BETA_SOURCE_ID,
    val primaryMangaId: Long,
    val alternateMangaId: Long,
    val primaryMangaUrl: String = PRIMARY_MANGA_URL,
    val alternateMangaUrl: String = ALTERNATE_MANGA_URL,
    val primaryGapChapter: AlternateSourceReaderFixtureChapterSnapshot? = null,
    val bridgeBaselineHash: String,
    val identityBaselineHash: String,
    val actionHistoryBaselineIds: Set<String> = emptySet(),
    val seededOverlayHash: String? = null,
) {
    fun isStructurallyValid(now: Long = System.currentTimeMillis()): Boolean {
        if (schemaVersion != CURRENT_SCHEMA_VERSION || revision != REVISION) return false
        if (!operationId.isUuid() || !pairedFixtureOperationId.isUuid() || operationId == pairedFixtureOperationId) {
            return false
        }
        if (scenario == AlternateSourceReaderFixtureScenario.OFF) return false
        if (createdAt <= 0L || createdAt > now + MAX_FUTURE_SKEW_MS || now - createdAt > MAX_AGE_MS) return false
        if (primarySourceId != BestVersionPairedFixtureGate.ALPHA_SOURCE_ID) return false
        if (alternateSourceId != BestVersionPairedFixtureGate.BETA_SOURCE_ID) return false
        if (primaryMangaId <= 0L || alternateMangaId <= 0L || primaryMangaId == alternateMangaId) return false
        if (primaryMangaUrl != PRIMARY_MANGA_URL || alternateMangaUrl != ALTERNATE_MANGA_URL) return false
        if (!bridgeBaselineHash.isSha256() || !identityBaselineHash.isSha256()) return false
        if (seededOverlayHash?.isSha256() == false) return false
        if (actionHistoryBaselineIds.size > MAX_ACTION_HISTORY_BASELINE) return false
        if (actionHistoryBaselineIds.any { !it.isUuid() }) return false
        if (!hasValidStepOrder()) return false
        if (AlternateSourceReaderFixtureStep.PRIMARY_GAP in completedSteps && primaryGapChapter == null) return false
        if (primaryGapChapter?.isStructurallyValid() == false) return false
        return true
    }

    private fun hasValidStepOrder(): Boolean {
        val steps = AlternateSourceReaderFixtureStep.entries
        val completedIndexes = completedSteps.map(steps::indexOf)
        if (completedIndexes.any { it < 0 }) return false
        if (completedIndexes.sorted() != (0 until completedIndexes.size).toList()) return false
        val pendingIndex = pendingStep?.let(steps::indexOf) ?: return true
        return pendingIndex == completedSteps.size && pendingStep !in completedSteps
    }

    private fun String.isUuid(): Boolean = runCatching { UUID.fromString(this) }.isSuccess

    private fun String.isSha256(): Boolean = matches(SHA_256_REGEX)

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val REVISION = "b07-r1"
        const val PRIMARY_MANGA_URL = "/kmk-fixture/f2/origin"
        const val ALTERNATE_MANGA_URL = "/kmk-fixture/f2/target"
        const val MAX_FUTURE_SKEW_MS = 5 * 60 * 1_000L
        const val MAX_AGE_MS = 24 * 60 * 60 * 1_000L
        const val MAX_ACTION_HISTORY_BASELINE = 40
        private val SHA_256_REGEX = Regex("[0-9A-F]{64}")
    }
}
